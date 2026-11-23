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
const lecture = (champs = {}) => ({ lecteurId: 'ici', lectureId: 'l', source: 'FICHIER_LOCAL', etat: 'PREPAREE', ...champs });
const bloc = (courant, lecture = null, champs = {}) => ({ courant, lecture, lecteurDesigneId: 'ici', etat: 'EN_COURS', ...champs });
const demarre = (champs = {}) => bloc(appel('p', { etat: 'EN_COURS', numeroModification: 2 }), lecture({ etat: 'DEMARREE' }), champs);

function banc() {
	let temps = maintenant, numero = 0, fin, erreur, refus, lectureEnAttente;
	const delais = new Map(), commandes = [], gestes = [], chargements = [], etats = [];
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
		lecteur, lecteurId: 'ici', horloge: { maintenantServeur: () => temps },
		envoyer: (methode, chemin, corps) => new Promise((resolve, reject) => commandes.push({ methode, chemin, corps, resolve, reject })),
		surEtat: etat => etats.push(etat),
		setTimeout(f, ms) { delais.set(++numero, { f, ms }); return numero; },
		clearTimeout: id => delais.delete(id),
	});
	return { cycle, commandes, gestes, chargements, delais, etats, etat: () => cycle.etat().etat,
		fin: () => fin?.(), erreur: () => erreur?.(),
		bloquer: () => { refus = new Error('Son bloqué'); }, debloquer: () => { refus = null; },
		attendreLecture: promesse => { lectureEnAttente = promesse; },
		avancer: ms => { temps += ms; },
		expirer() { for (const [id, { f }] of delais) { delais.delete(id); f(); } },
	};
}

async function preparer(fichiers = [fichier]) {
	const b = banc();
	await b.cycle.debloquer();
	b.cycle.choisirDossier(fichiers);
	b.cycle.surVue(bloc(appel()), capture);
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

test('recalage du délai par la vue, suspension en pause puis expiration avant les métadonnées', async () => {
	const b = await preparer();
	assert.equal([...b.delais.values()][0].ms, 5000);
	b.avancer(2000);
	b.cycle.surVue(bloc(appel()), capture);
	assert.equal([...b.delais.values()][0].ms, 3000);
	b.cycle.surVue(bloc(appel('p', { echeanceAppelLe: null }), null, { etat: 'EN_PAUSE' }), capture);
	assert.equal(b.delais.size, 0);
	b.cycle.surVue(bloc(appel('p', { calculeLe: maintenant + 2000, delaiAppelRestantMs: 3000 })), capture);
	b.expirer();
	assert.equal(b.commandes.at(-1).corps.motifParolesSeules, 'FICHIER_ILLISIBLE');
	assert.equal(b.commandes.at(-1).corps.fichier, null);
	b.chargements[0].resolve({ dureeMs: 10000 });
	await terminerMicrotaches();
	assert.equal(b.commandes.length, 2);
	b.cycle.detruire();
	assert.equal(b.delais.size, 0);
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

test('une désignation après l’échéance renonce aux métadonnées immédiatement', async () => {
	const b = banc();
	await b.cycle.debloquer();
	b.cycle.choisirDossier([fichier]);
	b.avancer(5001);
	b.cycle.surVue(bloc(appel()), capture);
	assert.equal(b.chargements.length, 0);
	assert.equal(b.commandes.at(-1).corps.motifParolesSeules, 'FICHIER_ILLISIBLE');
});

for (const [nom, fichiers, motif] of [
	['absent', [], 'FICHIER_ABSENT'],
	['ambigu', [fichier, { ...fichier, webkitRelativePath: 'autre/Halo.WAV' }], 'FICHIER_AMBIGU'],
	['illisible', [fichier], 'FICHIER_ILLISIBLE'],
]) {
	test(`un fichier ${nom} donne les paroles seules et aucun départ du lecteur`, async () => {
		const b = await preparer(fichiers);
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

async function fichierPrepare() {
	const b = await preparer();
	b.chargements[0].resolve({ dureeMs: 20000 });
	await terminerMicrotaches();
	b.commandes[1].resolve({ statut: 200, corps: { lectureId: 'l' } });
	await terminerMicrotaches();
	return b;
}

async function fichierEnLecture() {
	const b = await fichierPrepare();
	b.cycle.surVue(demarre(), evenement('passage-demarre'));
	await terminerMicrotaches();
	return b;
}

test('le reçu permet de rattraper une vue portant le même lectureId et la vue est prioritaire si elle désigne un autre lecteur', async () => {
	const b = await fichierPrepare();
	b.cycle.surVue({ ...demarre(), lecture: lecture({ etat: 'DEMARREE', lecteurId: null }) }, evenement('passage-demarre'));
	await terminerMicrotaches();
	assert.equal(b.etat(), 'lecture');
	const autre = await fichierPrepare();
	autre.cycle.surVue({ ...demarre(), lecture: lecture({ etat: 'DEMARREE', lecteurId: 'ailleurs' }) }, evenement('passage-demarre'));
	assert.equal(autre.etat(), 'pret');
	assert.ok(!autre.gestes.includes('lire'));
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
