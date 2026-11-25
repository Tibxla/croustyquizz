import { creerInterface, creerTicket, creerCatalogue, texteEnvoi, texteAutresPassages } from './commun.mjs';
import { peutReessayer } from './envois.mjs';
import { formaterTempsRestant } from './presentation.mjs';
import { delaiAppelRestant } from './paroles.mjs';
import { corpsVote, delaiVoteRestant, etatVoteManette, formaterMoyenne, formaterNombreVotes, formaterNote } from './vote.mjs';

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte), { e, bouton } = ui;
	const votes = installerVotes(ui);
	const titreScene = e('h2', '', 'En scène'); titreScene.tabIndex = -1;
	const scene = e('div', 'karaoke__scene-manette'); scene.hidden = true;
	ui.principale.append(scene);
	const titre = e('h2', '', 'Tes Passages'); titre.tabIndex = -1;
	const personnels = e('div', 'karaoke__personnels');
	const invalidation = e('p'); invalidation.setAttribute('role', 'status');
	ui.principale.append(titre, personnels, invalidation);
	const catalogue = creerCatalogue(ui, ui.principale, { inscrire: true });
	const ensuite = e('h2', '', 'Ensuite'); ensuite.tabIndex = -1;
	const file = e('ol', 'rouleau'), autres = e('p'); ui.principale.append(ensuite, file, autres);
	ui.messageInvalidation = () => { invalidation.textContent = 'Le Passage a changé, vérifie avant de recommencer.'; };
	function desinscrire(passage, declencheur) {
		const mancheId = ui.bloc.mancheId, cible = structuredClone(passage);
		const valide = () => ui.bloc.mancheId === mancheId && ui.bloc.mesPassagesEnAttente.some(p =>
			p.passageId === cible.passageId && p.numeroModification === cible.numeroModification && p.tour === cible.tour);
		const dialogue = ui.dialogue({ titre: 'Me désinscrire', declencheur, valide, confirmation: {
			libelle: 'Confirmer la désinscription',
			permise: () => ui.mutationsPermises,
			executer: () => ui.commande({ methode: 'POST', chemin: `${ui.racine()}/passages/${encodeURIComponent(cible.passageId)}/desinscription`,
				corps: { numeroModification: cible.numeroModification }, mancheId, acteurType: 'JOUEUR', passageId: cible.passageId }),
		} });
		dialogue.annuler.textContent = 'Annuler';
		dialogue.contenu.append(e('p', '', `Retirer ton Passage ${cible.numero} · ${cible.chanson.titre} ?`), dialogue.confirmer);
	}
	let dernierCourant, descriptionTicket, ticketCourant, etiquette, minuterie, confirmerPresence, statutPresence, reessai;
	function decompter() {
		const p = ui.bloc?.courant;
		if (p?.etat !== 'APPELE' || !minuterie) return;
		const enPause = ui.bloc.etat === 'EN_PAUSE';
		const restant = delaiAppelRestant({ ...p, maintenant: contexte.maintenantServeur(), enPause });
		minuterie.textContent = formaterTempsRestant(restant);
		etiquette.classList.toggle('mon-ticket__etiquette--texte', enPause || restant === 0);
		etiquette.textContent = enPause ? 'Pause : attends la reprise.'
			: restant === 0 ? 'Le temps est écoulé, tu peux encore confirmer.' : "C'est ton tour";
	}
	function rendreScene(origine) {
		const p = ui.bloc.courant;
		const personnel = p?.interpretes.some(i => i.joueurId === contexte.acteur.identifiant);
		const description = p && JSON.stringify([p.passageId, p.etat, p.numero, p.chanson.titre, p.chanson.artiste, p.interpretes, personnel]);
		const actif = ui.document.activeElement;
		const avaitFocus = scene.contains(actif);
		const boutonCible = actif === reessai ? 'reessai' : 'confirmer';
		if (description !== descriptionTicket) {
			descriptionTicket = description;
			minuterie = confirmerPresence = statutPresence = reessai = undefined;
			scene.hidden = !p;
			scene.replaceChildren();
			if (p) {
				ticketCourant = creerTicket(ui, p, { personnel, courant: true });
				if (personnel) {
					etiquette = ticketCourant.querySelector('.mon-ticket__etiquette');
					const bas = ticketCourant.querySelector('.mon-ticket__bas');
					statutPresence = e('p'); statutPresence.setAttribute('role', 'status');
					if (p.etat === 'APPELE') {
						ticketCourant.classList.add('mon-ticket--appele');
						if (dernierCourant !== p.passageId && origine?.source === 'evenement' && origine.type === 'passage-appele') {
							ticketCourant.classList.add('mon-ticket--detachement');
						}
						minuterie = e('p', 'chiffres');
						confirmerPresence = bouton('Je suis prêt', () => {
							const cible = ui.bloc.courant;
							ui.commande({ type: 'CONFIRMER_PRESENCE', methode: 'POST', chemin: `${ui.racine()}/passages/${encodeURIComponent(cible.passageId)}/presence`,
								corps: { etapeId: cible.etapeId, numeroModification: cible.numeroModification }, mancheId: ui.bloc.mancheId,
								acteurType: 'JOUEUR', passageId: cible.passageId });
						}, 'bouton bouton--action');
						reessai = bouton('Réessayer le même envoi', () => ui.reessayer(envoiPresence()?.cle));
						bas.append(minuterie, confirmerPresence, reessai);
					} else {
						etiquette.classList.toggle('mon-ticket__etiquette--texte', ['EN_COURS', 'EN_PAUSE'].includes(p.etat));
						etiquette.textContent = {
							EN_COURS: 'À toi de chanter !', EN_PAUSE: 'Pause : attends la reprise.',
							TERMINE: 'Passage terminé', SAUTE: 'Passage sauté',
						}[p.etat] ?? '';
					}
					bas.append(statutPresence);
				}
				scene.append(titreScene, ticketCourant);
			}
		}
		dernierCourant = p?.passageId;
		if (origine?.source === 'capture') ticketCourant?.classList.remove('mon-ticket--detachement');
		const envoi = envoiPresence();
		const texte = texteEnvoi(envoi);
		if (statutPresence && statutPresence.textContent !== texte) statutPresence.textContent = texte;
		if (confirmerPresence) {
			confirmerPresence.hidden = !ui.bloc.peutConfirmerPresence || !ui.synchronisee || ui.bloc.etat === 'EN_PAUSE';
			confirmerPresence.disabled = !ui.mutationsPermises || ['en-cours', 'accepte'].includes(envoi?.etat) || Boolean(peutReessayer(envoi));
			reessai.hidden = !ui.reessaiPossible(envoi);
		}
		if (avaitFocus && (!actif.isConnected || actif.hidden || actif.disabled || ui.document.activeElement !== actif)) {
			const cible = boutonCible === 'reessai' ? reessai : confirmerPresence;
			(actif !== titreScene && cible && !cible.hidden && !cible.disabled ? cible : p ? titreScene : titre).focus();
		}
		decompter();
	}
	function envoiPresence() {
		return ui.dernierEnvoi(e => e.commande.type === 'CONFIRMER_PRESENCE' && e.commande.passageId === ui.bloc.courant?.passageId);
	}
	const horloge = setInterval(() => { decompter(); votes.decompter(); }, 1000);
	ui.surRendu = origine => {
		if (!ui.bloc) return;
		votes.rendre(titre, origine);
		rendreScene(origine);
		const tickets = ui.bloc.mesPassagesEnAttente.map(p => {
			const ticket = creerTicket(ui, p, { personnel: true, estimation: true });
			const retirer = bouton('Me désinscrire', () => desinscrire(p, retirer), 'bouton bouton--lien');
			retirer.setAttribute('data-passage-id', p.passageId); retirer.disabled = !ui.mutationsPermises;
			const envoi = [...ui.envois.values()].filter(e => e.commande.passageId === p.passageId).at(-1);
			if (envoi?.etat === 'en-cours') retirer.disabled = true;
			const commandes = e('div', 'karaoke__commandes'); commandes.append(retirer);
			const etat = e('p', '', texteEnvoi(envoi)); etat.setAttribute('role', 'status'); commandes.append(etat);
			if (ui.reessaiPossible(envoi)) commandes.append(bouton('Réessayer le même envoi', () => ui.reessayer(envoi.cle)));
			// Le lien de désinscription reste sur le ticket, avec l'encre du ticket.
			ticket.querySelector('.mon-ticket__bas').append(commandes); return ticket;
		});
		ui.remplacerListe(personnels, tickets.length ? tickets : [e('p', '', "Tu n'attends aucun Passage.")], titre);
		ui.remplacerListe(file, ui.bloc.prochainsPassages.map(p => { const li = e('li'); li.append(creerTicket(ui, p)); return li; }), ensuite);
		file.hidden = !ui.bloc.prochainsPassages.length;
		autres.textContent = texteAutresPassages(ui.bloc.nombreAutresPassages);
		autres.hidden = !ui.bloc.nombreAutresPassages;
		catalogue.rendre();
	};
	ui.surManche = () => {
		votes.reinitialiser();
		dernierCourant = undefined; descriptionTicket = undefined;
		catalogue.reinitialiser(); invalidation.textContent = '';
	};
	ui.surDestruction = () => { clearInterval(horloge); catalogue.detruire(); };
	return { afficher: ui.afficher, demonter: ui.demonter };
}

function installerVotes(ui) {
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
	const contexteVote = () => ({ joueurId: contexte.acteur.identifiant, enPause: ui.bloc.etat === 'EN_PAUSE' });
	const resultat = vote => vote.moyenneDixiemes == null ? 'Aucun vote.'
		: `Moyenne : ${formaterMoyenne(vote.moyenneDixiemes)} (${formaterNombreVotes(vote.nombreVotes)}).`;

	function creer(vote) {
		const passageId = vote.passageId;
		const ticket = creerTicket(ui, vote, { personnel: true, courant: true });
		ticket.classList.add('karaoke__ticket-vote');
		ticket.querySelector('.mon-ticket__etiquette').remove();
		const message = e('p', 'karaoke__etat-vote');
		const notePersonnelle = e('p');
		const temps = e('span', 'chiffres');
		const choix = e('div', 'karaoke__notes');
		choix.setAttribute('role', 'group');
		choix.setAttribute('aria-label', `Note du Passage ${vote.numero}`);
		const fiche = { ticket, temps, vote, note: null };
		const notes = [1, 2, 3, 4, 5].map(note => {
			const action = bouton(String(note), () => { fiche.note = note; ui.rendre(); });
			action.setAttribute('aria-label', formaterNote(note));
			choix.append(action);
			return action;
		});
		const envoyer = bouton('Envoyer ma note', () => ui.commande({
			type: 'VOTER', acteurType: 'JOUEUR', methode: 'POST', mancheId: ui.bloc.mancheId, passageId,
			chemin: `${ui.racine()}/passages/${encodeURIComponent(passageId)}/vote`,
			corps: corpsVote({ vote: fiche.vote, note: fiche.note }),
		}));
		const reessayer = bouton('Réessayer le même envoi', () => ui.reessayer(envoiDuVote(passageId)?.cle));
		const statut = e('p');
		statut.setAttribute('role', 'status');
		ticket.querySelector('.mon-ticket__bas').append(message, notePersonnelle, temps, choix, envoyer, statut, reessayer);
		return Object.assign(fiche, { message, notePersonnelle, choix, notes, envoyer, statut, reessayer });
	}

	function dessiner(fiche, etat, principale) {
		const { vote, note, message, notePersonnelle, temps, choix, notes, envoyer, statut, reessayer } = fiche;
		const envoi = envoiDuVote(vote.passageId);
		const accepte = envoi?.etat === 'accepte';
		const enCours = envoi?.etat === 'en-cours';
		const actionsVisibles = etat === 'a-voter' && !accepte;
		const interdit = !ui.mutationsPermises || !vote.peutVoter || enCours || Boolean(peutReessayer(envoi));
		message.textContent = etat === 'clos' ? resultat(vote) : {
			'a-voter': accepte ? '' : 'Note ce Passage',
			interprete: 'Le public vote pour ton Passage.',
			enregistre: enCours || accepte ? ''
				: `Ta note : ${formaterNote(vote.maNote)}. Résultat à la fin du vote.`,
			pause: 'Pause : tu pourras voter à la reprise.',
		}[etat];
		message.hidden = !message.textContent;
		notePersonnelle.hidden = etat !== 'clos' || vote.maNote == null;
		notePersonnelle.textContent = notePersonnelle.hidden ? '' : `Ta note : ${formaterNote(vote.maNote)}.`;
		temps.hidden = choix.hidden = envoyer.hidden = !actionsVisibles;
		envoyer.className = principale && actionsVisibles ? 'bouton bouton--action bouton--grand' : 'bouton';
		envoyer.disabled = interdit || note == null;
		notes.forEach((action, index) => {
			action.disabled = interdit;
			action.setAttribute('aria-pressed', String(index + 1 === note));
		});
		reessayer.hidden = !actionsVisibles || !ui.reessaiPossible(envoi);
		const texte = etat === 'clos' ? '' : enCours ? 'Envoi de ta note…' : accepte
			? `Ta note est enregistrée : ${formaterNote(envoi.corps.note)}. Résultat à la fin du vote.` : texteEnvoi(envoi);
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
