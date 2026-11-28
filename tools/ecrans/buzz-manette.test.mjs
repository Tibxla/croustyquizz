import test from 'node:test';
import assert from 'node:assert/strict';
import { setImmediate } from 'node:timers/promises';
import { monterBuzz } from '../../src/main/resources/static/js/buzz-manette.mjs';
import { creerContexte } from '../../src/main/resources/static/js/contexte.mjs';

const ouverte = { soiree: { etat: 'OUVERTE' }, modeEnCours: null };

function monter() {
	const bouton = new EventTarget();
	const envois = [];
	const { contexte, changerEtat } = creerContexte({ soireeId: 's1', resynchroniser() {},
		envoyer: (methode, route, corps, { signal }) => new Promise((resoudre, rejeter) => {
			envois.push({ methode, route, corps, signal, resoudre, rejeter });
		}),
	});
	const buzz = monterBuzz(bouton, contexte);
	return { bouton, buzz, envois, changerEtat, cliquer: () => bouton.dispatchEvent(new Event('click')) };
}

test('le Buzz apparaît hors Manche et attend une vue synchronisée', () => {
	const { bouton, buzz, changerEtat, cliquer, envois } = monter();
	assert.equal(bouton.hidden, true);
	buzz.afficher(ouverte);
	assert.equal(bouton.hidden, false);
	assert.equal(bouton.disabled, true);
	cliquer();
	assert.equal(envois.length, 0);
	changerEtat('synchronisee');
	assert.equal(bouton.disabled, false);
	buzz.afficher({ ...ouverte, modeEnCours: 'QUIZ' });
	assert.equal(bouton.hidden, true);
	buzz.afficher({ ...ouverte, soiree: { etat: 'TERMINEE' } });
	assert.equal(bouton.hidden, true);
	buzz.demonter();
});

test('la Manette réessaie sur demande avec le même envoi après une confirmation perdue', async () => {
	const { bouton, buzz, changerEtat, cliquer, envois } = monter();
	buzz.afficher(ouverte);
	changerEtat('synchronisee');
	cliquer();
	cliquer();
	assert.equal(envois.length, 1);
	assert.equal(bouton.disabled, true);
	assert.equal(envois[0].methode, 'POST');
	assert.equal(envois[0].route, '/api/soirees/s1/buzz');
	const id = envois[0].corps.envoiId;
	assert.match(id, /^[0-9a-f-]{36}$/);
	envois[0].rejeter(new Error('Connexion perdue'));
	await setImmediate();
	assert.equal(envois.length, 1);
	assert.equal(bouton.textContent, 'Réessayer le Buzz');
	cliquer();
	assert.equal(envois[1].corps.envoiId, id);
	envois[1].resoudre({ statut: 200, corps: { envoiId: id } });
	await setImmediate();
	assert.equal(bouton.textContent, 'Buzzer');
	cliquer();
	assert.notEqual(envois[2].corps.envoiId, id);
	buzz.demonter();
	assert.equal(envois[2].signal.aborted, true);
});

test('un 429 de la limite de requêtes affiche son detail comme tout refus, sans réessai automatique', async () => {
	const bouton = new EventTarget();
	const envois = [];
	const bandeau = [];
	const { contexte, changerEtat } = creerContexte({ soireeId: 's1', resynchroniser() { throw new Error('Aucune recapture attendue.'); },
		envoyer: (methode, route, corps) => new Promise(resoudre => { envois.push({ corps, resoudre }); }),
		surEnvoi: (etat, detail) => bandeau.push({ etat, detail }),
	});
	const buzz = monterBuzz(bouton, contexte);
	buzz.afficher(ouverte);
	changerEtat('synchronisee');
	bouton.dispatchEvent(new Event('click'));
	const detail = 'Trop de requêtes en peu de temps. Réessaie dans 1 seconde.';
	envois[0].resoudre({ statut: 429, corps: { status: 429, code: 'TROP_DE_REQUETES', detail } });
	await setImmediate();
	await setImmediate();

	assert.deepEqual(bandeau.at(-1), { etat: 'refuse', detail });
	assert.equal(envois.length, 1);
	assert.equal(bouton.disabled, false);
	assert.equal(bouton.textContent, 'Buzzer');
	bouton.dispatchEvent(new Event('click'));
	assert.notEqual(envois[1].corps.envoiId, envois[0].corps.envoiId);
	buzz.demonter();
});

test('un téléphone en http:// buzze sans crypto.randomUUID, réservé aux contextes sécurisés', t => {
	Object.defineProperty(globalThis.crypto, 'randomUUID', { value: undefined, configurable: true });
	t.after(() => { delete globalThis.crypto.randomUUID; });
	const { buzz, changerEtat, cliquer, envois } = monter();
	buzz.afficher(ouverte);
	changerEtat('synchronisee');
	cliquer();
	assert.equal(envois.length, 1);
	assert.match(envois[0].corps.envoiId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
	buzz.demonter();
});
