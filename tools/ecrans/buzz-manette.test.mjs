import test from 'node:test';
import assert from 'node:assert/strict';
import { setImmediate } from 'node:timers/promises';
import { monterBuzz } from '../../src/main/resources/static/js/buzz-manette.mjs';
import { creerContexte } from '../../src/main/resources/static/js/contexte.mjs';

const ouverte = { soiree: { etat: 'OUVERTE' }, modeEnCours: null };

function monter() {
	const bouton = new EventTarget();
	const envois = [];
	const annonces = [];
	const { contexte, changerEtat } = creerContexte({ soireeId: 's1', resynchroniser() {}, surEnvoi: (...a) => annonces.push(a),
		envoyer: (methode, route, corps, { signal }) => new Promise((resoudre, rejeter) => {
			envois.push({ methode, route, corps, signal, resoudre, rejeter });
		}),
	});
	const buzz = monterBuzz(bouton, contexte);
	return { bouton, buzz, envois, annonces, changerEtat, cliquer: () => bouton.dispatchEvent(new Event('click')) };
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

test('un Buzz accepté s’annonce comme tel, sans jargon (#279)', async () => {
	const { buzz, changerEtat, cliquer, envois, annonces } = monter();
	buzz.afficher(ouverte);
	changerEtat('synchronisee');
	cliquer();
	envois[0].resoudre({ statut: 200, corps: {} });
	await setImmediate();
	assert.deepEqual(annonces.at(-1), ['accepte', 'Buzz envoyé.']);
	buzz.demonter();
});
