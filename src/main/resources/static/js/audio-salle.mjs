import { creerCoordinationSon } from './coordination-son.mjs';
import { creerLecteurDePassage } from './lecteur-de-passage.mjs';
import { creerMusiqueDAttente } from './musique-attente.mjs';
import { creerUuid } from './modes/karaoke/commun.mjs';
import { estFichierAudio, fichiersDeMusiqueDAttente } from './modes/karaoke/fichiers.mjs';
import { creerLecteurLocal } from './modes/karaoke/lecteur-local.mjs';
import { decrireSon } from './salle-accueil.mjs';

export function creerAudioSalle({ lecteur, sonde, mesureur, coordonner, lecteurId }) {
	const abonnes = new Set();
	let selectionLocale = null, fichiers = null, morceauxDisponibles = null, sonDebloque = false, suspendu = false, detruit = false;
	let musiqueAutorisee = false, attenteAutorisee = false;
	let mesures = Promise.resolve(null);
	const etat = () => ({ fichiers, sonDebloque, morceauxDisponibles });
	function notifier() { for (const rappel of abonnes) rappel(etat()); }
	const musique = creerMusiqueDAttente({ lecteur, surSonBloque() {
		sonDebloque = false;
		coordination.activer(false);
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
		selection: () => selectionLocale,
		// Une mesure à la fois : un nouveau chargement libérerait le précédent sans durée.
		mesurerDuree(fichier) {
			const mesure = mesures.then(async () => {
				if (detruit || !mesureur) return null;
				try { return (await mesureur.charger(fichier)).dureeMs ?? null; }
				catch { return null; }
				finally { mesureur.detruire(); }
			});
			mesures = mesure;
			return mesure;
		},
		surChangement(rappel) { abonnes.add(rappel); return () => abonnes.delete(rappel); },
		choisirDossier(selection) {
			selectionLocale = selection;
			fichiers = selection.filter(estFichierAudio);
			const morceaux = fichiersDeMusiqueDAttente(fichiers);
			morceauxDisponibles = morceaux.length > 0;
			musique.choisirFichiers(morceaux);
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
			coordination.activer(!suspendu);
			notifier();
		},
		signalerSonDeManche(actif) {
			coordination.signalerSonDeManche(actif);
			return actif ? coordination.attendreSonDeManche() : Promise.resolve(false);
		},
		suspendre(valeur) {
			suspendu = valeur;
			coordination.activer(sonDebloque && !suspendu);
		},
		detruire() {
			detruit = true;
			musique.detruire();
			lecteur.detruire();
			mesureur?.detruire();
			abonnes.clear();
			return coordination.detruire();
		},
	};
}

function coordonnerDans(navigateur, id, surAttente) {
	if (!navigateur.navigator.locks || !navigateur.BroadcastChannel) {
		return { activer() {}, signalerSonDeManche() {}, attendreSonDeManche: async () => true, detruire() {} };
	}
	return creerCoordinationSon({ verrous: navigateur.navigator.locks,
		canal: new navigateur.BroadcastChannel('croustyquizz:son'),
		ouverteA: navigateur.performance.timeOrigin, id, surAttente });
}

export function monterAudioSalle(zone) {
	const document = zone.ownerDocument, navigateur = document.defaultView;
	const ressources = {
		document, creerUrl: fichier => navigateur.URL.createObjectURL(fichier), revoquerUrl: url => navigateur.URL.revokeObjectURL(url),
	};
	const lecteurId = creerUuid(navigateur.crypto);
	const audio = creerAudioSalle({ lecteurId, lecteur: creerLecteurLocal(ressources),
		// Un élément séparé lit seulement les métadonnées pendant l’appel. Seul l’élément audio joue.
		sonde: creerLecteurLocal({ ...ressources, type: 'video' }),
		// Le catalogue du Freestyle mesure ses fichiers sur un élément à part, sans gêner la préparation d’un Passage.
		mesureur: creerLecteurLocal({ ...ressources, type: 'video' }),
		coordonner: surAttente => coordonnerDans(navigateur, lecteurId, surAttente) });
	const choisir = zone.querySelector('[data-choisir-dossier]');
	const choix = zone.querySelector('input');
	const activer = zone.querySelector('[data-activer-son]');
	const aide = zone.querySelector('[data-aide-son]');
	const pastille = zone.querySelector('[data-son-etat]');
	choisir.addEventListener('click', () => { void audio.activerSon(); choix.click(); });
	activer.addEventListener('click', audio.activerSon);
	choix.addEventListener('change', () => {
		audio.choisirDossier(Array.from(choix.files));
		choix.value = '';
	});
	// Le panneau d'installation reste tant que le son n'est pas activé, puis laisse une pastille dans le pied (#281).
	function rendre(etat) {
		const { etat: etape, pastille: texte } = decrireSon(etat);
		const avaitFocus = zone.contains(document.activeElement);
		zone.dataset.etat = etape;
		zone.dataset.dossier = etat.fichiers?.length ? 'choisi' : 'absent';
		activer.hidden = etat.sonDebloque;
		if (pastille && pastille.textContent !== texte) pastille.textContent = texte;
		const nombre = etat.fichiers?.length;
		aide.textContent = nombre === undefined || etape === 'pret' ? '' : `${nombre} morceau${nombre > 1 ? 'x' : ''} trouvé${nombre > 1 ? 's' : ''}`;
		if (avaitFocus && etape === 'pret') choisir.focus();
	}
	audio.surChangement(rendre);
	rendre(audio.etat());
	zone.hidden = false;
	return audio;
}
