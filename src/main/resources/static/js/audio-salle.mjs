import { creerCoordinationSon } from './coordination-son.mjs';
import { creerLecteurDePassage } from './lecteur-de-passage.mjs';
import { creerMusiqueDAttente } from './musique-attente.mjs';
import { CATALOGUE_VIDE, chargerBibliotheque, fusionnerMorceauxDAttente, morceauxDeLaBibliotheque, texteCredit } from './bibliotheque.mjs';
import { creerUuid } from './modes/karaoke/commun.mjs';
import { estFichierAudio } from './modes/karaoke/fichiers.mjs';
import { creerLecteurLocal } from './modes/karaoke/lecteur-local.mjs';

// `dossier` garde les fichiers du Lieu (null sans dossier). `fichiers` est ce que lisent les modules : null tant que la
// fenêtre n'est pas prête, [] quand « Activer le son » la rend prête avec les seules Chansons de la Bibliothèque de base.
export function creerAudioSalle({ lecteur, sonde, coordonner, lecteurId, bibliotheque: catalogue = CATALOGUE_VIDE }) {
	const abonnes = new Set();
	let fichiers = null, dossier = null, bibliotheque = [], chansonsLibres = false, morceauxDisponibles = null, morceauEnCours = null;
	let sonDebloque = false, suspendu = false, detruit = false, musiqueAutorisee = false, attenteAutorisee = false;
	const etat = () => ({ fichiers, dossier, bibliotheque: bibliotheque.length > 0 || chansonsLibres, sonDebloque,
		morceauxDisponibles, morceauEnCours });
	function lireCatalogue(nouveau) {
		bibliotheque = morceauxDeLaBibliotheque(nouveau, 'attente');
		chansonsLibres = nouveau.entrees.some(morceau => morceau.type === 'chanson');
		if (chansonsLibres && sonDebloque) fichiers ??= [];
	}
	function notifier() { for (const rappel of abonnes) rappel(etat()); }
	// Seule une fenêtre qui a des morceaux dans Attente candidate : sans eux, elle ferait taire celle qui en a.
	// Le son activé sert aussi au son de Manche, qui se passe de dossier.
	function actualiserCandidature() { coordination.activer(sonDebloque && !suspendu && morceauxDisponibles === true); }
	const musique = creerMusiqueDAttente({ lecteur, surSonBloque() {
		sonDebloque = false;
		actualiserCandidature();
		notifier();
	}, surMorceauxEpuises() {
		// Tous les morceaux d'Attente sont illisibles : comme un dossier sans Attente, jusqu'au prochain choix.
		morceauxDisponibles = false;
		coordination.annoncerMorceaux(false);
		actualiserCandidature();
		notifier();
	}, surMorceau(morceau) {
		morceauEnCours = morceau;
		notifier();
	} });
	// Sans dossier ni bibliothèque, la fenêtre ne sait rien de ses morceaux et ne signale rien.
	function actualiserMorceaux() {
		const morceaux = fusionnerMorceauxDAttente(bibliotheque, dossier ?? []);
		morceauxDisponibles = morceaux.length > 0 ? true : dossier === null ? null : false;
		musique.choisirFichiers(morceaux);
		if (morceauxDisponibles !== null) coordination.annoncerMorceaux(morceauxDisponibles);
		actualiserCandidature();
		notifier();
	}
	function actualiserMusique() { musique.autoriserLecture(musiqueAutorisee && attenteAutorisee); }
	const coordination = coordonner(autorisee => {
		attenteAutorisee = autorisee;
		actualiserMusique();
	});
	lireCatalogue(catalogue);
	if (bibliotheque.length) actualiserMorceaux();
	return {
		lecteurId,
		lecteur: creerLecteurDePassage({ lecteur, sonde, attendreSonDeManche: coordination.attendreSonDeManche }),
		etat,
		surChangement(rappel) { abonnes.add(rappel); return () => abonnes.delete(rappel); },
		attendreAucunMorceauAilleurs: signal => coordination.attendreAucunMorceauAilleurs(signal),
		choisirDossier(selection) {
			dossier = fichiers = selection.filter(estFichierAudio);
			actualiserMorceaux();
		},
		recevoirBibliotheque(nouveau) {
			if (detruit) return;
			lireCatalogue(nouveau);
			actualiserMorceaux();
		},
		reglerMusiqueDAttente(autorisee) {
			musiqueAutorisee = autorisee;
			actualiserMusique();
		},
		async activerSon() {
			if (sonDebloque || detruit) return;
			try { await lecteur.debloquer(); }
			catch { if (!detruit) notifier(); return; }
			if (detruit) return;
			sonDebloque = true;
			if (chansonsLibres) fichiers ??= [];
			actualiserCandidature();
			notifier();
		},
		signalerSonDeManche(actif) {
			coordination.signalerSonDeManche(actif);
			return actif ? coordination.attendreSonDeManche() : Promise.resolve(false);
		},
		suspendre(valeur) {
			suspendu = valeur;
			actualiserCandidature();
		},
		detruire() {
			detruit = true;
			musique.detruire();
			lecteur.detruire();
			abonnes.clear();
			return coordination.detruire();
		},
	};
}

function coordonnerDans(navigateur, id, surAttente) {
	if (!navigateur.navigator.locks || !navigateur.BroadcastChannel) {
		// Sans Web Locks (page en http:// hors localhost), la fenêtre se croit seule : elle joue dès qu'elle candidate.
		return { activer: candidate => surAttente(candidate), signalerSonDeManche() {}, attendreSonDeManche: async () => true,
			annoncerMorceaux() {}, attendreAucunMorceauAilleurs: async () => {}, detruire() {} };
	}
	return creerCoordinationSon({ verrous: navigateur.navigator.locks,
		canal: new navigateur.BroadcastChannel('croustyquizz:son'),
		ouverteA: navigateur.performance.timeOrigin, id, surAttente });
}

export function monterAudioSalle(zone, { bibliotheque = chargerBibliotheque(navigateurDe(zone).fetch?.bind(navigateurDe(zone))) } = {}) {
	const document = zone.ownerDocument, navigateur = document.defaultView;
	// Un morceau de la Bibliothèque de base se lit par son URL ; seul un fichier du Lieu passe par une URL blob.
	const ressources = {
		document, creerUrl: fichier => fichier.url ?? navigateur.URL.createObjectURL(fichier),
		revoquerUrl: url => { if (url.startsWith('blob:')) navigateur.URL.revokeObjectURL(url); },
	};
	const lecteurId = creerUuid(navigateur.crypto);
	const audio = creerAudioSalle({ lecteurId, lecteur: creerLecteurLocal(ressources),
		// Un élément séparé lit seulement les métadonnées pendant l’appel. Seul l’élément audio joue.
		sonde: creerLecteurLocal({ ...ressources, type: 'video' }),
		coordonner: surAttente => coordonnerDans(navigateur, lecteurId, surAttente) });
	const choisir = zone.querySelector('[data-choisir-dossier]');
	const choix = zone.querySelector('input');
	const activer = zone.querySelector('[data-activer-son]');
	const aide = zone.querySelector('[data-aide-son]');
	const alerte = zone.querySelector('[data-son-bloque]');
	const credit = zone.querySelector('[data-credit-salle]') ?? document.querySelector('[data-credit-salle]');
	choisir.addEventListener('click', () => { void audio.activerSon(); choix.click(); });
	activer.addEventListener('click', audio.activerSon);
	choix.addEventListener('change', () => {
		audio.choisirDossier(Array.from(choix.files));
		choix.value = '';
	});
	function afficher({ fichiers, dossier, bibliotheque: libre, sonDebloque, morceauEnCours }) {
		const avaitFocus = document.activeElement === activer;
		// « Activer le son » se propose dès l'ouverture : la bibliothèque n'attend que ce clic.
		activer.hidden = sonDebloque;
		alerte.hidden = sonDebloque || fichiers === null;
		if (avaitFocus && activer.hidden) choisir.focus();
		const nombre = dossier?.length;
		const lieu = nombre === undefined ? '' : `${nombre} fichier${nombre > 1 ? 's' : ''} audio`;
		aide.textContent = libre ? (lieu ? `${lieu} en plus de la Bibliothèque de base` : 'Bibliothèque de base') : lieu;
		if (credit) {
			credit.hidden = !morceauEnCours?.credit;
			const texte = credit.hidden ? '' : `Musique : ${texteCredit(morceauEnCours.credit)}`;
			if (credit.textContent !== texte) credit.textContent = texte;
		}
	}
	audio.surChangement(afficher);
	afficher(audio.etat());
	void Promise.resolve(bibliotheque).then(catalogue => audio.recevoirBibliotheque(catalogue));
	zone.hidden = false;
	return audio;
}

function navigateurDe(zone) { return zone.ownerDocument.defaultView; }
