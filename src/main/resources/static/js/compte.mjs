import { appeler } from './api.js';

export const ROUTE_SESSION = '/api/animateurs/session';
export const SOIREES_DU_LIEU = '/soirees.html';
const PAGES_PROTEGEES = new Set([SOIREES_DU_LIEU, '/preparer-soiree.html', '/console.html']);

export function adresseConnexion(retour) {
	return `/connexion.html?${new URLSearchParams({ retour })}`;
}

export function retourApresConnexion(recherche) {
	const retour = new URLSearchParams(recherche).get('retour');
	if (!retour?.startsWith('/') || retour.startsWith('//') || retour.includes('\\')) return SOIREES_DU_LIEU;
	const adresse = new URL(retour, 'https://croustyquizz.invalid');
	if (!PAGES_PROTEGEES.has(adresse.pathname) || adresse.origin !== 'https://croustyquizz.invalid') return SOIREES_DU_LIEU;
	return adresse.pathname + adresse.search + adresse.hash;
}

export function renvoyerVersConnexion() {
	location.replace(adresseConnexion(location.pathname + location.search + location.hash));
}

export async function consulterSession() {
	const session = await appeler('GET', ROUTE_SESSION);
	if (session.statut === 401) renvoyerVersConnexion();
	return session;
}

// Le trafic STOMP ne prolonge pas la session HTTP, qui expire après 30 minutes sans requête :
// la Console la consulte régulièrement tant qu'elle est ouverte, Pilotage Automatique compris.
export const INTERVALLE_MAINTIEN_SESSION = 5 * 60_000;

export function maintenirSession({ consulter = consulterSession, planifier = setInterval, annuler = clearInterval } = {}) {
	const minuterie = planifier(() => Promise.resolve().then(consulter).catch(() => {
		// Panne réseau : la consultation suivante réessaie, et la coupure STOMP se signale déjà.
	}), INTERVALLE_MAINTIEN_SESSION);
	return () => annuler(minuterie);
}
