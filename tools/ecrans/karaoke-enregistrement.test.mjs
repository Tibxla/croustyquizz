import assert from 'node:assert/strict';
import test from 'node:test';
import { creerEnregistrement, diagnostiquer, RETARD_DEPART_MAX_MS } from '../../src/main/resources/static/js/modes/karaoke/enregistrement.mjs';
import { nomFichier } from '../../src/main/resources/static/js/modes/karaoke/enregistrement-manette.mjs';
import { monter as manette } from '../../src/main/resources/static/js/modes/karaoke/manette.mjs';
import { monter as consoleKaraoke } from '../../src/main/resources/static/js/modes/karaoke/console.mjs';
import { creerBanc, creerDocument, terminerMicrotaches, passage, vue } from './karaoke-dom-factice.mjs';

const evenement = type => ({ source: 'evenement', type });
const capture = { source: 'capture' };

// Piste factice : `ended` ne part que si le système coupe le micro, jamais après stop().
function creerPiste(journal) {
	const piste = new EventTarget();
	piste.stop = () => journal.pistes++;
	piste.couper = () => piste.dispatchEvent(new Event('ended'));
	return piste;
}

function banc({ formats = ['audio/mp4', 'audio/webm;codecs=opus'], demanderMicro, arretLeve = false } = {}) {
	const appels = [], resultats = [], journal = { pistes: 0 };
	let demandes = 0;
	const piste = creerPiste(journal);
	const flux = { getTracks: () => [piste] };
	let appareil;
	class MediaRecorderFactice {
		static isTypeSupported(format) { return formats.includes(format); }
		constructor(pistes, { mimeType }) {
			assert.equal(pistes === flux, true);
			this.mimeType = mimeType;
			appareil = this;
		}
		start(intervalle) { this.intervalle = intervalle; appels.push('start'); }
		pause() { appels.push('pause'); }
		resume() { appels.push('resume'); }
		stop() {
			appels.push('stop');
			if (arretLeve) throw new Error('Appareil déjà arrêté');
		}
		fragment(texte) { this.ondataavailable?.({ data: new Blob([texte]) }); }
		livrer(texte = 'extrait') {
			if (texte) this.fragment(texte);
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
		surResultat: resultat => resultats.push(resultat),
	});
	return { prise, appels, resultats, piste, pistesLiberees: () => journal.pistes, demandes: () => demandes, recorder: () => appareil };
}

async function preparer(b) {
	b.prise.suivre({ accord: true, phase: 'avant' }, capture);
	await b.prise.autoriserMicro();
	assert.equal(b.prise.lire().etat, 'micro');
}

test('un geste après l’accord arme le micro et les événements pilotent la prise par fragments', async () => {
	const { prise, appels, resultats, pistesLiberees, demandes, recorder } = banc();
	await prise.autoriserMicro();
	assert.equal(demandes(), 0);
	prise.suivre({ accord: true, phase: 'avant' }, capture);
	assert.equal(prise.lire().etat, 'accord');
	assert.equal(demandes(), 0);
	await prise.autoriserMicro();
	assert.equal(prise.lire().etat, 'micro');
	prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	assert.equal(recorder().intervalle > 0, true, 'Les fragments arrivent pendant la prise, pas seulement à l’arrêt.');
	prise.suivre({ accord: true, phase: 'pause' }, evenement('manche-en-pause'));
	prise.suivre({ accord: true, phase: 'pause' }, capture);
	assert.equal(prise.lire().etat, 'pause');
	prise.suivre({ accord: true, phase: 'actif' }, evenement('manche-reprise'));
	prise.suivre({ accord: false, phase: 'termine' }, evenement('passage-appele'));
	assert.deepEqual(appels, ['start', 'pause', 'resume', 'stop']);
	assert.equal(pistesLiberees(), 1);
	recorder().livrer();
	assert.equal(prise.lire().etat, 'pret');
	assert.equal(prise.lire().fichier.type, 'audio/mp4');
	assert.equal(await prise.lire().fichier.text(), 'extrait');
	assert.equal(resultats.length, 1);
	assert.equal(resultats[0].motif, undefined);
});

test('MP4 passe en premier et WebM sert de repli', async () => {
	const webm = banc({ formats: ['audio/webm;codecs=opus'] });
	await preparer(webm);
	assert.equal(webm.recorder().mimeType, 'audio/webm;codecs=opus');
	const deux = banc();
	await preparer(deux);
	assert.equal(deux.recorder().mimeType, 'audio/mp4');
});

test('le départ sans micro prêt donne un motif propre à chaque cas', async () => {
	const sansMicro = banc();
	sansMicro.prise.suivre({ accord: true, phase: 'avant' }, capture);
	sansMicro.prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	assert.equal(sansMicro.prise.lire().motif, 'micro-non-autorise');

	let autoriser;
	const enDemande = banc({ demanderMicro: flux => new Promise(resoudre => { autoriser = () => resoudre(flux); }) });
	enDemande.prise.suivre({ accord: true, phase: 'avant' }, capture);
	const demande = enDemande.prise.autoriserMicro();
	enDemande.prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	assert.equal(enDemande.prise.lire().motif, 'demande-en-cours');
	autoriser();
	await demande;
	assert.equal(enDemande.pistesLiberees(), 1, 'Une permission tardive libère aussitôt le micro.');
	assert.deepEqual(enDemande.appels, []);

	const recharge = banc();
	recharge.prise.suivre({ accord: true, phase: 'actif', retardDepartMs: 0 }, capture);
	assert.equal(recharge.prise.lire().motif, 'rechargement');
});

test('une capture juste après le départ manqué lance la prise préparée, une fois', async () => {
	const { prise, appels, pistesLiberees } = banc();
	await preparer({ prise });
	prise.suivre({ accord: true, phase: 'actif', retardDepartMs: 3000 }, capture);
	assert.equal(prise.lire().etat, 'enregistrement');
	prise.suivre({ accord: true, phase: 'actif', retardDepartMs: 3200 }, evenement('passage-demarre'));
	prise.suivre({ accord: true, phase: 'actif', retardDepartMs: 3400 }, capture);
	assert.deepEqual(appels, ['start'], 'L’événement et la capture qui suivent ne relancent pas la prise.');
	assert.equal(pistesLiberees(), 0);
});

test('une capture tardive ou sans heure de départ ne démarre pas le micro préparé', async () => {
	for (const retardDepartMs of [RETARD_DEPART_MAX_MS + 1, null, undefined]) {
		const { prise, appels, pistesLiberees } = banc();
		await preparer({ prise });
		prise.suivre({ accord: true, phase: 'actif', retardDepartMs }, capture);
		prise.suivre({ accord: true, phase: 'actif', retardDepartMs: 0 }, evenement('passage-demarre'));
		assert.deepEqual(appels, [], `retard ${retardDepartMs}`);
		assert.equal(pistesLiberees(), 1);
		assert.equal(prise.lire().motif, 'depart-manque');
	}
	const juste = banc();
	await preparer(juste);
	juste.prise.suivre({ accord: true, phase: 'actif', retardDepartMs: RETARD_DEPART_MAX_MS }, capture);
	assert.deepEqual(juste.appels, ['start'], 'La borne est incluse.');
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

test('le diagnostic exige un contexte sécurisé, MediaRecorder et un format pris en charge', () => {
	const Enregistreur = { isTypeSupported: format => format === 'audio/webm;codecs=opus' };
	const demanderMicro = () => {};
	assert.equal(diagnostiquer({ securise: true, demanderMicro, Enregistreur }), null);
	assert.equal(diagnostiquer({ securise: false, demanderMicro, Enregistreur }), 'page-non-securisee');
	assert.equal(diagnostiquer({ securise: true, demanderMicro }), 'navigateur-incompatible');
	assert.equal(diagnostiquer({ securise: true, Enregistreur }), 'navigateur-incompatible');
	assert.equal(diagnostiquer({ securise: true, demanderMicro, Enregistreur: { isTypeSupported: () => false } }), 'format-absent');
});

test('un micro coupé avant le départ ramène à l’accord, et le micro se redemande', async () => {
	const b = banc();
	await preparer(b);
	b.piste.couper();
	assert.equal(b.prise.lire().etat, 'accord');
	assert.equal(b.prise.lire().motif, 'micro-coupe');
	assert.equal(b.pistesLiberees(), 1);
	await b.prise.autoriserMicro();
	assert.equal(b.prise.lire().etat, 'micro');
	assert.equal(b.demandes(), 2);
});

test('un micro coupé pendant la prise propose le fichier partiel comme interrompu', async () => {
	for (const arretLeve of [false, true]) {
		const b = banc({ arretLeve });
		await preparer(b);
		b.prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
		b.recorder().fragment('début');
		b.piste.couper();
		// Un navigateur qui a déjà arrêté l’appareil peut lever : les fragments reçus restent proposés.
		if (!arretLeve) b.recorder().livrer('');
		assert.equal(b.prise.lire().etat, 'pret');
		assert.equal(b.prise.lire().motif, 'interrompu');
		assert.equal(await b.prise.lire().fichier.text(), 'début');
		assert.equal(b.resultats.length, 1);
		b.recorder().livrer('tardif');
		assert.equal(b.resultats.length, 1, 'Une seule conclusion par prise.');
	}
});

test('une erreur pendant la prise assemble les fragments reçus, et une prise vide reste un échec', async () => {
	const partielle = banc();
	await preparer(partielle);
	partielle.prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	partielle.recorder().fragment('voix');
	partielle.recorder().onerror();
	assert.equal(partielle.prise.lire().etat, 'pret');
	assert.equal(partielle.prise.lire().motif, 'interrompu');
	assert.equal(await partielle.resultats[0].fichier.text(), 'voix');

	const vide = banc();
	await preparer(vide);
	vide.prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	vide.prise.suivre({ accord: true, phase: 'termine' }, evenement('passage-termine'));
	vide.recorder().livrer('');
	assert.equal(vide.prise.lire().etat, 'echec');
	assert.deepEqual(vide.resultats, [{ echec: true, motif: 'capture-vide' }]);
	vide.prise.suivre({ accord: true, phase: 'termine' }, capture);
	assert.equal(vide.prise.lire().etat, 'echec', 'Un rendu suivant n’efface pas l’échec.');
});

test('le démontage arrête la prise en cours et laisse l’appareil livrer le fichier', async () => {
	const { prise, appels, resultats, pistesLiberees, recorder } = banc();
	await preparer({ prise });
	prise.suivre({ accord: true, phase: 'actif' }, evenement('passage-demarre'));
	prise.detruire();
	assert.deepEqual(appels, ['start', 'stop']);
	assert.equal(pistesLiberees(), 1);
	recorder().livrer();
	assert.equal(resultats.length, 1);
	assert.equal(resultats[0].motif, 'interrompu');
	assert.equal(await resultats[0].fichier.text(), 'extrait');
});

test('une capture après coupure reprend une prise en pause sans relancer une prise arrêtée', async () => {
	const { prise, appels, recorder } = banc();
	await preparer({ prise });
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

test('le nom du fichier porte le titre nettoyé, la date du Passage et l’extension du format', () => {
	const webm = { type: 'audio/webm;codecs=opus' }, mp4 = { type: 'audio/mp4' };
	const depart = new Date(2026, 10, 24, 21, 30).toISOString();
	assert.equal(nomFichier('  Les / Lacs : du * Connemara ', depart, webm), 'Les Lacs du Connemara - 2026-11-24.webm');
	assert.equal(nomFichier('', null, mp4, new Date(2026, 0, 5, 12).getTime()), 'Passage - 2026-01-05.m4a');
});

// Fenêtre factice : MediaRecorder, micro, verrou d’écran et pagehide.
function fenetre({ securise = true, wakeLock = true, livraison = 'immediate' } = {}) {
	const journal = { demandes: 0, pistes: 0, appareils: [], pisteCourante: null, verrous: [], demandesVerrou: 0 };
	class MediaRecorderFactice {
		static isTypeSupported(format) { return format === 'audio/webm;codecs=opus'; }
		constructor() { this.appels = []; journal.appareils.push(this); }
		start() { this.appels.push('start'); }
		pause() { this.appels.push('pause'); }
		resume() { this.appels.push('resume'); }
		stop() {
			this.appels.push('stop');
			if (livraison === 'immediate') this.livrer('voix');
		}
		livrer(texte) {
			if (texte) this.ondataavailable?.({ data: new Blob([texte]) });
			this.onstop?.();
		}
	}
	const vueFenetre = Object.assign(new EventTarget(), {
		isSecureContext: securise,
		navigator: {
			mediaDevices: { getUserMedia: async contraintes => {
				assert.deepEqual(contraintes, { audio: true });
				journal.demandes++;
				journal.pisteCourante = creerPiste(journal);
				const piste = journal.pisteCourante;
				return { getTracks: () => [piste] };
			} },
		},
		MediaRecorder: MediaRecorderFactice,
	});
	if (wakeLock) vueFenetre.navigator.wakeLock = { request: async type => {
		assert.equal(type, 'screen');
		journal.demandesVerrou++;
		const sentinelle = Object.assign(new EventTarget(), { libere: false });
		sentinelle.release = async () => {
			if (sentinelle.libere) return;
			sentinelle.libere = true;
			sentinelle.dispatchEvent(new Event('release'));
		};
		journal.verrous.push(sentinelle);
		return sentinelle;
	} };
	const actifs = () => journal.verrous.filter(v => !v.libere).length;
	return { journal, vueFenetre, actifs };
}

const visibles = (zone, texte) => zone.querySelectorAll('button').filter(b => b.textContent === texte && b.isConnected
	&& !b.hidden && !b.parentNode.hidden);
const lien = zone => zone.querySelectorAll('a').find(a => a.textContent === 'Télécharger mon Enregistrement');
const statuts = zone => zone.querySelectorAll('[role="status"]').filter(s => s.className.includes('karaoke__annonce-enregistrement'));

function suivreUrls(t) {
	const urls = { creees: [], revoquees: [] };
	let numero = 0;
	t.mock.method(URL, 'createObjectURL', () => { const url = `blob:enregistrement-${++numero}`; urls.creees.push(url); return url; });
	t.mock.method(URL, 'revokeObjectURL', url => urls.revoquees.push(url));
	return urls;
}

function monterManette(document) {
	const b = creerBanc(manette, 'JOUEUR', document);
	b.connecter('synchronisee');
	return b;
}

const appele = () => ({ ...passage('p'), etat: 'APPELE', etapeId: 'etape', echeanceAppelLe: Date.now() + 30000 });
const commun = { enregistrementAutorise: true, mesAccordsEnregistrement: ['p'] };
const demarreLe = new Date(2026, 10, 24, 22).toISOString();
const enCours = () => ({ ...appele(), etat: 'EN_COURS', demarreLe });

async function lancerPrise(b) {
	b.module.afficher(vue({ ...commun, courant: appele() }), capture);
	visibles(b.principale, 'Autoriser le micro')[0].click();
	await terminerMicrotaches();
	b.module.afficher(vue({ ...commun, courant: enCours() }), evenement('passage-demarre'));
	await terminerMicrotaches();
}

test('Manette : l’accord part en PUT idempotent, puis le micro n’est demandé qu’au geste', async t => {
	const document = creerDocument(), { journal, vueFenetre } = fenetre();
	document.defaultView = vueFenetre;
	const b = monterManette(document);
	t.after(() => b.module.demonter());
	const attente = { enregistrementAutorise: true, mesAccordsEnregistrement: [], mesPassagesEnAttente: [passage('p')] };
	b.module.afficher(vue(attente), capture);
	assert.equal(visibles(b.principale, 'Autoriser le micro').length, 0);
	const [enregistrer] = visibles(b.principale, 'Enregistrer mon Passage');
	assert.ok(enregistrer, 'L’option est proposée sur le ticket en attente.');
	assert.equal(enregistrer.getAttribute('aria-label'), 'Enregistrer mon Passage : Titre de démonstration');
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

	b.module.afficher(vue({ ...attente, mesAccordsEnregistrement: ['p'] }), evenement('accord-enregistrement-modifie'));
	const [retirer] = visibles(b.principale, 'Retirer mon accord');
	assert.equal(retirer.getAttribute('aria-label'), 'Retirer mon accord : Titre de démonstration');
	const [micro] = visibles(b.principale, 'Autoriser le micro');
	assert.ok(micro, 'Le micro se demande après l’accord.');
	assert.equal(micro.getAttribute('aria-label'), 'Autoriser le micro : Titre de démonstration');
	micro.click();
	await terminerMicrotaches();
	assert.equal(journal.demandes, 1);
	assert.match(b.principale.textContent, /Micro prêt/);

	b.module.afficher(vue({ ...attente, enregistrementAutorise: false }), evenement('autorisation-enregistrement-modifiee'));
	await terminerMicrotaches();
	assert.equal(visibles(b.principale, 'Enregistrer mon Passage').length, 0);
	assert.equal(visibles(b.principale, 'Retirer mon accord').length, 0);
	assert.equal(visibles(b.principale, 'Autoriser le micro').length, 0);
	assert.equal(journal.pistes, 1);
	assert.equal(journal.appareils.every(a => a.appels.length === 0), true);
});

test('Manette : une seule zone de statut annonce le micro, la prise et le fichier prêt', async t => {
	const document = creerDocument(), { vueFenetre } = fenetre();
	document.defaultView = vueFenetre;
	suivreUrls(t);
	const b = monterManette(document);
	t.after(() => b.module.demonter());
	const attente = { ...commun, mesPassagesEnAttente: [passage('p')] };
	b.module.afficher(vue(attente), capture);
	const [zone] = statuts(b.principale);
	assert.ok(zone, 'Une zone d’annonce est réservée à l’Enregistrement.');
	assert.equal(statuts(b.principale).length, 1);
	const commandes = b.principale.querySelector('.karaoke__enregistrement');
	assert.equal(commandes.querySelectorAll('[role="status"]').length, 0, 'Les textes près des boutons ne sont pas des zones d’annonce.');
	visibles(b.principale, 'Autoriser le micro')[0].click();
	await terminerMicrotaches();
	assert.match(zone.textContent, /Micro prêt/);
	b.module.afficher(vue({ ...commun, courant: appele() }), evenement('passage-appele'));
	b.module.afficher(vue({ ...commun, courant: enCours() }), evenement('passage-demarre'));
	await terminerMicrotaches();
	assert.match(zone.textContent, /Enregistrement en cours/);
	b.module.afficher(vue({ ...commun, courant: { ...enCours(), etat: 'TERMINE' } }), evenement('passage-termine'));
	await terminerMicrotaches();
	assert.match(zone.textContent, /Ton Enregistrement est prêt/);
	assert.equal(statuts(b.principale)[0] === zone, true, 'La zone reste la même d’un rendu à l’autre.');
	assert.equal(lien(b.principale).getAttribute('aria-label'), 'Télécharger mon Enregistrement : Titre de démonstration');
});

test('Manette : la prise suit le Passage et le fichier survit au démontage jusqu’au retrait confirmé', async t => {
	const document = creerDocument(), { journal, vueFenetre } = fenetre();
	document.defaultView = vueFenetre;
	const urls = suivreUrls(t);
	const b = monterManette(document);
	await lancerPrise(b);
	assert.match(b.principale.textContent, /Enregistrement en cours/);
	b.module.afficher(vue({ ...commun, etat: 'EN_PAUSE', courant: { ...enCours(), etat: 'EN_PAUSE' } }), evenement('manche-en-pause'));
	b.module.afficher(vue({ ...commun, courant: enCours() }), evenement('manche-reprise'));
	b.module.afficher(vue({ ...commun, courant: { ...enCours(), etat: 'TERMINE' }, mesAccordsEnregistrement: [] }),
		evenement('passage-termine'));
	await terminerMicrotaches();
	assert.deepEqual(journal.appareils[0].appels, ['start', 'pause', 'resume', 'stop']);
	assert.equal(journal.pistes, 1);
	assert.equal(lien(b.principale).href, 'blob:enregistrement-1');
	assert.equal(lien(b.principale).download, 'Titre de démonstration - 2026-11-24.webm');
	const ecoute = b.principale.querySelector('audio');
	assert.equal(ecoute.controls, true);
	assert.equal(ecoute.src, 'blob:enregistrement-1');
	assert.doesNotMatch(b.principale.textContent, /avant la fin de la Manche/);
	assert.match(b.principale.textContent, /rechargement/);

	b.module.demonter();
	assert.deepEqual(urls.revoquees, [], 'La fin de la Manche ne détruit pas le fichier.');
	assert.equal(b.principale.childElementCount, 0);

	const suivante = monterManette(document);
	t.after(() => suivante.module.demonter());
	suivante.module.afficher(vue({ mancheId: 'm2', enregistrementAutorise: true }), capture);
	assert.equal(lien(suivante.principale).href, 'blob:enregistrement-1', 'Le fichier est reproposé à la Manche suivante.');
	const [retirer] = visibles(suivante.principale, 'Retirer');
	assert.equal(retirer.getAttribute('aria-label'), 'Retirer l’Enregistrement : Titre de démonstration');
	retirer.click();
	assert.deepEqual(urls.revoquees, [], 'Le retrait attend la confirmation du Joueur.');
	const [confirmer] = visibles(suivante.principale, 'Confirmer le retrait');
	confirmer.click();
	await terminerMicrotaches();
	assert.deepEqual(urls.revoquees, ['blob:enregistrement-1']);
	assert.equal(lien(suivante.principale), undefined);
});

test('Manette : une capture qui remplace passage-demarre lance la prise si le départ est récent', async t => {
	for (const [retardMs, attendu] of [[2000, ['start']], [RETARD_DEPART_MAX_MS + 5000, []]]) {
		const document = creerDocument(), { journal, vueFenetre } = fenetre();
		document.defaultView = vueFenetre;
		const b = monterManette(document);
		t.after(() => b.module.demonter());
		b.module.afficher(vue({ ...commun, courant: appele() }), capture);
		visibles(b.principale, 'Autoriser le micro')[0].click();
		await terminerMicrotaches();
		// La coupure a fait manquer passage-demarre : la capture de resynchronisation arrive après.
		const depart = new Date(Date.now() - retardMs).toISOString();
		b.module.afficher(vue({ ...commun, courant: { ...enCours(), demarreLe: depart } }), capture);
		await terminerMicrotaches();
		assert.deepEqual(journal.appareils[0].appels, attendu, `retard de ${retardMs} ms`);
		assert.match(b.principale.textContent, attendu.length ? /Enregistrement en cours/ : /La prise n’a pas pu démarrer/);
	}
});

test('Manette : la fin de la Manche pendant la prise laisse le fichier arriver, puis pagehide le libère', async t => {
	const document = creerDocument(), { journal, vueFenetre } = fenetre({ livraison: 'differee' });
	document.defaultView = vueFenetre;
	const urls = suivreUrls(t);
	const b = monterManette(document);
	await lancerPrise(b);
	b.module.demonter();
	assert.deepEqual(journal.appareils[0].appels, ['start', 'stop']);
	journal.appareils[0].livrer('voix');
	await terminerMicrotaches();

	const suivante = monterManette(document);
	t.after(() => suivante.module.demonter());
	suivante.module.afficher(vue({ mancheId: 'm2', enregistrementAutorise: true }), capture);
	assert.equal(lien(suivante.principale).href, 'blob:enregistrement-1');
	assert.match(suivante.principale.textContent, /Cette prise a été interrompue/);
	// Une page mise en cache peut revenir telle quelle : ses liens doivent rester valides.
	const miseEnCache = new Event('pagehide');
	Object.defineProperty(miseEnCache, 'persisted', { value: true });
	vueFenetre.dispatchEvent(miseEnCache);
	assert.deepEqual(urls.revoquees, []);
	vueFenetre.dispatchEvent(new Event('pagehide'));
	assert.deepEqual(urls.revoquees, ['blob:enregistrement-1']);
});

test('Manette : une prise vide reste affichée comme échec dans « Tes Enregistrements »', async t => {
	const document = creerDocument(), { journal, vueFenetre } = fenetre({ livraison: 'differee' });
	document.defaultView = vueFenetre;
	suivreUrls(t);
	const b = monterManette(document);
	t.after(() => b.module.demonter());
	await lancerPrise(b);
	b.module.afficher(vue({ ...commun, courant: { ...enCours(), etat: 'TERMINE' } }), evenement('passage-termine'));
	journal.appareils[0].livrer('');
	await terminerMicrotaches();
	b.module.afficher(vue({ ...commun, courant: null }), capture);
	await terminerMicrotaches();
	const section = b.principale.querySelector('.karaoke__enregistrements');
	assert.equal(section.hidden, false);
	assert.match(section.textContent, /Titre de démonstration/);
	assert.match(section.textContent, /aucun son/);
	assert.equal(lien(b.principale), undefined);
});

test('Manette : le verrou d’écran couvre le micro et la prise, et revient quand la page redevient visible', async t => {
	const document = creerDocument(), { vueFenetre, actifs, journal } = fenetre();
	document.defaultView = vueFenetre;
	suivreUrls(t);
	const b = monterManette(document);
	b.module.afficher(vue({ ...commun, courant: appele() }), capture);
	await terminerMicrotaches();
	assert.equal(journal.demandesVerrou, 0, 'Aucun verrou avant le micro.');
	visibles(b.principale, 'Autoriser le micro')[0].click();
	await terminerMicrotaches();
	assert.equal(actifs(), 1);
	b.module.afficher(vue({ ...commun, courant: enCours() }), evenement('passage-demarre'));
	await terminerMicrotaches();
	assert.equal(actifs(), 1);
	assert.equal(journal.demandesVerrou, 1);

	// Le système libère le verrou quand la page passe en arrière-plan.
	document.visibilityState = 'hidden';
	await journal.verrous[0].release();
	document.dispatchEvent({ type: 'visibilitychange' });
	await terminerMicrotaches();
	assert.equal(actifs(), 0);
	document.visibilityState = 'visible';
	document.dispatchEvent({ type: 'visibilitychange' });
	await terminerMicrotaches();
	assert.equal(actifs(), 1);

	b.module.afficher(vue({ ...commun, courant: { ...enCours(), etat: 'TERMINE' } }), evenement('passage-termine'));
	await terminerMicrotaches();
	assert.equal(actifs(), 0);
	b.module.afficher(vue({ ...commun, mesPassagesEnAttente: [passage('q', 15)], mesAccordsEnregistrement: ['q'] }), capture);
	visibles(b.principale, 'Autoriser le micro')[0].click();
	await terminerMicrotaches();
	assert.equal(actifs(), 1);
	b.module.demonter();
	await terminerMicrotaches();
	assert.equal(actifs(), 0, 'Le démontage libère le verrou.');

	const sansVerrou = fenetre({ wakeLock: false });
	const autreDocument = creerDocument();
	autreDocument.defaultView = sansVerrou.vueFenetre;
	const c = monterManette(autreDocument);
	t.after(() => c.module.demonter());
	c.module.afficher(vue({ ...commun, courant: appele() }), capture);
	visibles(c.principale, 'Autoriser le micro')[0].click();
	await terminerMicrotaches();
	assert.match(c.principale.textContent, /Micro prêt/);
});

test('Manette : un micro coupé avant le départ réaffiche « Autoriser le micro »', async t => {
	const document = creerDocument(), { journal, vueFenetre } = fenetre();
	document.defaultView = vueFenetre;
	const b = monterManette(document);
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ ...commun, courant: appele() }), capture);
	visibles(b.principale, 'Autoriser le micro')[0].click();
	await terminerMicrotaches();
	assert.equal(visibles(b.principale, 'Autoriser le micro').length, 0);
	journal.pisteCourante.couper();
	await terminerMicrotaches();
	assert.equal(visibles(b.principale, 'Autoriser le micro').length, 1);
	assert.match(b.principale.textContent, /Le micro s’est coupé/);
});

test('Manette : le focus reste sur le bouton d’Enregistrement après chaque rendu', async t => {
	const document = creerDocument(), { vueFenetre } = fenetre();
	document.defaultView = vueFenetre;
	const b = monterManette(document);
	t.after(() => b.module.demonter());
	const attente = { enregistrementAutorise: true, mesAccordsEnregistrement: [], mesPassagesEnAttente: [passage('p')] };
	b.module.afficher(vue(attente), capture);
	visibles(b.principale, 'Enregistrer mon Passage')[0].focus();
	b.module.afficher(vue(attente), capture);
	const actif = () => document.activeElement;
	assert.equal(actif().getAttribute('data-action'), 'choix');
	assert.equal(actif().isConnected, true);

	actif().click();
	b.appels[0].resolve({ statut: 200, corps: { accord: true } });
	await terminerMicrotaches();
	b.module.afficher(vue({ ...attente, mesAccordsEnregistrement: ['p'] }), evenement('accord-enregistrement-modifie'));
	assert.equal(actif().getAttribute('data-action'), 'choix', 'Le focus revient au bouton une fois l’envoi reçu.');
	assert.equal(actif().textContent, 'Retirer mon accord');

	visibles(b.principale, 'Autoriser le micro')[0].focus();
	b.module.afficher(vue({ ...attente, mesAccordsEnregistrement: ['p'] }), capture);
	assert.equal(actif().getAttribute('data-action'), 'micro');
});

test('Manette : sans contexte sécurisé ni MediaRecorder, l’option n’est pas proposée et la raison est dite', async t => {
	for (const [reglage, raison] of [[{ securise: false }, /HTTPS/], [{ sansEnregistreur: true }, /navigateur/]]) {
		const document = creerDocument(), { vueFenetre } = fenetre(reglage);
		if (reglage.sansEnregistreur) delete vueFenetre.MediaRecorder;
		document.defaultView = vueFenetre;
		const b = monterManette(document);
		t.after(() => b.module.demonter());
		b.module.afficher(vue({ enregistrementAutorise: true, mesAccordsEnregistrement: [], mesPassagesEnAttente: [passage('p')] }), capture);
		assert.equal(visibles(b.principale, 'Enregistrer mon Passage').length, 0);
		const commandes = b.principale.querySelector('.karaoke__enregistrement');
		assert.equal(commandes.hidden, false);
		assert.match(commandes.textContent, raison);
	}
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
	b.module.afficher(vue({ enregistrementAutorise: false }), evenement('autorisation-enregistrement-modifiee'));
	assert.equal(visibles(b.principale, 'Autoriser les Enregistrements').length, 1);
});
