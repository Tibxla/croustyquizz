import { creerSocle, ecrire, rendreParties, textePhase } from './commun.mjs';
import { creerAudioExtrait, creerLecteurExtrait } from './lecteur-extrait.mjs';
import { extraitRestantMs, formaterSecondes, tempsRestantMs, texteNumero } from './logique.mjs';

/** Événements qui animent leur élément ; une capture restaure le même état sans rien rejouer. */
const ANIMATIONS = { 'buzz-recu': 'main', 'proposition-jugee': 'parties', 'morceau-revele': 'revelation' };

export function monter(zones, contexte) {
	const document = zones.principale.ownerDocument;
	return creerEcranBlindTest(zones, contexte, {
		audio: creerAudioExtrait(document), verrous: document.defaultView.navigator.locks,
	});
}

/**
 * Écran de salle du Blind test : l'extrait joué par une seule fenêtre, qui a la main, les parties trouvées et la
 * Révélation. Le son passe par la coquille, qui fait taire la Musique d'attente pendant la Manche.
 */
export function creerEcranBlindTest(zones, contexte, { audio, verrous, intervalle }) {
	const socle = creerSocle(zones, contexte, { classe: 'blindtest--salle', intervalle });
	const { e } = socle;
	const numero = e('p', 'blindtest__numero chiffres');
	const phase = e('h2', 'blindtest__phase');
	const main = e('p', 'blindtest__main');
	const temps = e('p', 'blindtest__temps chiffres');
	const revelation = e('figure', 'blindtest__revelation');
	const pochette = e('img', 'blindtest__pochette');
	const legende = e('figcaption');
	const revelationTitre = e('p', 'blindtest__revele-titre'), revelationArtiste = e('p', 'blindtest__revele-artiste');
	legende.append(revelationTitre, revelationArtiste);
	revelation.append(pochette, legende);
	const sonBloque = e('p', 'coupure', 'Le navigateur bloque le son : active-le avec le bouton de l’Écran de salle.');
	sonBloque.hidden = true;
	socle.principale.append(numero, phase, main, temps, revelation, sonBloque);

	const titreParties = e('h2', '', 'Trouvé');
	const parties = e('ul', 'blindtest__parties');
	(socle.laterale ?? socle.principale).append(titreParties, parties);

	const audioSalle = contexte.audioSalle;
	const lecteur = creerLecteurExtrait({ audio, verrous,
		signalerSonDeManche: actif => contexte.signalerSonDeManche(actif),
		sonActive: () => audioSalle?.etat().sonDebloque ?? true,
		maintenantServeur: () => contexte.maintenantServeur(),
		surBlocage: () => { sonBloque.hidden = false; },
		surLecture: () => { sonBloque.hidden = true; } });
	const retirerAudio = audioSalle?.surChangement(() => { void lecteur.relancer(); });
	let bloc, detruit = false;

	function rafraichirTemps() {
		if (!bloc) return;
		const maintenant = contexte.maintenantServeur();
		if (bloc.phase === 'EXTRAIT') ecrire(temps, formaterSecondes(extraitRestantMs(bloc.lecture, maintenant)));
		else if (bloc.phase === 'PROPOSITION') ecrire(temps, formaterSecondes(tempsRestantMs(bloc.main, maintenant)));
	}

	function animer(element, nom, origine) {
		const actif = origine?.source === 'evenement' && ANIMATIONS[origine.type] === nom;
		element.classList.toggle('blindtest--arrivee', actif);
	}

	function rendre(origine) {
		ecrire(numero, texteNumero(bloc));
		ecrire(phase, bloc.etat === 'EN_PAUSE' ? 'Pause' : textePhase(bloc));
		main.hidden = bloc.phase !== 'PROPOSITION';
		ecrire(main, bloc.main ? `${bloc.main.pseudo} a la main` : '');
		animer(main, 'main', origine);
		temps.hidden = bloc.phase !== 'EXTRAIT' && bloc.phase !== 'PROPOSITION';
		rafraichirTemps();
		const nouvelles = rendreParties(socle, parties, bloc.partiesTrouvees);
		titreParties.hidden = parties.hidden;
		if (nouvelles) animer(parties, 'parties', origine);
		revelation.hidden = !bloc.revelation;
		if (bloc.revelation) {
			ecrire(revelationTitre, bloc.revelation.titre);
			ecrire(revelationArtiste, bloc.revelation.artiste);
			const lien = bloc.revelation.pochette;
			pochette.hidden = !lien;
			if (lien && pochette.getAttribute('src') !== lien) pochette.setAttribute('src', lien);
			pochette.setAttribute('alt', `Pochette de ${bloc.revelation.titre}`);
		}
		animer(revelation, 'revelation', origine);
		const decompte = bloc.etat === 'EN_COURS' && (bloc.phase === 'PROPOSITION'
			|| (bloc.phase === 'EXTRAIT' && bloc.lecture?.etat === 'EN_LECTURE'));
		socle.animer(decompte, rafraichirTemps);
	}

	return {
		afficher(vue, origine) {
			if (detruit) return;
			bloc = vue.blindtest;
			rendre(origine);
			void lecteur.suivre(bloc);
		},
		demonter() {
			if (detruit) return;
			detruit = true;
			lecteur.detruire();
			retirerAudio?.();
			socle.detruire();
		},
	};
}
