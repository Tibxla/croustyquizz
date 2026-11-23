import { creerInterface, creerTicket, creerUuid, texteAutresPassages } from './commun.mjs';
import { creerCycleLecture } from './cycle-lecture.mjs';
import { estFichierAudio } from './fichiers.mjs';
import { creerLecteurLocal } from './lecteur-local.mjs';
import { delaiAppelRestant, determinerLignes, positionChronologique } from './paroles.mjs';
import { formaterTempsRestant, passageActif, teintePassage, libelleEtatTicketPublic } from './presentation.mjs';

export const etatEcranKaraoke = {
	dossier: null, fichiers: [], lecteur: null, lecteurId: null, aEteLectrice: false, sonDebloque: false,
};
export function reinitialiserEtatEcranPourTests() {
	Object.assign(etatEcranKaraoke, {
		dossier: null, fichiers: [], lecteur: null, lecteurId: null, aEteLectrice: false, sonDebloque: false,
	});
}

export function monter(zones, contexte) {
	const document = zones.principale.ownerDocument;
	const navigateur = document.defaultView;
	return creerEcranKaraoke(zones, contexte, {
		lecteur: etatEcranKaraoke.lecteur ?? creerLecteurLocal({
			document, creerUrl: fichier => navigateur.URL.createObjectURL(fichier), revoquerUrl: url => navigateur.URL.revokeObjectURL(url),
		}),
		document, performance: navigateur.performance,
		requestAnimationFrame: f => navigateur.requestAnimationFrame(f), cancelAnimationFrame: id => navigateur.cancelAnimationFrame(id),
		setTimeout: (f, ms) => navigateur.setTimeout(f, ms), clearTimeout: id => navigateur.clearTimeout(id),
	});
}

export function creerEcranKaraoke(zones, contexte, dependances) {
	const { document, requestAnimationFrame, cancelAnimationFrame, setTimeout, clearTimeout } = dependances;
	const page = etatEcranKaraoke;
	const lecteur = page.lecteur ??= dependances.lecteur;
	const lecteurId = page.lecteurId ??= creerUuid();
	const ui = creerInterface(zones, contexte, { annoncerConnexion: false }), { e, bouton, ecouter } = ui;
	const invitation = e('h2', 'karaoke__invitation'), prochain = e('div', 'karaoke__prochain');
	const minutes = e('p', 'karaoke__delai chiffres'), paroles = e('div', 'karaoke__paroles');
	ui.principale.append(invitation, prochain, minutes, paroles);
	const courantAffiche = e('div', 'karaoke__courant-salle');
	const titre = e('h2', '', 'Ensuite'), file = e('ol', 'rouleau'), autres = e('p');
	ui.laterale.append(courantAffiche, titre, file, autres);

	const choix = e('input');
	choix.type = 'file'; choix.setAttribute('webkitdirectory', ''); choix.multiple = true; choix.hidden = true;
	const commandesLecteur = e('div', 'karaoke__lecteur');
	commandesLecteur.tabIndex = -1;
	const choisir = bouton('Choisir le dossier de musique', () => {
		void cycle.debloquer();
		choix.click();
	});
	const activer = bouton('Activer le son', () => { void cycle.debloquer({ attendreVue: () => !ui.synchronisee }); });
	const aide = e('p'), alerteSon = e('p', '', 'Son bloqué');
	commandesLecteur.append(choisir, choix, aide, alerteSon, activer);
	ui.principale.append(commandesLecteur);
	ecouter(choix, 'change', () => {
		const fichiers = Array.from(choix.files);
		page.dossier = fichiers[0]?.webkitRelativePath?.split('/')[0] ?? '';
		page.fichiers = fichiers.filter(estFichierAudio);
		choix.value = '';
		cycle.choisirDossier(page.fichiers);
		rendreLecteur(cycle.etat());
	});
	let cycle, requetes, ticketCourant, descriptionCourant, etiquetteCourant, blocAffiche, trame, versionParoles, lignes = [], indexAffiche;
	function rendreLecteur(etat) {
		const avaitFocus = document.activeElement === activer;
		choisir.disabled = !ui.synchronisee;
		alerteSon.hidden = activer.hidden = etat.etat !== 'son-bloque' || etat.sonDebloque;
		if (avaitFocus && activer.hidden) (choisir.disabled ? commandesLecteur : choisir).focus();
		const nombre = page.fichiers.length;
		aide.textContent = etat.autreLecteur ? 'Cet écran affiche seulement.'
			: page.dossier === null ? '' : `${nombre} fichier${nombre > 1 ? 's' : ''} audio`;
	}
	function monterCycle() {
		requetes = new AbortController();
		const signal = requetes.signal;
		cycle = creerCycleLecture({
			lecteur, lecteurId, horloge: contexte, setTimeout, clearTimeout,
			envoyer: (methode, chemin, corps) => contexte.envoyer(methode, `${ui.racine()}${chemin}`, corps,
				{ entetes: { 'Idempotency-Key': creerUuid() }, signal, annoncer: false }),
			surEtat(etat) {
				page.aEteLectrice = etat.aEteLectrice;
				page.sonDebloque = etat.sonDebloque;
				rendreLecteur(etat);
			},
		});
		if (page.dossier !== null) cycle.reprendreDossier(page);
	}
	function detruireCycle() {
		requetes.abort();
		cycle.detruire();
	}
	monterCycle();
	ui.surManche = precedent => {
		if (!precedent) return;
		detruireCycle();
		monterCycle();
		ticketCourant = descriptionCourant = etiquetteCourant = undefined;
	};
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
		const debut = avantPremiere ? 0 : Math.max(0, indexEnCours - 1);
		const fin = avantPremiere ? 3 : indexEnCours + 3;
		paroles.replaceChildren(...lignes.slice(debut, fin).map((ligne, i) => e('p',
			debut + i === indexEnCours ? 'paroles__ligne paroles__ligne--en-cours paroles__chante' : 'paroles__ligne', ligne.texte)));
	}
	function actualiserTemps() {
		const bloc = ui.bloc;
		if (!bloc || !passageActif(bloc.courant)) return;
		if (!minutes.hidden) {
			const restant = delaiAppelRestant({ ...bloc.courant, maintenant: contexte.maintenantServeur(), enPause: bloc.etat === 'EN_PAUSE' });
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
		const animee = !document.hidden && passageActif(ui.bloc?.courant);
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
		if (origine || blocAffiche !== bloc) {
			blocAffiche = bloc;
			cycle.surVue(bloc, origine);
		}
		const { courant, prochainsPassages: passages, lecture } = bloc;
		const actif = passageActif(courant), appel = actif?.etat === 'APPELE', pause = bloc.etat === 'EN_PAUSE';
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
		rendreLecteur(cycle.etat());
		actualiserTemps();
		planifier();
	};
	ui.surDestruction = () => {
		if (trame !== undefined) cancelAnimationFrame(trame);
		detruireCycle();
	};
	return { afficher: ui.afficher, demonter: ui.demonter };
}
