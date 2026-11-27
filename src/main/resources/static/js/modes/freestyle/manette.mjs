import { creerChoixInvites, creerInterface, creerTicket, texteEnvoi } from '../karaoke/commun.mjs';
import { estInscripteur, lignesInvitations } from '../karaoke/invitations.mjs';
import { installerScene } from '../passage/scene-manette.mjs';
import { installerVotes } from '../passage/votes-manette.mjs';
import { installerInvitations } from '../passage/invitations-manette.mjs';
import { cleVibration, texteCouplet } from './couplets.mjs';

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte, { mode: 'freestyle' });
	const { e, bouton } = ui;
	const votes = installerVotes(ui, { afficheMoyenne: false });
	const scene = installerScene(ui, 'À toi d’improviser !');
	// Une mesure avant ton Couplet : le serveur l'annonce et cette Manette vibre une fois (#74).
	const annonce = e('p', 'freestyle__annonce-manette');
	annonce.setAttribute('role', 'status');
	const couplet = e('p', 'freestyle__couplet-manette');
	ui.principale.append(annonce, couplet);
	const invitations = installerInvitations(ui, { verbe: 'rapper' });
	const vibrations = new Set();
	const titre = e('h2', '', 'S’inscrire à un Passage'); titre.tabIndex = -1;
	const choix = e('select'); choix.id = 'freestyle-instrumentale';
	const label = e('label', '', 'Instrumentale'); label.setAttribute('for', choix.id);
	const inscription = envoi => envoi.commande.type === 'INSCRIRE_FREESTYLE';
	const moi = contexte.acteur.identifiant;
	let inscriptionVidee;
	const invites = creerChoixInvites(ui, moi, { verbe: 'Rapper' });
	const envoyer = bouton('M’inscrire', () => {
		const invitesIds = invites.choisis();
		// Sans invité, le corps reste celui d'une inscription seule.
		ui.commande({ methode: 'POST', chemin: ui.racine() + '/passages',
			acteurType: 'JOUEUR', mancheId: ui.bloc.mancheId, type: 'INSCRIRE_FREESTYLE',
			corps: invitesIds.length ? { instrumentaleId: choix.value || null, invitesIds } : { instrumentaleId: choix.value || null } });
	}, 'bouton bouton--action bouton--grand');
	const statut = e('p'); statut.setAttribute('role', 'status');
	const reessayer = bouton('Réessayer le même envoi', () => ui.reessayer(ui.dernierEchec(inscription)?.cle));
	const aide = e('p');
	// Seul le choix de l'Instrumentale est un champ : « .champ input » grossirait les cases des invités.
	const champ = e('div', 'champ');
	champ.append(label, choix);
	const panneau = e('div', 'freestyle__inscription');
	panneau.append(champ, invites.element, aide, envoyer, statut, reessayer);
	const file = e('div', 'karaoke__personnels');
	ui.principale.append(titre, panneau, e('h2', '', 'Tes Passages en attente'), file);

	let choixPrecedents;
	const decompte = setInterval(() => { scene.decompter(); votes.decompter(); }, 1000);
	ui.surRendu = origine => {
		const bloc = ui.bloc;
		if (!bloc) return;
		votes.rendre(titre, origine);
		scene.rendre(origine, titre);
		invitations.rendre(titre);
		// Le Couplet ne se dit que pendant le Passage : une fois terminé, la ligne disparaît (#283).
		const couplets = ['EN_COURS', 'EN_PAUSE'].includes(bloc.courant?.etat) ? bloc.courant.couplets ?? null : null;
		const interprete = bloc.courant?.interpretes.some(i => i.joueurId === moi);
		const enScene = interprete && ['APPELE', 'EN_COURS', 'EN_PAUSE'].includes(bloc.courant.etat);
		const texteAnnonce = bloc.monCoupletAnnonce ? 'À toi dans une mesure !' : '';
		if (annonce.textContent !== texteAnnonce) annonce.textContent = texteAnnonce;
		couplet.hidden = !couplets || !interprete;
		couplet.textContent = couplet.hidden ? '' : `${texteCouplet(couplets)} · ${couplets.courant.interprete.pseudo}`;
		const cle = cleVibration(bloc, origine);
		if (cle && !vibrations.has(cle)) {
			vibrations.add(cle);
			ui.document.defaultView?.navigator?.vibrate?.(200);
		}
		const envoi = ui.dernierEnvoi(inscription);
		// Une inscription acceptée a déjà invité ces Joueurs : la suivante repart sans invité coché.
		const viderInvites = envoi?.etat === 'accepte' && envoi.cle !== inscriptionVidee;
		if (viderInvites) inscriptionVidee = envoi.cle;
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
		const enCours = envoi?.etat === 'en-cours';
		// Sans confirmation, un nouvel envoi pourrait doubler l'inscription : seul le réessai du même envoi reste permis.
		const aConfirmer = ['inconnu', 'indisponible'].includes(envoi?.etat);
		envoyer.disabled = choix.disabled = !ui.mutationsPermises || !bloc.peutInscrire || enCours || aConfirmer;
		// En scène, le Joueur rappe : la liste d'invitation revient après son Passage.
		invites.rendre({ vider: viderInvites, desactiver: envoyer.disabled, masquer: enScene });
		aide.textContent = disponibles.length ? (bloc.peutInscrire ? '' : 'Tu ne peux pas ajouter de Passage pour le moment.')
			: 'Aucune Instrumentale disponible pour le moment.';
		const texte = texteEnvoi(envoi);
		if (statut.textContent !== texte) statut.textContent = texte;
		reessayer.hidden = !ui.reessaiPossible(ui.dernierEchec(inscription));
		const tickets = bloc.mesPassagesEnAttente.map(passage => {
			const ticket = creerTicket(ui, passage, { personnel: true });
			const lignes = estInscripteur(passage, moi) ? lignesInvitations(bloc.invitationsEmises, passage.passageId) : [];
			if (lignes.length) {
				const liste = e('ul', 'karaoke__etat-invitations');
				liste.setAttribute('aria-label', 'Invitations');
				for (const ligne of lignes) liste.append(e('li', '', ligne));
				ticket.querySelector('.mon-ticket__bas').append(liste);
			}
			return ticket;
		});
		ui.remplacerListe(file, tickets.length ? tickets : [e('p', '', 'Tu n’attends aucun Passage.')], titre);
	};
	ui.surManche = () => {
		choixPrecedents = undefined;
		vibrations.clear();
		votes.reinitialiser();
		scene.reinitialiser();
	};
	ui.surDestruction = () => clearInterval(decompte);
	return { afficher: ui.afficher, demonter: ui.demonter };
}
