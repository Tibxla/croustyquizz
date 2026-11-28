import { choisirFichierDeChanson, fichierParUrl } from './fichiers.mjs';
import { passageActif } from './presentation.mjs';

const estPreparation = etat => etat === 'preparation' || etat === 'preparee';
const joueOuEstEnPause = etat => etat === 'lecture' || etat === 'pause';

export function creerCycleLecture({ lecteur, lecteurId, envoyer, surEtat, attendreDisparitionLecteur, peutDesigner = () => true }) {
	let phase = 'sans-dossier', phaseInterrompue, sonDebloque = false, aEteLectrice = false, autreLecteur = false;
	let fichiers, prochainDossier, versionDossier = 0, intentionDesignation = false, bibliothequeSeule = false, relais;
	let vue, derniereDesignation, preparation, depart, essaiSon = 0, detruit = false;
	const declarations = new Set(), demandesTraitees = new Set();
	const lecturesJouees = new Set(), erreursSignalees = new Set();

	function etat() {
		return { etat: phase, aEteLectrice, sonDebloque, autreLecteur, dureeMs: preparation?.fichier?.dureeMs ?? null };
	}
	function changerPhase(nouvellePhase) {
		if (phase === nouvellePhase) return;
		phase = nouvellePhase;
		surEtat(etat());
	}
	async function commander(methode, chemin, corps) {
		try { return await envoyer(methode, chemin, { lecteurId, ...corps }); }
		catch { return null; } // Une réponse perdue peut être rattrapée par la vue. Aucun réessai.
	}
	function designation() { void commander('PUT', '/lecteur', {}); }
	function annulerRelais() {
		relais?.controle?.abort();
		relais = undefined;
	}
	function demanderRelais(id) {
		annulerRelais();
		relais = { id, demande: true };
		intentionDesignation = false;
		designation();
	}
	function actualiserRelais() {
		if (!bibliothequeSeule || !vue || !sonDebloque || !peutDesigner()) return;
		const { lecteurDesigneId: id, courant } = vue;
		if (relais && relais.id !== id) annulerRelais();
		if (id === lecteurId) {
			intentionDesignation = false;
			return;
		}
		if (relais?.demande) return;
		if (intentionDesignation && (id == null || !passageActif(courant))) {
			demanderRelais(id);
		} else if (id != null && attendreDisparitionLecteur && !relais) {
			const attente = relais = { id, controle: new AbortController() };
			void attendreDisparitionLecteur(id, attente.controle.signal).then(disparu => {
				if (!disparu || relais !== attente) return;
				if (peutDesigner() && sonDebloque && vue.lecteurDesigneId === id) demanderRelais(id);
				else annulerRelais();
			}, () => {});
		}
	}
	const cheminPassage = (courant, suffixe) => `/passages/${encodeURIComponent(courant.passageId)}/${suffixe}`;

	function signaler(suffixe, champs = {}) {
		const { courant, lecture } = vue;
		void commander('POST', cheminPassage(courant, suffixe), { etapeId: courant.etapeId, lectureId: lecture.lectureId, ...champs });
	}
	function libererPassage() {
		const avaitFichier = preparation?.charge || depart;
		preparation = undefined;
		depart = undefined;
		demandesTraitees.clear();
		if (avaitFichier) lecteur.detruire();
	}
	function appliquerDossier(nouveauxFichiers) {
		bibliothequeSeule = false;
		annulerRelais();
		fichiers = nouveauxFichiers;
		prochainDossier = undefined;
		versionDossier++;
		designation();
	}
	function redevenirPret() {
		libererPassage();
		if (prochainDossier) appliquerDossier(prochainDossier);
		changerPhase('pret');
	}
	function reprendreApresDeblocage() {
		const interrompue = phaseInterrompue;
		phaseInterrompue = undefined;
		if (estPreparation(interrompue) && preparation?.version === versionDossier) changerPhase(interrompue);
		else if (fichiers) redevenirPret();
		else changerPhase('sans-dossier');
	}

	function estToujoursEnPreparation(cible) {
		const { courant } = vue;
		return preparation === cible && cible.version === versionDossier && courant?.etat === 'APPELE'
			&& courant.passageId === cible.passageId && courant.numeroModification === cible.numeroModification;
	}
	function declarer(cible) {
		if (phase !== 'preparation' || !estToujoursEnPreparation(cible) || cible.declaree || !cible.resultat) return;
		cible.declaree = true;
		const { fichier, motif } = cible.resultat;
		cible.fichier = fichier;
		if (motif && cible.charge) {
			cible.charge = false;
			lecteur.detruire();
		}
		void commander('POST', cheminPassage(vue.courant, 'lecture'), {
			etapeId: vue.courant.etapeId, numeroModification: cible.numeroModification,
			source: fichier ? 'FICHIER_LOCAL' : 'PAROLES_SEULES', fichier, motifParolesSeules: motif,
		}).then(reponse => {
			if (!estToujoursEnPreparation(cible) || !reponse || reponse.statut < 200 || reponse.statut >= 300) return;
			if (phase === 'preparation') changerPhase('preparee');
			else if (phase === 'son-bloque' && phaseInterrompue === 'preparation') phaseInterrompue = 'preparee';
		});
	}

	async function preparer(courant) {
		const { passageId, numeroModification, chanson } = courant;
		const cle = JSON.stringify([passageId, numeroModification, versionDossier]);
		if (declarations.has(cle)) return;
		declarations.add(cle);
		const cible = { passageId, numeroModification, version: versionDossier };
		preparation = cible;
		changerPhase('preparation');
		const resultat = choisirFichierDeChanson(fichiers, chanson);
		let fichier = null, motif = 'FICHIER_' + resultat.etat.toUpperCase();
		const candidats = resultat.etat === 'unique' ? [resultat.fichier] : [];
		if (!resultat.bibliotheque && chanson.bibliotheque) {
			const libre = chanson.bibliotheque;
			candidats.push(fichierParUrl(libre.fichier, libre.tailleOctets));
		}
		for (const candidat of candidats) {
			const { name: nom, size: tailleOctets } = candidat;
			motif = 'FICHIER_ILLISIBLE';
			// FichierAudioLocal.nom accepte un nom seul, sans séparateur de chemin.
			if (/^[^/\\]+$/.test(nom)) {
				cible.charge = true;
				try {
					const { dureeMs } = await lecteur.charger(candidat);
					fichier = { nom, tailleOctets, dureeMs };
					motif = null;
					break;
				} catch { /* Le fichier reste illisible. */ }
				if (!estToujoursEnPreparation(cible)) return;
			}
		}
		if (!estToujoursEnPreparation(cible) || cible.declaree) return;
		cible.resultat = { fichier, motif };
		declarer(cible);
	}

	function estNotreLecture(courant, lecture) {
		return courant && lecture?.lecteurId === lecteurId && (!preparation || courant.passageId === preparation.passageId);
	}
	function passerEnErreur() {
		if (!preparation) preparation = { passageId: vue.courant.passageId };
		if (preparation.charge || depart) lecteur.detruire();
		preparation.charge = false;
		depart = undefined;
		changerPhase('erreur');
		const lectureId = vue.lecture.lectureId;
		if (!erreursSignalees.has(lectureId)) {
			erreursSignalees.add(lectureId);
			signaler('erreur-lecture');
		}
	}
	async function jouer(reprise) {
		const tentative = depart = { lectureId: vue.lecture.lectureId, enCours: true };
		try {
			await (reprise ? lecteur.reprendre() : lecteur.lire());
		} catch {
			if (tentative === depart) passerEnErreur();
			return;
		}
		// Une clôture a déjà libéré le fichier et invalidé cette tentative.
		if (tentative !== depart) return;
		tentative.enCours = false;
		lecturesJouees.add(tentative.lectureId);
		if (vue.etat === 'EN_PAUSE') {
			lecteur.pause();
			changerPhase('pause');
		} else changerPhase('lecture');
		releverPosition();
	}
	function releverPosition() {
		const demandeId = vue.lecture?.demandePosition?.demandeId;
		if (!demandeId || demandesTraitees.has(demandeId)) return;
		demandesTraitees.add(demandeId);
		signaler('calage/position', { demandeId, positionMs: lecteur.position() });
	}

	function traiterVue(origine, etatManchePrecedent, reexaminer = true) {
		if (!vue || phase === 'sans-dossier' || phase === 'son-bloque') return;
		const { courant, lecture, lecteurDesigneId } = vue;
		const actif = passageActif(courant);
		const locale = lecture?.source === 'FICHIER_LOCAL';
		const notreLecture = estNotreLecture(courant, lecture);
		const appel = actif?.etat === 'APPELE';
		const autrePassage = actif?.passageId !== preparation?.passageId;
		const evenement = origine?.source === 'evenement';

		if (phase === 'erreur' && !(appel && autrePassage)) return;
		const lectureEngagee = depart || phase === 'fin';
		const abandonnerPreparation = lecteurDesigneId !== lecteurId || (locale ? !notreLecture : !appel);
		if (phase === 'erreur' || (lectureEngagee ? !actif || autrePassage || !locale
			: estPreparation(phase) && (!actif || autrePassage || abandonnerPreparation))) {
			redevenirPret();
			if (reexaminer) traiterVue(undefined, undefined, false);
			return;
		}

		if (depart) {
			if (joueOuEstEnPause(phase) && notreLecture) releverPosition();
			if (depart.enCours || !notreLecture) return;
			if (phase === 'lecture' && vue.etat === 'EN_PAUSE') {
				lecteur.pause();
				changerPhase('pause');
			} else if (phase === 'pause' && evenement && etatManchePrecedent === 'EN_PAUSE'
				&& vue.etat === 'EN_COURS' && actif.etat === 'EN_COURS' && depart.lectureId === lecture.lectureId) {
				void jouer(true);
			}
			return;
		}
		if (phase === 'preparation' && notreLecture) changerPhase('preparee');
		if (actif && locale && notreLecture && lecture.etat === 'DEMARREE') {
			if (phase === 'preparee' && evenement && origine.type === 'passage-demarre' && preparation?.fichier) void jouer(false);
			else if ((!origine || origine.source === 'capture' || (evenement && origine.type === 'passage-demarre'))
				&& !lecturesJouees.has(lecture.lectureId) && !erreursSignalees.has(lecture.lectureId)) passerEnErreur();
			return;
		}
		if (phase === 'preparation') declarer(preparation);
		else if (phase === 'pret' && appel && lecteurDesigneId === lecteurId) void preparer(courant);
	}

	const retirerFin = lecteur.surFin(() => {
		if (phase !== 'lecture') return;
		changerPhase('fin');
		signaler('fin-lecture', { positionMs: lecteur.position() });
	});
	const retirerErreur = lecteur.surErreur(() => {
		if (phase === 'lecture') passerEnErreur();
	});

	return {
		etat,
		choisirDossier(nouveauxFichiers) {
			if (detruit) return;
			if (estPreparation(phase) || phase === 'lecture' || (phase === 'son-bloque' && estPreparation(phaseInterrompue))) {
				prochainDossier = nouveauxFichiers;
				return;
			}
			appliquerDossier(nouveauxFichiers);
			if (phase === 'sans-dossier') changerPhase(sonDebloque ? 'pret' : 'son-bloque');
			if (phase === 'pret') traiterVue();
		},
		reprendreDossier(dossier) {
			if (detruit || phase !== 'sans-dossier') return;
			({ fichiers, aEteLectrice, sonDebloque } = dossier);
			bibliothequeSeule = dossier.dossier === null;
			intentionDesignation = bibliothequeSeule || aEteLectrice;
			changerPhase(sonDebloque ? 'pret' : 'son-bloque');
		},
		async debloquer({ attendreVue = () => false } = {}) {
			if (detruit) return;
			const essai = ++essaiSon;
			const lectureEnAttente = depart?.enCours;
			try { await lecteur.debloquer(); }
			catch {
				if (detruit || essai !== essaiSon || lectureEnAttente || depart?.enCours || joueOuEstEnPause(phase)) return;
				if (phase === 'son-bloque') return;
				sonDebloque = false;
				phaseInterrompue = phase;
				changerPhase('son-bloque');
				return;
			}
			if (detruit || essai !== essaiSon) return;
			const change = !sonDebloque;
			sonDebloque = true;
			if (phase === 'son-bloque' && !attendreVue()) {
				reprendreApresDeblocage();
				traiterVue();
			} else if (change) surEtat(etat());
			if (change && bibliothequeSeule) intentionDesignation = true;
			actualiserRelais();
		},
		actualiserRelais,
		surVue(nouvelleVue, origine) {
			if (detruit) return;
			const precedente = vue;
			vue = nouvelleVue;
			const { lecteurDesigneId } = vue;
			const avant = aEteLectrice, avantAutre = autreLecteur;
			autreLecteur = lecteurDesigneId != null && lecteurDesigneId !== lecteurId;
			if (lecteurDesigneId != null) aEteLectrice = !autreLecteur;
			if (intentionDesignation && !bibliothequeSeule) {
				intentionDesignation = false;
				if (lecteurDesigneId == null) designation();
			}
			const redesigne = lecteurDesigneId === lecteurId && derniereDesignation && derniereDesignation !== lecteurId;
			if (redesigne && (phase === 'pret' || phase === 'son-bloque')) versionDossier++;
			if (lecteurDesigneId != null) derniereDesignation = lecteurDesigneId;
			if (avant !== aEteLectrice || avantAutre !== autreLecteur) surEtat(etat());
			if (phase === 'son-bloque' && sonDebloque) reprendreApresDeblocage();
			traiterVue(origine, precedente?.etat);
			actualiserRelais();
		},
		detruire() {
			if (detruit) return;
			detruit = true;
			annulerRelais();
			retirerFin?.();
			retirerErreur?.();
			preparation = undefined;
			depart = undefined;
			lecteur.detruire();
		},
	};
}
