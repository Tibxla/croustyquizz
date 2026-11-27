import { decrireManche } from './vue-commune.mjs';

function decrireReglages({ reglages }) {
	if (reglages.nombreEtapes !== undefined) {
		const nombre = reglages.nombreEtapes;
		return `${nombre} étape${nombre === 1 ? '' : 's'} · ${reglages.tempsParEtapeSecondes} s par étape`;
	}
	if (reglages.limitePassagesEnAttente !== undefined) {
		const limite = reglages.limitePassagesEnAttente;
		return `Jusqu’à ${limite} Passage${limite === 1 ? '' : 's'} en attente`;
	}
	if (reglages.nombreMorceaux !== undefined) {
		const nombre = reglages.nombreMorceaux, { type, idDeezer } = reglages.source;
		return `${nombre} Morceau${nombre === 1 ? '' : 'x'} · ${type === 'GENRE' ? 'genre' : 'playlist'} Deezer ${idDeezer}`;
	}
	return '';
}

/** Les mutations passent par HTTP et le programme affiché vient de la vue synchronisée. */
export function monterProgramme(panneau, contexte) {
	const document = panneau.ownerDocument;
	const element = nom => panneau.querySelector(`[data-${nom}]`);
	const formulaire = element('ajout-manche');
	const communs = element('reglages-communs');
	const champAvecErreur = nom => ({ champ: element(nom), erreur: element(`${nom}-erreur`) });
	const choixMode = champAvecErreur('mode-manche');
	const mode = choixMode.champ;
	const nombre = champAvecErreur('nombre-etapes');
	const temps = champAvecErreur('temps-par-etape');
	const erreurAjout = element('ajout-manche-erreur');
	const blindTest = element('reglages-blindtest');
	const ajout = element('soumettre-manche');
	const liste = element('programme');
	const route = `/api/soirees/${contexte.soireeId}`;
	let modifiable = false;
	let synchronisee = false;
	let envoi;
	let dernierProgramme;

	const commandes = () => [...liste.querySelectorAll('[data-action-programme]')];
	const peutEnvoyer = () => modifiable && synchronisee && !envoi;
	function actualiserCommandes() {
		for (const bouton of [ajout, ...commandes()]) {
			bouton.setAttribute('aria-disabled', String(!peutEnvoyer() || bouton.disabled));
		}
	}

	// 400 : réglages hors contrat ; 422 : réglages à corriger, comme une source Deezer inconnue ou trop courte.
	const aCorriger = statut => statut === 400 || statut === 422;

	async function modifier(methode, chemin, corps, surRequeteInvalide) {
		if (!peutEnvoyer()) return;
		const annulation = new AbortController();
		envoi = annulation;
		actualiserCommandes();
		try {
			const { statut, corps: reponse } = await contexte.envoyer(methode, route + chemin, corps, {
				signal: annulation.signal,
				annoncer: ({ statut }) => !aCorriger(statut) || !surRequeteInvalide || !modifiable,
			});
			if (annulation.signal.aborted) return;
			if (statut === 400 || (statut === 422 && surRequeteInvalide)) {
				if (modifiable) surRequeteInvalide?.(reponse);
				return;
			}
			// Récupère l’état courant avant une autre édition, même si la Console a manqué l’événement.
			contexte.resynchroniser();
		} catch {
			if (!annulation.signal.aborted) contexte.resynchroniser();
		} finally {
			envoi = undefined;
			if (!annulation.signal.aborted) actualiserCommandes();
		}
	}

	function actualiserReglages() {
		effacerErreurs();
		communs.hidden = mode.value !== 'QUIZ';
		communs.disabled = communs.hidden;
		if (blindTest) {
			blindTest.hidden = mode.value !== 'BLIND_TEST';
			blindTest.disabled = blindTest.hidden;
		}
	}

	/** Réglages du Blind test ; le Quiz est validé champ par champ, le Karaoké utilise la limite du serveur. */
	function reglagesBlindTest() {
		return {
			source: { type: element('source-blindtest').value, idDeezer: Number(element('id-deezer').value) },
			nombreMorceaux: Number(element('nombre-morceaux').value),
		};
	}

	function effacerErreurs() {
		for (const { champ, erreur } of [choixMode, nombre, temps]) {
			erreur.hidden = true;
			erreur.textContent = '';
			champ.removeAttribute('aria-invalid');
		}
		erreurAjout.hidden = true;
		erreurAjout.textContent = '';
	}

	function signalerChamp({ champ, erreur }, message) {
		erreur.textContent = message;
		erreur.hidden = false;
		champ.setAttribute('aria-invalid', 'true');
		champ.focus();
	}

	function lireEntier(saisie, consigne) {
		const { champ } = saisie;
		const valeur = Number(champ.value);
		const min = Number(champ.getAttribute('min'));
		const max = Number(champ.getAttribute('max'));
		if (Number.isInteger(valeur) && valeur >= min && valeur <= max) return valeur;
		signalerChamp(saisie, consigne(min, max));
		// Rien ne part : le bandeau d'un envoi précédent ne doit pas rester à côté de cette erreur.
		contexte.effacerAnnonce();
	}

	function ajouter(evenement) {
		evenement.preventDefault();
		if (!peutEnvoyer()) return;
		effacerErreurs();
		const modeDemande = mode.value;
		let reglages = {};
		if (modeDemande === 'QUIZ') {
			const nombreEtapes = lireEntier(nombre, (min, max) => `Indique un nombre entier d’étapes entre ${min} et ${max}.`);
			if (nombreEtapes === undefined) return;
			const tempsParEtapeSecondes = lireEntier(temps, (min, max) => `Indique une durée entière entre ${min} et ${max} secondes.`);
			if (tempsParEtapeSecondes === undefined) return;
			reglages = { nombreEtapes, tempsParEtapeSecondes };
		} else if (modeDemande === 'BLIND_TEST') {
			reglages = reglagesBlindTest();
		}
		void modifier('POST', '/manches', { mode: modeDemande, reglages }, reponse => {
			// Le Probleme ne désigne aucun champ : le refus s'affiche sous le formulaire, sans en marquer un,
			// et s'annonce par son rôle alert. Le focus reste où l'Animateur l'a laissé.
			erreurAjout.textContent = reponse?.detail || 'Vérifie le Mode de jeu et les réglages de la Manche.';
			erreurAjout.hidden = false;
		});
	}

	function ligne(manche, index, programme) {
		const li = document.createElement('li');
		li.className = 'programme__ligne';
		const texte = document.createElement('div');
		const titre = document.createElement('strong');
		titre.textContent = decrireManche(manche);
		texte.append(titre);
		const reglages = decrireReglages(manche);
		if (reglages) {
			const detail = document.createElement('small');
			detail.textContent = reglages;
			texte.append(detail);
		}
		li.append(texte);
		if (!modifiable) return li;

		const actions = document.createElement('div');
		actions.className = 'programme__commandes';
		for (const [action, libelle, decalage] of [['monter', 'Monter', -1], ['descendre', 'Descendre', 1], ['retirer', 'Retirer', 0]]) {
			const bouton = document.createElement('button');
			bouton.type = 'button';
			bouton.className = 'bouton';
			bouton.textContent = libelle;
			bouton.setAttribute('aria-label', `${libelle} la Manche ${manche.ordre + 1}`);
			bouton.setAttribute('data-action-programme', action);
			bouton.setAttribute('data-manche', manche.id);
			const position = index + decalage;
			bouton.disabled = position < 0 || position >= programme.length;
			bouton.addEventListener('click', () => {
				if (!decalage) {
					void modifier('DELETE', `/manches/${manche.id}`);
					return;
				}
				const mancheIds = programme.map(m => m.id);
				[mancheIds[index], mancheIds[position]] = [mancheIds[position], mancheIds[index]];
				void modifier('PUT', '/ordre-des-manches', { mancheIds });
			});
			actions.append(bouton);
		}
		li.append(actions);
		return li;
	}

	const desabonner = contexte.surEtatConnexion(etat => {
		synchronisee = etat === 'synchronisee';
		actualiserCommandes();
	});
	formulaire.addEventListener('submit', ajouter);
	mode.addEventListener('change', actualiserReglages);
	actualiserReglages();

	return {
		afficher(vue) {
			const programme = vue.programme ?? [];
			modifiable = ['PREPARATION', 'OUVERTE'].includes(vue.soiree.etat)
				&& programme.every(manche => manche.etat === 'EN_ATTENTE');
			const signature = JSON.stringify([modifiable, programme]);
			if (signature === dernierProgramme) return;
			dernierProgramme = signature;
			const focus = document.activeElement;
			const focusListe = liste.contains(focus);
			const focusFormulaire = formulaire.contains(focus);
			formulaire.hidden = !modifiable;
			element('programme-fige').hidden = modifiable;
			element('programme-vide').hidden = programme.length > 0;
			liste.replaceChildren(...programme.map((manche, index) => ligne(manche, index, programme)));
			actualiserCommandes();

			if (!modifiable && (focusListe || focusFormulaire)) {
				panneau.querySelector('h2').focus();
			} else if (focusListe) {
				const equivalents = commandes().filter(bouton => !bouton.disabled
					&& bouton.getAttribute('data-manche') === focus.getAttribute('data-manche'));
				const suivant = equivalents.find(bouton => bouton.getAttribute('data-action-programme')
					=== focus.getAttribute('data-action-programme'));
				(suivant ?? equivalents[0] ?? ajout).focus();
			}
		},
		demonter() {
			envoi?.abort();
			desabonner();
			formulaire.removeEventListener('submit', ajouter);
			mode.removeEventListener('change', actualiserReglages);
			liste.replaceChildren();
		},
	};
}
