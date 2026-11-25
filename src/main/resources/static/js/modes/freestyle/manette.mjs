import { creerInterface, creerTicket, texteEnvoi } from '../karaoke/commun.mjs';
import { installerScene } from '../passage/scene-manette.mjs';
import { installerVotes } from '../passage/votes-manette.mjs';

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte, { mode: 'freestyle', annoncerConnexion: false });
	const { e, bouton } = ui;
	const votes = installerVotes(ui, { afficheMoyenne: false });
	const scene = installerScene(ui, 'À toi d’improviser !');
	const titre = e('h2', '', 'S’inscrire à un Passage'); titre.tabIndex = -1;
	const choix = e('select'); choix.id = 'freestyle-instrumentale';
	const label = e('label', '', 'Instrumentale'); label.setAttribute('for', choix.id);
	const inscription = envoi => envoi.commande.type === 'INSCRIRE_FREESTYLE';
	const envoyer = bouton('M’inscrire', () => ui.commande({ methode: 'POST', chemin: ui.racine() + '/passages',
		acteurType: 'JOUEUR', mancheId: ui.bloc.mancheId, type: 'INSCRIRE_FREESTYLE',
		corps: { instrumentaleId: choix.value || null } }), 'bouton bouton--action bouton--grand');
	const statut = e('p'); statut.setAttribute('role', 'status');
	const reessayer = bouton('Réessayer le même envoi', () => ui.reessayer(ui.dernierEchec(inscription)?.cle));
	const aide = e('p');
	const panneau = e('div', 'champ');
	panneau.append(label, choix, aide, envoyer, statut, reessayer);
	const file = e('div', 'karaoke__personnels');
	ui.principale.append(titre, panneau, e('h2', '', 'Tes Passages en attente'), file);

	let choixPrecedents;
	const decompte = setInterval(() => { scene.decompter(); votes.decompter(); }, 1000);
	ui.surRendu = origine => {
		const bloc = ui.bloc;
		if (!bloc) return;
		votes.rendre(titre, origine);
		scene.rendre(origine, titre);
		const disponibles = bloc.instrumentales;
		const signature = JSON.stringify(disponibles);
		if (signature !== choixPrecedents) {
			const precedent = choix.value;
			const options = [{ instrumentaleId: '', titre: 'Au hasard' }, ...disponibles].map(fiche => {
				const option = e('option', '', fiche.titre);
				option.value = fiche.instrumentaleId;
				return option;
			});
			choix.replaceChildren(...options);
			choix.value = disponibles.some(fiche => fiche.instrumentaleId === precedent) ? precedent : '';
			choixPrecedents = signature;
		}
		const envoi = ui.dernierEnvoi(inscription);
		const enCours = envoi?.etat === 'en-cours';
		// Sans confirmation, un nouvel envoi pourrait doubler l'inscription : seul le réessai du même envoi reste permis.
		const aConfirmer = ['inconnu', 'indisponible'].includes(envoi?.etat);
		envoyer.disabled = choix.disabled = !ui.mutationsPermises || !bloc.peutInscrire || enCours || aConfirmer;
		aide.textContent = disponibles.length ? (bloc.peutInscrire ? '' : 'Tu ne peux pas ajouter de Passage pour le moment.')
			: 'Aucune Instrumentale disponible pour le moment.';
		const texte = texteEnvoi(envoi);
		if (statut.textContent !== texte) statut.textContent = texte;
		reessayer.hidden = !ui.reessaiPossible(ui.dernierEchec(inscription));
		const tickets = bloc.mesPassagesEnAttente.map(passage => creerTicket(ui, passage, { personnel: true }));
		ui.remplacerListe(file, tickets.length ? tickets : [e('p', '', 'Tu n’attends aucun Passage.')], titre);
	};
	ui.surManche = () => {
		choixPrecedents = undefined;
		votes.reinitialiser();
		scene.reinitialiser();
	};
	ui.surDestruction = () => clearInterval(decompte);
	return { afficher: ui.afficher, demonter: ui.demonter };
}
