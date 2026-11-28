import { appeler as appelerApi } from './api.js';

const FORME_CLE = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\.[A-Za-z0-9_-]{43}$/;
const LIEN_INVALIDE = 'Ce lien de désinscription est invalide ou a expiré.';

export function monterDesinscription(zone, { navigation = location, historique = history, appeler = appelerApi } = {}) {
	const bouton = zone.querySelector('button');
	const aide = zone.querySelector('[data-aide-desinscription]');
	const statut = zone.querySelector('[role="status"]');
	let cle = new URLSearchParams(navigation.hash.slice(1)).get('cle');
	if (navigation.hash) historique.replaceState(null, '', navigation.pathname + navigation.search);

	if (!cle || !FORME_CLE.test(cle)) {
		bouton.hidden = true;
		statut.textContent = LIEN_INVALIDE;
		return;
	}
	bouton.hidden = false;
	aide.hidden = false;
	bouton.addEventListener('click', async () => {
		if (!cle || bouton.disabled) return;
		bouton.disabled = true;
		statut.textContent = 'Désinscription en cours…';
		const resultat = await appeler('POST', '/api/contacts/desinscription', { corps: { cle } });
		if (resultat.statut === 204 || resultat.statut === 404) {
			cle = null;
			bouton.hidden = true;
			aide.hidden = true;
			statut.textContent = resultat.statut === 204
				? 'Ta désinscription est confirmée. Tu ne recevras plus les offres de ce Lieu par SMS.'
				: LIEN_INVALIDE;
			statut.focus();
			return;
		}
		statut.textContent = 'Impossible de confirmer ta désinscription. Réessaie dans un instant.';
		bouton.disabled = false;
	});
}

const zone = globalThis.document?.querySelector('[data-desinscription]');
if (zone) monterDesinscription(zone);
