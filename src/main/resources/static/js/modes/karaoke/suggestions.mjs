import { estInactif, marquerInactif, texteEnvoi } from './commun.mjs';
import { peutReessayer } from './envois.mjs';

const LONGUEUR_MAX = 120;
const longueur = texte => [...texte].length;
// String.strip() de Java retire aussi U+001C à U+001F, qui ne sont pas des blancs Unicode.
const sansExtremites = texte => texte.replace(/^[ \u001C-\u001F]+|[ \u001C-\u001F]+$/g, '');

/**
 * Anticipe CorpsKaraoke.verifierChampSuggestion, qui fait foi : même nettoyage (NFC, blancs Unicode regroupés,
 * extrémités retirées), mêmes caractères interdits (contrôles, format Cf, demi-surrogates) et même clé vide.
 * Rend le message à afficher sous le champ, ou une chaîne vide.
 *
 * Le serveur fait autorité. Les classes Unicode (Cf, Cs, P, M, White_Space) et les tables NFC et NFKC suivent
 * la version Unicode du navigateur, qui peut différer de celle de Java 21 : un caractère trop récent pour l'un
 * des deux peut être refusé ici et accepté par le serveur, ou l'inverse (le 400 du serveur prend alors le relais).
 * Cet écart est accepté : il ne touche que des caractères rares, et le cas courant reste vérifié avant l'envoi.
 */
export function erreurChampSuggestion(texte) {
	if (longueur(texte) > LONGUEUR_MAX) return `Utilise au plus ${LONGUEUR_MAX} caractères.`;
	const nettoye = sansExtremites(texte.normalize('NFC').replace(/\p{White_Space}+/gu, ' '));
	if (!nettoye) return 'Renseigne ce champ.';
	// Sous la limite à la saisie, au-delà une fois nettoyé : seul NFC allonge un texte (U+0958 donne deux points de code, U+FB2C trois).
	if (longueur(nettoye) > LONGUEUR_MAX) return `Certains caractères comptent pour plusieurs : raccourcis ce texte à ${LONGUEUR_MAX} caractères au plus.`;
	if (/[\p{Cc}\p{Cf}\p{Cs}]/u.test(nettoye)) return 'Retire les caractères invisibles ou de contrôle.';
	// Clé de regroupement du serveur (normaliserSuggestion) : sans marques, ponctuation ni blancs, il ne reste rien.
	const cle = nettoye.normalize('NFKC').normalize('NFD').replace(/[\p{M}\p{P}\p{White_Space}]+/gu, '');
	if (!sansExtremites(cle)) return 'Ajoute au moins une lettre ou un chiffre.';
	return '';
}

export function installerSuggestionManette(ui) {
	const { e, bouton, ecouter } = ui;
	const formulaire = e('form', 'champ karaoke__suggestion');
	formulaire.id = 'karaoke-suggestion';
	formulaire.noValidate = true;
	const ouvrir = bouton('Suggérer une Chanson', () => {
		afficherFormulaire(formulaire.hidden);
		if (!formulaire.hidden) champs[0].saisie.focus();
	}, 'bouton bouton--lien');
	ouvrir.setAttribute('aria-controls', formulaire.id);

	function afficherFormulaire(visible) {
		formulaire.hidden = !visible;
		ouvrir.setAttribute('aria-expanded', String(visible));
	}
	let composition = false;
	const champs = [['titre', 'Titre de la Chanson'], ['artiste', 'Artiste']].map(([nom, libelle]) => {
		const etiquette = e('label', '', libelle), saisie = e('input'), erreur = e('p', 'champ--erreur');
		saisie.id = `karaoke-suggestion-${nom}`;
		saisie.name = nom;
		saisie.required = true;
		erreur.id = `${saisie.id}-erreur`;
		etiquette.setAttribute('for', saisie.id);
		saisie.setAttribute('aria-describedby', erreur.id);
		ecouter(saisie, 'input', () => indiquerErreur(saisie, erreur, ''));
		// Comme le catalogue : la touche Entrée qui valide une composition IME ne soumet pas le formulaire.
		ecouter(saisie, 'compositionstart', () => { composition = true; });
		ecouter(saisie, 'compositionend', () => { composition = false; });
		formulaire.append(etiquette, saisie, erreur);
		return { nom, saisie, erreur };
	});

	const envoyer = e('button', 'bouton', 'Envoyer la suggestion');
	envoyer.type = 'submit';
	const statut = e('p'); statut.setAttribute('role', 'status');
	const envoi = () => ui.dernierEnvoi(fiche => fiche.commande.type === 'SUGGERER_CHANSON');
	const reessayer = bouton('Réessayer l’envoi de la suggestion', () => ui.reessayer(envoi()?.cle));
	formulaire.append(envoyer, statut, reessayer);
	ui.principale.append(ouvrir, formulaire);
	let dernierRecu;

	function indiquerErreur(saisie, erreur, message) {
		erreur.hidden = !message;
		erreur.textContent = message;
		if (message) saisie.setAttribute('aria-invalid', 'true');
		else saisie.removeAttribute('aria-invalid');
	}

	function reinitialiser() {
		afficherFormulaire(false);
		statut.textContent = '';
		for (const { saisie, erreur } of champs) {
			saisie.value = '';
			indiquerErreur(saisie, erreur, '');
		}
		dernierRecu = undefined;
	}
	reinitialiser();

	ecouter(formulaire, 'submit', evenement => {
		evenement.preventDefault();
		if (composition || formulaire.hidden || estInactif(envoyer)) return;
		const corps = {}, invalides = [];
		for (const { nom, saisie, erreur } of champs) {
			const message = erreurChampSuggestion(saisie.value);
			indiquerErreur(saisie, erreur, message);
			if (message) invalides.push(saisie);
			corps[nom] = saisie.value;
		}
		if (invalides.length) { invalides[0].focus(); return; }
		ui.commande({ type: 'SUGGERER_CHANSON', methode: 'POST', chemin: `${ui.racine()}/suggestions`,
			corps, mancheId: ui.bloc.mancheId, acteurType: 'JOUEUR' });
	});

	return {
		rendre() {
			const recu = envoi();
			const occupe = recu?.etat === 'en-cours' || Boolean(peutReessayer(recu));
			ouvrir.hidden = !ui.bloc.peutRechercher;
			if (ouvrir.hidden) afficherFormulaire(false);
			marquerInactif(envoyer, !ui.mutationsPermises || !ui.bloc.peutRechercher || occupe);
			for (const { saisie } of champs) saisie.readOnly = occupe;
			reessayer.hidden = !ui.reessaiPossible(recu);
			const texte = recu?.etat === 'accepte' ? 'Suggestion envoyée.' : texteEnvoi(recu);
			if (statut.textContent !== texte) statut.textContent = texte;
			if (recu?.etat === 'accepte' && recu.cle !== dernierRecu) {
				dernierRecu = recu.cle;
				for (const { saisie } of champs) saisie.value = '';
			}
		},
		reinitialiser,
	};
}

export function installerSuggestionsConsole(ui) {
	const { e, bouton } = ui;
	const section = e('section', 'karaoke__suggestions');
	const titre = e('h2', '', 'Suggestions de Chansons'); titre.tabIndex = -1;
	const liste = e('ul', 'karaoke__resultats');
	const vide = e('p', '', 'Aucune suggestion.');
	const statut = e('p'); statut.setAttribute('role', 'status');
	const envoi = () => ui.dernierEnvoi(fiche => fiche.commande.type === 'RETIRER_SUGGESTION_CHANSON');
	const LIBELLE_REESSAI = 'Réessayer le retrait de la suggestion';
	const reessayer = bouton(LIBELLE_REESSAI, () => ui.reessayer(envoi()?.cle));
	section.append(titre, liste, vide, statut, reessayer);
	ui.laterale.append(section);
	let signature;

	function ligne(suggestion) {
		const { suggestionId, titre, artiste, nombreJoueurs } = suggestion;
		const ligne = e('li');
		// Titre et artiste restent locaux : seul le corps part en HTTP, et le groupe peut quitter la vue avant le réessai.
		const retirer = bouton('Retirer', () => {
			if (estInactif(retirer)) return;
			ui.commande({
				type: 'RETIRER_SUGGESTION_CHANSON', methode: 'POST', acteurType: 'ANIMATEUR',
				chemin: `${ui.racine()}/suggestions/${encodeURIComponent(suggestionId)}/retrait`,
				mancheId: ui.bloc.mancheId, corps: { nombreJoueurs }, titre, artiste,
			});
		});
		retirer.setAttribute('data-suggestion-id', suggestionId);
		retirer.setAttribute('aria-label', `Retirer la suggestion ${titre} · ${artiste}`);
		ligne.append(e('strong', '', titre), e('p', 'artiste', artiste),
			e('p', '', `${nombreJoueurs} ${nombreJoueurs === 1 ? 'Joueur' : 'Joueurs'}`), retirer);
		return ligne;
	}

	return {
		rendre() {
			const suggestions = ui.bloc.suggestions ?? [];
			const nouvelles = JSON.stringify(suggestions);
			if (nouvelles !== signature) {
				signature = nouvelles;
				ui.remplacerListe(liste, suggestions.map(ligne), titre);
			}
			const recu = envoi();
			const occupe = recu?.etat === 'en-cours' || Boolean(peutReessayer(recu));
			for (const retirer of liste.querySelectorAll('button')) marquerInactif(retirer, !ui.mutationsPermises || occupe);
			vide.hidden = suggestions.length > 0;
			reessayer.hidden = !ui.reessaiPossible(recu);
			const nom = recu ? `${LIBELLE_REESSAI} ${recu.commande.titre} · ${recu.commande.artiste}` : null;
			if (nom) reessayer.setAttribute('aria-label', nom); else reessayer.removeAttribute('aria-label');
			const texte = recu?.etat === 'accepte' ? 'Suggestion retirée.' : texteEnvoi(recu);
			if (statut.textContent !== texte) statut.textContent = texte;
		},
	};
}
