import { creerCoordinationSon } from './coordination-son.mjs';
import { creerLecteurDePassage } from './lecteur-de-passage.mjs';
import { creerMusiqueDAttente } from './musique-attente.mjs';
import { CATALOGUE_VIDE, chargerBibliotheque, fusionnerMorceauxDAttente, morceauxDeLaBibliotheque, partiesDuCredit } from './bibliotheque.mjs';
import { creerUuid } from './uuid.mjs';
import { estFichierAudio } from './modes/karaoke/fichiers.mjs';
import { creerLecteurLocal } from './modes/karaoke/lecteur-local.mjs';

// `dossier` garde les fichiers du Lieu (null sans dossier). `fichiers` est ce que lisent les modules : null tant que la
// fenêtre n'est pas prête, [] quand « Activer le son » la rend prête avec les seules Chansons de la Bibliothèque de base.
export function creerAudioSalle({ lecteur, sonde, coordonner, lecteurId, bibliotheque: catalogue = CATALOGUE_VIDE }) {
	const abonnes = new Set();
	const morceauxIllisibles = new Set();
	let fichiers = null, dossier = null, bibliotheque = [], chansonsLibres = false, morceauxDisponibles = null, morceauEnCours = null;
	let morceauxDuLieu = false, creditDeManche = null;
	let sonDebloque = false, suspendu = false, detruit = false, musiqueAutorisee = false, attenteAutorisee = false;
	const etat = () => ({ fichiers, dossier, bibliotheque: bibliotheque.length > 0 || chansonsLibres, sonDebloque,
		morceauxDisponibles, morceauxDuLieu, morceauEnCours, creditDeManche });
	function lireCatalogue(nouveau) {
		for (const morceau of bibliotheque) morceauxIllisibles.delete(morceau);
		bibliotheque = morceauxDeLaBibliotheque(nouveau, 'attente');
		chansonsLibres = nouveau.entrees.some(morceau => morceau.type === 'chanson');
		if (chansonsLibres && sonDebloque) fichiers ??= [];
	}
	function notifier() { for (const rappel of abonnes) rappel(etat()); }
	// Seule une fenêtre qui a des morceaux dans Attente candidate : sans eux, elle ferait taire celle qui en a.
	// Le son activé sert aussi au son de Manche, qui se passe de dossier.
	// Une fenêtre qui n'a que la bibliothèque candidate aussi, mais derrière celles qui ont des morceaux du Lieu.
	function actualiserCandidature() {
		coordination.activer(sonDebloque && !suspendu && morceauxDisponibles === true, morceauxDuLieu);
	}
	const musique = creerMusiqueDAttente({ lecteur, surSonBloque() {
		sonDebloque = false;
		actualiserCandidature();
		notifier();
	}, surMorceauEcarte(morceau, nombreRestants) {
		morceauxIllisibles.add(morceau);
		actualiserMorceaux(nombreRestants);
	}, surMorceau(morceau) {
		morceauEnCours = morceau;
		notifier();
	} });
	// Sans dossier ni bibliothèque, la fenêtre ne sait rien de ses morceaux et ne signale rien.
	function actualiserMorceaux(nombreRestants) {
		const lisible = morceau => !morceauxIllisibles.has(morceau);
		const morceaux = fusionnerMorceauxDAttente(bibliotheque.filter(lisible), (dossier ?? []).filter(lisible));
		morceauxDuLieu = morceaux.some(morceau => !morceau.url);
		morceauxDisponibles = dossier !== null || bibliotheque.length > 0 ? morceaux.length > 0 : null;
		// Après un échec, ne recharger la liste que si un homonyme de la bibliothèque y revient.
		if (morceaux.length !== nombreRestants) {
			musique.choisirFichiers(morceaux, { conserverErreurs: nombreRestants !== undefined });
		}
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
		publierDisponibilite: coordination.publierDisponibilite,
		attendreDisparitionLecteur: coordination.attendreDisparitionLecteur,
		memoriserDesignation: coordination.memoriserDesignation,
		choisirDossier(selection) {
			morceauxIllisibles.clear();
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
		// Crédit du son de Manche (une Chanson de la Bibliothèque de base) : il prend la place de celui de la Musique
		// d'attente dans le pied tant que le module le maintient, et null le lui rend (ADR 0011).
		crediterSonDeManche(credit) {
			const nouveau = credit ? { nature: credit.nature, titre: credit.titre, artiste: credit.artiste, licence: credit.licence } : null;
			if (JSON.stringify(nouveau) === JSON.stringify(creditDeManche)) return;
			creditDeManche = nouveau;
			notifier();
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

function coordonnerDans(navigateur, id, soireeId, surAttente) {
	if (!navigateur.navigator.locks || !navigateur.BroadcastChannel) {
		// Sans Web Locks (page en http:// hors localhost), la fenêtre se croit seule : elle joue dès qu'elle candidate.
		return { activer: candidate => surAttente(candidate), signalerSonDeManche() {}, attendreSonDeManche: async () => true,
			annoncerMorceaux() {}, publierDisponibilite: (disponibles, signal, publier) => publier(), detruire() {} };
	}
	const canal = `croustyquizz:son:${soireeId.toLowerCase()}`;
	const cleLecteur = `${canal}:lecteur`;
	const memoireLecteur = {
		lire() { try { return navigateur.sessionStorage.getItem(cleLecteur); } catch { return null; } },
		retenir(id) { try { navigateur.sessionStorage.setItem(cleLecteur, id); } catch { /* Stockage indisponible. */ } },
	};
	return creerCoordinationSon({ verrous: navigateur.navigator.locks,
		canal: new navigateur.BroadcastChannel(canal),
		ouverteA: navigateur.performance.timeOrigin, id, soireeId, surAttente, memoireLecteur });
}

export function monterAudioSalle(zone, soireeId, { bibliotheque = chargerBibliotheque(navigateurDe(zone).fetch?.bind(navigateurDe(zone))) } = {}) {
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
		coordonner: surAttente => coordonnerDans(navigateur, lecteurId, soireeId, surAttente) });
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
	function afficher({ fichiers, dossier, bibliotheque: libre, sonDebloque, morceauxDuLieu, morceauEnCours, creditDeManche }) {
		const avaitFocus = document.activeElement === activer;
		// « Activer le son » se propose dès l'ouverture, pour la Bibliothèque de base et le Blind test.
		activer.hidden = sonDebloque;
		alerte.hidden = sonDebloque || fichiers === null;
		if (avaitFocus && activer.hidden) choisir.focus();
		const nombre = dossier?.length;
		const lieu = nombre === undefined ? '' : `${nombre} fichier${nombre > 1 ? 's' : ''} audio`;
		let texteAide = libre ? (lieu ? `${lieu} en plus de la Bibliothèque de base` : 'Bibliothèque de base') : lieu;
		if (dossier !== null && !morceauxDuLieu) {
			texteAide = libre ? 'Aucun morceau lisible dans Attente du dossier · Bibliothèque de base' : 'Aucun morceau lisible dans Attente';
		}
		if (aide.textContent !== texteAide) aide.textContent = texteAide;
		if (credit) {
			const affiche = creditDeManche ?? (morceauEnCours?.credit ? { nature: 'Musique', ...morceauEnCours.credit } : null);
			const parties = affiche ? partiesDuCredit(affiche) : [];
			credit.hidden = !affiche;
			if (credit.textContent !== parties.join('')) {
				credit.replaceChildren(...parties.map((texte, i) => {
					const partie = document.createElement('span');
					partie.className = i ? 'salle__credit-licence' : 'salle__credit-titre';
					partie.textContent = texte;
					return partie;
				}));
			}
		}
	}
	audio.surChangement(afficher);
	afficher(audio.etat());
	void Promise.resolve(bibliotheque).then(catalogue => audio.recevoirBibliotheque(catalogue));
	zone.hidden = false;
	return audio;
}

function navigateurDe(zone) { return zone.ownerDocument.defaultView; }
