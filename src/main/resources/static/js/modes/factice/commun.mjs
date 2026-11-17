import { blocDuMode } from '../../modules-de-mode.mjs';

export function monter(zones) {
	const document = zones.principale.ownerDocument;
	const titre = document.createElement('h2');
	titre.className = 'mode__titre';
	titre.textContent = 'Mode factice';
	const contenu = document.createElement('pre');
	contenu.className = 'mode__contenu';
	zones.principale.replaceChildren(titre, contenu);
	const provenance = document.createElement('p');
	provenance.className = 'coquille__note';
	zones.laterale?.replaceChildren(provenance);
	return {
		afficher(vue, origine) {
			contenu.textContent = JSON.stringify(vue[blocDuMode(vue.modeEnCours)] ?? null, null, 2);
			provenance.textContent = origine.source === 'evenement' ? `Événement : ${origine.type}` : 'Capture complète';
		},
		demonter() {
			zones.principale.replaceChildren();
			zones.laterale?.replaceChildren();
		},
	};
}
