import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { creerLignePalmaresSalle } from '../../src/main/resources/static/js/palmares.mjs';
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

test('le Palmarès du Freestyle dit les victoires des Interprètes avant les Passages notés (#74)', () => {
	const { palmares } = decrireVue({ soiree: {}, joueurs: [], classement: [], palmaresFreestyle: [
		{ rang: 1, interprete: { joueurId: 'z', pseudo: 'Zoé' }, nombreVictoires: 2, nombrePassages: 2 },
		{ rang: null, interprete: { joueurId: 'i', pseudo: 'Inès' }, nombreVictoires: 0, nombrePassages: 1 },
		{ passageId: 'p', numero: 3, rang: 1, instrumentale: { titre: 'Pavés mouillés' }, interprete: { pseudo: 'Lou' },
			nombreVotes: 2, moyenneDixiemes: 45 },
	] });
	assert.deepEqual(palmares.map(ligne => ligne.texte), [
		'1. Zoé · 2 victoires · 2 Passages à plusieurs',
		'Inès · Aucune victoire · 1 Passage à plusieurs',
		'1. n° 3 · Pavés mouillés · Lou · 4,5 sur 5 (2 votes)',
	]);
	const ligne = creerLignePalmaresSalle(creerDocument(), palmares[0]);
	assert.equal(Boolean(ligne.querySelector('.palmares__numero')), false);
	assert.equal(ligne.querySelector('.palmares__chanson')?.textContent, '2 victoires');
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

test('la liste du Palmarès a une hauteur bornée, pour que le talon d’accès reste à l’écran', async () => {
	const css = await cssSalle();
	const corps = css.match(/(?:^|[}/])\s*\.salle--coquille \.palmares ul\s*\{([^}]*)\}/)?.[1] ?? '';
	assert.match(corps, /max-block-size:\s*[\d.]+vh;/);
	assert.match(corps, /overflow:\s*auto;/);
});
