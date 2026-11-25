import { texteEnvoi } from './commun.mjs';
import { creerEnregistrement, diagnostiquer } from './enregistrement.mjs';

const occupeMicro = etat => ['demande-micro', 'micro', 'enregistrement', 'pause', 'finalisation'].includes(etat);
const gardeEcranAllume = etat => ['micro', 'enregistrement', 'pause'].includes(etat);
const ACTIONS = ['choix', 'micro', 'reessayer'];
const messages = {
	accord: 'Autorise le micro avant le départ. Ton Enregistrement reste sur ce téléphone.',
	'demande-micro': 'En attente de ton autorisation pour le micro…',
	micro: 'Micro prêt. Garde cette page ouverte et l’écran allumé jusqu’à la fin du Passage.',
	enregistrement: 'Enregistrement en cours. Garde cette page ouverte : un rechargement perd la prise.',
	pause: 'Enregistrement en pause. Garde cette page ouverte : un rechargement perd la prise.',
	finalisation: 'Préparation de ton Enregistrement…',
	pret: 'Ton Enregistrement est prêt, dans « Tes Enregistrements ».',
	interrompu: 'Ta prise a été interrompue. Retrouve-la dans « Tes Enregistrements ».',
	'capture-vide': 'La prise n’a capté aucun son.',
	'micro-coupe': 'Le micro s’est coupé. Autorise-le de nouveau avant le départ.',
	'micro-refuse': 'Le micro est refusé. Ton Passage aura lieu sans Enregistrement.',
	indisponible: 'Ce navigateur ne peut pas enregistrer le micro. Ton Passage peut continuer.',
	'micro-non-autorise': 'Le micro n’a pas été autorisé avant le départ. Ton Passage continue sans Enregistrement.',
	'demande-en-cours': 'Le Passage a démarré avant ta réponse pour le micro. Ton Passage continue sans Enregistrement.',
	'depart-manque': 'La prise n’a pas pu démarrer avec le Passage. Ton Passage continue sans Enregistrement.',
	rechargement: 'Le Passage a déjà commencé. Une prise ne redémarre pas après un rechargement.',
	'capture-impossible': 'La prise a échoué. Ton Passage peut continuer.',
};
const impossibilites = {
	'page-non-securisee': 'Enregistrement impossible : la page n’est pas en HTTPS.',
	'navigateur-incompatible': 'Enregistrement impossible : ce navigateur ne sait pas enregistrer le micro.',
	'format-absent': 'Enregistrement impossible : ce navigateur n’enregistre ni en MP4 ni en WebM.',
};
const AVERTISSEMENT = 'Ils restent sur ce téléphone tant que cette page reste ouverte, même après la Manche. '
	+ 'Un rechargement ou la fermeture de la page les efface : télécharge-les, puis retire-les.';

export function nomFichier(titre, depart, fichier, maintenant = Date.now()) {
	const chanson = (titre ?? '').replace(/[<>:"/\\|?*\u0000-\u001F]/g, ' ').trim().replace(/\s+/g, ' ').slice(0, 100);
	const instant = Date.parse(depart ?? '');
	const date = new Date(Number.isNaN(instant) ? maintenant : instant);
	const jour = [date.getFullYear(), date.getMonth() + 1, date.getDate()]
		.map(nombre => String(nombre).padStart(2, '0')).join('-');
	return `${chanson || 'Passage'} - ${jour}.${fichier.type.startsWith('audio/mp4') ? 'm4a' : 'webm'}`;
}

// Les fichiers prêts vivent hors du module de Manche, un registre par page : ils survivent au démontage
// et reviennent au montage suivant. Seuls le retrait confirmé et pagehide révoquent leurs URL.
// Rien n'est écrit sur le disque : un rechargement les perd, et la Manette le dit.
const registres = new WeakMap();
function registreDe(page) {
	const existant = registres.get(page);
	if (existant) return existant;
	const fichiers = new Map(), abonnes = new Set();
	const prevenir = () => [...abonnes].forEach(rappel => rappel());
	const revoquer = entree => { if (entree.url) URL.revokeObjectURL(entree.url); };
	const registre = {
		fichiers, abonnes,
		ajouter(entree) {
			const ancienne = fichiers.get(entree.id);
			if (ancienne) { revoquer(ancienne); fichiers.delete(entree.id); }
			fichiers.set(entree.id, entree);
			prevenir();
		},
		retirer(id) {
			const entree = fichiers.get(id);
			if (!entree) return;
			revoquer(entree);
			fichiers.delete(id);
			prevenir();
		},
	};
	page.defaultView?.addEventListener?.('pagehide', evenement => {
		// Une page gardée en cache (persisted) peut revenir telle quelle : ses liens restent valides.
		if (evenement?.persisted) return;
		for (const entree of fichiers.values()) revoquer(entree);
		fichiers.clear();
		prevenir();
	});
	registres.set(page, registre);
	return registre;
}

// Un verrou d'écran par Manette, voulu tant qu'un micro est ouvert. Le système le libère quand la page
// passe en arrière-plan : il se redemande quand elle redevient visible. Sans wakeLock, rien ne se passe.
function creerVerrouEcran(navigateur, page) {
	const wakeLock = navigateur?.wakeLock;
	let voulu = false, sentinelle = null, demande = false, fini = false;
	const relacher = verrou => { Promise.resolve().then(() => verrou.release()).catch(() => {}); };
	function demander() {
		if (!wakeLock || fini || !voulu || sentinelle || demande || page.visibilityState === 'hidden') return;
		demande = true;
		Promise.resolve().then(() => wakeLock.request('screen')).then(obtenu => {
			demande = false;
			if (fini || !voulu) { relacher(obtenu); return; }
			sentinelle = obtenu;
			obtenu.addEventListener?.('release', () => { if (sentinelle === obtenu) sentinelle = null; });
		}, () => { demande = false; });
	}
	const surVisibilite = () => { if (page.visibilityState !== 'hidden') demander(); };
	if (wakeLock) page.addEventListener?.('visibilitychange', surVisibilite);
	function maintenir(oui) {
		voulu = oui;
		if (oui) demander();
		else if (sentinelle) {
			const verrou = sentinelle;
			sentinelle = null;
			relacher(verrou);
		}
	}
	return {
		maintenir,
		detruire() {
			if (fini) return;
			maintenir(false);
			fini = true;
			if (wakeLock) page.removeEventListener?.('visibilitychange', surVisibilite);
		},
	};
}

// repli(commandes) donne le titre qui reçoit le focus quand le bouton actif n'est plus utilisable.
export function installerEnregistrementsManette(ui, { repli = () => null } = {}) {
	const { e, bouton, contexte } = ui;
	const page = ui.document;
	const fenetre = page.defaultView ?? globalThis;
	const appareils = fenetre.navigator?.mediaDevices;
	const demanderMicro = appareils?.getUserMedia?.bind(appareils);
	const Enregistreur = fenetre.MediaRecorder;
	const impossible = diagnostiquer({ securise: fenetre.isSecureContext === true, demanderMicro, Enregistreur });
	const registre = registreDe(page);
	const verrou = creerVerrouEcran(fenetre.navigator, page);
	const prises = new Map();
	// Une seule zone d'annonce, jamais déplacée : les textes près des boutons restent muets.
	const annonce = e('p', 'karaoke__annonce-enregistrement visuellement-cache');
	annonce.setAttribute('role', 'status');
	const resultats = e('section', 'karaoke__enregistrements');
	resultats.hidden = true;
	const titreResultats = e('h2', '', 'Tes Enregistrements');
	titreResultats.tabIndex = -1;
	const liste = e('div', 'karaoke__enregistrements-liste');
	resultats.append(titreResultats, e('p', '', AVERTISSEMENT), liste);
	ui.principale.append(annonce, resultats);
	const vuesFichiers = new Map();
	const dejaAnnonces = new Set(registre.fichiers.keys());
	const annonces = [];
	let renduPrevu = false, focus = null;
	function rafraichir() {
		if (renduPrevu) return;
		renduPrevu = true;
		queueMicrotask(() => { renduPrevu = false; ui.rendre(); });
	}
	registre.abonnes.add(rafraichir);

	const envoiAccord = id => ui.dernierEnvoi(e => e.commande.type === 'ACCORD_ENREGISTREMENT' && e.commande.passageId === id);
	const accordDe = id => ui.bloc.mesAccordsEnregistrement?.includes(id) ?? false;
	const microOccupeParUnAutre = id => [...prises].some(([autre, { prise }]) => autre !== id && occupeMicro(prise.lire().etat));

	function publier(passage, { fichier, motif, echec }) {
		const titre = passage.chanson.titre;
		registre.ajouter({
			id: passage.passageId, titre, motif, echec: Boolean(echec),
			url: fichier ? URL.createObjectURL(fichier) : undefined,
			nom: fichier ? nomFichier(titre, passage.demarreLe, fichier, contexte.maintenantServeur?.()) : undefined,
		});
	}

	function creerFiche(passage) {
		const id = passage.passageId;
		const fiche = { passage };
		fiche.prise = creerEnregistrement({
			demanderMicro, Enregistreur, surEtat: rafraichir,
			surResultat: resultat => publier(fiche.passage, resultat),
		});
		const commandes = e('div', 'karaoke__commandes karaoke__enregistrement');
		commandes.setAttribute('data-passage-id', id);
		const choix = bouton('', () => {
			ui.commande({
				methode: 'PUT', chemin: `${ui.racine()}/passages/${encodeURIComponent(id)}/accord-enregistrement`,
				type: 'ACCORD_ENREGISTREMENT', mancheId: ui.bloc.mancheId, acteurType: 'JOUEUR', passageId: id,
				corps: { accord: !accordDe(id), numeroModification: fiche.passage.numeroModification },
			});
		});
		const micro = bouton('Autoriser le micro', () => {
			if (ui.mutationsPermises && !microOccupeParUnAutre(id)) void fiche.prise.autoriserMicro();
		});
		const reessayer = bouton('Réessayer mon accord', () => ui.reessayer(envoiAccord(id)?.cle));
		const statut = e('p', 'karaoke__etat-enregistrement');
		Object.assign(fiche, { commandes, choix, micro, reessayer, statut });
		for (const action of ACTIONS) fiche[action].setAttribute('data-action', action);
		commandes.append(choix, micro, reessayer, statut);
		return fiche;
	}

	function rendreFiche(fiche, phase, accord, autorise) {
		const { passage, prise, commandes, choix, micro, reessayer, statut } = fiche;
		const { etat, motif } = prise.lire();
		const envoi = envoiAccord(passage.passageId);
		const rejouable = ui.reessaiPossible(envoi);
		const occupe = microOccupeParUnAutre(passage.passageId);
		choix.hidden = phase !== 'avant' || (Boolean(impossible) && !accord);
		choix.textContent = accord ? 'Retirer mon accord' : 'Enregistrer mon Passage';
		choix.disabled = !ui.mutationsPermises || envoi?.etat === 'en-cours' || rejouable;
		micro.hidden = phase !== 'avant' || !accord || Boolean(impossible) || !['accord', 'refus'].includes(etat);
		micro.disabled = !ui.mutationsPermises || occupe;
		reessayer.hidden = phase !== 'avant' || !rejouable;
		for (const action of ACTIONS) fiche[action].setAttribute('aria-label', `${fiche[action].textContent} : ${passage.chanson.titre}`);
		const texte = envoi && envoi.etat !== 'accepte' && phase === 'avant' ? texteEnvoi(envoi)
			: impossible && phase === 'avant' ? impossibilites[impossible]
				: !micro.hidden && occupe ? 'Le micro est réservé à un autre de tes Passages.'
					: messages[motif ?? etat] ?? '';
		if (statut.textContent !== texte) statut.textContent = texte;
		statut.hidden = !texte;
		// Le premier texte d'une fiche s'affiche sans annonce ; chaque changement est annoncé.
		if (fiche.texte !== undefined && texte && texte !== fiche.texte) annonces.push(texte);
		fiche.texte = texte;
		commandes.hidden = !autorise || (choix.hidden && micro.hidden && reessayer.hidden && statut.hidden);
		if (commandes.hidden) commandes.remove();
	}

	function retirerFichier(id) {
		registre.retirer(id);
		rendreFichiers();
		(registre.fichiers.size ? titreResultats : repli(null))?.focus();
	}

	function confirmerRetrait(entree, declencheur) {
		const dialogue = ui.dialogue({ titre: 'Retirer l’Enregistrement', declencheur, confirmation: {
			libelle: 'Confirmer le retrait',
			permise: () => registre.fichiers.has(entree.id),
			executer: () => retirerFichier(entree.id),
		} });
		dialogue.annuler.textContent = 'Annuler';
		dialogue.contenu.append(e('p', '', `As-tu bien téléchargé ${entree.titre} ? Le fichier sera effacé de ce téléphone.`),
			dialogue.confirmer);
	}

	function creerVueFichier(entree) {
		const vueFichier = e('div', 'karaoke__enregistrement-fichier');
		vueFichier.append(e('h3', '', entree.titre));
		if (entree.echec) vueFichier.append(e('p', '', 'La prise a échoué : aucun son n’a été capté.'));
		else {
			const lien = e('a', 'bouton', 'Télécharger mon Enregistrement');
			lien.href = entree.url;
			lien.download = entree.nom;
			lien.setAttribute('aria-label', `Télécharger mon Enregistrement : ${entree.titre}`);
			const ecoute = e('audio');
			ecoute.src = entree.url;
			ecoute.controls = true;
			ecoute.preload = 'metadata';
			ecoute.setAttribute('aria-label', `Écouter ton Enregistrement : ${entree.titre}`);
			vueFichier.append(lien, e('p', '', 'Écouter'), ecoute);
			if (entree.motif === 'interrompu') vueFichier.append(e('p', '', 'Cette prise a été interrompue.'));
		}
		// Le retrait d'un fichier vaut confirmation de son téléchargement ; un échec se retire d'un geste.
		const retirer = bouton('Retirer', () => {
			if (entree.echec) retirerFichier(entree.id);
			else confirmerRetrait(entree, retirer);
		}, 'bouton bouton--lien');
		retirer.setAttribute('data-action', 'retirer');
		retirer.setAttribute('aria-label', `Retirer l’Enregistrement : ${entree.titre}`);
		vueFichier.append(retirer);
		return vueFichier;
	}

	function rendreFichiers() {
		for (const [id, vueFichier] of vuesFichiers) {
			if (!registre.fichiers.has(id)) { vueFichier.remove(); vuesFichiers.delete(id); }
		}
		for (const entree of registre.fichiers.values()) {
			if (vuesFichiers.has(entree.id)) continue;
			const vueFichier = creerVueFichier(entree);
			vuesFichiers.set(entree.id, vueFichier);
			liste.append(vueFichier);
			if (dejaAnnonces.has(entree.id)) continue;
			dejaAnnonces.add(entree.id);
			annonces.push(entree.echec ? `La prise de ${entree.titre} a échoué : aucun son n’a été capté.`
				: `Ton Enregistrement est prêt : ${entree.titre}.`);
		}
		resultats.hidden = registre.fichiers.size === 0;
	}

	// Lu avant tout placer() : déplacer un bouton dans un nouveau ticket lui fait perdre le focus.
	function memoriserFocus() {
		const actif = page.activeElement;
		for (const [id, fiche] of prises) {
			const action = ACTIONS.find(nom => fiche[nom] === actif);
			if (action) { focus = { id, action }; return; }
		}
		// Le repli n'est qu'une étape : le focus revient au bouton dès qu'il est de nouveau utilisable.
		if (focus && (!focus.repli || actif !== focus.repli)) focus = null;
	}

	function liberer(fiche) {
		fiche.prise.detruire();
		fiche.commandes.remove();
	}

	function reinitialiser() {
		for (const fiche of prises.values()) liberer(fiche);
		prises.clear();
		focus = null;
		verrou.maintenir(false);
	}

	return {
		rendre(origine) {
			memoriserFocus();
			const bloc = ui.bloc;
			const courant = bloc.courant;
			const personnel = courant?.interpretes.some(i => i.joueurId === contexte.acteur.identifiant);
			const autorise = bloc.enregistrementAutorise !== false;
			const passages = new Map(bloc.mesPassagesEnAttente.map(p => [p.passageId, { passage: p, phase: 'avant' }]));
			if (personnel) {
				const phase = { APPELE: 'avant', EN_COURS: 'actif', EN_PAUSE: 'pause' }[courant.etat] ?? 'termine';
				passages.set(courant.passageId, { passage: courant, phase });
			}
			if (autorise) for (const [id, { passage, phase }] of passages) {
				if (phase !== 'termine' && !prises.has(id)) prises.set(id, creerFiche(passage));
			}
			for (const [id, fiche] of prises) {
				const visible = passages.get(id);
				const phase = visible?.phase ?? 'termine';
				if (visible) fiche.passage = visible.passage;
				fiche.prise.suivre({ phase, accord: autorise && accordDe(id) }, origine);
				// La prise finit seule : son fichier rejoint le registre, même après liberer().
				if (phase === 'termine') {
					liberer(fiche);
					prises.delete(id);
				}
			}
			for (const [id, fiche] of prises) {
				const phase = passages.get(id)?.phase ?? 'termine';
				rendreFiche(fiche, phase, autorise && accordDe(id), autorise && phase !== 'termine');
			}
			rendreFichiers();
			verrou.maintenir([...prises.values()].some(fiche => gardeEcranAllume(fiche.prise.lire().etat)));
			if (annonces.length) annonce.textContent = annonces.splice(0).join(' ');
		},
		placer(passage, parent) {
			const commandes = prises.get(passage.passageId)?.commandes;
			if (commandes && !commandes.hidden && commandes.parentNode !== parent) parent.append(commandes);
		},
		// À appeler à la fin du rendu, une fois tous les tickets en place.
		restaurerFocus() {
			if (!focus) return;
			const fiche = prises.get(focus.id);
			const cible = fiche?.[focus.action];
			if (cible?.isConnected && !cible.hidden && !cible.disabled && !cible.parentNode?.hidden) {
				if (page.activeElement !== cible) cible.focus();
				focus = null;
				return;
			}
			const titre = focus.repli ?? repli(fiche?.commandes ?? null);
			if (titre && page.activeElement !== titre) titre.focus();
			focus = fiche ? { ...focus, repli: titre } : null;
		},
		reinitialiser,
		detruire() {
			reinitialiser();
			verrou.detruire();
			registre.abonnes.delete(rafraichir);
		},
	};
}
