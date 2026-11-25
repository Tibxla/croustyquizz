import { appeler } from './api.js';

export const ROUTE_SESSION = '/api/animateurs/session';
export const SOIREES_DU_LIEU = '/soirees.html';
const PAGES_PROTEGEES = new Set([SOIREES_DU_LIEU, '/preparer-soiree.html', '/console.html']);

export function adresseConnexion(retour, { compteCree = false } = {}) {
	const parametres = new URLSearchParams({ retour });
	if (compteCree) parametres.set('inscription', '1');
	return `/connexion.html?${parametres}`;
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
