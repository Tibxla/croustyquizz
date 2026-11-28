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
	const blindTest = creer('reglages-blindtest', 'fieldset', formulaire);
	const source = creer('source-blindtest', 'select', blindTest);
	const idDeezer = creer('id-deezer', 'input', blindTest);
	const nombreMorceaux = creer('nombre-morceaux', 'input', blindTest);
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
	const erreurAjout = creer('ajout-manche-erreur', 'p', formulaire);
	erreurAjout.id = 'ajout-manche-erreur';
	erreurAjout.hidden = true;
	const ajout = creer('soumettre-manche', 'button', formulaire);
	ajout.setAttribute('aria-describedby', erreurAjout.id);
	const ailleurs = creer('ailleurs', 'button', document.body);
	const liste = creer('programme', 'ol');
	const programmeFige = creer('programme-fige', 'p');
	creer('programme-vide', 'p');
	const appels = [];
	const annonces = [];
	let captures = 0;
	const contexteCree = creerContexte({
		soireeId: 's',
		envoyer: (...args) => new Promise((resoudre, rejeter) => appels.push({ args, resoudre, rejeter })),
		resynchroniser: () => captures++,
		surEnvoi: etat => annonces.push(etat),
	});
	const { contexte, changerEtat } = contexteCree;
	const programme = monterProgramme(panneau, contexte);
	return {
		ailleurs, ajout, annonces, appels, communs, contexte, document, erreurAjout, erreurs, formulaire, liste, mode, nombre,
		programmeFige, temps, programme, blindTest, source, idDeezer, nombreMorceaux,
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

test('affiche un refus HTTP 400 de l’ajout sous le formulaire, sans marquer de champ', async () => {
	const b = banc();
	b.afficher([]);
	b.connecter('synchronisee');
	b.mode.value = 'QUIZ';
	b.mode.dispatchEvent({ type: 'change' });
	b.nombre.value = '5';
	b.temps.value = '25';
	const detail = 'Le Mode de jeu ou les réglages de la Manche sont invalides.';

	b.ajout.focus();
	b.formulaire.dispatchEvent({ type: 'submit' });
	b.appels[0].resoudre({ statut: 400, corps: { code: 'REQUETE_INVALIDE', detail } });
	await terminerMicrotaches();
	assert.equal(b.erreurAjout.hidden, false);
	assert.equal(b.erreurAjout.textContent, detail);
	assert.equal(b.ajout.getAttribute('aria-describedby'), b.erreurAjout.id);
	for (const [nom, champ] of [['mode-manche', b.mode], ['nombre-etapes', b.nombre], ['temps-par-etape', b.temps]]) {
		assert.equal(b.erreurs[nom].hidden, true, nom);
		assert.equal(champ.getAttribute('aria-invalid'), null, nom);
	}
	assert.equal(b.document.activeElement === b.ajout, true, 'le focus reste sur le bouton d’ajout');
	assert.deepEqual(b.annonces, ['en-cours', null]);
	assert.equal(b.captures(), 0);

	b.mode.value = 'KARAOKE';
	b.mode.dispatchEvent({ type: 'change' });
	assert.equal(b.erreurAjout.hidden, true);
	assert.equal(b.erreurAjout.textContent, '');
	b.formulaire.dispatchEvent({ type: 'submit' });
	b.appels[1].resoudre({ statut: 400, corps: { code: 'REQUETE_INVALIDE' } });
	await terminerMicrotaches();
	assert.equal(b.erreurAjout.hidden, false);
	assert.equal(b.erreurAjout.textContent, 'Vérifie le Mode de jeu et les réglages de la Manche.');
	assert.equal(b.mode.getAttribute('aria-invalid'), null);
});

test('un refus HTTP 400 tardif ne ramène pas le focus dans le formulaire', async () => {
	const b = banc();
	b.afficher([]);
	b.connecter('synchronisee');
	b.mode.value = 'KARAOKE';
	b.mode.dispatchEvent({ type: 'change' });
	b.ajout.focus();
	b.formulaire.dispatchEvent({ type: 'submit' });
	b.ailleurs.focus();
	b.appels[0].resoudre({ statut: 400, corps: { code: 'REQUETE_INVALIDE', detail: 'Refusé.' } });
	await terminerMicrotaches();
	assert.equal(b.erreurAjout.textContent, 'Refusé.');
	assert.equal(b.document.activeElement === b.ailleurs, true);
});

test('une saisie refusée avant l’envoi efface le bandeau laissé par l’envoi précédent', async () => {
	const b = banc();
	const a = manche('a', 0);
	const c = manche('c', 1);
	b.afficher([a, c]);
	b.connecter('synchronisee');
	b.liste.querySelectorAll('[data-action-programme="monter"]')[1].click();
	b.appels[0].resoudre({ statut: 409, corps: { code: 'ETAT_INCOMPATIBLE', detail: 'Envoi refusé.' } });
	await terminerMicrotaches();
	assert.deepEqual(b.annonces, ['en-cours', 'refuse']);

	b.mode.value = 'QUIZ';
	b.mode.dispatchEvent({ type: 'change' });
	b.nombre.value = '0';
	b.formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.appels.length, 1);
	assert.equal(b.erreurs['nombre-etapes'].hidden, false);
	assert.deepEqual(b.annonces, ['en-cours', 'refuse', null]);
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

test('le Blind test envoie sa source Deezer et son nombre de Morceaux, puis les décrit dans le programme', async () => {
	const b = banc();
	b.afficher([]);
	b.connecter('synchronisee');
	b.mode.value = 'BLIND_TEST';
	b.mode.dispatchEvent({ type: 'change' });
	assert.equal(b.communs.hidden, true);
	assert.equal(b.blindTest.hidden, false);
	assert.equal(b.blindTest.disabled, false);
	b.source.value = 'GENRE';
	b.idDeezer.value = '132';
	b.nombreMorceaux.value = '12';
	b.formulaire.dispatchEvent({ type: 'submit' });
	assert.deepEqual(b.appels[0].args.slice(0, 3), ['POST', '/api/soirees/s/manches',
		{ mode: 'BLIND_TEST', reglages: { source: { type: 'GENRE', idDeezer: 132 }, nombreMorceaux: 12 } }]);
	b.appels[0].resoudre({ statut: 201 });
	await terminerMicrotaches();
	b.afficher([{ ...manche('b', 0, 'BLIND_TEST'), reglages: { source: { type: 'GENRE', idDeezer: 132 }, nombreMorceaux: 12 } }]);
	assert.match(b.liste.textContent, /12 Morceaux · genre Deezer 132/);

	b.mode.value = 'QUIZ';
	b.mode.dispatchEvent({ type: 'change' });
	assert.equal(b.blindTest.hidden, true);
	assert.equal(b.blindTest.disabled, true, 'Les champs cachés ne bloquent pas la validation du formulaire.');
	b.programme.demonter();
});

test('une source Deezer refusée en 422 s’affiche sous le formulaire, pas dans le bandeau', async () => {
	const b = banc();
	b.afficher([]);
	b.connecter('synchronisee');
	b.mode.value = 'BLIND_TEST';
	b.mode.dispatchEvent({ type: 'change' });
	b.source.value = 'PLAYLIST';
	b.idDeezer.value = '0';
	b.nombreMorceaux.value = '10';
	const detail = 'Deezer ne connaît aucune playlist 0 : vérifie l’identifiant et le type (playlist ou genre).';

	b.formulaire.dispatchEvent({ type: 'submit' });
	b.appels[0].resoudre({ statut: 422, corps: { code: 'SOURCE_MUSICALE_INVALIDE', detail } });
	await terminerMicrotaches();
	assert.equal(b.erreurAjout.hidden, false);
	assert.equal(b.erreurAjout.textContent, detail);
	assert.deepEqual(b.annonces, ['en-cours', null]);
	assert.equal(b.captures(), 0);

	b.idDeezer.value = '1306931615';
	b.formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(b.erreurAjout.hidden, true, 'un nouvel envoi efface le refus précédent');
	b.appels[1].resoudre({ statut: 503, corps: { code: 'SOURCE_MUSICALE_INDISPONIBLE', detail: 'Le catalogue musical ne répond pas.' } });
	await terminerMicrotaches();
	assert.equal(b.erreurAjout.hidden, true, 'une panne reste annoncée par la coquille');
	assert.deepEqual(b.annonces, ['en-cours', null, 'en-cours', 'inconnu']);
	b.programme.demonter();
});

test('un refus tardif d’une saisie déjà modifiée passe par le bandeau, pas sous le formulaire', async () => {
	const b = banc();
	b.afficher([]);
	b.connecter('synchronisee');
	b.mode.value = 'BLIND_TEST';
	b.mode.dispatchEvent({ type: 'change' });
	b.source.value = 'PLAYLIST';
	b.idDeezer.value = '0';
	b.nombreMorceaux.value = '10';
	b.formulaire.dispatchEvent({ type: 'submit' });

	b.idDeezer.value = '1306931615';
	const detail = 'Deezer ne connaît aucune playlist 0 : vérifie l’identifiant et le type (playlist ou genre).';
	b.appels[0].resoudre({ statut: 422, corps: { code: 'SOURCE_MUSICALE_INVALIDE', detail } });
	await terminerMicrotaches();
	assert.equal(b.erreurAjout.hidden, true, 'Le formulaire ne montre plus la source refusée.');
	assert.deepEqual(b.annonces, ['en-cours', 'refuse'], 'La coquille annonce le refus avec son détail.');
	b.programme.demonter();
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
