import test from 'node:test';
import assert from 'node:assert/strict';
import { creerGestionnaireDeMode, cheminDuModule } from '../../src/main/resources/static/js/modules-de-mode.mjs';

test('le même mode reçoit toutes les vues, puis est démonté avant son remplaçant et hors Manche', async () => {
	const appels = [];
	const zones = { principale: { replaceChildren() { appels.push('vider'); } } };
	const contexte = { soireeId: 'soiree' };
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'manette', zones, contexte,
		charger: async chemin => ({ monter(z, c) {
			assert.equal(z, zones); assert.equal(c, contexte);
			appels.push(chemin);
			return { afficher: (vue, origine) => appels.push([vue.modeEnCours, origine.source]),
				demonter: () => appels.push('demonter') };
		} }),
	});
	await gestionnaire.afficher({ modeEnCours: 'QUIZ' }, { source: 'capture' });
	await gestionnaire.afficher({ modeEnCours: 'QUIZ' }, { source: 'evenement' });
	await gestionnaire.afficher({ modeEnCours: 'BLIND_TEST' }, { source: 'evenement' });
	await gestionnaire.afficher({ modeEnCours: null }, { source: 'capture' });
	assert.deepEqual(appels, ['vider', './modes/quiz/manette.mjs', ['QUIZ', 'capture'], ['QUIZ', 'evenement'],
		'demonter', 'vider', './modes/blindtest/manette.mjs', ['BLIND_TEST', 'evenement'], 'demonter', 'vider']);
	assert.equal(cheminDuModule('KARAOKE', 'ecran'), './modes/karaoke/ecran.mjs');
	assert.equal(cheminDuModule('FREESTYLE', 'console'), './modes/freestyle/console.mjs');
});

test('un import tardif ou un écran fermé ne monte jamais un module périmé', async () => {
	const imports = [];
	const montes = [];
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'console', zones: {}, contexte: {},
		charger: chemin => new Promise(resoudre => imports.push(() => resoudre({ monter() {
			montes.push(chemin); return { afficher() {}, demonter() {} };
		} }))),
	});
	const ancien = gestionnaire.afficher({ modeEnCours: 'QUIZ' }, {});
	const nouveau = gestionnaire.afficher({ modeEnCours: 'KARAOKE' }, {});
	imports[0](); await ancien;
	assert.deepEqual(montes, []);
	imports[1](); await nouveau;
	assert.deepEqual(montes, ['./modes/karaoke/console.mjs']);
	const ferme = gestionnaire.afficher({ modeEnCours: 'FREESTYLE' }, {});
	gestionnaire.demonter(); imports[2](); await ferme;
	assert.equal(montes.length, 1);
});

test('un import en cours affiche la dernière vue et un échec peut être réessayé sans perdre la partie commune', async () => {
	let resoudre;
	const affichages = [];
	const erreurs = [];
	let tentative = 0;
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'ecran', zones: {}, contexte: {},
		surErreur: erreur => erreurs.push(erreur.message),
		charger: () => ++tentative === 1 ? Promise.reject(new Error('Module indisponible')) :
			new Promise(r => { resoudre = r; }),
	});
	await gestionnaire.afficher({ modeEnCours: 'QUIZ' }, {});
	assert.deepEqual(erreurs, ['Module indisponible']);
	const attente = gestionnaire.afficher({ modeEnCours: 'QUIZ', numero: 1 }, { source: 'capture' });
	gestionnaire.afficher({ modeEnCours: 'QUIZ', numero: 2 }, { source: 'evenement' });
	resoudre({ monter: () => ({ afficher: vue => affichages.push(vue.numero), demonter() {} }) });
	await attente;
	assert.deepEqual(affichages, [2]);
});

test('un bloc nul en fin de Manche démonte le module, et la Manche suivante du même mode en remonte un neuf', async () => {
	const appels = [];
	const zones = { principale: { replaceChildren() {} } };
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'ecran', zones, contexte: {},
		charger: async () => ({ monter() {
			const numero = appels.filter(a => a === 'monter').length + 1;
			appels.push('monter');
			return { afficher: vue => appels.push(`afficher ${numero} ${vue.karaoke?.mancheId ?? 'nul'}`),
				demonter: () => appels.push(`demonter ${numero}`) };
		} }),
	});
	await gestionnaire.afficher({ modeEnCours: 'KARAOKE', karaoke: { mancheId: 'm1' } }, { source: 'capture' });
	await gestionnaire.afficher({ modeEnCours: 'KARAOKE', karaoke: null }, { source: 'evenement' });
	await gestionnaire.afficher({ modeEnCours: 'KARAOKE', karaoke: null }, { source: 'capture' });
	await gestionnaire.afficher({ modeEnCours: 'KARAOKE', karaoke: { mancheId: 'm2' } }, { source: 'evenement' });
	assert.deepEqual(appels, ['monter', 'afficher 1 m1', 'demonter 1', 'monter', 'afficher 2 m2']);
});
