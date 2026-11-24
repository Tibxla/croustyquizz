import assert from 'node:assert/strict';
import test from 'node:test';
import { creerEnregistrement } from '../../src/main/resources/static/js/modes/karaoke/enregistrement.mjs';
import { nomFichier } from '../../src/main/resources/static/js/modes/karaoke/enregistrement-manette.mjs';
import { monter as manette } from '../../src/main/resources/static/js/modes/karaoke/manette.mjs';
import { monter as consoleKaraoke } from '../../src/main/resources/static/js/modes/karaoke/console.mjs';
import { creerBanc, creerDocument, terminerMicrotaches, passage, vue } from './karaoke-dom-factice.mjs';

const evenement = type => ({ source: 'evenement', type });
const capture = { source: 'capture' };

function banc({ formats = ['audio/webm;codecs=opus', 'audio/mp4'], demanderMicro } = {}) {
	const appels = [];
	let pistesLiberees = 0, demandes = 0;
	const flux = { getTracks: () => [{ stop: () => pistesLiberees++ }] };
	let appareil;
	class MediaRecorderFactice {
		static isTypeSupported(format) { return formats.includes(format); }
		constructor(pistes, { mimeType }) {
			assert.equal(pistes, flux);
			this.mimeType = mimeType;
			appareil = this;
		}
		start() { appels.push('start'); }
		pause() { appels.push('pause'); }
		resume() { appels.push('resume'); }
		stop() { appels.push('stop'); }
		livrer() {
			this.ondataavailable?.({ data: new Blob(['extrait']) });
			this.onstop?.();
		}
	}
	const prise = creerEnregistrement({
		demanderMicro: contraintes => {
			assert.deepEqual(contraintes, { audio: true });
			demandes++;
			return demanderMicro ? demanderMicro(flux) : Promise.resolve(flux);
		},
		Enregistreur: MediaRecorderFactice,
		surEtat() {},
	});
	return { prise, appels, pistesLiberees: () => pistesLiberees, demandes: () => demandes, recorder: () => appareil };
}

test('un geste après l’accord arme le micro et les événements pilotent la prise', async () => {
	const { prise, appels, pistesLiberees, demandes, recorder } = banc();
	await prise.autoriserMicro();
	assert.equal(demandes(), 0);
	prise.suivre({ accord: true, phase: 'avant' }, capture);
	assert.equal(prise.lire().etat, 'accord');
	assert.equal(demandes(), 0);
	await prise.autoriserMicro();
	assert.equal(prise.lire().etat, 'micro');
	prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	prise.suivre({ accord: true, phase: 'pause' }, evenement('manche-en-pause'));
	prise.suivre({ accord: true, phase: 'pause' }, capture);
	assert.equal(prise.lire().etat, 'pause');
	prise.suivre({ accord: true, phase: 'actif' }, evenement('manche-reprise'));
	prise.suivre({ accord: false, phase: 'termine' }, evenement('passage-appele'));
	assert.deepEqual(appels, ['start', 'pause', 'resume', 'stop']);
	assert.equal(pistesLiberees(), 1);
	recorder().livrer();
	assert.equal(prise.lire().etat, 'pret');
	assert.equal(prise.lire().fichier.type, 'audio/webm;codecs=opus');
	assert.equal(await prise.lire().fichier.text(), 'extrait');
});

test('une capture restaurée pendant le Passage ne démarre jamais le micro préparé', async () => {
	const { prise, appels, pistesLiberees } = banc();
	prise.suivre({ accord: true, phase: 'avant' }, capture);
	await prise.autoriserMicro();
	prise.suivre({ accord: true, phase: 'actif' }, capture);
	prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	assert.deepEqual(appels, []);
	assert.equal(pistesLiberees(), 1);
	assert.equal(prise.lire().motif, 'depart-manque');
});

test('une permission reçue après interdiction libère aussitôt les pistes', async () => {
	let autoriser;
	const { prise, pistesLiberees, appels } = banc({
		demanderMicro: flux => new Promise(resoudre => { autoriser = () => resoudre(flux); }),
	});
	prise.suivre({ accord: true, phase: 'avant' }, capture);
	const demande = prise.autoriserMicro();
	prise.suivre({ accord: false, phase: 'avant' }, evenement('autorisation-enregistrement-modifiee'));
	autoriser();
	await demande;
	assert.equal(prise.lire().etat, 'inactif');
	assert.equal(pistesLiberees(), 1);
	assert.deepEqual(appels, []);
});

test('le refus du navigateur et le format absent sont des états locaux', async () => {
	const refuse = banc({ demanderMicro: () => Promise.reject(new Error('Refus du navigateur')) });
	const absent = banc({ formats: [] });
	for (const { prise } of [refuse, absent]) {
		prise.suivre({ accord: true, phase: 'avant' }, capture);
		await prise.autoriserMicro();
		assert.equal(prise.lire().etat, 'refus');
	}
	assert.equal(refuse.prise.lire().motif, 'micro-refuse');
	assert.equal(absent.prise.lire().motif, 'indisponible');
	assert.equal(absent.demandes(), 0);
});

test('le format MP4 sert de repli et un démontage ignore toute livraison tardive', async () => {
	const { prise, appels, pistesLiberees, recorder } = banc({ formats: ['audio/mp4'] });
	prise.suivre({ accord: true, phase: 'avant' }, capture);
	await prise.autoriserMicro();
	assert.equal(recorder().mimeType, 'audio/mp4');
	prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	prise.detruire();
	recorder().livrer();
	assert.deepEqual(appels, ['start', 'stop']);
	assert.equal(pistesLiberees(), 1);
	assert.equal(prise.lire().fichier, undefined);
});

test('une capture après coupure reprend une prise en pause sans relancer une prise arrêtée', async () => {
	const { prise, appels, recorder } = banc();
	prise.suivre({ accord: true, phase: 'avant' }, capture);
	await prise.autoriserMicro();
	prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	prise.suivre({ accord: true, phase: 'pause' }, evenement('manche-en-pause'));
	// La reprise a été manquée pendant la coupure : la capture suffit, la prise a déjà démarré.
	prise.suivre({ accord: true, phase: 'actif' }, capture);
	assert.equal(prise.lire().etat, 'enregistrement');
	prise.suivre({ accord: true, phase: 'termine' }, capture);
	recorder().livrer();
	prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	assert.deepEqual(appels, ['start', 'pause', 'resume', 'stop']);
	assert.equal(prise.lire().etat, 'pret');
});

test('le nom du fichier porte le titre nettoyé et la date du Passage, avec un repli sur l’heure du serveur', () => {
	const webm = { type: 'audio/webm;codecs=opus' }, mp4 = { type: 'audio/mp4' };
	const depart = new Date(2026, 10, 24, 21, 30).toISOString();
	assert.equal(nomFichier('  Les / Lacs : du * Connemara ', depart, webm), 'Les Lacs du Connemara - 2026-11-24.webm');
	assert.equal(nomFichier('', null, mp4, new Date(2026, 0, 5, 12).getTime()), 'Passage - 2026-01-05.mp4');
});

function fenetre() {
	const journal = { demandes: 0, pistes: 0, appareils: [], urls: [], revoquees: [] };
	const flux = { getTracks: () => [{ stop: () => journal.pistes++ }] };
	class MediaRecorderFactice {
		static isTypeSupported(format) { return format === 'audio/webm;codecs=opus'; }
		constructor() { this.appels = []; journal.appareils.push(this); }
		start() { this.appels.push('start'); }
		pause() { this.appels.push('pause'); }
		resume() { this.appels.push('resume'); }
		stop() {
			this.appels.push('stop');
			this.ondataavailable?.({ data: new Blob(['voix']) });
			this.onstop?.();
		}
	}
	const vueFenetre = {
		navigator: { mediaDevices: { getUserMedia: async contraintes => {
			assert.deepEqual(contraintes, { audio: true });
			journal.demandes++;
			return flux;
		} } },
		MediaRecorder: MediaRecorderFactice,
	};
	return { journal, vueFenetre };
}

const visibles = (zone, texte) => zone.querySelectorAll('button').filter(b => b.textContent === texte && b.isConnected
	&& !b.hidden && !b.parentNode.hidden);

test('Manette : l’accord part en PUT idempotent, puis le micro n’est demandé qu’au geste', async t => {
	const document = creerDocument(), { journal, vueFenetre } = fenetre();
	document.defaultView = vueFenetre;
	const b = creerBanc(manette, 'JOUEUR', document);
	t.after(() => b.module.demonter());
	b.connecter('synchronisee');
	const attente = { enregistrementAutorise: true, mesAccordsEnregistrement: [], mesPassagesEnAttente: [passage('p')] };
	b.module.afficher(vue(attente), capture);
	assert.equal(visibles(b.principale, 'Autoriser le micro').length, 0);
	const [enregistrer] = visibles(b.principale, 'Enregistrer mon Passage');
	assert.ok(enregistrer, 'L’option est proposée sur le ticket en attente.');
	enregistrer.click();
	assert.equal(b.appels.length, 1);
	const [methode, chemin, corps, options] = b.appels[0].args;
	assert.equal(methode, 'PUT');
	assert.equal(chemin, '/api/soirees/s/manches/m/passages/p/accord-enregistrement');
	assert.deepEqual(corps, { accord: true, numeroModification: 2 });
	assert.equal(options.entetes['Idempotency-Key'].length, 36);
	b.appels[0].resolve({ statut: 200, corps: { accord: true } });
	await terminerMicrotaches();
	assert.equal(journal.demandes, 0);

	b.module.afficher(vue({ ...attente, mesAccordsEnregistrement: ['p'] }),
		{ source: 'evenement', type: 'accord-enregistrement-modifie' });
	assert.equal(visibles(b.principale, 'Retirer mon accord').length, 1);
	const [micro] = visibles(b.principale, 'Autoriser le micro');
	assert.ok(micro, 'Le micro se demande après l’accord.');
	micro.click();
	await terminerMicrotaches();
	assert.equal(journal.demandes, 1);
	assert.match(b.principale.textContent, /Micro prêt/);

	b.module.afficher(vue({ ...attente, enregistrementAutorise: false }),
		{ source: 'evenement', type: 'autorisation-enregistrement-modifiee' });
	await terminerMicrotaches();
	assert.equal(visibles(b.principale, 'Enregistrer mon Passage').length, 0);
	assert.equal(visibles(b.principale, 'Retirer mon accord').length, 0);
	assert.equal(visibles(b.principale, 'Autoriser le micro').length, 0);
	assert.equal(journal.pistes, 1);
	assert.equal(journal.appareils.every(a => a.appels.length === 0), true);
});

test('Manette : la prise suit le Passage, propose le fichier puis libère tout au démontage', async t => {
	const document = creerDocument(), { journal, vueFenetre } = fenetre();
	document.defaultView = vueFenetre;
	let numero = 0;
	t.mock.method(URL, 'createObjectURL', () => { const url = `blob:enregistrement-${++numero}`; journal.urls.push(url); return url; });
	t.mock.method(URL, 'revokeObjectURL', url => journal.revoquees.push(url));
	const b = creerBanc(manette, 'JOUEUR', document);
	b.connecter('synchronisee');
	const appele = { ...passage('p'), etat: 'APPELE', etapeId: 'etape', echeanceAppelLe: Date.now() + 30000 };
	const commun = { enregistrementAutorise: true, mesAccordsEnregistrement: ['p'] };
	b.module.afficher(vue({ ...commun, courant: appele }), capture);
	visibles(b.principale, 'Autoriser le micro')[0].click();
	await terminerMicrotaches();
	const demarreLe = new Date(2026, 10, 24, 22).toISOString();
	const enCours = { ...appele, etat: 'EN_COURS', demarreLe };
	b.module.afficher(vue({ ...commun, courant: enCours }), { source: 'evenement', type: 'passage-demarre' });
	assert.match(b.principale.textContent, /Enregistrement en cours/);
	b.module.afficher(vue({ ...commun, etat: 'EN_PAUSE', courant: { ...enCours, etat: 'EN_PAUSE' } }),
		{ source: 'evenement', type: 'manche-en-pause' });
	b.module.afficher(vue({ ...commun, courant: enCours }), { source: 'evenement', type: 'manche-reprise' });
	b.module.afficher(vue({ ...commun, courant: { ...enCours, etat: 'TERMINE' }, mesAccordsEnregistrement: [] }),
		{ source: 'evenement', type: 'passage-termine' });
	await terminerMicrotaches();
	assert.deepEqual(journal.appareils[0].appels, ['start', 'pause', 'resume', 'stop']);
	assert.equal(journal.pistes, 1);
	const lien = b.principale.querySelectorAll('a').find(a => a.textContent === 'Télécharger mon Enregistrement');
	assert.ok(lien, 'Le lien de téléchargement est proposé.');
	assert.equal(lien.href, 'blob:enregistrement-1');
	assert.equal(lien.download, 'Titre de démonstration - 2026-11-24.webm');
	const ecoute = b.principale.querySelector('audio');
	assert.ok(ecoute, 'Un lecteur permet d’écouter la prise.');
	assert.equal(ecoute.controls, true);
	assert.equal(ecoute.src, 'blob:enregistrement-1');
	b.module.demonter();
	assert.deepEqual(journal.revoquees, ['blob:enregistrement-1']);
	assert.equal(b.principale.childElementCount, 0);
});

test('Console : l’Animateur interdit puis réautorise les Enregistrements de la Soirée', async t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	b.connecter('synchronisee');
	b.module.afficher(vue({ enregistrementAutorise: true }), capture);
	const [interdire] = visibles(b.principale, 'Interdire les Enregistrements');
	assert.ok(interdire, 'Le réglage est proposé dans la Console.');
	interdire.click();
	const [methode, chemin, corps, options] = b.appels[0].args;
	assert.equal(methode, 'PUT');
	assert.equal(chemin, '/api/soirees/s/manches/m/autorisation-enregistrement');
	assert.deepEqual(corps, { autorise: false });
	assert.equal(options.entetes['Idempotency-Key'].length, 36);
	b.appels[0].resolve({ statut: 200, corps: { autorise: false } });
	await terminerMicrotaches();
	b.module.afficher(vue({ enregistrementAutorise: false }),
		{ source: 'evenement', type: 'autorisation-enregistrement-modifiee' });
	assert.equal(visibles(b.principale, 'Autoriser les Enregistrements').length, 1);
});
