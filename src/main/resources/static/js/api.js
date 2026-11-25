// Appels HTTP au contrat docs/api : JSON en entrée, JSON ou Probleme en sortie.
// Un secret (jeton de Joueur, clé d'Écran) part dans Authorization, jamais dans l'URL.
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

// UUID v4 pour Idempotency-Key ; crypto.randomUUID n'existe qu'en contexte sécurisé (HTTPS ou localhost).
export function nouvelleCle() {
	if (crypto.randomUUID) return crypto.randomUUID();
	const octets = crypto.getRandomValues(new Uint8Array(16));
	octets[6] = (octets[6] & 0x0f) | 0x40;
	octets[8] = (octets[8] & 0x3f) | 0x80;
	const hexa = [...octets].map((octet) => octet.toString(16).padStart(2, '0')).join('');
	return `${hexa.slice(0, 8)}-${hexa.slice(8, 12)}-${hexa.slice(12, 16)}-${hexa.slice(16, 20)}-${hexa.slice(20)}`;
}

// Le stockage peut être refusé (navigation privée, réglages) : la page doit continuer sans.
export const memoire = {
	lire(cle) { try { return sessionStorage.getItem(cle); } catch { return null; } },
	ecrire(cle, valeur) { try { sessionStorage.setItem(cle, valeur); } catch { /* stockage indisponible */ } },
	oublier(cle) { try { sessionStorage.removeItem(cle); } catch { /* stockage indisponible */ } },
};
