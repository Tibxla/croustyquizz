// Structure et libellés des trois coquilles (Console, Manette, Écran de salle), lus comme du texte.
// La mise en page réelle se vérifie dans le navigateur (docs/ecrans.md, « Vérification visuelle »).
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';

const racine = new URL('../../src/main/resources/static/', import.meta.url);
const lire = chemin => readFile(new URL(chemin, racine), 'utf8');
async function fichiers(dossier) {
	const entrees = await readdir(new URL(dossier, racine), { withFileTypes: true });
	const liste = [];
	for (const entree of entrees) {
		const chemin = `${dossier}${entree.name}`;
		if (entree.isDirectory()) liste.push(...await fichiers(`${chemin}/`));
		else if (/\.(mjs|js|html)$/.test(entree.name)) liste.push(chemin);
	}
	return liste;
}
// Texte affiché : le contenu des balises HTML et les chaînes des modules, sans les commentaires.
const sansCommentaires = source => source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '').replace(/<!--[\s\S]*?-->/g, '');

test('les trois écrans ne parlent plus le jargon du code (#279)', async () => {
	const jargon = [/Vue synchronisée/, /Envoi accepté par le serveur/, /Non classé/, /Écran lecteur/,
		/Dossier d.attente pas encore signalé/, /Ça commence/, /[−+]0,1 s['"`]/, /Fichier local :/];
	const pages = ['console.html', 'manette.html', 'ecran.html'];
	const modules = (await fichiers('js/')).filter(chemin => !chemin.includes('/factice/'));
	for (const chemin of [...pages, ...modules]) {
		const source = sansCommentaires(await lire(chemin));
		for (const motif of jargon) {
			assert.doesNotMatch(source, motif, `${chemin} : ${motif}`);
		}
	}
});

// Corps d'un élément repéré par un attribut, de sa balise ouvrante à la fermante correspondante (sans imbrication du même nom).
function bloc(html, attribut) {
	const echappe = attribut.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
	const debut = html.search(new RegExp(`<(\\w+)[^>]*(?<![\\w-])${echappe}(?![\\w-])[^>]*>`));
	assert.ok(debut >= 0, `${attribut} absent`);
	const balise = html.slice(debut).match(/^<(\w+)/)[1];
	let profondeur = 0;
	for (const m of html.slice(debut).matchAll(new RegExp(`<(/?)${balise}\\b[^>]*>`, 'g'))) {
		profondeur += m[1] ? -1 : 1;
		if (profondeur === 0) return html.slice(debut, debut + m.index + m[0].length);
	}
	return html.slice(debut);
}

test('Console : la barre dit l’étape, le code, les Joueurs et la connexion, et range le reste dans « Plus » (#282)', async () => {
	const html = await lire('console.html');
	const barre = bloc(html, 'class="pilotage"');
	for (const attribut of ['data-titre', 'data-barre-mode', 'data-barre-code', 'data-barre-joueurs', 'data-connexion',
		'data-piloter-soiree="LANCER"', 'data-piloter-soiree="TERMINER"']) {
		assert.match(barre, new RegExp(attribut.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')), attribut);
	}
	const bouton = barre.match(/<button[^>]*data-menu-bouton[^>]*>[\s\S]*?<\/button>/)?.[0];
	assert.ok(bouton, 'bouton du menu « Plus »');
	assert.match(bouton, /Plus/);
	const menu = bloc(barre, 'data-menu');
	assert.match(menu, /id="menu-console"/);
	assert.match(menu, /data-reprise/);
	assert.match(menu, /data-musique-attente/);
	assert.match(menu, /data-resume/);
	// Le Mode de jeu et le titre quittent la colonne : elle commence par la zone du mode.
	const colonne = bloc(html, 'aria-label="Soirée et mode en cours"');
	assert.doesNotMatch(colonne, /data-titre[\s>]|data-manche/);
	// La raison d'un lancement impossible ouvre la colonne, au-dessus de la zone du mode.
	assert.ok(colonne.indexOf('data-raison-lancer') < colonne.indexOf('data-principale'));
});

test('Console : les Joueurs sont dépliés, et la zone du mode passe après le programme et les Joueurs (#282)', async () => {
	const html = await lire('console.html');
	const cote = bloc(html, 'aria-label="Programme et Joueurs"');
	assert.match(cote, /<details[^>]*open[^>]*>\s*<summary[^>]*data-nombre-joueurs/);
	assert.ok(cote.indexOf('data-laterale') > cote.indexOf('data-joueurs'), 'le Catalogue du mode vient après les Joueurs');
	assert.ok(cote.indexOf('data-edition-programme') < cote.indexOf('data-joueurs'));
});

test('Console : la barre de pilotage tient sur une ligne au-dessus de 860 px (#282)', async () => {
	const css = await lire('css/console.css');
	const pilotage = css.match(/^\.pilotage \{([^}]*)\}/m)?.[1] ?? '';
	assert.match(pilotage, /flex-wrap:\s*nowrap/);
	assert.match(css, /@media \(max-width: 860px\)\s*\{[^@]*\.pilotage \{[^}]*flex-wrap:\s*wrap/);
});
