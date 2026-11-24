import test from 'node:test';
import assert from 'node:assert/strict';
import { creerBanc, creerDocument, terminerMicrotaches, chanson, passage, vue } from './karaoke-dom-factice.mjs';
import { monter as manette } from '../../src/main/resources/static/js/modes/karaoke/manette.mjs';
import { monter as consoleKaraoke } from '../../src/main/resources/static/js/modes/karaoke/console.mjs';
import { creerEcranKaraoke, reinitialiserEtatEcranPourTests, etatEcranKaraoke } from '../../src/main/resources/static/js/modes/karaoke/ecran.mjs';
import { readFile } from 'node:fs/promises';
const capture = { source: 'capture', type: 'capture' }, evenement = { source: 'evenement', type: 'karaoke-file-modifiee' };
const bouton = (zone, texte) => zone.querySelectorAll('button').find(b => b.textContent === texte);

function bancLecteur() {
	const appels = [], trames = new Map();
	let numero = 0;
	const lecteur = {
		debloquer: async () => { appels.push('son'); },
		charger: async () => { appels.push('charger'); return { dureeMs: 2000 }; },
		lire: async () => { appels.push('lire'); },
		pause: () => appels.push('pause'),
		reprendre: async () => { appels.push('reprendre'); },
		position: () => 1500,
		surFin: () => () => {},
		surErreur: () => () => {},
		detruire: () => appels.push('libérer'),
	};
	return { lecteur, appels, trames,
		dependances: { lecteur, requestAnimationFrame(f) { trames.set(++numero, f); return numero; },
			cancelAnimationFrame: id => trames.delete(id), setTimeout, clearTimeout, performance } };
}
function ecran(zones, contexte) {
	reinitialiserEtatEcranPourTests();
	return creerEcranKaraoke(zones, contexte, { ...bancLecteur().dependances, document: zones.principale.ownerDocument });
}

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
		assert.equal(b.document.activeElement, bouton(dialogue, 'Annuler'));
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
	assert.equal(b.captures(), 1); assert.equal(bouton(b.principale, 'Réessayer le même envoi'), undefined);
	b.connecter('synchronisation'); b.module.afficher(vue({ passagesEnAttente: [{ ...p, numeroModification: 4 }] }), capture);
	assert.equal(bouton(b.principale, 'Réessayer le même envoi'), undefined);
	b.connecter('synchronisee'); const reessai = bouton(b.principale, 'Réessayer le même envoi'); reessai.click(); reessai.click();
	assert.equal(b.appels.length, 2); assert.deepEqual(b.appels[1].args[2], b.appels[0].args[2]);
	assert.equal(b.appels[1].args[3].entetes['Idempotency-Key'], b.appels[0].args[3].entetes['Idempotency-Key']);
	b.appels[1].resolve({ statut: 409, corps: { code: 'COMMANDE_PERIMEE', detail: 'Trop tard' } }); await terminerMicrotaches();
	assert.equal(b.captures(), 2);
	assert.equal(bouton(b.principale, 'Appliquer').disabled, true);
	b.module.afficher(vue({ passagesEnAttente: [p] }), evenement);
	assert.equal(bouton(b.principale, 'Appliquer').disabled, true);
	b.module.afficher(vue({ passagesEnAttente: [p] }), capture); assert.equal(bouton(b.principale, 'Réessayer le même envoi'), undefined);
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

test('salle : le premier Passage en grand, seulement les suivants dans « Ensuite », seul le choix du dossier est proposé', () => {
	const b = creerBanc(ecran, 'ECRAN');
	b.module.afficher(vue({ prochainsPassages: [passage(), passage('p2', 15)], nombreAutresPassages: 1 }), capture);
	assert.match(b.principale.textContent, /Prépare-toi/); assert.equal(b.principale.querySelectorAll('.ticket').length, 1);
	assert.equal(b.laterale.querySelectorAll('.ticket').length, 1, 'le premier Passage n’est pas répété');
	assert.match(b.laterale.textContent, /et 1 autre$/);
	b.module.afficher(vue({ prochainsPassages: [passage()], nombreAutresPassages: 2 }), capture);
	assert.equal(b.laterale.querySelectorAll('.ticket').length, 0); assert.match(b.laterale.textContent, /et 2 autres/);
	assert.deepEqual(b.document.body.querySelectorAll('button').filter(b => !b.hidden).map(b => b.textContent), ['Choisir le dossier de musique']);
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
	assert.equal(bouton(b.principale, 'Réessayer le même envoi'), undefined);
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
	assert.equal(b.principale.querySelector('.mon-ticket--appele'), null);
	b.module.afficher(vue({ courant: courant('SAUTE', { interpretes: [{ joueurId: 'autre', pseudo: 'Alizé' }] }) }), capture);
	assert.ok(b.principale.querySelector('.ticket--bleu'));
	assert.equal(bouton(b.principale, 'Je suis prêt'), undefined);
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
	reinitialiserEtatEcranPourTests();
	const lecteur = bancLecteur();
	const b = creerBanc((z, c) => creerEcranKaraoke(z, c, { ...lecteur.dependances, document: z.principale.ownerDocument }), 'ECRAN');
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heureAppel;
	const prochainsPassages = [passage('a'), passage('b'), passage('c')];
	const principaux = () => b.principale.querySelector('.karaoke__prochain');
	const afficher = (courant, origine, champs = {}) => b.module.afficher(vue({ courant, prochainsPassages, nombreAutresPassages: 2, ...champs }), origine);
	afficher(courant(), { source: 'evenement', type: 'passage-appele' });
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

test('salle : zéro fichier au singulier et activation sonore locale pendant une coupure', async t => {
	reinitialiserEtatEcranPourTests();
	const audio = bancLecteur();
	etatEcranKaraoke.dossier = 'Dossier vide';
	const b = creerBanc((z, c) => creerEcranKaraoke(z, c, { ...audio.dependances, document: z.principale.ownerDocument }), 'ECRAN');
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heureAppel;
	const appel = vue({ courant: courant(), lecteurDesigneId: etatEcranKaraoke.lecteurId });
	b.module.afficher(appel, capture);
	b.connecter('coupure');
	assert.match(b.principale.textContent, /0 fichier audio/);
	const activer = bouton(b.principale, 'Activer le son');
	assert.ok(!activer.hidden && !activer.disabled);
	activer.focus();
	activer.click();
	await terminerMicrotaches();
	assert.deepEqual(audio.appels, ['son']);
	assert.equal(b.appels.length, 0);
	assert.ok(activer.hidden && b.document.activeElement === activer.parentNode);
	b.module.afficher(appel, capture);
	assert.equal(b.appels[0].args[2].motifParolesSeules, 'FICHIER_ABSENT');
});

test('salle : dossier et lecteur vivent entre les Manches, les commandes restent silencieuses et uniques', async t => {
	reinitialiserEtatEcranPourTests();
	const audio = bancLecteur();
	const b = creerBanc((z, c) => creerEcranKaraoke(z, c, { ...audio.dependances, document: z.principale.ownerDocument }), 'ECRAN');
	t.after(() => b.module.demonter());
	b.connecter('synchronisee');
	b.contexte.maintenantServeur = () => heureAppel;
	b.module.afficher(vue({ lecteurDesigneId: null }), capture);
	const choisir = bouton(b.principale, 'Choisir le dossier de musique');
	choisir.click();
	assert.deepEqual(audio.appels, ['son']);
	const input = b.principale.querySelector('input[webkitdirectory]');
	input.files = [{ name: 'Artiste fictif - Titre de démonstration.wav', size: 600, webkitRelativePath: 'privé/essai.wav' }];
	input.dispatchEvent({ type: 'change' });
	assert.equal(etatEcranKaraoke.dossier, 'privé');
	await terminerMicrotaches();
	const id = etatEcranKaraoke.lecteurId;
	assert.deepEqual(b.appels[0].args.slice(0, 3), ['PUT', '/api/soirees/s/manches/m/lecteur', { lecteurId: id }]);
	b.module.afficher(vue({ courant: courant(), lecteurDesigneId: id }), evenement);
	await terminerMicrotaches();
	assert.equal(b.appels[1].args[1], '/api/soirees/s/manches/m/passages/p/lecture');
	assert.equal(b.appels[1].args[2].fichier.nom, input.files[0].name);
	assert.doesNotMatch(b.principale.textContent, /privé|\.wav|Envoi/);
	const nouveauFichier = { name: 'Autre.wav', size: 715, webkitRelativePath: 'nouveau/Autre.wav' };
	input.files = [nouveauFichier]; input.dispatchEvent({ type: 'change' });
	assert.equal(etatEcranKaraoke.dossier, 'nouveau');
	assert.deepEqual(etatEcranKaraoke.fichiers, [nouveauFichier]);
	assert.equal(b.appels.length, 2, 'le cycle diffère le choix pour le Passage déjà préparé');
	b.module.afficher(vue({ courant: courant('EN_COURS'), lecteurDesigneId: id,
		lecture: { lecteurId: id, lectureId: 'local', source: 'FICHIER_LOCAL', etat: 'DEMARREE' } }), { source: 'evenement', type: 'passage-demarre' });
	await terminerMicrotaches();
	assert.ok(audio.appels.includes('lire'));
	b.module.afficher(vue({ mancheId: 'm2', lecteurDesigneId: null }), capture);
	assert.equal(etatEcranKaraoke.lecteur, audio.lecteur);
	assert.equal(etatEcranKaraoke.lecteurId, id);
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
