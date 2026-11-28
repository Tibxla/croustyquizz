import { creerSocle, ecrire, rendreParties, textePhase } from './commun.mjs';
import { creerAudioExtrait, creerLecteurExtrait } from './lecteur-extrait.mjs';
import { extraitJouable, extraitRestantMs, formaterSecondes, tempsRestantMs, texteArbitrage, texteNumero }
	from './logique.mjs';

/** Événements qui animent leur élément ; une capture restaure le même état sans rien rejouer. */
const ANIMATIONS = { 'buzz-recu': 'main', 'proposition-jugee': 'parties', 'morceau-revele': 'revelation',
	'morceau-saute': 'annonce', 'verdict-renverse': 'arbitrage' };

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
	// Reçoit le focus quand « Lancer l'extrait » disparaît sous lui.
	phase.setAttribute('tabindex', '-1');
	const main = e('p', 'blindtest__main');
	const temps = e('p', 'blindtest__temps chiffres');
	const revelation = e('figure', 'blindtest__revelation');
	const pochette = e('img', 'blindtest__pochette');
	const legende = e('figcaption');
	const revelationTitre = e('p', 'blindtest__revele-titre'), revelationArtiste = e('p', 'blindtest__revele-artiste');
	legende.append(revelationTitre, revelationArtiste);
	revelation.append(pochette, legende);
	// ADR 0009, point 14 : un Morceau sans extrait est sauté, et la salle le dit brièvement.
	const annonce = e('p', 'blindtest__annonce', 'Extrait indisponible : ce Morceau est sauté.');
	annonce.hidden = true;
	// ADR 0009, point 15 : le dernier verdict renversé par l'Animateur, jusqu'au Morceau suivant.
	const arbitrage = e('p', 'blindtest__arbitrage');
	arbitrage.setAttribute('role', 'status');
	arbitrage.hidden = true;
	// Pourquoi l'extrait est muet. « Lancer l'extrait » rejoue pendant le geste, quand le navigateur bloque le son.
	const son = e('div', 'blindtest__son');
	const sonTexte = e('p', 'coupure');
	const lancer = e('button', 'bouton bouton--action', 'Lancer l’extrait');
	lancer.type = 'button';
	son.append(sonTexte, lancer);
	son.hidden = true;
	socle.principale.append(numero, phase, annonce, arbitrage, main, temps, revelation, son);

	const titreParties = e('h2', '', 'Trouvé');
	const parties = e('ul', 'blindtest__parties');
	(socle.laterale ?? socle.principale).append(titreParties, parties);

	const audioSalle = contexte.audioSalle;
	/** null, ou ce qui empêche l'extrait de jouer : 'bloque' (le navigateur attend un geste) ou 'illisible'. */
	let bloc, detruit = false, incident = null, etapeIncident, etapeSautee;
	const signaler = valeur => () => { incident = valeur; etapeIncident = bloc?.etapeId; rendreSon(); };
	const lecteur = creerLecteurExtrait({ audio, verrous,
		signalerSonDeManche: actif => contexte.signalerSonDeManche(actif),
		sonActive: () => audioSalle?.etat().sonDebloque ?? true,
		maintenantServeur: () => contexte.maintenantServeur(),
		surBlocage: signaler('bloque'), surIllisible: signaler('illisible'), surLecture: signaler(null) });
	const retirerAudio = audioSalle?.surChangement(() => { incident = null; rendreSon(); void lecteur.relancer(); });
	socle.ecouter(lancer, 'click', () => { void lecteur.lancerParGeste(); });

	function rendreSon() {
		if (!bloc || detruit) return;
		if (etapeIncident !== bloc.etapeId) incident = null;
		const inactif = audioSalle ? !audioSalle.etat().sonDebloque : false;
		const texte = !extraitJouable(bloc) ? ''
			: inactif ? 'Son coupé : clique sur « Activer le son » pour entendre l’extrait.'
				: incident === 'bloque' ? 'Le navigateur bloque le son de l’extrait.'
					: incident === 'illisible' ? 'L’extrait ne peut pas être lu.' : '';
		ecrire(sonTexte, texte);
		const avaitFocus = socle.document.activeElement === lancer;
		lancer.hidden = !texte || inactif || incident !== 'bloque';
		son.hidden = !texte;
		if (avaitFocus && lancer.hidden) phase.focus();
	}

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
		// Le saut n'est connu que par son événement : l'annonce reste pendant la préparation du Morceau suivant.
		if (origine?.source === 'evenement' && origine.type === 'morceau-saute') etapeSautee = bloc.etapeId;
		annonce.hidden = etapeSautee !== bloc.etapeId || bloc.phase !== 'PREPARATION';
		animer(annonce, 'annonce', origine);
		ecrire(arbitrage, texteArbitrage(bloc.arbitrage));
		arbitrage.hidden = !bloc.arbitrage;
		animer(arbitrage, 'arbitrage', origine);
		main.hidden = bloc.phase !== 'PROPOSITION';
		ecrire(main, bloc.main ? `${bloc.main.pseudo} a la main` : '');
		animer(main, 'main', origine);
		temps.hidden = bloc.phase !== 'EXTRAIT' && bloc.phase !== 'PROPOSITION';
		rafraichirTemps();
		const nouvelles = rendreParties(socle, parties, bloc.partiesTrouvees);
		titreParties.hidden = parties.hidden;
		// Une capture retire l'animation en cours ; une vue sans nouvelle partie ne la relance pas.
		if (nouvelles || origine?.source !== 'evenement') animer(parties, 'parties', origine);
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
		rendreSon();
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
