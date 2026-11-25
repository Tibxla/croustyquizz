import { ouvrirDialogue } from '../../dialogue.mjs';
import { creerMachineEnvoi, exigeCapture, peutReessayer } from './envois.mjs';
import { creerMachineRecherche } from './recherche.mjs';
import { teintePassage, formaterDuree, formaterAttente, etatInscription, libelleEtatTicketPublic } from './presentation.mjs';

export const texteAutresPassages = n => (n ? `et ${n} ${n > 1 ? 'autres' : 'autre'}` : '');

export function creerUuid(cryptoDisponible = globalThis.crypto) {
	if (cryptoDisponible.randomUUID) return cryptoDisponible.randomUUID();
	const octets = cryptoDisponible.getRandomValues(new Uint8Array(16));
	octets[6] = (octets[6] & 15) | 64;
	octets[8] = (octets[8] & 63) | 128;
	const hex = Array.from(octets, octet => octet.toString(16).padStart(2, '0')).join('');
	return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

const styles = new WeakMap();
const paroles = { SYNCHRONISEES: 'Paroles synchronisées', NON_SYNCHRONISEES: 'Paroles non synchronisées',
	ABSENTES: 'Sans paroles', SANS_VOIX: 'Sans voix', INCONNU: 'Paroles à vérifier' };

export function texteEnvoi(e) {
	if (!e) return '';
	if (e.etat === 'en-cours') return 'Envoi en cours…';
	if (e.etat === 'accepte') return 'Envoi accepté par le serveur.';
	if (e.etat === 'inconnu') return 'Confirmation à vérifier.';
	return e.detail ?? (e.etat === 'indisponible' ? 'Service indisponible.' : 'Envoi refusé.');
}

// Adaptateur commun aux trois modules : cycle de vie, connexion, reçus et DOM sûr.
export function creerInterface(zones, contexte, { annoncerConnexion = true } = {}) {
	const document = zones.principale.ownerDocument;
	const ecouteurs = [], dialogues = new Set();
	let detruite = false, machine, quitter, bloc, rendue = false, etatConnexion = 'synchronisation';
	let captures = 0, attendCapture = false, csrfInvalide = false, ordre = 0, commandeEnPreparation, suspendre = false;
	const envois = new Map();
	function e(tag, classe = '', texte) {
		const element = document.createElement(tag); element.className = classe;
		if (texte !== undefined) element.textContent = texte;
		return element;
	}
	function ecouter(element, type, rappel) { element.addEventListener(type, rappel); ecouteurs.push([element, type, rappel]); }
	function bouton(texte, rappel, classe = 'bouton') {
		const b = e('button', classe, texte); b.type = 'button'; ecouter(b, 'click', rappel); return b;
	}
	let feuille = styles.get(document);
	if (!feuille) {
		const lien = e('link'); lien.setAttribute('rel', 'stylesheet'); lien.setAttribute('href', '/css/karaoke.css'); lien.setAttribute('data-mode', 'karaoke');
		document.head.append(lien); feuille = { lien, nombre: 0 }; styles.set(document, feuille);
	}
	feuille.nombre++;
	const principale = e('section', 'karaoke'), laterale = zones.laterale ? e('section', 'karaoke') : null;
	zones.principale.replaceChildren(principale); if (laterale) zones.laterale.replaceChildren(laterale);
	const connexion = e('p', 'coupure');
	if (annoncerConnexion) connexion.setAttribute('role', 'status');
	connexion.hidden = true; principale.append(connexion);
	function rendre(origine) {
		if (detruite || suspendre) return;
		for (const d of [...dialogues]) {
			if (d.valide && !d.valide()) {
				d.fermer(); ui.messageInvalidation();
			} else if (d.confirmer) d.confirmer.disabled = !d.confirmationPermise();
		}
		connexion.hidden = etatConnexion !== 'coupure'; connexion.textContent = 'Connexion perdue, on réessaie.';
		ui.surRendu(origine);
		// Les listes réécrites ne gardent pas leurs anciens écouteurs en mémoire.
		for (let i = ecouteurs.length - 1; i >= 0; i--) if (!ecouteurs[i][0].isConnected) {
			const [element, type, rappel] = ecouteurs.splice(i, 1)[0]; element.removeEventListener(type, rappel);
		}
	}
	function creerMachine() {
		// Le module affiche son propre statut près de l'action : la coquille ne l'annonce pas une seconde fois.
		return creerMachineEnvoi({ envoyer: (methode, chemin, corps, options) =>
			contexte.envoyer(methode, chemin, corps, { ...options, annoncer: false }), creerUuid,
			programmerDelai: (f, ms) => setTimeout(f, ms), annulerDelai: clearTimeout,
			delaiConfirmationMs: bloc.configurationInterface.delaiConfirmationNavigateurMs,
			resynchroniser: () => contexte.resynchroniser(),
			surEtat(etat) {
				const precedent = envois.get(etat.cle);
				const fiche = { ...(precedent ?? commandeEnPreparation), ...etat };
				fiche.reessaiEnCours = etat.etat === 'en-cours' && (peutReessayer(precedent) || Boolean(precedent?.reessaiEnCours));
				if (exigeCapture(etat) && precedent?.etat !== etat.etat) { fiche.captureRequise = captures + 1; attendCapture = true; }
				if (etat.code === 'CSRF_INVALIDE') csrfInvalide = true;
				envois.set(etat.cle, fiche); rendre();
			},
		});
	}
	const ui = {
		document, principale, laterale, e, bouton, ecouter, envois, contexte,
		surRendu() {}, surManche() {}, surDestruction() {}, messageInvalidation() {},
		get bloc() { return bloc; }, get synchronisee() { return etatConnexion === 'synchronisee' && rendue; },
		get mutationsPermises() { return ui.synchronisee && !attendCapture && !csrfInvalide; },
		get csrfInvalide() { return csrfInvalide; },
		rendre,
		racine: () => `/api/soirees/${encodeURIComponent(contexte.soireeId)}/manches/${encodeURIComponent(bloc.mancheId)}`,
		inscription: chansonId => machine?.retrouverInscription(bloc.mancheId, chansonId),
		envoiInscription: chansonId => [...envois.values()].filter(e => e.commande.chansonId === chansonId && !e.commande.passageId).at(-1),
		capacite(commande) {
			if (!bloc) return false;
			switch (commande.type) {
				case 'VOTER': return (bloc.votes ?? []).some(vote => vote.passageId === commande.passageId && vote.peutVoter);
				case 'CONFIRMER_PRESENCE':
					return bloc.peutConfirmerPresence && commande.passageId === bloc.courant?.passageId;
				case 'APPELER': return bloc.peutAppeler && bloc.passagesEnAttente.some(p => p.position === 1 && p.passageId === commande.passageId);
				case 'MARQUER_DEBUT':
				case 'CORRIGER_CALAGE': return bloc.peutCaler;
				case 'REPRENDRE_EN_PAROLES_SEULES': return bloc.peutReprendreEnParolesSeules;
				case 'PILOTER':
					switch (commande.corps.action) {
						case 'METTRE_EN_PAUSE': return bloc.etat === 'EN_COURS';
						case 'REPRENDRE': return bloc.peutReprendre;
						case 'PASSER_ETAPE': return bloc.peutPasser;
						case 'TERMINER': return true;
						default: return false;
					}
			}
			if (commande.acteurType === 'JOUEUR') return commande.passageId ?
				bloc.mesPassagesEnAttente.some(p => p.passageId === commande.passageId) : bloc.peutInscrire;
			return commande.chemin.endsWith('/limite') ? bloc.peutReglerLimite : bloc.peutGererFile;
		},
		commande(commande) {
			if (!ui.mutationsPermises || !ui.capacite(commande)) return null;
			commandeEnPreparation = { commande, ordre: ++ordre };
			const resultat = machine.envoyerCommande(commande); commandeEnPreparation = null; return resultat;
		},
		reessaiPossible(fiche) { return Boolean(fiche && peutReessayer(fiche) && ui.mutationsPermises &&
			captures >= (fiche.captureRequise ?? 0) && ui.capacite(fiche.commande)); },
		reessayer(cle) { if (ui.reessaiPossible(envois.get(cle))) machine.reessayer(cle); },
		dernierEnvoi(filtre) { return [...envois.values()].filter(filtre).sort((a, b) => b.ordre - a.ordre)[0]; },
		dernierEchec(filtre) { return ui.dernierEnvoi(e => filtre(e) && (['refuse', 'inconnu', 'indisponible'].includes(e.etat) || e.reessaiEnCours)); },
		dialogue({ titre, declencheur, valide, fermer = () => {}, confirmation }) {
			const dialog = e('dialog', 'dialogue');
			const titreElement = e('h3', '', titre); titreElement.id = `karaoke-dialogue-${++ordre}`;
			dialog.setAttribute('aria-labelledby', titreElement.id); dialog.setAttribute('tabindex', '-1');
			const contenu = e('div', 'karaoke__fiche');
			const annuler = bouton('Fermer', () => dialog.close());
			const d = { element: dialog, contenu, annuler, valide, fermer: () => { if (dialog.open) dialog.close(); terminer(); } };
			if (confirmation) {
				d.confirmationPermise = confirmation.permise;
				d.confirmer = bouton(confirmation.libelle, () => {
					if ((valide && !valide()) || !confirmation.permise()) return;
					d.fermer(); confirmation.executer();
				});
				d.confirmer.disabled = !confirmation.permise();
			}
			function terminer() {
				if (!dialogues.delete(d)) return;
				fermer(); dialog.remove();
				if (!declencheur?.isConnected) (principale.querySelector('input') ?? principale.querySelector('h2'))?.focus();
			}
			dialog.append(titreElement, contenu, annuler); principale.append(dialog);
			dialogues.add(d); ecouter(dialog, 'close', terminer); ouvrirDialogue(dialog, declencheur); return d;
		},
		remplacerListe(liste, enfants, titre) {
			const actif = document.activeElement, avaitFocus = liste.contains(actif);
			const id = actif?.getAttribute('data-passage-id') ?? actif?.getAttribute('data-chanson-id');
			liste.replaceChildren(...enfants);
			if (avaitFocus) {
				(Array.from(liste.querySelectorAll('[data-passage-id], [data-chanson-id]')).find(e =>
					e.tagName === actif.tagName && (e.getAttribute('data-passage-id') ?? e.getAttribute('data-chanson-id')) === id) ?? titre)?.focus();
			}
		},
		afficher(vue, origine) {
			if (detruite) return;
			suspendre = true;
			try {
				if (vue.karaoke.mancheId !== bloc?.mancheId) {
					const confirmationInvalidee = [...dialogues].some(d => d.valide);
					machine?.detruire(); envois.clear(); attendCapture = false;
					for (const d of [...dialogues]) d.fermer();
					const blocPrecedent = bloc;
					bloc = vue.karaoke; machine = creerMachine(); ui.surManche(blocPrecedent);
					if (confirmationInvalidee) ui.messageInvalidation();
				} else bloc = vue.karaoke;
				rendue = true;
				if (origine?.source === 'capture') { captures++; attendCapture = false; }
				const personnels = bloc.mesPassagesEnAttente ?? [];
				const visibles = [...envois.values()].filter(e => e.commande.acteurType === 'JOUEUR' && e.commande.chansonId &&
					personnels.some(p => p.chanson.chansonId === e.commande.chansonId)).map(e => e.cle);
				for (const cle of machine.reconcilier(visibles)) envois.delete(cle);
				for (const [cle, e] of envois) {
					const { acteurType, passageId, type } = e.commande;
					if (acteurType !== 'JOUEUR' || !passageId) continue;
					const visible = type === 'VOTER' ? voteAttendUnRecu(bloc, passageId, e.etat)
						: type === 'CONFIRMER_PRESENCE' ? bloc.courant?.passageId === passageId
							: personnels.some(p => p.passageId === passageId);
					if (!visible) { machine.oublier(cle); envois.delete(cle); }
				}
			} finally { suspendre = false; }
			rendre(origine);
		},
		demonter() {
			if (detruite) return;
			detruite = true; machine?.detruire(); quitter?.(); ui.surDestruction();
			for (const d of [...dialogues]) d.fermer();
			for (const [element, type, rappel] of ecouteurs) element.removeEventListener(type, rappel);
			ecouteurs.length = 0; envois.clear();
			if (--feuille.nombre === 0) { feuille.lien.remove(); styles.delete(document); }
			for (const zone of Object.values(zones)) zone.replaceChildren();
		},
	};
	quitter = contexte.surEtatConnexion(etat => { etatConnexion = etat; rendre(); });
	return ui;
}

function voteAttendUnRecu(bloc, passageId, etatEnvoi) {
	const vote = bloc.votes?.find(vote => vote.passageId === passageId);
	// Le STOMP peut devancer le HTTP. Laisser arriver le reçu tant que son ticket est visible.
	return vote && (etatEnvoi === 'en-cours' || (vote.etat === 'OUVERT' && vote.maNote == null));
}

export function creerTicket(ui, passage, { personnel = false, courant = false, selectionner, estimation = false, details = false } = {}) {
	const { e } = ui;
	const teinte = teintePassage(passage.numero);
	const ticket = e(selectionner ? 'button' : 'article', personnel ? 'mon-ticket' : `ticket ticket--${teinte}`);
	ticket.style.setProperty('--teinte', `var(--${teinte})`);
	ticket.setAttribute('data-passage-id', passage.passageId);
	ticket.setAttribute('aria-label', `Passage ${passage.numero} · ${passage.interpretes.map(i => i.pseudo).join(', ')} · ${passage.chanson.titre} · ${passage.chanson.artiste}${passage.prioritaire ? ' · Prioritaire' : ''}`);
	const haut = e(personnel ? 'div' : 'span', personnel ? 'mon-ticket__haut' : 'ticket__numero', personnel ? undefined : passage.numero);
	if (personnel) haut.append(e('span', 'mon-ticket__numero', passage.numero));
	if (personnel) haut.append(e('span', 'mon-ticket__etiquette', passage.prioritaire ? 'Prioritaire' : 'En attente'));
	const bas = e('div', personnel ? 'mon-ticket__bas' : 'ticket__corps');
	bas.append(e('p', personnel ? 'mon-ticket__quoi' : 'ticket__qui', passage.interpretes.map(i => i.pseudo).join(', ')),
		e('p', personnel ? 'mon-ticket__quoi' : 'ticket__quoi', passage.chanson.titre),
		e('p', 'ticket__quoi', passage.chanson.artiste));
	if ((personnel && !courant) || details) bas.append(e('p', 'karaoke__position', `Position ${passage.position} · Tour ${passage.tour}`));
	if (details) bas.append(e('p', 'chiffres', formaterDuree(passage.chanson.dureeMs)));
	if (estimation) bas.append(e('p', personnel ? 'mon-ticket__place' : 'karaoke__attente', formaterAttente(passage.attente, personnel)));
	if (passage.prioritaire && !personnel) bas.append(e('p', 'karaoke__priorite', 'Prioritaire'));
	if (courant && !personnel) bas.append(e('p', 'karaoke__etat-ticket', libelleEtatTicketPublic(passage)));
	ticket.append(haut, bas);
	if (selectionner) { ticket.type = 'button'; ticket.setAttribute('aria-pressed', 'false'); ui.ecouter(ticket, 'click', () => selectionner(passage.passageId)); }
	return ticket;
}

export function creerCatalogue(ui, parent, { inscrire = false } = {}) {
	const { e, bouton, ecouter } = ui;
	const titre = e('h2', '', 'Catalogue'); titre.tabIndex = -1;
	const formulaire = e('form', inscrire ? 'champ' : 'recherche');
	const label = e('label', '', 'Titre ou artiste'), champ = e('input'); champ.id = `karaoke-recherche-${inscrire ? 'joueur' : 'animateur'}`;
	label.setAttribute('for', champ.id); champ.type = 'search';
	const aide = e('p', 'champ__aide', 'Cherche un titre ou un artiste.'); aide.id = `${champ.id}-aide`;
	const erreur = e('p', 'champ--erreur'); erreur.id = `${champ.id}-erreur`; erreur.hidden = true;
	champ.setAttribute('aria-describedby', `${aide.id} ${erreur.id}`);
	const chercher = bouton('Chercher', () => {}); chercher.type = 'submit';
	formulaire.append(label, champ, chercher, aide, erreur);
	const etatLecture = e('p'); etatLecture.setAttribute('role', 'status');
	const CONSULTATION = 'Consultation en cours…';
	const resultats = e(inscrire ? 'ul' : 'table', `karaoke__resultats${inscrire ? '' : ' resultats'}`);
	parent.append(titre, formulaire, etatLecture, resultats);
	let composition = false, recherche, fiche, dialogue, boutonInscription, motif, etatAction, reessai, declencheur;
	const machine = creerMachineRecherche({ envoyer: (...args) => ui.contexte.envoyer(...args), creerUuid,
		programmerDelai: (f, ms) => setTimeout(f, ms), annulerDelai: clearTimeout,
		surEtat(etat) {
			if (etat.genre === 'recherche') { recherche = etat; dessinerResultats(); }
			else {
				fiche = etat;
				if (etat.etat !== 'chargement') { etatLecture.textContent = ''; ouvrirFiche(); }
				else etatLecture.textContent = CONSULTATION;
			}
			if (etat.origineErreur === 'reseau' || etat.statut === 503) ui.contexte.resynchroniser();
			rendre();
		},
	});
	const contexteLecture = () => ({ soireeId: ui.contexte.soireeId, mancheId: ui.bloc.mancheId, configurationInterface: ui.bloc.configurationInterface });
	function consulter(chansonId, boutonSource) { declencheur = boutonSource; void machine.consulter(chansonId, contexteLecture()); }
	function dessinerResultats() {
		erreur.hidden = recherche?.origineErreur !== 'validation'; erreur.textContent = erreur.hidden ? '' : recherche.detail;
		if (erreur.hidden) champ.removeAttribute('aria-invalid'); else champ.setAttribute('aria-invalid', 'true');
		etatLecture.textContent = !recherche ? '' : recherche.etat === 'chargement' ? 'Recherche en cours…' :
			recherche.etat === 'vide' ? 'Aucune Chanson trouvée.' : recherche.etat === 'erreur' && erreur.hidden ? (recherche.detail ?? 'La recherche est indisponible.') : '';
		const lignes = [];
		for (const chanson of recherche?.donnees?.chansons ?? []) {
			const ligne = e(inscrire ? 'li' : 'tr');
			const cellule = inscrire ? ligne : e('td');
			const ouvrir = bouton(chanson.titre, () => consulter(chanson.chansonId, ouvrir), 'bouton bouton--lien titre');
			ouvrir.setAttribute('data-chanson-id', chanson.chansonId);
			cellule.append(ouvrir, e('p', 'artiste', chanson.artiste));
			if (chanson.album) cellule.append(e('p', '', chanson.album));
			cellule.append(e('p', 'duree', formaterDuree(chanson.dureeMs)), e('p', '', paroles[chanson.etatParoles]));
			if (!inscrire) ligne.append(cellule); lignes.push(ligne);
		}
		if (recherche?.origineErreur === 'delai') {
			const ligne = e(inscrire ? 'li' : 'tr'), cellule = inscrire ? ligne : e('td');
			cellule.append(bouton('Réessayer', () => { void machine.chercher(champ.value, contexteLecture()); }));
			if (!inscrire) ligne.append(cellule); lignes.push(ligne);
		}
		ui.remplacerListe(resultats, lignes, titre);
	}
	function ouvrirFiche() {
		const courante = fiche;
		if (dialogue) dialogue.fermer();
		// fermer() invalide seulement l'ancienne lecture, pas le résultat reçu.
		dialogue = ui.dialogue({ titre: courante.donnees?.chanson.titre ?? 'Chanson indisponible', declencheur,
			fermer() {
				// fermerFiche annule la lecture sans émettre d'état : on retire nous-mêmes « Consultation en cours… ».
				machine.fermerFiche(); dialogue = null; fiche = null;
				if (etatLecture.textContent === CONSULTATION) etatLecture.textContent = '';
			} });
		fiche = courante;
		const contenu = dialogue.contenu;
		if (fiche.donnees) {
			const chanson = fiche.donnees.chanson;
			contenu.append(e('p', '', chanson.artiste));
			if (chanson.album) contenu.append(e('p', '', chanson.album));
			contenu.append(e('p', 'chiffres', formaterDuree(chanson.dureeMs)), e('p', '', paroles[chanson.etatParoles]));
		}
		motif = e('p'); contenu.append(motif);
		if (fiche.etat === 'erreur') {
			motif.textContent = fiche.statut === 404 ? "Cette Chanson n'est plus disponible." : (fiche.detail ?? 'La recherche est indisponible.');
			if (fiche.statut === 404 && fiche.detail) contenu.append(e('p', '', fiche.detail));
			if (fiche.origineErreur === 'delai') contenu.append(bouton('Réessayer', () => consulter(courante.chansonId, declencheur)));
		} else if (inscrire) {
			boutonInscription = bouton("M'inscrire", () => {
				if (!fiche?.donnees || !etatInscription(fiche.donnees, ui.bloc, ui.mutationsPermises).possible) return;
				ui.commande({ methode: 'POST', chemin: `${ui.racine()}/passages`, corps: { chansonId: fiche.chansonId },
					mancheId: ui.bloc.mancheId, acteurType: 'JOUEUR', chansonId: fiche.chansonId });
			}, 'bouton bouton--action bouton--grand');
			etatAction = e('p'); etatAction.setAttribute('role', 'status');
			reessai = bouton('Réessayer le même envoi', () => ui.reessayer(ui.envoiInscription(fiche.chansonId)?.cle));
			contenu.append(boutonInscription, etatAction, reessai);
		}
		rendre();
	}
	function rendre() {
		chercher.disabled = !ui.bloc?.peutRechercher;
		if (!dialogue || !fiche?.donnees) return;
		const etat = etatInscription(fiche.donnees, ui.bloc, ui.mutationsPermises);
		motif.textContent = etat.motif ?? '';
		if (inscrire) {
			const cle = ui.inscription(fiche.chansonId), envoi = ui.envoiInscription(fiche.chansonId);
			boutonInscription.disabled = !etat.possible || Boolean(cle);
			etatAction.textContent = texteEnvoi(envoi);
			reessai.hidden = !ui.reessaiPossible(envoi);
		}
	}
	ecouter(formulaire, 'submit', evenement => {
		evenement.preventDefault();
		if (composition || evenement.isComposing || !ui.bloc?.peutRechercher) return;
		void machine.chercher(champ.value, contexteLecture());
	});
	ecouter(champ, 'compositionstart', () => { composition = true; }); ecouter(champ, 'compositionend', () => { composition = false; });
	ecouter(champ, 'input', () => { if (!champ.value) { machine.invalider(); recherche = null; dessinerResultats(); } });
	return { rendre,
		reinitialiser() { machine.invalider(); dialogue?.fermer(); recherche = null; fiche = null; champ.value = ''; dessinerResultats(); },
		detruire() { machine.detruire(); dialogue?.fermer(); },
	};
}
