import { creerInterface, creerTicket, texteEnvoi } from '../karaoke/commun.mjs';
import { appelerSuivant, piloter, prochainPassage } from '../passage/pilotage.mjs';
import { formaterNombreVotes } from '../../vue-commune.mjs';

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte, { mode: 'freestyle', annoncerConnexion: false });
	const { e, bouton } = ui;
	const commande = (methode, chemin, corps, options) => ({ methode, chemin: ui.racine() + chemin, corps,
		mancheId: ui.bloc.mancheId, acteurType: 'ANIMATEUR', ...options });
	const titre = e('h2', '', 'Passage courant'); titre.tabIndex = -1;
	const identite = e('p');
	const instructions = e('p');
	const commandes = e('div', 'karaoke__commandes');
	const appeler = bouton('Appeler le suivant', () => appelerSuivant(ui, commande), 'bouton bouton--action');
	const pause = bouton('Pause', () => piloter(ui, commande, ui.bloc.etat === 'EN_PAUSE' ? 'REPRENDRE' : 'METTRE_EN_PAUSE'));
	const passer = bouton('Passer', () => piloter(ui, commande, 'PASSER_ETAPE'));
	const terminer = bouton('Terminer la Manche', () => {
		const mancheId = ui.bloc.mancheId;
		const dialogue = ui.dialogue({ titre: 'Terminer la Manche', declencheur: terminer,
			valide: () => ui.bloc.mancheId === mancheId,
			confirmation: { libelle: 'Terminer', permise: () => ui.mutationsPermises,
				executer: () => piloter(ui, commande, 'TERMINER') } });
		dialogue.annuler.textContent = 'Annuler';
		dialogue.contenu.append(e('p', '', 'Le Palmarès s’affichera à la fin de la Manche.'), dialogue.confirmer);
	});
	commandes.append(appeler, pause, passer, terminer);
	const statut = e('p'); statut.setAttribute('role', 'status');
	const message = e('p');
	const reessayer = bouton('Réessayer le même envoi', () => ui.reessayer(ui.dernierEchec(() => true)?.cle));
	const file = e('ol', 'rouleau a-venir');
	const vide = e('p', '', 'Aucun Passage en attente.');
	const votes = e('ul', 'karaoke__liste-votes');
	const zoneVotes = e('section', 'karaoke__votes');
	zoneVotes.append(e('h2', '', 'Vote du public'), votes);
	ui.principale.append(titre, identite, instructions, commandes, statut, message, reessayer,
		e('h2', '', 'File d’attente'), file, vide, zoneVotes);

	ui.messageInvalidation = () => { message.textContent = 'Le Passage a changé, vérifie avant de recommencer.'; };
	// Même route et même garde de compteur qu’au Karaoké : le serveur refuse un Passage qui n’attend plus.
	function confirmerRetrait(passageId, declencheur) {
		const cible = ui.bloc.passagesEnAttente.find(passage => passage.passageId === passageId);
		if (!cible) return;
		const mancheId = ui.bloc.mancheId;
		const valide = () => ui.bloc.mancheId === mancheId && ui.bloc.passagesEnAttente.some(passage =>
			passage.passageId === passageId && passage.numeroModification === cible.numeroModification);
		message.textContent = '';
		const dialogue = ui.dialogue({ titre: 'Retirer le Passage', declencheur, valide,
			confirmation: { libelle: 'Retirer', permise: () => ui.mutationsPermises && ui.bloc.peutGererFile,
				executer: () => ui.commande(commande('POST', `/passages/${encodeURIComponent(passageId)}/retrait`,
					{ numeroModification: cible.numeroModification })) } });
		dialogue.annuler.textContent = 'Annuler';
		dialogue.contenu.append(e('p', '',
			`Retirer le Passage ${cible.numero} · ${cible.interprete.pseudo} · ${cible.instrumentale.titre} ?`), dialogue.confirmer);
	}

	let automatique = false;
	ui.surRendu = () => {
		const bloc = ui.bloc;
		if (!bloc) return;
		const courant = bloc.courant;
		identite.textContent = courant ? `${courant.interprete.pseudo} · ${courant.instrumentale.titre}` : 'Aucun Passage appelé.';
		instructions.textContent = bloc.etat === 'EN_PAUSE' ? 'Manche en pause.' : {
			APPELE: 'Attend « Je suis prêt ».', EN_COURS: 'En scène.', TERMINE: 'Passage terminé.', SAUTE: 'Passage sauté.',
		}[courant?.etat] ?? '';
		appeler.hidden = automatique;
		appeler.disabled = !ui.mutationsPermises || !bloc.peutAppeler || !prochainPassage(bloc);
		pause.textContent = bloc.etat === 'EN_PAUSE' ? 'Reprendre' : 'Pause';
		pause.disabled = terminer.disabled = !ui.mutationsPermises;
		passer.disabled = !ui.mutationsPermises || !bloc.peutPasser;
		const texte = texteEnvoi(ui.dernierEnvoi(() => true));
		if (statut.textContent !== texte) statut.textContent = texte;
		reessayer.hidden = !ui.reessaiPossible(ui.dernierEchec(() => true));

		ui.remplacerListe(file, bloc.passagesEnAttente.map(passage => {
			const ligne = e('li');
			const retirer = bouton('Retirer', () => confirmerRetrait(passage.passageId, retirer));
			retirer.setAttribute('data-passage-id', passage.passageId);
			retirer.setAttribute('aria-label', `Retirer le Passage ${passage.numero}`);
			retirer.disabled = !ui.mutationsPermises || !bloc.peutGererFile;
			ligne.append(creerTicket(ui, { ...passage, prioritaire: passage.passageId === bloc.passagePrioritaireId }, { details: true }),
				retirer);
			return ligne;
		}), titre);
		vide.hidden = bloc.nombreEnAttente > 0;
		votes.replaceChildren(...bloc.votes.map(vote => e('li', '',
			`${vote.interprete.pseudo} · ${formaterNombreVotes(vote.nombreVotes)} · ${vote.etat === 'CLOS' ? 'Vote clos' : 'Vote ouvert'}`)));
		zoneVotes.hidden = bloc.votes.length === 0;
	};
	return {
		afficher(vue, origine) {
			automatique = vue.soiree?.pilotage === 'AUTOMATIQUE';
			ui.afficher(vue, origine);
		},
		demonter: ui.demonter,
	};
}
