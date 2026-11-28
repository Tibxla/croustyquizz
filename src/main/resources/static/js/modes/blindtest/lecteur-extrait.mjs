import { aligner, DUREE_EXTRAIT_MS, extraitJouable } from './logique.mjs';

/** En deçà de cette marge, l'extrait est considéré comme fini : la Révélation ne le relance pas. */
const MARGE_FIN_MS = 250;
/** Une seule fenêtre de salle lit l'extrait : celle qui tient ce verrou, de son élection au démontage. */
export const VERROU_EXTRAIT = 'croustyquizz:son:extrait-blindtest';

/**
 * Lecteur de l'extrait sur l'Écran de salle. Il réserve le son de Manche auprès de la coquille dès qu'une Manche est
 * affichée (la Musique d'attente se tait jusqu'au démontage), puis suit l'état de lecture publié sans rien signaler
 * au serveur. Parmi les fenêtres dont le son est activé, la première qui obtient le verrou joue ; les autres restent
 * muettes, en file d'attente sur le verrou, et la suivante prend la relève dès que l'élue se ferme. Pendant la Révélation, la fenêtre qui jouait laisse l'extrait aller
 * jusqu'à sa fin, pour l'ambiance : la vue ne porte plus de lecture, la salle continue d'où elle était.
 */
export function creerLecteurExtrait({ audio, verrous, signalerSonDeManche, sonActive, maintenantServeur,
	surBlocage = () => {}, surIllisible = () => {}, surLecture = () => {} }) {
	let bloc, lienCharge, tour = 0, signale = false, detruit = false;
	let elue = false, candidature, liberer, abandon;

	function candidater() {
		if (elue || candidature || detruit || !sonActive()) return;
		// Sans Web Locks (http:// hors localhost), la fenêtre se suppose seule, comme pour la Musique d'attente :
		// deux fenêtres de salle ouvertes joueraient alors toutes les deux.
		if (!verrous) { elue = true; return; }
		const fin = Promise.withResolvers();
		abandon = new AbortController();
		// Demande en file d'attente : la fenêtre muette obtient le verrou dès que l'élue le libère.
		candidature = verrous.request(VERROU_EXTRAIT, { signal: abandon.signal }, () => {
			if (detruit) return null;
			elue = true;
			liberer = fin.resolve;
			void suivre(bloc);
			return fin.promise;
		}).catch(() => {}).finally(() => {
			candidature = undefined;
			elue = false;
			liberer = undefined;
			abandon = undefined;
		});
	}

	/**
	 * Seul un refus du navigateur (NotAllowedError) demande un geste ; une lecture interrompue par un changement de
	 * source (AbortError) n'est pas une erreur ; le reste (lien expiré, média illisible) rend l'extrait illisible.
	 */
	async function jouer(t) {
		try {
			await audio.lire();
			if (t === tour && !detruit) surLecture();
		} catch (erreur) {
			if (t !== tour || detruit || erreur?.name === 'AbortError') return;
			if (erreur?.name === 'NotAllowedError') surBlocage();
			else surIllisible();
		}
	}

	function arreter() {
		audio.arreter();
		lienCharge = undefined;
	}

	/**
	 * Révélation : l'extrait déjà chargé continue d'où il était, en pause avec la Manche. Rien ne reprend après un
	 * rechargement (plus de lien dans la vue), ni sur un extrait arrivé au bout.
	 */
	async function prolonger(t) {
		const reste = lienCharge && elue && !audio.termine() && audio.position() < DUREE_EXTRAIT_MS - MARGE_FIN_MS;
		if (!reste) { arreter(); return; }
		if (bloc.etat === 'EN_PAUSE') { audio.pause(); return; }
		await jouer(t);
	}

	async function suivre(nouveau) {
		if (detruit || !nouveau) return;
		bloc = nouveau;
		const t = ++tour;
		signale = true;
		const autorise = await signalerSonDeManche(true);
		if (t !== tour || detruit) return;
		if (bloc.phase === 'REVELATION') { await prolonger(t); return; }
		if (!extraitJouable(bloc)) { arreter(); return; }
		candidater();
		if (!elue || !autorise) { arreter(); return; }
		const nouveauLien = lienCharge !== bloc.lienExtrait;
		if (nouveauLien) {
			audio.charger(bloc.lienExtrait);
			lienCharge = bloc.lienExtrait;
		}
		const { enLecture, allerA } = aligner({ lecture: bloc.lecture, maintenantMs: maintenantServeur(),
			positionMs: nouveauLien ? null : audio.position() });
		if (allerA !== null) audio.aller(allerA);
		if (!enLecture) { audio.pause(); return; }
		await jouer(t);
	}

	return {
		suivre,
		/** Le son vient d'être activé dans la coquille : la fenêtre peut se porter candidate. */
		relancer: () => suivre(bloc),
		/**
		 * Clic sur « Lancer l'extrait » : play() part pendant le geste, sur l'élément même de l'extrait, ce qui le
		 * débloque aussi sur les navigateurs qui débloquent élément par élément (Safari). Le suivi réaligne ensuite.
		 */
		lancerParGeste() {
			if (detruit || !elue || !lienCharge) return Promise.resolve();
			audio.lire().catch(() => {});
			return suivre(bloc);
		},
		get elue() { return elue; },
		detruire() {
			if (detruit) return;
			detruit = true;
			tour++;
			arreter();
			abandon?.abort();
			liberer?.();
			if (signale) void signalerSonDeManche(false);
		},
	};
}

/** Adaptateur d'un élément audio du navigateur ; il n'est pas ajouté à la page. */
export function creerAudioExtrait(document) {
	const element = document.createElement('audio');
	element.preload = 'auto';
	return {
		element,
		charger(lien) { element.src = lien; },
		lire: () => element.play(),
		pause: () => element.pause(),
		position: () => element.currentTime * 1000,
		termine: () => element.ended,
		aller(ms) { element.currentTime = ms / 1000; },
		arreter() {
			element.pause();
			if (element.getAttribute('src') !== null) {
				element.removeAttribute('src');
				element.load();
			}
		},
	};
}
