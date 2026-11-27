import assert from 'node:assert/strict';
import test from 'node:test';
import { creerContexte } from '../../src/main/resources/static/js/contexte.mjs';
import { monterPilotage } from '../../src/main/resources/static/js/pilotage-console.mjs';
import { creerDocument, terminerMicrotaches } from './karaoke-dom-factice.mjs';

function banc() {
	const document = creerDocument();
	const barre = document.createElement('div');
	document.body.append(barre);
	const creer = (parent, balise, attribut, valeur = '') => {
		const element = document.createElement(balise);
		element.setAttribute(attribut, valeur);
		parent.append(element);
		return element;
	};
	const lancer = creer(barre, 'button', 'data-piloter-soiree', 'LANCER');
	const suivante = creer(barre, 'button', 'data-piloter-soiree', 'MANCHE_SUIVANTE');
	const terminer = creer(barre, 'button', 'data-piloter-soiree', 'TERMINER');
	const dialogue = creer(barre, 'dialog', 'data-dialogue-fin');
	const annuler = creer(dialogue, 'button', 'data-annuler-fin');
	annuler.setAttribute('autofocus', '');
	const confirmer = creer(dialogue, 'button', 'data-confirmer-fin');
	const raison = document.createElement('p');
	raison.id = 'raison-lancer';
	document.body.append(raison);
	const appels = [];
	const annonces = [];
	let captures = 0;
	const { contexte, changerEtat } = creerContexte({
		soireeId: 's',
		envoyer: (...args) => new Promise((resoudre, rejeter) => appels.push({ args, resoudre, rejeter })),
		surEnvoi: (etat, detail) => annonces.push({ etat, detail }),
		resynchroniser: () => { captures++; changerEtat('synchronisation'); },
	});
	const pilotage = monterPilotage(barre, contexte, { raison });
	return {
		document, lancer, suivante, terminer, dialogue, annuler, confirmer, raison, appels, annonces, pilotage, changerEtat,
		captures: () => captures,
		afficher: (etat, mancheActive = null, programme = [{ id: 'm', etat: 'EN_ATTENTE' }]) =>
			pilotage.afficher({ soiree: { etat }, mancheActive, programme }),
	};
}

test('envoie le lancement seulement avec une vue synchronisée et attend le reçu', async () => {
	const b = banc();
	b.afficher('OUVERTE');
	b.lancer.click();
	assert.equal(b.appels.length, 0);
	b.changerEtat('synchronisee');
	b.lancer.click();
	b.lancer.click();
	assert.equal(b.appels.length, 1);
	const { args, resoudre } = b.appels[0];
	assert.deepEqual(args.slice(0, 3), ['POST', '/api/soirees/s/commandes', { action: 'LANCER' }]);
	assert.match(args[3].entetes['Idempotency-Key'], /^[0-9a-f-]{36}$/i);
	assert.equal(b.annonces.at(-1).etat, 'en-cours');
	resoudre({ statut: 200, corps: { envoiId: args[3].entetes['Idempotency-Key'] } });
	await terminerMicrotaches();
	// Le lancement se voit sur les trois écrans : le bandeau s'efface au lieu d'un reçu de serveur (#279).
	assert.equal(b.annonces.at(-1).etat, null);
	assert.equal(b.captures(), 1);
	assert.equal(b.lancer.getAttribute('aria-disabled'), 'true');
});

test('la prochaine Manche attend la fin de la précédente et un réessai garde sa clé', async () => {
	const b = banc();
	b.changerEtat('synchronisee');
	b.afficher('EN_COURS', { id: 'active', etat: 'EN_PAUSE' });
	b.suivante.click();
	assert.equal(b.appels.length, 0);
	b.afficher('EN_COURS');
	b.suivante.click();
	const premier = b.appels[0];
	assert.deepEqual(premier.args[2], { action: 'MANCHE_SUIVANTE' });
	premier.rejeter(new TypeError('Erreur réseau'));
	await terminerMicrotaches();
	assert.equal(b.annonces.at(-1).etat, 'inconnu');
	assert.equal(b.appels.length, 1);
	b.changerEtat('synchronisee');
	b.suivante.click();
	const seconde = b.appels[1];
	assert.equal(seconde.args[3].entetes['Idempotency-Key'], premier.args[3].entetes['Idempotency-Key']);
	b.pilotage.demonter();
	assert.equal(seconde.args[3].signal.aborted, true);
	seconde.rejeter(new DOMException('Annulation', 'AbortError'));
	await terminerMicrotaches();
	assert.equal(b.captures(), 1);
});

test('terminer demande une confirmation et rend le focus après annulation', async () => {
	const b = banc();
	b.afficher('EN_COURS');
	b.changerEtat('synchronisee');
	b.terminer.click();
	assert.equal(b.dialogue.open, true);
	assert.ok(b.document.activeElement === b.annuler);
	assert.equal(b.appels.length, 0);
	b.annuler.click();
	assert.equal(b.dialogue.open, false);
	assert.ok(b.document.activeElement === b.terminer);

	b.terminer.click();
	b.changerEtat('coupure');
	assert.equal(b.dialogue.open, false);
	b.confirmer.click();
	assert.equal(b.appels.length, 0);

	b.changerEtat('synchronisee');
	b.terminer.click();
	b.confirmer.click();
	assert.deepEqual(b.appels[0].args[2], { action: 'TERMINER' });
	assert.equal(b.dialogue.open, false);
	b.appels[0].resoudre({ statut: 409, corps: { detail: 'La Soirée est déjà terminée.' } });
	await terminerMicrotaches();
	assert.deepEqual(b.annonces.at(-1), { etat: 'refuse', detail: 'La Soirée est déjà terminée.' });
});

test('ne montre que les commandes de l’étape de la Soirée', () => {
	const b = banc();
	b.changerEtat('synchronisee');
	b.afficher('OUVERTE');
	assert.deepEqual([b.lancer.hidden, b.suivante.hidden, b.terminer.hidden], [false, true, true]);
	b.afficher('EN_COURS', { id: 'active', etat: 'EN_COURS' });
	assert.deepEqual([b.lancer.hidden, b.suivante.hidden, b.terminer.hidden], [true, false, false]);
	b.afficher('TERMINEE', null, [{ id: 'm', etat: 'TERMINEE' }]);
	assert.deepEqual([b.lancer.hidden, b.suivante.hidden, b.terminer.hidden], [true, true, true]);
});

test('un seul citron : « Lancer la Soirée » ne l’est que s’il peut partir, et dit sinon pourquoi (#282)', () => {
	const b = banc();
	b.changerEtat('synchronisee');
	b.afficher('OUVERTE', null, []);
	assert.equal(b.lancer.getAttribute('aria-disabled'), 'true');
	assert.doesNotMatch(b.lancer.className, /bouton--action/);
	assert.equal(b.raison.textContent, 'Ajoute une Manche au programme pour lancer la Soirée.');
	assert.equal(b.raison.hidden, false);
	assert.equal(b.lancer.getAttribute('aria-describedby'), 'raison-lancer');
	b.afficher('PREPARATION', null, [{ id: 'm', etat: 'EN_ATTENTE' }]);
	assert.equal(b.raison.textContent, 'Ouvre la Soirée aux Joueurs depuis la préparation pour la lancer.');
	b.afficher('OUVERTE');
	assert.equal(b.lancer.getAttribute('aria-disabled'), 'false');
	assert.match(b.lancer.className, /bouton--action/);
	assert.equal(b.raison.hidden, true);
	assert.equal(b.lancer.getAttribute('aria-describedby'), null);
	b.afficher('EN_COURS', { id: 'm', etat: 'EN_COURS' }, [{ id: 'm', etat: 'EN_COURS' }, { id: 'n', etat: 'EN_ATTENTE' }]);
	assert.doesNotMatch(b.suivante.className, /bouton--action/, 'Manche suivante n’est pas l’action tant qu’une Manche tourne');
	b.afficher('EN_COURS', null, [{ id: 'm', etat: 'TERMINEE' }, { id: 'n', etat: 'EN_ATTENTE' }]);
	assert.match(b.suivante.className, /bouton--action/);
	b.pilotage.demonter();
});
