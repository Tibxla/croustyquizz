import { decrireManche } from './vue-commune.mjs';

/**
 * Emplacement des réglages propres à chaque Mode de jeu (#9). Pour proposer un mode dans la Console, lui
 * ajouter une entrée ici, puis son option dans « Mode de jeu » de console.html et, s'il a des champs, un
 * fieldset `data-<zone>` dans le formulaire d'ajout. `lire` rend l'objet `reglages` de CreationManche
 * (docs/api/schemas.yaml), que le serveur valide avec la fabrique du mode ; `decrire` résume les réglages
 * d'une Manche du programme. Le Blind test (ReglagesBlindTest) n'a pas encore ses champs dans la Console.
 */
export const REGLAGES_DES_MODES = {
	QUIZ: {
		zone: 'reglages-communs',
		lire: element => ({
			nombreEtapes: Number(element('nombre-etapes').value),
			tempsParEtapeSecondes: Number(element('temps-par-etape').value),
		}),
		decrire: ({ nombreEtapes, tempsParEtapeSecondes }) => nombreEtapes === undefined ? ''
			: `${nombreEtapes} étape${nombreEtapes === 1 ? '' : 's'} · ${tempsParEtapeSecondes} s par étape`,
	},
	KARAOKE: {
		// Sans champ : le serveur applique sa limite de Passages en attente.
		lire: () => ({}),
		decrire: ({ limitePassagesEnAttente: limite }) => limite === undefined ? ''
			: `Jusqu’à ${limite} Passage${limite === 1 ? '' : 's'} en attente`,
	},
};

function decrireReglages({ mode, reglages }) {
	return REGLAGES_DES_MODES[mode]?.decrire(reglages) ?? '';
}

/** Les mutations passent par HTTP et le programme affiché vient de la vue synchronisée. */
export function monterProgramme(panneau, contexte) {
	const document = panneau.ownerDocument;
	const element = nom => panneau.querySelector(`[data-${nom}]`);
	const formulaire = element('ajout-manche');
	const mode = element('mode-manche');
	const zonesDeReglages = Object.entries(REGLAGES_DES_MODES).filter(([, { zone }]) => zone)
		.map(([modeDeJeu, { zone }]) => [modeDeJeu, element(zone)]);
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
		for (const [modeDeJeu, zone] of zonesDeReglages) {
			zone.hidden = mode.value !== modeDeJeu;
			zone.disabled = zone.hidden;
		}
	}

	function ajouter(evenement) {
		evenement.preventDefault();
		const reglagesDuMode = REGLAGES_DES_MODES[mode.value];
		if (!reglagesDuMode) return;
		void modifier('POST', '/manches', { mode: mode.value, reglages: reglagesDuMode.lire(element) });
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
