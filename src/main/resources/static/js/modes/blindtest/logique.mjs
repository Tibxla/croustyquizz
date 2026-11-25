// Calculs du Blind test sans DOM ni réseau : les pages n'y prennent aucune décision de jeu, elles lisent la vue.

export const DUREE_EXTRAIT_MS = 30000;
const PARTIES = { TITRE: 'Titre', ARTISTE: 'Artiste' };

/** Position où l'extrait doit être maintenant, d'après l'état de lecture publié par le serveur ; null sans lecture. */
export function positionCibleMs(lecture, maintenantMs) {
	if (!lecture) return null;
	if (lecture.etat !== 'EN_LECTURE') return lecture.positionMs;
	const ecoule = maintenantMs - Date.parse(lecture.depuisLe);
	return Math.min(DUREE_EXTRAIT_MS, Math.max(0, lecture.positionMs + ecoule));
}

/**
 * Ce que le lecteur local doit faire pour suivre la lecture publiée : jouer ou attendre, et se déplacer seulement
 * quand l'écart dépasse la tolérance, pour ne pas hacher le son à chaque vue.
 */
export function aligner({ lecture, maintenantMs, positionMs, toleranceMs = 350 }) {
	const cible = positionCibleMs(lecture, maintenantMs);
	if (cible === null) return { enLecture: false, allerA: null };
	const enLecture = lecture.etat === 'EN_LECTURE' && cible < DUREE_EXTRAIT_MS;
	const ecart = Math.abs((positionMs ?? 0) - cible);
	return { enLecture, allerA: positionMs == null || ecart > toleranceMs ? cible : null };
}

/** Temps restant de la saisie du Joueur qui a la main : gelé pendant une pause, sinon compté jusqu'à l'échéance. */
export function tempsRestantMs(main, maintenantMs) {
	if (!main) return 0;
	if (main.echeanceLe === null) return main.tempsRestantMs;
	return Math.max(0, Date.parse(main.echeanceLe) - maintenantMs);
}

/** Temps d'extrait restant, en millisecondes. */
export function extraitRestantMs(lecture, maintenantMs) {
	const position = positionCibleMs(lecture, maintenantMs);
	return position === null ? 0 : DUREE_EXTRAIT_MS - position;
}

export function formaterSecondes(ms) {
	return `${Math.ceil(Math.max(0, ms) / 1000)} s`;
}

export function libellePartie(partie) {
	return PARTIES[partie] ?? partie;
}

/** « Artiste trouvé par Léa : Beyoncé ». */
export function textePartieTrouvee({ partie, pseudo, valeur }) {
	return `${libellePartie(partie)} trouvé par ${pseudo} : ${valeur}`;
}

export function texteNumero(bloc) {
	return `Morceau ${bloc.numero} sur ${bloc.total}`;
}

/** Le son de Manche est réservé dès qu'une Manche de Blind test est affichée, jusqu'au démontage du module. */
export function extraitJouable(bloc) {
	return Boolean(bloc?.lienExtrait) && (bloc.phase === 'EXTRAIT' || bloc.phase === 'PROPOSITION');
}

/**
 * Verdict du Joueur lu dans la vue, jamais dans l'accusé : null tant que la vue ne dit rien de lui sur ce Morceau.
 */
export function verdictPersonnel(bloc, joueurId) {
	const trouvees = bloc.partiesTrouvees.filter(partie => partie.joueurId === joueurId);
	const points = trouvees.reduce((total, partie) => total + partie.points, 0);
	const gain = trouvees.length
		? `${trouvees.map(p => libellePartie(p.partie)).join(' et ')} trouvé${trouvees.length > 1 ? 's' : ''}, +${points} points.`
		: '';
	if (bloc.bloque) {
		return { ton: 'refuse', texte: gain ? `${gain} Le reste est faux : tu es bloqué sur ce Morceau.`
			: 'Proposition fausse : tu es bloqué sur ce Morceau.' };
	}
	return gain ? { ton: 'accepte', texte: gain } : null;
}

/**
 * Envoi du brouillon au fil de la saisie : espacé par un délai, une seule requête à la fois, jamais réessayé. La
 * saisie la plus récente part après la requête en cours ; un échec attend simplement la frappe suivante.
 */
export function creerEnvoiBrouillon({ envoyer, programmer, annuler, delaiMs = 500 }) {
	let numero = 0, minuterie, enVol = false, enAttente = null, detruit = false;
	function partir() {
		minuterie = undefined;
		if (enVol || !enAttente || detruit) return;
		const saisie = enAttente;
		enAttente = null;
		enVol = true;
		numero++;
		Promise.resolve().then(() => envoyer({ numero, titre: saisie.titre, artiste: saisie.artiste }))
			.catch(() => {})
			.finally(() => {
				enVol = false;
				if (enAttente && minuterie === undefined && !detruit) partir();
			});
	}
	return {
		/** Après un rechargement, la numérotation repart du brouillon retenu par le serveur. */
		reprendre(dernier) { numero = Math.max(numero, dernier ?? 0); },
		saisir(saisie) {
			if (detruit) return;
			enAttente = saisie;
			if (minuterie !== undefined) annuler(minuterie);
			minuterie = programmer(partir, delaiMs);
		},
		get numero() { return numero; },
		detruire() {
			detruit = true;
			if (minuterie !== undefined) annuler(minuterie);
			minuterie = undefined;
			enAttente = null;
		},
	};
}
