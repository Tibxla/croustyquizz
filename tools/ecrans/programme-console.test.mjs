import assert from 'node:assert/strict';
import test from 'node:test';
import { creerContexte } from '../../src/main/resources/static/js/contexte.mjs';
import { monterProgramme } from '../../src/main/resources/static/js/programme-console.mjs';
import { creerDocument, terminerMicrotaches } from './karaoke-dom-factice.mjs';

function banc() {
	const document = creerDocument();
	const panneau = document.createElement('section');
	document.body.append(panneau);
	const creer = (nom, balise, parent = panneau) => {
		const element = document.createElement(balise);
		element.setAttribute('data-' + nom, '');
		parent.append(element);
		return element;
	};
	const formulaire = creer('ajout-manche', 'form');
	const mode = creer('mode-manche', 'select', formulaire);
	const communs = creer('reglages-communs', 'fieldset', formulaire);
	const nombre = creer('nombre-etapes', 'input', communs);
	const temps = creer('temps-par-etape', 'input', communs);
	const erreurs = {};
	for (const [nom, champ] of [['mode-manche', mode], ['nombre-etapes', nombre], ['temps-par-etape', temps]]) {
		const zone = creer(`${nom}-erreur`, 'p', champ.parentNode);
		zone.id = `${nom}-erreur`;
		zone.hidden = true;
		champ.setAttribute('aria-describedby', zone.id);
		erreurs[nom] = zone;
	}
	nombre.setAttribute('min', '1');
	nombre.setAttribute('max', '100');
	temps.setAttribute('min', '1');
	temps.setAttribute('max', '3600');
	const ajout = creer('soumettre-manche', 'button', formulaire);
	const liste = creer('programme', 'ol');
	const programmeFige = creer('programme-fige', 'p');
	creer('programme-vide', 'p');
	const appels = [];
	const annonces = [];
	let captures = 0;
	const { contexte, changerEtat } = creerContexte({
		soireeId: 's',
		envoyer: (...args) => new Promise((resoudre, rejeter) => appels.push({ args, resoudre, rejeter })),
		resynchroniser: () => captures++,
		surEnvoi: etat => annonces.push(etat),
	});
	const programme = monterProgramme(panneau, contexte);
	return {
		ajout, annonces, appels, communs, document, erreurs, formulaire, liste, mode, nombre, programmeFige, temps, programme,
		captures: () => captures, connecter: changerEtat,
		afficher: (manches, etat = 'PREPARATION') => programme.afficher({ soiree: { etat }, programme: manches }),
	};
}

const manche = (id, ordre, mode = 'QUIZ') => ({ id, ordre, mode, etat: 'EN_ATTENTE', reglages: {} });

test('guide la correction des réglages du Quiz avant tout envoi', () => {
	const b = banc();
	b.afficher([]);
	b.connecter('synchronisee');
	b.mode.value = 'QUIZ';
	b.mode.dispatchEvent({ type: 'change' });
	b.nombre.value = '0';
	b.temps.value = '2.5';

	b.formulaire.dispatchEvent({ type: 'submit' });
	const erreurNombre = b.erreurs['nombre-etapes'];
	assert.equal(b.appels.length, 0);
	assert.equal(erreurNombre.hidden, false);
	assert.equal(erreurNombre.textContent, 'Indique un nombre entier d’étapes entre 1 et 100.');
	assert.equal(b.nombre.getAttribute('aria-describedby'), erreurNombre.id);
	assert.equal(b.nombre.getAttribute('aria-invalid'), 'true');
	assert.equal(b.document.activeElement === b.nombre, true);

	b.nombre.value = '100';
	b.formulaire.dispatchEvent({ type: 'submit' });
	const erreurTemps = b.erreurs['temps-par-etape'];
	assert.equal(b.appels.length, 0);
	assert.equal(erreurNombre.hidden, true);
	assert.equal(b.nombre.getAttribute('aria-invalid'), null);
	assert.equal(erreurTemps.hidden, false);
	assert.equal(erreurTemps.textContent, 'Indique une durée entière entre 1 et 3600 secondes.');
	assert.equal(b.temps.getAttribute('aria-describedby'), erreurTemps.id);
	assert.equal(b.temps.getAttribute('aria-invalid'), 'true');
	assert.equal(b.document.activeElement === b.temps, true);

	b.temps.value = '3600';
	b.formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels.length, 1);
	assert.equal(erreurTemps.hidden, true);
	assert.equal(b.temps.getAttribute('aria-invalid'), null);
});

test('affiche un refus HTTP 400 de l’ajout sous un champ visible de la Manche', async () => {
	const b = banc();
	b.afficher([]);
	b.connecter('synchronisee');
	b.mode.value = 'QUIZ';
	b.mode.dispatchEvent({ type: 'change' });
	b.nombre.value = '5';
	b.temps.value = '25';
	const detail = 'Le Mode de jeu ou les réglages de la Manche sont invalides.';

	b.formulaire.dispatchEvent({ type: 'submit' });
	b.appels[0].resoudre({ statut: 400, corps: { code: 'REQUETE_INVALIDE', detail } });
	await terminerMicrotaches();
	const erreurNombre = b.erreurs['nombre-etapes'];
	assert.equal(erreurNombre.hidden, false);
	assert.equal(erreurNombre.textContent, detail);
	assert.equal(b.nombre.getAttribute('aria-invalid'), 'true');
	assert.equal(b.document.activeElement === b.nombre, true);
	assert.deepEqual(b.annonces, ['en-cours', null]);
	assert.equal(b.captures(), 0);

	b.mode.value = 'KARAOKE';
	b.mode.dispatchEvent({ type: 'change' });
	assert.equal(erreurNombre.hidden, true);
	assert.equal(b.nombre.getAttribute('aria-invalid'), null);
	b.formulaire.dispatchEvent({ type: 'submit' });
	b.appels[1].resoudre({ statut: 400, corps: { code: 'REQUETE_INVALIDE', detail } });
	await terminerMicrotaches();
	const erreurMode = b.erreurs['mode-manche'];
	assert.equal(erreurMode.hidden, false);
	assert.equal(erreurMode.textContent, detail);
	assert.equal(b.mode.getAttribute('aria-describedby'), erreurMode.id);
	assert.equal(b.mode.getAttribute('aria-invalid'), 'true');
	assert.equal(b.document.activeElement === b.mode, true);
});

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
