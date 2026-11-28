import assert from 'node:assert/strict';
import test from 'node:test';

import { creerDocument, terminerMicrotaches } from './karaoke-dom-factice.mjs';
import { commandesFile, decrirePouvoirs, monterDelegation }
	from '../../src/main/resources/static/js/delegation-manette.mjs';

function banc() {
	const document = creerDocument();
	const refuge = document.createElement('h1');
	const zone = document.createElement('section');
	document.body.append(refuge, zone);
	const envois = [];
	const images = [];
	let connexion;
	const contexte = {
		soireeId: 's',
		envoyer: (...args) => new Promise(resolve => envois.push({ args, resolve })),
		chargerImage: (route, options) => new Promise(resolve => images.push({ route, options, resolve })),
		surEtatConnexion(rappel) {
			connexion = rappel;
			rappel('synchronisee');
			return () => { connexion = null; };
		},
	};
	let cle = 0;
	const module = monterDelegation(zone, contexte, { refuge, cle: () => `cle-${++cle}`,
		urlObjet: () => 'blob:photo', libererUrl: () => {} });
	return { document, refuge, zone, envois, images, module, connecter: etat => connexion?.(etat), abonne: () => Boolean(connexion) };
}

const chanson = titre => ({ chansonId: titre, titre, artiste: 'Groupe fictif', album: null, dureeMs: 180000, etatParoles: 'SYNCHRONISEES' });
const passage = (id, position, extra = {}) => ({ passageId: id, numeroModification: 0, numero: position, position, tour: 1,
	prioritaire: false, chanson: chanson(`Chanson ${id}`), interpretes: [{ joueurId: `j-${id}`, pseudo: `Joueur ${id}` }],
	attente: { etat: 'ESTIMEE', dureeMs: 0 }, ...extra });
const file = (extra = {}) => ({ mancheId: 'm', etat: 'EN_COURS', passagePrioritaireId: null, courant: null,
	passagesEnAttente: [passage('a', 1), passage('b', 2)], peutGererFile: true, peutAppeler: true, peutPasser: false, ...extra });
const vue = delegation => ({ joueurId: 'moi', delegation });

test('Délégation : la phrase nomme les pouvoirs reçus', () => {
	assert.equal(decrirePouvoirs(['FILE_ATTENTE']), 'L’Animateur t’a confié la File d’attente.');
	assert.equal(decrirePouvoirs(['FILE_ATTENTE', 'MUSIQUE_ATTENTE', 'PHOTOS']),
		'L’Animateur t’a confié la File d’attente, la Musique d’attente et les photos.');
	assert.equal(decrirePouvoirs([]), '');
});

test('Délégation : les commandes de la File suivent les droits de la vue, sans règle ajoutée', () => {
	const { appel, passage: passer, gestes } = commandesFile(file(), 's');
	assert.deepEqual(appel, { methode: 'POST', chemin: '/api/soirees/s/manches/m/passages/a/appel',
		corps: { numeroModification: 0 }, cible: 'appel:a' });
	assert.equal(passer, null);
	assert.deepEqual(gestes.get('a').map(g => g.libelle), ['Descendre', 'Priorité']);
	assert.deepEqual(gestes.get('b').map(g => g.libelle), ['Monter', 'Priorité']);
	const enScene = commandesFile(file({ peutAppeler: false, peutPasser: true, peutGererFile: false,
		courant: { ...passage('c', 1), etapeId: 'e', etat: 'APPELE' } }), 's');
	assert.equal(enScene.appel, null);
	assert.deepEqual(enScene.passage.corps, { action: 'PASSER_ETAPE', etapeId: 'e' });
	assert.equal(enScene.gestes.size, 0);
	assert.deepEqual(commandesFile(null, 's').gestes, new Map());
	const sansTete = commandesFile(file({ passagesEnAttente: [passage('b', 2), passage('c', 3)] }), 's');
	assert.equal(sansTete.appel, null);
});

test('Délégation : absente, la section reste cachée ; reçue, elle présente ses seuls pouvoirs', () => {
	const { zone, module } = banc();
	module.afficher(vue(null));
	assert.equal(zone.hidden, true);
	module.afficher(vue({ pouvoirs: ['FILE_ATTENTE'], fileAttente: file() }));
	assert.equal(zone.hidden, false);
	assert.match(zone.textContent, /Ta Délégation/);
	assert.match(zone.textContent, /la File d’attente\./);
	assert.match(zone.textContent, /Appeler le suivant/);
	assert.doesNotMatch(zone.textContent, /Musique|Photos à valider/);
	module.afficher(vue({ pouvoirs: ['FILE_ATTENTE'], fileAttente: null }));
	assert.match(zone.textContent, /Rien à gérer hors d’une Manche de Karaoké/);
});

test('Délégation : une commande part avec sa clé, sans succès anticipé, et une seule fois à la fois', async () => {
	const { zone, envois, module, connecter } = banc();
	module.afficher(vue({ pouvoirs: ['FILE_ATTENTE'], fileAttente: file() }));
	const appeler = zone.querySelector('[data-cible="appel:a"]');
	appeler.click();
	appeler.click();
	assert.equal(envois.length, 1);
	const [methode, chemin, corps, options] = envois[0].args;
	assert.equal(methode, 'POST');
	assert.equal(chemin, '/api/soirees/s/manches/m/passages/a/appel');
	assert.deepEqual(corps, { numeroModification: 0 });
	assert.equal(options.entetes['Idempotency-Key'], 'cle-1');
	assert.equal(appeler.getAttribute('aria-disabled'), 'true');
	envois[0].resolve({ statut: 409, corps: { code: 'COMMANDE_PERIMEE', detail: 'Ce Passage a changé.' } });
	await terminerMicrotaches();
	assert.equal(appeler.getAttribute('aria-disabled'), 'false');
	assert.equal(envois.length, 1, 'aucun nouvel essai automatique');
	connecter('coupure');
	assert.equal(appeler.getAttribute('aria-disabled'), 'true');
	appeler.click();
	assert.equal(envois.length, 1);
});

test('Délégation : Passer cite l’étape du courant, la musique et les photos visent leurs routes', async () => {
	const { zone, envois, images, module } = banc();
	module.afficher(vue({ pouvoirs: ['FILE_ATTENTE', 'MUSIQUE_ATTENTE', 'PHOTOS'],
		fileAttente: file({ peutAppeler: false, peutPasser: true, courant: { ...passage('c', 1), etapeId: 'e', etat: 'APPELE' } }),
		musiqueAttente: { etat: 'JOUE', morceauxDisponibles: true },
		photos: { enAttente: [{ photoId: 'p1', pseudo: 'Basile' }] } }));
	zone.querySelector('[data-cible="passer:e"]').click();
	assert.deepEqual(envois[0].args.slice(0, 3), ['POST', '/api/soirees/s/manches/m/commandes', { action: 'PASSER_ETAPE', etapeId: 'e' }]);
	zone.querySelector('[data-cible="musique"]').click();
	assert.deepEqual(envois[1].args.slice(0, 3), ['PUT', '/api/soirees/s/musique-attente', { etat: 'COUPEE' }]);
	assert.equal(images[0].route, '/api/soirees/s/photos/p1');
	const accepter = zone.querySelector('[data-cible="accepter:p1"]');
	assert.equal(accepter.getAttribute('aria-disabled'), 'true', 'pas de jugement avant de voir la photo');
	images[0].resolve(new Blob(['jpeg']));
	await terminerMicrotaches();
	zone.querySelector('img').dispatchEvent({ type: 'load' });
	assert.equal(accepter.getAttribute('aria-disabled'), 'false');
	accepter.click();
	assert.deepEqual(envois[2].args.slice(0, 3), ['POST', '/api/soirees/s/photos/p1/moderation', { acceptee: true }]);
});

test('Délégation : au retrait, la section disparaît et le focus va au refuge', () => {
	const { document, refuge, zone, envois, module } = banc();
	module.afficher(vue({ pouvoirs: ['FILE_ATTENTE'], fileAttente: file() }));
	const descendre = zone.querySelector('[data-cible="descendre:a"]');
	descendre.focus();
	descendre.click();
	const signal = envois[0].args[3].signal;
	// Une nouvelle vue garde le focus sur la même commande.
	module.afficher(vue({ pouvoirs: ['FILE_ATTENTE'], fileAttente: file() }));
	assert.equal(document.activeElement.getAttribute('data-cible'), 'descendre:a');
	module.afficher(vue(null));
	assert.equal(zone.hidden, true);
	assert.equal(document.activeElement, refuge);
	assert.equal(signal.aborted, true);
	assert.equal(zone.textContent, 'Ta Délégation');
});

test('Délégation : un pouvoir retiré rend le focus au titre de la section', () => {
	const { document, zone, module } = banc();
	module.afficher(vue({ pouvoirs: ['FILE_ATTENTE', 'MUSIQUE_ATTENTE'], fileAttente: file(),
		musiqueAttente: { etat: 'COUPEE', morceauxDisponibles: true } }));
	zone.querySelector('[data-cible="appel:a"]').focus();
	module.afficher(vue({ pouvoirs: ['MUSIQUE_ATTENTE'], musiqueAttente: { etat: 'COUPEE', morceauxDisponibles: true } }));
	assert.equal(document.activeElement, zone.querySelector('h2'));
	assert.match(zone.textContent, /Relancer la musique/);
});

test('Délégation : le démontage se désabonne et libère la zone', () => {
	const { zone, module, abonne } = banc();
	module.afficher(vue({ pouvoirs: ['MUSIQUE_ATTENTE'], musiqueAttente: { etat: 'JOUE', morceauxDisponibles: null } }));
	module.demonter();
	assert.equal(abonne(), false);
	assert.equal(zone.hidden, true);
	assert.equal(zone.children.length, 0);
});
