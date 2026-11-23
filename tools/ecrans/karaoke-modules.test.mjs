import test from 'node:test';
import assert from 'node:assert/strict';
import { creerBanc, creerDocument, terminerMicrotaches, chanson, passage, vue } from './karaoke-dom-factice.mjs';
import { monter as manette } from '../../src/main/resources/static/js/modes/karaoke/manette.mjs';
import { monter as consoleKaraoke } from '../../src/main/resources/static/js/modes/karaoke/console.mjs';
import { monter as ecran } from '../../src/main/resources/static/js/modes/karaoke/ecran.mjs';
import { readFile } from 'node:fs/promises';
const capture = { source: 'capture', type: 'capture' }, evenement = { source: 'evenement', type: 'karaoke-file-modifiee' };
const bouton = (zone, texte) => zone.querySelectorAll('button').find(b => b.textContent === texte);

test('les trois écrans partagent un lien CSS, conservent le talon et libèrent zones et abonnement', () => {
	const document = creerDocument(), talon = document.createElement('div'); talon.textContent = 'DEMO01'; document.body.append(talon);
	const bancs = [creerBanc(manette, 'JOUEUR', document), creerBanc(consoleKaraoke, 'ANIMATEUR', document), creerBanc(ecran, 'ECRAN', document)];
	assert.equal(document.head.querySelectorAll('link[data-mode=karaoke]').length, 1);
	for (const b of bancs) { b.module.afficher(vue(), capture); b.module.demonter(); assert.equal(b.principale.childElementCount, 0); assert.equal(b.laterale.childElementCount, 0); assert.equal(b.abonnee(), false); }
	assert.equal(document.head.childElementCount, 0); assert.equal(talon.textContent, 'DEMO01');
});

test('Console : sélection unique, placement figé et priorité invalidée si l’ancienne disparaît', () => {
	const p = passage('p', 12), q = { ...passage('q', 13), position: 1, prioritaire: true };
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR'); b.connecter('synchronisee');
	b.module.afficher(vue({ passagesEnAttente: [p, q], passagePrioritaireId: 'q' }), evenement);
	assert.equal(b.principale.querySelector('[aria-pressed=true]'), null);
	b.principale.querySelector('button[data-passage-id=p]').click();
	assert.equal(b.principale.querySelector('[aria-pressed=true]').getAttribute('data-passage-id'), 'p');
	bouton(b.principale, 'Priorité').click();
	assert.match(b.principale.querySelector('dialog').textContent, /Remplace la priorité du Passage 13/);
	b.module.afficher(vue({ passagesEnAttente: [p, q, passage('etranger', 16)], passagePrioritaireId: 'q' }), evenement);
	assert.equal(b.principale.querySelector('dialog').open, true);
	b.module.afficher(vue({ passagesEnAttente: [p], passagePrioritaireId: null }), evenement);
	assert.equal(b.principale.querySelector('dialog'), null); assert.match(b.principale.textContent, /Le Passage a changé/); assert.equal(b.appels.length, 0);
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
	assert.equal(b.principale.querySelector('dialog'), null); assert.match(b.principale.textContent, /Le Passage a changé/);
	bouton(b.principale, 'Retirer').click();
	b.module.afficher(vue({ mancheId: 'suivante', passagesEnAttente: [] }), capture);
	assert.equal(b.principale.querySelector('dialog'), null); assert.match(b.principale.textContent, /Le Passage a changé/);
	assert.equal(b.appels.length, 0); b.module.demonter();
});

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
	assert.equal(b.captures(), 1); assert.equal(bouton(b.principale, 'Réessayer le même envoi'), undefined);
	b.connecter('synchronisation'); b.module.afficher(vue({ passagesEnAttente: [{ ...p, numeroModification: 4 }] }), capture);
	assert.equal(bouton(b.principale, 'Réessayer le même envoi'), undefined);
	b.connecter('synchronisee'); const reessai = bouton(b.principale, 'Réessayer le même envoi'); reessai.click(); reessai.click();
	assert.equal(b.appels.length, 2); assert.deepEqual(b.appels[1].args[2], b.appels[0].args[2]);
	assert.equal(b.appels[1].args[3].entetes['Idempotency-Key'], b.appels[0].args[3].entetes['Idempotency-Key']);
	b.appels[1].resolve({ statut: 409, corps: { code: 'COMMANDE_PERIMEE', detail: 'Trop tard' } }); await terminerMicrotaches();
	b.module.afficher(vue({ passagesEnAttente: [p] }), capture); assert.equal(bouton(b.principale, 'Réessayer le même envoi'), undefined);
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
	assert.equal(bouton(b.principale, 'Réessayer le même envoi'), undefined); b.module.demonter();
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
	assert.match(b.principale.querySelector('dialog').textContent, /Source disparue/); assert.equal(bouton(b.principale, "M'inscrire"), undefined);
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

test('salle : le premier Passage en grand, seulement les suivants dans « Ensuite », pas de commande ni de détachement', () => {
	const b = creerBanc(ecran, 'ECRAN');
	b.module.afficher(vue({ prochainsPassages: [passage(), passage('p2', 15)], nombreAutresPassages: 1 }), capture);
	assert.match(b.principale.textContent, /Prépare-toi/); assert.equal(b.principale.querySelectorAll('.ticket').length, 1);
	assert.equal(b.laterale.querySelectorAll('.ticket').length, 1, 'le premier Passage n’est pas répété');
	assert.match(b.laterale.textContent, /et 1 autre$/);
	b.module.afficher(vue({ prochainsPassages: [passage()], nombreAutresPassages: 2 }), capture);
	assert.equal(b.laterale.querySelectorAll('.ticket').length, 0); assert.match(b.laterale.textContent, /et 2 autres/);
	assert.equal(b.document.body.querySelectorAll('button').length, 0);
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
	assert.equal(bouton(b.principale, chanson.titre), undefined);
	champ.value = 'nouveau'; b.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	b.module.demonter(); assert.equal(b.appels[1].args[3].signal.aborted, true);
	b.appels[1].reject(new Error()); await terminerMicrotaches(); assert.equal(b.principale.childElementCount, 0);
	assert.equal(b.captures(), 0);
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
