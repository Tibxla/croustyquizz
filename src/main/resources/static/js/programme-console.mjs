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
	return '';
}

/** Les mutations passent par HTTP et le programme affiché vient de la vue synchronisée. */
export function monterProgramme(panneau, contexte) {
	const document = panneau.ownerDocument;
	const element = nom => panneau.querySelector(`[data-${nom}]`);
	const formulaire = element('ajout-manche');
	const mode = element('mode-manche');
	const communs = element('reglages-communs');
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

	async function modifier(methode, chemin, corps) {
		if (!peutEnvoyer()) return;
		const annulation = new AbortController();
		envoi = annulation;
		actualiserCommandes();
		try {
			const { statut } = await contexte.envoyer(methode, route + chemin, corps, { signal: annulation.signal });
			// Récupère l’état courant avant une autre édition, même si la Console a manqué l’événement.
			if (!annulation.signal.aborted && statut !== 400) contexte.resynchroniser();
		} catch {
			if (!annulation.signal.aborted) contexte.resynchroniser();
		} finally {
			envoi = undefined;
			if (!annulation.signal.aborted) actualiserCommandes();
		}
	}

	function actualiserReglages() {
		communs.hidden = mode.value !== 'QUIZ';
		communs.disabled = communs.hidden;
	}

	function ajouter(evenement) {
		evenement.preventDefault();
		// Les réglages propres au mode se complètent ici. Le Karaoké utilise la limite du serveur.
		const reglages = mode.value === 'QUIZ' ? {
			nombreEtapes: Number(element('nombre-etapes').value),
			tempsParEtapeSecondes: Number(element('temps-par-etape').value),
		} : {};
		void modifier('POST', '/manches', { mode: mode.value, reglages });
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
