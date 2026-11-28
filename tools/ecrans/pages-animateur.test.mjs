// Pages de l'Animateur hors Console (#282, constats I12 et M6 de l'audit du 27/11) : accueil, compte, Soirées du Lieu
// et préparation. Les pages sont lues comme du texte : aucun navigateur dans node --test.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { decrirePreparation } from '../../src/main/resources/static/js/preparation-soiree.mjs';
import { monterMenuCompte } from '../../src/main/resources/static/js/menu-compte.mjs';
import { creerDocument, terminerMicrotaches } from './karaoke-dom-factice.mjs';

const lire = nom => readFile(new URL(`../../src/main/resources/static/${nom}`, import.meta.url), 'utf8');
const [accueil, connexion, inscription, soirees, preparation, compteCss] = await Promise.all(
	['index.html', 'connexion.html', 'inscription.html', 'soirees.html', 'preparer-soiree.html', 'css/compte.css'].map(lire));
const lien = (page, href) => page.match(new RegExp(`<a [^>]*href="${href.replace(/[?.]/g, '\\$&')}"[^>]*>[\\s\\S]*?</a>`))?.[0];

test('l’accueil suit la charte : feuille commune, mot-symbole, aucune ancienne couleur violette', () => {
	assert.match(accueil, /<html lang="fr">/);
	assert.match(accueil, /href="\/css\/croustyquizz\.css"/);
	assert.match(accueil, /class="marque"/);
	assert.doesNotMatch(accueil, /#14111f|#1f1a30|#ffb547/i);
});

test('l’accueil tutoie et ne vouvoie jamais', () => {
	const texte = accueil.replace(/<[^>]+>/g, ' ');
	assert.doesNotMatch(texte, /\b(vous|votre|vos|Répondez|Buzzez|Chantez)\b/);
});

test('l’accueil propose deux entrées : animer une Soirée et rejoindre une Soirée', () => {
	assert.match(lien(accueil, '/soirees.html') ?? '', /J’anime une Soirée/);
	assert.match(lien(accueil, '/rejoindre.html') ?? '', /Je rejoins une Soirée/);
	assert.ok(lien(accueil, '/inscription.html'), 'un Lieu sans compte doit pouvoir le créer depuis l’accueil');
});

test('les pages du compte ramènent à l’accueil par le mot-symbole', () => {
	for (const [nom, page] of [['accueil', accueil], ['connexion', connexion], ['inscription', inscription],
		['soirees', soirees], ['preparation', preparation]]) {
		if (nom === 'accueil') continue;
		assert.match(lien(page, '/') ?? '', /class="marque"/, nom);
	}
});

test('la connexion oriente un Joueur égaré vers rejoindre.html', () => {
	assert.match(lien(connexion, '/rejoindre.html') ?? '', /Rejoindre une Soirée/);
	assert.match(lien(inscription, '/rejoindre.html') ?? '', /Rejoindre une Soirée/);
});

test('les pages connectées réservent la place du menu du compte', () => {
	for (const page of [soirees, preparation]) assert.match(page, /<div [^>]*data-menu-compte[^>]*><\/div>/);
});

test('la préparation quitte la barre de la Console pour la coquille des pages du compte', () => {
	assert.doesNotMatch(preparation, /console\.css|class="pilotage/);
	assert.match(preparation, /href="\/css\/compte\.css"/);
	assert.match(preparation, /<script type="module" src="\/js\/preparation-soiree\.mjs"><\/script>/);
});

test('Format et Pilotage se choisissent sur des cartes expliquées en une ligne', () => {
	const cartes = [...preparation.matchAll(/<label class="choix"[^>]*>([\s\S]*?)<\/label>/g)].map(([, corps]) => corps);
	const valeurs = cartes.map(corps => corps.match(/name="(\w+)" value="(\w+)"/)?.slice(1).join('='));
	assert.deepEqual(valeurs, ['format=SOLO', 'format=EQUIPE', 'pilotage=MANUEL', 'pilotage=AUTOMATIQUE']);
	for (const corps of cartes) {
		assert.match(corps, /<span class="choix__aide">[^<]{10,}<\/span>/, 'chaque choix a sa ligne d’aide');
	}
	assert.match(cartes[2], /Tu lances chaque Manche toi-même/);
	assert.match(compteCss, /\.choix\s*{[^}]*min-height:\s*44px/);
});

test('le titre de la Soirée garde son identifiant et dit où il s’affiche', () => {
	assert.match(preparation, /<input id="titre"[^>]*aria-describedby="titre-aide"/);
	assert.match(preparation, /id="titre-aide" class="champ__aide">[^<]*Écran de salle/);
});

const soiree = (etat, codeAcces = etat === 'PREPARATION' ? null : 'K7M2QX') => ({
	id: 's', titre: 'Quiz du jeudi', format: 'SOLO', pilotage: 'MANUEL', etat, codeAcces,
});

test('en préparation, la seule action est d’ouvrir la Soirée aux Joueurs', () => {
	const vue = decrirePreparation(soiree('PREPARATION'));
	assert.equal(vue.etat, 'En préparation');
	assert.equal(vue.resume, 'Solo · Pilotage manuel');
	assert.equal(vue.code, null);
	assert.equal(vue.action, 'ouvrir');
	assert.deepEqual({ ouvrir: vue.ouvrir, ecran: vue.ecran }, { ouvrir: true, ecran: true });
	assert.match(vue.consigneCode, /Ouvre la Soirée/);
	assert.equal(vue.libelleConsole, 'Ouvrir la Console d’animation');
});

test('ouverte, le code s’affiche et l’action suivante est la Console', () => {
	const vue = decrirePreparation(soiree('OUVERTE'));
	assert.equal(vue.etat, 'Ouverte');
	assert.equal(vue.code, 'K7M2QX');
	assert.equal(vue.ouvrir, false);
	assert.equal(vue.ecran, true);
	assert.equal(vue.action, 'console');
	assert.match(vue.consigneCode, /QR code/);
});

test('en cours, la Console reste l’action ; terminée, plus d’action ni de code', () => {
	assert.equal(decrirePreparation(soiree('EN_COURS')).action, 'console');
	const finie = decrirePreparation(soiree('TERMINEE'));
	assert.equal(finie.action, null);
	assert.equal(finie.code, null);
	assert.equal(finie.ecran, false);
	assert.equal(finie.libelleConsole, 'Revoir dans la Console');
	assert.match(finie.consigneCode, /terminée/);
});

test('le pilotage automatique se résume en toutes lettres', () => {
	assert.equal(decrirePreparation({ ...soiree('PREPARATION'), format: 'EQUIPE', pilotage: 'AUTOMATIQUE' }).resume,
		'Équipe · Pilotage automatique');
});

function banc(options = {}) {
	const document = creerDocument();
	const conteneur = document.createElement('div');
	document.body.append(conteneur);
	const appels = [];
	const menu = monterMenuCompte(conteneur, {
		nom: 'Camille', page: '/soirees.html',
		deconnecter: async () => { appels.push('DELETE'); return options.sortie ?? { ok: true, statut: 204 }; },
		apresDeconnexion: () => appels.push('connexion'),
		...options,
	});
	const bouton = conteneur.querySelector('[data-menu-compte-bouton]');
	const panneau = conteneur.querySelector('[data-menu-compte-panneau]');
	return { document, conteneur, menu, bouton, panneau, appels };
}

test('le menu du compte se nomme d’après l’Animateur et reste fermé au départ', () => {
	const { bouton, panneau } = banc();
	assert.equal(bouton.tagName, 'BUTTON');
	assert.equal(bouton.getAttribute('aria-expanded'), 'false');
	assert.equal(bouton.getAttribute('aria-controls'), panneau.id);
	assert.equal(bouton.getAttribute('aria-label'), 'Compte de Camille');
	assert.match(bouton.textContent, /Camille/);
	assert.equal(panneau.hidden, true);
});

test('le menu du compte mène aux Soirées du Lieu, à la préparation et à la déconnexion', () => {
	const { panneau } = banc();
	const liens = panneau.querySelectorAll('a');
	assert.deepEqual(liens.map(a => [a.getAttribute('href'), a.textContent]),
		[['/soirees.html', 'Soirées du Lieu'], ['/preparer-soiree.html', 'Préparer une Soirée']]);
	assert.equal(liens[0].getAttribute('aria-current'), 'page');
	assert.equal(liens[1].getAttribute('aria-current'), null);
	assert.equal(panneau.querySelector('[data-deconnexion]').textContent, 'Se déconnecter');
});

test('le menu s’ouvre et se ferme au bouton', () => {
	const { bouton, panneau } = banc();
	bouton.click();
	assert.equal(bouton.getAttribute('aria-expanded'), 'true');
	assert.equal(panneau.hidden, false);
	bouton.click();
	assert.equal(bouton.getAttribute('aria-expanded'), 'false');
	assert.equal(panneau.hidden, true);
});

test('Échap ferme le menu et rend le focus à son bouton', () => {
	const { document, bouton, panneau } = banc();
	bouton.click();
	panneau.querySelector('a').focus();
	document.dispatchEvent({ type: 'keydown', key: 'Escape' });
	assert.equal(panneau.hidden, true);
	assert.equal(bouton.getAttribute('aria-expanded'), 'false');
	assert.equal(document.activeElement, bouton);
});

test('Échap sans menu ouvert ne vole pas le focus', () => {
	const { document, conteneur } = banc();
	const ailleurs = document.createElement('input');
	document.body.append(ailleurs);
	ailleurs.focus();
	document.dispatchEvent({ type: 'keydown', key: 'Escape' });
	assert.equal(document.activeElement, ailleurs);
	assert.ok(conteneur);
});

test('un clic hors du menu le ferme sans déplacer le focus', () => {
	const { document, bouton, panneau, conteneur } = banc();
	bouton.click();
	document.dispatchEvent({ type: 'click', target: panneau.querySelector('a') });
	assert.equal(panneau.hidden, false, 'un clic dans le menu le laisse ouvert');
	const ailleurs = document.createElement('p');
	document.body.append(ailleurs);
	document.dispatchEvent({ type: 'click', target: ailleurs });
	assert.equal(panneau.hidden, true);
	assert.ok(conteneur.contains(bouton));
});

test('le focus qui quitte le menu au clavier le referme', () => {
	const { document, bouton, panneau, conteneur } = banc();
	bouton.click();
	const ailleurs = document.createElement('a');
	document.body.append(ailleurs);
	conteneur.dispatchEvent({ type: 'focusout', relatedTarget: panneau.querySelector('a') });
	assert.equal(panneau.hidden, false);
	conteneur.dispatchEvent({ type: 'focusout', relatedTarget: ailleurs });
	assert.equal(panneau.hidden, true);
});

test('se déconnecter ferme la session puis renvoie à la connexion', async () => {
	const { bouton, panneau, appels } = banc();
	bouton.click();
	panneau.querySelector('[data-deconnexion]').click();
	await terminerMicrotaches();
	assert.deepEqual(appels, ['DELETE', 'connexion']);
});

test('une session déjà expirée renvoie aussi à la connexion', async () => {
	const { panneau, appels } = banc({ sortie: { ok: false, statut: 401 } });
	panneau.querySelector('[data-deconnexion]').click();
	await terminerMicrotaches();
	assert.deepEqual(appels, ['DELETE', 'connexion']);
});

test('un échec de déconnexion s’explique dans le menu et laisse réessayer', async () => {
	const { panneau, appels } = banc({ sortie: { ok: false, statut: 503 } });
	const sortie = panneau.querySelector('[data-deconnexion]');
	sortie.click();
	await terminerMicrotaches();
	assert.deepEqual(appels, ['DELETE']);
	const statut = panneau.querySelector('[data-menu-compte-statut]');
	assert.equal(statut.hidden, false);
	assert.match(statut.textContent, /Réessaie/);
	assert.equal(sortie.disabled, false);
});

test('démonter le menu retire ses écouteurs du document', () => {
	const { document, menu, bouton, panneau } = banc();
	bouton.click();
	menu.demonter();
	const ailleurs = document.createElement('p');
	document.dispatchEvent({ type: 'click', target: ailleurs });
	document.dispatchEvent({ type: 'keydown', key: 'Escape' });
	assert.equal(panneau.hidden, false);
});
