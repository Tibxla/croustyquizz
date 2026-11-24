import { creerInterface, creerTicket, texteEnvoi } from '../karaoke/commun.mjs';
import { appelerSuivant, piloter, prochainPassage } from '../passage/pilotage.mjs';
import { formaterNombreVotes } from '../../vue-commune.mjs';
import { pseudos, texteAnnonce, texteCouplet, texteOrdre } from './couplets.mjs';

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte, { mode: 'freestyle', annoncerConnexion: false });
	const { e, bouton } = ui;
	const commande = (methode, chemin, corps, options) => ({ methode, chemin: ui.racine() + chemin, corps,
		mancheId: ui.bloc.mancheId, acteurType: 'ANIMATEUR', ...options });
	const titre = e('h2', '', 'Passage courant'); titre.tabIndex = -1;
	const identite = e('p');
	const instructions = e('p');
	const couplets = e('p');
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
	const reessayer = bouton('Réessayer le même envoi', () => ui.reessayer(ui.dernierEchec(() => true)?.cle));
	const file = e('ol', 'rouleau a-venir');
	const vide = e('p', '', 'Aucun Passage en attente.');
	const votes = e('ul', 'karaoke__liste-votes');
	const zoneVotes = e('section', 'karaoke__votes');
	zoneVotes.append(e('h2', '', 'Vote du public'), votes);
	const invitations = e('ul', 'karaoke__etat-invitations');
	invitations.setAttribute('aria-label', 'Invitations en attente');
	ui.principale.append(titre, identite, instructions, couplets, commandes, statut, reessayer,
		e('h2', '', 'File d’attente'), file, vide, invitations, zoneVotes);

	let automatique = false;
	ui.surRendu = () => {
		const bloc = ui.bloc;
		if (!bloc) return;
		const courant = bloc.courant;
		identite.textContent = courant ? `${pseudos(courant.interpretes)} · ${courant.instrumentale.titre}` : 'Aucun Passage appelé.';
		const etatCouplets = courant?.couplets;
		couplets.hidden = !etatCouplets;
		couplets.textContent = etatCouplets ? [texteOrdre(etatCouplets), `${texteCouplet(etatCouplets)} · ${etatCouplets.courant.interprete.pseudo}`,
			texteAnnonce(etatCouplets)].filter(Boolean).join('. ') : '';
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
			ligne.append(creerTicket(ui, { ...passage, prioritaire: passage.passageId === bloc.passagePrioritaireId }, { details: true }));
			return ligne;
		}), titre);
		vide.hidden = bloc.nombreEnAttente > 0;
		const numeros = new Map(bloc.passagesEnAttente.map(p => [p.passageId, p.numero]));
		invitations.replaceChildren(...(bloc.invitationsEnAttente ?? []).map(i => e('li', '',
			`${i.invite.pseudo} n’a pas encore répondu${numeros.has(i.passageId) ? ` (Passage ${numeros.get(i.passageId)})` : ''}`)));
		invitations.hidden = !invitations.childElementCount;
		votes.replaceChildren(...bloc.votes.map(vote => e('li', '',
			`${pseudos(vote.interpretes)} · ${formaterNombreVotes(vote.nombreVotes)} · ${vote.etat === 'CLOS' ? 'Vote clos' : 'Vote ouvert'}`
			+ (vote.voix ? ` · ${vote.voix.map(v => `${v.interprete.pseudo} ${v.nombreVoix}`).join(', ')}` : ''))));
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
