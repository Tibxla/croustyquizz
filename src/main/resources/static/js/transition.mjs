import { decrireTransition, libellesTransition } from './vue-commune.mjs';

// Annonce et compte à rebours de la transition entre Manches (#123), dans la zone [data-zone-transition] de chaque
// coquille. La minuterie d'une seconde n'existe que pendant une transition ; le temps vient du serveur.
export function monterTransition(zone, { ecran, maintenantServeur, minuterie = globalThis }) {
	const annonce = zone.querySelector('[data-transition-annonce]');
	const delai = zone.querySelector('[data-transition-delai]');
	let vue = null;
	let tic = null;

	function rendre() {
		const description = vue && decrireTransition(vue, maintenantServeur());
		zone.hidden = !description;
		if (!description) return;
		const textes = libellesTransition(ecran, description);
		if (annonce.textContent !== textes.annonce) annonce.textContent = textes.annonce;
		delai.textContent = textes.delai;
	}
	function arreter() {
		if (tic === null) return;
		minuterie.clearInterval(tic);
		tic = null;
	}

	return {
		afficher(nouvelle) {
			vue = nouvelle;
			if (!vue.transitionManche) arreter();
			else if (tic === null) tic = minuterie.setInterval(rendre, 1000);
			rendre();
		},
		demonter() {
			arreter();
			vue = null;
			zone.hidden = true;
		},
	};
}
