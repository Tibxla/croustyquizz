import { aligner, extraitJouable } from './logique.mjs';

/** Une seule fenêtre de salle lit l'extrait : celle qui tient ce verrou, de son élection au démontage. */
export const VERROU_EXTRAIT = 'croustyquizz:son:extrait-blindtest';

/**
 * Lecteur de l'extrait sur l'Écran de salle. Il réserve le son de Manche auprès de la coquille dès qu'une Manche est
 * affichée (la Musique d'attente se tait jusqu'au démontage), puis suit l'état de lecture publié sans rien signaler
 * au serveur. Parmi les fenêtres dont le son est activé, la première qui obtient le verrou joue ; les autres restent
 * muettes et prennent la relève si elle se ferme.
 */
export function creerLecteurExtrait({ audio, verrous, signalerSonDeManche, sonActive, maintenantServeur,
	surBlocage = () => {}, surLecture = () => {} }) {
	let bloc, lienCharge, tour = 0, signale = false, detruit = false;
	let elue = false, candidature, liberer;

	function candidater() {
		if (elue || candidature || detruit || !sonActive()) return;
		// Sans Web Locks, la fenêtre se suppose seule, comme la coordination de la Musique d'attente.
		if (!verrous) { elue = true; return; }
		const fin = Promise.withResolvers();
		candidature = verrous.request(VERROU_EXTRAIT, { ifAvailable: true }, verrou => {
			if (!verrou || detruit) return null;
			elue = true;
			liberer = fin.resolve;
			void suivre(bloc);
			return fin.promise;
		}).catch(() => {}).finally(() => {
			candidature = undefined;
			elue = false;
			liberer = undefined;
		});
	}

	function arreter() {
		audio.arreter();
		lienCharge = undefined;
	}

	async function suivre(nouveau) {
		if (detruit || !nouveau) return;
		bloc = nouveau;
		const t = ++tour;
		signale = true;
		const autorise = await signalerSonDeManche(true);
		if (t !== tour || detruit) return;
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
		try {
			await audio.lire();
			if (t === tour && !detruit) surLecture();
		} catch (erreur) { if (t === tour && !detruit) surBlocage(erreur); }
	}

	return {
		suivre,
		/** Le son vient d'être activé dans la coquille : la fenêtre peut se porter candidate. */
		relancer: () => suivre(bloc),
		get elue() { return elue; },
		detruire() {
			if (detruit) return;
			detruit = true;
			tour++;
			arreter();
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
