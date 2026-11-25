import { ouvrirDialogue } from '../../dialogue.mjs';
import { creerUuid } from '../karaoke/commun.mjs';
import { creerSocle, ecrire, rendreParties, textePhase } from './commun.mjs';
import { extraitRestantMs, formaterSecondes, tempsRestantMs, texteNumero } from './logique.mjs';

/**
 * Console du Blind test : le pilotage de la Manche, la réponse attendue pour l'arbitrage, l'état du Morceau, qui a la
 * main et les parties trouvées. La coquille ne porte que les commandes de Soirée : chaque module pilote sa Manche.
 */
export function monter(zones, contexte, dependances = {}) {
	const socle = creerSocle(zones, contexte, { classe: 'blindtest--console', intervalle: dependances.intervalle });
	const { e } = socle;
	const creerCle = dependances.creerUuid ?? (() => creerUuid());

	// Pilotage de la Manche : chaque commande nomme le Morceau affiché (contrat, CommandeManche).
	const pilotage = e('div', 'blindtest__pilotage');
	pilotage.setAttribute('role', 'group');
	pilotage.setAttribute('aria-label', 'Pilotage de la Manche');
	const pause = bouton('Pause', () => piloter(bloc.etat === 'EN_PAUSE' ? 'REPRENDRE' : 'METTRE_EN_PAUSE'));
	const terminer = bouton('Terminer la Manche', () => ouvrirDialogue(dialogue, terminer));
	const passer = bouton('Révéler le Morceau', () => piloter('PASSER_ETAPE'), 'bouton bouton--action');
	pilotage.append(pause, terminer, passer);
	const dialogue = e('dialog', 'dialogue');
	const titreDialogue = e('h3', '', 'Terminer la Manche ?');
	titreDialogue.id = 'blindtest-titre-fin';
	dialogue.setAttribute('aria-labelledby', titreDialogue.id);
	const explication = e('p', '', 'Les Morceaux restants ne seront pas joués. Les points déjà gagnés restent acquis.');
	const annuler = bouton('Annuler', () => dialogue.close());
	annuler.setAttribute('autofocus', '');
	const confirmer = bouton('Terminer la Manche', () => { dialogue.close(); piloter('TERMINER'); }, 'bouton bouton--action');
	dialogue.append(titreDialogue, explication, annuler, confirmer);

	const entete = e('div', 'blindtest__entete');
	const numero = e('p', 'blindtest__numero chiffres');
	const phase = e('h2', 'blindtest__phase');
	entete.append(numero, phase);

	const reponse = e('section', 'panneau blindtest__reponse');
	const titreReponse = e('h3', '', 'Réponse attendue');
	const reponseTitre = e('p', 'blindtest__revele-titre'), reponseArtiste = e('p', 'blindtest__revele-artiste');
	reponse.append(titreReponse, reponseTitre, reponseArtiste);

	const morceau = e('section', 'panneau blindtest__etat');
	const titreEtat = e('h3', '', 'Morceau en cours');
	const lecture = e('p', 'blindtest__lecture');
	const main = e('p', 'blindtest__main');
	const temps = e('p', 'blindtest__temps chiffres');
	const parties = e('ul', 'blindtest__parties');
	morceau.append(titreEtat, lecture, main, temps, parties);
	socle.principale.append(pilotage, entete, reponse, morceau, dialogue);

	const bloques = e('section', 'panneau blindtest__bloques');
	const titreBloques = e('h3', '', 'Joueurs bloqués');
	const listeBloques = e('ul');
	const aucun = e('p', '', 'Aucun Joueur bloqué sur ce Morceau.');
	bloques.append(titreBloques, listeBloques, aucun);
	(socle.laterale ?? socle.principale).append(bloques);

	let vue, bloc, detruit = false, envoi, reessai;

	function bouton(texte, rappel, classe = 'bouton') {
		const b = e('button', classe, texte);
		b.type = 'button';
		socle.ecouter(b, 'click', () => { if (!b.disabled) rappel(); });
		return b;
	}

	/**
	 * Une commande à la fois. Après une confirmation inconnue, le même geste sur le même Morceau reprend la même clé
	 * d'idempotence ; rien n'est réessayé tout seul. La coquille affiche l'envoi et son reçu.
	 */
	async function piloter(action) {
		if (envoi || !socle.synchronisee || !bloc) return;
		const etapeId = bloc.etapeId;
		const cle = reessai?.action === action && reessai.etapeId === etapeId ? reessai.cle : creerCle();
		reessai = undefined;
		envoi = new AbortController();
		rendre();
		try {
			const { statut, corps } = await contexte.envoyer('POST',
				`/api/soirees/${encodeURIComponent(contexte.soireeId)}/manches/${encodeURIComponent(bloc.mancheId)}/commandes`,
				{ action, etapeId }, { entetes: { 'Idempotency-Key': cle }, signal: envoi.signal });
			if (statut === 503 || (statut >= 500 && !corps?.code)) {
				reessai = { action, etapeId, cle };
				contexte.resynchroniser();
			}
		} catch (erreur) {
			if (erreur.name !== 'AbortError') {
				reessai = { action, etapeId, cle };
				contexte.resynchroniser();
			}
		} finally {
			envoi = undefined;
			if (!detruit) rendre();
		}
	}

	function texteAvancer() {
		if (bloc.phase !== 'REVELATION') return 'Révéler le Morceau';
		return bloc.numero < bloc.total ? 'Morceau suivant' : 'Finir la Manche';
	}

	function rendrePilotage() {
		const bloque = !socle.synchronisee || Boolean(envoi);
		const enPause = bloc.etat === 'EN_PAUSE';
		ecrire(pause, enPause ? 'Reprendre' : 'Pause');
		ecrire(passer, texteAvancer());
		pause.disabled = terminer.disabled = confirmer.disabled = bloque;
		// Le serveur refuse de passer l'étape pendant la pause.
		passer.disabled = bloque || enPause;
	}

	function texteLecture() {
		if (bloc.phase === 'PREPARATION') return 'Lien d’extrait demandé au catalogue.';
		if (bloc.phase === 'REVELATION') return 'Morceau révélé.';
		const position = formaterSecondes(30000 - extraitRestantMs(bloc.lecture, contexte.maintenantServeur()));
		return bloc.lecture?.etat === 'EN_LECTURE' ? `Extrait en lecture, ${position} sur 30 s`
			: `Extrait en pause à ${position}`;
	}

	function rafraichirTemps() {
		if (!bloc) return;
		ecrire(lecture, texteLecture());
		ecrire(temps, bloc.main ? `${formaterSecondes(tempsRestantMs(bloc.main, contexte.maintenantServeur()))} pour répondre` : '');
	}

	function rendre() {
		if (!bloc || detruit) return;
		rendrePilotage();
		ecrire(numero, texteNumero(bloc));
		ecrire(phase, textePhase(bloc));
		ecrire(reponseTitre, bloc.reponse.titre);
		ecrire(reponseArtiste, bloc.reponse.artiste);
		main.hidden = temps.hidden = !bloc.main;
		ecrire(main, bloc.main ? `${bloc.main.pseudo} a la main` : '');
		rafraichirTemps();
		rendreParties(socle, parties, bloc.partiesTrouvees);
		const pseudos = new Map((vue.joueurs ?? []).map(joueur => [joueur.id, joueur.pseudo]));
		const noms = bloc.joueursBloques.map(id => pseudos.get(id) ?? 'Joueur parti');
		const actuels = Array.from(listeBloques.children, enfant => enfant.textContent);
		if (noms.length !== actuels.length || noms.some((nom, i) => nom !== actuels[i])) {
			listeBloques.replaceChildren(...noms.map(nom => e('li', '', nom)));
		}
		listeBloques.hidden = noms.length === 0;
		aucun.hidden = noms.length > 0;
		const decompte = bloc.etat === 'EN_COURS' && (bloc.phase === 'PROPOSITION'
			|| (bloc.phase === 'EXTRAIT' && bloc.lecture?.etat === 'EN_LECTURE'));
		socle.animer(decompte, rafraichirTemps);
	}

	socle.surConnexion = () => rendre();

	return {
		afficher(nouvelle) {
			if (detruit) return;
			vue = nouvelle;
			if (bloc && bloc.etapeId !== nouvelle.blindtest.etapeId) reessai = undefined;
			bloc = nouvelle.blindtest;
			rendre();
		},
		demonter() {
			if (detruit) return;
			detruit = true;
			envoi?.abort();
			if (dialogue.open) dialogue.close();
			socle.detruire();
		},
	};
}
