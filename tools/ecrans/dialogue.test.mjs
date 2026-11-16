import test from 'node:test';
import assert from 'node:assert/strict';
import { ouvrirDialogue } from '../../src/main/resources/static/js/dialogue.mjs';

test('ouvrir le dialogue donne le focus au contrôle puis le rend au déclencheur à la fermeture', () => {
	const focus = []; let fermer;
	const dialogue = { ownerDocument: { activeElement: { isConnected: true, focus: () => focus.push('déclencheur') } },
		addEventListener: (nom, rappel) => { assert.equal(nom, 'close'); fermer = rappel; },
		showModal() { this.open = true; },
		querySelector: () => ({ focus: () => focus.push('contrôle') }),
	};
	ouvrirDialogue(dialogue); ouvrirDialogue(dialogue);
	assert.deepEqual(focus, ['contrôle']); fermer();
	assert.deepEqual(focus, ['contrôle', 'déclencheur']);
});
