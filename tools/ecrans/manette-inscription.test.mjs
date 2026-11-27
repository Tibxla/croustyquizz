// Inscription au Karaoké depuis la Manette (#280) : des résultats en cartes, une fiche qui se ferme au succès
// et un message vrai qui dit la position, une limite expliquée avant l'envoi.
import test from 'node:test';
import assert from 'node:assert/strict';
import { creerBanc, chanson, passage, terminerMicrotaches, vue } from './karaoke-dom-factice.mjs';
import { monter as manette } from '../../src/main/resources/static/js/modes/karaoke/manette.mjs';

const capture = { source: 'capture', type: 'capture' }, evenement = { source: 'evenement', type: 'karaoke-file-modifiee' };
const bouton = (zone, texte) => zone.querySelectorAll('button').find(b => b.textContent === texte);
const chansons = Array.from({ length: 12 }, (_, i) => ({ ...chanson, chansonId: `c${i}`, titre: `Titre ${i}`,
	etatParoles: i === 1 ? 'NON_SYNCHRONISEES' : 'SYNCHRONISEES' }));

async function chercher(b, resultats = chansons) {
	const champ = b.principale.querySelector('input');
	champ.value = 'titre';
	b.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	b.appels.at(-1).resolve({ statut: 200, corps: { chansons: resultats } });
	await terminerMicrotaches();
}

test('les résultats sont des cartes entières : titre en gras, artiste, durée ; dix d’abord, puis « Voir plus »', async t => {
	const b = creerBanc(manette); t.after(() => b.module.demonter());
	b.module.afficher(vue(), capture); b.connecter('synchronisee');
	await chercher(b);
	const liste = b.principale.querySelector('ul.karaoke__resultats');
	assert.equal(liste.children.length, 10);
	const [premiere] = liste.children;
	const cartes = premiere.querySelectorAll('button');
	assert.equal(cartes.length, 1, 'une seule cible par résultat');
	assert.match(cartes[0].className, /karaoke__carte/);
	assert.equal(cartes[0].querySelector('strong').textContent, 'Titre 0');
	assert.match(cartes[0].textContent, /Artiste fictif/);
	assert.match(cartes[0].textContent, /2:03/);
	bouton(b.principale, 'Voir plus').click();
	assert.equal(liste.children.length, 12);
	assert.equal(bouton(b.principale, 'Voir plus').hidden, true);
});

test('une Chanson aux paroles non synchronisées est grisée avec la raison, et ne s’ouvre pas', async t => {
	const b = creerBanc(manette); t.after(() => b.module.demonter());
	b.module.afficher(vue(), capture); b.connecter('synchronisee');
	await chercher(b);
	const carte = b.principale.querySelector('ul.karaoke__resultats').children[1].querySelector('button');
	assert.equal(carte.disabled, true);
	assert.match(carte.textContent, /Paroles non synchronisées : impossible de la chanter ici\./);
	const appels = b.appels.length;
	carte.click();
	assert.equal(b.appels.length, appels);
});

test('après l’inscription, la fiche se ferme et un message vrai dit la position, au-dessus de « Tes Passages »', async t => {
	const b = creerBanc(manette); t.after(() => b.module.demonter());
	b.module.afficher(vue({ limitePassagesEnAttente: 1 }), capture); b.connecter('synchronisee');
	await chercher(b);
	b.principale.querySelector('ul.karaoke__resultats').children[0].querySelector('button').click();
	b.appels.at(-1).resolve({ statut: 200, corps: { chanson: chansons[0], inscriptionPossible: true, motifIndisponibilite: null } });
	await terminerMicrotaches();
	bouton(b.principale, "M'inscrire").click();
	b.appels.at(-1).resolve({ statut: 201, corps: {} });
	await terminerMicrotaches();
	const inscrit = { ...passage('p', 7), position: 2, chanson: chansons[0] };
	b.module.afficher(vue({ limitePassagesEnAttente: 1, mesPassagesEnAttente: [inscrit], motifInscriptionRefusee: 'LIMITE_ATTEINTE' }), evenement);
	assert.equal(b.principale.querySelector('dialog'), null, 'la fiche est fermée');
	const annonce = b.principale.querySelector('.karaoke__annonce-inscription');
	assert.equal(annonce.textContent, 'C’est noté : tu passes en position 2.');
	assert.equal(annonce.getAttribute('role'), 'status');
	const titre = b.principale.querySelectorAll('h2').find(h => h.textContent === 'Tes Passages');
	const enfants = b.principale.children[0].children;
	assert.ok(enfants.indexOf(annonce) < enfants.indexOf(titre));
	assert.ok(b.document.defilements.at(-1).element === annonce);
	assert.ok(b.document.activeElement === titre);
	assert.doesNotMatch(b.principale.textContent, /limite/i);
});

test('la limite se dit avant l’envoi, avec sa raison, et les invitations se désactivent avec le bouton', async t => {
	const b = creerBanc(manette); t.after(() => b.module.demonter());
	const autres = [{ id: 'j', pseudo: 'Jo' }, { id: 'k', pseudo: 'Kim' }];
	const v = { ...vue({ limitePassagesEnAttente: 1, motifInscriptionRefusee: 'LIMITE_ATTEINTE', peutInscrire: false }), joueurs: autres };
	b.module.afficher(v, capture); b.connecter('synchronisee');
	await chercher(b, [chansons[0]]);
	b.principale.querySelector('ul.karaoke__resultats').children[0].querySelector('button').click();
	b.appels.at(-1).resolve({ statut: 200, corps: { chanson: chansons[0], inscriptionPossible: true, motifIndisponibilite: null } });
	await terminerMicrotaches();
	const fiche = b.principale.querySelector('dialog');
	assert.match(fiche.textContent, /Tu chantes déjà dans un Passage en attente : attends ton tour pour t’inscrire à nouveau\./);
	assert.equal(bouton(fiche, "M'inscrire").disabled, true);
	assert.equal(fiche.querySelector('fieldset').disabled, true);
});
