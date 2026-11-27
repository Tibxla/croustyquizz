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
