import { creerInterface, creerTicket, creerCatalogue, texteEnvoi, texteAutresPassages } from './commun.mjs';

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte), { e, bouton } = ui;
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
	ui.surRendu = () => {
		if (!ui.bloc) return;
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
	ui.surManche = () => { catalogue.reinitialiser(); invalidation.textContent = ''; };
	ui.surDestruction = () => catalogue.detruire();
	return { afficher: ui.afficher, demonter: ui.demonter };
}
