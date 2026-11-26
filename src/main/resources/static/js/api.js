// Appels HTTP au contrat docs/api : JSON en entrée, JSON ou Probleme en sortie.
// Un secret (jeton de Joueur, clé d'Écran) part dans Authorization, jamais dans l'URL.
import { creerUuid } from './uuid.mjs';

export async function appeler(methode, route, { corps, secret, entetes = {} } = {}) {
	const options = { method: methode, headers: { Accept: 'application/json', ...entetes } };
	if (secret) options.headers.Authorization = `Bearer ${secret}`;
	if (corps !== undefined) {
		options.headers['Content-Type'] = 'application/json';
		options.body = JSON.stringify(corps);
	}
	let reponse;
	try {
		reponse = await fetch(route, options);
	} catch {
		return { ok: false, statut: 0, code: 'RESEAU_INDISPONIBLE' };
	}
	const type = reponse.headers.get('Content-Type') ?? '';
	const contenu = type.includes('json') ? await reponse.json().catch(() => null) : null;
	if (reponse.ok) return { ok: true, statut: reponse.status, corps: contenu };
	// Le detail d'un Probleme dit ce qui bloque, par exemple combien de temps attendre après un 429.
	const detail = typeof contenu?.detail === 'string' ? contenu.detail : undefined;
	return { ok: false, statut: reponse.status, code: contenu?.code ?? 'ERREUR_INATTENDUE', ...(detail ? { detail } : {}) };
}

// UUID v4 pour Idempotency-Key, y compris hors contexte sécurisé (voir uuid.mjs).
export function nouvelleCle() {
	return creerUuid();
}

// Le stockage peut être refusé (navigation privée, réglages) : la page doit continuer sans.
export const memoire = {
	lire(cle) { try { return sessionStorage.getItem(cle); } catch { return null; } },
	ecrire(cle, valeur) { try { sessionStorage.setItem(cle, valeur); } catch { /* stockage indisponible */ } },
	oublier(cle) { try { sessionStorage.removeItem(cle); } catch { /* stockage indisponible */ } },
};
