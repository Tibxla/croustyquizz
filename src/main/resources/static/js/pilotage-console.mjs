import { nouvelleCle } from './api.js';
import { ouvrirDialogue } from './dialogue.mjs';

export function monterPilotage(barre, contexte) {
	const boutons = [...barre.querySelectorAll('[data-piloter-soiree]')];
	const actionDe = bouton => bouton.getAttribute('data-piloter-soiree');
	const dialogue = barre.querySelector('[data-dialogue-fin]');
	const annuler = dialogue.querySelector('[data-annuler-fin]');
	const confirmer = dialogue.querySelector('[data-confirmer-fin]');
	const route = `/api/soirees/${contexte.soireeId}/commandes`;
	let vue;
	let synchronisee = false;
	let envoi;
	let reessai;

	const suivante = () => vue?.programme?.find(manche => manche.etat === 'EN_ATTENTE');
	function peutEnvoyer(action) {
		if (!vue || !synchronisee || envoi) return false;
		const enCours = vue.soiree.etat === 'EN_COURS';
		const lancementAReprendre = enCours && reessai?.action === 'LANCER'
			&& vue.programme.every(manche => manche.etat === 'EN_ATTENTE');
		switch (action) {
			case 'LANCER':
				return Boolean(suivante()) && (vue.soiree.etat === 'OUVERTE' || lancementAReprendre);
			case 'MANCHE_SUIVANTE':
				return enCours && !vue.mancheActive && Boolean(suivante()) && !lancementAReprendre;
			case 'TERMINER':
				return enCours;
		}
		return false;
	}

	// Seules les commandes de l'étape de la Soirée restent visibles : un « Lancer la Soirée » désactivé pendant la
	// Soirée ressemblait encore à l'action principale.
	function visible(action) {
		const etat = vue?.soiree?.etat;
		if (!etat) return action === 'LANCER';
		if (action === 'LANCER') return etat === 'PREPARATION' || etat === 'OUVERTE' || peutEnvoyer('LANCER');
		return etat === 'EN_COURS';
	}

	function actualiser() {
		const focus = barre.ownerDocument.activeElement;
		for (const bouton of boutons) {
			bouton.setAttribute('aria-disabled', String(!peutEnvoyer(actionDe(bouton))));
			bouton.hidden = !visible(actionDe(bouton));
		}
		if (focus && boutons.includes(focus) && focus.hidden) boutons.find(bouton => !bouton.hidden)?.focus();
		if (dialogue.open && !peutEnvoyer('TERMINER')) dialogue.close();
	}

	async function envoyer(action) {
		if (!peutEnvoyer(action)) return;
		const cible = action === 'MANCHE_SUIVANTE' ? suivante().id : null;
		const cle = reessai?.action === action && reessai.cible === cible ? reessai.cle : nouvelleCle();
		reessai = { action, cible, cle };
		const annulation = new AbortController();
		envoi = annulation;
		actualiser();
		try {
			const { statut, corps } = await contexte.envoyer('POST', route, { action },
				{ entetes: { 'Idempotency-Key': cle }, signal: annulation.signal });
			if (annulation.signal.aborted) return;
			const inconnu = statut === 503 || (statut >= 500 && !corps?.code);
			if (!inconnu) reessai = undefined;
			if (inconnu || statut === 200 || statut === 409) contexte.resynchroniser();
		} catch {
			if (!annulation.signal.aborted) contexte.resynchroniser();
		} finally {
			envoi = undefined;
			if (!annulation.signal.aborted) actualiser();
		}
	}

	function piloter(evenement) {
		const bouton = evenement.currentTarget ?? evenement.target;
		const action = actionDe(bouton);
		if (!peutEnvoyer(action)) return;
		if (action === 'TERMINER') ouvrirDialogue(dialogue, bouton);
		else void envoyer(action);
	}
	const fermer = () => dialogue.close();
	const terminer = () => {
		if (!dialogue.open) return;
		fermer();
		void envoyer('TERMINER');
	};
	for (const bouton of boutons) bouton.addEventListener('click', piloter);
	annuler.addEventListener('click', fermer);
	confirmer.addEventListener('click', terminer);

	const desabonner = contexte.surEtatConnexion(etat => {
		synchronisee = etat === 'synchronisee';
		actualiser();
	});
	return {
		afficher(nouvelleVue) {
			vue = nouvelleVue;
			if (reessai?.action === 'LANCER' && (vue.mancheActive || vue.soiree.etat === 'TERMINEE')) reessai = undefined;
			actualiser();
		},
		demonter() {
			envoi?.abort();
			desabonner();
			for (const bouton of boutons) bouton.removeEventListener('click', piloter);
			annuler.removeEventListener('click', fermer);
			confirmer.removeEventListener('click', terminer);
			if (dialogue.open) fermer();
		},
	};
}
