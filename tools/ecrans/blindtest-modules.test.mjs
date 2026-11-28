import test from 'node:test';
import assert from 'node:assert/strict';
import { creerBanc, creerDocument, terminerMicrotaches } from './karaoke-dom-factice.mjs';
import { monter as manette } from '../../src/main/resources/static/js/modes/blindtest/manette.mjs';
import { monter as consoleBlindTest } from '../../src/main/resources/static/js/modes/blindtest/console.mjs';
import { creerEcranBlindTest } from '../../src/main/resources/static/js/modes/blindtest/ecran.mjs';

const T0 = Date.parse('2026-11-25T20:00:00Z');
const capture = { source: 'capture', type: 'capture' };
const evenement = type => ({ source: 'evenement', type });
const LIEN = 'https://cdnt-preview.dzcdn.net/extrait.mp3?hdnea=exp=1~hmac=ab';
const lectureEnCours = (positionMs, depuis = T0) => ({ etat: 'EN_LECTURE', positionMs, depuisLe: new Date(depuis).toISOString() });
const lectureEnPause = positionMs => ({ etat: 'EN_PAUSE', positionMs, depuisLe: null });
const mainDe = (joueurId, pseudo, echeance = T0 + 20000) => ({ joueurId, pseudo, echeanceLe: new Date(echeance).toISOString(), tempsRestantMs: null });
const commun = (phase, extra = {}) => ({ mancheId: 'm', etat: 'EN_COURS', etapeId: 'e1', numero: 2, total: 5, phase,
	lecture: null, main: null, partiesTrouvees: [], revelation: null, ...extra });
const joueurs = [{ id: 'lea', pseudo: 'Léa', connecte: true }, { id: 'sam', pseudo: 'Sam', connecte: true }];
const vueManette = (phase, extra = {}) => ({ modeEnCours: 'BLIND_TEST', joueurId: 'lea', joueurs, dernierBuzz: null,
	blindtest: { ...commun(phase), peutBuzzer: false, bloque: false, aLaMain: false, champsDemandes: ['TITRE', 'ARTISTE'],
		monBrouillon: null, ...extra } });
const vueSalle = (phase, extra = {}) => ({ modeEnCours: 'BLIND_TEST', joueurs, dernierBuzz: null,
	blindtest: { ...commun(phase), lienExtrait: ['EXTRAIT', 'PROPOSITION'].includes(phase) ? LIEN : null, ...extra } });
const vueConsole = (phase, extra = {}) => ({ modeEnCours: 'BLIND_TEST', joueurs, dernierBuzz: null,
	blindtest: { ...commun(phase), reponse: { titre: 'Halo', artiste: 'Beyoncé', pochette: null }, joueursBloques: [], ...extra } });
const bouton = (zone, texte) => zone.querySelectorAll('button').find(b => b.textContent === texte);
const texte = zone => zone.textContent;

function horloges() {
	const intervalles = new Map(), delais = new Map();
	let numero = 0;
	return {
		intervalles, delais,
		intervalle: { programmer: (f, ms) => { intervalles.set(++numero, { f, ms }); return numero; }, annuler: id => intervalles.delete(id) },
		programmerDelai: (f, ms) => { delais.set(++numero, { f, ms }); return numero; },
		annulerDelai: id => delais.delete(id),
		declencherDelais() { const liste = [...delais.values()]; delais.clear(); for (const { f } of liste) f(); },
	};
}

function bancManette(document = creerDocument()) {
	const h = horloges();
	let identifiants = 0;
	const banc = creerBanc((zones, contexte) => manette(zones, contexte,
		{ ...h, creerUuid: () => `envoi-${++identifiants}` }), 'JOUEUR', document);
	banc.contexte.maintenantServeur = () => T0;
	return { ...banc, ...h };
}

function bancEcran(document = creerDocument(), { sonActive = true } = {}) {
	const journal = [], signaux = [], abonnes = new Set();
	let position = 0, refus = null;
	const audio = {
		termine: () => false,
		charger: lien => journal.push(`charger ${lien}`),
		lire: async () => { journal.push('lire'); if (refus) throw new DOMException('refus', refus); },
		pause: () => journal.push('pause'), position: () => position,
		aller: ms => { position = ms; journal.push(`aller ${ms}`); }, arreter: () => journal.push('arrêter'),
	};
	const h = horloges();
	const etatSon = { sonDebloque: sonActive };
	const banc = creerBanc((zones, contexte) => {
		Object.assign(contexte, {
			maintenantServeur: () => T0 + 3000,
			signalerSonDeManche: async actif => { signaux.push(actif); return actif; },
			audioSalle: { etat: () => etatSon, surChangement: rappel => { abonnes.add(rappel); return () => abonnes.delete(rappel); } },
		});
		return creerEcranBlindTest(zones, contexte, { audio, verrous: undefined, intervalle: h.intervalle });
	}, 'ECRAN', document);
	return { ...banc, ...h, journal, signaux, abonnes, etatSon, echouer: nom => { refus = nom; } };
}

test('les trois écrans partagent un lien CSS du mode et libèrent zones, abonnements et minuteries', async () => {
	const document = creerDocument(), talon = document.createElement('div');
	talon.textContent = 'DEMO01';
	document.body.append(talon);
	const bancs = [bancManette(document), creerBanc(consoleBlindTest, 'ANIMATEUR', document), bancEcran(document)];
	assert.equal(document.head.querySelectorAll('link[data-mode=blindtest]').length, 1);
	assert.equal(document.head.querySelector('link[data-mode=blindtest]').getAttribute('href'), '/css/blindtest.css');
	bancs[0].module.afficher(vueManette('PROPOSITION', { main: mainDe('lea', 'Léa'), aLaMain: true,
		lecture: lectureEnPause(4000) }), capture);
	bancs[1].module.afficher(vueConsole('EXTRAIT', { lecture: lectureEnCours(0) }), capture);
	bancs[2].module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(0) }), capture);
	await terminerMicrotaches();
	assert.equal(bancs[0].intervalles.size, 1, 'Le compte à rebours de la saisie tourne.');
	assert.equal(bancs[2].intervalles.size, 1, 'Le temps d’extrait tourne.');
	for (const b of bancs) {
		b.module.demonter();
		assert.equal(b.principale.childElementCount, 0);
		assert.equal(b.laterale.childElementCount, 0);
		assert.equal(b.abonnee(), false);
	}
	assert.equal(bancs[0].intervalles.size + bancs[2].intervalles.size, 0);
	assert.equal(document.head.childElementCount, 0);
	assert.equal(talon.textContent, 'DEMO01');
});

test('Manette : le Buzz suit peutBuzzer et la connexion, nomme son Morceau et ne se réessaie qu’à la main', async () => {
	const b = bancManette();
	b.module.afficher(vueManette('EXTRAIT', { lecture: lectureEnCours(0), peutBuzzer: true }), capture);
	const buzz = bouton(b.principale, 'Buzzer');
	assert.equal(buzz.disabled, true, 'Rien n’est envoyé tant que la vue n’est pas synchronisée.');
	b.connecter('synchronisee');
	assert.equal(buzz.disabled, false);

	buzz.click();
	assert.equal(buzz.disabled, true, 'Un seul Buzz à la fois.');
	const [methode, chemin, corps, options] = b.appels[0].args;
	assert.deepEqual([methode, chemin, corps, options.annoncer], ['POST', '/api/soirees/s/buzz',
		{ envoiId: 'envoi-1', mancheId: 'm', etapeId: 'e1' }, false]);
	b.appels[0].reject(new TypeError('réseau'));
	await terminerMicrotaches();
	assert.equal(b.captures(), 1, 'Une confirmation inconnue demande une capture.');
	assert.equal(b.appels.length, 1, 'Aucun nouvel essai automatique.');
	assert.match(texte(b.principale), /Confirmation à vérifier/);
	bouton(b.principale, 'Réessayer le Buzz').click();
	assert.equal(b.appels[1].args[2].envoiId, 'envoi-1', 'Le nouvel essai reprend le même envoi.');
	b.appels[1].resolve({ statut: 409, corps: { code: 'BUZZ_NON_AUTORISE', detail: 'Le Buzz n’est pas ouvert pour ce Joueur.' } });
	await terminerMicrotaches();
	assert.match(texte(b.principale), /Le Buzz n’est pas ouvert pour ce Joueur/);
	assert.ok(bouton(b.principale, 'Buzzer'));

	b.module.afficher(vueManette('EXTRAIT', { lecture: lectureEnCours(0), peutBuzzer: false, bloque: true }), capture);
	assert.equal(bouton(b.principale, 'Buzzer').hidden, true, 'Un Joueur bloqué ne voit plus le Buzz.');
	assert.match(texte(b.principale), /Proposition fausse : tu es bloqué sur ce Morceau/);
	b.module.demonter();
});

test('Manette rechargée en pleine saisie : champs demandés, brouillon et échéance du serveur', async () => {
	const b = bancManette();
	b.connecter('synchronisee');
	b.contexte.maintenantServeur = () => T0 + 7200;
	b.module.afficher(vueManette('PROPOSITION', { lecture: lectureEnPause(9000), main: mainDe('lea', 'Léa'), aLaMain: true,
		champsDemandes: ['TITRE'], monBrouillon: { numero: 3, titre: 'Hal', artiste: '' },
		partiesTrouvees: [{ partie: 'ARTISTE', valeur: 'Beyoncé', joueurId: 'sam', pseudo: 'Sam', points: 30 }] }), capture);
	const [titre, artiste] = b.principale.querySelectorAll('input');
	assert.equal(titre.value, 'Hal');
	assert.equal(titre.parentNode.hidden, false);
	assert.equal(artiste.parentNode.hidden, true, 'Une partie trouvée n’est plus demandée.');
	assert.match(texte(b.principale), /13 s pour répondre/);
	assert.match(texte(b.principale), /Artiste trouvé par Sam : Beyoncé/);
	assert.notEqual(b.document.activeElement, titre, 'Une capture ne déplace pas le focus.');

	titre.value = 'Halo';
	titre.dispatchEvent({ type: 'input' });
	assert.equal(b.appels.length, 0, 'Le brouillon attend le délai d’espacement.');
	b.declencherDelais();
	await terminerMicrotaches();
	const [methode, chemin, corps, options] = b.appels[0].args;
	assert.deepEqual([methode, chemin, corps, options.annoncer], ['PUT', '/api/soirees/s/manches/m/morceaux/e1/brouillon',
		{ numero: 4, titre: 'Halo', artiste: '' }, false]);

	b.contexte.maintenantServeur = () => T0 + 15100;
	[...b.intervalles.values()][0].f();
	assert.match(texte(b.principale), /5 s pour répondre/);
	b.module.demonter();
	assert.equal(b.appels[0].args[3].signal.aborted, true, 'Le démontage annule les requêtes du module.');
});

test('Manette : le Buzz accepté donne le focus à la saisie ; Valider attend la vue pour le verdict', async () => {
	const b = bancManette();
	b.connecter('synchronisee');
	b.module.afficher(vueManette('EXTRAIT', { lecture: lectureEnCours(0), peutBuzzer: true }), capture);
	b.module.afficher(vueManette('PROPOSITION', { lecture: lectureEnPause(5000), main: mainDe('lea', 'Léa'), aLaMain: true }),
		evenement('buzz-recu'));
	await terminerMicrotaches();
	const [titre, artiste] = b.principale.querySelectorAll('input');
	assert.equal(b.document.activeElement, titre);
	titre.value = '';
	artiste.value = 'Beyoncé';

	const valider = bouton(b.principale, 'Valider');
	b.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	assert.equal(valider.disabled, true);
	assert.deepEqual(b.appels[0].args.slice(0, 3), ['POST', '/api/soirees/s/manches/m/morceaux/e1/proposition',
		{ envoiId: 'envoi-1', titre: '', artiste: 'Beyoncé' }]);
	b.appels[0].resolve({ statut: 200, corps: { titre: 'FAUX', artiste: 'JUSTE', pointsGagnes: 30 } });
	await terminerMicrotaches();
	assert.doesNotMatch(texte(b.principale), /trouvé|bloqué/, 'L’accusé ne suffit pas à annoncer le verdict.');
	assert.equal(valider.disabled, true, 'Une Proposition jugée ne se renvoie pas en attendant la vue.');

	b.module.afficher(vueManette('EXTRAIT', { lecture: lectureEnCours(5000), bloque: true,
		partiesTrouvees: [{ partie: 'ARTISTE', valeur: 'Beyoncé', joueurId: 'lea', pseudo: 'Léa', points: 30 }] }),
	evenement('proposition-jugee'));
	assert.match(texte(b.principale), /Artiste trouvé, \+30 points\. Le reste est faux : tu es bloqué sur ce Morceau\./);
	assert.equal(b.principale.querySelector('form').hidden, true);
	b.module.demonter();
});

test('Manette : Valider est refusé pendant la pause, la saisie reste possible', () => {
	const b = bancManette();
	b.connecter('synchronisee');
	b.module.afficher(vueManette('PROPOSITION', { etat: 'EN_PAUSE', lecture: lectureEnPause(5000), aLaMain: true,
		main: { joueurId: 'lea', pseudo: 'Léa', echeanceLe: null, tempsRestantMs: 11000 } }), capture);
	assert.equal(bouton(b.principale, 'Valider').disabled, true);
	assert.equal(b.principale.querySelectorAll('input')[0].disabled, false);
	assert.match(texte(b.principale), /11 s pour répondre/);
	assert.equal(b.intervalles.size, 0, 'Un temps gelé ne fait rien tourner.');
	b.module.demonter();
});

test('Écran rechargé : l’extrait reprend à la bonne position, ou reste en pause, sans animation', async () => {
	const b = bancEcran();
	b.module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(9000) }), capture);
	await terminerMicrotaches();
	assert.deepEqual(b.journal, [`charger ${LIEN}`, 'aller 12000', 'lire']);
	assert.deepEqual(b.signaux, [true], 'Le son de Manche est réservé avant de jouer.');
	assert.match(texte(b.principale), /18 s/);
	assert.equal(b.principale.querySelectorAll('.blindtest--arrivee').length, 0);

	const pause = bancEcran();
	pause.module.afficher(vueSalle('PROPOSITION', { lecture: lectureEnPause(14000), main: mainDe('lea', 'Léa') }), capture);
	await terminerMicrotaches();
	assert.deepEqual(pause.journal, [`charger ${LIEN}`, 'aller 14000', 'pause']);
	assert.match(texte(pause.principale), /Léa a la main/);
	assert.equal(pause.principale.querySelector('.blindtest__main').classList.contains('blindtest--arrivee'), false);
	b.module.demonter();
	pause.module.demonter();
});

test('Écran : le Buzz, la partie trouvée et la Révélation s’animent sur un événement', async () => {
	const b = bancEcran();
	b.module.afficher(vueSalle('PROPOSITION', { lecture: lectureEnPause(5000), main: mainDe('lea', 'Léa') }), evenement('buzz-recu'));
	await terminerMicrotaches();
	assert.equal(b.principale.querySelector('.blindtest__main').classList.contains('blindtest--arrivee'), true);
	b.module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(5000, T0 + 3000),
		partiesTrouvees: [{ partie: 'ARTISTE', valeur: 'Beyoncé', joueurId: 'lea', pseudo: 'Léa', points: 30 }] }),
	evenement('proposition-jugee'));
	await terminerMicrotaches();
	assert.equal(texte(b.laterale.querySelector('.blindtest__parties')), 'Artiste trouvé par Léa : Beyoncé');
	assert.equal(b.laterale.querySelector('.blindtest__parties').classList.contains('blindtest--arrivee'), true);
	b.module.afficher(vueSalle('REVELATION', { revelation: { titre: 'Halo', artiste: 'Beyoncé',
		pochette: 'https://e-cdns-images.dzcdn.net/images/cover/halo.jpg' } }), evenement('morceau-revele'));
	await terminerMicrotaches();
	const revelation = b.principale.querySelector('.blindtest__revelation');
	assert.equal(revelation.hidden, false);
	assert.equal(revelation.classList.contains('blindtest--arrivee'), true);
	assert.match(texte(revelation), /HaloBeyoncé/);
	assert.equal(revelation.querySelector('img').getAttribute('alt'), 'Pochette de Halo');
	assert.equal(b.journal.at(-1), 'lire', 'La Révélation laisse l’extrait finir, pour l’ambiance.');
	assert.equal(b.signaux.includes(false), false, 'La Musique d’attente reste muette jusqu’à la fin de la Manche.');
	b.module.demonter();
});

test('Écran : le démontage arrête le son puis rend le son de Manche ; un son activé plus tard relance la lecture', async () => {
	const b = bancEcran(undefined, { sonActive: false });
	b.module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(0) }), capture);
	await terminerMicrotaches();
	assert.equal(b.journal.includes('lire'), false, 'Sans son activé, la fenêtre ne joue pas.');
	b.etatSon.sonDebloque = true;
	for (const rappel of b.abonnes) rappel(b.etatSon);
	await terminerMicrotaches();
	assert.equal(b.journal.at(-1), 'lire');
	b.module.demonter();
	assert.equal(b.journal.at(-1), 'arrêter');
	assert.equal(b.signaux.at(-1), false);
	assert.equal(b.abonnes.size, 0);
});

test('Console : réponse attendue, qui a la main, parties trouvées et Joueurs bloqués', () => {
	const b = creerBanc(consoleBlindTest, 'ANIMATEUR');
	b.contexte.maintenantServeur = () => T0 + 4000;
	b.module.afficher(vueConsole('PROPOSITION', { lecture: lectureEnPause(12000), main: mainDe('sam', 'Sam'),
		joueursBloques: ['lea'], partiesTrouvees: [{ partie: 'ARTISTE', valeur: 'Beyoncé', joueurId: 'lea', pseudo: 'Léa', points: 30 }] }),
	capture);
	const contenu = texte(b.principale);
	assert.match(contenu, /Morceau 2 sur 5/);
	assert.match(contenu, /Réponse attendueHaloBeyoncé/);
	assert.match(contenu, /Sam a la main/);
	assert.match(contenu, /16 s pour répondre/);
	assert.match(contenu, /Extrait en pause à 12 s/);
	assert.match(contenu, /Artiste trouvé par Léa : Beyoncé/);
	assert.equal(texte(b.laterale.querySelector('ul')), 'Léa');
	b.module.demonter();
});

function bancConsole() {
	let cles = 0;
	const b = creerBanc((zones, contexte) => consoleBlindTest(zones, contexte, { creerUuid: () => `cle-${++cles}` }), 'ANIMATEUR');
	b.contexte.maintenantServeur = () => T0;
	return b;
}

test('Console : le module pilote sa Manche en nommant le Morceau affiché', async () => {
	const b = bancConsole();
	b.module.afficher(vueConsole('EXTRAIT', { lecture: lectureEnCours(0) }), capture);
	const passer = bouton(b.principale, 'Révéler le Morceau');
	assert.equal(passer.disabled, true, 'Rien ne part avant la synchronisation.');
	b.connecter('synchronisee');
	assert.equal(passer.disabled, false);
	assert.equal(passer.classList.contains('bouton--action'), true);

	passer.click();
	assert.equal(passer.disabled, true, 'Une commande à la fois.');
	const [methode, chemin, corps, options] = b.appels[0].args;
	assert.deepEqual([methode, chemin, corps, options.entetes], ['POST', '/api/soirees/s/manches/m/commandes',
		{ action: 'PASSER_ETAPE', etapeId: 'e1' }, { 'Idempotency-Key': 'cle-1' }]);
	b.appels[0].resolve({ statut: 200, corps: {} });
	await terminerMicrotaches();

	b.module.afficher(vueConsole('REVELATION', { revelation: { titre: 'Halo', artiste: 'Beyoncé', pochette: null } }),
		evenement('morceau-revele'));
	assert.ok(bouton(b.principale, 'Morceau suivant'));
	b.module.afficher(vueConsole('REVELATION', { numero: 5, revelation: { titre: 'Halo', artiste: 'Beyoncé', pochette: null } }),
		capture);
	assert.ok(bouton(b.principale, 'Finir la Manche'), 'Après le dernier Morceau, passer termine la Manche.');
	b.module.demonter();
});

test('Console : pause et reprise, « Passer » refusé pendant la pause, nouvel essai manuel avec la même clé', async () => {
	const b = bancConsole();
	b.connecter('synchronisee');
	b.module.afficher(vueConsole('EXTRAIT', { lecture: lectureEnCours(0) }), capture);
	bouton(b.principale, 'Pause').click();
	assert.deepEqual(b.appels[0].args[2], { action: 'METTRE_EN_PAUSE', etapeId: 'e1' });
	b.appels[0].reject(new TypeError('réseau'));
	await terminerMicrotaches();
	assert.equal(b.captures(), 1, 'Une confirmation inconnue demande une capture.');
	assert.equal(b.appels.length, 1, 'Aucun nouvel essai automatique.');
	bouton(b.principale, 'Pause').click();
	assert.equal(b.appels[1].args[3].entetes['Idempotency-Key'], 'cle-1', 'Le même geste reprend la même clé.');
	b.appels[1].resolve({ statut: 200, corps: {} });
	await terminerMicrotaches();

	b.module.afficher(vueConsole('EXTRAIT', { etat: 'EN_PAUSE', lecture: lectureEnPause(4000) }), evenement('manche-en-pause'));
	assert.equal(bouton(b.principale, 'Révéler le Morceau').disabled, true);
	bouton(b.principale, 'Reprendre').click();
	assert.deepEqual(b.appels[2].args[2], { action: 'REPRENDRE', etapeId: 'e1' });
	assert.equal(b.appels[2].args[3].entetes['Idempotency-Key'], 'cle-2');
	b.module.demonter();
	assert.equal(b.appels[2].args[3].signal.aborted, true, 'Le démontage annule la commande en cours.');
});

const propositionLea = { propositionId: 'p-lea', joueurId: 'lea', pseudo: 'Léa', points: 70,
	titre: { saisie: 'halo', verdict: 'JUSTE', arbitre: false }, artiste: { saisie: 'Beyonse', verdict: 'FAUX', arbitre: false } };

test('Console : en Pilotage Manuel, l’Animateur accorde un champ jugé faux, avec la même clé après une panne', async () => {
	const b = bancConsole();
	const vue = (extra = {}) => ({ ...vueConsole('EXTRAIT', { lecture: lectureEnCours(0), joueursBloques: ['lea'],
		propositions: [propositionLea], ...extra }), soiree: { pilotage: 'MANUEL' } });
	b.module.afficher(vue(), capture);
	assert.match(texte(b.principale), /Léa · \+70 points/);
	assert.match(texte(b.principale), /Artiste : « Beyonse », faux/);
	const accorder = bouton(b.principale, 'Accorder');
	assert.equal(accorder.disabled, true, 'Rien ne part avant la synchronisation.');
	assert.ok(bouton(b.principale, 'Refuser'), 'Un champ juste peut être refusé.');
	b.connecter('synchronisee');

	bouton(b.principale, 'Accorder').click();
	const [methode, chemin, corps, options] = b.appels[0].args;
	assert.deepEqual([methode, chemin, corps, options.entetes], ['POST', '/api/soirees/s/manches/m/morceaux/e1/arbitrage',
		{ propositionId: 'p-lea', partie: 'ARTISTE', verdict: 'JUSTE' }, { 'Idempotency-Key': 'cle-1' }]);
	assert.equal(bouton(b.principale, 'Accorder').disabled, true, 'Une commande à la fois.');
	b.appels[0].reject(new TypeError('réseau'));
	await terminerMicrotaches();
	bouton(b.principale, 'Accorder').click();
	assert.equal(b.appels[1].args[3].entetes['Idempotency-Key'], 'cle-1', 'Le même geste reprend la même clé.');
	b.appels[1].resolve({ statut: 200, corps: { modifie: true } });
	await terminerMicrotaches();

	b.module.afficher(vue({ phase: 'REVELATION', lecture: null, joueursBloques: [],
		propositions: [{ ...propositionLea, points: 100, artiste: { saisie: 'Beyonse', verdict: 'JUSTE', arbitre: true } }] }),
	evenement('morceau-revele'));
	assert.match(texte(b.principale), /Artiste : « Beyonse », juste \(arbitré\)/);
	assert.equal(bouton(b.principale, 'Accorder'), undefined);
	b.module.demonter();
});

test('Console : en Pilotage Automatique, aucun geste d’arbitrage', () => {
	const b = bancConsole();
	b.connecter('synchronisee');
	b.module.afficher({ ...vueConsole('EXTRAIT', { lecture: lectureEnCours(0), propositions: [propositionLea] }),
		soiree: { pilotage: 'AUTOMATIQUE' } }, capture);
	assert.match(texte(b.principale), /le verdict de la correction est définitif/);
	assert.equal(bouton(b.principale, 'Accorder'), undefined);
	assert.equal(bouton(b.principale, 'Refuser'), undefined);
	b.module.demonter();
});

test('Écran : un verdict renversé est annoncé et animé sur son événement, gardé sans animation sur une capture', async () => {
	const b = bancEcran();
	const arbitrage = { partie: 'ARTISTE', joueurId: 'lea', pseudo: 'Léa', verdict: 'JUSTE' };
	b.module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(0), arbitrage: null }), capture);
	const annonce = b.principale.querySelector('.blindtest__arbitrage');
	assert.equal(annonce.hidden, true);
	b.module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(0), arbitrage }), evenement('verdict-renverse'));
	assert.equal(annonce.hidden, false);
	assert.equal(annonce.textContent, 'L’Animateur accorde l’artiste à Léa.');
	assert.equal(annonce.classList.contains('blindtest--arrivee'), true);
	b.module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(0), arbitrage }), capture);
	assert.equal(annonce.hidden, false);
	assert.equal(annonce.classList.contains('blindtest--arrivee'), false);
	await terminerMicrotaches();
	b.module.demonter();
});

test('Console : « Terminer la Manche » demande confirmation', () => {
	const b = bancConsole();
	b.connecter('synchronisee');
	b.module.afficher(vueConsole('EXTRAIT', { lecture: lectureEnCours(0) }), capture);
	const terminer = bouton(b.principale, 'Terminer la Manche');
	terminer.click();
	const dialogue = b.principale.querySelector('dialog');
	assert.equal(dialogue.open, true);
	assert.equal(b.appels.length, 0);
	bouton(dialogue, 'Annuler').click();
	assert.equal(dialogue.open, false);
	assert.equal(b.document.activeElement, terminer, 'Le focus revient au déclencheur.');
	terminer.click();
	bouton(dialogue, 'Terminer la Manche').click();
	assert.deepEqual(b.appels[0].args[2], { action: 'TERMINER', etapeId: 'e1' });
	b.module.demonter();
});

test('Écran : sans son activé, la salle dit pourquoi l’extrait est muet', async () => {
	const b = bancEcran(undefined, { sonActive: false });
	b.module.afficher(vueSalle('PREPARATION'), capture);
	const message = b.principale.querySelector('.blindtest__son');
	assert.equal(message.hidden, true, 'Rien à dire tant qu’aucun extrait ne joue.');
	b.module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(0) }), evenement('morceau-ouvert'));
	assert.equal(message.hidden, false);
	assert.match(message.textContent, /Activer le son/);
	assert.equal(bouton(message, 'Lancer l’extrait').hidden, true, 'Le geste attendu est celui de la coquille.');
	b.etatSon.sonDebloque = true;
	for (const rappel of b.abonnes) rappel(b.etatSon);
	await terminerMicrotaches();
	assert.equal(message.hidden, true);
	b.module.demonter();
});

test('Écran : un son bloqué par le navigateur propose « Lancer l’extrait », qui rejoue pendant le geste', async () => {
	const b = bancEcran();
	b.echouer('NotAllowedError');
	b.module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(0) }), capture);
	await terminerMicrotaches();
	const son = b.principale.querySelector('.blindtest__son');
	assert.equal(son.hidden, false);
	assert.match(son.textContent, /Le navigateur bloque le son de l’extrait/);
	assert.doesNotMatch(son.textContent, /Activer le son/, 'Le bouton de la coquille est caché : la salle n’y renvoie pas.');
	const lancer = bouton(son, 'Lancer l’extrait');
	assert.equal(lancer.hidden, false);

	b.echouer(null);
	lancer.focus();
	const lectures = b.journal.filter(ligne => ligne === 'lire').length;
	lancer.click();
	assert.equal(b.journal.filter(ligne => ligne === 'lire').length, lectures + 1, 'play() part pendant le clic.');
	await terminerMicrotaches();
	assert.equal(son.hidden, true);
	assert.equal(b.document.activeElement, b.principale.querySelector('.blindtest__phase'), 'Le focus ne tombe pas sur la page.');
	b.module.demonter();
});

test('Écran : un extrait illisible le dit sans demander de geste, jusqu’au Morceau suivant', async () => {
	const b = bancEcran();
	b.echouer('NotSupportedError');
	b.module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(0) }), capture);
	await terminerMicrotaches();
	const son = b.principale.querySelector('.blindtest__son');
	assert.match(son.textContent, /L’extrait ne peut pas être lu/);
	assert.equal(bouton(son, 'Lancer l’extrait').hidden, true);

	b.echouer(null);
	b.module.afficher(vueSalle('EXTRAIT', { etapeId: 'e2', numero: 3, lecture: lectureEnCours(0) }), evenement('morceau-ouvert'));
	assert.equal(son.hidden, true, 'Le Morceau suivant repart sans incident.');
	b.module.demonter();
});

test('Écran : un Morceau sauté est annoncé pendant la préparation du suivant, jamais sur une capture', async () => {
	const b = bancEcran();
	b.module.afficher(vueSalle('PREPARATION', { etapeId: 'e2', numero: 3 }), evenement('morceau-saute'));
	const annonce = b.principale.querySelector('.blindtest__annonce');
	assert.equal(annonce.hidden, false);
	assert.match(annonce.textContent, /Extrait indisponible : ce Morceau est sauté/);
	assert.equal(annonce.classList.contains('blindtest--arrivee'), true);
	b.module.afficher(vueSalle('PREPARATION', { etapeId: 'e2', numero: 3 }), capture);
	assert.equal(annonce.hidden, false, 'Une capture du même Morceau garde l’annonce, sans l’animer.');
	assert.equal(annonce.classList.contains('blindtest--arrivee'), false);
	b.module.afficher(vueSalle('EXTRAIT', { etapeId: 'e2', numero: 3, lecture: lectureEnCours(0) }), evenement('morceau-ouvert'));
	assert.equal(annonce.hidden, true, 'L’extrait du Morceau suivant remplace l’annonce.');

	const recharge = bancEcran();
	recharge.module.afficher(vueSalle('PREPARATION', { etapeId: 'e2', numero: 3 }), capture);
	assert.equal(recharge.principale.querySelector('.blindtest__annonce').hidden, true);
	await terminerMicrotaches();
	b.module.demonter();
	recharge.module.demonter();
});

test('Écran : une capture qui suit un événement ne rejoue ni ne prolonge aucune animation', async () => {
	const b = bancEcran();
	const trouvee = [{ partie: 'ARTISTE', valeur: 'Beyoncé', joueurId: 'lea', pseudo: 'Léa', points: 30 }];
	b.module.afficher(vueSalle('EXTRAIT', { lecture: lectureEnCours(5000), partiesTrouvees: trouvee }), evenement('proposition-jugee'));
	assert.equal(b.laterale.querySelector('.blindtest__parties').classList.contains('blindtest--arrivee'), true);
	b.module.afficher(vueSalle('REVELATION', { partiesTrouvees: [...trouvee,
		{ partie: 'TITRE', valeur: 'Halo', joueurId: 'sam', pseudo: 'Sam', points: 70 }],
	revelation: { titre: 'Halo', artiste: 'Beyoncé', pochette: null } }), capture);
	await terminerMicrotaches();
	assert.equal(b.principale.querySelectorAll('.blindtest--arrivee').length
		+ b.laterale.querySelectorAll('.blindtest--arrivee').length, 0);
	b.module.demonter();
});

test('Manette : un brouillon en échec se dit près des champs et se renvoie à la main avec un numéro supérieur', async () => {
	const b = bancManette();
	b.connecter('synchronisee');
	b.module.afficher(vueManette('PROPOSITION', { lecture: lectureEnPause(5000), main: mainDe('lea', 'Léa'), aLaMain: true,
		monBrouillon: { numero: 2, titre: 'Hal', artiste: '' } }), capture);
	const [titre] = b.principale.querySelectorAll('input');
	const zone = b.principale.querySelector('.blindtest__brouillon');
	assert.equal(zone.hidden, true);
	titre.value = 'Halo';
	titre.dispatchEvent({ type: 'input' });
	b.declencherDelais();
	await terminerMicrotaches();
	b.appels[0].resolve({ statut: 503, corps: { detail: 'Service indisponible.' } });
	await terminerMicrotaches();
	assert.equal(zone.hidden, false);
	assert.match(zone.textContent, /Ta saisie n’est pas enregistrée/);
	assert.equal(b.appels.length, 1, 'Aucun nouvel essai automatique.');

	bouton(zone, 'Enregistrer ma saisie').click();
	await terminerMicrotaches();
	assert.deepEqual(b.appels[1].args[2], { numero: 4, titre: 'Halo', artiste: '' });
	b.appels[1].resolve({ statut: 204, corps: null });
	await terminerMicrotaches();
	assert.equal(zone.hidden, true);
	b.module.demonter();
});

test('Manette : pendant une coupure, le brouillon attend la synchronisation pour partir', async () => {
	const b = bancManette();
	b.connecter('synchronisee');
	b.module.afficher(vueManette('PROPOSITION', { lecture: lectureEnPause(5000), main: mainDe('lea', 'Léa'), aLaMain: true }), capture);
	b.connecter('coupure');
	const [titre] = b.principale.querySelectorAll('input');
	titre.value = 'Halo';
	titre.dispatchEvent({ type: 'input' });
	b.declencherDelais();
	await terminerMicrotaches();
	assert.equal(b.appels.length, 0);
	b.connecter('synchronisee');
	b.declencherDelais();
	await terminerMicrotaches();
	assert.deepEqual(b.appels[0].args[2], { numero: 1, titre: 'Halo', artiste: '' });
	b.module.demonter();
});

test('Manette : une Proposition à confirmation inconnue fige la saisie ; un refus la rend', async () => {
	const b = bancManette();
	b.connecter('synchronisee');
	b.module.afficher(vueManette('PROPOSITION', { lecture: lectureEnPause(5000), main: mainDe('lea', 'Léa'), aLaMain: true }), capture);
	const [titre, artiste] = b.principale.querySelectorAll('input');
	titre.value = 'Halo';
	const formulaire = b.principale.querySelector('form');
	formulaire.dispatchEvent({ type: 'submit' });
	b.appels[0].reject(new TypeError('réseau'));
	await terminerMicrotaches();
	assert.equal(b.captures(), 1);
	assert.match(texte(b.principale), /Confirmation à vérifier\. Ta réponse reste celle envoyée\./);
	assert.equal(titre.readOnly, true, 'Le même envoiId ne peut pas repartir avec un autre contenu.');
	assert.equal(artiste.readOnly, true);
	titre.dispatchEvent({ type: 'input' });
	assert.equal(b.appels.length, 1, 'Aucun brouillon pendant le doute.');

	formulaire.dispatchEvent({ type: 'submit' });
	assert.deepEqual(b.appels[1].args[2], { envoiId: 'envoi-1', titre: 'Halo', artiste: '' });
	b.appels[1].resolve({ statut: 409, corps: { code: 'ETAT_INCOMPATIBLE', detail: 'Tu n’as pas la main.' } });
	await terminerMicrotaches();
	assert.match(texte(b.principale), /Tu n’as pas la main\./);
	assert.equal(titre.readOnly, false, 'Un refus rend la saisie.');
	formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels[2].args[2].envoiId, 'envoi-2', 'Après un refus, une nouvelle Proposition a un nouvel envoi.');
	b.module.demonter();
});

test('Manette : le Buzz annonce son envoi puis son reçu, et rend le focus quand il disparaît', async () => {
	const b = bancManette();
	b.connecter('synchronisee');
	b.module.afficher(vueManette('EXTRAIT', { lecture: lectureEnCours(0), peutBuzzer: true }), capture);
	const buzz = bouton(b.principale, 'Buzzer');
	const statut = b.principale.querySelector('[role=status]');
	buzz.focus();
	buzz.click();
	assert.equal(statut.textContent, 'Envoi du Buzz…');
	b.appels[0].resolve({ statut: 200, corps: { rang: 1 } });
	await terminerMicrotaches();
	assert.equal(statut.textContent, 'Buzz reçu.');

	b.module.afficher(vueManette('PROPOSITION', { lecture: lectureEnPause(3000), main: mainDe('sam', 'Sam') }),
		evenement('buzz-recu'));
	assert.equal(statut.textContent, '', 'La vue suivante dit qui a la main.');
	assert.equal(buzz.hidden, true);
	assert.equal(b.document.activeElement, b.principale.querySelector('.blindtest__titre'));
	b.module.demonter();
});

test('Manette : un reçu arrivé après la vue suivante ne s’affiche pas', async () => {
	const b = bancManette();
	b.connecter('synchronisee');
	b.module.afficher(vueManette('EXTRAIT', { lecture: lectureEnCours(0), peutBuzzer: true }), capture);
	bouton(b.principale, 'Buzzer').click();
	b.module.afficher(vueManette('PROPOSITION', { lecture: lectureEnPause(3000), main: mainDe('lea', 'Léa'), aLaMain: true }),
		evenement('buzz-recu'));
	b.appels[0].resolve({ statut: 200, corps: { rang: 1 } });
	await terminerMicrotaches();
	assert.equal(b.principale.querySelector('[role=status]').textContent, '');
	b.module.demonter();
});
