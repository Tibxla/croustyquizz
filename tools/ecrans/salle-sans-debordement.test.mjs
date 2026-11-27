// Écran de salle sans débordement (#276) : la page ne défile jamais, en 1280 × 720 comme en 1920 × 1080.
// Les règles CSS sont lues comme du texte ; la mise en page réelle est mesurée par la recette navigateur.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fenetreParoles } from '../../src/main/resources/static/js/modes/karaoke/paroles.mjs';

const lireStatique = chemin => readFile(new URL(`../../src/main/resources/static/${chemin}`, import.meta.url), 'utf8');
const echapper = texte => texte.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// Corps de la dernière règle dont le sélecteur est exactement celui-ci (hors @media).
const regle = (css, selecteur) => [...css.matchAll(new RegExp(`(?:^|[}/])\\s*${echapper(selecteur)}\\s*\\{([^}]*)\\}`, 'g'))]
	.map(m => m[1]).join('\n');

test('la scène tient dans la fenêtre : une seule rangée bornée, rien ne l’allonge', async () => {
	const css = await lireStatique('css/ecran-de-salle.css');
	assert.match(regle(css, '.salle__scene'), /grid-template-rows:\s*minmax\(0, 1fr\)/);
	const principal = regle(css, '.salle--coquille .salle__principal');
	assert.match(principal, /overflow:\s*hidden/);
	assert.match(principal, /min-height:\s*0/);
	assert.match(regle(css, '.salle--coquille .salle__principal > *'), /flex:\s*none/);
	// La zone du mode prend la place qui reste entre l'en-tête et le pied, et coupe ce qui dépasse.
	const zone = regle(css, '.salle--coquille .salle__principal > [data-principale]');
	assert.match(zone, /flex:\s*1 1 0/);
	assert.match(zone, /min-height:\s*0/);
	assert.match(zone, /overflow:\s*hidden/);
	assert.match(regle(css, '.salle--coquille .salle__principal > [data-principale]:empty'), /display:\s*none/);
	// La photo affichée entre deux Manches rétrécit avec la place restante au lieu d'être coupée.
	assert.match(regle(css, '.salle--coquille .salle__principal > .photos--affiche'), /flex:\s*1 1 0/);
});

test('le pied suit la zone du mode et garde une place tronquée pour un crédit de Chanson', async () => {
	const css = await lireStatique('css/ecran-de-salle.css');
	const pied = regle(css, '.salle--coquille .salle__principal > .salle__pied');
	assert.match(pied, /position:\s*static/);
	assert.match(pied, /margin-top:\s*auto/);
	assert.match(regle(css, '.salle--coquille .salle__pied'), /grid-template-columns:\s*auto minmax\(0, 1fr\) auto/);
	const credit = regle(css, '.salle--coquille .salle__pied > :not(.marque)');
	for (const propriete of [/min-width:\s*0/, /overflow:\s*hidden/, /text-overflow:\s*ellipsis/, /white-space:\s*nowrap/]) {
		assert.match(credit, propriete);
	}
});

test('la colonne fige le talon en bas et coupe le rouleau et les Joueurs avant lui', async () => {
	const css = await lireStatique('css/ecran-de-salle.css');
	const cote = regle(css, '.salle--coquille .salle__cote');
	assert.match(cote, /display:\s*grid/);
	assert.match(cote, /grid-template-rows:\s*auto minmax\(0, 1fr\) auto/);
	const coupe = regle(css, '.salle--coquille .salle__cote > :not(.talon)');
	assert.match(coupe, /min-height:\s*0/);
	assert.match(coupe, /overflow:\s*clip/);
	assert.match(regle(css, '.salle--coquille .salle__cote > .talon'), /align-self:\s*end/);
	// Hors Manche, la zone du mode vide disparaît : chaque section garde sa rangée, les Joueurs restent visibles.
	assert.match(regle(css, '.salle--coquille .salle__cote > [data-laterale]'), /grid-row:\s*1/);
	assert.match(regle(css, '.salle--coquille .salle__cote > .commun'), /grid-row:\s*2/);
	assert.match(regle(css, '.salle--coquille .salle__cote > .talon'), /grid-row:\s*3/);
});

test('la ligne chantée est bornée par la largeur et la hauteur, ses voisines par deux lignes', async () => {
	const css = await lireStatique('css/ecran-de-salle.css');
	assert.match(regle(css, '.paroles__ligne--en-cours'), /font-size:\s*min\(4\.4vw, 9vh\)/);
	assert.match(regle(css, '.paroles__ligne--longue'), /font-size:\s*min\([\d.]+vw, [\d.]+vh\)/);
	const voisine = regle(css, '.paroles__ligne:not(.paroles__ligne--en-cours)');
	assert.match(voisine, /-webkit-line-clamp:\s*2/);
	assert.match(voisine, /overflow:\s*hidden/);
});

test('une ligne longue laisse moins de voisines, une ligne très longue passe en corps réduit', () => {
	const lignes = ['Un', 'Deux', 'Trois', 'Quatre', 'Cinq', 'Six'].map(texte => ({ texte }));
	assert.deepEqual(fenetreParoles(lignes, 2, false), { debut: 1, fin: 5, longueur: 'courte' });
	assert.deepEqual(fenetreParoles(lignes, 0, false), { debut: 0, fin: 3, longueur: 'courte' });
	assert.deepEqual(fenetreParoles(lignes, -1, true), { debut: 0, fin: 3, longueur: 'courte' });
	const longue = lignes.with(2, { texte: 'Fi your time up, a mi the man a mind up' });
	assert.deepEqual(fenetreParoles(longue, 2, false), { debut: 1, fin: 4, longueur: 'longue' });
	const tresLongue = lignes.with(2, { texte: 'Him whisper inna mi ears a say, a mi one have it, gyal, fi your time up' });
	// Très longue, elle perd aussi la ligne déjà chantée : seule la suivante reste.
	assert.deepEqual(fenetreParoles(tresLongue, 2, false), { debut: 2, fin: 4, longueur: 'tres-longue' });
	// Avant la première ligne, aucune n'est chantée : les trois premières restent des voisines.
	assert.deepEqual(fenetreParoles(tresLongue, -1, true), { debut: 0, fin: 3, longueur: 'courte' });
});

test('le Freestyle se compacte sur la salle : tailles bornées par la hauteur, plus aucun plancher en rem', async () => {
	const css = await lireStatique('css/freestyle.css');
	for (const selecteur of ['.salle .freestyle .freestyle__interprete', '.salle .freestyle .freestyle__grille',
		'.salle .freestyle .freestyle__phase', '.salle .freestyle .freestyle__mesure', '.salle .freestyle .freestyle__suivant']) {
		const corps = regle(css, selecteur);
		assert.match(corps, /font(?:-size)?:[^;]*min\([\d.]+vw, [\d.]+vh\)/, selecteur);
		assert.doesNotMatch(corps, /rem/, selecteur);
	}
	assert.match(regle(css, '.salle .freestyle .freestyle__mot'), /-webkit-line-clamp:\s*2/);
});
