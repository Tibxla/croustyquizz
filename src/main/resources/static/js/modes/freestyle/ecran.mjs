import { creerInterface, creerTicket, texteAutresPassages } from '../karaoke/commun.mjs';
import { formaterNombreVotes } from '../../vue-commune.mjs';
import { positionDansGrille } from './grille.mjs';

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte, { mode: 'freestyle', annoncerConnexion: false });
	const { e } = ui;
	const fenetre = ui.document.defaultView;
	const audio = contexte.audioSalle;
	const lecteur = audio?.lecteur;
	const catalogue = contexte.catalogueInstrumentales;
	const interprete = e('h2', 'freestyle__interprete');
	const phase = e('p', 'freestyle__phase');
	const rythme = e('section', 'freestyle__grille');
	rythme.setAttribute('aria-label', 'Grille de rimes');
	const mesure = e('p', 'freestyle__mesure');
	const mot = e('p', 'freestyle__mot');
	const temps = e('ol', 'freestyle__temps');
	temps.setAttribute('aria-label', 'Temps de la mesure');
	const points = [1, 2, 3, 4].map(numero => {
		const point = e('li', 'freestyle__temps-fort', String(numero));
		const balle = e('span', 'freestyle__balle');
		balle.setAttribute('aria-hidden', 'true');
		balle.hidden = true;
		point.append(balle);
		temps.append(point);
		return { point, balle, numero };
	});
	rythme.append(mesure, temps, mot);
	const etatLecture = e('p', 'freestyle__phase');
	etatLecture.setAttribute('role', 'status');
	ui.principale.append(interprete, phase, rythme, etatLecture);
	const titresVotes = e('h2', '', 'Vote du public');
	const votes = e('ul', 'karaoke__liste-votes');
	const file = e('ol', 'rouleau a-venir');
	const autres = e('p');
	ui.laterale.append(titresVotes, votes, e('h2', '', 'À venir'), file, autres);

	let source, image, positionConnue = null, derniersTemps, dernierMot, dernierPassage, recaler = false;
	const actif = () => ui.bloc?.grille && ui.bloc.courant?.etapeId;
	const peutLire = cible => source === cible && ui.synchronisee && ui.bloc.etat === 'EN_COURS' && actif() === cible.etapeId;
	function afficherPosition(position) {
		const { grille, courant } = ui.bloc;
		const repere = position === null ? { mesure: null, temps: null, mot: null } :
			positionDansGrille(grille, courant.instrumentale, position);
		const numero = repere.mesure ?? 1;
		const texte = `Mesure ${numero} sur ${grille.nombreMesures} · Rime ${grille.mots[numero - 1].famille}`;
		if (mesure.textContent !== texte) mesure.textContent = texte;
		const affichage = repere.mot ?? (repere.temps === grille.mots[numero - 1].temps ? 'À toi de trouver' : '');
		if (dernierMot !== affichage) {
			mot.textContent = dernierMot = affichage;
		}
		if (derniersTemps !== repere.temps) {
			for (const { point, balle, numero } of points) {
				balle.hidden = numero !== repere.temps;
				if (balle.hidden) point.removeAttribute('aria-current');
				else point.setAttribute('aria-current', 'step');
			}
			derniersTemps = repere.temps;
		}
		if (repere.temps) {
			const hauteur = Math.sin(repere.progression * Math.PI);
			points[repere.temps - 1].balle.style.setProperty('--rebond', String(hauteur));
		}
	}
	function rafraichir() {
		if (!source?.joue) return;
		positionConnue = lecteur.position();
		afficherPosition(positionConnue);
		image = fenetre.requestAnimationFrame(rafraichir);
	}
	function figer() {
		if (image !== undefined) fenetre.cancelAnimationFrame(image);
		image = undefined;
		if (!source) return;
		if (source.joue || source.demarrage) {
			source.interruption++;
			lecteur.pause();
		}
		source.joue = false;
	}
	function arreter() {
		figer();
		if (source) {
			lecteur.detruire();
			void contexte.signalerSonDeManche(false);
		}
		source = undefined;
	}
	function afficherEtat(texte) {
		if (etatLecture.textContent !== texte) etatLecture.textContent = texte;
	}
	function echec(cible, message) {
		if (!cible || source !== cible) return;
		figer();
		cible.echec = true;
		lecteur.detruire();
		void contexte.signalerSonDeManche(false);
		afficherEtat(message);
	}
	function positionServeur() {
		const courant = ui.bloc.courant;
		const maintenant = contexte.maintenantServeur();
		return Math.max(0, (courant.positionChronologiqueMs ?? 0) + maintenant - Date.parse(courant.calculeLe));
	}
	async function demarrer(cible) {
		const interruption = cible.interruption;
		cible.demarrage = true;
		try {
			if (!cible.charge) {
				const autorisee = await contexte.signalerSonDeManche(true);
				if (!peutLire(cible)) return;
				if (!autorisee) throw new Error('Le lecteur de la Manche est indisponible.');
				await lecteur.charger(cible.fichier);
				if (!peutLire(cible)) return;
				await lecteur.lire(() => peutLire(cible) ? positionServeur() : null);
				cible.charge = peutLire(cible);
			} else {
				if (recaler) lecteur.positionner(positionServeur());
				await lecteur.reprendre();
			}
			if (!peutLire(cible)) {
				if (source === cible) lecteur.pause();
				return;
			}
			recaler = false;
			cible.joue = true;
			afficherEtat('');
			rafraichir();
		} catch {
			if (peutLire(cible) && interruption === cible.interruption) {
				echec(cible, 'Lecture impossible. Vérifie le fichier ou passe ce Passage depuis la Console.');
			}
		} finally {
			cible.demarrage = false;
			if (!cible.joue && !cible.echec && interruption !== cible.interruption && peutLire(cible)) synchroniser();
		}
	}
	function synchroniser() {
		const etapeId = actif();
		if (source && source.etapeId !== etapeId) arreter();
		if (!etapeId) { afficherEtat(''); return; }
		if (!ui.synchronisee || ui.bloc.etat === 'EN_PAUSE') {
			figer();
			recaler = true;
			return;
		}
		if (source?.echec) return;
		if (!source) {
			const fichier = catalogue?.fichier(ui.bloc.courant.instrumentale.instrumentaleId);
			if (!fichier) { afficherEtat('Instrumentale introuvable dans le dossier de musique.'); return; }
			if (!audio?.etat().sonDebloque) { afficherEtat('Active le son pour lire l’Instrumentale.'); return; }
			source = { etapeId, fichier, charge: false, joue: false, interruption: 0 };
		}
		if (!source.demarrage && !source.joue) void demarrer(source);
	}
	const quitterCatalogue = catalogue?.surChangement(synchroniser);
	const quitterAudio = audio?.surChangement(synchroniser);
	const quitterFin = lecteur?.surFin(() => echec(source, 'L’Instrumentale est terminée.'));
	const quitterErreur = lecteur?.surErreur(() => echec(source, 'Le fichier de l’Instrumentale est illisible.'));
	ui.surRendu = origine => {
		const bloc = ui.bloc;
		if (!bloc) return;
		const { courant, grille } = bloc;
		if (dernierPassage !== courant?.etapeId) positionConnue = null;
		dernierPassage = courant?.etapeId;
		if (origine?.source === 'capture') {
			recaler = true;
			figer();
		}
		interprete.hidden = !courant;
		interprete.textContent = courant?.interprete.pseudo ?? '';
		phase.textContent = bloc.etat === 'EN_PAUSE' ? 'Manche en pause' :
			({ APPELE: 'Monte sur scène', EN_COURS: courant?.instrumentale.titre, TERMINE: 'Passage terminé',
				SAUTE: 'Passage sauté' })[courant?.etat] ?? 'En attente d’un Passage';
		rythme.hidden = !grille;
		if (grille) afficherPosition(positionConnue);
		votes.replaceChildren(...bloc.votes.filter(vote => vote.etat === 'OUVERT').map(vote =>
			e('li', '', `${vote.interprete.pseudo} · ${formaterNombreVotes(vote.nombreVotes)}`)));
		titresVotes.hidden = !votes.childElementCount;
		file.replaceChildren(...bloc.passagesEnAttente.map(passage => {
			const ligne = e('li');
			ligne.append(creerTicket(ui, passage));
			return ligne;
		}));
		autres.textContent = texteAutresPassages(bloc.nombreEnAttente - bloc.passagesEnAttente.length);
		synchroniser();
	};
	ui.surDestruction = () => {
		arreter();
		quitterCatalogue?.();
		quitterAudio?.();
		quitterFin?.();
		quitterErreur?.();
	};
	return { afficher: ui.afficher, demonter: ui.demonter };
}
