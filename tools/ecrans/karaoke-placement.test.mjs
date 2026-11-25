import test from 'node:test';
import assert from 'node:assert/strict';
import { preparerPlacement } from '../../src/main/resources/static/js/modes/karaoke/placement.mjs';

test('les gestes restent dans le Tour trié, excluent la priorité et figent les compteurs', () => {
	const passages = [
		{ passageId: 'c', position: 5, tour: 1, numeroModification: 7 },
		{ passageId: 'x', position: 6, tour: 2, numeroModification: 0 },
		{ passageId: 'a', position: 2, tour: 1, numeroModification: 3 },
		{ passageId: 'p', position: 1, tour: 1, prioritaire: true, numeroModification: 9 },
		{ passageId: 'b', position: 4, tour: 1, numeroModification: 5 },
	];
	const avant = structuredClone(passages);
	assert.deepEqual(preparerPlacement(passages, 'b', 'monter'), { numeroModification: 5, tour: 1, avant: { passageId: 'a', numeroModification: 3 } });
	assert.deepEqual(preparerPlacement(passages, 'a', 'descendre'), { numeroModification: 3, tour: 1, avant: { passageId: 'c', numeroModification: 7 } });
	assert.deepEqual(preparerPlacement(passages, 'b', 'descendre'), { numeroModification: 5, tour: 1, avant: null });
	assert.deepEqual(preparerPlacement(passages, 'a', 'fin-du-tour'), { numeroModification: 3, tour: 1, avant: null });
	for (const [id, geste] of [['a', 'monter'], ['c', 'descendre'], ['c', 'fin-du-tour'], ['x', 'monter'], ['p', 'descendre'], ['absent', 'monter']]) assert.equal(preparerPlacement(passages, id, geste), null);
	assert.deepEqual(passages, avant);
});
