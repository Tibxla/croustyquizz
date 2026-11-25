import assert from 'node:assert/strict';
import test from 'node:test';
import { monterEnregistrements } from '../../src/main/resources/static/js/enregistrements.mjs';
import { creerGestionnaireDeMode } from '../../src/main/resources/static/js/modules-de-mode.mjs';
import * as karaoke from '../../src/main/resources/static/js/modes/karaoke/manette.mjs';
import { creerDocument, terminerMicrotaches, passage, vue } from './karaoke-dom-factice.mjs';

// « Tes Enregistrements » dans la coquille de la Manette (#208) : le module du Karaoké, une coquille
// et un module de Quiz factice partagent un faux DOM, comme dans manette.html et page.mjs.
const evenement = type => ({ source: 'evenement', type });
const capture = { source: 'capture' };

function fenetre({ livraison = 'immediate' } = {}) {
	const appareils = [];
	class MediaRecorderFactice {
		static isTypeSupported(format) { return format === 'audio/webm;codecs=opus'; }
		constructor() { appareils.push(this); }
		start() {}
		pause() {}
		resume() {}
		stop() { if (livraison === 'immediate') this.livrer('voix'); }
		livrer(texte) {
			if (texte) this.ondataavailable?.({ data: new Blob([texte]) });
			this.onstop?.();
		}
	}
	const vueFenetre = Object.assign(new EventTarget(), {
		isSecureContext: true,
		navigator: { mediaDevices: { getUserMedia: async () => {
			const piste = Object.assign(new EventTarget(), { stop() {} });
			return { getTracks: () => [piste] };
		} } },
		MediaRecorder: MediaRecorderFactice,
	});
	return { vueFenetre, appareils };
}

function suivreUrls(t) {
	const urls = { creees: [], revoquees: [] };
	t.mock.method(URL, 'createObjectURL', () => {
		const url = `blob:enregistrement-${urls.creees.length + 1}`;
		urls.creees.push(url);
		return url;
	});
	t.mock.method(URL, 'revokeObjectURL', url => urls.revoquees.push(url));
	return urls;
}

const quiz = {
	monter(zones) {
		const question = zones.principale.ownerDocument.createElement('p');
		question.textContent = 'Question du Quiz';
		zones.principale.replaceChildren(question);
		return { afficher() {}, demonter() {} };
	},
};

function monterManette(t, options) {
	const document = creerDocument();
	const { vueFenetre, appareils } = fenetre(options);
	document.defaultView = vueFenetre;
	const titre = document.createElement('h1');
	const principale = document.createElement('section');
	const zone = document.createElement('div');
	document.body.append(titre, principale, zone);
	const appels = [];
	const contexte = { soireeId: 's', acteur: { type: 'JOUEUR', identifiant: 'j' }, maintenantServeur: Date.now,
		envoyer: (...args) => new Promise(resolve => appels.push({ args, resolve })),
		resynchroniser() {}, surEtatConnexion: rappel => { rappel('synchronisee'); return () => {}; } };
	const enregistrements = monterEnregistrements(zone, { repli: () => titre });
	const modules = { './modes/karaoke/manette.mjs': karaoke, './modes/quiz/manette.mjs': quiz };
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'manette', zones: { principale }, contexte,
		charger: async chemin => modules[chemin] });
	t.after(() => { gestionnaire.demonter(); enregistrements.demonter(); });
	const afficher = async (vueAffichee, origine = capture) => {
		await gestionnaire.afficher(vueAffichee, origine);
		await terminerMicrotaches();
	};
	return { document, vueFenetre, appareils, titre, principale, zone, appels, afficher, enregistrements };
}

const accord = { enregistrementAutorise: true, mesAccordsEnregistrement: ['p'] };
const appele = () => ({ ...passage('p'), etat: 'APPELE', etapeId: 'etape', echeanceAppelLe: Date.now() + 30000 });
const enCours = () => ({ ...appele(), etat: 'EN_COURS', demarreLe: new Date(2026, 10, 24, 22).toISOString() });
const finDeManche = { modeEnCours: 'KARAOKE', karaoke: null };
const pendantLeQuiz = { modeEnCours: 'QUIZ', quiz: { question: 'Q' } };
const soireeTerminee = { modeEnCours: null, soiree: { etat: 'TERMINEE' } };

const section = b => b.zone.querySelector('section');
const annonce = b => b.zone.querySelector('[role="status"]');
const lien = racine => racine.querySelectorAll('a').find(a => a.textContent === 'Télécharger mon Enregistrement');
const boutons = (racine, texte) => racine.querySelectorAll('button').filter(b => b.textContent === texte && b.isConnected);

async function chanter(b, { terminer = true } = {}) {
	await b.afficher(vue({ ...accord, courant: appele() }));
	boutons(b.principale, 'Autoriser le micro')[0].click();
	await terminerMicrotaches();
	await b.afficher(vue({ ...accord, courant: enCours() }), evenement('passage-demarre'));
	if (terminer) await b.afficher(vue({ ...accord, courant: { ...enCours(), etat: 'TERMINE' } }), evenement('passage-termine'));
}

test('Coquille : après la dernière Manche de Karaoké, le Joueur retrouve son Enregistrement et peut le télécharger', async t => {
	const urls = suivreUrls(t);
	const b = monterManette(t);
	assert.equal(section(b).hidden, true, 'Sans fichier, la coquille ne montre rien.');
	await chanter(b);
	assert.equal(lien(b.principale).href, 'blob:enregistrement-1');
	assert.equal(section(b).hidden, true, 'Le Karaoké monté présente lui-même la liste.');
	assert.match(b.principale.textContent, /Ton Enregistrement est prêt/);

	await b.afficher(finDeManche, evenement('manche-terminee'));
	assert.equal(b.principale.childElementCount, 0, 'Le module du Karaoké est démonté.');
	assert.equal(section(b).hidden, false);
	const telecharger = lien(b.zone);
	assert.equal(telecharger.href, 'blob:enregistrement-1');
	assert.equal(telecharger.download, 'Titre de démonstration - 2026-11-24.webm');
	assert.equal(b.zone.querySelector('audio').src, 'blob:enregistrement-1');
	assert.deepEqual(urls.revoquees, [], 'La fin de la Manche ne détruit pas le fichier.');
	assert.equal(annonce(b).textContent, '', 'Le fichier déjà annoncé par le Karaoké ne l’est pas une seconde fois.');

	await b.afficher(soireeTerminee, evenement('soiree-terminee'));
	assert.equal(section(b).hidden, false, 'La fin de la Soirée laisse le fichier téléchargeable.');
	assert.equal(lien(b.zone).href, 'blob:enregistrement-1');
});

test('Coquille : pendant une Manche de Quiz, les Enregistrements restent accessibles sans toucher au module', async t => {
	suivreUrls(t);
	const b = monterManette(t);
	await chanter(b);
	await b.afficher(finDeManche, evenement('manche-terminee'));
	await b.afficher(pendantLeQuiz, evenement('manche-demarree'));
	assert.equal(b.principale.textContent, 'Question du Quiz', 'La zone du Quiz ne contient que le Quiz.');
	assert.equal(b.principale.querySelector('section'), null);
	assert.equal(section(b).hidden, false);
	assert.equal(lien(b.zone).href, 'blob:enregistrement-1');

	await b.afficher(vue({ mancheId: 'm2', enregistrementAutorise: true }), evenement('manche-demarree'));
	assert.equal(section(b).hidden, true, 'Une nouvelle Manche de Karaoké reprend la liste.');
	assert.equal(lien(b.principale).href, 'blob:enregistrement-1');
	assert.equal(b.document.querySelectorAll('a').filter(a => a.textContent === 'Télécharger mon Enregistrement'
		&& !a.parentNode.parentNode.parentNode.hidden).length, 1, 'Un seul lien visible à la fois.');
});

test('Coquille : une prise arrêtée par la fin de la Manche arrive après le démontage et s’annonce une fois', async t => {
	suivreUrls(t);
	const b = monterManette(t, { livraison: 'differee' });
	await chanter(b, { terminer: false });
	await b.afficher(finDeManche, evenement('manche-terminee'));
	assert.equal(section(b).hidden, true, 'Le fichier n’est pas encore livré.');
	b.appareils[0].livrer('voix');
	await terminerMicrotaches();
	assert.equal(section(b).hidden, false);
	assert.match(b.zone.textContent, /Cette prise a été interrompue/);
	assert.equal(annonce(b).textContent, 'Ton Enregistrement est prêt : Titre de démonstration.');
	assert.equal(b.zone.querySelectorAll('[role="status"]').length, 1, 'Une seule zone d’annonce dans la coquille.');
});

test('Coquille : le retrait demande une confirmation, révoque l’URL et rend le focus au titre de la page', async t => {
	const urls = suivreUrls(t);
	const b = monterManette(t);
	await chanter(b);
	await b.afficher(soireeTerminee, evenement('soiree-terminee'));
	const [retirer] = boutons(b.zone, 'Retirer');
	assert.equal(retirer.getAttribute('aria-label'), 'Retirer l’Enregistrement : Titre de démonstration');
	retirer.click();
	const dialogue = b.zone.querySelector('dialog');
	assert.equal(dialogue.open, true);
	assert.match(dialogue.textContent, /As-tu bien téléchargé Titre de démonstration \?/);
	assert.doesNotMatch(dialogue.textContent, /effacé de ce téléphone/, 'Le fichier téléchargé, lui, reste sur le téléphone.');
	assert.deepEqual(urls.revoquees, [], 'Le retrait attend la confirmation du Joueur.');
	boutons(dialogue, 'Annuler')[0].click();
	assert.equal(b.zone.querySelector('dialog'), null);
	assert.equal(b.document.activeElement, retirer, 'Annuler rend le focus au bouton.');

	retirer.click();
	boutons(b.zone.querySelector('dialog'), 'Confirmer le retrait')[0].click();
	await terminerMicrotaches();
	assert.deepEqual(urls.revoquees, ['blob:enregistrement-1']);
	assert.equal(section(b).hidden, true);
	assert.equal(lien(b.zone), undefined);
	assert.equal(b.document.activeElement, b.titre);
});

test('Coquille : une prise vide se retire d’un geste', async t => {
	suivreUrls(t);
	const b = monterManette(t, { livraison: 'differee' });
	await chanter(b);
	b.appareils[0].livrer('');
	await terminerMicrotaches();
	await b.afficher(finDeManche, evenement('manche-terminee'));
	assert.match(section(b).textContent, /aucun son/);
	assert.equal(lien(b.zone), undefined);
	boutons(b.zone, 'Retirer')[0].click();
	await terminerMicrotaches();
	assert.equal(section(b).hidden, true);
	assert.equal(b.zone.querySelector('dialog'), null);
});

test('Coquille : le texte ne promet pas plus que la page, et pagehide libère les fichiers', async t => {
	const urls = suivreUrls(t);
	const b = monterManette(t);
	await chanter(b);
	await b.afficher(finDeManche, evenement('manche-terminee'));
	const texte = section(b).textContent;
	assert.match(texte, /ne les écrit pas sur ton téléphone/);
	assert.match(texte, /Un rechargement ou la fermeture de la page les efface/);
	assert.doesNotMatch(texte, /restent sur ce téléphone/);
	boutons(b.zone, 'Retirer')[0].click();

	// Une page mise en cache peut revenir telle quelle : ses liens doivent rester valides.
	const miseEnCache = new Event('pagehide');
	Object.defineProperty(miseEnCache, 'persisted', { value: true });
	b.vueFenetre.dispatchEvent(miseEnCache);
	assert.deepEqual(urls.revoquees, []);
	b.vueFenetre.dispatchEvent(new Event('pagehide'));
	await terminerMicrotaches();
	assert.deepEqual(urls.revoquees, ['blob:enregistrement-1']);
	assert.equal(section(b).hidden, true);
	assert.equal(b.zone.querySelector('dialog'), null, 'La confirmation d’un fichier effacé se ferme.');
});

test('Coquille : aucun son ne part au serveur (ADR 0005)', async t => {
	suivreUrls(t);
	const b = monterManette(t);
	await chanter(b);
	await b.afficher(finDeManche, evenement('manche-terminee'));
	await b.afficher(pendantLeQuiz, evenement('manche-demarree'));
	boutons(b.zone, 'Retirer')[0].click();
	boutons(b.zone.querySelector('dialog'), 'Confirmer le retrait')[0].click();
	await terminerMicrotaches();
	for (const { args: [, chemin, corps] } of b.appels) {
		assert.equal(chemin.endsWith('/accord-enregistrement'), true, `Envoi inattendu : ${chemin}`);
		assert.doesNotThrow(() => JSON.stringify(corps));
		assert.equal(Object.values(corps ?? {}).some(valeur => valeur instanceof Blob || String(valeur).startsWith('blob:')), false);
	}
	b.enregistrements.demonter();
	assert.equal(b.zone.childElementCount, 0);
});
