import test from 'node:test';
import assert from 'node:assert/strict';
import { aligner, creerEnvoiBrouillon, extraitJouable, extraitRestantMs, formaterSecondes, positionCibleMs,
	tempsRestantMs, textePartieTrouvee, verdictPersonnel } from '../../src/main/resources/static/js/modes/blindtest/logique.mjs';
import { creerLecteurExtrait, VERROU_EXTRAIT } from '../../src/main/resources/static/js/modes/blindtest/lecteur-extrait.mjs';
import { terminerMicrotaches } from './karaoke-dom-factice.mjs';

const T0 = Date.parse('2026-11-25T20:00:00Z');
const enLecture = (positionMs, depuis = T0) => ({ etat: 'EN_LECTURE', positionMs, depuisLe: new Date(depuis).toISOString() });
const enPause = positionMs => ({ etat: 'EN_PAUSE', positionMs, depuisLe: null });

test('la position cible suit l’Horloge du serveur et s’arrête aux 30 s', () => {
	assert.equal(positionCibleMs(null, T0), null);
	assert.equal(positionCibleMs(enLecture(4000), T0 + 2500), 6500);
	assert.equal(positionCibleMs(enLecture(29000), T0 + 5000), 30000);
	assert.equal(positionCibleMs(enLecture(1000), T0 - 400), 600, 'Une horloge en avance ne recule pas sous 0.');
	assert.equal(positionCibleMs(enPause(12000), T0 + 60000), 12000);
	assert.equal(extraitRestantMs(enLecture(10000), T0 + 5000), 15000);
});

test('l’alignement ne se déplace qu’au-delà de la tolérance et attend en pause', () => {
	assert.deepEqual(aligner({ lecture: enLecture(5000), maintenantMs: T0 + 1000, positionMs: null }),
		{ enLecture: true, allerA: 6000 }, 'Un extrait juste chargé part de la position publiée.');
	assert.deepEqual(aligner({ lecture: enLecture(5000), maintenantMs: T0 + 1000, positionMs: 5900 }),
		{ enLecture: true, allerA: null });
	assert.deepEqual(aligner({ lecture: enLecture(5000), maintenantMs: T0 + 1000, positionMs: 3000 }),
		{ enLecture: true, allerA: 6000 });
	assert.deepEqual(aligner({ lecture: enPause(8000), maintenantMs: T0, positionMs: 9500 }),
		{ enLecture: false, allerA: 8000 }, 'Une Proposition met l’extrait en pause à sa position publiée.');
	assert.deepEqual(aligner({ lecture: enLecture(29900), maintenantMs: T0 + 500, positionMs: 30000 }),
		{ enLecture: false, allerA: null }, 'Un extrait arrivé au bout ne se relance pas.');
});

test('le compte à rebours vient de l’échéance du serveur, ou du reste gelé pendant la pause', () => {
	const main = { joueurId: 'j', pseudo: 'Léa', echeanceLe: new Date(T0 + 20000).toISOString(), tempsRestantMs: null };
	assert.equal(tempsRestantMs(main, T0 + 7200), 12800);
	assert.equal(tempsRestantMs(main, T0 + 25000), 0);
	assert.equal(tempsRestantMs({ ...main, echeanceLe: null, tempsRestantMs: 9100 }, T0 + 99999), 9100);
	assert.equal(formaterSecondes(12800), '13 s');
	assert.equal(formaterSecondes(0), '0 s');
	assert.equal(formaterSecondes(-5), '0 s');
});

test('textes : partie trouvée et verdict lu dans la vue', () => {
	assert.equal(textePartieTrouvee({ partie: 'ARTISTE', pseudo: 'Léa', valeur: 'Beyoncé' }), 'Artiste trouvé par Léa : Beyoncé');
	const artiste = { partie: 'ARTISTE', valeur: 'Beyoncé', joueurId: 'lea', pseudo: 'Léa', points: 30 };
	const titre = { partie: 'TITRE', valeur: 'Halo', joueurId: 'lea', pseudo: 'Léa', points: 70 };
	assert.equal(verdictPersonnel({ partiesTrouvees: [], bloque: false }, 'lea'), null);
	assert.deepEqual(verdictPersonnel({ partiesTrouvees: [artiste], bloque: true }, 'lea'),
		{ ton: 'refuse', texte: 'Artiste trouvé, +30 points. Le reste est faux : tu es bloqué sur ce Morceau.' });
	assert.deepEqual(verdictPersonnel({ partiesTrouvees: [artiste], bloque: false }, 'sam'), null);
	assert.deepEqual(verdictPersonnel({ partiesTrouvees: [], bloque: true }, 'sam'),
		{ ton: 'refuse', texte: 'Proposition fausse : tu es bloqué sur ce Morceau.' });
	assert.deepEqual(verdictPersonnel({ partiesTrouvees: [titre, artiste], bloque: false }, 'lea'),
		{ ton: 'accepte', texte: 'Titre et Artiste trouvés, +100 points.' });
});

function minuteries() {
	const enAttente = new Map();
	let numero = 0;
	return {
		programmer(f, ms) { enAttente.set(++numero, { f, ms }); return numero; },
		annuler: id => enAttente.delete(id),
		declencher() { const liste = [...enAttente.values()]; enAttente.clear(); for (const { f } of liste) f(); },
		get nombre() { return enAttente.size; },
	};
}

test('le brouillon part espacé, une requête à la fois, la saisie la plus récente en dernier', async () => {
	const temps = minuteries(), envois = [];
	const envoi = creerEnvoiBrouillon({ ...temps, delaiMs: 400,
		envoyer: corps => new Promise((resolve, reject) => envois.push({ corps, resolve, reject })) });
	envoi.saisir({ titre: 'H', artiste: '' });
	envoi.saisir({ titre: 'Ha', artiste: '' });
	assert.equal(temps.nombre, 1, 'Chaque frappe repousse le même délai.');
	temps.declencher();
	await terminerMicrotaches();
	assert.deepEqual(envois.map(e => e.corps), [{ numero: 1, titre: 'Ha', artiste: '' }]);

	envoi.saisir({ titre: 'Hal', artiste: '' });
	envoi.saisir({ titre: 'Halo', artiste: '' });
	temps.declencher();
	await terminerMicrotaches();
	assert.equal(envois.length, 1, 'Rien ne part tant que la requête précédente est en cours.');
	envois[0].resolve({ statut: 204 });
	await terminerMicrotaches();
	assert.deepEqual(envois[1].corps, { numero: 2, titre: 'Halo', artiste: '' });
});

test('un brouillon en échec est signalé sans être renvoyé tout seul, et la numérotation reprend après un rechargement', async () => {
	const temps = minuteries(), envois = [], echecs = [];
	const envoi = creerEnvoiBrouillon({ ...temps, surEchec: valeur => echecs.push(valeur),
		envoyer: corps => new Promise((resolve, reject) => envois.push({ corps, resolve, reject })) });
	envoi.reprendre(7);
	envoi.saisir({ titre: '', artiste: 'Bey' });
	temps.declencher();
	await terminerMicrotaches();
	envois[0].reject(new TypeError('réseau'));
	await terminerMicrotaches();
	assert.equal(envois.length, 1);
	assert.equal(temps.nombre, 0);
	assert.equal(envois[0].corps.numero, 8);
	assert.deepEqual(echecs, [true]);
	assert.equal(envoi.echec, true);

	envoi.renvoyer({ titre: '', artiste: 'Bey' });
	await terminerMicrotaches();
	assert.deepEqual(envois[1].corps, { numero: 9, titre: '', artiste: 'Bey' }, 'Le renvoi à la main part aussitôt, numéro supérieur.');
	envois[1].resolve({ statut: 503, corps: { detail: 'Indisponible' } });
	await terminerMicrotaches();
	assert.deepEqual(echecs, [true], 'Une réponse hors 2xx est aussi un échec.');
	envoi.renvoyer({ titre: '', artiste: 'Bey' });
	await terminerMicrotaches();
	envois[2].resolve({ statut: 204, corps: null });
	await terminerMicrotaches();
	assert.deepEqual(echecs, [true, false]);
	assert.equal(envoi.echec, false);

	envoi.saisir({ titre: '', artiste: 'Beyoncé' });
	envoi.detruire();
	temps.declencher();
	await terminerMicrotaches();
	assert.equal(envois.length, 3, 'Un envoi détruit ne part plus.');
});

const bloc = (phase, lecture, lienExtrait = 'https://cdnt-preview.dzcdn.net/a.mp3') => ({
	phase, lecture, lienExtrait: ['EXTRAIT', 'PROPOSITION'].includes(phase) ? lienExtrait : null });

test('extraitJouable : seulement en phases EXTRAIT et PROPOSITION avec un lien', () => {
	assert.equal(extraitJouable(bloc('EXTRAIT', enLecture(0))), true);
	assert.equal(extraitJouable(bloc('PROPOSITION', enPause(3000))), true);
	assert.equal(extraitJouable(bloc('PREPARATION', null)), false);
	assert.equal(extraitJouable(bloc('REVELATION', null)), false);
});

/**
 * Web Locks factices : un verrou exclusif partagé entre les fenêtres d'un même navigateur, avec sa file d'attente.
 * Une demande en attente s'abandonne par son signal, comme dans le navigateur.
 */
function verrousPartages() {
	const tenus = new Map(), files = new Map();
	function accorder(nom) {
		const suivante = files.get(nom)?.shift();
		if (!suivante) return;
		tenus.set(nom, suivante);
		Promise.resolve().then(() => suivante.rappel({ name: nom })).then(suivante.resolve, suivante.reject)
			.finally(() => { tenus.delete(nom); accorder(nom); });
	}
	return {
		tenus,
		enAttente: nom => files.get(nom)?.length ?? 0,
		request(nom, options, rappel) {
			assert.equal(options.ifAvailable, undefined, 'La demande attend son tour : la relève est immédiate.');
			return new Promise((resolve, reject) => {
				const demande = { rappel, resolve, reject };
				const file = files.get(nom) ?? [];
				files.set(nom, file);
				file.push(demande);
				options.signal?.addEventListener('abort', () => {
					const i = file.indexOf(demande);
					if (i < 0) return;
					file.splice(i, 1);
					reject(new DOMException('Demande abandonnée.', 'AbortError'));
				});
				if (!tenus.has(nom)) accorder(nom);
			});
		},
	};
}

function fenetre(verrous, { sonActive = true, maintenant = T0 } = {}) {
	const journal = [], signaux = [], incidents = [];
	let position = 0, autorise = true, termine = false, refus = null;
	const audio = {
		termine: () => termine,
		charger: lien => journal.push(`charger ${lien}`),
		lire: async () => {
			journal.push('lire');
			if (refus) throw new DOMException('refus', refus);
		},
		pause: () => journal.push('pause'),
		position: () => position,
		aller: ms => { position = ms; journal.push(`aller ${ms}`); },
		arreter: () => journal.push('arrêter'),
	};
	const etat = { sonActive, maintenant };
	const lecteur = creerLecteurExtrait({ audio, verrous,
		signalerSonDeManche: async actif => { signaux.push(actif); return actif && autorise; },
		sonActive: () => etat.sonActive, maintenantServeur: () => etat.maintenant,
		surBlocage: () => incidents.push('bloqué'), surIllisible: () => incidents.push('illisible'),
		surLecture: () => incidents.push('lecture') });
	return { lecteur, journal, signaux, etat, incidents, regler: p => { position = p; }, refuser() { autorise = false; },
		finir() { termine = true; }, echouer(nom) { refus = nom; } };
}

test('restauration : la fenêtre élue reprend l’extrait à la position publiée, ou le laisse en pause', async () => {
	const verrous = verrousPartages(), f = fenetre(verrous, { maintenant: T0 + 3000 });
	await f.lecteur.suivre(bloc('EXTRAIT', enLecture(9000)));
	await terminerMicrotaches();
	assert.equal(f.lecteur.elue, true);
	assert.deepEqual(f.journal.slice(-3), ['charger https://cdnt-preview.dzcdn.net/a.mp3', 'aller 12000', 'lire']);
	assert.deepEqual(f.signaux, [true, true], 'Le son de Manche est réservé avant toute lecture.');

	const g = fenetre(verrousPartages());
	await g.lecteur.suivre(bloc('PROPOSITION', enPause(14000)));
	await terminerMicrotaches();
	assert.deepEqual(g.journal.slice(-3), ['charger https://cdnt-preview.dzcdn.net/a.mp3', 'aller 14000', 'pause']);
	assert.equal(g.journal.includes('lire'), false);
});

test('avec deux fenêtres de salle, une seule joue ; l’autre prend la relève quand la première se ferme', async () => {
	const verrous = verrousPartages(), a = fenetre(verrous), b = fenetre(verrous);
	const extrait = bloc('EXTRAIT', enLecture(0));
	await a.lecteur.suivre(extrait);
	await b.lecteur.suivre(extrait);
	await terminerMicrotaches();
	assert.equal(a.lecteur.elue, true);
	assert.equal(b.lecteur.elue, false);
	assert.equal(b.journal.includes('lire'), false);
	assert.deepEqual(b.signaux.at(-1), true, 'La fenêtre muette fait quand même taire sa Musique d’attente.');

	assert.equal(verrous.enAttente(VERROU_EXTRAIT), 1, 'La fenêtre muette attend son tour.');

	a.lecteur.detruire();
	await terminerMicrotaches();
	assert.deepEqual(a.journal.at(-1), 'arrêter');
	assert.equal(a.signaux.at(-1), false, 'Le démontage rend le son de Manche après l’arrêt.');
	assert.equal(b.lecteur.elue, true, 'La relève est immédiate, sans attendre la vue suivante.');
	assert.equal(b.journal.at(-1), 'lire');

	b.lecteur.detruire();
	await terminerMicrotaches();
	assert.equal(verrous.tenus.has(VERROU_EXTRAIT), false);
});

test('une fenêtre démontée en attente abandonne sa demande de verrou', async () => {
	const verrous = verrousPartages(), a = fenetre(verrous), b = fenetre(verrous);
	const extrait = bloc('EXTRAIT', enLecture(0));
	await a.lecteur.suivre(extrait);
	await b.lecteur.suivre(extrait);
	await terminerMicrotaches();
	b.lecteur.detruire();
	await terminerMicrotaches();
	assert.equal(verrous.enAttente(VERROU_EXTRAIT), 0);
	a.lecteur.detruire();
	await terminerMicrotaches();
	assert.equal(verrous.tenus.has(VERROU_EXTRAIT), false, 'Personne ne reprend le verrou après le démontage.');
	assert.equal(b.journal.includes('lire'), false);
});

test('seul un refus du navigateur demande un geste ; un lien illisible le dit, une lecture interrompue ne dit rien', async () => {
	const extrait = bloc('EXTRAIT', enLecture(0));
	const bloquee = fenetre(verrousPartages());
	bloquee.echouer('NotAllowedError');
	await bloquee.lecteur.suivre(extrait);
	await terminerMicrotaches();
	assert.deepEqual(bloquee.incidents, ['bloqué']);
	bloquee.echouer(null);
	await bloquee.lecteur.lancerParGeste();
	await terminerMicrotaches();
	assert.equal(bloquee.journal.filter(ligne => ligne === 'lire').length, 3, 'Le geste joue, puis le suivi réaligne.');
	assert.equal(bloquee.incidents.at(-1), 'lecture');

	const expiree = fenetre(verrousPartages());
	expiree.echouer('NotSupportedError');
	await expiree.lecteur.suivre(extrait);
	await terminerMicrotaches();
	assert.deepEqual(expiree.incidents, ['illisible']);

	const interrompue = fenetre(verrousPartages());
	interrompue.echouer('AbortError');
	await interrompue.lecteur.suivre(extrait);
	await terminerMicrotaches();
	assert.deepEqual(interrompue.incidents, []);
});

test('sans son activé, la fenêtre ne se porte pas candidate ; elle le fait quand la coquille active le son', async () => {
	const verrous = verrousPartages(), f = fenetre(verrous, { sonActive: false });
	await f.lecteur.suivre(bloc('EXTRAIT', enLecture(0)));
	await terminerMicrotaches();
	assert.equal(verrous.tenus.size, 0);
	assert.equal(f.journal.includes('lire'), false);
	f.etat.sonActive = true;
	await f.lecteur.relancer();
	await terminerMicrotaches();
	assert.equal(f.journal.at(-1), 'lire');
});

const revelation = (etat = 'EN_COURS') => ({ phase: 'REVELATION', etat, lecture: null, lienExtrait: null });

test('la Révélation laisse l’extrait aller jusqu’à sa fin, en pause avec la Manche, puis le Morceau suivant l’arrête', async () => {
	const f = fenetre(verrousPartages(), { maintenant: T0 + 9000 });
	await f.lecteur.suivre(bloc('PROPOSITION', enPause(9000)));
	await terminerMicrotaches();
	assert.equal(f.journal.at(-1), 'pause', 'La Proposition met l’extrait en pause.');

	await f.lecteur.suivre(revelation());
	assert.equal(f.journal.at(-1), 'lire', 'Les deux parties trouvées : l’extrait reprend d’où il était.');
	assert.equal(f.journal.filter(ligne => ligne.startsWith('aller')).length, 1, 'Aucun déplacement : rien à aligner.');
	await f.lecteur.suivre(revelation('EN_PAUSE'));
	assert.equal(f.journal.at(-1), 'pause');
	await f.lecteur.suivre(revelation());
	assert.equal(f.journal.at(-1), 'lire');

	await f.lecteur.suivre({ phase: 'PREPARATION', etat: 'EN_COURS', lecture: null, lienExtrait: null });
	assert.equal(f.journal.at(-1), 'arrêter');
	assert.equal(f.signaux.includes(false), false, 'Le son de Manche reste réservé jusqu’au démontage.');
	f.lecteur.detruire();
	assert.equal(f.signaux.at(-1), false);
});

test('la Révélation ne relance ni un extrait fini, ni rien après un rechargement', async () => {
	const fini = fenetre(verrousPartages());
	await fini.lecteur.suivre(bloc('EXTRAIT', enLecture(0)));
	await terminerMicrotaches();
	fini.finir();
	await fini.lecteur.suivre(revelation());
	assert.equal(fini.journal.at(-1), 'arrêter', 'Un extrait arrivé au bout ne repart pas du début.');

	const recharge = fenetre(verrousPartages());
	await recharge.lecteur.suivre(revelation());
	await terminerMicrotaches();
	assert.equal(recharge.journal.includes('lire'), false, 'Sans lien dans la vue, la salle rechargée reste muette.');
});

test('rien ne joue tant que la coquille ne rend pas le son de Manche', async () => {
	const f = fenetre(verrousPartages());
	f.refuser();
	await f.lecteur.suivre(bloc('EXTRAIT', enLecture(0)));
	await terminerMicrotaches();
	assert.equal(f.journal.includes('lire'), false);
});
