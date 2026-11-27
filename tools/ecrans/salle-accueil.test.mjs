// Écran de salle hors Passage (#281) : accueillir les arrivées, annoncer le Buzz en grand, sortir la mise en route du son.
import test from 'node:test';
import assert from 'node:assert/strict';
import { creerAnnonceBuzz, creerArrivees, decrireSon, estAccueil } from '../../src/main/resources/static/js/salle-accueil.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

const capture = { source: 'capture', type: 'capture' };
const evenement = type => ({ source: 'evenement', type });
const joueur = (id, pseudo = `Joueur ${id}`) => ({ id, pseudo, connecte: true });

test('hors Manche, Soirée ouverte ou en cours, sans Palmarès ni classement ni photo : c’est l’accueil', () => {
	const base = { soiree: { etat: 'OUVERTE' }, modeEnCours: null, mancheActive: null, classement: [], joueurs: [] };
	assert.equal(estAccueil(base), true);
	assert.equal(estAccueil({ ...base, soiree: { etat: 'EN_COURS' } }), true, 'entre deux Manches sans point : accueil');
	assert.equal(estAccueil({ ...base, modeEnCours: 'KARAOKE', mancheActive: { ordre: 0 } }), false);
	assert.equal(estAccueil({ ...base, soiree: { etat: 'EN_COURS' }, classement: [{ rang: 1, nom: 'Léa', points: 3 }] }), false,
		'le classement général prend la scène entre deux Manches');
	assert.equal(estAccueil({ ...base, palmaresKaraoke: [] }), false);
	assert.equal(estAccueil({ ...base, soiree: { etat: 'TERMINEE' } }), false);
	assert.equal(estAccueil({ ...base, soiree: { etat: 'PREPARATION' } }), false);
	assert.equal(estAccueil(base, { photo: true }), false);
});

test('les arrivées s’ajoutent une par une ; seul un événement les anime', () => {
	const document = creerDocument();
	const liste = document.createElement('ul');
	const arrivees = creerArrivees(liste);
	arrivees.afficher([joueur('a', 'Léa'), joueur('b', 'Max')], capture);
	assert.deepEqual(liste.children.map(li => li.textContent), ['Léa', 'Max']);
	assert.ok(liste.children.every(li => !li.classList.contains('arrivee--nouvelle')), 'une capture restaure sans animer');
	const lea = liste.children[0];
	arrivees.afficher([joueur('a', 'Léa'), joueur('b', 'Max'), joueur('c', 'Inès')], evenement('joueur-arrive'));
	assert.ok(liste.children[0] === lea, 'les arrivées déjà là ne sont pas recréées');
	assert.equal(liste.children[2].textContent, 'Inès');
	assert.ok(liste.children[2].classList.contains('arrivee--nouvelle'));
	assert.ok(!liste.children[0].classList.contains('arrivee--nouvelle'));
});

test('à soixante Joueurs, la liste garde les derniers arrivés et dit combien d’autres sont là (#281, M9)', () => {
	const document = creerDocument();
	const liste = document.createElement('ul');
	const arrivees = creerArrivees(liste, { maximum: 24 });
	arrivees.afficher(Array.from({ length: 60 }, (_, i) => joueur(`j${i}`, `Joueur ${i}`)), capture);
	assert.equal(liste.children.length, 25);
	assert.equal(liste.children[0].textContent, 'et 36 autres');
	assert.equal(liste.children.at(-1).textContent, 'Joueur 59');
});

test('le Buzz s’annonce en grand sur un événement, puis s’efface ; une capture ne le rejoue pas (#281, M5)', () => {
	const document = creerDocument();
	const element = document.createElement('p');
	const minuteurs = [];
	const annonce = creerAnnonceBuzz(element, { programmer: (f, ms) => { minuteurs.push({ f, ms }); return minuteurs.length; }, annuler() {} });
	const vue = (envoiId, pseudo = 'Léa') => ({ modeEnCours: null, dernierBuzz: envoiId ? { envoiId, pseudo } : null });
	annonce.afficher(vue('b1'), capture);
	assert.equal(element.hidden, true, 'un Buzz déjà là à l’ouverture ne s’annonce pas');
	annonce.afficher(vue('b2', 'Bartholoméo'), evenement('buzz-recu'));
	assert.equal(element.hidden, false);
	assert.equal(element.textContent, 'Bartholoméo a buzzé\u202f!', 'espace insécable avant le point d’exclamation');
	assert.equal(minuteurs.at(-1).ms, 4000);
	minuteurs.at(-1).f();
	assert.equal(element.hidden, true);
	annonce.afficher(vue('b2', 'Bartholoméo'), evenement('autre'));
	assert.equal(element.hidden, true, 'le même Buzz ne se rejoue pas');
	annonce.afficher(vue('b3'), capture);
	assert.equal(element.hidden, true, 'une capture ne joue pas d’animation');
});

test('la mise en route du son est un panneau d’installation, puis une pastille (#281, I8)', () => {
	assert.deepEqual(decrireSon({ fichiers: null, sonDebloque: false }), { etat: 'installation', pastille: 'Son coupé' });
	assert.deepEqual(decrireSon({ fichiers: [{}, {}], sonDebloque: false }), { etat: 'installation', pastille: 'Son coupé' });
	assert.deepEqual(decrireSon({ fichiers: null, sonDebloque: true }), { etat: 'pret', pastille: 'Paroles seules' });
	assert.deepEqual(decrireSon({ fichiers: [], sonDebloque: true }), { etat: 'pret', pastille: 'Paroles seules' });
	assert.deepEqual(decrireSon({ fichiers: [{}], sonDebloque: true }), { etat: 'pret', pastille: 'Son activé · 1 morceau' });
	assert.deepEqual(decrireSon({ fichiers: [{}, {}, {}], sonDebloque: true }), { etat: 'pret', pastille: 'Son activé · 3 morceaux' });
});
