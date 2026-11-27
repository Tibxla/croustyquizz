import { creerInterface, creerTicket, creerUuid, texteAutresPassages } from './commun.mjs';
import { creerCycleLecture } from './cycle-lecture.mjs';
import { delaiAppelRestant, determinerLignes, fenetreParoles, positionChronologique } from './paroles.mjs';
import { formaterTempsRestant, passageActif, teintePassage, libelleEtatTicketPublic } from './presentation.mjs';
import { delaiVoteRestant, formaterMoyenne, formaterNombreVotes, votesVisiblesSalle } from './vote.mjs';

const designations = new WeakMap();

export function monter(zones, contexte) {
	const document = zones.principale.ownerDocument;
	const navigateur = document.defaultView;
	return creerEcranKaraoke(zones, contexte, {
		document, performance: navigateur.performance,
		requestAnimationFrame: f => navigateur.requestAnimationFrame(f), cancelAnimationFrame: id => navigateur.cancelAnimationFrame(id),
	});
}

export function creerEcranKaraoke(zones, contexte, dependances) {
	const { document, requestAnimationFrame, cancelAnimationFrame } = dependances;
	const audio = contexte.audioSalle;
	const { lecteur, lecteurId } = audio;
	const designation = designations.get(audio) ?? { fichiers: null, aEteLectrice: false };
	designations.set(audio, designation);
	const ui = creerInterface(zones, contexte, { annoncerConnexion: false }), { e, ecouter } = ui;
	const invitation = e('h2', 'karaoke__invitation'), prochain = e('div', 'karaoke__prochain');
	const minutes = e('p', 'karaoke__delai chiffres'), paroles = e('div', 'karaoke__paroles');
	ui.principale.append(invitation, prochain, minutes, paroles);
	const listeVotes = e('div', 'karaoke__bandeaux-vote');
	const bandeaux = new Map();
	listeVotes.hidden = true;
	const courantAffiche = e('div', 'karaoke__courant-salle');
	const titre = e('h2', '', 'Ensuite'), file = e('ol', 'rouleau'), autres = e('p');
	ui.laterale.append(listeVotes, courantAffiche, titre, file, autres);

	const autreLecteur = e('p', '', 'Un autre Écran de salle est désigné pour lire les Chansons.');
	autreLecteur.hidden = true;
	ui.principale.append(autreLecteur);
	let cycle, requetes, ticketCourant, descriptionCourant, etiquetteCourant, blocAffiche, trame, versionParoles, lignes = [], indexAffiche;
	let votesChronometres = false;
	function actualiserDossier({ fichiers, sonDebloque } = audio.etat()) {
		if (ui.synchronisee && fichiers !== designation.fichiers) {
			designation.fichiers = fichiers;
			cycle.choisirDossier(fichiers);
		}
		if (sonDebloque && !cycle.etat().sonDebloque) void cycle.debloquer({ attendreVue: () => !ui.synchronisee });
	}
	function monterCycle() {
		requetes = new AbortController();
		const signal = requetes.signal;
		cycle = creerCycleLecture({
			lecteur, lecteurId,
			envoyer: (methode, chemin, corps) => contexte.envoyer(methode, `${ui.racine()}${chemin}`, corps,
				{ entetes: { 'Idempotency-Key': creerUuid() }, signal, annoncer: false }),
			surEtat(etat) {
				designation.aEteLectrice = etat.aEteLectrice;
				autreLecteur.hidden = !etat.autreLecteur;
			},
		});
		const dossier = audio.etat();
		if (dossier.fichiers !== null) {
			cycle.reprendreDossier({ ...dossier, aEteLectrice: designation.aEteLectrice || dossier.fichiers !== designation.fichiers });
			designation.fichiers = dossier.fichiers;
		}
	}
	function detruireCycle() {
		requetes.abort();
		cycle.detruire();
	}
	monterCycle();
	const retirerAudio = audio.surChangement(actualiserDossier);
	ui.surManche = precedent => {
		if (!precedent) return;
		detruireCycle();
		monterCycle();
		ticketCourant = descriptionCourant = etiquetteCourant = undefined;
		bandeaux.clear();
	};
	function creerBandeau(id) {
		const bandeau = e('article', 'bandeau-vote');
		bandeau.setAttribute('data-passage-id', id);
		const numero = e('div', 'bandeau-vote__numero');
		const corps = e('div', 'bandeau-vote__corps');
		const chanson = e('p', 'bandeau-vote__chanson'), interpretes = e('p');
		const invitationVote = e('p', '', 'Note ce Passage sur ta Manette');
		const donnees = e('div', 'bandeau-vote__donnees');
		const moyenne = e('p', 'bandeau-vote__moyenne');
		const nombre = e('p', 'bandeau-vote__nombre'), temps = e('p');
		donnees.append(moyenne, nombre, temps);
		corps.append(chanson, interpretes, invitationVote, donnees);
		bandeau.append(numero, corps);
		return { bandeau, numero, chanson, interpretes, invitationVote, moyenne, nombre, temps };
	}
	function rendreVotes(bloc, maintenant) {
		const visibles = votesVisiblesSalle(bloc, maintenant);
		const pause = bloc.etat === 'EN_PAUSE';
		votesChronometres = false;
		for (const id of bandeaux.keys()) {
			if (!visibles.some(vote => vote.passageId === id)) bandeaux.delete(id);
		}
		const ordre = visibles.map(vote => {
			const fiche = bandeaux.get(vote.passageId) ?? creerBandeau(vote.passageId);
			const ouvert = vote.etat === 'OUVERT';
			if (fiche.vote !== vote) {
				fiche.vote = vote;
				bandeaux.set(vote.passageId, fiche);
				fiche.bandeau.style.setProperty('--teinte', `var(--${teintePassage(vote.numero)})`);
				fiche.numero.textContent = vote.numero;
				fiche.chanson.textContent = vote.chanson.titre;
				fiche.interpretes.textContent = vote.interpretes.map(interprete => interprete.pseudo).join(', ');
				fiche.invitationVote.hidden = fiche.temps.hidden = !ouvert;
				fiche.moyenne.hidden = ouvert || vote.moyenneDixiemes == null;
				fiche.moyenne.textContent = fiche.moyenne.hidden ? '' : formaterMoyenne(vote.moyenneDixiemes);
				fiche.nombre.textContent = formaterNombreVotes(vote.nombreVotes);
				if (!ouvert) fiche.temps.textContent = '';
			}
			if (ouvert) {
				const restant = delaiVoteRestant(vote, maintenant, pause);
				const texte = (pause ? 'Pause · ' : '') + formaterTempsRestant(restant);
				if (fiche.temps.textContent !== texte) fiche.temps.textContent = texte;
				votesChronometres ||= !pause && restant > 0;
			} else votesChronometres ||= vote.suivantDemarre;
			return fiche.bandeau;
		});
		listeVotes.hidden = ordre.length === 0;
		if (ordre.length !== listeVotes.childElementCount || ordre.some((bandeau, i) => listeVotes.children[i] !== bandeau)) {
			listeVotes.replaceChildren(...ordre);
		}
	}
	function rendreCourant(courant, origine) {
		const description = courant ? JSON.stringify([courant.passageId, courant.numero, courant.chanson.titre,
			courant.chanson.artiste, courant.interpretes.map(i => i.pseudo), Boolean(courant.prioritaire)]) : null;
		if (description !== descriptionCourant) {
			descriptionCourant = description;
			courantAffiche.hidden = !courant;
			if (!courant) {
				courantAffiche.replaceChildren();
				ticketCourant = etiquetteCourant = undefined;
				return;
			}
			const memePassage = ticketCourant?.getAttribute('data-passage-id') === courant.passageId;
			ticketCourant = creerTicket(ui, courant, { courant: true });
			ticketCourant.classList.add('ticket--detache');
			if (memePassage || origine?.source !== 'evenement') ticketCourant.classList.add('ticket--immobile');
			etiquetteCourant = ticketCourant.querySelector('.karaoke__etat-ticket');
			courantAffiche.replaceChildren(ticketCourant);
		}
		if (etiquetteCourant) {
			const libelle = libelleEtatTicketPublic(courant);
			if (etiquetteCourant.textContent !== libelle) etiquetteCourant.textContent = libelle;
		}
	}
	function rendreParoles(bloc) {
		const { courant, lecture } = bloc;
		if (!bloc.paroles || paroles.hidden) return;
		if (versionParoles !== bloc.paroles.parolesId) {
			versionParoles = bloc.paroles.parolesId;
			lignes = bloc.paroles.lignes.filter(ligne => ligne.texte.trim());
			indexAffiche = undefined;
		}
		const etat = cycle.etat(), maintenant = contexte.maintenantServeur();
		const joue = etat.etat === 'lecture' || etat.etat === 'pause';
		const dureeReferenceMs = joue && etat.dureeMs > 0 ? etat.dureeMs : bloc.paroles.dureeMs;
		const position = joue ? Math.min(lecteur.position(), dureeReferenceMs) : positionChronologique({
			...courant, maintenant, enPause: bloc.etat === 'EN_PAUSE', dureeReferenceMs,
		});
		const { indexEnCours, avantPremiere } = determinerLignes(lignes, position, lecture?.decalageMs ?? 0);
		paroles.style.setProperty('--teinte-passage', `var(--${teintePassage(courant.numero)})`);
		if (indexEnCours === indexAffiche) return;
		indexAffiche = indexEnCours;
		// Une ligne chantée longue laisse moins de voisines, pour que la zone principale ne déborde pas (#276).
		const { debut, fin, longueur } = fenetreParoles(lignes, indexEnCours, avantPremiere);
		const enCours = `paroles__ligne paroles__ligne--en-cours paroles__chante${longueur === 'tres-longue' ? ' paroles__ligne--longue' : ''}`;
		paroles.replaceChildren(...lignes.slice(debut, fin).map((ligne, i) => e('p',
			debut + i === indexEnCours ? enCours : 'paroles__ligne', ligne.texte)));
	}
	function actualiserTemps() {
		const bloc = ui.bloc;
		if (!bloc) return;
		const maintenant = contexte.maintenantServeur();
		rendreVotes(bloc, maintenant);
		if (!passageActif(bloc.courant)) return;
		if (!minutes.hidden) {
			const restant = delaiAppelRestant({ ...bloc.courant, maintenant, enPause: bloc.etat === 'EN_PAUSE' });
			const texte = formaterTempsRestant(restant);
			if (minutes.textContent !== texte) minutes.textContent = texte;
		}
		rendreParoles(bloc);
	}
	function animer() {
		trame = undefined;
		actualiserTemps();
		planifier();
	}
	function planifier() {
		const animee = !document.hidden && (passageActif(ui.bloc?.courant) || votesChronometres);
		if (animee && trame === undefined) trame = requestAnimationFrame(animer);
		if (!animee && trame !== undefined) {
			cancelAnimationFrame(trame);
			trame = undefined;
		}
	}
	ecouter(document, 'visibilitychange', () => { if (!document.hidden) actualiserTemps(); planifier(); });
	ui.surRendu = origine => {
		const bloc = ui.bloc;
		if (!bloc) return;
		const { courant, prochainsPassages: passages, lecture } = bloc;
		const actif = passageActif(courant), appel = actif?.etat === 'APPELE', pause = bloc.etat === 'EN_PAUSE';
		if (origine || blocAffiche !== bloc) {
			blocAffiche = bloc;
			const sonDeManche = Boolean(actif && !appel);
			if (sonDeManche) void contexte.signalerSonDeManche(true);
			cycle.surVue(bloc, origine);
			if (!sonDeManche) void contexte.signalerSonDeManche(false);
		}
		actualiserDossier();
		const enErreur = lecture?.etat === 'EN_ERREUR';
		if (!actif) invitation.textContent = passages.length ? 'Prépare-toi.' : 'Choisis ta Chanson sur ta Manette.';
		else if (enErreur) invitation.textContent = "La musique ne se lance pas. L'Animateur choisit la suite.";
		else if (appel) invitation.textContent = pause ? 'Prépare-toi · Pause' : 'Prépare-toi';
		else invitation.textContent = pause ? 'Pause' : '';
		invitation.hidden = !invitation.textContent;
		prochain.hidden = Boolean(actif) || !passages.length;
		prochain.replaceChildren(...(prochain.hidden ? [] : [creerTicket(ui, passages[0])]));
		minutes.hidden = !appel || enErreur;
		paroles.hidden = !actif || appel || enErreur || !bloc.paroles;
		if (paroles.hidden) {
			paroles.replaceChildren();
			versionParoles = indexAffiche = undefined;
		}

		rendreCourant(courant, origine);
		const suivants = actif ? passages : passages.slice(1);
		file.hidden = !suivants.length; titre.hidden = !suivants.length && !bloc.nombreAutresPassages;
		file.replaceChildren(...suivants.map(p => { const li = e('li'); li.append(creerTicket(ui, p)); return li; }));
		autres.hidden = !bloc.nombreAutresPassages;
		autres.textContent = texteAutresPassages(bloc.nombreAutresPassages);
		actualiserTemps();
		planifier();
	};
	ui.surDestruction = () => {
		if (trame !== undefined) cancelAnimationFrame(trame);
		retirerAudio();
		detruireCycle();
		void contexte.signalerSonDeManche(false);
	};
	return { afficher: ui.afficher, demonter: ui.demonter };
}
