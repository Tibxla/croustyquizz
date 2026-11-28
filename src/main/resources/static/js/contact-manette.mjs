import { ouvrirDialogue } from './dialogue.mjs';

export function monterContactManette(zone, { soireeId, envoyer }) {
	const route = `/api/soirees/${soireeId}/contact`;
	const bouton = zone.querySelector('button');
	const invitation = zone.querySelector('[data-contact-fin]');
	const dialogue = zone.querySelector('dialog');
	const champ = nom => dialogue.querySelector(`[name="${nom}"]`);
	const formulaire = dialogue.querySelector('form');
	const mention = dialogue.querySelector('[data-mention-contact]');
	const declaration = dialogue.querySelector('[data-declaration-contact]');
	const consentement = champ('consentement');
	const valider = dialogue.querySelector('[data-envoyer-contact]');
	const statut = dialogue.querySelector('[data-statut-contact]');
	const lien = dialogue.querySelector('[data-lien-contact]');
	const reessayer = dialogue.querySelector('[data-reessayer-contact]');
	const cycle = new AbortController();
	const { signal } = cycle;
	let mentionChargee = false;
	let chargement = false;
	let inscrit = false;
	invitation.hidden = true;
	zone.hidden = false;

	async function chargerMention(renouveler = false) {
		if (chargement || inscrit || (mentionChargee && !renouveler)) return;
		chargement = true;
		mentionChargee = false;
		formulaire.hidden = true;
		consentement.checked = false;
		reessayer.hidden = true;
		statut.textContent = 'Chargement du formulaire…';
		try {
			const resultat = await envoyer('GET', route, undefined, { signal });
			if (signal.aborted) return;
			if (resultat.statut === 200 && resultat.corps?.mention && resultat.corps?.declaration) {
				mention.textContent = resultat.corps.mention;
				declaration.textContent = resultat.corps.declaration;
				mentionChargee = true;
				formulaire.hidden = false;
				statut.textContent = renouveler ? 'La mention a changé. Lis-la avant de cocher la case.' : '';
				return;
			}
			if ([401, 403, 404].includes(resultat.statut)) {
				statut.textContent = 'L’inscription aux offres est indisponible pour cette Soirée.';
				return;
			}
		} catch {
			if (signal.aborted) return;
		} finally {
			chargement = false;
		}
		statut.textContent = 'Impossible de charger le formulaire. Réessaie dans un instant.';
		reessayer.hidden = false;
	}

	bouton.addEventListener('click', () => {
		ouvrirDialogue(dialogue, bouton);
		void chargerMention();
	}, { signal });
	dialogue.querySelector('[data-fermer-contact]').addEventListener('click', () => dialogue.close(), { signal });
	dialogue.addEventListener('close', () => { consentement.checked = false; }, { signal });
	reessayer.addEventListener('click', () => void chargerMention(), { signal });

	formulaire.addEventListener('submit', async evenement => {
		evenement.preventDefault();
		if (!mentionChargee || !consentement.checked || valider.disabled || inscrit) return;
		valider.disabled = true;
		statut.textContent = 'Enregistrement du Consentement…';
		try {
			const resultat = await envoyer('POST', route, {
				prenom: champ('prenom').value, nom: champ('nom').value, numero: champ('numero').value,
				mention: mention.textContent, declaration: declaration.textContent, consentement: true,
			}, { signal, annoncer: false });
			if (signal.aborted) return;
			if (resultat.statut === 200 && resultat.corps?.consentiLe) {
				inscrit = true;
				formulaire.reset();
				formulaire.hidden = true;
				const adresse = resultat.corps.lienDeDesinscription;
				if (adresse) {
					lien.setAttribute('href', adresse);
					lien.hidden = false;
				}
				statut.textContent = adresse
					? 'Ton Consentement est enregistré. Garde ton lien personnel pour te désinscrire à tout moment.'
					: 'Ton Consentement est enregistré. Tu retrouveras ton lien de désinscription dans chaque SMS.';
				if (dialogue.open) statut.focus();
			} else if (resultat.corps?.code === 'MENTION_MODIFIEE') {
				await chargerMention(true);
				if (dialogue.open) statut.focus();
			} else {
				statut.textContent = resultat.corps?.detail ?? 'Impossible de confirmer l’inscription. Tu peux réessayer.';
			}
		} catch {
			if (!signal.aborted) statut.textContent = 'Impossible de confirmer l’inscription. Tu peux réessayer.';
		} finally {
			valider.disabled = false;
		}
	}, { signal });

	return {
		afficher(vue) { invitation.hidden = vue.soiree.etat !== 'TERMINEE'; },
		demonter() { cycle.abort(); },
	};
}
