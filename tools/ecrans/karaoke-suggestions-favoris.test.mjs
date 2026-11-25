import test from 'node:test';
import assert from 'node:assert/strict';
import { chanson, creerBanc, terminerMicrotaches, vue } from './karaoke-dom-factice.mjs';
import { monter as manette } from '../../src/main/resources/static/js/modes/karaoke/manette.mjs';
import { monter as consoleKaraoke } from '../../src/main/resources/static/js/modes/karaoke/console.mjs';

const capture = { source: 'capture' };
const bouton = (zone, texte) => zone.querySelectorAll('button').find(b => b.textContent === texte);

test('Manette : la suggestion est confirmée par le reçu même lorsque le quota est atteint', async t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ peutInscrire: false, motifInscriptionRefusee: 'LIMITE_ATTEINTE' }), capture);
	b.connecter('synchronisee');
	bouton(b.principale, 'Suggérer une Chanson').click();
	const formulaire = b.principale.querySelector('form.karaoke__suggestion');
	const [titre, artiste] = formulaire.querySelectorAll('input');
	const statut = formulaire.querySelector('[role=status]');
	const envoyer = bouton(formulaire, 'Envoyer la suggestion');
	assert.equal(b.document.activeElement === titre, true);

	b.connecter('coupure');
	assert.equal(envoyer.getAttribute('aria-disabled'), 'true');
	assert.equal(envoyer.disabled, false);
	b.connecter('synchronisee');
	titre.value = 'É'.repeat(121);
	artiste.value = 'Les Accords';
	formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels.length, 0);
	assert.equal(titre.getAttribute('aria-invalid'), 'true');

	titre.value = 'Éclat du soir';
	formulaire.dispatchEvent({ type: 'submit' });
	const [methode, chemin, corps, options] = b.appels[0].args;
	assert.equal(methode, 'POST');
	assert.equal(chemin, '/api/soirees/s/manches/m/suggestions');
	assert.deepEqual(corps, { titre: 'Éclat du soir', artiste: 'Les Accords' });
	assert.equal(options.entetes['Idempotency-Key'].length, 36);
	assert.equal(options.annoncer, false);
	assert.equal(envoyer.getAttribute('aria-disabled'), 'true');
	assert.equal(statut.textContent, 'Envoi en cours…');
	b.module.afficher(vue(), { source: 'evenement', type: 'file-attente-modifiee' });
	assert.doesNotMatch(statut.textContent, /envoyée/);

	b.appels[0].resolve({ statut: 200, corps: { envoiId: options.entetes['Idempotency-Key'] } });
	await terminerMicrotaches();
	assert.equal(statut.textContent, 'Suggestion envoyée.');
	assert.equal(titre.value, '');
	assert.equal(artiste.value, '');
});

test('Manette : le refus conserve la saisie et le démontage annule la requête suivante', async t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.module.afficher(vue(), capture);
	b.connecter('synchronisee');
	bouton(b.principale, 'Suggérer une Chanson').click();
	const formulaire = b.principale.querySelector('form.karaoke__suggestion');
	const [titre, artiste] = formulaire.querySelectorAll('input');
	titre.value = 'Éclat du soir';
	artiste.value = 'Les Accords';
	formulaire.dispatchEvent({ type: 'submit' });
	b.appels[0].resolve({ statut: 400, corps: { code: 'REQUETE_INVALIDE', detail: 'Suggestion invalide.' } });
	await terminerMicrotaches();
	assert.equal(formulaire.querySelector('[role=status]').textContent, 'Suggestion invalide.');
	assert.equal(titre.value, 'Éclat du soir');
	formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels.length, 2);
	b.module.demonter();
	assert.equal(b.appels[1].args[3].signal.aborted, true);
});

test('Console : la suggestion associe le compteur au retrait et disparaît sur la vue publiée', async t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	const suggestion = { suggestionId: 'a', titre: 'Éclat du soir', artiste: 'Les Accords', nombreJoueurs: 2 };
	b.module.afficher(vue({ suggestions: [suggestion] }), capture);
	const section = b.laterale.querySelector('.karaoke__suggestions');
	assert.match(section.textContent, /Éclat du soir.*Les Accords.*2 Joueurs/);
	const retirer = bouton(section, 'Retirer');
	assert.equal(retirer.getAttribute('aria-disabled'), 'true');
	retirer.click();
	assert.equal(b.appels.length, 0);
	b.connecter('synchronisee');
	bouton(section, 'Retirer').click();
	const [methode, chemin, corps, options] = b.appels[0].args;
	assert.equal(methode, 'POST');
	assert.equal(chemin, '/api/soirees/s/manches/m/suggestions/a/retrait');
	assert.deepEqual(corps, { nombreJoueurs: 2 });
	assert.equal(options.entetes['Idempotency-Key'].length, 36);

	b.appels[0].resolve({ statut: 200, corps: {} });
	await terminerMicrotaches();
	assert.match(section.textContent, /Éclat du soir/);
	b.module.afficher(vue({ suggestions: [] }), { source: 'evenement', type: 'suggestions-chansons-modifiees' });
	assert.match(section.textContent, /Aucune suggestion/);
	assert.doesNotMatch(section.textContent, /Éclat du soir/);
});

test('Console : la fiche définit un favori et suit la Vue synchronisée', async t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ chansonsFavorites: [] }), capture);
	b.connecter('synchronisee');
	const recherche = b.laterale.querySelector('form');
	recherche.querySelector('input').value = 'Titre';
	recherche.dispatchEvent({ type: 'submit' });
	b.appels[0].resolve({ statut: 200, corps: { recherche: 'Titre', chansons: [chanson] } });
	await terminerMicrotaches();
	bouton(b.laterale, chanson.titre).click();
	b.appels[1].resolve({ statut: 200, corps: { chanson, inscriptionPossible: false, motifIndisponibilite: 'ANIMATEUR' } });
	await terminerMicrotaches();
	const dialogue = b.principale.querySelector('dialog');
	bouton(dialogue, 'Ajouter aux favoris').click();
	const [methode, chemin, corps, options] = b.appels[2].args;
	assert.equal(methode, 'PUT');
	assert.equal(chemin, '/api/soirees/s/manches/m/chansons/c/favori');
	assert.deepEqual(corps, { favorite: true });
	assert.equal(options.entetes['Idempotency-Key'].length, 36);

	b.appels[2].resolve({ statut: 200, corps: {} });
	await terminerMicrotaches();
	assert.equal(Boolean(bouton(dialogue, 'Ajouter aux favoris')), true);
	b.module.afficher(vue({ chansonsFavorites: ['c'] }), { source: 'evenement', type: 'favoris-chansons-modifies' });
	assert.equal(bouton(dialogue, 'Retirer des favoris').getAttribute('aria-disabled'), null);
	assert.equal(b.appels.length, 3);
});

const autre = { ...chanson, chansonId: 'd', titre: 'Autre titre' };
const ordreResultats = zone => zone.querySelectorAll('[data-chanson-id]').map(e => e.getAttribute('data-chanson-id'));

async function ouvrirFicheConsole(b, chansons) {
	b.module.afficher(vue({ chansonsFavorites: [] }), capture);
	b.connecter('synchronisee');
	const recherche = b.laterale.querySelector('form');
	recherche.querySelector('input').value = 'Titre';
	recherche.dispatchEvent({ type: 'submit' });
	b.appels.at(-1).resolve({ statut: 200, corps: { recherche: 'Titre', chansons } });
	await terminerMicrotaches();
	const declencheur = bouton(b.laterale, chansons.at(-1).titre);
	declencheur.focus();
	declencheur.click();
	b.appels.at(-1).resolve({ statut: 200, corps: { chanson: chansons.at(-1), inscriptionPossible: false, motifIndisponibilite: 'ANIMATEUR' } });
	await terminerMicrotaches();
	return { declencheur, dialogue: b.principale.querySelector('dialog') };
}

test('Console : basculer un favori retrie les résultats affichés sans relancer la recherche ni perdre le focus', async t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	const { declencheur, dialogue } = await ouvrirFicheConsole(b, [chanson, autre]);
	assert.deepEqual(ordreResultats(b.laterale), ['c', 'd']);
	const favori = bouton(dialogue, 'Ajouter aux favoris');
	favori.focus();
	favori.click();
	assert.equal(b.appels.length, 3);
	assert.equal(favori.disabled, false);
	assert.equal(favori.getAttribute('aria-disabled'), 'true');
	assert.equal(b.document.activeElement, favori);
	favori.click();
	assert.equal(b.appels.length, 3);

	b.appels[2].resolve({ statut: 200, corps: {} });
	await terminerMicrotaches();
	b.module.afficher(vue({ chansonsFavorites: ['d'] }), { source: 'evenement', type: 'favoris-chansons-modifies' });
	assert.equal(b.appels.length, 3);
	assert.deepEqual(ordreResultats(b.laterale), ['d', 'c']);
	assert.equal(declencheur.isConnected, true);
	assert.equal(favori.getAttribute('aria-disabled'), null);
	dialogue.close();
	assert.equal(b.document.activeElement, declencheur);

	b.module.afficher(vue({ chansonsFavorites: [] }), { source: 'evenement', type: 'favoris-chansons-modifies' });
	assert.deepEqual(ordreResultats(b.laterale), ['d', 'c']);
	b.module.afficher(vue({ chansonsFavorites: ['c'] }), { source: 'evenement', type: 'favoris-chansons-modifies' });
	assert.deepEqual(ordreResultats(b.laterale), ['c', 'd']);
	assert.equal(b.appels.length, 3);
});

test('Console : à la fermeture de la fiche, le focus retrouve le résultat redessiné par data-chanson-id', async t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	const { declencheur, dialogue } = await ouvrirFicheConsole(b, [chanson, autre]);
	const recherche = b.laterale.querySelector('form');
	recherche.dispatchEvent({ type: 'submit' });
	b.appels.at(-1).resolve({ statut: 200, corps: { recherche: 'Titre', chansons: [autre, chanson] } });
	await terminerMicrotaches();
	assert.equal(declencheur.isConnected, false);
	dialogue.close();
	const actif = b.document.activeElement;
	assert.notEqual(actif, declencheur);
	assert.equal(actif.getAttribute('data-chanson-id'), 'd');
	assert.equal(actif.isConnected, true);
});

test('Manette : l’envoi garde le focus sur un bouton inactif et la composition IME bloque la soumission', async t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.module.afficher(vue(), capture);
	b.connecter('synchronisee');
	bouton(b.principale, 'Suggérer une Chanson').click();
	const formulaire = b.principale.querySelector('form.karaoke__suggestion');
	const [titre, artiste] = formulaire.querySelectorAll('input');
	const envoyer = bouton(formulaire, 'Envoyer la suggestion');
	titre.value = 'Éclat du soir';
	artiste.value = 'Les Accords';

	titre.dispatchEvent({ type: 'compositionstart' });
	formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels.length, 0);
	titre.dispatchEvent({ type: 'compositionend' });

	envoyer.focus();
	formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels.length, 1);
	assert.equal(envoyer.disabled, false);
	assert.equal(envoyer.getAttribute('aria-disabled'), 'true');
	assert.equal(b.document.activeElement, envoyer);
	formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels.length, 1);
	b.appels[0].resolve({ statut: 200, corps: {} });
	await terminerMicrotaches();
	assert.equal(envoyer.getAttribute('aria-disabled'), null);
});

test('Console : le retrait en cours garde le focus et son réessai nomme la suggestion en échec', async t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	const suggestion = { suggestionId: 'a', titre: 'Éclat du soir', artiste: 'Les Accords', nombreJoueurs: 2 };
	b.module.afficher(vue({ suggestions: [suggestion] }), capture);
	b.connecter('synchronisee');
	const section = b.laterale.querySelector('.karaoke__suggestions');
	const retirer = bouton(section, 'Retirer');
	retirer.focus();
	retirer.click();
	assert.equal(b.appels.length, 1);
	assert.equal(retirer.disabled, false);
	assert.equal(retirer.getAttribute('aria-disabled'), 'true');
	assert.equal(b.document.activeElement, retirer);
	retirer.click();
	assert.equal(b.appels.length, 1);

	b.appels[0].resolve({ statut: 503, corps: { code: 'SERVICE_INDISPONIBLE', detail: 'Service indisponible.' } });
	await terminerMicrotaches();
	assert.equal(retirer.disabled, false);
	b.module.afficher(vue({ suggestions: [] }), capture);
	const reessai = bouton(section, 'Réessayer le retrait de la suggestion');
	assert.equal(reessai.hidden, false);
	assert.equal(reessai.getAttribute('aria-label'), 'Réessayer le retrait de la suggestion Éclat du soir · Les Accords');
});
