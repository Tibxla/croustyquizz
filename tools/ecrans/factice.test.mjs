import test from 'node:test';
import assert from 'node:assert/strict';

// Adaptateur DOM minimal : le test observe les textes publics, pas le balisage interne.
function zone() {
	return { ownerDocument: { createElement: () => ({}) }, replaceChildren(...enfants) { this.enfants = enfants; } };
}
for (const ecran of ['manette', 'console', 'ecran']) {
	test(`le mode factice affiche le bloc et libère ses zones sur ${ecran}`, async () => {
		const { monter } = await import(`../../src/main/resources/static/js/modes/factice/${ecran}.mjs`);
		const zones = { principale: zone(), ...(ecran === 'manette' ? {} : { laterale: zone() }) };
		const module = monter(zones, {});
		module.afficher({ modeEnCours: 'QUIZ', quiz: { phase: '<script>test</script>' } }, { source: 'capture', type: 'capture' });
		assert.equal(zones.principale.enfants[1].textContent, '{\n  "phase": "<script>test</script>"\n}');
		module.afficher({ modeEnCours: 'QUIZ', quiz: { phase: 'EN_PAUSE' } }, { source: 'evenement', type: 'manche-en-pause' });
		if (zones.laterale) assert.equal(zones.laterale.enfants[0].textContent, 'Événement : manche-en-pause');
		module.demonter();
		assert.deepEqual(zones.principale.enfants, []);
		if (zones.laterale) assert.deepEqual(zones.laterale.enfants, []);
	});
}
