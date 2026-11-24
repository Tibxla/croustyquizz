import { creerTicket, texteEnvoi } from '../karaoke/commun.mjs';
import { peutReessayer } from '../karaoke/envois.mjs';
import { formaterTempsRestant } from '../karaoke/presentation.mjs';
import { corpsVote, delaiVoteRestant, etatVoteManette, formaterMoyenne, formaterNombreVotes, formaterNote } from '../karaoke/vote.mjs';

export function installerVotes(ui, { afficheMoyenne = true } = {}) {
	const { e, bouton, contexte } = ui;
	const section = e('section', 'karaoke__votes');
	const titre = e('h2', '', 'Vote du public');
	titre.id = 'karaoke-votes-manette';
	titre.tabIndex = -1;
	section.setAttribute('aria-labelledby', titre.id);
	const liste = e('div', 'karaoke__liste-votes');
	const annonce = e('p', 'karaoke__annonce-vote visuellement-cache');
	annonce.setAttribute('role', 'status');
	const tickets = new Map();
	const cloturesAnnoncees = new Set();
	section.hidden = true;
	section.append(titre, liste);
	ui.principale.append(section, annonce);

	const envoiDuVote = passageId => ui.dernierEnvoi(e => e.commande.type === 'VOTER' && e.commande.passageId === passageId);
	const attenteResultat = afficheMoyenne ? 'Résultat à la fin du vote.' : 'Résultat au Palmarès.';
	const contexteVote = () => ({ joueurId: contexte.acteur.identifiant, enPause: ui.bloc.etat === 'EN_PAUSE' });
	const resultat = vote => !afficheMoyenne ? 'Vote clos.' : vote.moyenneDixiemes == null ? 'Aucun vote.'
		: `Moyenne : ${formaterMoyenne(vote.moyenneDixiemes)} (${formaterNombreVotes(vote.nombreVotes)}).`;

	// Passage de Freestyle à plusieurs (#74) : le Joueur désigne un seul Interprète au lieu de noter.
	const parDesignation = vote => vote.scrutin === 'MEILLEUR_INTERPRETE';
	const pseudoDe = (vote, id) => vote.interpretes.find(i => i.joueurId === id)?.pseudo ?? '';

	function creer(vote) {
		const passageId = vote.passageId;
		const designation = parDesignation(vote);
		const ticket = creerTicket(ui, vote, { personnel: true, courant: true });
		ticket.classList.add('karaoke__ticket-vote');
		ticket.querySelector('.mon-ticket__etiquette').remove();
		const message = e('p', 'karaoke__etat-vote');
		const notePersonnelle = e('p');
		const temps = e('span', 'chiffres');
		const choix = e('div', 'karaoke__notes');
		choix.setAttribute('role', 'group');
		choix.setAttribute('aria-label', designation ? `Meilleur Interprète du Passage ${vote.numero}` : `Note du Passage ${vote.numero}`);
		const fiche = { ticket, temps, vote, note: null, designation };
		const valeurs = designation ? vote.interpretes.map(i => i.joueurId) : [1, 2, 3, 4, 5];
		const notes = valeurs.map(valeur => {
			const action = bouton(designation ? pseudoDe(vote, valeur) : String(valeur), () => { fiche.note = valeur; ui.rendre(); });
			if (!designation) action.setAttribute('aria-label', formaterNote(valeur));
			choix.append(action);
			return action;
		});
		const envoyer = bouton(designation ? 'Envoyer mon choix' : 'Envoyer ma note', () => ui.commande({
			type: 'VOTER', acteurType: 'JOUEUR', methode: 'POST', mancheId: ui.bloc.mancheId, passageId,
			chemin: `${ui.racine()}/passages/${encodeURIComponent(passageId)}/${designation ? 'meilleur-interprete' : 'vote'}`,
			corps: designation ? { numeroModification: fiche.vote.numeroModification, interpreteId: fiche.note }
				: corpsVote({ vote: fiche.vote, note: fiche.note }),
		}));
		const reessayer = bouton('Réessayer le même envoi', () => ui.reessayer(envoiDuVote(passageId)?.cle));
		const statut = e('p');
		statut.setAttribute('role', 'status');
		ticket.querySelector('.mon-ticket__bas').append(message, notePersonnelle, temps, choix, envoyer, statut, reessayer);
		return Object.assign(fiche, { message, notePersonnelle, choix, notes, envoyer, statut, reessayer });
	}

	function dessiner(fiche, etat, principale) {
		const { vote, note, message, notePersonnelle, temps, choix, notes, envoyer, statut, reessayer, designation } = fiche;
		const avis = valeur => designation ? pseudoDe(vote, valeur) : formaterNote(valeur);
		const monAvis = designation ? vote.monChoix : vote.maNote;
		const ton = designation ? 'Ton choix' : 'Ta note';
		const envoi = envoiDuVote(vote.passageId);
		const accepte = envoi?.etat === 'accepte';
		const enCours = envoi?.etat === 'en-cours';
		const actionsVisibles = etat === 'a-voter' && !accepte;
		const interdit = !ui.mutationsPermises || !vote.peutVoter || enCours || Boolean(peutReessayer(envoi));
		message.textContent = etat === 'clos' ? resultat(vote) : {
			'a-voter': accepte ? '' : designation ? 'Désigne le meilleur Interprète' : 'Note ce Passage',
			interprete: 'Le public vote pour ton Passage.',
			enregistre: enCours || accepte ? ''
				: `${ton} : ${avis(monAvis)}. ${attenteResultat}`,
			pause: 'Pause : tu pourras voter à la reprise.',
		}[etat];
		message.hidden = !message.textContent;
		notePersonnelle.hidden = etat !== 'clos' || monAvis == null;
		notePersonnelle.textContent = notePersonnelle.hidden ? '' : `${ton} : ${avis(monAvis)}.`;
		temps.hidden = choix.hidden = envoyer.hidden = !actionsVisibles;
		envoyer.className = principale && actionsVisibles ? 'bouton bouton--action bouton--grand' : 'bouton';
		envoyer.disabled = interdit || note == null;
		const valeurs = designation ? vote.interpretes.map(i => i.joueurId) : [1, 2, 3, 4, 5];
		notes.forEach((action, index) => {
			action.disabled = interdit;
			action.setAttribute('aria-pressed', String(valeurs[index] === note));
		});
		reessayer.hidden = !actionsVisibles || !ui.reessaiPossible(envoi);
		const texte = etat === 'clos' ? '' : enCours ? `Envoi de ${designation ? 'ton choix' : 'ta note'}…` : accepte
			? `${ton} est enregistré${designation ? '' : 'e'} : ${avis(designation ? envoi.corps.interpreteId : envoi.corps.note)}. ${attenteResultat}`
			: texteEnvoi(envoi);
		const variante = envoi?.etat === 'indisponible' ? 'refuse' : envoi?.etat;
		statut.className = texte ? `envoi envoi--${variante}` : '';
		if (statut.textContent !== texte) statut.textContent = texte;
	}

	function decompter() {
		for (const { vote, temps } of tickets.values()) {
			if (!temps.hidden) temps.textContent =
				formaterTempsRestant(delaiVoteRestant(vote, contexte.maintenantServeur(), contexteVote().enPause));
		}
	}

	function annoncerClotures(votes, origine) {
		if (origine?.source === 'capture' && annonce.textContent) annonce.textContent = '';
		if (origine?.source !== 'evenement') return;
		const nouvelles = [];
		for (const vote of votes) {
			const id = vote.passageId;
			if (vote.etat !== 'CLOS' || tickets.get(id)?.vote.etat !== 'OUVERT' || cloturesAnnoncees.has(id)) continue;
			cloturesAnnoncees.add(id);
			nouvelles.push(`Passage ${vote.numero}. ${resultat(vote)}`);
		}
		if (nouvelles.length) annonce.textContent = nouvelles.join(' ');
	}

	return {
		decompter,
		rendre(titreRepli, origine) {
			const votes = ui.bloc.votes ?? [];
			annoncerClotures(votes, origine);
			const actif = ui.document.activeElement;
			const avaitFocus = section.contains(actif);
			section.hidden = votes.length === 0;
			const ids = new Set(votes.map(vote => vote.passageId));
			for (const [id, fiche] of tickets) {
				if (!ids.has(id)) { fiche.ticket.remove(); tickets.delete(id); }
			}
			const etats = votes.map(vote => etatVoteManette(vote, contexteVote()));
			const proposePresence = ui.bloc.peutConfirmerPresence && ui.synchronisee;
			const principal = proposePresence ? -1 : etats.findIndex((etat, index) =>
				etat === 'a-voter' && envoiDuVote(votes[index].passageId)?.etat !== 'accepte');
			const ordre = votes.map((vote, index) => {
				const fiche = tickets.get(vote.passageId) ?? creer(vote);
				tickets.set(vote.passageId, fiche);
				fiche.vote = vote;
				dessiner(fiche, etats[index], index === principal);
				return fiche.ticket;
			});
			if (ordre.some((ticket, index) => liste.children[index] !== ticket)) liste.replaceChildren(...ordre);
			decompter();
			const actifMasque = [...tickets.values()].some(fiche => fiche.choix.hidden && fiche.choix.contains(actif));
			if (avaitFocus) {
				if (!actif.isConnected || actif.hidden || actif.disabled || actifMasque) {
					(section.hidden ? titreRepli : titre).focus();
				} else if (ui.document.activeElement !== actif) actif.focus();
			}
		},
		reinitialiser() {
			tickets.clear();
			cloturesAnnoncees.clear();
			liste.replaceChildren();
			annonce.textContent = '';
		},
	};
}
