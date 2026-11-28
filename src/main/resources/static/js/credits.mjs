// Page des crédits musicaux, générée depuis le catalogue de la Bibliothèque de base (ADR 0011).
import { chargerBibliotheque, decrireCredits } from './bibliotheque.mjs';

function lien(document, texte, adresse) {
	const a = document.createElement('a');
	a.href = adresse;
	a.textContent = texte;
	return a;
}

export function rendreCredits(document, conteneur, catalogue) {
	const sections = decrireCredits(catalogue);
	if (!sections.length) {
		const vide = document.createElement('p');
		vide.textContent = 'La Bibliothèque de base est vide pour l’instant : seule la musique du Lieu est jouée.';
		conteneur.replaceChildren(vide);
		return;
	}
	conteneur.replaceChildren(...sections.map(({ titre, entrees }) => {
		const section = document.createElement('section');
		const h2 = document.createElement('h2');
		h2.textContent = titre;
		const liste = document.createElement('ul');
		liste.append(...entrees.map(entree => {
			const li = document.createElement('li');
			const nom = document.createElement('strong');
			nom.textContent = `« ${entree.titre} »`;
			li.append(nom, `, ${entree.artiste}. Licence `,
				entree.urlLicence ? lien(document, entree.licence, entree.urlLicence) : entree.licence, '. ',
				lien(document, 'Source', entree.source), '.');
			if (entree.paroles) {
				li.append(' ', lien(document, 'Paroles synchronisées', entree.paroles),
					entree.parolesPartagees ? ', sous la même licence.' : '.');
			}
			return li;
		}));
		section.append(h2, liste);
		return section;
	}));
}

if (typeof document !== 'undefined' && document.querySelector('[data-credits]')) {
	rendreCredits(document, document.querySelector('[data-credits]'), await chargerBibliotheque());
}
