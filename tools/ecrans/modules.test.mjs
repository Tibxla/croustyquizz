import test from 'node:test';
import assert from 'node:assert/strict';
import { creerGestionnaireDeMode, cheminDuModule } from '../../src/main/resources/static/js/modules-de-mode.mjs';

const capture = { source: 'capture' };
const evenement = { source: 'evenement' };
const mancheQuizId = '00000000-0000-4000-8000-000000000002';
const premiereMancheKaraokeId = '00000000-0000-4000-8000-000000000003';
const secondeMancheKaraokeId = '00000000-0000-4000-8000-000000000004';
const vueSalle = {
	soiree: { id: '00000000-0000-4000-8000-000000000001', titre: 'Soirée fictive', format: 'SOLO',
		pilotage: 'MANUEL', etat: 'EN_COURS', codeAcces: 'DEMO01' },
	mancheActive: null, modeEnCours: null, joueurs: [], classement: [], dernierBuzz: null,
};
const vueQuiz = { ...vueSalle, modeEnCours: 'QUIZ', quiz: { phase: 'ATTENTE' },
	mancheActive: { id: mancheQuizId, ordre: 0, mode: 'QUIZ', etat: 'EN_COURS',
		reglages: { nombreEtapes: 3, tempsParEtapeSecondes: 20 } } };
function vueKaraoke(mancheId = premiereMancheKaraokeId) {
	return {
		...vueSalle, modeEnCours: 'KARAOKE',
		mancheActive: { id: mancheId, ordre: mancheId === premiereMancheKaraokeId ? 1 : 2, mode: 'KARAOKE', etat: 'EN_COURS',
			reglages: { limitePassagesEnAttente: 2 } },
		karaoke: { mancheId, etat: 'EN_COURS', limitePassagesEnAttente: 2, nombreEnAttente: 0, tourOuvert: 1,
			passagePrioritaireId: null, courant: null, prochainsPassages: [], nombreAutresPassages: 0,
			configurationInterface: { longueurRechercheMax: 120, delaiConsultationNavigateurMs: 8000,
				delaiConfirmationNavigateurMs: 10000, delaiAppelMs: 30000 },
			lecteurDesigneId: null, lecture: null, paroles: null },
	};
}

test('le même mode reçoit toutes les vues, puis est démonté avant son remplaçant et hors Manche', async () => {
	const appels = [];
	const zones = { principale: { replaceChildren() { appels.push('vider'); } } };
	const contexte = { soireeId: 'soiree' };
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'ecran', zones, contexte,
		charger: async chemin => ({ monter(z, c) {
			assert.equal(z, zones); assert.equal(c, contexte);
			appels.push(chemin);
			return { afficher: (vue, origine) => appels.push([vue.modeEnCours, origine.source]),
				demonter: () => appels.push('demonter') };
		} }),
	});
	await gestionnaire.afficher(vueQuiz, capture);
	await gestionnaire.afficher(vueQuiz, evenement);
	await gestionnaire.afficher(vueKaraoke(), evenement);
	await gestionnaire.afficher(vueSalle, capture);
	assert.deepEqual(appels, ['vider', './modes/quiz/ecran.mjs', ['QUIZ', 'capture'], ['QUIZ', 'evenement'],
		'demonter', 'vider', './modes/karaoke/ecran.mjs', ['KARAOKE', 'evenement'], 'demonter', 'vider']);
	assert.equal(cheminDuModule('QUIZ', 'manette'), './modes/quiz/manette.mjs');
	assert.equal(cheminDuModule('BLIND_TEST', 'manette'), './modes/blindtest/manette.mjs');
	assert.equal(cheminDuModule('KARAOKE', 'ecran'), './modes/karaoke/ecran.mjs');
	assert.equal(cheminDuModule('FREESTYLE', 'console'), './modes/freestyle/console.mjs');
});

test('un import tardif ou un écran fermé ne monte jamais un module périmé', async () => {
	const imports = [];
	const montes = [];
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'ecran', zones: {}, contexte: {},
		charger: chemin => new Promise(resoudre => imports.push(() => resoudre({ monter() {
			montes.push(chemin); return { afficher() {}, demonter() {} };
		} }))),
	});
	const ancien = gestionnaire.afficher(vueQuiz, capture);
	const nouveau = gestionnaire.afficher(vueKaraoke(), evenement);
	imports[0](); await ancien;
	assert.deepEqual(montes, []);
	imports[1](); await nouveau;
	assert.deepEqual(montes, ['./modes/karaoke/ecran.mjs']);
	const ferme = gestionnaire.afficher(vueQuiz, evenement);
	gestionnaire.demonter(); imports[2](); await ferme;
	assert.equal(montes.length, 1);
});

test('les blocs réservés du Blind test et du Freestyle ne déclenchent ni import ni erreur', async () => {
	const appels = [];
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'ecran', zones: {}, contexte: {},
		surErreur: erreur => appels.push(erreur.message),
		charger: async chemin => {
			appels.push(chemin);
			return { monter: () => ({ afficher() {}, demonter: () => appels.push('demonter') }) };
		},
	});
	await gestionnaire.afficher(vueQuiz, capture);
	// Le contrat admet ces Modes de jeu dans la vue, avec un bloc nul et sans objet Manche dédié.
	await gestionnaire.afficher({ ...vueSalle, modeEnCours: 'BLIND_TEST', blindtest: null }, evenement);
	await gestionnaire.afficher({ ...vueSalle, modeEnCours: 'FREESTYLE', freestyle: null }, evenement);
	assert.deepEqual(appels, ['./modes/quiz/ecran.mjs', 'demonter']);
});

test('un bloc devenu nul pendant le chargement empêche le montage', async () => {
	let resoudre;
	const appels = [];
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'ecran', zones: {}, contexte: {},
		charger: () => new Promise(r => { resoudre = r; }),
		surErreur: erreur => appels.push(erreur.message),
	});
	const attente = gestionnaire.afficher(vueKaraoke(), capture);
	await gestionnaire.afficher({ ...vueSalle, modeEnCours: 'KARAOKE', karaoke: null }, evenement);
	resoudre({ monter: () => { appels.push('monter'); return { afficher() {}, demonter() {} }; } });
	await attente;
	assert.deepEqual(appels, []);
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
	await gestionnaire.afficher(vueQuiz, capture);
	assert.deepEqual(erreurs, ['Module indisponible']);
	const attente = gestionnaire.afficher(vueQuiz, capture);
	const derniereVue = { ...vueQuiz, soiree: { ...vueQuiz.soiree, titre: 'Soirée renommée' } };
	gestionnaire.afficher(derniereVue, evenement);
	resoudre({ monter: () => ({ afficher: (vue, origine) => affichages.push([vue, origine]), demonter() {} }) });
	await attente;
	assert.deepEqual(affichages, [[derniereVue, evenement]]);
});

test('un bloc nul en fin de Manche démonte le module, et la Manche suivante du même mode en remonte un neuf', async () => {
	const appels = [];
	const zones = { principale: { replaceChildren() {} } };
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'ecran', zones, contexte: {},
		charger: async () => ({ monter() {
			const numero = appels.filter(a => a === 'monter').length + 1;
			appels.push('monter');
			return { afficher: vue => appels.push(['afficher', numero, vue.karaoke.mancheId]),
				demonter: () => appels.push(`demonter ${numero}`) };
		} }),
	});
	const fin = { ...vueSalle, modeEnCours: 'KARAOKE', karaoke: null };
	await gestionnaire.afficher(vueKaraoke(), capture);
	await gestionnaire.afficher(fin, evenement);
	await gestionnaire.afficher(fin, capture);
	await gestionnaire.afficher(vueKaraoke(secondeMancheKaraokeId), evenement);
	assert.deepEqual(appels, ['monter', ['afficher', 1, premiereMancheKaraokeId], 'demonter 1',
		'monter', ['afficher', 2, secondeMancheKaraokeId]]);
});

test('un bloc absent vaut un bloc nul : aucun module, et la Manche suivante en remonte un neuf', async () => {
	const appels = [];
	const erreurs = [];
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'ecran', zones: {}, contexte: {},
		surErreur: erreur => erreurs.push(erreur.message),
		charger: async () => ({ monter() {
			const numero = appels.filter(a => a === 'monter').length + 1;
			appels.push('monter');
			return { afficher: vue => appels.push(['afficher', numero, vue.karaoke.mancheId]),
				demonter: () => appels.push(`demonter ${numero}`) };
		} }),
	});
	const sansBloc = { ...vueSalle, modeEnCours: 'KARAOKE' };
	await gestionnaire.afficher(sansBloc, capture);
	assert.deepEqual(appels, []);
	await gestionnaire.afficher(vueKaraoke(), evenement);
	await gestionnaire.afficher(sansBloc, evenement);
	await gestionnaire.afficher(vueKaraoke(secondeMancheKaraokeId), evenement);
	assert.deepEqual(appels, ['monter', ['afficher', 1, premiereMancheKaraokeId], 'demonter 1',
		'monter', ['afficher', 2, secondeMancheKaraokeId]]);
	assert.deepEqual(erreurs, []);
});

test('une vue invalide avec un mode inconnu signale une erreur', async () => {
	const erreurs = [];
	const gestionnaire = creerGestionnaireDeMode({ ecran: 'ecran', zones: {}, contexte: {},
		surErreur: erreur => erreurs.push(erreur.message),
	});
	await gestionnaire.afficher({ ...vueSalle, modeEnCours: 'INCONNU' }, capture);
	assert.deepEqual(erreurs, ['Mode de jeu ou écran inconnu.']);
});
