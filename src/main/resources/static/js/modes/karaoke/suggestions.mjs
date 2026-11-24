import { texteEnvoi } from './commun.mjs';
import { peutReessayer } from './envois.mjs';

const LONGUEUR_MAX = 120;

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
	const champs = [['titre', 'Titre de la Chanson'], ['artiste', 'Artiste']].map(([nom, libelle]) => {
		const etiquette = e('label', '', libelle), saisie = e('input'), erreur = e('p', 'champ--erreur');
		saisie.id = `karaoke-suggestion-${nom}`;
		saisie.name = nom;
		saisie.required = true;
		erreur.id = `${saisie.id}-erreur`;
		etiquette.setAttribute('for', saisie.id);
		saisie.setAttribute('aria-describedby', erreur.id);
		ecouter(saisie, 'input', () => indiquerErreur(saisie, erreur, ''));
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
		if (evenement.isComposing || formulaire.hidden || envoyer.disabled) return;
		const corps = {}, invalides = [];
		for (const { nom, saisie, erreur } of champs) {
			const texte = saisie.value;
			const nettoye = texte.replace(/\p{White_Space}+/gu, ' ').trim();
			let message = '';
			if ([...texte].length > LONGUEUR_MAX) message = `Utilise au plus ${LONGUEUR_MAX} caractères.`;
			else if (!nettoye.normalize('NFD').replace(/\p{M}+/gu, '').trim()) message = 'Renseigne ce champ.';
			indiquerErreur(saisie, erreur, message);
			if (message) invalides.push(saisie);
			corps[nom] = texte;
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
			envoyer.disabled = !ui.mutationsPermises || !ui.bloc.peutRechercher || occupe;
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
	const reessayer = bouton('Réessayer le retrait de la suggestion', () => ui.reessayer(envoi()?.cle));
	section.append(titre, liste, vide, statut, reessayer);
	ui.laterale.append(section);
	let signature;

	function ligne(suggestion) {
		const { suggestionId, titre, artiste, nombreJoueurs } = suggestion;
		const ligne = e('li');
		const retirer = bouton('Retirer', () => ui.commande({
			type: 'RETIRER_SUGGESTION_CHANSON', methode: 'POST', acteurType: 'ANIMATEUR',
			chemin: `${ui.racine()}/suggestions/${encodeURIComponent(suggestionId)}/retrait`,
			mancheId: ui.bloc.mancheId, corps: { nombreJoueurs },
		}));
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
			for (const retirer of liste.querySelectorAll('button')) retirer.disabled = !ui.mutationsPermises || occupe;
			vide.hidden = suggestions.length > 0;
			reessayer.hidden = !ui.reessaiPossible(recu);
			const texte = recu?.etat === 'accepte' ? 'Suggestion retirée.' : texteEnvoi(recu);
			if (statut.textContent !== texte) statut.textContent = texte;
		},
	};
}
