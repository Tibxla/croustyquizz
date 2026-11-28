// Menu du compte des pages de l'Animateur connecté (#282) : un bouton qui déplie un panneau (motif « disclosure »,
// sans role="menu" ni navigation aux flèches). Échap le ferme et rend le focus au bouton ; un clic ou un focus hors
// du menu le ferme sans déplacer le focus.
import { appelerAvecCsrf } from './api.js';
import { adresseConnexion, CAMPAGNES_SMS, CONTACTS, ROUTE_SESSION, SOIREES_DU_LIEU } from './compte.mjs';

const LIENS = [
	[SOIREES_DU_LIEU, 'Soirées du Lieu'], ['/preparer-soiree.html', 'Préparer une Soirée'], [CONTACTS, 'Contacts du Lieu'], [CAMPAGNES_SMS, 'Campagnes SMS'],
];
let compteur = 0;

function creer(document, balise, classe, texte) {
	const element = document.createElement(balise);
	if (classe) element.className = classe;
	if (texte !== undefined) element.textContent = texte;
	return element;
}

function icone(document, nom) {
	const element = creer(document, 'span', `icone icone--${nom}`);
	element.setAttribute('aria-hidden', 'true');
	return element;
}

/**
 * Monte le menu dans `conteneur`. `nom` vient de la session (GET /api/animateurs/session) déjà consultée par la page :
 * le menu n'appelle jamais la session lui-même. `page` est le chemin courant, marqué par aria-current.
 */
export function monterMenuCompte(conteneur, {
	nom,
	page = globalThis.location?.pathname,
	deconnecter = () => appelerAvecCsrf('DELETE', ROUTE_SESSION),
	apresDeconnexion = () => location.replace(adresseConnexion(location.pathname + location.search)),
} = {}) {
	const document = conteneur.ownerDocument;
	const identifiant = `menu-compte-${++compteur}`;
	conteneur.classList.add('menu-compte');

	const bouton = creer(document, 'button', 'bouton menu-compte__bouton');
	bouton.setAttribute('type', 'button');
	bouton.setAttribute('aria-expanded', 'false');
	bouton.setAttribute('aria-controls', identifiant);
	bouton.setAttribute('aria-label', `Compte de ${nom}`);
	bouton.setAttribute('data-menu-compte-bouton', '');
	bouton.append(icone(document, 'compte'), creer(document, 'span', 'menu-compte__nom', nom), icone(document, 'deplier'));

	const panneau = creer(document, 'div', 'menu-compte__panneau');
	panneau.setAttribute('id', identifiant);
	panneau.setAttribute('data-menu-compte-panneau', '');
	panneau.hidden = true;
	const qui = creer(document, 'p', 'menu-compte__qui', 'Compte de ');
	qui.append(creer(document, 'strong', '', nom));
	const liste = creer(document, 'ul', 'menu-compte__liens');
	for (const [href, libelle] of LIENS) {
		const li = creer(document, 'li');
		const lien = creer(document, 'a', 'menu-compte__lien', libelle);
		lien.setAttribute('href', href);
		if (href === page) lien.setAttribute('aria-current', 'page');
		li.append(lien);
		liste.append(li);
	}
	const sortie = creer(document, 'button', 'bouton menu-compte__sortie', 'Se déconnecter');
	sortie.setAttribute('type', 'button');
	sortie.setAttribute('data-deconnexion', '');
	const statut = creer(document, 'p', 'champ--erreur');
	statut.setAttribute('role', 'status');
	statut.setAttribute('data-menu-compte-statut', '');
	statut.hidden = true;
	panneau.append(qui, liste, sortie, statut);
	conteneur.replaceChildren(bouton, panneau);

	const ouvert = () => bouton.getAttribute('aria-expanded') === 'true';
	function basculer(ouvrir) {
		bouton.setAttribute('aria-expanded', String(ouvrir));
		panneau.hidden = !ouvrir;
	}

	bouton.addEventListener('click', () => basculer(!ouvert()));
	const surTouche = evenement => {
		if (evenement.key !== 'Escape' || !ouvert()) return;
		basculer(false);
		bouton.focus();
	};
	const surClic = evenement => {
		if (ouvert() && !conteneur.contains(evenement.target)) basculer(false);
	};
	conteneur.addEventListener('focusout', evenement => {
		// relatedTarget nul : la fenêtre perd le focus, ou un clic sur une zone inerte, que surClic traite déjà.
		if (ouvert() && evenement.relatedTarget && !conteneur.contains(evenement.relatedTarget)) basculer(false);
	});
	document.addEventListener('keydown', surTouche);
	document.addEventListener('click', surClic);

	sortie.addEventListener('click', async () => {
		if (sortie.disabled) return;
		sortie.disabled = true;
		statut.hidden = true;
		let resultat;
		try {
			resultat = await deconnecter();
		} catch {
			resultat = { ok: false };
		}
		if (resultat.ok || resultat.statut === 401) {
			apresDeconnexion();
			return;
		}
		statut.textContent = 'Impossible de confirmer la déconnexion. Réessaie.';
		statut.hidden = false;
		sortie.disabled = false;
	});

	return {
		fermer: () => basculer(false),
		demonter() {
			document.removeEventListener('keydown', surTouche);
			document.removeEventListener('click', surClic);
		},
	};
}
