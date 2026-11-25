import { ouvrirDialogue } from './dialogue.mjs';

// « Tes Enregistrements » sur la Manette (#71, #208). Les fichiers prêts vivent dans un registre par page,
// hors du module de Manche : le module du Karaoké les présente quand il est monté, la coquille sinon.
// Le son ne quitte jamais le téléphone (ADR 0005) : rien ici ne reçoit de quoi envoyer une requête.
// Rien n'est écrit sur le disque non plus : seuls le retrait confirmé et pagehide révoquent les URL.

export const AVERTISSEMENT = 'Cette page les garde en mémoire, même après la Manche, mais ne les écrit pas sur ton téléphone. '
	+ 'Un rechargement ou la fermeture de la page les efface, et le téléphone peut la fermer si elle reste en arrière-plan : '
	+ 'télécharge-les sans attendre.';

const registres = new WeakMap();

export function registreDe(page) {
	const existant = registres.get(page);
	if (existant) return existant;
	const fichiers = new Map(), abonnes = new Set();
	let presentations = 0, fermee = false;
	const prevenir = () => [...abonnes].forEach(rappel => rappel());
	const revoquer = entree => { if (entree.url) URL.revokeObjectURL(entree.url); };
	const registre = {
		fichiers,
		get presente() { return presentations > 0; },
		abonner(rappel) {
			abonnes.add(rappel);
			return () => abonnes.delete(rappel);
		},
		// Un module qui affiche lui-même la liste le déclare, et la coquille se retire tant qu'il la présente.
		presenter() {
			let actif = true;
			presentations++;
			prevenir();
			return () => {
				if (!actif) return;
				actif = false;
				presentations--;
				prevenir();
			};
		},
		// Une prise finie après pagehide (le démontage arrête le micro) ne survivrait pas à la page : son URL est
		// révoquée tout de suite, quel que soit l'ordre des écouteurs de pagehide.
		ajouter(entree) {
			if (fermee) { revoquer(entree); return; }
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
		fermee = true;
		for (const entree of fichiers.values()) revoquer(entree);
		fichiers.clear();
		prevenir();
	});
	registres.set(page, registre);
	return registre;
}

let dialogues = 0;

// La liste, ses gestes (télécharger, écouter, retirer avec confirmation) et ses annonces, sans zone d'annonce
// propre : annoncer(texte) passe par la seule zone role="status" de celui qui l'affiche.
// repli() donne l'élément qui reçoit le focus quand le dernier fichier est retiré.
export function creerListeEnregistrements({ document, registre, annoncer = () => {}, repli = () => null }) {
	const e = (tag, classe = '', texte) => {
		const element = document.createElement(tag);
		element.className = classe;
		if (texte !== undefined) element.textContent = texte;
		return element;
	};
	const bouton = (texte, rappel, classe = 'bouton') => {
		const b = e('button', classe, texte);
		b.type = 'button';
		b.addEventListener('click', rappel);
		return b;
	};
	const section = e('section', 'enregistrements');
	section.hidden = true;
	const titre = e('h2', '', 'Tes Enregistrements');
	titre.tabIndex = -1;
	const liste = e('div', 'enregistrements__liste');
	section.append(titre, e('p', '', AVERTISSEMENT), liste);
	// Par identifiant : l'entrée affichée et sa vue. Une entrée republiée sous le même id est un autre fichier.
	const vues = new Map();
	// Un fichier déjà là n'est pas annoncé une seconde fois par une nouvelle liste.
	const vus = new WeakSet(registre.fichiers.values());
	let visible = false, dialogue = null, dialogueEntree = null;

	function fermerDialogue() {
		if (dialogue?.open) dialogue.close();
		dialogue?.remove();
		dialogue = null;
		dialogueEntree = null;
	}

	function retirerFichier(id) {
		registre.retirer(id);
		rendre(visible);
		(registre.fichiers.size ? titre : repli())?.focus();
	}

	function confirmerRetrait(entree, declencheur) {
		fermerDialogue();
		const element = e('dialog', 'dialogue');
		const titreDialogue = e('h3', '', 'Retirer l’Enregistrement');
		titreDialogue.id = `enregistrement-retrait-${++dialogues}`;
		element.setAttribute('aria-labelledby', titreDialogue.id);
		element.setAttribute('tabindex', '-1');
		const confirmer = bouton('Confirmer le retrait', () => {
			if (registre.fichiers.get(entree.id) !== entree) return;
			element.close();
			retirerFichier(entree.id);
		});
		const annuler = bouton('Annuler', () => element.close());
		element.append(titreDialogue,
			e('p', '', `As-tu bien téléchargé ${entree.titre} ? La copie gardée par cette page sera effacée.`),
			confirmer, annuler);
		element.addEventListener('close', () => {
			element.remove();
			if (dialogue === element) dialogue = null;
		});
		dialogue = element;
		dialogueEntree = entree;
		section.append(element);
		ouvrirDialogue(element, declencheur);
	}

	function creerVue(entree) {
		const vue = e('div', 'enregistrements__fichier');
		vue.append(e('h3', '', entree.titre));
		if (entree.echec) vue.append(e('p', '', 'La prise a échoué : aucun son n’a été capté.'));
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
			vue.append(lien, e('p', '', 'Écouter'), ecoute);
			if (entree.motif === 'interrompu') vue.append(e('p', '', 'Cette prise a été interrompue.'));
		}
		// Le retrait d'un fichier vaut confirmation de son téléchargement ; un échec se retire d'un geste.
		const retirer = bouton('Retirer', () => {
			if (entree.echec) retirerFichier(entree.id);
			else confirmerRetrait(entree, retirer);
		}, 'bouton bouton--lien');
		retirer.setAttribute('data-action', 'retirer');
		retirer.setAttribute('aria-label', `Retirer l’Enregistrement : ${entree.titre}`);
		vue.append(retirer);
		return vue;
	}

	// affichee : la liste est à l'écran. Cachée, elle n'annonce rien et ne garde aucun lecteur :
	// un <audio> retiré du document se met en pause, et le fichier n'est lu qu'à un endroit.
	function rendre(affichee = true) {
		visible = affichee;
		if (!affichee) {
			for (const entree of registre.fichiers.values()) vus.add(entree);
			vues.clear();
			liste.replaceChildren();
			section.hidden = true;
			fermerDialogue();
			return;
		}
		// Une entrée retirée, ou remplacée sous le même id (son ancienne URL est révoquée), perd sa vue.
		for (const [id, { entree, vue }] of vues) {
			if (registre.fichiers.get(id) !== entree) { vue.remove(); vues.delete(id); }
		}
		for (const entree of registre.fichiers.values()) {
			if (!vues.has(entree.id)) {
				const vue = creerVue(entree);
				vues.set(entree.id, { entree, vue });
				liste.append(vue);
			}
			if (vus.has(entree)) continue;
			vus.add(entree);
			annoncer(entree.echec ? `La prise de ${entree.titre} a échoué : aucun son n’a été capté.`
				: `Ton Enregistrement est prêt : ${entree.titre}.`);
		}
		section.hidden = registre.fichiers.size === 0;
		// Un fichier effacé ou remplacé entre-temps (pagehide, nouvelle prise) ferme la confirmation en cours.
		if (dialogue && registre.fichiers.get(dialogueEntree.id) !== dialogueEntree) fermerDialogue();
	}

	return {
		element: section,
		titre,
		rendre,
		detruire() {
			fermerDialogue();
			section.remove();
		},
	};
}

// La zone role="status" de celui qui affiche la liste. Elle est vidée avant chaque annonce, et une annonce
// identique à la précédente (deux Passages de la même Chanson) n'est écrite qu'après un court délai :
// un lecteur d'écran ne relit pas un texte qui n'a pas changé.
export const DELAI_ANNONCE_REPETEE_MS = 100;
export function creerZoneAnnonce(document, classe = 'visuellement-cache') {
	const element = document.createElement('p');
	element.className = classe;
	element.setAttribute('role', 'status');
	let minuteur = null, derniere = '';
	return {
		element,
		annoncer(texte) {
			clearTimeout(minuteur);
			minuteur = null;
			const repetee = texte === derniere;
			derniere = texte;
			element.textContent = '';
			if (!repetee) { element.textContent = texte; return; }
			minuteur = setTimeout(() => { minuteur = null; element.textContent = texte; }, DELAI_ANNONCE_REPETEE_MS);
		},
		detruire() { clearTimeout(minuteur); minuteur = null; },
	};
}

// La liste de la coquille, sur la Manette : elle présente le registre quand aucun module ne le fait
// (hors Manche, pendant une Manche d'un autre Mode de jeu, après la fin de la Soirée).
export function monterEnregistrements(zone, { repli = () => null } = {}) {
	const document = zone.ownerDocument;
	const registre = registreDe(document);
	const annonce = creerZoneAnnonce(document);
	const annonces = [];
	const liste = creerListeEnregistrements({ document, registre, annoncer: texte => annonces.push(texte), repli });
	liste.element.classList.add('panneau');
	zone.replaceChildren(annonce.element, liste.element);
	let prevu = false, demontee = false;
	function rendre() {
		if (demontee) return;
		liste.rendre(!registre.presente);
		if (annonces.length) annonce.annoncer(annonces.splice(0).join(' '));
	}
	const quitter = registre.abonner(() => {
		if (prevu) return;
		prevu = true;
		queueMicrotask(() => { prevu = false; rendre(); });
	});
	rendre();
	return {
		demonter() {
			demontee = true;
			quitter();
			annonce.detruire();
			liste.detruire();
			zone.replaceChildren();
		},
	};
}
