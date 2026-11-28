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

const ARTICLES = { TITRE: 'le titre', ARTISTE: 'l’artiste' };

/** Annonce de la salle après un arbitrage : « L’Animateur accorde l’artiste à Léa. » ; vide sans arbitrage. */
export function texteArbitrage(arbitrage) {
	if (!arbitrage) return '';
	const partie = ARTICLES[arbitrage.partie] ?? arbitrage.partie;
	return arbitrage.verdict === 'JUSTE' ? `L’Animateur accorde ${partie} à ${arbitrage.pseudo}.`
		: `L’Animateur refuse ${partie} de ${arbitrage.pseudo}.`;
}

/** Le même arbitrage, dit au Joueur concerné ; vide s'il ne le concerne pas. */
function arbitragePersonnel(arbitrage, joueurId) {
	if (!arbitrage || arbitrage.joueurId !== joueurId) return '';
	return arbitrage.verdict === 'JUSTE' ? `L’Animateur t’accorde ${ARTICLES[arbitrage.partie]}.`
		: `L’Animateur refuse ton ${arbitrage.partie === 'TITRE' ? 'titre' : 'artiste'}.`;
}

/**
 * Verdict du Joueur lu dans la vue, jamais dans l'accusé : null tant que la vue ne dit rien de lui sur ce Morceau.
 * Un arbitrage qui le concerne est dit en premier.
 */
export function verdictPersonnel(bloc, joueurId) {
	const trouvees = bloc.partiesTrouvees.filter(partie => partie.joueurId === joueurId);
	const points = trouvees.reduce((total, partie) => total + partie.points, 0);
	const gain = trouvees.length
		? `${trouvees.map(p => libellePartie(p.partie)).join(' et ')} trouvé${trouvees.length > 1 ? 's' : ''}, +${points} points.`
		: '';
	const arbitre = arbitragePersonnel(bloc.arbitrage, joueurId);
	const prefixe = arbitre ? `${arbitre} ` : '';
	if (bloc.bloque) {
		return { ton: 'refuse', texte: prefixe + (gain ? `${gain} Le reste est faux : tu es bloqué sur ce Morceau.`
			: 'Proposition fausse : tu es bloqué sur ce Morceau.') };
	}
	if (gain) return { ton: 'accepte', texte: prefixe + gain };
	return arbitre ? { ton: 'accepte', texte: arbitre } : null;
}

/**
 * Geste d'arbitrage proposé à l'Animateur pour un champ jugé : refuser un champ juste, accorder un champ faux non
 * vide. Un champ non demandé, ou vide, n'en a pas. Le serveur reste seul juge (partie trouvée depuis, par exemple).
 */
export function actionArbitrage(champ) {
	if (champ.verdict === 'JUSTE') return { verdict: 'FAUX', libelle: 'Refuser' };
	if (champ.verdict === 'FAUX' && champ.saisie.trim()) return { verdict: 'JUSTE', libelle: 'Accorder' };
	return null;
}

/** « Artiste : « Beyonse », faux » ; « (arbitré) » quand l'Animateur a renversé le verdict. */
export function texteChampJuge(partie, champ) {
	const libelle = libellePartie(partie);
	if (champ.verdict === 'NON_DEMANDE') return `${libelle} : déjà trouvé`;
	const saisie = champ.saisie.trim() ? `« ${champ.saisie} »` : 'vide';
	return `${libelle} : ${saisie}, ${champ.verdict === 'JUSTE' ? 'juste' : 'faux'}${champ.arbitre ? ' (arbitré)' : ''}`;
}

/**
 * Envoi du brouillon au fil de la saisie : espacé par un délai, une seule requête à la fois, jamais réessayé tout
 * seul. La saisie la plus récente part après la requête en cours. Un échec est signalé (surEchec(true)) tant qu'aucun
 * envoi plus récent n'a réussi : le Joueur renvoie à la main, avec un numéro supérieur, ce que le serveur accepte sans
 * risque puisqu'il ignore tout numéro qui n'augmente pas.
 */
export function creerEnvoiBrouillon({ envoyer, programmer, annuler, delaiMs = 500, surEchec = () => {} }) {
	let numero = 0, minuterie, enVol = false, enAttente = null, detruit = false, echec = false;
	function signaler(valeur) {
		if (detruit || valeur === echec) return;
		echec = valeur;
		surEchec(valeur);
	}
	function partir() {
		if (minuterie !== undefined) annuler(minuterie);
		minuterie = undefined;
		if (enVol || !enAttente || detruit) return;
		const saisie = enAttente;
		enAttente = null;
		enVol = true;
		numero++;
		Promise.resolve().then(() => envoyer({ numero, titre: saisie.titre, artiste: saisie.artiste }))
			.then(reponse => signaler(!(reponse?.statut >= 200 && reponse.statut < 300)),
				erreur => { if (erreur?.name !== 'AbortError') signaler(true); })
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
		/** Geste du Joueur après un échec : la saisie courante part sans attendre le délai. */
		renvoyer(saisie) {
			if (detruit) return;
			enAttente = saisie;
			partir();
		},
		get numero() { return numero; },
		get echec() { return echec; },
		detruire() {
			detruit = true;
			if (minuterie !== undefined) annuler(minuterie);
			minuterie = undefined;
			enAttente = null;
		},
	};
}
