import { appelerSuivant, piloter, prochainPassage } from '../passage/pilotage.mjs';
import { creerInterface, creerTicket, creerCatalogue, texteEnvoi } from './commun.mjs';
import { joindrePseudos } from './invitations.mjs';
import { preparerPlacement } from './placement.mjs';
import { formaterDecalage, formaterTempsRestant, passageActif } from './presentation.mjs';
import { delaiAppelRestant, tempsAvantFinPrevue } from './paroles.mjs';
import { delaiVoteRestant, formaterMoyenne, formaterNombreVotes } from './vote.mjs';

// Bornes du contrat : limitePassagesEnAttente vaut de 1 à 20 (docs/api/README.md, File d'attente).
const LIMITE_PASSAGES_MAX = 20;
const phases = { APPELE: 'Appelé', EN_COURS: 'En scène', EN_PAUSE: 'En pause', TERMINE: 'Terminé', SAUTE: 'Sauté' };
const motifsLecture = {
	FICHIER_ABSENT: 'Paroles seules : cette Chanson n’est pas dans ton dossier de musique',
	FICHIER_AMBIGU: 'Paroles seules : plusieurs morceaux de ton dossier portent ce titre',
	FICHIER_ILLISIBLE: 'Paroles seules : le morceau de ton dossier est illisible',
	ECRAN_NON_PRET: 'Paroles seules : l’Écran de salle n’était pas prêt',
	ERREUR_LECTURE: 'Paroles seules après une erreur de lecture',
};

function installerPassage(ui, commande) {
	const { e, bouton } = ui;
	const barre = e('div', 'karaoke__barre-passage');
	const pilotage = e('div', 'karaoke__commandes'), calage = e('div', 'karaoke__commandes karaoke__calage');
	pilotage.setAttribute('role', 'group'); pilotage.setAttribute('aria-label', 'Pilotage du Passage');
	// Le calage se lit sans légende : un titre, ce que l'Animateur constate, puis le décalage en cours (#279).
	const titreCalage = e('h3', 'karaoke__calage-titre', 'Caler les paroles');
	titreCalage.id = 'karaoke-calage-titre';
	calage.setAttribute('role', 'group'); calage.setAttribute('aria-labelledby', titreCalage.id);
	calage.append(titreCalage);
	const statut = e('p'); statut.setAttribute('role', 'status');
	let reessayer;
	barre.append(pilotage, calage, statut);
	const fiche = e('section', 'karaoke__passage-courant'), titre = e('h2', '', 'Passage courant'); titre.tabIndex = -1;
	const identite = e('p'), phase = e('p'), temps = e('p', 'chiffres'), source = e('p'), lecteur = e('p'),
		decalage = e('p', 'karaoke__decalage'), mesure = e('p'), erreur = e('p');
	erreur.setAttribute('role', 'status');
	fiche.append(titre, identite, phase, temps, source, lecteur, mesure, erreur);
	const votes = e('section', 'karaoke__votes');
	const titreVotes = e('h2', '', 'Vote du public');
	titreVotes.id = 'karaoke-votes-console';
	votes.setAttribute('aria-labelledby', titreVotes.id);
	const listeVotes = e('ul', 'karaoke__liste-votes');
	votes.hidden = true;
	votes.append(titreVotes, listeVotes);
	// Le Passage courant ouvre la colonne, ses commandes juste dessous (#282).
	ui.principale.append(fiche, barre, votes);
	let delaisVote = [];

	const prochain = () => prochainPassage(ui.bloc);
	const envoyerPilotage = action => piloter(ui, commande, action);
	const basculer = bouton('Pause', () => envoyerPilotage(ui.bloc.etat === 'EN_PAUSE' ? 'REPRENDRE' : 'METTRE_EN_PAUSE'));
	const passer = bouton('Passer', () => envoyerPilotage('PASSER_ETAPE'));
	const terminer = bouton('Terminer la Manche', () => {
		const mancheId = ui.bloc.mancheId;
		const d = ui.dialogue({ titre: 'Terminer la Manche ?', declencheur: terminer, valide: () => ui.bloc.mancheId === mancheId,
			confirmation: { libelle: 'Terminer la Manche', permise: () => ui.mutationsPermises, executer: () => envoyerPilotage('TERMINER') } });
		const palmares = 'Le Palmarès s’affiche sur l’Écran de salle.';
		d.contenu.append(e('p', '', passageActif(ui.bloc.courant) ? `Le Passage en cours s’arrête. ${palmares}` : palmares));
	});
	const appeler = bouton('Appeler le suivant', () => appelerSuivant(ui, commande), 'bouton bouton--action');
	const appelAutomatique = e('span', 'console__note', 'Appel automatique');
	const boutonsPilotage = [basculer, passer, terminer, appeler];
	pilotage.append(...boutonsPilotage);
	function actionLecture(libelle, type, suffixe, sens) {
		const action = bouton(libelle, () => {
			const { courant, lecture } = ui.bloc;
			if (!courant || !lecture) return;
			const corps = { etapeId: courant.etapeId, lectureId: lecture.lectureId };
			if (sens) Object.assign(corps, { numeroModificationCalage: lecture.numeroModificationCalage, sens });
			ui.commande(commande('POST', `/passages/${encodeURIComponent(courant.passageId)}/${suffixe}`, corps, { type }));
		});
		calage.append(action);
		return action;
	}
	const caler = [
		actionLecture('Les paroles commencent', 'MARQUER_DEBUT', 'calage/debut'),
		// Des paroles en retard sur la musique s'avancent d'un dixième de seconde, et inversement.
		actionLecture('Paroles en retard', 'CORRIGER_CALAGE', 'calage/correction', 'AVANCER'),
		actionLecture('Paroles en avance', 'CORRIGER_CALAGE', 'calage/correction', 'RETARDER'),
	];
	const parolesSeules = actionLecture('Paroles seules', 'REPRENDRE_EN_PAROLES_SEULES', 'paroles-seules');
	calage.append(decalage);
	function decompter() {
		const bloc = ui.bloc;
		const p = bloc?.courant;
		if (p?.etat === 'APPELE') {
			const restant = delaiAppelRestant({ ...p, maintenant: ui.contexte.maintenantServeur(), enPause: bloc.etat === 'EN_PAUSE' });
			temps.textContent = formaterTempsRestant(restant);
		} else if (p?.etat === 'EN_COURS' && p.finPrevueLe != null && bloc.etat === 'EN_COURS') {
			const restant = tempsAvantFinPrevue({ finPrevueLe: p.finPrevueLe, maintenant: ui.contexte.maintenantServeur() });
			temps.textContent = `Fin prévue dans ${formaterTempsRestant(restant)}`;
		} else temps.textContent = '';
		const maintenant = ui.contexte.maintenantServeur();
		for (const { vote, delai } of delaisVote) {
			delai.textContent = bloc.etat === 'EN_PAUSE' ? 'en pause'
				: formaterTempsRestant(delaiVoteRestant(vote, maintenant, false));
		}
	}
	const horloge = setInterval(decompter, 1000);
	return {
		rendre(automatique) {
			const bloc = ui.bloc, permis = ui.mutationsPermises, p = bloc.courant, lecture = bloc.lecture;
			const enPause = bloc.etat === 'EN_PAUSE';
			const focusPilotage = boutonsPilotage.indexOf(ui.document.activeElement);
			basculer.textContent = enPause ? 'Reprendre' : 'Pause';
			basculer.disabled = !permis || !(enPause ? bloc.peutReprendre : bloc.etat === 'EN_COURS');
			passer.disabled = !permis || !bloc.peutPasser;
			terminer.disabled = !permis;
			appeler.disabled = automatique || !permis || !bloc.peutAppeler || !prochain();
			const actionAppel = automatique ? appelAutomatique : appeler;
			if (!pilotage.contains(actionAppel)) {
				appeler.remove();
				appelAutomatique.remove();
				pilotage.append(actionAppel);
			}
			if (focusPilotage !== -1 && boutonsPilotage[focusPilotage].disabled) {
				const suivant = boutonsPilotage.slice(focusPilotage + 1).find(action => !action.disabled);
				(suivant ?? titre).focus();
			}
			const focusCalage = ui.document.activeElement;
			const avaitFocusCalage = calage.contains(focusCalage);
			calage.hidden = !bloc.peutCaler && !bloc.peutReprendreEnParolesSeules;
			for (const action of caler) action.disabled = !permis || !bloc.peutCaler;
			parolesSeules.disabled = !permis || !bloc.peutReprendreEnParolesSeules;
			if (avaitFocusCalage && (calage.hidden || focusCalage.disabled)) {
				(!calage.hidden && !parolesSeules.disabled ? parolesSeules : titre).focus();
			}
			const envoi = ui.dernierEnvoi(estCommandePassage);
			const texte = ui.csrfInvalide && envoi ? 'Recharge la Console pour rétablir la session. ' + texteEnvoi(envoi) : texteEnvoi(envoi);
			if (statut.textContent !== texte) statut.textContent = texte;
			if (ui.reessaiPossible(ui.dernierEchec(estCommandePassage))) {
				if (!reessayer) {
					reessayer = bouton('Réessayer le même envoi', () => ui.reessayer(ui.dernierEchec(estCommandePassage)?.cle));
					barre.append(reessayer);
				}
			} else if (reessayer) {
				if (ui.document.activeElement === reessayer) titre.focus();
				reessayer.remove(); reessayer = undefined;
			}
			identite.textContent = p ? `N° ${p.numero} · ${p.interpretes.map(i => i.pseudo).join(', ')} · ${p.chanson.titre} · ${p.chanson.artiste}`
				: 'Aucun Passage appelé.';
			phase.textContent = p ? phases[p.etat] : '';
			if (enPause && p?.etat !== 'EN_PAUSE') phase.textContent = phase.textContent ? phase.textContent + ' · En pause' : 'En pause';
			// La Console parle de musique, pas de fichier (#279, M10).
			if (lecture?.source === 'FICHIER_LOCAL') source.textContent = 'Musique : ton dossier';
			else source.textContent = lecture ? motifsLecture[lecture.motifParolesSeules]
				: p?.etat === 'APPELE' && bloc.lecteurDesigne ? 'Préparation de la musique…' : '';
			if (lecture?.origineParoles === 'REPLI_INSCRIPTION') source.textContent += ' · Paroles d’origine : aucune version calée sur ta musique';
			// Un motif de paroles seules suffit : la ligne de l'Écran ne le répète pas.
			lecteur.textContent = bloc.lecteurDesigne ? 'La télé joue la musique'
				: source.textContent.startsWith('Paroles seules') ? '' : 'Pas de musique sur l’Écran : paroles seules';
			decalage.textContent = lecture ? `Décalage : ${formaterDecalage(lecture.decalageMs)}` : '';
			mesure.textContent = lecture?.demandePosition ? 'Calage en attente de l’Écran de salle' : '';
			const messageErreur = lecture?.etat === 'EN_ERREUR' ? 'Erreur de lecture : reprends en paroles seules ou passe le Passage.' : '';
			if (erreur.textContent !== messageErreur) erreur.textContent = messageErreur;
			delaisVote = [];
			listeVotes.replaceChildren(...(bloc.votes ?? []).map(vote => {
				const ligne = e('li');
				const identite = `n° ${vote.numero} · ${vote.chanson.titre}`;
				const nombre = formaterNombreVotes(vote.nombreVotes);
				if (vote.etat === 'CLOS') {
					const resultat = vote.moyenneDixiemes == null ? nombre : `${formaterMoyenne(vote.moyenneDixiemes)} · ${nombre}`;
					ligne.textContent = `${identite} · Vote clos · ${resultat}`;
				} else {
					const delai = e('span', 'chiffres');
					ligne.append(e('span', '', `${identite} · ${nombre} · `), delai);
					delaisVote.push({ vote, delai });
				}
				return ligne;
			}));
			votes.hidden = listeVotes.childElementCount === 0;
			decompter();
		},
		detruire: () => clearInterval(horloge),
	};
}

const estCommandePassage = envoi => Boolean(envoi.commande.type);
const estCommandeFile = envoi => !estCommandePassage(envoi);

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte), { e, bouton, ecouter } = ui;
	let automatique = false;
	const commande = (methode, chemin, corps, metadata) => ({ methode, chemin: `${ui.racine()}${chemin}`, corps,
		mancheId: ui.bloc.mancheId, acteurType: 'ANIMATEUR', ...metadata });
	const passage = installerPassage(ui, commande);
	const titre = e('h2', '', "File d'attente"); titre.tabIndex = -1;
	const limite = e('form', 'karaoke__limite champ'), label = e('label', '', 'Passages en attente au plus par Joueur');
	const champ = e('input'); champ.id = 'karaoke-limite'; champ.type = 'number'; champ.min = '1'; champ.max = String(LIMITE_PASSAGES_MAX); champ.step = '1';
	label.setAttribute('for', champ.id);
	const erreur = e('p', 'champ--erreur'); erreur.id = 'karaoke-limite-erreur'; erreur.hidden = true; champ.setAttribute('aria-describedby', erreur.id);
	const appliquer = bouton('Appliquer', () => {}); appliquer.type = 'submit';
	const valeurSoumise = e('p'); valeurSoumise.setAttribute('role', 'status');
	limite.append(label, champ, appliquer, erreur, valeurSoumise);
	const file = e('ol', 'rouleau a-venir'), vide = e('p', '', 'Aucun Passage en attente.');
	const commandes = e('div', 'karaoke__commandes'), message = e('p'); message.setAttribute('role', 'status');
	// La File suit le Passage courant ; la limite, réglée une fois pour la Manche, passe après (#282).
	ui.principale.append(titre, file, vide, commandes, message, limite);
	const catalogue = creerCatalogue(ui, ui.laterale, { replie: true });
	let selection = null;
	ui.messageInvalidation = () => { message.textContent = 'Le Passage a changé, vérifie avant de recommencer.'; };
	const passages = () => ui.bloc.passagesEnAttente;
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
		const cmd = action === 'retirer' ? commande('POST', `/passages/${encodeURIComponent(cible.passageId)}/retrait`, { numeroModification: cible.numeroModification }) :
			commande('PUT', '/file-attente/priorite', { passage: { passageId: cible.passageId, numeroModification: cible.numeroModification }, prioritaire: !cible.prioritaire });
		const d = ui.dialogue({ titre: libelle, declencheur, valide, confirmation: {
			libelle: action === 'retirer' ? 'Retirer ce Passage' : cible.prioritaire ? 'Lever la priorité' : 'Donner la priorité',
			permise: () => ui.mutationsPermises && ui.bloc.peutGererFile,
			executer: () => ui.commande(cmd),
		} });
		d.contenu.append(e('p', '', `${libelle} · Passage ${cible.numero} · ${cible.chanson.titre} ?`));
		if (ancienne) d.contenu.append(e('p', '', `Remplace la priorité du Passage ${ancienne.numero} · ${ancienne.chanson.titre}.`));
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
		passage.rendre(automatique);
		if (!passages().some(p => p.passageId === selection)) selection = null;
		const envoiLimite = [...ui.envois.values()].filter(e => e.commande.chemin.endsWith('/limite')).at(-1);
		if (ui.document.activeElement !== champ && envoiLimite?.etat !== 'en-cours') champ.value = String(ui.bloc.limitePassagesEnAttente);
		appliquer.disabled = !ui.mutationsPermises || !ui.bloc.peutReglerLimite;
		valeurSoumise.textContent = envoiLimite?.etat === 'en-cours' ? `Limite envoyée : ${envoiLimite.commande.corps.limitePassagesEnAttente} · Envoi en cours…` : texteEnvoi(envoiLimite);
		const tickets = [...passages()].sort((a, b) => a.position - b.position).map(p => {
			const li = e('li');
			const ticket = creerTicket(ui, p, { estimation: true, details: true, selectionner: id => { selection = id; message.textContent = ''; ui.rendre(); } });
			// Les Invitations en suspens ne sont pas encore des Interprètes : la Console les nomme à part (#135).
			const enSuspens = (ui.bloc.invitationsEnAttente ?? []).filter(i => i.passageId === p.passageId).map(i => i.invite.pseudo);
			if (enSuspens.length) {
				const texte = `Invités sans réponse : ${joindrePseudos(enSuspens)}`;
				ticket.querySelector('.ticket__corps').append(e('p', 'karaoke__attente', texte));
				// L'aria-label du ticket masque son contenu : il nomme aussi les invités en suspens.
				ticket.setAttribute('aria-label', `${ticket.getAttribute('aria-label')} · ${texte}`);
			}
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
		const dernier = ui.dernierEnvoi(estCommandeFile);
		if (dernier) {
			const statut = e('p', '', ui.csrfInvalide ? 'Recharge la Console pour rétablir la session. ' + texteEnvoi(dernier) : texteEnvoi(dernier)); statut.setAttribute('role', 'status'); actions.push(statut);
		}
		const echec = ui.dernierEchec(estCommandeFile);
		if (ui.reessaiPossible(echec)) {
			if (echec !== dernier) actions.push(e('p', '', texteEnvoi(echec)));
			actions.push(bouton('Réessayer le même envoi', () => ui.reessayer(echec.cle)));
		}
		if (ui.csrfInvalide && !dernier && !ui.dernierEnvoi(estCommandePassage)) actions.push(e('p', '', 'Recharge la Console pour rétablir la session.'));
		const actif = ui.document.activeElement, focusCommande = commandes.contains(actif);
		const nomCommande = focusCommande ? actif.textContent : null;
		commandes.replaceChildren(...actions);
		if (focusCommande) {
			const equivalente = actions.find(a => a.tagName === 'BUTTON' && a.textContent === nomCommande && !a.disabled);
			(equivalente ?? Array.from(file.querySelectorAll('button')).find(b => b.getAttribute('aria-pressed') === 'true') ?? titre).focus();
		}
		catalogue.rendre();
	};
	ui.surManche = () => { selection = null; catalogue.reinitialiser(); message.textContent = ''; };
	ui.surDestruction = () => { passage.detruire(); catalogue.detruire(); };
	return {
		afficher(vue, origine) {
			automatique = vue.soiree?.pilotage === 'AUTOMATIQUE';
			ui.afficher(vue, origine);
		},
		demonter: ui.demonter,
	};
}
