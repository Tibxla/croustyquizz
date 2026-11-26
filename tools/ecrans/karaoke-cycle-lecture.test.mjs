import test from 'node:test';
import assert from 'node:assert/strict';
import { terminerMicrotaches } from './karaoke-dom-factice.mjs';
import { creerCycleLecture } from '../../src/main/resources/static/js/modes/karaoke/cycle-lecture.mjs';

const capture = { source: 'capture' };
const evenement = type => ({ source: 'evenement', type });
const maintenant = Date.parse('2026-11-17T21:00:00Z');
const fichier = { name: 'Beyoncé - Halo.WAV', size: 196, webkitRelativePath: 'secret/Halo.WAV' };
const appel = (passageId = 'p', champs = {}) => ({
	passageId, numeroModification: 1, etapeId: 'e-' + passageId, etat: 'APPELE', chanson: { titre: 'Halo', artiste: 'Beyoncé' },
	calculeLe: maintenant, echeanceAppelLe: maintenant + 5000, delaiAppelRestantMs: 5000, ...champs,
});
const appelEchu = () => appel('p', { calculeLe: maintenant + 5001, delaiAppelRestantMs: 0 });
const lecture = (champs = {}) => ({ lecteurId: 'ici', lectureId: 'l', source: 'FICHIER_LOCAL', etat: 'PREPAREE', ...champs });
const bloc = (courant, lecture = null, champs = {}) => ({ courant, lecture, lecteurDesigneId: 'ici', etat: 'EN_COURS', ...champs });
const demarre = (champs = {}) => bloc(appel('p', { etat: 'EN_COURS', numeroModification: 2 }), lecture({ etat: 'DEMARREE' }), champs);

function banc() {
	let fin, erreur, refus, lectureEnAttente;
	const commandes = [], gestes = [], chargements = [], etats = [];
	const lecteur = {
		debloquer: () => refus ? Promise.reject(refus) : Promise.resolve(),
		charger: f => new Promise((resolve, reject) => chargements.push({ f, resolve, reject })),
		lire() { gestes.push('lire'); return lectureEnAttente ?? Promise.resolve(); },
		pause() { gestes.push('pause'); },
		reprendre() { gestes.push('reprendre'); return Promise.resolve(); },
		position: () => 13500,
		surFin(f) { fin = f; return () => { fin = null; }; },
		surErreur(f) { erreur = f; return () => { erreur = null; }; },
		detruire() { gestes.push('libérer'); },
	};
	const cycle = creerCycleLecture({
		lecteur, lecteurId: 'ici',
		envoyer: (methode, chemin, corps) => new Promise((resolve, reject) => commandes.push({ methode, chemin, corps, resolve, reject })),
		surEtat: etat => etats.push(etat),
	});
	return { cycle, commandes, gestes, chargements, etats, etat: () => cycle.etat().etat,
		fin: () => fin?.(), erreur: () => erreur?.(),
		bloquer: () => { refus = new Error('Son bloqué'); }, debloquer: () => { refus = null; },
		attendreLecture: promesse => { lectureEnAttente = promesse; },
	};
}

async function preparer(fichiers = [fichier], courant = appel()) {
	const b = banc();
	await b.cycle.debloquer();
	b.cycle.choisirDossier(fichiers);
	b.cycle.surVue(bloc(courant), capture);
	return b;
}

test('désigne au choix, prépare le fichier exact, rattrape une réponse perdue via la vue puis joue sur le départ', async () => {
	const b = await preparer();
	assert.equal(b.etat(), 'preparation');
	assert.equal(b.cycle.etat().aEteLectrice, true);
	assert.equal(b.etats.at(-1).etat, 'preparation');
	assert.equal(b.chargements[0].f, fichier);
	b.chargements[0].resolve({ dureeMs: 20000 });
	await terminerMicrotaches();
	assert.deepEqual(b.commandes.map(({ methode, chemin, corps }) => [methode, chemin, corps]), [
		['PUT', '/lecteur', { lecteurId: 'ici' }],
		['POST', '/passages/p/lecture', { lecteurId: 'ici', etapeId: 'e-p', numeroModification: 1, source: 'FICHIER_LOCAL',
			fichier: { nom: fichier.name, tailleOctets: 196, dureeMs: 20000 }, motifParolesSeules: null }],
	]);
	b.commandes[1].reject(new Error('Réponse perdue'));
	await terminerMicrotaches();
	assert.equal(b.etat(), 'preparation');
	b.cycle.surVue(bloc(appel(), lecture()), evenement('lecture-chanson-modifiee'));
	assert.equal(b.etat(), 'preparee');
	b.cycle.surVue(demarre(), evenement('passage-demarre'));
	await terminerMicrotaches();
	assert.equal(b.etat(), 'lecture');
	assert.deepEqual(b.gestes, ['lire']);
	b.cycle.surVue(demarre({ lecteurDesigneId: 'ailleurs' }), evenement('lecture-chanson-modifiee'));
	assert.equal(b.cycle.etat().aEteLectrice, false);
	assert.deepEqual(b.gestes, ['lire']);
	b.fin();
	b.fin();
	assert.equal(b.etat(), 'fin');
	assert.deepEqual(b.commandes.at(-1).corps, { lecteurId: 'ici', lectureId: 'l', etapeId: 'e-p', positionMs: 13500 });
	assert.equal(b.commandes.at(-1).chemin, '/passages/p/fin-lecture');
	assert.equal(b.commandes.length, 3);
});

test('une confirmation avant la déclaration démarre en paroles seules et abandonne le chargement', async () => {
	const b = await preparer();
	const sansFichier = lecture({ lecteurId: null, lectureId: 'secours', source: 'PAROLES_SEULES', etat: 'DEMARREE',
		motifParolesSeules: 'ECRAN_NON_PRET' });
	b.cycle.surVue(bloc(demarre().courant, sansFichier), evenement('passage-demarre'));
	b.chargements[0].resolve({ dureeMs: 10000 });
	await terminerMicrotaches();
	assert.equal(b.etat(), 'pret');
	assert.deepEqual(b.gestes, ['libérer']);
	assert.deepEqual(b.commandes.map(c => c.chemin), ['/lecteur']);
});

test('une capture de sa lecture jamais jouée rend une seule erreur, y compris pendant la préparation', async () => {
	const b = await preparer();
	b.cycle.surVue(demarre(), capture);
	b.cycle.surVue(demarre(), capture);
	assert.equal(b.etat(), 'erreur');
	assert.ok(!b.gestes.includes('lire'));
	assert.deepEqual(b.commandes.at(-1).corps, { lecteurId: 'ici', lectureId: 'l', etapeId: 'e-p' });
	assert.equal(b.commandes.at(-1).chemin, '/passages/p/erreur-lecture');
	assert.equal(b.commandes.length, 2);
	b.cycle.surVue(bloc(appel('suivant')), capture);
	assert.equal(b.etat(), 'preparation');
	assert.equal(b.chargements.length, 2);
});

test('le retour de désignation prépare une seule fois et ignore les résultats de la préparation abandonnée', async () => {
	const b = await preparer();
	b.cycle.surVue(bloc(appel(), null, { lecteurDesigneId: 'autre' }), capture);
	assert.equal(b.etat(), 'pret');
	b.cycle.surVue(bloc(appel()), capture);
	assert.equal(b.chargements.length, 2);
	b.chargements[0].resolve({ dureeMs: 50 });
	b.chargements[1].resolve({ dureeMs: null });
	await terminerMicrotaches();
	assert.equal(b.commandes.length, 2);
	assert.equal(b.commandes[1].corps.fichier.dureeMs, null);
	b.commandes[1].resolve({ statut: 200, corps: { lectureId: 'l' } });
	await terminerMicrotaches();
	assert.equal(b.etat(), 'preparee');
});

test('une désignation après l’échéance prépare le fichier et permet de le jouer à la confirmation', async () => {
	const b = await preparer([fichier], appelEchu());
	assert.equal(b.chargements.length, 1);
	assert.deepEqual(b.commandes.map(c => c.chemin), ['/lecteur']);
	b.chargements[0].resolve({ dureeMs: 20000 });
	await terminerMicrotaches();
	assert.equal(b.commandes[1].chemin, '/passages/p/lecture');
	assert.equal(b.commandes[1].corps.source, 'FICHIER_LOCAL');
	assert.equal(b.commandes[1].corps.fichier.nom, fichier.name);
	b.cycle.surVue(demarre(), evenement('passage-demarre'));
	await terminerMicrotaches();
	assert.deepEqual(b.gestes, ['lire']);
});

test('les métadonnées arrivées après l’échéance donnent la durée réelle du fichier', async () => {
	const b = await preparer();
	b.cycle.surVue(bloc(appelEchu()), capture);
	assert.deepEqual(b.commandes.map(c => c.chemin), ['/lecteur']);
	b.chargements[0].resolve({ dureeMs: 20000 });
	await terminerMicrotaches();
	assert.equal(b.commandes[1].corps.source, 'FICHIER_LOCAL');
	assert.equal(b.commandes[1].corps.fichier.dureeMs, 20000);
	assert.equal(b.commandes[1].corps.motifParolesSeules, null);
});

for (const [nom, fichiers, motif] of [
	['absent', [], 'FICHIER_ABSENT'],
	['ambigu', [fichier, { ...fichier, webkitRelativePath: 'autre/Halo.WAV' }], 'FICHIER_AMBIGU'],
	['illisible', [fichier], 'FICHIER_ILLISIBLE'],
]) {
	test(`un fichier ${nom} donne le motif réel après l’échéance et aucun départ du lecteur`, async () => {
		const b = await preparer(fichiers, appelEchu());
		if (nom === 'illisible') {
			b.chargements[0].reject(new Error('Corrompu'));
			await terminerMicrotaches();
		}
		assert.equal(b.commandes[1].corps.motifParolesSeules, motif);
		b.commandes[1].resolve({ statut: 409, corps: { code: 'COMMANDE_PERIMEE' } });
		await terminerMicrotaches();
		assert.equal(b.etat(), 'preparation');
		b.cycle.surVue(bloc(demarre().courant, lecture({ source: 'PAROLES_SEULES', lecteurId: null, etat: 'DEMARREE' })), evenement('passage-demarre'));
		assert.equal(b.etat(), 'pret');
		assert.ok(!b.gestes.includes('lire'));
		assert.equal(b.commandes.length, 2);
	});
}

async function fichierPrepare(fichiers) {
	const b = await preparer(fichiers);
	b.chargements[0].resolve({ dureeMs: 20000 });
	await terminerMicrotaches();
	b.commandes[1].resolve({ statut: 200, corps: { lectureId: 'l' } });
	await terminerMicrotaches();
	return b;
}

async function fichierEnLecture(fichiers) {
	const b = await fichierPrepare(fichiers);
	b.cycle.surVue(demarre(), evenement('passage-demarre'));
	await terminerMicrotaches();
	return b;
}

for (const phase of ['lecture', 'pause', 'fin']) {
	for (const enchaine of [true, false]) {
		test(`depuis ${phase}, une clôture ${enchaine ? 'avec nouvel appel prépare le suivant' : 'sans nouvel appel laisse le lecteur prêt'}`, async t => {
			const suivant = { name: 'Duo fictif - Le Phare.ogg', size: 315 };
			const b = await fichierEnLecture([fichier, suivant]);
			t.after(() => b.cycle.detruire());
			if (phase === 'pause') b.cycle.surVue(demarre({ etat: 'EN_PAUSE' }), evenement('manche-en-pause'));
			if (phase === 'fin') b.fin();
			assert.equal(b.etat(), phase);
			const nouveauCourant = enchaine
				? appel('suivant', { chanson: { titre: 'Le Phare', artiste: 'Duo fictif' } })
				: appel('p', { etat: 'TERMINE', numeroModification: 3 });
			const vue = bloc(nouveauCourant);
			const origine = evenement(enchaine ? 'passage-appele' : 'passage-termine');
			b.cycle.surVue(vue, origine);

			assert.equal(b.etat(), enchaine ? 'preparation' : 'pret');
			assert.equal(b.gestes.filter(geste => geste === 'libérer').length, 1);
			assert.equal(b.chargements.length, enchaine ? 2 : 1);
			if (enchaine) {
				assert.equal(b.chargements[1].f.name, suivant.name);
				b.chargements[1].resolve({ dureeMs: 30000 });
				await terminerMicrotaches();
			}
			b.cycle.surVue(vue, capture);
			const declarations = b.commandes.filter(commande => commande.chemin === '/passages/suivant/lecture');
			assert.equal(declarations.length, enchaine ? 1 : 0);
			if (enchaine) {
				assert.equal(declarations[0].corps.etapeId, 'e-suivant');
				assert.equal(declarations[0].corps.numeroModification, 1);
			}
			assert.equal(b.chargements.length, enchaine ? 2 : 1);
			assert.equal(b.gestes.filter(geste => geste === 'libérer').length, 1);
		});
	}
}

test('le départ trouve la lecture locale dans la vue même si le 2xx a précédé la publication de la préparation', async () => {
	const b = await fichierPrepare();
	b.cycle.surVue(demarre(), evenement('passage-demarre'));
	await terminerMicrotaches();
	assert.equal(b.etat(), 'lecture');

	const b2 = await fichierPrepare();
	b2.cycle.surVue(bloc(appel(), null, { lecteurDesigneId: 'ailleurs' }), evenement('lecture-chanson-modifiee'));
	const autre = lecture({ etat: 'DEMARREE', lecteurId: 'ailleurs', lectureId: 'autre-lecture' });
	b2.cycle.surVue(bloc(demarre().courant, autre, { lecteurDesigneId: 'ailleurs' }), evenement('passage-demarre'));
	assert.equal(b2.etat(), 'pret');
	assert.ok(!b2.gestes.includes('lire'));
});

test('un nom de fichier interdit par le contrat est déclaré illisible sans charger le fichier', async () => {
	const b = await preparer([{ ...fichier, name: 'Beyoncé\\Halo.WAV' }]);
	assert.equal(b.chargements.length, 0);
	assert.deepEqual(b.commandes[1].corps, { lecteurId: 'ici', etapeId: 'e-p', numeroModification: 1,
		source: 'PAROLES_SEULES', fichier: null, motifParolesSeules: 'FICHIER_ILLISIBLE' });
});

test('une capture d’un Passage déjà commencé en paroles seules libère le fichier préparé', async () => {
	const sansFichier = lecture({ lecteurId: null, lectureId: 'secours', source: 'PAROLES_SEULES', etat: 'DEMARREE',
		motifParolesSeules: 'ECRAN_NON_PRET' });
	for (const [preparerFichier, lectureVue] of [[preparer, null], [fichierPrepare, sansFichier]]) {
		const b = await preparerFichier();
		b.cycle.surVue(bloc(demarre().courant, lectureVue), capture);
		assert.equal(b.etat(), 'pret');
		assert.deepEqual(b.gestes, ['libérer']);
	}

	const b = await fichierPrepare();
	b.bloquer();
	await b.cycle.debloquer();
	b.cycle.surVue(bloc(appel('p', { etat: 'EN_PAUSE', numeroModification: 2 }), sansFichier, { etat: 'EN_PAUSE' }), capture);
	b.debloquer();
	await b.cycle.debloquer();
	assert.equal(b.etat(), 'pret');
	assert.deepEqual(b.gestes, ['libérer']);
});

test('une redésignation pendant le blocage recharge une seule fois à la reprise du même appel', async () => {
	for (const avantBlocage of [false, true]) {
		const b = await fichierPrepare();
		const ailleurs = bloc(appel(), null, { lecteurDesigneId: 'ailleurs' });
		if (avantBlocage) b.cycle.surVue(ailleurs, capture);
		b.bloquer();
		await b.cycle.debloquer();
		if (!avantBlocage) b.cycle.surVue(ailleurs, capture);
		b.cycle.surVue(bloc(appel()), capture);
		assert.equal(b.etat(), 'son-bloque');
		assert.equal(b.chargements.length, 1);
		b.debloquer();
		await b.cycle.debloquer();
		assert.equal(b.etat(), 'preparation');
		assert.equal(b.chargements.length, 2);
		b.chargements[1].resolve({ dureeMs: 20000 });
		await terminerMicrotaches();
		assert.equal(b.commandes.filter(c => c.chemin.endsWith('/lecture')).length, 2);
	}
});

test('un refus de déblocage pendant un lire en attente laisse aboutir le départ', async () => {
	const b = await fichierPrepare();
	let terminerLecture;
	b.attendreLecture(new Promise(resolve => { terminerLecture = resolve; }));
	b.cycle.surVue(demarre(), evenement('passage-demarre'));
	b.bloquer();
	await b.cycle.debloquer();
	assert.equal(b.etat(), 'preparee');
	assert.equal(b.cycle.etat().sonDebloque, true);
	terminerLecture();
	await terminerMicrotaches();
	assert.equal(b.etat(), 'lecture');
	assert.deepEqual(b.gestes, ['lire']);
});

test('un dossier restauré avec le son bloqué attend le déblocage avant de préparer le fichier', async () => {
	const b = banc();
	b.cycle.reprendreDossier({ fichiers: [fichier], aEteLectrice: true, sonDebloque: false });
	assert.equal(b.etat(), 'son-bloque');
	b.cycle.surVue(bloc(appel()), capture);
	assert.equal(b.chargements.length, 0);
	await b.cycle.debloquer();
	assert.equal(b.chargements.length, 1);
});

test('la clôture suivant la fin locale libère le fichier sans émettre une seconde fin', async () => {
	const b = await fichierEnLecture();
	b.fin();
	assert.equal(b.etat(), 'fin');
	b.cycle.surVue(bloc(appel('p', { etat: 'TERMINE' })), evenement('passage-termine'));
	assert.equal(b.etat(), 'pret');
	assert.deepEqual(b.gestes, ['lire', 'libérer']);
	b.fin();
	assert.equal(b.commandes.filter(c => c.chemin.endsWith('/fin-lecture')).length, 1);
});

test('deux vues identiques en prêt ne redéclarent pas sans redésignation', async () => {
	const b = await fichierPrepare();
	b.cycle.surVue(bloc(appel(), null, { lecteurDesigneId: null }), capture);
	assert.equal(b.etat(), 'pret');
	const memeAppel = bloc(appel());
	b.cycle.surVue(memeAppel, capture);
	b.cycle.surVue(memeAppel, capture);
	assert.equal(b.etat(), 'pret');
	assert.equal(b.chargements.length, 1);
	assert.equal(b.commandes.filter(c => c.chemin.endsWith('/lecture')).length, 1);
});

test('pause, relevé unique, capture sans reprise et reprise sur transition observée', async () => {
	const b = await fichierEnLecture();
	const enPause = { ...demarre({ etat: 'EN_PAUSE' }), courant: appel('p', { etat: 'EN_PAUSE' }),
		lecture: lecture({ etat: 'DEMARREE', demandePosition: { demandeId: 'd1' } }) };
	b.cycle.surVue(enPause, evenement('manche-mise-en-pause'));
	b.cycle.surVue(enPause, capture);
	assert.deepEqual(b.gestes, ['lire', 'pause']);
	assert.equal(b.etat(), 'pause');
	assert.deepEqual(b.commandes.at(-1).corps, { lecteurId: 'ici', lectureId: 'l', etapeId: 'e-p', demandeId: 'd1', positionMs: 13500 });
	assert.equal(b.commandes.at(-1).chemin, '/passages/p/calage/position');
	assert.equal(b.commandes.length, 3);
	b.cycle.surVue(demarre(), capture);
	b.cycle.surVue(demarre(), evenement('lecture-chanson-modifiee'));
	assert.equal(b.etat(), 'pause');
	assert.deepEqual(b.gestes, ['lire', 'pause']);
	b.cycle.surVue(enPause, evenement('manche-mise-en-pause'));
	b.cycle.surVue(demarre(), evenement('manche-reprise'));
	await terminerMicrotaches();
	assert.equal(b.etat(), 'lecture');
	assert.deepEqual(b.gestes, ['lire', 'pause', 'reprendre']);
	b.erreur(); b.erreur();
	assert.equal(b.etat(), 'erreur');
	assert.equal(b.commandes.filter(c => c.chemin.endsWith('/erreur-lecture')).length, 1);
});

test('le refus de lire produit une erreur unique, la réponse tardive respecte une pause ou une clôture', async () => {
	for (const issue of ['refus', 'pause', 'clôture']) {
		const b = await fichierPrepare();
		let resoudre, rejeter;
		b.attendreLecture(new Promise((resolve, reject) => { resoudre = resolve; rejeter = reject; }));
		b.cycle.surVue(demarre(), evenement('passage-demarre'));
		if (issue === 'refus') rejeter(new DOMException('Bloqué', 'NotAllowedError'));
		if (issue === 'pause') b.cycle.surVue(demarre({ etat: 'EN_PAUSE' }), capture);
		if (issue === 'clôture') b.cycle.surVue(bloc(appel('p', { etat: 'TERMINE' })), capture);
		if (issue !== 'refus') resoudre();
		await terminerMicrotaches();
		assert.equal(b.etat(), { refus: 'erreur', pause: 'pause', clôture: 'pret' }[issue]);
		assert.deepEqual(b.gestes, ['lire', issue === 'pause' ? 'pause' : 'libérer']);
		assert.equal(b.commandes.filter(c => c.chemin.endsWith('/erreur-lecture')).length, Number(issue === 'refus'));
	}
});

test('la fenêtre précédente se redésigne une fois, celle qui arrive pendant un Passage affiche seulement', () => {
	for (const [designe, aEteLectrice, attendus] of [[null, true, 1], ['ailleurs', true, 0], [null, false, 0]]) {
		const b = banc();
		b.cycle.reprendreDossier({ fichiers: [fichier], aEteLectrice, sonDebloque: true });
		b.cycle.surVue(bloc(null, null, { lecteurDesigneId: designe }), capture);
		b.cycle.surVue(bloc(null, null, { lecteurDesigneId: null }), capture);
		assert.equal(b.commandes.length, attendus);
		const autreLecture = { ...demarre({ lecteurDesigneId: 'ailleurs' }), lecture: lecture({ etat: 'DEMARREE', lecteurId: 'ailleurs' }) };
		b.cycle.surVue(autreLecture, capture);
		assert.deepEqual(b.gestes, []);
		assert.equal(b.commandes.length, attendus);
		assert.equal(b.cycle.etat().aEteLectrice, false);
	}
});

test('sans dossier, le blocage désigne dès le choix, mais le fichier attend le déblocage', async () => {
	const b = banc();
	b.bloquer();
	await b.cycle.debloquer();
	assert.equal(b.etat(), 'son-bloque');
	b.debloquer();
	await b.cycle.debloquer();
	assert.equal(b.etat(), 'sans-dossier');
	b.bloquer();
	await b.cycle.debloquer();
	b.cycle.choisirDossier([fichier]);
	assert.equal(b.commandes.at(-1).chemin, '/lecteur');
	b.cycle.surVue(bloc(appel()), capture);
	assert.equal(b.chargements.length, 0);
	b.debloquer();
	await b.cycle.debloquer();
	assert.equal(b.chargements.length, 1);
	assert.equal(b.etat(), 'preparation');
});

for (const phase of ['preparation', 'preparee', 'lecture']) {
	test(`le nouveau dossier est différé en ${phase} et le nouvel appel applique le dernier choix`, async () => {
		const b = await { preparation: preparer, preparee: fichierPrepare, lecture: fichierEnLecture }[phase]();
		b.cycle.choisirDossier([]);
		const nouveau = { ...fichier, size: 300 };
		b.cycle.choisirDossier([nouveau]);
		assert.equal(b.commandes.filter(c => c.chemin === '/lecteur').length, 1);
		assert.equal(b.chargements.length, 1);
		b.cycle.surVue(bloc(appel('p2')), capture);
		assert.equal(b.etat(), 'preparation');
		assert.equal(b.commandes.filter(c => c.chemin === '/lecteur').length, 2);
		assert.equal(b.chargements[1].f, nouveau);
	});
}

for (const phase of ['preparation', 'preparee']) {
	test(`son bloqué pendant ${phase} : la préparation survit et le dossier reste différé`, async () => {
		const b = await (phase === 'preparation' ? preparer() : fichierPrepare());
		b.bloquer();
		await b.cycle.debloquer();
		assert.equal(b.etat(), 'son-bloque');
		b.cycle.choisirDossier([]);
		assert.equal(b.commandes.filter(c => c.chemin === '/lecteur').length, 1);
		b.debloquer();
		await b.cycle.debloquer();
		assert.equal(b.etat(), phase);
		b.cycle.surVue(bloc(appel('p2')), capture);
		assert.equal(b.etat(), 'preparation');
		assert.equal(b.commandes.filter(c => c.chemin === '/lecteur').length, 2);
		assert.equal(b.commandes.at(-1).corps.motifParolesSeules, 'FICHIER_ABSENT');
	});
}

test('les métadonnées de la version en cours restent valides pendant un choix de dossier différé', async () => {
	const b = await preparer();
	b.cycle.choisirDossier([{ ...fichier, size: 300 }]);
	b.chargements[0].resolve({ dureeMs: 20000 });
	await terminerMicrotaches();
	assert.equal(b.commandes[1].corps.fichier.tailleOctets, 196);
	b.commandes[1].resolve({ statut: 200, corps: { lectureId: 'l' } });
	await terminerMicrotaches();
	b.cycle.surVue(bloc(appel('p', { etat: 'SAUTE' })), capture);
	assert.equal(b.etat(), 'pret');
	assert.deepEqual(b.commandes.map(c => c.chemin), ['/lecteur', '/passages/p/lecture', '/lecteur']);
});

test('le choix après une fin ou une erreur se désigne aussitôt et garde l’état courant', async () => {
	for (const etat of ['fin', 'erreur']) {
		const b = await fichierEnLecture();
		b[etat]();
		b.cycle.choisirDossier([]);
		assert.equal(b.etat(), etat);
		assert.equal(b.commandes.at(-1).chemin, '/lecteur');
		assert.equal(b.chargements.length, 1);
	}
});

test('le refus de déblocage ne coupe pas un fichier en lecture ou en pause', async () => {
	const b = await fichierEnLecture();
	b.bloquer();
	await b.cycle.debloquer();
	assert.equal(b.etat(), 'lecture');
	b.cycle.surVue(demarre({ etat: 'EN_PAUSE' }), capture);
	await b.cycle.debloquer();
	assert.equal(b.etat(), 'pause');
	b.cycle.choisirDossier([]);
	assert.equal(b.commandes.at(-1).chemin, '/lecteur');
	assert.deepEqual(b.gestes, ['lire', 'pause']);
	b.cycle.surVue(bloc(demarre().courant, lecture({ source: 'PAROLES_SEULES', etat: 'DEMARREE' })), evenement('lecture-chanson-modifiee'));
	assert.equal(b.etat(), 'pret');
	b.cycle.detruire();
	b.erreur(); b.fin();
	assert.deepEqual(b.gestes, ['lire', 'pause', 'libérer', 'libérer']);
});

test('un nouvel appel capturé après une lecture déjà jouée réexamine sa vue une fois, sans jouer tard', async () => {
	const b = await fichierEnLecture();
	const nouveau = bloc(appel('p2', { etat: 'EN_COURS' }), lecture({ lectureId: 'l2', etat: 'DEMARREE' }));
	b.cycle.surVue(nouveau, capture);
	b.cycle.surVue(nouveau, capture);
	assert.equal(b.etat(), 'erreur');
	assert.deepEqual(b.gestes.filter(g => g === 'lire'), ['lire']);
	assert.equal(b.commandes.filter(c => c.chemin === '/passages/p2/erreur-lecture').length, 1);
});

test('sans fichier du Lieu, une Chanson de la Bibliothèque de base se prépare par son URL et se déclare comme un fichier local', async () => {
	const libre = { fichier: '/bibliotheque/chansons/les-lucioles-la-lanterne.mp3', tailleOctets: 2400, licence: 'CC BY-SA 4.0',
		urlLicence: 'https://creativecommons.org/licenses/by-sa/4.0/deed.fr', urlSource: 'https://example.org/la-lanterne' };
	const chanson = { titre: 'La lanterne', artiste: 'Les Lucioles', origine: 'BIBLIOTHEQUE', bibliotheque: libre };
	const b = await preparer([], appel('p', { chanson }));
	assert.deepEqual(b.chargements[0].f, { name: 'les-lucioles-la-lanterne.mp3', size: 2400, url: libre.fichier });
	b.chargements[0].resolve({ dureeMs: 4000 });
	await terminerMicrotaches();
	assert.deepEqual(b.commandes.at(-1).corps, { lecteurId: 'ici', etapeId: 'e-p', numeroModification: 1, source: 'FICHIER_LOCAL',
		fichier: { nom: 'les-lucioles-la-lanterne.mp3', tailleOctets: 2400, dureeMs: 4000 }, motifParolesSeules: null });
	// Le fichier du Lieu, s'il existe, l'emporte.
	const lieu = { name: 'Les Lucioles - La lanterne.mp3', size: 99, webkitRelativePath: 'Musique/Les Lucioles - La lanterne.mp3' };
	const avecDossier = await preparer([lieu], appel('p', { chanson }));
	assert.equal(avecDossier.chargements[0].f, lieu);
});

test('un fichier du Lieu illisible laisse sa chance à la Bibliothèque de base avant les paroles seules', async () => {
	const url = '/bibliotheque/chansons/la-lanterne.mp3';
	const chanson = { titre: 'La lanterne', artiste: 'Les Lucioles', origine: 'BIBLIOTHEQUE',
		bibliotheque: { fichier: url, tailleOctets: 2400 } };
	const lieu = { name: 'Les Lucioles - La lanterne.mp3', size: 99 };
	for (const lisible of [true, false]) {
		const b = await preparer([lieu], appel('p', { chanson }));
		assert.equal(b.chargements[0].f, lieu);
		b.chargements[0].reject(new Error('Fichier du Lieu illisible'));
		await terminerMicrotaches();
		assert.equal(b.chargements[1]?.f.url, url);
		assert.deepEqual(b.commandes.map(c => c.chemin), ['/lecteur'], 'attend le résultat de la bibliothèque');
		if (lisible) b.chargements[1].resolve({ dureeMs: 4000 });
		else b.chargements[1].reject(new Error('Fichier de la bibliothèque illisible'));
		await terminerMicrotaches();
		assert.equal(b.commandes.at(-1).chemin, '/passages/p/lecture');
		assert.equal(b.commandes.at(-1).corps.source, lisible ? 'FICHIER_LOCAL' : 'PAROLES_SEULES');
		assert.equal(b.commandes.at(-1).corps.motifParolesSeules, lisible ? null : 'FICHIER_ILLISIBLE');
	}
});

test('un Passage inscrit avant le retrait de son entrée du catalogue se prépare en paroles seules', async () => {
	const chanson = { titre: 'La lanterne', artiste: 'Les Lucioles', origine: 'BIBLIOTHEQUE', bibliotheque: null };
	const homonyme = { name: 'Les Lucioles - La lanterne.mp3', size: 99 };
	const b = await preparer([homonyme], appel('p', { chanson }));
	assert.deepEqual(b.chargements, [], 'le fichier homonyme ne remplace pas l’enregistrement retiré');
	assert.equal(b.commandes.at(-1).chemin, '/passages/p/lecture');
	assert.equal(b.commandes.at(-1).corps.source, 'PAROLES_SEULES');
	assert.equal(b.commandes.at(-1).corps.motifParolesSeules, 'FICHIER_ABSENT');
});
