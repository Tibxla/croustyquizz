import test from 'node:test';
import assert from 'node:assert/strict';
import { validerRecherche, creerMachineRecherche } from '../../src/main/resources/static/js/modes/karaoke/recherche.mjs';

const contexte = { soireeId: 's', mancheId: 'm', configurationInterface: { longueurRechercheMax: 120, delaiConsultationNavigateurMs: 80 } };
function banc() {
	const appels = [], etats = [], delais = new Map(); let id = 0;
	const machine = creerMachineRecherche({ envoyer: (...args) => new Promise((resolve, reject) => appels.push({ args, resolve, reject })),
		creerUuid: () => `r${++id}`, programmerDelai: f => { delais.set(++id, f); return id; }, annulerDelai: id => delais.delete(id), surEtat: e => etats.push(e) });
	return { machine, appels, etats, delais };
}

test('la validation compte les points de code bruts, normalise NFC et les espaces Unicode', () => {
	assert.deepEqual(validerRecherche('  e\u0301\u2003東京  ', 20), { valide: true, texteAffiche: 'é 東京', message: null });
	assert.equal(validerRecherche('𐐀'.repeat(120), 120).valide, true);
	assert.equal(validerRecherche('a'.repeat(121), 120).message, 'La recherche est trop longue.');
	assert.equal(validerRecherche('ab', 1).valide, false);
	assert.equal(validerRecherche('  ', 120).message, 'Saisis un titre ou un artiste.');
	assert.equal(validerRecherche('🎵', 120).message, 'Utilise une lettre ou un chiffre.');
	assert.equal(validerRecherche('\uFEFFa', 1).valide, false); // FEFF n'est pas White_Space côté serveur.
	assert.equal(validerRecherche('\u001Ca', 1).valide, true); // trim() Java enlève les contrôles ASCII en bordure.
});

test('consulter et fermer une fiche préservent la recherche ; détruire résout toute lecture invalidée sans émission', async () => {
	const b = banc();
	const recherche = b.machine.chercher('titre', contexte), fiche = b.machine.consulter('c', contexte);
	b.machine.fermerFiche(); assert.equal(await fiche, null);
	assert.equal(b.appels[0].args[3].signal.aborted, false);
	b.appels[0].resolve({ statut: 200, corps: { chansons: [{ titre: 'Chanson' }] } });
	assert.equal((await recherche).etat, 'resultats');
	const tardive = b.machine.consulter('autre', contexte), nombre = b.etats.length;
	b.machine.detruire(); assert.equal(await tardive, null);
	b.appels[2].reject(new Error()); await Promise.resolve(); await Promise.resolve();
	assert.equal(b.etats.length, nombre);
});

test('une saisie invalide n’émet ni chargement ni requête et invalider reste silencieux', async () => {
	const b = banc();
	const resultat = await b.machine.chercher('', contexte);
	assert.equal(resultat.origineErreur, 'validation'); assert.equal(b.etats.length, 1); assert.equal(b.appels.length, 0);
	b.machine.invalider(); assert.equal(b.etats.length, 1);
});

test('seule la génération courante aboutit et le brut est envoyé, un résultat vide reste un succès', async () => {
	const b = banc();
	const ancien = b.machine.chercher('ancien', contexte);
	const courant = b.machine.chercher('  é  ', contexte);
	assert.equal(new URL(b.appels[1].args[1], 'http://local').searchParams.get('recherche'), '  é  ');
	assert.equal(b.appels[0].args[3].signal.aborted, true);
	b.appels[0].resolve({ statut: 200, corps: { chansons: [{ titre: 'ancien' }] } });
	assert.equal(await ancien, null);
	b.appels[1].resolve({ statut: 200, corps: { chansons: [] } });
	assert.equal((await courant).etat, 'vide'); assert.equal(b.etats.at(-1).donnees.chansons.length, 0);
});

test('fiche, réseau, HTTP et délai ont des origines distinctes, sans rejet', async () => {
	const b = banc();
	let attente = b.machine.consulter('opaque/a', contexte);
	assert.match(b.appels[0].args[1], /opaque%2Fa$/);
	b.appels[0].resolve({ statut: 404, corps: { code: 'RESSOURCE_INTROUVABLE', detail: 'Disparue' } });
	assert.equal((await attente).origineErreur, 'http'); assert.equal(b.etats.at(-1).detail, 'Disparue');
	attente = b.machine.chercher('titre', contexte); b.appels[1].reject(new Error('réseau'));
	assert.equal((await attente).origineErreur, 'reseau');
	attente = b.machine.consulter('c', contexte); [...b.delais.values()][0]();
	assert.equal((await attente).origineErreur, 'delai');
	b.appels[2].resolve({ statut: 200, corps: {} }); await Promise.resolve();
	assert.equal(b.etats.at(-1).origineErreur, 'delai');
	attente = b.machine.consulter('c', contexte); b.machine.fermerFiche(); b.appels[3].resolve({ statut: 200, corps: {} });
	assert.equal(await attente, null);
	b.machine.detruire(); assert.equal(await b.machine.chercher('titre', contexte), null);
});
