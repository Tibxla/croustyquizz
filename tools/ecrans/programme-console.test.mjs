import assert from 'node:assert/strict';
import test from 'node:test';
import { monterProgramme } from '../../src/main/resources/static/js/programme-console.mjs';
import { creerDocument, terminerMicrotaches } from './karaoke-dom-factice.mjs';

function banc(avecFreestyle = false) {
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
	const zone = avecFreestyle ? creer('reglages-freestyle', 'fieldset') : null;
	if (zone) {
		for (const [nom, balise, valeur] of [
			['instrumentales', 'div', ''], ['catalogue-etat', 'p', ''], ['catalogue-actualiser', 'button', ''],
			['theme-rimes', 'select', 'VILLE'], ['schema-rimes', 'select', 'ABAB'],
			['difficulte-rimes', 'select', 'PREMIER'], ['nombre-mesures', 'select', '16'],
		]) {
			const element = creer(nom, balise);
			element.value = valeur;
			zone.append(element);
		}
	}
	const appels = [];
	let rappel, captures = 0;
	const programme = monterProgramme(panneau, {
		soireeId: 's',
		envoyer: (...args) => new Promise((resoudre, rejeter) => appels.push({ args, resoudre, rejeter })),
		resynchroniser: () => captures++,
		surEtatConnexion: f => { rappel = f; f('synchronisation'); return () => { rappel = null; }; },
	});
	return {
		ajout, appels, communs, formulaire, liste, mode, nombre, programmeFige, temps, programme, zone,
		captures: () => captures, connecter: etat => rappel?.(etat),
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

test('le programme ajoute une Manche de Freestyle avec les fiches cochées après synchronisation', async () => {
	const b = banc(true);
	b.afficher([]);
	b.mode.value = 'FREESTYLE';
	b.mode.dispatchEvent({ type: 'change' });
	assert.equal(b.appels.length, 0);
	b.connecter('synchronisee');
	assert.equal(b.appels[0].args[1], '/api/soirees/s/freestyle/instrumentales');
	assert.equal(b.ajout.disabled, true);
	b.appels[0].resoudre({ statut: 200, corps: { instrumentales: [{ instrumentaleId: 'i', titre: 'À la lueur', tempoBpm: 120 }] } });
	await terminerMicrotaches();
	const choix = b.zone.querySelector('input');
	choix.checked = true;
	b.zone.querySelector('[data-instrumentales]').dispatchEvent({ type: 'change' });
	assert.equal(b.ajout.disabled, false);
	b.formulaire.dispatchEvent({ type: 'submit' });
	assert.deepEqual(b.appels[1].args.slice(0, 3), [
		'POST', '/api/soirees/s/manches', {
			mode: 'FREESTYLE',
			reglages: { theme: 'VILLE', schemaDeRimes: 'ABAB', difficulte: 'PREMIER', nombreMesures: 16, instrumentaleIds: ['i'] },
		},
	]);
	b.programme.demonter();
});
