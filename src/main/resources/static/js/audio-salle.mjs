import { creerCoordinationSon } from './coordination-son.mjs';
import { creerLecteurDePassage } from './lecteur-de-passage.mjs';
import { creerMusiqueDAttente } from './musique-attente.mjs';
import { creerUuid } from './modes/karaoke/commun.mjs';
import { estFichierAudio, fichiersDeMusiqueDAttente } from './modes/karaoke/fichiers.mjs';
import { creerLecteurLocal } from './modes/karaoke/lecteur-local.mjs';

export function creerAudioSalle({ lecteur, sonde, coordonner, lecteurId }) {
	const abonnes = new Set();
	let fichiers = null, morceauxDisponibles = null, sonDebloque = false, suspendu = false, detruit = false;
	let musiqueAutorisee = false, attenteAutorisee = false;
	const etat = () => ({ fichiers, sonDebloque, morceauxDisponibles });
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
	} });
	function actualiserMusique() { musique.autoriserLecture(musiqueAutorisee && attenteAutorisee); }
	const coordination = coordonner(autorisee => {
		attenteAutorisee = autorisee;
		actualiserMusique();
	});
	return {
		lecteurId,
		lecteur: creerLecteurDePassage({ lecteur, sonde, attendreSonDeManche: coordination.attendreSonDeManche }),
		etat,
		surChangement(rappel) { abonnes.add(rappel); return () => abonnes.delete(rappel); },
		publierDisponibilite: coordination.publierDisponibilite,
		choisirDossier(selection) {
			fichiers = selection.filter(estFichierAudio);
			const morceaux = fichiersDeMusiqueDAttente(fichiers);
			morceauxDisponibles = morceaux.length > 0;
			musique.choisirFichiers(morceaux);
			coordination.annoncerMorceaux(morceauxDisponibles);
			actualiserCandidature();
			notifier();
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

function coordonnerDans(navigateur, id, soireeId, surAttente) {
	if (!navigateur.navigator.locks || !navigateur.BroadcastChannel) {
		// Sans Web Locks (page en http:// hors localhost), la fenêtre se croit seule : elle joue dès qu'elle candidate.
		return { activer: candidate => surAttente(candidate), signalerSonDeManche() {}, attendreSonDeManche: async () => true,
			annoncerMorceaux() {}, publierDisponibilite: (disponibles, signal, publier) => publier(), detruire() {} };
	}
	return creerCoordinationSon({ verrous: navigateur.navigator.locks,
		canal: new navigateur.BroadcastChannel('croustyquizz:son'),
		ouverteA: navigateur.performance.timeOrigin, id, soireeId, surAttente });
}

export function monterAudioSalle(zone, soireeId) {
	const document = zone.ownerDocument, navigateur = document.defaultView;
	const ressources = {
		document, creerUrl: fichier => navigateur.URL.createObjectURL(fichier), revoquerUrl: url => navigateur.URL.revokeObjectURL(url),
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
	choisir.addEventListener('click', () => { void audio.activerSon(); choix.click(); });
	activer.addEventListener('click', audio.activerSon);
	choix.addEventListener('change', () => {
		audio.choisirDossier(Array.from(choix.files));
		choix.value = '';
	});
	audio.surChangement(({ fichiers, sonDebloque }) => {
		const avaitFocus = document.activeElement === activer;
		alerte.hidden = activer.hidden = sonDebloque;
		if (avaitFocus && activer.hidden) choisir.focus();
		const nombre = fichiers?.length;
		aide.textContent = nombre === undefined ? '' : `${nombre} fichier${nombre > 1 ? 's' : ''} audio`;
	});
	zone.hidden = false;
	return audio;
}
