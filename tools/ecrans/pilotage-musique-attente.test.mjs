import assert from 'node:assert/strict';
import test from 'node:test';
import { creerAudioSalle } from '../../src/main/resources/static/js/audio-salle.mjs';
import { creerContexte } from '../../src/main/resources/static/js/contexte.mjs';
import { monterMusiqueDAttenteConsole, monterMusiqueDAttenteSalle } from '../../src/main/resources/static/js/pilotage-musique-attente.mjs';
import { terminerMicrotaches } from './karaoke-dom-factice.mjs';

const chemin = '/api/soirees/soiree/musique-attente';
const vue = (etat, morceauxDisponibles = null) => ({ musiqueAttente: { etat, morceauxDisponibles } });

test('la Console règle le son depuis le pilotage commun et attend une vue pour changer le bouton', async () => {
	const bouton = new EventTarget();
	const statut = {};
	const envois = [];
	let captures = 0;
	const confirmation = Promise.withResolvers();
	const { contexte, changerEtat } = creerContexte({ soireeId: 'soiree', resynchroniser: () => captures++,
		envoyer(methode, route, corps, options) {
			envois.push({ methode, route, corps, options });
			return confirmation.promise;
		},
	});
	const console = monterMusiqueDAttenteConsole(bouton, statut, contexte);
	const clique = () => bouton.dispatchEvent(new Event('click'));
	console.afficher(vue('JOUE', true));
	clique();
	assert.equal(bouton.disabled, true);
	assert.equal(envois.length, 0);
	changerEtat('synchronisee');
	assert.equal(bouton.textContent, 'Couper la musique');
	assert.match(statut.textContent, /joue/);
	clique();
	clique();
	assert.equal(bouton.disabled, true);
	assert.equal(envois.length, 1);
	assert.deepEqual(envois[0].corps, { etat: 'COUPEE' });
	assert.equal(envois[0].methode, 'PUT');
	assert.equal(envois[0].route, chemin);
	confirmation.resolve({ statut: 200, corps: { etat: 'COUPEE' } });
	await terminerMicrotaches();
	assert.equal(captures, 1);
	assert.equal(bouton.textContent, 'Couper la musique');
	console.afficher(vue('COUPEE', true));
	assert.equal(bouton.textContent, 'Relancer la musique');
	assert.match(statut.textContent, /coupée/);
	console.afficher(vue('COUPEE', false));
	assert.equal(statut.textContent, 'Aucun morceau dans le dossier Attente de l’Écran de salle');
	changerEtat('coupure');
	assert.equal(bouton.disabled, true);
	console.demonter();
});

test('la Console ne propose la musique que si l’Écran en a, et dit quoi faire sinon (#279, #282)', () => {
	const bouton = new EventTarget();
	const statut = {};
	const envois = [];
	const { contexte, changerEtat } = creerContexte({ soireeId: 'soiree', resynchroniser() {},
		envoyer: async (...args) => { envois.push(args); return { statut: 200, corps: {} }; } });
	const console = monterMusiqueDAttenteConsole(bouton, statut, contexte);
	changerEtat('synchronisee');
	console.afficher(vue('JOUE', null));
	assert.equal(statut.textContent, 'Choisis le dossier de musique sur l’Écran de salle');
	assert.equal(bouton.disabled, true);
	console.afficher(vue('JOUE', false));
	assert.equal(bouton.disabled, true);
	bouton.dispatchEvent(new Event('click'));
	assert.equal(envois.length, 0);
	console.afficher(vue('JOUE', true));
	assert.equal(bouton.disabled, false);
	console.demonter();
});

test('le reçu de la musique dit ce qui a changé (#279)', async () => {
	const bouton = new EventTarget();
	const annonces = [];
	const { contexte, changerEtat } = creerContexte({ soireeId: 'soiree', resynchroniser() {},
		envoyer: async () => ({ statut: 200, corps: {} }), surEnvoi: (...a) => annonces.push(a) });
	const console = monterMusiqueDAttenteConsole(bouton, {}, contexte);
	changerEtat('synchronisee');
	console.afficher(vue('JOUE', true));
	bouton.dispatchEvent(new Event('click'));
	await terminerMicrotaches();
	assert.deepEqual(annonces.at(-1), ['accepte', 'Musique coupée.']);
	console.afficher(vue('COUPEE', true));
	bouton.dispatchEvent(new Event('click'));
	await terminerMicrotaches();
	assert.deepEqual(annonces.at(-1), ['accepte', 'Musique relancée.']);
	console.demonter();
});

test('la Console garde le réglage affiché après un refus sans redemander de capture', async () => {
	const bouton = new EventTarget();
	let captures = 0;
	let etatEnvoi;
	const { contexte, changerEtat } = creerContexte({ soireeId: 'soiree', resynchroniser: () => captures++,
		envoyer: async () => ({ statut: 409, corps: { code: 'COMMANDE_SANS_EFFET' } }),
		surEnvoi: etat => { etatEnvoi = etat; },
	});
	const console = monterMusiqueDAttenteConsole(bouton, {}, contexte);
	console.afficher(vue('JOUE', true));
	changerEtat('synchronisee');
	bouton.dispatchEvent(new Event('click'));
	await terminerMicrotaches();
	assert.equal(etatEnvoi, 'refuse');
	assert.equal(captures, 0);
	assert.equal(bouton.textContent, 'Couper la musique');
	assert.equal(bouton.disabled, false);
	console.demonter();
});

test('la salle applique la coupure dès la capture et ne relance qu’après le signal de Manche', async () => {
	let audible, source;
	const attente = { name: 'Attente.ogg', webkitRelativePath: 'Musique/Attente/Attente.ogg' };
	const audio = creerAudioSalle({ lecteur: {
		async debloquer() {},
		async charger(fichier) { source = fichier; },
		async lire() { audible = source; },
		detruire() { audible = undefined; },
		position: () => 0, surFin: () => () => {}, surErreur: () => () => {},
	}, coordonner(surAttente) {
		return { activer: surAttente, signalerSonDeManche: actif => surAttente(!actif),
			attendreSonDeManche: async () => true, detruire() {} };
	} });
	const { contexte, changerEtat } = creerContexte({ soireeId: 'soiree', envoyer: async () => ({ statut: 200 }) });
	const salle = monterMusiqueDAttenteSalle(audio, contexte);
	audio.choisirDossier([attente]);
	await audio.activerSon();
	await terminerMicrotaches();
	assert.equal(audible, undefined);
	salle.afficher(vue('COUPEE'));
	changerEtat('synchronisee');
	await terminerMicrotaches();
	assert.equal(audible, undefined);

	const montage = Promise.withResolvers();
	salle.afficher(vue('JOUE'), montage.promise);
	await terminerMicrotaches();
	assert.equal(audible, undefined);
	await audio.signalerSonDeManche(true);
	montage.resolve();
	await terminerMicrotaches();
	assert.equal(audible, undefined);
	await audio.signalerSonDeManche(false);
	await terminerMicrotaches();
	assert.equal(audible, attente);
	salle.afficher(vue('COUPEE'));
	assert.equal(audible, undefined);
	salle.afficher(vue('JOUE'));
	await terminerMicrotaches();
	assert.equal(audible, attente);
	changerEtat('coupure');
	assert.equal(audible, undefined);
	salle.demonter();
	await audio.detruire();
});

test('la salle signale seulement la disponibilité du dossier choisi, dans l’ordre des choix', async () => {
	const envois = [];
	const confirmation = Promise.withResolvers();
	let dossierChoisi;
	const audio = {
		reglerMusiqueDAttente() {},
		surChangement(rappel) { dossierChoisi = rappel; return () => { dossierChoisi = undefined; }; },
	};
	const { contexte, changerEtat } = creerContexte({ soireeId: 'soiree',
		envoyer(methode, route, corps, options) {
			envois.push({ methode, route, corps, options });
			return envois.length === 1 ? confirmation.promise : Promise.resolve({ statut: 200 });
		},
	});
	const salle = monterMusiqueDAttenteSalle(audio, contexte);
	dossierChoisi({ fichiers: [], morceauxDisponibles: false });
	assert.equal(envois.length, 0);
	changerEtat('synchronisee');
	const fichiers = [{ name: 'Fichier local.ogg' }];
	dossierChoisi({ fichiers, morceauxDisponibles: true });
	dossierChoisi({ fichiers, morceauxDisponibles: true, sonDebloque: true });
	assert.equal(envois.length, 1);
	confirmation.resolve({ statut: 200 });
	await terminerMicrotaches();
	assert.deepEqual(envois.map(({ methode, route, corps }) => ({ methode, route, corps })), [
		{ methode: 'PUT', route: chemin + '/disponibilite', corps: { morceauxDisponibles: false } },
		{ methode: 'PUT', route: chemin + '/disponibilite', corps: { morceauxDisponibles: true } },
	]);
	salle.demonter();
	assert.equal(dossierChoisi, undefined);
});
