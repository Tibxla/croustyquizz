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

test('Manette : l’en-tête tient sur une ligne et le reste se range dans un menu (#280)', async () => {
	const html = await lire('manette.html');
	const entete = bloc(html, 'class="manette__entete"');
	for (const attribut of ['class="marque"', 'data-pseudo', 'data-connexion', 'data-menu-bouton']) assert.match(entete, new RegExp(attribut), attribut);
	assert.doesNotMatch(entete, /data-titre|data-resume|data-manche/);
	const menu = bloc(html, 'data-menu');
	assert.match(menu, /id="menu-manette"/);
	for (const attribut of ['data-titre', 'data-joueurs', 'data-classement', 'data-photos', 'data-reprise']) assert.match(menu, new RegExp(attribut), attribut);
	const actions = bloc(html, 'class="manette__actions"');
	assert.match(actions, /data-buzzer/);
	assert.doesNotMatch(actions, /data-reprise/);
	assert.ok(html.indexOf('data-principale') < html.indexOf('class="manette__actions"'));
});

test('Manette : l’action du moment reste collée en bas, zone de sécurité comprise (#280)', async () => {
	const css = await lire('css/manette.css');
	const actions = css.match(/^\.manette__actions \{([^}]*)\}/m)?.[1] ?? '';
	assert.match(actions, /position:\s*sticky/);
	assert.match(actions, /bottom:\s*0/);
	assert.match(actions, /safe-area-inset-bottom/);
});

test('Manette : aucun texte sous 16 px (#280, M1)', async () => {
	const css = await lire('css/manette.css');
	const regle = css.match(/\/\* Aucun texte sous 16 px[^*]*\*\/\s*([^{]+)\{([^}]*)\}/);
	assert.ok(regle, 'règle de plancher typographique');
	for (const selecteur of ['.champ__aide', '.ticket__quoi', '.karaoke__position', '.karaoke__attente', '.mon-ticket__etiquette--texte', '.karaoke__etat-invitations']) {
		assert.ok(regle[1].includes(selecteur), selecteur);
	}
	assert.match(regle[2], /font-size:\s*1rem/);
});

test('Écran de salle : l’accueil invite à jouer au centre, le pied porte le titre, le son et la connexion (#281)', async () => {
	const html = await lire('ecran.html');
	const principal = bloc(html, 'class="salle__principal"');
	const accueil = bloc(principal, 'data-accueil');
	assert.match(accueil, /data-accueil-talon/);
	assert.match(accueil, /data-arrivees/);
	assert.match(principal, /data-annonce-buzz/);
	const talon = bloc(html, 'data-talon');
	assert.match(talon, /Scanne pour jouer/);
	assert.doesNotMatch(talon, /Code d’accès de la Soirée/);
	const pied = bloc(html, 'class="salle__pied"');
	for (const attribut of ['data-titre-pied', 'data-connexion', 'data-audio-salle', 'data-son-etat']) assert.match(pied, new RegExp(attribut), attribut);
	// Les commandes de mise en route quittent la scène pour le pied.
	assert.doesNotMatch(principal.replace(pied, ''), /data-choisir-dossier|data-activer-son/);
});

test('Écran de salle : en Manche, la scène commence par le mode ; à l’accueil, la colonne s’efface (#281, #283)', async () => {
	const css = await lire('css/ecran-de-salle.css');
	assert.match(css, /\.salle--manche \.salle__principal > \.coquille__commun \{[^}]*display:\s*none/);
	assert.match(css, /\.salle--accueil \.salle__scene \{[^}]*grid-template-columns:\s*minmax\(0, 1fr\)/);
	assert.match(css, /\.salle--accueil \.salle__cote \{[^}]*display:\s*none/);
});
