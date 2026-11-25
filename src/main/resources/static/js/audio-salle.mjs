import { creerCoordinationSon } from './coordination-son.mjs';
import { creerLecteurDePassage } from './lecteur-de-passage.mjs';
import { creerMusiqueDAttente } from './musique-attente.mjs';
import { creerUuid } from './modes/karaoke/commun.mjs';
import { estFichierAudio, fichiersDeMusiqueDAttente } from './modes/karaoke/fichiers.mjs';
import { creerLecteurLocal } from './modes/karaoke/lecteur-local.mjs';

export function creerAudioSalle({ lecteur, sonde, mesureur, coordonner, lecteurId }) {
	const abonnes = new Set();
	let selectionLocale = null, fichiers = null, sonDebloque = false, suspendu = false, detruit = false;
	let mesures = Promise.resolve(null);
	const etat = () => ({ fichiers, sonDebloque });
	function notifier() { for (const rappel of abonnes) rappel(etat()); }
	const musique = creerMusiqueDAttente({ lecteur, surSonBloque() {
		sonDebloque = false;
		coordination.activer(false);
		notifier();
	} });
	const coordination = coordonner(musique.autoriserLecture);
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
			musique.choisirFichiers(fichiersDeMusiqueDAttente(fichiers));
			notifier();
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
