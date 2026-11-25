import { creerImage, element } from './commun.mjs';

function programmerExpiration(action, delai) {
	const minuteur = setTimeout(action, delai);
	return () => clearTimeout(minuteur);
}

export function monter(zone, contexte, adaptations = {}) {
	const { programmer = programmerExpiration } = adaptations;
	const image = element(zone.ownerDocument, 'img', { alt: 'Photo de la salle' });
	zone.replaceChildren(image);
	zone.hidden = true;
	const chargeur = creerImage(image, contexte, { ...adaptations, surErreur: effacer });
	let synchronisee = false;
	let affiche, photoId, annuler;

	function effacer() {
		annuler?.();
		annuler = null;
		chargeur.effacer();
		photoId = null;
		zone.hidden = true;
	}

	function actualiser() {
		const delai = affiche && Date.parse(affiche.echeance) - contexte.maintenantServeur();
		if (!synchronisee || !(delai > 0)) {
			effacer();
			return;
		}
		if (photoId !== affiche.photoId) {
			effacer();
			photoId = affiche.photoId;
			void chargeur.charger(photoId);
		}
		annuler?.();
		annuler = programmer(effacer, delai);
	}
	const imageChargee = () => {
		zone.hidden = !photoId || Date.parse(affiche.echeance) <= contexte.maintenantServeur();
	};
	image.addEventListener('load', imageChargee);
	image.addEventListener('error', effacer);
	const desabonner = contexte.surEtatConnexion(etat => {
		synchronisee = etat === 'synchronisee';
		actualiser();
	});

	return {
		afficher(vue) {
			affiche = vue.mancheActive ? null : vue.photos;
			actualiser();
		},
		demonter() {
			effacer();
			desabonner();
			image.removeEventListener('load', imageChargee);
			image.removeEventListener('error', effacer);
			zone.replaceChildren();
		},
	};
}
