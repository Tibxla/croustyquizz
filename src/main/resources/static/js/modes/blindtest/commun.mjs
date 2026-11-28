import { textePartieTrouvee } from './logique.mjs';

const feuilles = new WeakMap();

/**
 * Socle commun aux trois modules : feuille de style du mode, éléments, écouteurs, état de connexion et minuterie
 * d'affichage. Il ne contient aucune règle de jeu.
 */
export function creerSocle(zones, contexte, { classe, intervalle = {} } = {}) {
	const document = zones.principale.ownerDocument;
	const programmer = intervalle.programmer ?? ((f, ms) => setInterval(f, ms));
	const annuler = intervalle.annuler ?? (id => clearInterval(id));
	const ecouteurs = [];
	let feuille = feuilles.get(document);
	if (!feuille) {
		const lien = document.createElement('link');
		lien.setAttribute('rel', 'stylesheet');
		lien.setAttribute('href', '/css/blindtest.css');
		lien.setAttribute('data-mode', 'blindtest');
		document.head.append(lien);
		feuille = { lien, nombre: 0 };
		feuilles.set(document, feuille);
	}
	feuille.nombre++;

	function e(tag, nom = '', texte) {
		const element = document.createElement(tag);
		element.className = nom;
		if (texte !== undefined) element.textContent = texte;
		return element;
	}
	const principale = e('section', `blindtest ${classe}`);
	const laterale = zones.laterale ? e('section', `blindtest ${classe}`) : null;
	zones.principale.replaceChildren(principale);
	if (laterale) zones.laterale.replaceChildren(laterale);

	let etatConnexion = 'synchronisation', minuterie, detruit = false;
	const socle = {
		document, principale, laterale, e,
		get synchronisee() { return etatConnexion === 'synchronisee'; },
		surConnexion() {},
		ecouter(element, type, rappel) {
			element.addEventListener(type, rappel);
			ecouteurs.push([element, type, rappel]);
		},
		/** Rafraîchit les comptes à rebours tant que demande() est vrai ; rien ne tourne sinon. */
		animer(demande, rafraichir) {
			if (demande && minuterie === undefined && !detruit) minuterie = programmer(rafraichir, 250);
			if (!demande && minuterie !== undefined) { annuler(minuterie); minuterie = undefined; }
		},
		detruire() {
			if (detruit) return;
			detruit = true;
			quitter();
			if (minuterie !== undefined) annuler(minuterie);
			for (const [element, type, rappel] of ecouteurs) element.removeEventListener(type, rappel);
			ecouteurs.length = 0;
			if (--feuille.nombre === 0) { feuille.lien.remove(); feuilles.delete(document); }
			for (const zone of Object.values(zones)) zone?.replaceChildren();
		},
	};
	const quitter = contexte.surEtatConnexion(etat => { etatConnexion = etat; socle.surConnexion(etat); });
	return socle;
}

/** Remplace un texte seulement s'il change, pour ne pas faire relire une zone vivante. */
export function ecrire(element, texte) {
	if (element.textContent !== texte) element.textContent = texte;
}

/** Liste des parties trouvées du Morceau, dans l'ordre du serveur. */
export function rendreParties(socle, liste, parties) {
	const textes = parties.map(textePartieTrouvee);
	const actuels = Array.from(liste.children, enfant => enfant.textContent);
	if (textes.length === actuels.length && textes.every((texte, i) => texte === actuels[i])) return false;
	liste.replaceChildren(...textes.map(texte => socle.e('li', 'blindtest__partie', texte)));
	liste.hidden = textes.length === 0;
	return true;
}

/** Libellé public de la phase du Morceau. */
export function textePhase(bloc) {
	if (bloc.etat === 'EN_PAUSE') return 'Pause';
	switch (bloc.phase) {
		case 'PREPARATION': return 'Le Morceau arrive…';
		case 'EXTRAIT': return 'Écoutez et buzzez !';
		case 'PROPOSITION': return 'Proposition en cours';
		case 'REVELATION': return 'C’était…';
		default: return '';
	}
}
