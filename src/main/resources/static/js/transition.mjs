import { decrireTransition, libellesTransition } from './vue-commune.mjs';

// Marge après la frontière de seconde : une minuterie qui tire pile dessus afficherait encore l'ancien nombre.
const MARGE_MS = 25;

// Annonce et compte à rebours de la transition entre Manches (#123), dans la zone [data-zone-transition] de chaque
// coquille. Le temps vient du serveur : chaque rendu programme le suivant juste après la prochaine frontière de
// seconde de l'échéance, si bien qu'un retard de minuterie ne saute ni ne répète aucune seconde. Une seule minuterie
// vit pendant la transition ; elle s'arrête à zéro, quand la transition disparaît ou au départ de la page.
export function monterTransition(zone, { ecran, maintenantServeur, minuterie = globalThis }) {
	const annonce = zone.querySelector('[data-transition-annonce]');
	const delai = zone.querySelector('[data-transition-delai]');
	let vue = null;
	let tic = null;

	function rendre() {
		tic = null;
		const maintenant = maintenantServeur();
		const description = vue && decrireTransition(vue, maintenant);
		zone.hidden = !description;
		if (!description) return;
		const textes = libellesTransition(ecran, description);
		if (annonce.textContent !== textes.annonce) annonce.textContent = textes.annonce;
		delai.textContent = textes.delai;
		const restant = Date.parse(vue.transitionManche.finPrevueLe) - maintenant;
		if (restant > 0) tic = minuterie.setTimeout(rendre, (restant % 1000 || 1000) + MARGE_MS);
	}
	function arreter() {
		if (tic === null) return;
		minuterie.clearTimeout(tic);
		tic = null;
	}

	return {
		afficher(nouvelle) {
			vue = nouvelle;
			arreter();
			rendre();
		},
		demonter() {
			arreter();
			vue = null;
			zone.hidden = true;
		},
	};
}
