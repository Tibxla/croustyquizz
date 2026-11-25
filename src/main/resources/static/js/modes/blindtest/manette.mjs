import { creerSocle, ecrire, rendreParties } from './commun.mjs';
import { creerEnvoiBrouillon, formaterSecondes, tempsRestantMs, texteNumero, verdictPersonnel } from './logique.mjs';

const INCONNU = 'Confirmation à vérifier.';

/**
 * Manette du Blind test : le Buzz, puis la Proposition de titre du Joueur qui a la main. Le verdict est lu dans la
 * vue publiée, jamais déduit de l'accusé ni de la saisie.
 */
export function monter(zones, contexte, dependances = {}) {
	const socle = creerSocle(zones, contexte, { classe: 'blindtest--manette', intervalle: dependances.intervalle });
	const { e } = socle;
	const programmerDelai = dependances.programmerDelai ?? ((f, ms) => setTimeout(f, ms));
	const annulerDelai = dependances.annulerDelai ?? (id => clearTimeout(id));
	const creerId = dependances.creerUuid ?? (() => globalThis.crypto.randomUUID());

	const numero = e('p', 'blindtest__numero chiffres');
	const titre = e('h2', 'blindtest__titre');
	const parties = e('ul', 'blindtest__parties');
	parties.hidden = true;
	const verdict = e('p', 'blindtest__verdict');
	verdict.setAttribute('aria-live', 'polite');
	const revelation = e('div', 'blindtest__revelation');
	const revelationTitre = e('p', 'blindtest__revele-titre'), revelationArtiste = e('p', 'blindtest__revele-artiste');
	revelation.append(revelationTitre, revelationArtiste);

	const formulaire = e('form', 'blindtest__proposition');
	formulaire.setAttribute('novalidate', '');
	const temps = e('p', 'blindtest__temps chiffres');
	const champs = { TITRE: champ('Titre', 'blindtest-titre'), ARTISTE: champ('Artiste', 'blindtest-artiste') };
	const valider = e('button', 'bouton bouton--action bouton--grand', 'Valider');
	valider.type = 'submit';
	formulaire.append(temps, champs.TITRE.conteneur, champs.ARTISTE.conteneur, valider);

	const statut = e('p', 'blindtest__statut');
	statut.setAttribute('role', 'status');
	const buzz = e('button', 'bouton bouton--action bouton--grand blindtest__buzz', 'Buzzer');
	buzz.type = 'button';
	socle.principale.append(numero, titre, parties, verdict, revelation, formulaire, statut, buzz);

	let vue, bloc, detruit = false;
	let buzzEnVol, buzzEnvoiId, buzzEtape;
	let main, validation;

	function champ(libelle, id) {
		const conteneur = e('div', 'champ');
		const label = e('label', '', libelle);
		const entree = e('input');
		entree.id = id;
		entree.type = 'text';
		entree.setAttribute('maxlength', '200');
		entree.setAttribute('autocomplete', 'off');
		entree.setAttribute('autocapitalize', 'off');
		entree.setAttribute('spellcheck', 'false');
		label.setAttribute('for', id);
		conteneur.append(label, entree);
		return { conteneur, entree };
	}

	const racine = () => `/api/soirees/${encodeURIComponent(contexte.soireeId)}/manches/${encodeURIComponent(bloc.mancheId)}`
		+ `/morceaux/${encodeURIComponent(bloc.etapeId)}`;
	const demandes = () => bloc.champsDemandes;
	const saisie = () => ({
		titre: demandes().includes('TITRE') ? champs.TITRE.entree.value : '',
		artiste: demandes().includes('ARTISTE') ? champs.ARTISTE.entree.value : '',
	});
	// Même règle que la coquille : un 503, ou un 5xx sans code du contrat, peut suivre une commande appliquée.
	const inconnu = (code, corps) => code === 503 || (code >= 500 && !corps?.code);

	/** Une main par Morceau : à sa première vue, la saisie repart du brouillon retenu par le serveur. */
	function suivreMain(origine) {
		const cle = bloc.phase === 'PROPOSITION' && bloc.aLaMain ? bloc.etapeId : null;
		if (cle === main?.cle) return;
		main?.envoi.detruire();
		main?.requetes.abort();
		main = undefined;
		validation = undefined;
		if (!cle) return;
		const requetes = new AbortController();
		const envoi = creerEnvoiBrouillon({ programmer: programmerDelai, annuler: annulerDelai,
			envoyer: corps => contexte.envoyer('PUT', `${racine()}/brouillon`, corps,
				{ signal: requetes.signal, annoncer: false }) });
		main = { cle, envoi, requetes, url: racine() };
		const retenu = bloc.monBrouillon;
		envoi.reprendre(retenu?.numero);
		champs.TITRE.entree.value = retenu?.titre ?? '';
		champs.ARTISTE.entree.value = retenu?.artiste ?? '';
		// Le Buzz vient d'être accepté : la saisie prend le focus ; une capture ne déplace rien.
		if (origine?.source === 'evenement') {
			queueMicrotask(() => {
				if (main?.cle === cle) (demandes().includes('TITRE') ? champs.TITRE : champs.ARTISTE).entree.focus();
			});
		}
	}

	function surSaisie() {
		if (main && !validation?.enVol && !validation?.acceptee) main.envoi.saisir(saisie());
	}

	async function proposer(evenement) {
		evenement.preventDefault();
		if (valider.disabled || !main) return;
		const courante = main;
		validation = { envoiId: validation?.envoiId ?? creerId(), enVol: true };
		const tentative = validation;
		ecrire(statut, 'Envoi en cours…');
		rendre();
		try {
			const { statut: code, corps } = await contexte.envoyer('POST', `${courante.url}/proposition`,
				{ envoiId: tentative.envoiId, ...saisie() }, { signal: courante.requetes.signal, annoncer: false });
			if (validation !== tentative) return;
			if (code >= 200 && code < 300) {
				// Jugée : la saisie s'arrête là, la vue publiée apportera le verdict.
				tentative.acceptee = true;
				courante.envoi.detruire();
				ecrire(statut, 'Proposition envoyée.');
			} else if (inconnu(code, corps)) {
				ecrire(statut, INCONNU);
				contexte.resynchroniser();
			} else {
				validation = undefined;
				ecrire(statut, corps?.detail ?? `Proposition refusée (HTTP ${code}).`);
			}
		} catch (erreur) {
			if (validation !== tentative || erreur.name === 'AbortError') return;
			ecrire(statut, INCONNU);
			contexte.resynchroniser();
		} finally {
			if (validation === tentative) tentative.enVol = false;
			if (!detruit) rendre();
		}
	}

	async function buzzer() {
		if (buzz.disabled) return;
		buzzEnvoiId ??= creerId();
		const requete = buzzEnVol = new AbortController();
		const corps = { envoiId: buzzEnvoiId, mancheId: bloc.mancheId, etapeId: bloc.etapeId };
		ecrire(statut, '');
		rendre();
		try {
			const reponse = await contexte.envoyer('POST', `/api/soirees/${encodeURIComponent(contexte.soireeId)}/buzz`,
				corps, { signal: requete.signal, annoncer: false });
			if (reponse.statut >= 200 && reponse.statut < 300) buzzEnvoiId = undefined;
			else if (inconnu(reponse.statut, reponse.corps)) {
				ecrire(statut, INCONNU);
				contexte.resynchroniser();
			} else {
				buzzEnvoiId = undefined;
				ecrire(statut, reponse.corps?.detail ?? `Buzz refusé (HTTP ${reponse.statut}).`);
			}
		} catch (erreur) {
			if (erreur.name !== 'AbortError') {
				ecrire(statut, INCONNU);
				contexte.resynchroniser();
			}
		} finally {
			if (buzzEnVol === requete) buzzEnVol = undefined;
			if (!detruit) rendre();
		}
	}

	function rafraichirTemps() {
		if (!bloc?.main || !main) return;
		ecrire(temps, `${formaterSecondes(tempsRestantMs(bloc.main, contexte.maintenantServeur()))} pour répondre`);
	}

	function texteTitre() {
		if (bloc.etat === 'EN_PAUSE') return 'Pause';
		switch (bloc.phase) {
			case 'PREPARATION': return 'Le Morceau arrive…';
			case 'EXTRAIT': return bloc.bloque ? 'Écoute la suite' : 'Écoute et buzze !';
			case 'PROPOSITION': return bloc.aLaMain ? 'À toi !' : `${bloc.main.pseudo} a la main`;
			default: return 'C’était…';
		}
	}

	function rendre() {
		if (!bloc || detruit) return;
		ecrire(numero, texteNumero(bloc));
		ecrire(titre, texteTitre());
		rendreParties(socle, parties, bloc.partiesTrouvees);
		const personnel = verdictPersonnel(bloc, vue.joueurId);
		ecrire(verdict, personnel?.texte ?? '');
		verdict.hidden = !personnel;
		verdict.className = `blindtest__verdict${personnel ? ` blindtest__verdict--${personnel.ton}` : ''}`;
		revelation.hidden = !bloc.revelation;
		ecrire(revelationTitre, bloc.revelation?.titre ?? '');
		ecrire(revelationArtiste, bloc.revelation?.artiste ?? '');

		formulaire.hidden = !main;
		for (const [partie, { conteneur }] of Object.entries(champs)) conteneur.hidden = !main || !demandes().includes(partie);
		valider.disabled = !main || !socle.synchronisee || bloc.etat !== 'EN_COURS'
			|| Boolean(validation?.enVol || validation?.acceptee);
		rafraichirTemps();
		temps.hidden = !main;

		if (buzzEtape !== bloc.etapeId) { buzzEtape = bloc.etapeId; buzzEnvoiId = undefined; }
		buzz.hidden = bloc.phase !== 'EXTRAIT' || bloc.bloque;
		buzz.disabled = !bloc.peutBuzzer || !socle.synchronisee || Boolean(buzzEnVol);
		ecrire(buzz, buzzEnvoiId && !buzzEnVol ? 'Réessayer le Buzz' : 'Buzzer');
		socle.animer(Boolean(main) && bloc.etat === 'EN_COURS', rafraichirTemps);
	}

	socle.ecouter(buzz, 'click', buzzer);
	socle.ecouter(formulaire, 'submit', proposer);
	socle.ecouter(champs.TITRE.entree, 'input', surSaisie);
	socle.ecouter(champs.ARTISTE.entree, 'input', surSaisie);
	socle.surConnexion = () => rendre();

	return {
		afficher(nouvelle, origine) {
			if (detruit) return;
			const precedent = bloc;
			vue = nouvelle;
			bloc = nouvelle.blindtest;
			if (precedent && precedent.etapeId !== bloc.etapeId) ecrire(statut, '');
			suivreMain(origine);
			rendre();
		},
		demonter() {
			if (detruit) return;
			detruit = true;
			buzzEnVol?.abort();
			main?.envoi.detruire();
			main?.requetes.abort();
			socle.detruire();
		},
	};
}
