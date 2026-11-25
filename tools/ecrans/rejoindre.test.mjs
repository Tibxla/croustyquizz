import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const page = await readFile(new URL('../../src/main/resources/static/rejoindre.html', import.meta.url), 'utf8');
const balise = (id) => page.match(new RegExp(`<[a-z]+ id="${id}"[^>]*>`))?.[0];

// Tant que le jeton gardé n'est pas vérifié, envoyer un pseudo créerait un second Joueur dans la même Soirée.
test("le formulaire d'arrivée reste masqué tant que l'accès gardé n'est pas vérifié", () => {
	assert.match(balise('arrivee'), /\shidden[\s>]/);
	assert.doesNotMatch(balise('verification'), /\shidden[\s>]/);
	assert.match(balise('verification'), /role="status"/);
});

// DESIGN.md : l'erreur d'un champ s'affiche sous lui, reliée par aria-describedby ; la couleur ne suffit jamais.
test('le code et le pseudo ont chacun leur message d’erreur, relié au champ et masqué au départ', () => {
	for (const [champ, erreur] of [['code', 'code-erreur'], ['pseudo', 'pseudo-erreur']]) {
		assert.match(balise(champ), new RegExp(`aria-describedby="[^"]*\\b${erreur}\\b[^"]*"`), champ);
		assert.match(balise(erreur), /class="champ--erreur"/, erreur);
		assert.match(balise(erreur), /\shidden[\s>]/, erreur);
	}
	assert.match(balise('pseudo'), /aria-describedby="[^"]*\bpseudo-aide\b/);
	assert.match(balise('pseudo-aide'), /class="champ__aide"/);
});
