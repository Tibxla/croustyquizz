import test from 'node:test';
import assert from 'node:assert/strict';
import { creerBanc, creerDocument, terminerMicrotaches, chanson, passage, vue } from './karaoke-dom-factice.mjs';
import { monter as manette } from '../../src/main/resources/static/js/modes/karaoke/manette.mjs';
import { monter as consoleKaraoke } from '../../src/main/resources/static/js/modes/karaoke/console.mjs';
import { creerEcranKaraoke } from '../../src/main/resources/static/js/modes/karaoke/ecran.mjs';
import { creerAudioSalle } from '../../src/main/resources/static/js/audio-salle.mjs';
import { creerCoordinationSon } from '../../src/main/resources/static/js/coordination-son.mjs';
import { readFile } from 'node:fs/promises';
const capture = { source: 'capture', type: 'capture' }, evenement = { source: 'evenement', type: 'karaoke-file-modifiee' };
const bouton = (zone, texte) => zone.querySelectorAll('button').find(b => b.textContent === texte);

function bancLecteur({ bibliotheque, lecteurId = 'lecteur', soireeId, memoireLecteur } = {}) {
	const appels = [], sonsDeManche = [], trames = new Map();
	let numero = 0;
	let sonDebloque = false;
	const lecteur = {
		debloquer: async () => { if (!sonDebloque) appels.push('son'); sonDebloque = true; },
		charger: async () => { appels.push('charger'); return { dureeMs: 2000 }; },
		lire: async () => { appels.push('lire'); },
		pause: () => appels.push('pause'),
		reprendre: async () => { appels.push('reprendre'); },
		position: () => 1500,
		surFin: () => () => {},
		surErreur: () => () => {},
		detruire: () => appels.push('libérer'),
	};
	const audioSalle = creerAudioSalle({ lecteur, lecteurId, bibliotheque,
		sonde: { charger: async () => ({ dureeMs: 2000 }), detruire() {} },
		coordonner: surAttente => soireeId ? creerCoordinationSon({ verrous: navigator.locks,
			canal: new BroadcastChannel(soireeId), ouverteA: performance.timeOrigin, id: lecteurId, soireeId, surAttente, memoireLecteur }) :
			({ activer() {}, signalerSonDeManche: actif => sonsDeManche.push(actif),
			attendreSonDeManche: async () => sonsDeManche.at(-1), annoncerMorceaux() {}, attendreAucunMorceauAilleurs: async () => {},
			detruire() {} }),
	});
	return { audioSalle, appels, sonsDeManche, trames,
		dependances: { requestAnimationFrame(f) { trames.set(++numero, f); return numero; },
			cancelAnimationFrame: id => trames.delete(id), performance } };
}
function ecran(zones, contexte, { audioSalle, dependances } = bancLecteur()) {
	Object.assign(contexte, { audioSalle, signalerSonDeManche: audioSalle.signalerSonDeManche });
	return creerEcranKaraoke(zones, contexte, { ...dependances, document: zones.principale.ownerDocument });
}

test('les trois écrans partagent un lien CSS, conservent le talon et libèrent zones et abonnement', () => {
	const document = creerDocument(), talon = document.createElement('div'); talon.textContent = 'DEMO01'; document.body.append(talon);
	const bancs = [creerBanc(manette, 'JOUEUR', document), creerBanc(consoleKaraoke, 'ANIMATEUR', document), creerBanc(ecran, 'ECRAN', document)];
	assert.equal(document.head.querySelectorAll('link[data-mode=karaoke]').length, 1);
	for (const b of bancs) { b.module.afficher(vue(), capture); b.module.demonter(); assert.equal(b.principale.childElementCount, 0); assert.equal(b.laterale.childElementCount, 0); assert.equal(b.abonnee(), false); }
	assert.equal(document.head.childElementCount, 0); assert.equal(talon.textContent, 'DEMO01');
});

test('Console : la mention Appel automatique remplace la dernière action de pilotage', t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	b.connecter('synchronisee');
	const manuel = { ...vue({ passagesEnAttente: [{ ...passage(), position: 1 }], peutAppeler: true }),
		soiree: { pilotage: 'MANUEL' } };
	b.module.afficher(manuel, capture);
	const appeler = bouton(b.principale, 'Appeler le suivant');
	assert.ok(appeler, 'Le bouton est présent en Pilotage Manuel.');
	const pilotage = appeler.parentNode;
	const commandes = pilotage.querySelectorAll('button').slice(0, -1).map(bouton => bouton.textContent);
	appeler.focus();

	b.module.afficher({ ...manuel, soiree: { pilotage: 'AUTOMATIQUE' },
		karaoke: { ...manuel.karaoke, peutAppeler: false } }, capture);
	assert.equal(Boolean(bouton(pilotage, 'Appeler le suivant')), false);
	assert.equal(Boolean(pilotage.querySelector('.bouton--action')), false);
	const mention = pilotage.querySelector('.console__note');
	assert.ok(mention, 'La mention est présente en Pilotage Automatique.');
	assert.equal(mention.textContent, 'Appel automatique');
	assert.equal(pilotage.children.at(-1) === mention, true);
	assert.deepEqual(pilotage.querySelectorAll('button').map(bouton => bouton.textContent), commandes);
	assert.equal(b.document.activeElement.textContent, 'Passage courant');

	b.module.afficher(manuel, capture);
	assert.equal(Boolean(pilotage.querySelector('.console__note')), false);
	assert.equal(bouton(pilotage, 'Appeler le suivant') === appeler, true);
	assert.equal(appeler.disabled, false);
});

test('Console : sélection unique, placement figé et priorité invalidée si l’ancienne disparaît', () => {
	const p = passage('p', 12), q = { ...passage('q', 13), position: 1, prioritaire: true };
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR'); b.connecter('synchronisee');
	b.module.afficher(vue({ passagesEnAttente: [p, q], passagePrioritaireId: 'q' }), evenement);
	assert.ok(b.principale.querySelector('[aria-pressed=true]') === null);
	b.principale.querySelector('button[data-passage-id=p]').click();
	assert.equal(b.principale.querySelector('[aria-pressed=true]').getAttribute('data-passage-id'), 'p');
	bouton(b.principale, 'Priorité').click();
	assert.match(b.principale.querySelector('dialog').textContent, /Remplace la priorité du Passage 13/);
	b.module.afficher(vue({ passagesEnAttente: [p, q, passage('etranger', 16)], passagePrioritaireId: 'q' }), evenement);
	assert.equal(b.principale.querySelector('dialog').open, true);
	b.module.afficher(vue({ passagesEnAttente: [p], passagePrioritaireId: null }), evenement);
	assert.ok(b.principale.querySelector('dialog') === null); assert.match(b.principale.textContent, /Le Passage a changé/); assert.equal(b.appels.length, 0);
	b.module.afficher(vue({ passagesEnAttente: [p, passage('r', 15), passage('s', 16)] }), capture);
	bouton(b.principale, 'Descendre').click();
	assert.deepEqual(b.appels[0].args[2], { numeroModification: 2, tour: 1, avant: { passageId: 's', numeroModification: 2 } });
	assert.equal(b.appels[0].args[3].entetes['Idempotency-Key'].length, 36);
	b.module.demonter();
});

test('une fermeture native différée après confirmation ne produit pas une fausse invalidation', () => {
	const p = passage(), b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	b.module.afficher(vue({ passagesEnAttente: [p] }), capture); b.connecter('synchronisee');
	b.principale.querySelector('button.ticket').click(); bouton(b.principale, 'Retirer').click();
	const dialog = b.principale.querySelector('dialog');
	dialog.close = () => { dialog.open = false; }; // Le navigateur émet close après le geste.
	bouton(b.principale, 'Confirmer').click();
	b.module.afficher(vue({ passagesEnAttente: [] }), evenement);
	assert.doesNotMatch(b.principale.textContent, /Le Passage a changé/); b.module.demonter();
});

test('une priorité apparue pendant la confirmation et un changement de Manche invalident le geste', () => {
	const p = passage('p', 12), q = passage('q', 13), b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	b.module.afficher(vue({ passagesEnAttente: [p, q], passagePrioritaireId: null }), capture); b.connecter('synchronisee');
	b.principale.querySelector('button[data-passage-id=p]').click(); bouton(b.principale, 'Priorité').click();
	b.module.afficher(vue({ passagesEnAttente: [p, { ...q, prioritaire: true }], passagePrioritaireId: 'q' }), evenement);
	assert.ok(b.principale.querySelector('dialog') === null); assert.match(b.principale.textContent, /Le Passage a changé/);
	bouton(b.principale, 'Retirer').click();
	b.module.afficher(vue({ mancheId: 'suivante', passagesEnAttente: [] }), capture);
	assert.ok(b.principale.querySelector('dialog') === null); assert.match(b.principale.textContent, /Le Passage a changé/);
	assert.equal(b.appels.length, 0); b.module.demonter();
});

for (const [nom, monter, acteur, action, confirmation] of [
	['Console', consoleKaraoke, 'ANIMATEUR', 'Retirer', 'Confirmer'],
	['Manette', manette, 'JOUEUR', 'Me désinscrire', 'Confirmer la désinscription'],
]) {
	test(`${nom} : la confirmation reste désactivée pendant une coupure quel que soit son libellé`, t => {
		const b = creerBanc(monter, acteur), p = passage();
		t.after(() => b.module.demonter());
		b.module.afficher(vue({ passagesEnAttente: [p], mesPassagesEnAttente: [p] }), capture);
		b.connecter('synchronisee');
		if (acteur === 'ANIMATEUR') b.principale.querySelector('button.ticket').click();
		bouton(b.principale, action).click();
		const dialogue = b.principale.querySelector('dialog');
		const confirmer = bouton(dialogue, confirmation);
		assert.ok(b.document.activeElement === bouton(dialogue, 'Annuler'));
		confirmer.textContent = 'Valider';
		b.connecter('coupure');
		assert.equal(confirmer.disabled, true);
		confirmer.click();
		assert.equal(b.appels.length, 0);
		assert.equal(dialogue.open, true);
		b.connecter('synchronisee');
		assert.equal(confirmer.disabled, false);
		confirmer.click();
		assert.equal(b.appels.length, 1);
		assert.equal(dialogue.open, false);
	});
}

test('la commande Console focalisée reste joignable après envoi et événement, ou revient au titre si elle disparaît', () => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR'), passages = [passage('a', 12), passage('b', 13)];
	b.module.afficher(vue({ passagesEnAttente: passages }), capture); b.connecter('synchronisee');
	b.principale.querySelector('button[data-passage-id=b]').click();
	const monter = bouton(b.principale, 'Monter'); monter.focus(); monter.click();
	assert.equal(b.document.activeElement.isConnected, true); assert.equal(b.document.activeElement.textContent, 'Monter');
	b.module.afficher(vue({ passagesEnAttente: passages }), evenement);
	assert.equal(b.document.activeElement.isConnected, true); assert.equal(b.document.activeElement.textContent, 'Monter');
	b.module.afficher(vue({ passagesEnAttente: [] }), evenement);
	assert.equal(b.document.activeElement.textContent, "File d'attente"); b.module.demonter();
});

test('Console : limite sous focus, valeur soumise conservée pendant l’envoi et validation sûre', () => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR'); b.module.afficher(vue(), capture); b.connecter('synchronisee');
	const champ = b.principale.querySelector('#karaoke-limite'), formulaire = b.principale.querySelector('form');
	champ.focus(); champ.value = '5'; b.module.afficher(vue({ limitePassagesEnAttente: 3 }), evenement); assert.equal(champ.value, '5');
	formulaire.dispatchEvent({ type: 'submit' }); assert.equal(b.appels[0].args[2].limitePassagesEnAttente, 5);
	b.document.body.focus(); b.module.afficher(vue({ limitePassagesEnAttente: 4 }), capture);
	assert.match(b.principale.textContent, /Limite envoyée : 5/); assert.equal(champ.value, '5');
	for (const valeur of ['0', '1.5', '21', '9007199254740992', '']) { champ.value = valeur; formulaire.dispatchEvent({ type: 'submit' }); assert.equal(champ.getAttribute('aria-invalid'), 'true'); }
	assert.equal(b.appels.length, 1); b.module.demonter();
});

test('Console : un seul réessai manuel après capture puis synchronisée, contenu inchangé et double appui unique', async () => {
	const p = passage(), b = creerBanc(consoleKaraoke, 'ANIMATEUR'); b.module.afficher(vue({ passagesEnAttente: [p] }), evenement); b.connecter('synchronisee');
	b.principale.querySelector('button.ticket').click(); bouton(b.principale, 'Retirer').click(); bouton(b.principale, 'Confirmer').click();
	b.appels[0].resolve({ statut: 503, corps: { detail: 'Panne de démonstration' } }); await terminerMicrotaches();
	assert.equal(b.captures(), 1); assert.ok(bouton(b.principale, 'Réessayer le même envoi') === undefined);
	b.connecter('synchronisation'); b.module.afficher(vue({ passagesEnAttente: [{ ...p, numeroModification: 4 }] }), capture);
	assert.ok(bouton(b.principale, 'Réessayer le même envoi') === undefined);
	b.connecter('synchronisee'); const reessai = bouton(b.principale, 'Réessayer le même envoi'); reessai.click(); reessai.click();
	assert.equal(b.appels.length, 2); assert.deepEqual(b.appels[1].args[2], b.appels[0].args[2]);
	assert.equal(b.appels[1].args[3].entetes['Idempotency-Key'], b.appels[0].args[3].entetes['Idempotency-Key']);
	b.appels[1].resolve({ statut: 409, corps: { code: 'COMMANDE_PERIMEE', detail: 'Trop tard' } }); await terminerMicrotaches();
	assert.equal(b.captures(), 2);
	assert.equal(bouton(b.principale, 'Appliquer').disabled, true);
	b.module.afficher(vue({ passagesEnAttente: [p] }), evenement);
	assert.equal(bouton(b.principale, 'Appliquer').disabled, true);
	b.module.afficher(vue({ passagesEnAttente: [p] }), capture); assert.ok(bouton(b.principale, 'Réessayer le même envoi') === undefined);
	assert.equal(bouton(b.principale, 'Appliquer').disabled, false);
	b.module.demonter();
});

test('Console : CSRF_INVALIDE bloque toutes les mutations même après capture et changement de Manche', async () => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR'); b.module.afficher(vue({ passagesEnAttente: [passage()] }), capture); b.connecter('synchronisee');
	b.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	b.appels[0].resolve({ statut: 403, corps: { code: 'CSRF_INVALIDE', detail: 'Session périmée' } }); await terminerMicrotaches();
	assert.equal(b.captures(), 0); b.module.afficher(vue({ passagesEnAttente: [passage()] }), capture);
	assert.equal(bouton(b.principale, 'Appliquer').disabled, true);
	b.principale.querySelector('button.ticket').click(); assert.equal(bouton(b.principale, 'Retirer').disabled, true);
	b.module.afficher(vue({ mancheId: 'nouvelle' }), capture); assert.equal(bouton(b.principale, 'Appliquer').disabled, true);
	assert.equal(bouton(b.laterale, 'Chercher').disabled, false); b.module.demonter();
});

test('le réessai Animateur vise le dernier échec même après un succès, et un refus définitif plus récent le masque', async () => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR'); b.module.afficher(vue(), capture); b.connecter('synchronisee');
	const champ = b.principale.querySelector('#karaoke-limite'), formulaire = b.principale.querySelector('form');
	champ.value = '5'; formulaire.dispatchEvent({ type: 'submit' });
	b.appels[0].resolve({ statut: 503, corps: { detail: 'Panne' } }); await terminerMicrotaches(); b.module.afficher(vue(), capture);
	champ.value = '6'; formulaire.dispatchEvent({ type: 'submit' });
	b.appels[1].resolve({ statut: 200, corps: {} }); await terminerMicrotaches();
	assert.ok(bouton(b.principale, 'Réessayer le même envoi'));
	bouton(b.principale, 'Réessayer le même envoi').click(); assert.equal(b.appels[2].args[2].limitePassagesEnAttente, 5);
	b.appels[2].resolve({ statut: 503, corps: { detail: 'Panne' } }); await terminerMicrotaches(); b.module.afficher(vue(), capture);
	champ.value = '7'; formulaire.dispatchEvent({ type: 'submit' });
	b.appels[3].resolve({ statut: 400, corps: { code: 'REQUETE_INVALIDE', detail: 'Refus définitif' } }); await terminerMicrotaches();
	assert.ok(bouton(b.principale, 'Réessayer le même envoi') === undefined); b.module.demonter();
});

test('Manette : deux tickets, désinscription confirmée en pause, refus sans disparition et oubli par vue', async () => {
	const p = passage(), q = passage('q', 15), b = creerBanc(manette);
	b.module.afficher(vue({ etat: 'EN_PAUSE', peutInscrire: false, mesPassagesEnAttente: [p, q] }), capture); b.connecter('synchronisee');
	assert.equal(b.principale.querySelectorAll('.mon-ticket').length, 2);
	const retirer = b.principale.querySelector('button[data-passage-id=p]'); retirer.focus(); retirer.click();
	assert.match(b.principale.querySelector('dialog').textContent, /14 · Titre de démonstration/);
	bouton(b.principale, 'Annuler').click(); assert.equal(b.appels.length, 0);
	bouton(b.principale, 'Me désinscrire').click(); bouton(b.principale, 'Confirmer la désinscription').click();
	assert.equal(b.appels[0].args[2].numeroModification, 2); assert.ok(b.appels[0].args[2].envoiId);
	b.appels[0].reject(new Error()); await terminerMicrotaches(); assert.equal(b.captures(), 1);
	b.module.afficher(vue({ mesPassagesEnAttente: [p, q] }), capture);
	bouton(b.principale, 'Réessayer le même envoi').click(); assert.equal(b.appels.length, 2);
	b.module.afficher(vue({ mesPassagesEnAttente: [q] }), evenement); assert.equal(b.appels[1].args[3].signal.aborted, true);
	b.appels[1].resolve({ statut: 200, corps: {} }); await terminerMicrotaches();
	assert.equal(b.principale.querySelectorAll('.mon-ticket').length, 1); assert.doesNotMatch(b.principale.textContent, /Envoi accepté/);
	b.module.demonter();
});

test('Manette : recherche par composition, validation accessible, vidage silencieux et erreurs HTTP de fiche', async () => {
	const b = creerBanc(manette); b.module.afficher(vue(), capture);
	const champ = b.principale.querySelector('input'), formulaire = b.principale.querySelector('form');
	champ.value = 'titre'; champ.dispatchEvent({ type: 'compositionstart' }); formulaire.dispatchEvent({ type: 'submit' }); assert.equal(b.appels.length, 0);
	champ.dispatchEvent({ type: 'compositionend' }); champ.value = '🎵'; formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(champ.getAttribute('aria-invalid'), 'true'); assert.ok(champ.getAttribute('aria-describedby'));
	champ.value = ''; champ.dispatchEvent({ type: 'input' }); assert.equal(champ.getAttribute('aria-invalid'), null); assert.equal(b.appels.length, 0);
	champ.value = 'titre'; formulaire.dispatchEvent({ type: 'submit' });
	b.appels[0].resolve({ statut: 200, corps: { chansons: [chanson] } }); await terminerMicrotaches();
	bouton(b.principale, chanson.titre).click(); b.appels[1].resolve({ statut: 404, corps: { detail: 'Source disparue' } }); await terminerMicrotaches();
	assert.match(b.principale.querySelector('dialog').textContent, /Cette Chanson n'est plus disponible/);
	assert.match(b.principale.querySelector('dialog').textContent, /Source disparue/); assert.ok(bouton(b.principale, "M'inscrire") === undefined);
	b.module.demonter();
});

test('Manette : l’inscription inconnue se réessaie après capture, puis un ticket confirme le 503', async () => {
	const b = creerBanc(manette); b.module.afficher(vue(), evenement); b.connecter('synchronisee');
	const champ = b.principale.querySelector('input'); champ.value = 'titre'; b.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	b.appels[0].resolve({ statut: 200, corps: { chansons: [chanson] } }); await terminerMicrotaches();
	bouton(b.principale, chanson.titre).click(); b.appels[1].resolve({ statut: 200, corps: { chanson, inscriptionPossible: true, motifIndisponibilite: null } }); await terminerMicrotaches();
	bouton(b.principale, "M'inscrire").click(); b.appels[2].reject(new Error()); await terminerMicrotaches();
	assert.equal(bouton(b.principale, 'Réessayer le même envoi').hidden, true);
	b.module.afficher(vue(), evenement); assert.equal(bouton(b.principale, 'Réessayer le même envoi').hidden, true);
	b.connecter('synchronisation'); b.module.afficher(vue(), capture); assert.equal(champ.value, 'titre');
	b.connecter('synchronisee'); bouton(b.principale, 'Réessayer le même envoi').click();
	assert.deepEqual(b.appels[3].args[2], b.appels[2].args[2]);
	b.appels[3].resolve({ statut: 503, corps: { detail: 'Panne' } }); await terminerMicrotaches();
	b.module.afficher(vue({ mesPassagesEnAttente: [{ ...passage(), chanson }] }), capture);
	assert.equal(bouton(b.principale, 'Réessayer le même envoi').hidden, true);
	assert.match(b.principale.querySelector('dialog').textContent, /Envoi accepté/);
	assert.equal(bouton(b.principale, "M'inscrire").disabled, true);
	assert.doesNotMatch(b.principale.textContent, /Consultation en cours/);
	b.module.afficher(vue(), capture); assert.equal(bouton(b.principale, "M'inscrire").disabled, false);
	b.module.demonter();
});

test('la sélection garde la grille et sa teinte sans citron, les captures n’ont aucune animation', async () => {
	const css = await readFile(new URL('../../src/main/resources/static/css/karaoke.css', import.meta.url), 'utf8');
	const selection = css.match(/button\.ticket\[aria-pressed="true"\] \{([^}]+)\}/)[1];
	assert.match(selection, /var\(--teinte\)/); assert.doesNotMatch(selection, /citron|display:/);
	for (const monter of [manette, consoleKaraoke, ecran]) {
		const b = creerBanc(monter); b.module.afficher(vue({ prochainsPassages: [passage()], passagesEnAttente: [passage()], mesPassagesEnAttente: [passage()] }), capture);
		assert.equal(b.document.body.querySelectorAll('.mon-ticket--appele, .ticket--detache').length, 0); b.module.demonter();
	}
});

test('un refus définitif d’inscription garde le détail près de l’action tout en libérant un nouvel envoi', async () => {
	const b = creerBanc(manette); b.module.afficher(vue(), capture); b.connecter('synchronisee');
	const champ = b.principale.querySelector('input'); champ.value = 'titre'; b.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	b.appels[0].resolve({ statut: 200, corps: { chansons: [chanson] } }); await terminerMicrotaches();
	bouton(b.principale, chanson.titre).click(); b.appels[1].resolve({ statut: 200, corps: { chanson, inscriptionPossible: true, motifIndisponibilite: null } }); await terminerMicrotaches();
	bouton(b.principale, "M'inscrire").click(); b.appels[2].resolve({ statut: 422, corps: { code: 'DONNEES_INVALIDES', detail: 'La durée manque côté serveur.' } }); await terminerMicrotaches();
	assert.match(b.principale.querySelector('dialog').textContent, /La durée manque côté serveur/);
	assert.equal(bouton(b.principale, "M'inscrire").disabled, false); b.module.demonter();
});

test('la Manette conserve la recherche au quota, consulte par GET puis attend le reçu sans inventer de ticket', async () => {
	const b = creerBanc(manette); b.module.afficher(vue({ peutInscrire: false, motifInscriptionRefusee: 'LIMITE_ATTEINTE' }), evenement);
	const champ = b.principale.querySelector('input'); champ.value = 'Titre';
	b.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	assert.equal(b.appels[0].args[0], 'GET');
	b.appels[0].resolve({ statut: 200, corps: { chansons: [chanson] } }); await terminerMicrotaches();
	bouton(b.principale, chanson.titre).click(); b.appels[1].resolve({ statut: 200, corps: { chanson, inscriptionPossible: true, motifIndisponibilite: null } }); await terminerMicrotaches();
	assert.equal(bouton(b.principale, "M'inscrire").disabled, true);
	b.module.afficher(vue(), capture); b.connecter('synchronisee');
	bouton(b.principale, "M'inscrire").click(); assert.equal(b.appels.length, 3);
	assert.equal(b.principale.querySelectorAll('.mon-ticket').length, 0);
	b.appels[2].resolve({ statut: 201, corps: { passageId: 'p' } }); await terminerMicrotaches();
	assert.equal(b.principale.querySelectorAll('.mon-ticket').length, 0);
	assert.equal(bouton(b.principale, "M'inscrire").disabled, true);
	b.module.demonter();
});

test('salle : le premier Passage en grand, seulement les suivants dans « Ensuite »', () => {
	const b = creerBanc(ecran, 'ECRAN');
	b.module.afficher(vue({ prochainsPassages: [passage(), passage('p2', 15)], nombreAutresPassages: 1 }), capture);
	assert.match(b.principale.textContent, /Prépare-toi/); assert.equal(b.principale.querySelectorAll('.ticket').length, 1);
	assert.equal(b.laterale.querySelectorAll('.ticket').length, 1, 'le premier Passage n’est pas répété');
	assert.match(b.laterale.textContent, /et 1 autre$/);
	b.module.afficher(vue({ prochainsPassages: [passage()], nombreAutresPassages: 2 }), capture);
	assert.equal(b.laterale.querySelectorAll('.ticket').length, 0); assert.match(b.laterale.textContent, /et 2 autres/);
	assert.equal(b.document.body.querySelectorAll('.ticket--detache').length, 0);
	b.module.demonter(); assert.equal(b.principale.childElementCount, 0); assert.equal(b.document.head.childElementCount, 0);
});

test('la coupure bloque les mutations mais permet le catalogue ; une panne de lecture ne pose pas de verrou local', async () => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR'); b.module.afficher(vue({ passagesEnAttente: [passage()] }), capture); b.connecter('synchronisee');
	b.connecter('coupure'); assert.match(b.principale.textContent, /Connexion perdue, on réessaie/);
	assert.equal(bouton(b.principale, 'Appliquer').disabled, true); assert.equal(bouton(b.laterale, 'Chercher').disabled, false);
	const champ = b.laterale.querySelector('input'); champ.value = 'titre'; b.laterale.querySelector('form').dispatchEvent({ type: 'submit' });
	b.connecter('synchronisee'); b.appels[0].resolve({ statut: 503, corps: { code: 'SOURCE_PAROLES_INDISPONIBLE', detail: 'Source indisponible.' } }); await terminerMicrotaches();
	assert.equal(b.captures(), 1); assert.match(b.laterale.textContent, /Source indisponible/);
	assert.doesNotMatch(b.laterale.textContent, /Aucune Chanson trouvée/); assert.equal(bouton(b.principale, 'Appliquer').disabled, false);
	b.module.demonter();
});

test('changer de Manche ou démonter annule les lectures et ignore toute réponse tardive', async () => {
	const b = creerBanc(manette); b.module.afficher(vue(), capture);
	const champ = b.principale.querySelector('input'); champ.value = 'ancien'; b.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	b.module.afficher(vue({ mancheId: 'nouvelle' }), capture); assert.equal(b.appels[0].args[3].signal.aborted, true); assert.equal(champ.value, '');
	b.appels[0].resolve({ statut: 200, corps: { chansons: [chanson] } }); await terminerMicrotaches();
	assert.ok(bouton(b.principale, chanson.titre) === undefined);
	champ.value = 'nouveau'; b.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	b.module.demonter(); assert.equal(b.appels[1].args[3].signal.aborted, true);
	b.appels[1].reject(new Error()); await terminerMicrotaches(); assert.equal(b.principale.childElementCount, 0);
	assert.equal(b.captures(), 0);
});

test('changer de Manche annule les mutations précédentes et leurs réponses tardives', async () => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	b.module.afficher(vue(), capture);
	b.connecter('synchronisee');
	const formulaire = b.principale.querySelector('form');
	formulaire.dispatchEvent({ type: 'submit' });
	b.module.afficher(vue({ mancheId: 'nouvelle' }), evenement);
	assert.equal(b.appels[0].args[3].signal.aborted, true);
	assert.doesNotMatch(b.principale.textContent, /Envoi en cours/);
	b.appels[0].resolve({ statut: 503, corps: {} });
	await terminerMicrotaches();
	assert.equal(b.captures(), 0);
	assert.ok(bouton(b.principale, 'Réessayer le même envoi') === undefined);
	formulaire.dispatchEvent({ type: 'submit' });
	assert.match(b.appels[1].args[1], /\/manches\/nouvelle\//);
	assert.notEqual(b.appels[1].args[3].entetes['Idempotency-Key'], b.appels[0].args[3].entetes['Idempotency-Key']);
	b.module.demonter();
});

test('les mutations Karaoké se taisent dans le bandeau de la coquille : un seul statut annoncé, près de l’action', () => {
	const console_ = creerBanc(consoleKaraoke, 'ANIMATEUR'); console_.connecter('synchronisee');
	console_.module.afficher(vue({ passagesEnAttente: [passage('p', 12), passage('r', 15), passage('s', 16)] }), capture);
	console_.principale.querySelector('button[data-passage-id=p]').click();
	bouton(console_.principale, 'Descendre').click();
	assert.notEqual(console_.appels[0].args[0], 'GET'); assert.equal(console_.appels[0].args[3].annoncer, false);
	console_.module.demonter();
	const p = passage(), manetteBanc = creerBanc(manette);
	manetteBanc.module.afficher(vue({ mesPassagesEnAttente: [p] }), capture); manetteBanc.connecter('synchronisee');
	bouton(manetteBanc.principale, 'Me désinscrire').click(); bouton(manetteBanc.principale, 'Confirmer la désinscription').click();
	assert.notEqual(manetteBanc.appels[0].args[0], 'GET'); assert.equal(manetteBanc.appels[0].args[3].annoncer, false);
	manetteBanc.module.demonter();
});

test('Console : la limite se borne à 20 comme le contrat, et le message donne l’intervalle', () => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR'); b.module.afficher(vue(), capture); b.connecter('synchronisee');
	const champ = b.principale.querySelector('#karaoke-limite'), formulaire = b.principale.querySelector('form');
	assert.equal(champ.max, '20');
	champ.value = '25'; formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels.length, 0); assert.match(b.principale.textContent, /entre 1 et 20/);
	champ.value = '20'; formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels[0].args[2].limitePassagesEnAttente, 20); b.module.demonter();
});

test('Manette : fermer la fiche pendant une consultation retire « Consultation en cours… »', async () => {
	const b = creerBanc(manette); b.module.afficher(vue(), capture);
	const champ = b.principale.querySelector('input'), formulaire = b.principale.querySelector('form');
	champ.value = 'titre'; formulaire.dispatchEvent({ type: 'submit' });
	b.appels[0].resolve({ statut: 200, corps: { chansons: [chanson, { ...chanson, chansonId: 'autre', titre: 'Autre' }] } }); await terminerMicrotaches();
	bouton(b.principale, chanson.titre).click(); b.appels[1].resolve({ statut: 200, corps: { chanson, paroles: null } }); await terminerMicrotaches();
	assert.ok(b.principale.querySelector('dialog'));
	bouton(b.principale, 'Autre').click(); await terminerMicrotaches();
	assert.match(b.principale.textContent, /Consultation en cours…/);
	bouton(b.principale.querySelector('dialog'), 'Fermer').click(); await terminerMicrotaches();
	assert.doesNotMatch(b.principale.textContent, /Consultation en cours…/); b.module.demonter();
});

const heureAppel = Date.parse('2026-11-17T21:00:00Z');
const courant = (etat = 'APPELE', champs = {}) => ({ ...passage(), etat, etapeId: 'etape', calculeLe: heureAppel,
	echeanceAppelLe: heureAppel + 30000, delaiAppelRestantMs: 30000, finPrevueLe: heureAppel + 27000, ...champs });

test('Manette : l’appel personnel garde son ticket et permet la confirmation tardive après recapture', async t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heureAppel + 31000;
	const v = vue({ courant: courant(), peutConfirmerPresence: true });
	b.connecter('synchronisee');
	b.module.afficher(v, { source: 'evenement', type: 'passage-appele' });
	const ticket = b.principale.querySelector('.mon-ticket--appele');
	assert.ok(ticket.classList.contains('mon-ticket--detachement'));
	assert.equal(ticket.style.getPropertyValue('--teinte'), 'var(--bleu)');
	assert.match(ticket.textContent, /Le temps est écoulé, tu peux encore confirmer/);
	assert.doesNotMatch(ticket.textContent, /Position|Tour|En attente/);
	bouton(ticket, 'Je suis prêt').click();
	assert.deepEqual(b.appels[0].args.slice(0, 2), ['POST', '/api/soirees/s/manches/m/passages/p/presence']);
	assert.deepEqual(Object.keys(b.appels[0].args[2]).sort(), ['envoiId', 'etapeId', 'numeroModification']);
	const premiereCle = b.appels[0].args[2].envoiId;
	b.appels[0].resolve({ statut: 409, corps: { code: 'COMMANDE_PERIMEE', detail: 'Le Passage a changé.' } });
	await terminerMicrotaches();
	assert.equal(b.captures(), 1);
	assert.equal(bouton(ticket, 'Je suis prêt').disabled, true);
	b.module.afficher(v, capture);
	assert.match(b.principale.textContent, /Le Passage a changé/);
	bouton(b.principale, 'Je suis prêt').click();
	assert.notEqual(b.appels[1].args[2].envoiId, premiereCle);
	b.module.afficher(vue({ courant: courant('EN_COURS') }), evenement);
	assert.match(b.principale.textContent, /À toi de chanter/);
	assert.ok(b.principale.querySelector('.mon-ticket--appele') === null);
	b.module.afficher(vue({ courant: courant('SAUTE', { interpretes: [{ joueurId: 'autre', pseudo: 'Alizé' }] }) }), capture);
	assert.ok(b.principale.querySelector('.ticket--bleu'));
	assert.ok(bouton(b.principale, 'Je suis prêt') === undefined);
});

test('Console : capacités et corps du Passage, calage affiché uniquement après la vue', async t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heureAppel;
	b.connecter('synchronisee');
	const p = { ...passage('premier'), position: 1 };
	b.module.afficher(vue({ passagesEnAttente: [p], peutAppeler: true }), capture);
	assert.equal(bouton(b.principale, 'Passer').disabled, true);
	assert.equal(bouton(b.principale, 'Pause').disabled, false);
	assert.equal(bouton(b.principale, 'Ça commence').parentNode.hidden, true);
	bouton(b.principale, 'Appeler le suivant').click();
	assert.deepEqual(b.appels[0].args.slice(0, 3), ['POST', '/api/soirees/s/manches/m/passages/premier/appel', { numeroModification: 2 }]);
	const lecture = { lectureId: 'lecture', etat: 'DEMARREE', source: 'FICHIER_LOCAL', numeroModificationCalage: 3, decalageMs: -300 };
	const v = vue({ courant: courant('EN_COURS'), lecture, fichierLocal: { nom: 'A.wav' }, peutCaler: true, peutPasser: true, lecteurDesigne: true });
	b.module.afficher(v, evenement);
	assert.match(b.principale.textContent, /Fin prévue dans 27 s/);
	assert.match(b.principale.textContent, /Fichier local : A.wav/);
	bouton(b.principale, 'Pause').click();
	assert.deepEqual(b.appels[1].args[2], { action: 'METTRE_EN_PAUSE', etapeId: 'etape' });
	bouton(b.principale, 'Ça commence').click();
	assert.deepEqual(b.appels[2].args[2], { etapeId: 'etape', lectureId: 'lecture' });
	for (const [libelle, sens, nom] of [['−0,1 s', 'AVANCER', 'Avancer'], ['+0,1 s', 'RETARDER', 'Retarder']]) {
		const action = bouton(b.principale, libelle);
		assert.equal(action.getAttribute('aria-label'), nom + ' les paroles de 0,1 s');
		action.click();
		assert.deepEqual(b.appels.at(-1).args[2], { etapeId: 'etape', lectureId: 'lecture', numeroModificationCalage: 3, sens });
	}
	b.appels.at(-1).resolve({ statut: 200, corps: { decalageMs: 1500 } });
	await terminerMicrotaches();
	assert.match(b.principale.textContent, /−0,3 s/);
	assert.doesNotMatch(b.principale.textContent, /\+1,5 s/);
	b.module.afficher(vue({ ...v.karaoke, etat: 'EN_PAUSE', courant: courant('EN_PAUSE'), peutCaler: false, peutReprendre: false,
		peutReprendreEnParolesSeules: true, lecture: { ...lecture, etat: 'EN_ERREUR' } }), evenement);
	assert.equal(bouton(b.principale, 'Reprendre').disabled, true);
	assert.doesNotMatch(b.principale.textContent, /Fin prévue dans/);
	bouton(b.principale, 'Paroles seules').click();
	assert.deepEqual(b.appels.at(-1).args[2], { etapeId: 'etape', lectureId: 'lecture' });
	bouton(b.principale, 'Terminer la Manche').click();
	bouton(b.principale, 'Confirmer').click();
	assert.deepEqual(b.appels.at(-1).args[2], { action: 'TERMINER', etapeId: 'etape' });
});

test('Console : le focus suit le prochain bouton de pilotage actif ou revient au titre', t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	b.connecter('synchronisee');
	const file = [{ ...passage('premier'), position: 1 }];
	b.module.afficher(vue({ courant: courant('EN_COURS'), peutPasser: true, passagesEnAttente: file }), capture);
	bouton(b.principale, 'Passer').focus();
	b.module.afficher(vue({ courant: courant('TERMINE'), peutAppeler: true, passagesEnAttente: file }), evenement);
	assert.ok(b.document.activeElement === bouton(b.principale, 'Terminer la Manche'), 'Terminer est le prochain bouton actif');
	bouton(b.principale, 'Appeler le suivant').focus();
	b.module.afficher(vue({ courant: courant(), peutPasser: true }), evenement);
	const titre = b.principale.querySelector('.karaoke__passage-courant').querySelector('h2');
	assert.ok(b.document.activeElement === titre, 'le dernier bouton devenu inactif renvoie au titre');
	assert.equal(titre.tabIndex, -1);
});

test('Console : une étape inactive bloque le bouton jusqu’à la capture, puis produit une nouvelle commande', async t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	b.connecter('synchronisee');
	b.module.afficher(vue({ courant: courant('EN_COURS') }), capture);
	const pause = bouton(b.principale, 'Pause');
	pause.click();
	b.appels[0].resolve({ statut: 409, corps: { code: 'ETAPE_INACTIVE' } });
	await terminerMicrotaches();
	assert.equal(b.captures(), 1);
	assert.equal(pause.disabled, true);
	assert.ok(!bouton(b.principale, 'Réessayer le même envoi'), 'aucun réessai avant la capture');
	b.module.afficher(vue({ courant: courant('EN_COURS', { etapeId: 'nouvelle-etape' }) }), capture);
	assert.equal(pause.disabled, false);
	assert.ok(!bouton(b.principale, 'Réessayer le même envoi'), 'aucun réessai de l’ancienne étape après la capture');
	pause.click();
	assert.equal(b.appels[1].args[2].etapeId, 'nouvelle-etape');
	assert.notEqual(b.appels[0].args[3].entetes['Idempotency-Key'], b.appels[1].args[3].entetes['Idempotency-Key']);
});

test('Manette : le ticket public du courant indique son état sans bouton', t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	const interpretes = [{ joueurId: 'autre', pseudo: 'Alizé' }];
	for (const [etat, libelle] of [['APPELE', 'Sur scène'], ['TERMINE', 'Terminé'], ['SAUTE', 'Sauté']]) {
		b.module.afficher(vue({ courant: courant(etat, { interpretes }) }), capture);
		const ticket = b.principale.querySelector('.ticket');
		assert.equal(ticket.querySelector('.karaoke__etat-ticket')?.textContent, libelle);
		assert.equal(ticket.querySelectorAll('button').length, 0);
	}
});

test('salle : tickets actifs, animation unique, calage des lignes et reprise de la boucle', t => {
	const lecteur = bancLecteur();
	const b = creerBanc((z, c) => ecran(z, c, lecteur), 'ECRAN');
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heureAppel;
	const prochainsPassages = [passage('a'), passage('b'), passage('c')];
	const principaux = () => b.principale.querySelector('.karaoke__prochain');
	const afficher = (courant, origine, champs = {}) => b.module.afficher(vue({ courant, prochainsPassages, nombreAutresPassages: 2, ...champs }), origine);
	afficher(courant(), { source: 'evenement', type: 'passage-appele' });
	assert.equal(lecteur.sonsDeManche.at(-1), false);
	assert.ok(principaux().hidden);
	assert.equal(principaux().childElementCount, 0);
	assert.equal(b.laterale.querySelectorAll('.ticket').length, 4);
	assert.match(b.laterale.textContent, /Sur scène.*et 2 autres/);
	const ticket = b.laterale.querySelector('.ticket--detache');
	assert.ok(!ticket.classList.contains('ticket--immobile'));
	afficher(courant(), { source: 'evenement', type: 'lecture-chanson-modifiee' }, { nombreAutresPassages: 1 });
	assert.ok(b.laterale.querySelector('.ticket--detache') === ticket, 'le même élément poursuit son animation');
	assert.ok(ticket.classList.contains('ticket--detache') && !ticket.classList.contains('ticket--immobile'));
	assert.match(b.laterale.textContent, /et 1 autre$/);
	const lignes = ['Prélude', ' ', '<Chant>', 'Suivante', 'Dernière', 'Hors cadre'].map((texte, i) => ({ texte, positionMs: i * 1000 }));
	const paroles = { parolesId: 'v1', dureeMs: 6000, lignes };
	const lecture = { lectureId: 'l', source: 'PAROLES_SEULES', decalageMs: 0 };
	afficher(courant('EN_COURS', { positionChronologiqueMs: 2300 }), capture, { paroles, lecture });
	assert.equal(lecteur.sonsDeManche.at(-1), true);
	assert.deepEqual(b.principale.querySelectorAll('.paroles__ligne').map(p => p.textContent), ['Prélude', '<Chant>', 'Suivante', 'Dernière']);
	assert.equal(b.principale.querySelector('.paroles__chante').textContent, '<Chant>');
	assert.equal(b.principale.querySelector('.paroles__chante').childElementCount, 0);
	afficher(courant('EN_COURS', { positionChronologiqueMs: 2300 }), evenement, { paroles, lecture: { ...lecture, decalageMs: 1000 } });
	assert.equal(b.principale.querySelector('.paroles__chante').textContent, 'Prélude');
	assert.equal(lecteur.trames.size, 1);
	b.document.hidden = true; b.document.dispatchEvent({ type: 'visibilitychange' });
	assert.equal(lecteur.trames.size, 0);
	b.document.hidden = false; b.document.dispatchEvent({ type: 'visibilitychange' });
	assert.equal(lecteur.trames.size, 1);
	afficher(courant('TERMINE'), capture);
	assert.equal(lecteur.sonsDeManche.at(-1), false);
	assert.ok(!principaux().hidden);
	assert.equal(b.laterale.querySelectorAll('.ticket').length, 3);
	assert.match(b.laterale.textContent, /Terminé/);
	assert.ok(b.laterale.querySelector('.ticket--detache') === ticket, 'la clôture actualise l’étiquette du même élément');
	afficher(courant('SAUTE', { chanson: { ...courant().chanson, titre: 'Titre corrigé' } }), capture);
	assert.ok(b.laterale.querySelector('.ticket--detache') !== ticket, 'une nouvelle description renouvelle le ticket');
	assert.match(b.laterale.querySelector('.ticket--immobile').textContent, /Titre corrigé.*Sauté/);
	b.module.demonter();
	assert.equal(lecteur.trames.size, 0);
});

test('salle : activation sonore locale pendant une coupure avec un dossier vide', async t => {
	const audio = bancLecteur();
	audio.audioSalle.choisirDossier([]);
	const b = creerBanc((z, c) => ecran(z, c, audio), 'ECRAN');
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heureAppel;
	const appel = vue({ courant: courant(), lecteurDesigneId: audio.audioSalle.lecteurId });
	b.module.afficher(appel, capture);
	b.connecter('coupure');
	await audio.audioSalle.activerSon();
	await terminerMicrotaches();
	assert.deepEqual(audio.appels, ['son']);
	assert.equal(b.appels.length, 0);
	assert.ok(audio.audioSalle.etat().sonDebloque);
	b.module.afficher(appel, capture);
	assert.equal(b.appels[0].args[2].motifParolesSeules, 'FICHIER_ABSENT');
});

test('salle : dossier et lecteur vivent entre les Manches, les commandes restent silencieuses et uniques', async t => {
	const audio = bancLecteur();
	const b = creerBanc((z, c) => ecran(z, c, audio), 'ECRAN');
	t.after(() => b.module.demonter());
	b.connecter('synchronisee');
	b.contexte.maintenantServeur = () => heureAppel;
	b.module.afficher(vue({ lecteurDesigneId: null }), capture);
	await audio.audioSalle.activerSon();
	assert.deepEqual(audio.appels, ['son']);
	const fichier = { name: 'Artiste fictif - Titre de démonstration.wav', size: 600, webkitRelativePath: 'privé/essai.wav' };
	audio.audioSalle.choisirDossier([fichier]);
	await terminerMicrotaches();
	const id = audio.audioSalle.lecteurId;
	assert.deepEqual(b.appels[0].args.slice(0, 3), ['PUT', '/api/soirees/s/manches/m/lecteur', { lecteurId: id }]);
	b.module.afficher(vue({ courant: courant(), lecteurDesigneId: id }), evenement);
	await terminerMicrotaches();
	assert.equal(b.appels[1].args[1], '/api/soirees/s/manches/m/passages/p/lecture');
	assert.equal(b.appels[1].args[2].fichier.nom, fichier.name);
	assert.doesNotMatch(b.principale.textContent, /privé|\.wav|Envoi/);
	const nouveauFichier = { name: 'Autre.wav', size: 715, webkitRelativePath: 'nouveau/Autre.wav' };
	audio.audioSalle.choisirDossier([nouveauFichier]);
	assert.deepEqual(audio.audioSalle.etat().fichiers, [nouveauFichier]);
	assert.equal(b.appels.length, 2, 'le cycle diffère le choix pour le Passage déjà préparé');
	b.module.afficher(vue({ courant: courant('EN_COURS'), lecteurDesigneId: id,
		lecture: { lecteurId: id, lectureId: 'local', source: 'FICHIER_LOCAL', etat: 'DEMARREE' } }), { source: 'evenement', type: 'passage-demarre' });
	await terminerMicrotaches();
	assert.ok(audio.appels.includes('lire'));
	b.module.afficher(vue({ mancheId: 'm2', lecteurDesigneId: null }), capture);
	assert.equal(audio.sonsDeManche.at(-1), false);
	assert.equal(audio.appels.at(-1), 'libérer');
	assert.deepEqual(b.appels.map(a => a.args[3].annoncer), [false, false, false]);
	assert.equal(new Set(b.appels.map(a => a.args[3].entetes['Idempotency-Key'])).size, 3);
	assert.deepEqual(b.appels[2].args.slice(0, 3), ['PUT', '/api/soirees/s/manches/m2/lecteur', { lecteurId: id }]);
	assert.equal(b.appels[0].args[3].signal.aborted, true);
});

test('les deux décomptes se rafraîchissent chaque seconde et libèrent leurs minuteries', t => {
	t.mock.timers.enable({ apis: ['setInterval'] });
	let lectures = 0, heure = heureAppel;
	const man = creerBanc(manette), con = creerBanc(consoleKaraoke, 'ANIMATEUR');
	for (const b of [man, con]) {
		t.after(() => b.module.demonter());
		b.contexte.maintenantServeur = () => { lectures++; return heure; };
	}
	man.module.afficher(vue({ courant: courant() }), capture);
	con.module.afficher(vue({ courant: courant('EN_COURS') }), capture);
	assert.match(man.principale.textContent, /30 s/);
	assert.match(con.principale.textContent, /Fin prévue dans 27 s/);
	heure += 1000;
	t.mock.timers.tick(1000);
	assert.match(man.principale.textContent, /29 s/);
	assert.match(con.principale.textContent, /Fin prévue dans 26 s/);
	man.module.demonter(); con.module.demonter();
	const avant = lectures;
	t.mock.timers.tick(3000);
	assert.equal(lectures, avant);
});

test('un appel sur capture restaure les quatre teintes personnelles sans détachement animé', t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heureAppel;
	for (const [numero, teinte] of ['orange', 'eau', 'bleu', 'rose'].entries()) {
		b.module.afficher(vue({ courant: courant('APPELE', { passageId: 'p' + numero, numero }) }), capture);
		const ticket = b.principale.querySelector('.mon-ticket--appele');
		assert.equal(ticket.style.getPropertyValue('--teinte'), `var(--${teinte})`);
		assert.equal(ticket.classList.contains('mon-ticket--detachement'), false);
	}
});

function voteDuPublic(id = 'p', numero = 14) {
	return { ...passage(id, numero), etat: 'OUVERT', interpretes: [{ joueurId: 'autre', pseudo: 'Lou' }],
		numeroModification: 3, maNote: null, peutVoter: true, calculeLe: '2026-11-18T21:00:00Z',
		delaiRestantMs: 30_000, clotureLe: null, moyenneDixiemes: null, nombreVotes: null };
}

function voteClos(vote, champs = {}) {
	return { ...vote, etat: 'CLOS', peutVoter: false, delaiRestantMs: 0, clotureLe: '2026-11-18T21:00:30Z',
		moyenneDixiemes: 43, nombreVotes: 7, ...champs };
}

test('Manette : cinq notes, une seule action citron et confirmation après le 200', async t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	const vote = voteDuPublic();
	const votes = [vote, voteDuPublic('q', 13)];
	const appel = { ...passage('appel', 15), etat: 'APPELE', etapeId: 'e', delaiAppelRestantMs: 30_000,
		calculeLe: vote.calculeLe };
	b.module.afficher(vue({ votes, courant: appel, peutConfirmerPresence: true }), capture);
	const section = b.principale.querySelector('.karaoke__votes');
	const premier = section.querySelector('article[data-passage-id=p]');
	assert.equal(b.principale.querySelector('h2').textContent, 'Vote du public');
	assert.deepEqual(premier.querySelectorAll('[aria-pressed=false]').map(action => action.textContent), ['1', '2', '3', '4', '5']);
	b.connecter('synchronisee');
	assert.equal(b.principale.querySelectorAll('.bouton--action').filter(action => !action.hidden).length, 1);
	assert.equal(bouton(b.principale, 'Je suis prêt').classList.contains('bouton--action'), true);
	b.module.afficher(vue({ votes }), evenement);
	assert.equal(section.querySelectorAll('.bouton--action').length, 1);
	const envoyer = bouton(premier, 'Envoyer ma note');
	assert.equal(envoyer.classList.contains('bouton--grand'), true);
	assert.equal(envoyer.disabled, true);
	bouton(premier, '4').click();
	assert.equal(bouton(premier, '4').getAttribute('aria-pressed'), 'true');
	b.connecter('coupure');
	assert.equal(envoyer.disabled, true);
	b.connecter('synchronisee');
	envoyer.click();
	assert.equal(envoyer.disabled, true);
	const [methode, chemin, corps, options] = b.appels[0].args;
	assert.equal(methode, 'POST');
	assert.equal(chemin, '/api/soirees/s/manches/m/passages/p/vote');
	assert.deepEqual(corps, { envoiId: options.entetes['Idempotency-Key'], numeroModification: 3, note: 4 });
	assert.equal(options.annoncer, false);
	assert.equal(premier.querySelector('.envoi--en-cours').textContent, 'Envoi de ta note…');
	b.module.afficher(vue({ votes: [{ ...vote, maNote: 4, peutVoter: false }, votes[1]] }), evenement);
	assert.ok(premier.querySelector('.envoi--accepte') === null);
	b.appels[0].resolve({ statut: 200, corps: { ...corps } });
	await terminerMicrotaches();
	assert.equal(premier.querySelector('.envoi--accepte').textContent, 'Ta note est enregistrée : 4 sur 5. Résultat à la fin du vote.');
});

test('Manette : après un refus définitif le détail est visible et une nouvelle tentative a une autre clé', async t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	const votes = [voteDuPublic()];
	b.module.afficher(vue({ votes }), capture);
	b.connecter('synchronisee');
	const ticket = b.principale.querySelector('.karaoke__ticket-vote');
	bouton(ticket, '4').click();
	bouton(ticket, 'Envoyer ma note').click();
	b.appels[0].resolve({ statut: 409, corps: { code: 'VOTE_CLOS', detail: 'Le vote de ce Passage est clos.' } });
	await terminerMicrotaches();
	b.module.afficher(vue({ votes }), capture);
	assert.equal(ticket.querySelector('.envoi--refuse').textContent, 'Le vote de ce Passage est clos.');
	bouton(ticket, 'Envoyer ma note').click();
	assert.equal(b.appels.length, 2);
	assert.notEqual(b.appels[1].args[2].envoiId, b.appels[0].args[2].envoiId);
});

test('Console : les votes et le délai figurent sous le Passage courant, le délai est remplacé en pause', t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => Date.parse('2026-11-18T21:00:12Z');
	const vote = { ...voteDuPublic(), nombreVotes: 7 };
	b.module.afficher(vue({ votes: [vote] }), capture);
	const section = b.principale.querySelector('.karaoke__votes');
	assert.match(section.textContent, /n° 14 · Titre de démonstration · 7 votes · 18 s/);
	const titres = b.principale.querySelectorAll('h2').map(titre => titre.textContent);
	assert.equal(titres[titres.indexOf('Passage courant') + 1], 'Vote du public');
	b.module.afficher(vue({ votes: [vote], etat: 'EN_PAUSE' }), evenement);
	assert.match(section.textContent, /7 votes · en pause/);
	assert.doesNotMatch(section.textContent, /18 s/);
	b.module.afficher(vue({ votes: [voteClos(vote)] }), evenement);
	assert.match(section.textContent, /Vote clos · 4,3 sur 5 · 7 votes/);
	b.module.afficher(vue({ votes: [voteClos(vote, { moyenneDixiemes: null, nombreVotes: 0 })] }), capture);
	assert.match(section.textContent, /Vote clos · Aucun vote$/);
});

test('Manette : la clôture annonce la moyenne une fois par Passage, même à l’appel automatique', t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	const vote = { ...voteDuPublic(), maNote: 4, peutVoter: false };
	const autre = voteDuPublic('q', 15);
	const section = b.principale.querySelector('.karaoke__votes');
	const annonce = b.principale.querySelector('.karaoke__annonce-vote');
	assert.equal(annonce.getAttribute('role'), 'status');
	const affichage = vue({ votes: [vote, autre] });
	b.module.afficher(affichage, capture);
	assert.doesNotMatch(section.textContent, /Moyenne|\d+ votes|Aucun vote/);
	const resultat = voteClos(vote);
	const apres = vue({ votes: [resultat, autre] });
	b.module.afficher(apres, { source: 'evenement', type: 'passage-appele' });
	assert.match(section.textContent, /Moyenne : 4,3 sur 5 \(7 votes\)\.Ta note : 4 sur 5\./);
	assert.equal(annonce.textContent, 'Passage 14. Moyenne : 4,3 sur 5 (7 votes).');
	assert.equal(b.principale.querySelectorAll('[role=status]').filter(p => p.textContent.includes('7 votes')).length, 1);
	b.module.afficher(apres, capture);
	assert.equal(annonce.textContent, '');
	assert.match(section.textContent, /4,3 sur 5/);
	b.module.afficher(affichage, capture);
	b.module.afficher(apres, { source: 'evenement', type: 'vote-clos' });
	assert.equal(annonce.textContent, '');
	const sansVote = voteClos(autre, { moyenneDixiemes: null, nombreVotes: 0 });
	b.module.afficher(vue({ votes: [sansVote] }), { source: 'evenement', type: 'vote-clos' });
	assert.equal(annonce.textContent, 'Passage 15. Aucun vote.');
	assert.match(section.textContent, /Aucun vote\./);
});

test('Manette : une clôture découverte en capture affiche la moyenne sans annoncer de résultat', t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	const vote = voteDuPublic();
	b.module.afficher(vue({ votes: [vote] }), capture);
	const apres = vue({ votes: [voteClos(vote)] });
	b.module.afficher(apres, capture);
	b.module.afficher(apres, { source: 'evenement', type: 'vote-clos' });
	assert.match(b.principale.querySelector('.karaoke__votes').textContent, /Moyenne : 4,3 sur 5 \(7 votes\)\./);
	assert.equal(b.principale.querySelector('.karaoke__annonce-vote').textContent, '');
});

test('Salle : deux bandeaux stables au-dessus du rouleau, avec compte des votes et temps figé sous Pause', t => {
	const b = creerBanc(ecran, 'ECRAN');
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => Date.parse('2026-11-18T21:00:12Z');
	const a = { ...voteDuPublic('a', 14), nombreVotes: 0, suivantDemarre: false };
	const vote = voteDuPublic('b', 15);
	const recent = { ...vote, chanson: { ...vote.chanson, titre: '<Chanson>' }, nombreVotes: 1, suivantDemarre: false };
	const courantAppele = courant('APPELE', { passageId: 'c' });
	const prochainsPassages = [passage('d', 16)];
	b.module.afficher(vue({ votes: [recent, a], courant: courantAppele, prochainsPassages }), capture);
	const bandeaux = b.laterale.querySelectorAll('.bandeau-vote');
	assert.deepEqual(bandeaux.map(p => p.getAttribute('data-passage-id')), ['b', 'a']);
	const [premier, second] = bandeaux;
	const rouleau = b.laterale.querySelector('.rouleau');
	assert.ok(rouleau && rouleau.parentNode.children[0] === premier.parentNode);
	assert.equal(premier.style.getPropertyValue('--teinte'), 'var(--rose)');
	assert.match(b.principale.querySelector('.karaoke__invitation').textContent, /Prépare-toi/);
	assert.match(premier.textContent, /15.*<Chanson>.*Lou.*Note ce Passage sur ta Manette.*1 vote.*18 s/);
	assert.match(second.textContent, /Aucun vote/);
	assert.equal(b.laterale.querySelectorAll('[role=status]').length, 0);
	assert.ok(!premier.querySelector('.bouton--action'));
	b.module.afficher(vue({ votes: [{ ...recent, nombreVotes: 7 }, a], courant: courantAppele, prochainsPassages,
		etat: 'EN_PAUSE' }), { source: 'evenement', type: 'vote-enregistre' });
	assert.ok(b.laterale.querySelector('.bandeau-vote') === premier);
	assert.match(premier.textContent, /7 votes.*Pause.*30 s/);
	assert.doesNotMatch(premier.textContent, /sur 5/);
});

test('Salle : la boucle masque le résultat à l’échéance, même en pause et sans Passage en cours', t => {
	const audio = bancLecteur();
	const b = creerBanc((zones, contexte) => ecran(zones, contexte, audio), 'ECRAN');
	t.after(() => b.module.demonter());
	const cloture = Date.parse('2026-11-18T21:00:30Z');
	let maintenant = cloture + 9999;
	b.contexte.maintenantServeur = () => maintenant;
	const resultat = voteClos(voteDuPublic(), { suivantDemarre: true });
	const configurationInterface = { ...vue().karaoke.configurationInterface, dureeAffichageMoyenneMs: 10_000 };
	b.module.afficher(vue({ etat: 'EN_PAUSE', votes: [resultat], configurationInterface }), capture);
	assert.match(b.laterale.querySelector('.bandeau-vote').textContent, /4,3 sur 5.*7 votes/);
	assert.equal(audio.trames.size, 1);
	const [[id, trame]] = audio.trames;
	audio.trames.delete(id);
	maintenant++;
	trame();
	assert.equal(b.laterale.querySelectorAll('.bandeau-vote').length, 0);
	assert.equal(audio.trames.size, 0);
});

test('Salle : « Aucun vote » seul à la clôture, en Atkinson et sans aplat citron', async t => {
	const b = creerBanc(ecran, 'ECRAN');
	t.after(() => b.module.demonter());
	const resultat = voteClos(voteDuPublic(), { moyenneDixiemes: null, nombreVotes: 0, suivantDemarre: false });
	b.module.afficher(vue({ votes: [resultat] }), capture);
	const bandeau = b.laterale.querySelector('.bandeau-vote');
	assert.equal(bandeau.querySelector('.bandeau-vote__nombre').textContent, 'Aucun vote');
	assert.ok(bandeau.querySelector('.bandeau-vote__moyenne').hidden);
	const css = await readFile(new URL('../../src/main/resources/static/css/karaoke.css', import.meta.url), 'utf8');
	assert.match(css, /\.bandeau-vote\s*\{[^}]*var\(--police-texte\)/s);
	assert.doesNotMatch(css.match(/[^{}]*\.bandeau-vote[^{}]*\{[^}]*\}/g).join(''), /var\(--citron/);
});

const AVEC_CHANSON = { version: 1, licences: { 'CC-BY-SA-4.0': { nom: 'CC BY-SA 4.0',
	url: 'https://creativecommons.org/licenses/by-sa/4.0/deed.fr' } }, entrees: [{ type: 'chanson', numero: 1, titre: 'La lanterne',
	artiste: 'Les Lucioles', dureeMs: 4000, licence: 'CC-BY-SA-4.0', source: 'https://example.org/la-lanterne',
	fichier: 'chansons/les-lucioles-la-lanterne.mp3', tailleOctets: 2400, lrc: 'chansons/les-lucioles-la-lanterne.lrc' }] };

test('salle : « Activer le son » sans dossier choisit un lecteur libre, ou reprend la désignation au prochain intervalle', async t => {
	const audio = bancLecteur({ bibliotheque: AVEC_CHANSON });
	const b = creerBanc((z, c) => ecran(z, c, audio), 'ECRAN');
	t.after(() => b.module.demonter());
	b.connecter('synchronisee');
	b.contexte.maintenantServeur = () => heureAppel;
	b.module.afficher(vue({ lecteurDesigneId: 'ailleurs', courant: courant('EN_COURS') }), capture);
	await audio.audioSalle.activerSon();
	await terminerMicrotaches();
	assert.deepEqual(b.appels.map(appel => appel.args[1]), [], 'un autre Écran lit déjà : il le reste');
	b.module.afficher(vue({ lecteurDesigneId: 'ailleurs' }), evenement);
	assert.deepEqual(b.appels.map(appel => [appel.args[0], appel.args[1].split('/').at(-1)]), [['PUT', 'lecteur']]);

	const seule = bancLecteur({ bibliotheque: AVEC_CHANSON });
	const c = creerBanc((z, contexte) => ecran(z, contexte, seule), 'ECRAN');
	t.after(() => c.module.demonter());
	c.connecter('synchronisee');
	c.contexte.maintenantServeur = () => heureAppel;
	c.module.afficher(vue({ lecteurDesigneId: null }), capture);
	assert.deepEqual(c.appels, [], 'le son n’est pas activé : aucune désignation');
	await seule.audioSalle.activerSon();
	await terminerMicrotaches();
	assert.deepEqual(c.appels.map(appel => [appel.args[0], appel.args[1].split('/').at(-1)]), [['PUT', 'lecteur']]);
});

test('salle : après rechargement sans dossier, le nouveau lecteur reprend l’appel et joue au départ', { timeout: 3000 }, async t => {
	const soireeId = 'rechargement-karaoke';
	let identifiantRetenu;
	const memoireLecteur = { lire: () => identifiantRetenu, retenir: id => { identifiantRetenu = id; } };
	const premiere = bancLecteur({ bibliotheque: AVEC_CHANSON, lecteurId: 'disparu', soireeId, memoireLecteur });
	const ancien = creerBanc((z, c) => ecran(z, c, premiere), 'ECRAN');
	ancien.connecter('synchronisee');
	ancien.contexte.maintenantServeur = () => heureAppel;
	ancien.module.afficher(vue({ lecteurDesigneId: null }), capture);
	await premiere.audioSalle.activerSon();
	assert.deepEqual(ancien.appels.map(a => a.args[2]), [{ lecteurId: 'disparu' }]);
	ancien.module.demonter();
	await premiere.audioSalle.detruire();
	const audio = bancLecteur({ bibliotheque: AVEC_CHANSON, lecteurId: 'nouveau', soireeId, memoireLecteur });
	const b = creerBanc((z, c) => ecran(z, c, audio), 'ECRAN');
	t.after(() => { b.module.demonter(); return audio.audioSalle.detruire(); });
	b.connecter('synchronisee');
	b.contexte.maintenantServeur = () => heureAppel;
	const chanson = { titre: 'La lanterne', artiste: 'Les Lucioles', origine: 'BIBLIOTHEQUE',
		bibliotheque: { fichier: '/bibliotheque/chansons/les-lucioles-la-lanterne.mp3', tailleOctets: 2400 } };
	const appele = courant('APPELE', { chanson });
	b.module.afficher(vue({ lecteurDesigneId: 'disparu', courant: appele }), capture);
	assert.deepEqual(b.appels, []);
	await audio.audioSalle.activerSon();
	await terminerMicrotaches();
	assert.deepEqual(b.appels.map(a => a.args[2]), [{ lecteurId: 'nouveau' }]);
	b.module.afficher(vue({ lecteurDesigneId: 'nouveau', courant: appele }), evenement);
	await terminerMicrotaches();
	assert.equal(b.appels[1].args[2].fichier.nom, 'les-lucioles-la-lanterne.mp3');
	b.appels[1].resolve({ statut: 200 });
	await terminerMicrotaches();
	b.module.afficher(vue({ lecteurDesigneId: 'nouveau', courant: { ...appele, etat: 'EN_COURS' },
		lecture: { lecteurId: 'nouveau', lectureId: 'l', source: 'FICHIER_LOCAL', etat: 'DEMARREE' } }),
	{ source: 'evenement', type: 'passage-demarre' });
	await terminerMicrotaches();
	assert.ok(audio.appels.includes('lire'));
});

test('salle : un lecteur d’un autre navigateur garde la désignation pendant son appel', async t => {
	const audio = bancLecteur({ bibliotheque: AVEC_CHANSON, soireeId: 'lecteur-exterieur' });
	const b = creerBanc((z, c) => ecran(z, c, audio), 'ECRAN');
	t.after(() => { b.module.demonter(); return audio.audioSalle.detruire(); });
	b.connecter('synchronisee');
	b.contexte.maintenantServeur = () => heureAppel;
	b.module.afficher(vue({ lecteurDesigneId: 'autre-navigateur', courant: courant('APPELE') }), capture);
	await audio.audioSalle.activerSon();
	await terminerMicrotaches();
	assert.deepEqual(b.appels, []);
});

test('salle : le crédit d’une Chanson de la Bibliothèque de base reste affiché pendant tout son Passage', t => {
	const b = creerBanc((z, c) => ecran(z, c), 'ECRAN');
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heureAppel;
	const libre = { ...chanson, titre: 'La lanterne', artiste: 'Les Lucioles', origine: 'BIBLIOTHEQUE', bibliotheque: {
		fichier: '/bibliotheque/chansons/les-lucioles-la-lanterne.mp3', tailleOctets: 2400, licence: 'CC BY-SA 4.0',
		urlLicence: 'https://creativecommons.org/licenses/by-sa/4.0/deed.fr', urlSource: 'https://example.org/la-lanterne' } };
	const credit = () => b.principale.querySelector('.karaoke__credit');
	const paroles = { parolesId: 'v1', dureeMs: 4000, lignes: [{ positionMs: 0, texte: 'Une lanterne' }] };
	const lecture = { lectureId: 'l', source: 'PAROLES_SEULES', decalageMs: 0 };
	b.module.afficher(vue({ courant: courant('APPELE', { chanson: libre }) }), capture);
	assert.equal(credit().hidden, true, 'pas encore pendant l’appel');
	b.module.afficher(vue({ courant: courant('EN_COURS', { chanson: libre }), paroles, lecture }), capture);
	assert.equal(credit().hidden, false);
	assert.equal(credit().textContent, 'Chanson : « La lanterne », Les Lucioles · CC BY-SA 4.0');
	b.module.afficher(vue({ courant: courant('EN_COURS', { chanson: { ...chanson, origine: 'LRCLIB', bibliotheque: null } }), paroles, lecture }),
		capture);
	assert.equal(credit().hidden, true);
});
