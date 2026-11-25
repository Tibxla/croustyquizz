import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { afficherPalmaresDeSalle, creerLignePalmaresSalle, resumerPalmaresDeSalle } from '../../src/main/resources/static/js/palmares.mjs';
import { decrireVue } from '../../src/main/resources/static/js/vue-commune.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

function rendreLigne(passage) {
	const { palmares } = decrireVue({ soiree: {}, joueurs: [], classement: [], palmaresKaraoke: [passage] });
	return creerLignePalmaresSalle(creerDocument(), palmares[0]);
}

test('la salle affiche les pseudos complets et fournit une seule phrase aux lecteurs d’écran', () => {
	const titre = 'La Chanson au titre très long que les Interprètes ont choisie pour cette Soirée de démonstration';
	const pseudo = 'Joueuse de démonstration Alizé';
	const ligne = rendreLigne({ numero: 10, rang: 1, chanson: { titre }, interpretes: [{ pseudo }, { pseudo: 'Basile' }],
		nombreVotes: 7, moyenneDixiemes: 43 });

	assert.equal(ligne.tagName, 'LI');
	assert.equal(ligne.getAttribute('data-teinte'), '2');
	const presentation = ligne.querySelector('[aria-hidden="true"]');
	assert.equal(presentation?.querySelector('.palmares__rang')?.textContent, '1.');
	assert.equal(presentation?.querySelector('.palmares__numero')?.textContent, 'n° 10');
	assert.equal(presentation?.querySelector('.palmares__interpretes')?.textContent, `${pseudo} et Basile`);
	assert.equal(presentation?.querySelector('.palmares__chanson')?.textContent, titre);
	const note = presentation?.querySelector('.palmares__note');
	assert.deepEqual(note?.children.map(enfant => [enfant.className, enfant.textContent]),
		[['palmares__moyenne', '4,3'], ['palmares__sur', 'sur 5']]);
	assert.equal(presentation?.querySelector('.palmares__votes')?.textContent, '7 votes');
	assert.deepEqual(ligne.children.filter(enfant => enfant.getAttribute('aria-hidden') !== 'true')
		.map(enfant => [enfant.className, enfant.textContent]), [
			['visuellement-cache', `1. n° 10 · ${titre} · ${pseudo} et Basile · 4,3 sur 5 (7 votes)`],
		]);
});

test('un Passage sans vote conserve sa souche et affiche « Aucun vote » sans rang ni note', () => {
	const ligne = rendreLigne({ numero: 9, rang: null, chanson: { titre: 'Le phare' }, interpretes: [{ pseudo: 'Alizé' }],
		nombreVotes: 0, moyenneDixiemes: null });

	assert.equal(ligne.getAttribute('data-teinte'), '1');
	assert.equal(Boolean(ligne.querySelector('.palmares__rang')), false);
	assert.equal(Boolean(ligne.querySelector('.palmares__note')), false);
	assert.equal(ligne.querySelector('.palmares__votes')?.textContent, 'Aucun vote');
	assert.equal(ligne.querySelector('.visuellement-cache')?.textContent, 'n° 9 · Le phare · Alizé · Aucun vote');
});

const cssSalle = () => readFile(new URL('../../src/main/resources/static/css/ecran-de-salle.css', import.meta.url), 'utf8');
const reglesPalmares = css => [...css.matchAll(/([^{}]*\.palmares__[^{}]*)\{([^}]*)\}/g)]
	.map(([, selecteurs, corps]) => ({ selecteurs: selecteurs.trim().split(/\s*,\s*/), corps }));

test('les règles des lignes du Palmarès sont limitées à la coquille de salle', async () => {
	const regles = reglesPalmares(await cssSalle());
	assert.ok(regles.length > 0);
	for (const selecteur of regles.flatMap(regle => regle.selecteurs)) {
		assert.match(selecteur, /^\.salle--coquille \.palmares \.palmares__/, selecteur);
	}
});

test('aucune colonne fixe ne coupe le rang, le numéro ou la note, et les colonnes restent alignées d’une ligne à l’autre', async () => {
	const css = await cssSalle();
	const corpsDe = selecteur => css.match(new RegExp(`(?:^|[}/])\\s*${selecteur.replace(/[.*]/g, '\\$&')}\\s*\\{([^}]*)\\}`))?.[1] ?? '';
	assert.match(corpsDe('.salle--coquille .palmares ul'), /grid-template-columns:\s*max-content minmax\(0, 1fr\) max-content;/);
	for (const selecteur of ['.salle--coquille .palmares li', '.salle--coquille .palmares .palmares__ligne']) {
		assert.match(corpsDe(selecteur), /grid-template-columns:\s*subgrid;/, selecteur);
	}
	assert.doesNotMatch(css, /grid-template-columns:\s*[\d.]+vw minmax\(0, 1fr\) [\d.]+vw/);
	const tronques = reglesPalmares(css).filter(regle => /text-overflow:\s*ellipsis/.test(regle.corps)).flatMap(regle => regle.selecteurs);
	assert.deepEqual(tronques, ['.salle--coquille .palmares .palmares__chanson']);
});

test('la liste du Palmarès s’affiche en entier, sans hauteur bornée ni défilement', async () => {
	const css = await cssSalle();
	const corps = css.match(/(?:^|[}/])\s*\.salle--coquille \.palmares ul\s*\{([^}]*)\}/)?.[1] ?? '';
	assert.ok(corps.length > 0);
	assert.doesNotMatch(corps, /max-(?:block-size|height):/);
	assert.doesNotMatch(corps, /overflow/);
	// Sous le Palmarès, la note d'attente vide ne prend pas d'écart : environ 23 px gagnés en 1920 × 1080, 15 px en 1280 × 720.
	assert.match(css, /\.salle--coquille \.palmares:not\(\[hidden\]\) ~ \.coquille__note:empty\s*\{\s*display:\s*none;\s*\}/);
});

const lignesPalmares = (classes, sansVote = 0) => decrireVue({ soiree: {}, joueurs: [], classement: [], palmaresKaraoke: [
	...Array.from({ length: classes }, (_, i) => ({ numero: i + 1, rang: i + 1, chanson: { titre: `Chanson ${i + 1}` },
		interpretes: [{ pseudo: `Joueur ${i + 1}` }], nombreVotes: 2, moyenneDixiemes: 50 - i })),
	...Array.from({ length: sansVote }, (_, i) => ({ numero: classes + i + 1, rang: null, chanson: { titre: 'Sans vote' },
		interpretes: [{ pseudo: 'Alizé' }], nombreVotes: 0, moyenneDixiemes: null })),
] }).palmares;
const resumer = (classes, sansVote) => {
	const { lignes, texteAutres } = resumerPalmaresDeSalle(lignesPalmares(classes, sansVote));
	return { numeros: lignes.map(ligne => ligne.numero), suite: texteAutres };
};

test('la salle montre les trois premiers Passages classés, puis le nombre des autres Passages', () => {
	assert.deepEqual(resumer(3), { numeros: [1, 2, 3], suite: '' });
	assert.deepEqual(resumer(4), { numeros: [1, 2, 3], suite: 'et 1 autre Passage' });
	assert.deepEqual(resumer(5), { numeros: [1, 2, 3], suite: 'et 2 autres Passages' });
	assert.deepEqual(resumer(10), { numeros: [1, 2, 3], suite: 'et 7 autres Passages' });
	assert.deepEqual(resumer(2), { numeros: [1, 2], suite: '' });
});

test('les Passages sans vote restent hors de la salle mais comptent parmi les autres Passages', () => {
	assert.deepEqual(resumer(2, 2), { numeros: [1, 2], suite: 'et 2 autres Passages' });
	assert.deepEqual(resumer(3, 1), { numeros: [1, 2, 3], suite: 'et 1 autre Passage' });
	// Sans Passage classé, « Aucun Passage noté. » suffit : pas de ligne « et N autres ».
	assert.deepEqual(resumer(0, 3), { numeros: [], suite: '' });
	assert.deepEqual(resumer(0), { numeros: [], suite: '' });
});

function zonesPalmares() {
	const document = creerDocument();
	const zones = { liste: document.createElement('ul'), suite: document.createElement('p'), vide: document.createElement('p') };
	return { document, zones };
}

test('l’Écran de salle remplit la liste, la ligne « et N autres Passages » et le texte du Palmarès vide', () => {
	const { document, zones } = zonesPalmares();
	afficherPalmaresDeSalle(document, zones, lignesPalmares(10, 2));
	assert.deepEqual(zones.liste.children.map(ligne => ligne.querySelector('.palmares__numero')?.textContent), ['n° 1', 'n° 2', 'n° 3']);
	assert.equal(zones.suite.hidden, false);
	assert.equal(zones.suite.textContent, 'et 9 autres Passages');
	assert.equal(zones.vide.hidden, true);

	afficherPalmaresDeSalle(document, zones, lignesPalmares(3));
	assert.equal(zones.liste.children.length, 3);
	assert.equal(zones.suite.hidden, true);
	assert.equal(zones.suite.textContent, '');

	afficherPalmaresDeSalle(document, zones, lignesPalmares(0, 2));
	assert.equal(zones.liste.children.length, 0);
	assert.equal(zones.suite.hidden, true);
	assert.equal(zones.vide.hidden, false);

	afficherPalmaresDeSalle(document, zones, null);
	assert.equal(zones.liste.children.length, 0);
	assert.equal(zones.suite.hidden, true);
	assert.equal(zones.vide.hidden, true);
});
