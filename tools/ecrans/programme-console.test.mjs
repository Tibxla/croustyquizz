import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { monterProgramme, REGLAGES_DES_MODES } from '../../src/main/resources/static/js/programme-console.mjs';
import { creerContexte } from '../../src/main/resources/static/js/contexte.mjs';
import { creerTransport } from '../../src/main/resources/static/js/transport.mjs';
import { creerDocument, terminerMicrotaches } from './karaoke-dom-factice.mjs';

function banc({ envoyer, surEnvoi } = {}) {
	const document = creerDocument();
	const panneau = document.createElement('section');
	document.body.append(panneau);
	const creer = (nom, balise) => {
		const element = document.createElement(balise);
		element.setAttribute('data-' + nom, '');
		panneau.append(element);
		return element;
	};
	const formulaire = creer('ajout-manche', 'form');
	const mode = creer('mode-manche', 'select');
	const communs = creer('reglages-communs', 'fieldset');
	const nombre = creer('nombre-etapes', 'input');
	const temps = creer('temps-par-etape', 'input');
	const ajout = creer('soumettre-manche', 'button');
	const liste = creer('programme', 'ol');
	const programmeFige = creer('programme-fige', 'p');
	creer('programme-vide', 'p');
	const appels = [];
	let captures = 0;
	const { contexte, changerEtat } = creerContexte({
		soireeId: 's',
		envoyer: envoyer ?? ((...args) => new Promise((resoudre, rejeter) => appels.push({ args, resoudre, rejeter }))),
		resynchroniser: () => captures++,
		surEnvoi,
	});
	const programme = monterProgramme(panneau, contexte);
	return {
		ajout, appels, communs, formulaire, liste, mode, nombre, programmeFige, temps, programme,
		captures: () => captures, connecter: changerEtat,
		afficher: (manches, etat = 'PREPARATION') => programme.afficher({ soiree: { etat }, programme: manches }),
	};
}

const manche = (id, ordre, mode = 'QUIZ') => ({ id, ordre, mode, etat: 'EN_ATTENTE', reglages: {} });

test('ajoute les réglages communs du Quiz, puis laisse la vue synchronisée afficher la Manche', async () => {
	const b = banc();
	b.afficher([]);
	b.mode.value = 'QUIZ';
	b.mode.dispatchEvent({ type: 'change' });
	b.nombre.value = '5';
	b.temps.value = '25';
	b.formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels.length, 0);

	b.connecter('synchronisee');
	b.formulaire.dispatchEvent({ type: 'submit' });
	b.formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels.length, 1);
	assert.deepEqual(b.appels[0].args.slice(0, 3), [
		'POST', '/api/soirees/s/manches', { mode: 'QUIZ', reglages: { nombreEtapes: 5, tempsParEtapeSecondes: 25 } },
	]);
	b.appels[0].resoudre({ statut: 201, corps: manche('a', 0) });
	await terminerMicrotaches();
	assert.equal(b.captures(), 1);
	assert.equal(b.liste.childElementCount, 0);
	b.afficher([manche('a', 0)]);
	assert.match(b.liste.textContent, /Quiz/);
});

test('le Karaoké envoie un objet de réglages vide et le démontage annule l’envoi', async () => {
	const b = banc();
	b.afficher([], 'OUVERTE');
	b.connecter('synchronisee');
	b.mode.value = 'KARAOKE';
	b.mode.dispatchEvent({ type: 'change' });
	assert.equal(b.communs.hidden, true);
	assert.equal(b.communs.disabled, true);
	b.formulaire.dispatchEvent({ type: 'submit' });
	assert.deepEqual(b.appels[0].args[2], { mode: 'KARAOKE', reglages: {} });
	const signal = b.appels[0].args[3].signal;
	b.programme.demonter();
	assert.equal(signal.aborted, true);
	b.appels[0].rejeter(new DOMException('Annulation', 'AbortError'));
	await terminerMicrotaches();
	assert.equal(b.captures(), 0);
});

test('déplace avec une permutation complète, retire avec DELETE et affiche le programme figé', async () => {
	const b = banc();
	b.connecter('synchronisee');
	const a = manche('a', 0);
	const c = manche('c', 1, 'KARAOKE');
	b.afficher([a, c], 'OUVERTE');
	const monter = b.liste.querySelectorAll('[data-action-programme="monter"]');
	assert.equal(monter[0].disabled, true);
	monter[1].click();
	assert.deepEqual(b.appels[0].args.slice(0, 3), [
		'PUT', '/api/soirees/s/ordre-des-manches', { mancheIds: ['c', 'a'] },
	]);
	b.appels[0].resoudre({ statut: 200, corps: {} });
	await terminerMicrotaches();
	b.afficher([{ ...c, ordre: 0 }, { ...a, ordre: 1 }], 'OUVERTE');
	b.liste.querySelector('[data-action-programme="retirer"]').click();
	assert.deepEqual(b.appels[1].args.slice(0, 3), ['DELETE', '/api/soirees/s/manches/c', undefined]);
	b.appels[1].resoudre({ statut: 204, corps: null });
	await terminerMicrotaches();
	b.afficher([a], 'EN_COURS');
	assert.equal(b.formulaire.hidden, true);
	assert.equal(b.programmeFige.hidden, false);
	assert.equal(b.liste.querySelectorAll('button').length, 0);
});

test('les trois mutations de la Console passent par le transport de la session et son CSRF', async () => {
	const requetes = [];
	const etats = [];
	const reponses = [
		{ status: 201, texte: '{"id":"b"}' },
		{ status: 200, texte: '{"vue":{"programme":[]}}' },
		{ status: 204, texte: '' },
	];
	const transport = creerTransport({ csrf: 'csrf-session' }, {
		adresse: 'https://exemple.test/console.html?soiree=s',
		requete: async (url, options) => {
			requetes.push({
				chemin: url.pathname, methode: options.method, credentials: options.credentials,
				csrf: options.headers['X-CSRF-TOKEN'], autorisation: options.headers.Authorization,
				corps: options.body === undefined ? undefined : JSON.parse(options.body),
			});
			const { status, texte } = reponses.shift();
			return { status, headers: new Headers({ 'Content-Type': 'application/json' }), text: async () => texte };
		},
	});
	const b = banc({ envoyer: transport.envoyer, surEnvoi: etat => etats.push(etat) });
	const quiz = manche('a', 0);
	const karaoke = manche('b', 1, 'KARAOKE');

	b.connecter('synchronisee');
	b.afficher([quiz]);
	b.mode.value = 'KARAOKE';
	b.formulaire.dispatchEvent({ type: 'submit' });
	await terminerMicrotaches();

	b.afficher([quiz, karaoke]);
	b.liste.querySelectorAll('[data-action-programme="monter"]')[1].click();
	await terminerMicrotaches();

	b.afficher([{ ...karaoke, ordre: 0 }, { ...quiz, ordre: 1 }]);
	b.liste.querySelector('[data-action-programme="retirer"]').click();
	await terminerMicrotaches();

	assert.deepEqual(requetes.map(({ methode, chemin, corps }) => [methode, chemin, corps]), [
		['POST', '/api/soirees/s/manches', { mode: 'KARAOKE', reglages: {} }],
		['PUT', '/api/soirees/s/ordre-des-manches', { mancheIds: ['b', 'a'] }],
		['DELETE', '/api/soirees/s/manches/b', undefined],
	]);
	for (const requete of requetes) {
		assert.equal(requete.credentials, 'same-origin');
		assert.equal(requete.csrf, 'csrf-session');
		assert.equal(requete.autorisation, undefined);
	}
	assert.deepEqual(etats, ['en-cours', 'accepte', 'en-cours', 'accepte', 'en-cours', 'accepte']);
	assert.equal(b.captures(), 3);
});

test('chaque Mode de jeu proposé par la Console remplit son emplacement de réglages', () => {
	const page = readFileSync(new URL('../../src/main/resources/static/console.html', import.meta.url), 'utf8');
	const selecteur = page.match(/<select id="mode-manche"[\s\S]*?<\/select>/)[0];
	const modes = [...selecteur.matchAll(/<option value="([A-Z_]+)"/g)].map(([, mode]) => mode);
	assert.deepEqual(modes, ['QUIZ', 'KARAOKE']);
	assert.deepEqual(Object.keys(REGLAGES_DES_MODES).sort(), [...modes].sort());
	for (const { zone } of Object.values(REGLAGES_DES_MODES)) {
		if (zone) assert.match(page, new RegExp(`<fieldset[^>]* data-${zone}[ >]`));
	}
	assert.equal(REGLAGES_DES_MODES.QUIZ.decrire({ nombreEtapes: 1, tempsParEtapeSecondes: 20 }), '1 étape · 20 s par étape');
	assert.equal(REGLAGES_DES_MODES.KARAOKE.decrire({ limitePassagesEnAttente: 2 }), 'Jusqu’à 2 Passages en attente');
	assert.equal(REGLAGES_DES_MODES.QUIZ.decrire({}), '');
});
