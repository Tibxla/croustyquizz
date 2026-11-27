import { creerInterface, creerTicket, texteAutresPassages } from '../karaoke/commun.mjs';
import { formaterNombreVotes } from '../../vue-commune.mjs';
import { positionDansGrille } from './grille.mjs';
import { pseudos, texteAnnonce, texteCouplet, texteOrdre } from './couplets.mjs';
import { creerAppelSalle, creerVoteSalle, voteAuCentre } from '../passage/salle.mjs';

// Écart toléré entre le lecteur et la position estimée par le serveur avant de recaler la lecture.
const ECART_DE_RECALAGE_MS = 300;

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
	const suivant = e('p', 'freestyle__suivant');
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
	rythme.append(mesure, temps, mot, suivant);
	const etatLecture = e('p', 'freestyle__phase');
	etatLecture.setAttribute('role', 'status');
	// Passage à plusieurs (#74) : ordre tiré par le serveur, Couplet courant et annonce du suivant.
	const ordreCouplets = e('p', 'freestyle__ordre');
	const coupletCourant = e('p', 'freestyle__couplet');
	const annonceSuivant = e('p', 'freestyle__annonce');
	annonceSuivant.setAttribute('role', 'status');
	// La Grille doit rester entière à 1080 px : l'ordre et le Couplet courant vont dans la zone latérale.
	// À l'appel (#277), la salle nomme les Interprètes en grand, avec l'Instrumentale et le temps restant.
	// Pendant le vote (#278), sans Passage en scène, la salle montre le vote au centre.
	const appelSalle = creerAppelSalle(ui), voteSalle = creerVoteSalle(ui);
	ui.principale.append(appelSalle.element, voteSalle.element, interprete, phase, annonceSuivant, rythme, etatLecture);
	let decompte, voteCentre = null;
	function decompter() {
		const bloc = ui.bloc;
		const appele = bloc?.courant?.etat === 'APPELE' ? bloc.courant : null;
		const temps = { maintenant: contexte.maintenantServeur(), enPause: bloc?.etat === 'EN_PAUSE' };
		appelSalle.decompter(appele, temps);
		voteSalle.decompter(voteCentre, temps);
	}
	function suivreDecompte(actif) {
		if (actif && decompte === undefined) decompte = setInterval(decompter, 250);
		if (!actif && decompte !== undefined) { clearInterval(decompte); decompte = undefined; }
	}
	const titresVotes = e('h2', '', 'Vote du public');
	const votes = e('ul', 'karaoke__liste-votes');
	const file = e('ol', 'rouleau a-venir');
	const autres = e('p');
	ui.laterale.append(ordreCouplets, coupletCourant, titresVotes, votes, e('h2', '', 'À venir'), file, autres);

	let source, image, positionConnue = null, derniersTemps, dernierMot, dernierPassage, recaler = false;
	let sonDeManche = false, autorisation = Promise.resolve(false);
	const actif = () => ui.bloc?.grille && ui.bloc.courant?.etapeId;
	const enCours = () => ui.bloc.etat === 'EN_COURS';
	// Seule la fenêtre désignée par la dernière déclaration joue ; les autres suivent la position du serveur.
	const estLecteur = () => Boolean(lecteur) && ui.bloc?.lecteurDesigneId === audio.lecteurId;
	// La connexion ne compte pas ici : seule la dernière vue reçue décide de jouer ou de figer.
	const peutLire = cible => source === cible && enCours() && actif() === cible.etapeId && estLecteur();
	function afficherPosition(position) {
		const { grille, courant } = ui.bloc;
		const repere = position === null ? { phase: 'avant', mesure: null, temps: null } :
			positionDansGrille(grille, courant.instrumentale, position);
		// Après la Grille, la dernière mesure reste affichée sans mot actif jusqu’à la fin du Passage.
		const numero = repere.phase === 'apres' ? grille.nombreMesures : repere.mesure ?? 1;
		const texte = `Mesure ${numero} sur ${grille.nombreMesures} · Rime ${grille.mots[numero - 1].famille}`;
		if (mesure.textContent !== texte) mesure.textContent = texte;
		// Le mot reste affiché toute sa mesure pour que l'Interprète le lise à 5 m, et il s'allume sur son temps.
		const motDeLaMesure = grille.mots[numero - 1], prochain = grille.mots[numero];
		const affichage = repere.phase === 'avant' ? '' : motDeLaMesure.mot ?? 'À toi de trouver';
		const annonce = repere.phase !== 'pendant' || !prochain ? '' : `Ensuite : ${prochain.mot ?? '…'}`;
		if (dernierMot !== affichage) {
			mot.textContent = dernierMot = affichage;
		}
		if (suivant.textContent !== annonce) suivant.textContent = annonce;
		mot.classList.toggle('freestyle__mot--actif', repere.phase === 'pendant' && repere.temps === motDeLaMesure.temps);
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
	function positionAnimee() {
		if (source?.joue) return lecteur.position();
		if (!estLecteur() && actif() && enCours()) return positionServeur();
		return undefined;
	}
	function rafraichir() {
		image = undefined;
		const position = positionAnimee();
		if (position === undefined) return;
		positionConnue = position;
		afficherPosition(position);
		image = fenetre.requestAnimationFrame(rafraichir);
	}
	function animer() {
		if (image === undefined) rafraichir();
	}
	function arreterAnimation() {
		if (image !== undefined) fenetre.cancelAnimationFrame(image);
		image = undefined;
	}
	function figer() {
		arreterAnimation();
		if (!source) return;
		if (source.joue || source.demarrage) {
			source.interruption++;
			lecteur.pause();
		}
		source.joue = false;
	}
	function arreter() {
		figer();
		if (source) lecteur.detruire();
		source = undefined;
	}
	// Comme au Karaoké, tout Écran de salle suspend la Musique d’attente pendant un Passage, pause comprise.
	function signalerSon(actif) {
		if (actif === sonDeManche) return;
		sonDeManche = actif;
		if (actif) autorisation = contexte.signalerSonDeManche?.(true) ?? Promise.resolve(false);
		else void contexte.signalerSonDeManche?.(false);
	}
	function afficherEtat(texte) {
		if (etatLecture.textContent !== texte) etatLecture.textContent = texte;
	}
	function echec(cible, message) {
		if (!cible || source !== cible) return;
		figer();
		cible.echec = true;
		lecteur.detruire();
		signalerSon(false);
		afficherEtat(message);
	}
	// Le serveur seul termine le Passage : le son de Manche reste réservé jusqu’à passage-termine.
	function terminerFichier(cible) {
		if (!cible || source !== cible || !cible.joue) return;
		arreterAnimation();
		cible.joue = false;
		cible.fini = true;
		positionConnue = lecteur.position();
		if (ui.bloc?.grille) afficherPosition(positionConnue);
	}
	function positionServeur() {
		const courant = ui.bloc.courant;
		const position = courant.positionChronologiqueMs ?? 0;
		if (!enCours()) return position;
		return Math.max(0, position + contexte.maintenantServeur() - Date.parse(courant.calculeLe));
	}
	async function demarrer(cible) {
		const interruption = cible.interruption;
		cible.demarrage = true;
		try {
			if (!cible.charge) {
				const autorisee = await autorisation;
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
			animer();
		} catch {
			if (peutLire(cible) && interruption === cible.interruption) {
				echec(cible, 'Lecture impossible. Vérifie le fichier ou passe ce Passage depuis la Console.');
			}
		} finally {
			cible.demarrage = false;
			if (!cible.joue && !cible.echec && interruption !== cible.interruption && peutLire(cible)) synchroniser();
		}
	}
	function suivreSansLecteur() {
		afficherEtat(ui.bloc.lecteurDesigneId ? 'Un autre Écran de salle joue l’Instrumentale.'
			: 'Choisis le dossier de musique sur l’Écran de salle qui jouera l’Instrumentale.');
		positionConnue = positionServeur();
		afficherPosition(positionConnue);
		if (enCours()) animer();
		else arreterAnimation();
	}
	function synchroniser() {
		const etapeId = actif();
		if (source && (source.etapeId !== etapeId || !estLecteur())) arreter();
		signalerSon(Boolean(etapeId) && !source?.echec);
		if (!etapeId) { arreterAnimation(); afficherEtat(''); return; }
		if (!estLecteur()) { suivreSansLecteur(); return; }
		if (ui.bloc.etat === 'EN_PAUSE') {
			figer();
			recaler = true;
			return;
		}
		if (source?.echec || source?.fini) return;
		// Hors synchronisation, une lecture en cours continue ; la prochaine capture la recale.
		if (!ui.synchronisee) return;
		if (!source) {
			const fichier = catalogue?.fichier(ui.bloc.courant.instrumentale.instrumentaleId);
			if (!fichier) { afficherEtat('Instrumentale introuvable dans le dossier de musique.'); return; }
			if (!audio.etat().sonDebloque) { afficherEtat('Active le son pour lire l’Instrumentale.'); return; }
			source = { etapeId, fichier, charge: false, joue: false, interruption: 0 };
		}
		if (!source.demarrage && !source.joue) void demarrer(source);
	}
	function recalerSurCapture() {
		if (!source?.joue || !peutLire(source)) { recaler = true; return; }
		const cible = positionServeur();
		if (Math.abs(lecteur.position() - cible) > ECART_DE_RECALAGE_MS) lecteur.positionner(cible);
	}
	const quitterCatalogue = catalogue?.surChangement(synchroniser);
	const quitterAudio = audio?.surChangement(synchroniser);
	const quitterFin = lecteur?.surFin(() => terminerFichier(source));
	const quitterErreur = lecteur?.surErreur(() => echec(source, 'Le fichier de l’Instrumentale est illisible.'));
	ui.surRendu = origine => {
		const bloc = ui.bloc;
		if (!bloc) return;
		const { courant, grille } = bloc;
		if (dernierPassage !== courant?.etapeId) positionConnue = null;
		dernierPassage = courant?.etapeId;
		if (origine?.source === 'capture') recalerSurCapture();
		// Une fois le Passage clos, la salle renomme tous ses Interprètes et retire les Couplets.
		const couplets = ['EN_COURS', 'EN_PAUSE'].includes(courant?.etat) ? courant.couplets ?? null : null;
		const appele = courant?.etat === 'APPELE' ? courant : null;
		appelSalle.rendre(appele, { contenu: appele?.instrumentale.titre ?? '', enPause: bloc.etat === 'EN_PAUSE',
			maintenant: contexte.maintenantServeur() });
		const enScene = ['APPELE', 'EN_COURS', 'EN_PAUSE'].includes(courant?.etat);
		voteCentre = voteAuCentre(bloc.votes, enScene);
		voteSalle.rendre(voteCentre, { contenu: voteCentre?.instrumentale.titre ?? '', enPause: bloc.etat === 'EN_PAUSE',
			maintenant: contexte.maintenantServeur() });
		suivreDecompte(Boolean(appele || voteCentre) && bloc.etat !== 'EN_PAUSE');
		interprete.hidden = !courant || Boolean(appele || voteCentre);
		interprete.textContent = couplets ? couplets.courant.interprete.pseudo : pseudos(courant?.interpretes);
		for (const [element, texte] of [[ordreCouplets, texteOrdre(couplets)], [coupletCourant, texteCouplet(couplets)],
			[annonceSuivant, texteAnnonce(couplets)]]) {
			if (element.textContent !== texte) element.textContent = texte;
			element.hidden = !texte;
		}
		phase.textContent = bloc.etat === 'EN_PAUSE' ? 'Manche en pause' :
			({ APPELE: 'Monte sur scène', EN_COURS: courant?.instrumentale.titre, TERMINE: 'Passage terminé',
				SAUTE: 'Passage sauté' })[courant?.etat] ?? 'En attente d’un Passage';
		phase.hidden = Boolean(appele || voteCentre);
		rythme.hidden = !grille;
		if (grille) afficherPosition(positionConnue);
		votes.replaceChildren(...bloc.votes.filter(vote => vote.etat === 'OUVERT' && vote !== voteCentre).map(vote =>
			e('li', '', `${pseudos(vote.interpretes)} · ${formaterNombreVotes(vote.nombreVotes)}`)));
		titresVotes.hidden = !votes.childElementCount;
		file.replaceChildren(...bloc.passagesEnAttente.map(passage => {
			const ligne = e('li');
			ligne.append(creerTicket(ui, passage));
			return ligne;
		}));
		// « À venir » ne reste jamais vide sans rien dire (#283).
		autres.textContent = bloc.passagesEnAttente.length ? texteAutresPassages(bloc.nombreEnAttente - bloc.passagesEnAttente.length)
			: 'Personne n’est inscrit.';
		synchroniser();
	};
	ui.surDestruction = () => {
		suivreDecompte(false);
		arreter();
		sonDeManche = false;
		void contexte.signalerSonDeManche?.(false);
		quitterCatalogue?.();
		quitterAudio?.();
		quitterFin?.();
		quitterErreur?.();
	};
	return { afficher: ui.afficher, demonter: ui.demonter };
}
