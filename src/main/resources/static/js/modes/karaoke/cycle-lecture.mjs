import { trouverFichierDeChanson } from './fichiers.mjs';
import { delaiAppelRestant } from './paroles.mjs';
import { passageActif } from './presentation.mjs';

const estPreparation = etat => etat === 'preparation' || etat === 'preparee';
const joueOuEstEnPause = etat => etat === 'lecture' || etat === 'pause';
const fichierIllisible = { fichier: null, motif: 'FICHIER_ILLISIBLE' };

export function creerCycleLecture({ lecteur, lecteurId, envoyer, horloge, surEtat, setTimeout, clearTimeout }) {
	let phase = 'sans-dossier', phaseInterrompue, sonDebloque = false, aEteLectrice = false, autreLecteur = false;
	let fichiers, prochainDossier, versionDossier = 0, intentionDesignation = false;
	let vue, derniereDesignation, preparation, depart, delai, essaiSon = 0, detruit = false;
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
	const cheminPassage = (courant, suffixe) => `/passages/${encodeURIComponent(courant.passageId)}/${suffixe}`;

	function signaler(suffixe, champs = {}) {
		const { courant, lecture } = vue;
		void commander('POST', cheminPassage(courant, suffixe), { etapeId: courant.etapeId, lectureId: lecture.lectureId, ...champs });
	}
	function annulerDelai() {
		if (delai !== undefined) clearTimeout(delai);
		delai = undefined;
	}
	function libererPassage() {
		annulerDelai();
		const avaitFichier = preparation?.charge || depart;
		preparation = undefined;
		depart = undefined;
		demandesTraitees.clear();
		if (avaitFichier) lecteur.detruire();
	}
	function appliquerDossier(nouveauxFichiers) {
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

	function estToujoursEnPreparation(cible) {
		const { courant } = vue;
		return preparation === cible && cible.version === versionDossier && courant?.etat === 'APPELE'
			&& courant.passageId === cible.passageId && courant.numeroModification === cible.numeroModification;
	}
	function resteAvantEcheance(courant) {
		if (courant.echeanceAppelLe == null) return null;
		return delaiAppelRestant({ ...courant, maintenant: horloge.maintenantServeur(), enPause: false });
	}
	function declarer(cible) {
		if (phase !== 'preparation' || !estToujoursEnPreparation(cible) || cible.declaree || !cible.resultat) return;
		cible.declaree = true;
		annulerDelai();
		// Les métadonnées peuvent arriver avant le rappel du minuteur, même après l'échéance.
		const { fichier, motif } = resteAvantEcheance(vue.courant) === 0 ? fichierIllisible : cible.resultat;
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
			cible.lectureId = reponse.corps?.lectureId;
			if (phase === 'preparation') changerPhase('preparee');
			else if (phase === 'son-bloque' && phaseInterrompue === 'preparation') phaseInterrompue = 'preparee';
		});
	}

	function recalerDelai(cible) {
		annulerDelai();
		if (!estToujoursEnPreparation(cible) || cible.declaree) return;
		const restant = resteAvantEcheance(vue.courant);
		if (restant === null) return;
		const expirer = () => {
			cible.resultat = fichierIllisible;
			declarer(cible);
		};
		if (restant === 0) expirer();
		else delai = setTimeout(expirer, restant);
	}

	async function preparer(courant) {
		const { passageId, numeroModification, chanson } = courant;
		const cle = JSON.stringify([passageId, numeroModification, versionDossier]);
		if (declarations.has(cle)) return;
		declarations.add(cle);
		const cible = { passageId, numeroModification, version: versionDossier };
		preparation = cible;
		changerPhase('preparation');
		recalerDelai(cible);
		if (cible.declaree) return;
		const resultat = trouverFichierDeChanson(fichiers, chanson);
		let fichier = null, motif = 'FICHIER_' + resultat.etat.toUpperCase();
		if (resultat.etat === 'unique') {
			cible.charge = true;
			try {
				const { dureeMs } = await lecteur.charger(resultat.fichier);
				fichier = { nom: resultat.fichier.name, tailleOctets: resultat.fichier.size, dureeMs };
				motif = null;
			} catch { motif = 'FICHIER_ILLISIBLE'; }
		}
		if (!estToujoursEnPreparation(cible) || cible.declaree) return;
		cible.resultat = { fichier, motif };
		declarer(cible);
	}

	function estNotreLecture(courant, lecture) {
		if (!courant || !lecture) return false;
		if (lecture.lecteurId != null) {
			return lecture.lecteurId === lecteurId && (!preparation || courant.passageId === preparation.passageId);
		}
		return Boolean(preparation?.lectureId && lecture.lectureId === preparation.lectureId && courant.passageId === preparation.passageId);
	}
	function passerEnErreur() {
		if (!preparation) preparation = { passageId: vue.courant.passageId };
		if (preparation.charge || depart) lecteur.detruire();
		preparation.charge = false;
		depart = undefined;
		annulerDelai();
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
		const abandonnerPreparation = estPreparation(phase) && (lecteurDesigneId !== lecteurId || (locale && !notreLecture)
			|| (evenement && origine.type === 'passage-demarre' && !locale));
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
		if (phase === 'preparation' && notreLecture) {
			annulerDelai();
			changerPhase('preparee');
		}
		if (actif && locale && notreLecture && lecture.etat === 'DEMARREE') {
			if (phase === 'preparee' && evenement && origine.type === 'passage-demarre' && preparation?.fichier) void jouer(false);
			else if ((!origine || origine.source === 'capture' || (evenement && origine.type === 'passage-demarre'))
				&& !lecturesJouees.has(lecture.lectureId) && !erreursSignalees.has(lecture.lectureId)) passerEnErreur();
			return;
		}
		if (phase === 'preparation') {
			if (preparation.resultat) declarer(preparation);
			recalerDelai(preparation);
		} else if (phase === 'pret' && appel && lecteurDesigneId === lecteurId) void preparer(courant);
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
			intentionDesignation = aEteLectrice;
			changerPhase(sonDebloque ? 'pret' : 'son-bloque');
		},
		async debloquer() {
			if (detruit) return;
			const essai = ++essaiSon;
			try { await lecteur.debloquer(); }
			catch {
				if (detruit || essai !== essaiSon || joueOuEstEnPause(phase)) return;
				if (phase === 'son-bloque') return;
				sonDebloque = false;
				phaseInterrompue = phase;
				annulerDelai();
				changerPhase('son-bloque');
				return;
			}
			if (detruit || essai !== essaiSon) return;
			const change = !sonDebloque;
			sonDebloque = true;
			if (phase === 'son-bloque') {
				const interrompue = phaseInterrompue;
				phaseInterrompue = undefined;
				if (!estPreparation(interrompue)) {
					if (fichiers) redevenirPret();
					else changerPhase('sans-dossier');
				} else changerPhase(interrompue);
				traiterVue();
			} else if (change) surEtat(etat());
		},
		surVue(nouvelleVue, origine) {
			if (detruit) return;
			const precedente = vue;
			vue = nouvelleVue;
			const { lecteurDesigneId } = vue;
			const avant = aEteLectrice, avantAutre = autreLecteur;
			autreLecteur = lecteurDesigneId != null && lecteurDesigneId !== lecteurId;
			if (lecteurDesigneId != null) aEteLectrice = !autreLecteur;
			if (intentionDesignation) {
				intentionDesignation = false;
				if (lecteurDesigneId == null) designation();
			}
			if (lecteurDesigneId === lecteurId && derniereDesignation && derniereDesignation !== lecteurId && phase === 'pret') versionDossier++;
			if (lecteurDesigneId != null) derniereDesignation = lecteurDesigneId;
			if (avant !== aEteLectrice || avantAutre !== autreLecteur) surEtat(etat());
			traiterVue(origine, precedente?.etat);
		},
		detruire() {
			if (detruit) return;
			detruit = true;
			retirerFin?.();
			retirerErreur?.();
			annulerDelai();
			preparation = undefined;
			depart = undefined;
			lecteur.detruire();
		},
	};
}
