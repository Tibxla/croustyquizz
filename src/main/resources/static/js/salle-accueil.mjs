// Écran de salle hors Passage (#281) : l'accueil des arrivées, l'annonce du Buzz et l'état du son.
// Aucune règle de jeu : tout vient de la vue synchronisée ; seul un événement anime.
import { decrireClassementDeSalle, decrirePalmaresDeSalle } from './vue-commune.mjs';

/** Hors Manche, quand rien d'autre ne tient la scène (Palmarès, classement, photo), la salle accueille les arrivées. */
export function estAccueil(vue, { photo = false } = {}) {
	const ouverte = vue.soiree.etat === 'OUVERTE' || vue.soiree.etat === 'EN_COURS';
	return ouverte && !vue.modeEnCours && !photo && decrirePalmaresDeSalle(vue) === null && decrireClassementDeSalle(vue) === null;
}

/**
 * Les arrivées, dans l'ordre : les derniers arrivés restent visibles, les autres se résument (« et 36 autres »).
 * Un Joueur déjà affiché n'est pas recréé ; une arrivée reçue par événement s'anime, une capture restaure sans animer.
 */
export function creerArrivees(liste, { maximum = 24 } = {}) {
	const document = liste.ownerDocument;
	const lignes = new Map();
	const resume = document.createElement('li');
	resume.className = 'arrivee arrivee--autres';
	return {
		afficher(joueurs, origine) {
			const visibles = joueurs.slice(-maximum);
			const presents = new Set(visibles.map(joueur => joueur.id));
			for (const id of [...lignes.keys()]) if (!presents.has(id)) lignes.delete(id);
			const enfants = visibles.map(joueur => {
				let ligne = lignes.get(joueur.id);
				if (!ligne) {
					ligne = document.createElement('li');
					ligne.className = 'arrivee';
					if (origine?.source === 'evenement') ligne.classList.add('arrivee--nouvelle');
					lignes.set(joueur.id, ligne);
				}
				if (ligne.textContent !== joueur.pseudo) ligne.textContent = joueur.pseudo;
				return ligne;
			});
			const autres = joueurs.length - visibles.length;
			resume.textContent = `et ${autres} ${autres > 1 ? 'autres' : 'autre'}`;
			liste.replaceChildren(...(autres > 0 ? [resume] : []), ...enfants);
		},
	};
}

/** « Bartholoméo a buzzé ! » en grand, quelques secondes, à chaque nouveau Buzz reçu par événement. */
export function creerAnnonceBuzz(element, { programmer = (f, ms) => setTimeout(f, ms), annuler = clearTimeout, duree = 4000 } = {}) {
	let dernier;
	let minuteur;
	element.hidden = true;
	function effacer() { element.hidden = true; minuteur = undefined; }
	return {
		afficher(vue, origine) {
			const buzz = vue.dernierBuzz;
			const nouveau = buzz && dernier !== undefined && buzz.envoiId !== dernier;
			dernier = buzz?.envoiId ?? null;
			if (vue.modeEnCours) { if (minuteur !== undefined) annuler(minuteur); effacer(); return; }
			if (!nouveau || origine?.source !== 'evenement') return;
			element.textContent = `${buzz.pseudo} a buzzé !`;
			element.hidden = false;
			// Relancer l'animation d'entrée pour un Buzz qui suit de près le précédent.
			element.classList.remove('annonce-buzz--entree');
			void element.offsetWidth;
			element.classList.add('annonce-buzz--entree');
			if (minuteur !== undefined) annuler(minuteur);
			minuteur = programmer(effacer, duree);
		},
		demonter() { if (minuteur !== undefined) annuler(minuteur); },
	};
}

const morceaux = n => `${n} morceau${n > 1 ? 'x' : ''}`;

/** Le panneau d'installation tant que le son n'est pas activé, puis une simple pastille dans le pied (#281, I8). */
export function decrireSon({ fichiers, sonDebloque }) {
	if (!sonDebloque) return { etat: 'installation', pastille: 'Son coupé' };
	return { etat: 'pret', pastille: fichiers?.length ? `Son activé · ${morceaux(fichiers.length)}` : 'Paroles seules' };
}
