import { creerInterface, creerTicket, creerCatalogue, texteEnvoi } from './commun.mjs';
import { preparerPlacement } from './placement.mjs';

// Bornes du contrat : limitePassagesEnAttente vaut de 1 à 20 (docs/api/README.md, File d'attente).
const LIMITE_PASSAGES_MAX = 20;

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte), { e, bouton, ecouter } = ui;
	const titre = e('h2', '', "File d'attente"); titre.tabIndex = -1;
	const limite = e('form', 'karaoke__limite champ'), label = e('label', '', 'Passages en attente par Interprète');
	const champ = e('input'); champ.id = 'karaoke-limite'; champ.type = 'number'; champ.min = '1'; champ.max = String(LIMITE_PASSAGES_MAX); champ.step = '1';
	label.setAttribute('for', champ.id);
	const erreur = e('p', 'champ--erreur'); erreur.id = 'karaoke-limite-erreur'; erreur.hidden = true; champ.setAttribute('aria-describedby', erreur.id);
	const appliquer = bouton('Appliquer', () => {}); appliquer.type = 'submit';
	const valeurSoumise = e('p'); valeurSoumise.setAttribute('role', 'status');
	limite.append(label, champ, appliquer, erreur, valeurSoumise);
	const file = e('ol', 'rouleau a-venir'), vide = e('p', '', 'Aucun Passage en attente.');
	const commandes = e('div', 'karaoke__commandes'), message = e('p'); message.setAttribute('role', 'status');
	ui.principale.append(titre, limite, file, vide, commandes, message);
	const catalogue = creerCatalogue(ui, ui.laterale);
	let selection = null;
	ui.messageInvalidation = () => { message.textContent = 'Le Passage a changé, vérifie avant de recommencer.'; };
	const passages = () => ui.bloc.passagesEnAttente;
	const commande = (methode, chemin, corps) => ({ methode, chemin: `${ui.racine()}${chemin}`, corps,
		mancheId: ui.bloc.mancheId, acteurType: 'ANIMATEUR' });
	function confirmer(action, declencheur) {
		const cible = structuredClone(passages().find(p => p.passageId === selection));
		if (!cible) return;
		const mancheId = ui.bloc.mancheId;
		const prioriteInitialeId = ui.bloc.passagePrioritaireId;
		const ancienne = action === 'priorite' && !cible.prioritaire ? structuredClone(passages().find(p => p.passageId === ui.bloc.passagePrioritaireId)) : null;
		const identique = reference => passages().some(p => p.passageId === reference.passageId &&
			p.numeroModification === reference.numeroModification && p.tour === reference.tour);
		const valide = () => ui.bloc.mancheId === mancheId && identique(cible) &&
			(action !== 'priorite' || ui.bloc.passagePrioritaireId === prioriteInitialeId) && (!ancienne ||
			(ui.bloc.passagePrioritaireId === ancienne.passageId && identique(ancienne)));
		const libelle = action === 'retirer' ? 'Retirer' : cible.prioritaire ? 'Lever la priorité' : 'Priorité';
		const d = ui.dialogue({ titre: libelle, declencheur, valide }); d.annuler.textContent = 'Annuler';
		d.contenu.append(e('p', '', `${libelle} · Passage ${cible.numero} · ${cible.chanson.titre} ?`));
		if (ancienne) d.contenu.append(e('p', '', `Remplace la priorité du Passage ${ancienne.numero} · ${ancienne.chanson.titre}.`));
		const cmd = action === 'retirer' ? commande('POST', `/passages/${encodeURIComponent(cible.passageId)}/retrait`, { numeroModification: cible.numeroModification }) :
			commande('PUT', '/file-attente/priorite', { passage: { passageId: cible.passageId, numeroModification: cible.numeroModification }, prioritaire: !cible.prioritaire });
		const confirmer = bouton('Confirmer', () => { if (!valide() || !ui.mutationsPermises || !ui.bloc.peutGererFile) return; d.fermer(); ui.commande(cmd); });
		d.contenu.append(confirmer);
	}
	ecouter(limite, 'submit', evenement => {
		evenement.preventDefault();
		const valeur = Number(champ.value);
		const valide = /^\d+$/.test(champ.value) && Number.isSafeInteger(valeur) && valeur >= 1 && valeur <= LIMITE_PASSAGES_MAX;
		erreur.hidden = valide; erreur.textContent = valide ? '' : `Saisis un entier entre 1 et ${LIMITE_PASSAGES_MAX}.`;
		if (!valide) { champ.setAttribute('aria-invalid', 'true'); return; }
		champ.removeAttribute('aria-invalid');
		ui.commande(commande('PUT', '/file-attente/limite', { limitePassagesEnAttente: valeur }));
	});
	ui.surRendu = () => {
		if (!ui.bloc) return;
		if (!passages().some(p => p.passageId === selection)) selection = null;
		const envoiLimite = [...ui.envois.values()].filter(e => e.commande.chemin.endsWith('/limite')).at(-1);
		if (ui.document.activeElement !== champ && envoiLimite?.etat !== 'en-cours') champ.value = String(ui.bloc.limitePassagesEnAttente);
		appliquer.disabled = !ui.mutationsPermises || !ui.bloc.peutReglerLimite;
		valeurSoumise.textContent = envoiLimite?.etat === 'en-cours' ? `Limite envoyée : ${envoiLimite.commande.corps.limitePassagesEnAttente} · Envoi en cours…` : texteEnvoi(envoiLimite);
		const tickets = [...passages()].sort((a, b) => a.position - b.position).map(p => {
			const li = e('li');
			const ticket = creerTicket(ui, p, { estimation: true, details: true, selectionner: id => { selection = id; message.textContent = ''; ui.rendre(); } });
			ticket.setAttribute('aria-pressed', String(p.passageId === selection)); li.append(ticket); return li;
		});
		ui.remplacerListe(file, tickets, titre); vide.hidden = Boolean(tickets.length); file.hidden = !tickets.length;
		const cible = passages().find(p => p.passageId === selection), actions = [];
		if (cible) {
			if (!cible.prioritaire) for (const [geste, libelle] of [['monter', 'Monter'], ['descendre', 'Descendre'], ['fin-du-tour', 'Fin du Tour']]) {
				const action = bouton(libelle, () => {
					const corps = preparerPlacement(passages(), cible.passageId, geste);
					if (corps) ui.commande(commande('PUT', `/passages/${encodeURIComponent(cible.passageId)}/placement`, corps));
				});
				action.disabled = !ui.mutationsPermises || !ui.bloc.peutGererFile || !preparerPlacement(passages(), cible.passageId, geste); actions.push(action);
			}
			for (const [geste, libelle] of [['priorite', cible.prioritaire ? 'Lever la priorité' : 'Priorité'], ['retirer', 'Retirer']]) {
				const action = bouton(libelle, () => confirmer(geste, action)); action.disabled = !ui.mutationsPermises || !ui.bloc.peutGererFile; actions.push(action);
			}
		}
		const dernier = ui.dernierEnvoi();
		if (dernier) {
			const statut = e('p', '', ui.csrfInvalide ? 'Recharge la Console pour rétablir la session. ' + texteEnvoi(dernier) : texteEnvoi(dernier)); statut.setAttribute('role', 'status'); actions.push(statut);
		}
		const echec = ui.dernierEchec();
		if (ui.reessaiPossible(echec)) {
			if (echec !== dernier) actions.push(e('p', '', texteEnvoi(echec)));
			actions.push(bouton('Réessayer le même envoi', () => ui.reessayer(echec.cle)));
		}
		if (ui.csrfInvalide && !dernier) actions.push(e('p', '', 'Recharge la Console pour rétablir la session.'));
		const actif = ui.document.activeElement, focusCommande = commandes.contains(actif);
		const nomCommande = focusCommande ? actif.textContent : null;
		commandes.replaceChildren(...actions);
		if (focusCommande) {
			const equivalente = actions.find(a => a.tagName === 'BUTTON' && a.textContent === nomCommande && !a.disabled);
			(equivalente ?? Array.from(file.querySelectorAll('button')).find(b => b.getAttribute('aria-pressed') === 'true') ?? titre).focus();
		}
		for (const d of ui.principale.querySelectorAll('dialog')) {
			const confirmer = Array.from(d.querySelectorAll('button')).find(b => b.textContent === 'Confirmer');
			if (confirmer) confirmer.disabled = !ui.mutationsPermises || !ui.bloc.peutGererFile;
		}
		catalogue.rendre();
	};
	ui.surManche = () => { selection = null; catalogue.reinitialiser(); message.textContent = ''; };
	ui.surDestruction = () => catalogue.detruire();
	return { afficher: ui.afficher, demonter: ui.demonter };
}
