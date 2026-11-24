// Frontières réseau injectables ; aucun accès au DOM ni stockage d'identité ici.

export const reponseIllisible = 'Le serveur ne répond pas correctement.';

// Lit le corps sans jamais lever de SyntaxError : seul un JSON annoncé comme tel (application/json ou
// application/problem+json) est analysé. Une page HTML de proxy (502…) rend { lisible: false }.
export async function lireCorps(reponse) {
	const texte = await reponse.text();
	if (!texte) return { lisible: true, corps: null };
	const type = reponse.headers?.get('Content-Type') ?? '';
	if (!/^application\/(?:problem\+)?json\s*(?:;|$)/i.test(type)) return { lisible: false };
	try { return { lisible: true, corps: JSON.parse(texte) }; } catch { return { lisible: false }; }
}

export function creerTransport({ jeton, csrf } = {}, adaptations = {}) {
	const adresse = adaptations.adresse ?? globalThis.location?.href;
	const requete = adaptations.requete ?? ((...args) => fetch(...args));
	const entetesIdentite = () => ({
		...(jeton ? { Authorization: `Bearer ${jeton}` } : {}),
		...(csrf ? { 'X-CSRF-TOKEN': csrf } : {}),
	});
	function requerir(methode, chemin, corps, { entetes = {}, signal } = {}) {
		const url = new URL(chemin, adresse);
		if (url.origin !== new URL(adresse).origin) throw new Error('La requête doit rester sur CroustyQuizz.');
		return requete(url, {
			method: methode, credentials: 'same-origin', cache: 'no-store', signal,
			headers: { Accept: 'application/json', ...entetes,
				...(corps === undefined ? {} : { 'Content-Type': 'application/json' }), ...entetesIdentite() },
			...(corps === undefined ? {} : { body: JSON.stringify(corps) }),
		});
	}
	async function envoyer(methode, chemin, corps, options) {
		const reponse = await requerir(methode, chemin, corps, options);
		const { lisible, corps: lu } = await lireCorps(reponse);
		if (lisible) return { statut: reponse.status, corps: lu };
		// Un succès illisible n'est pas une confirmation : l'appelant le traite comme une panne réseau.
		if (reponse.status >= 200 && reponse.status < 300) throw new Error(reponseIllisible);
		return { statut: reponse.status, corps: { detail: reponseIllisible } };
	}
	return {
		envoyer,
		async chargerQrCode(soireeId, signal) {
			if (!jeton || jeton.startsWith('demo:')) return null;
			const reponse = await requerir('GET', `/api/soirees/${encodeURIComponent(soireeId)}/qrcode`, undefined, {
				entetes: { Accept: 'image/svg+xml, application/problem+json' }, signal,
			});
			const adresseManette = reponse.headers.get('Adresse-Manette');
			if (!reponse.ok || !adresseManette || !/^image\/svg\+xml\s*(?:;|$)/i.test(reponse.headers.get('Content-Type') ?? '')) {
				return null;
			}
			return { blob: await reponse.blob(), adresseManette };
		},
		ouvrir({ surConnexion, surMessage, surDeconnexion, surErreur }) {
			const url = new URL('/ws', adresse);
			url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
			const Client = adaptations.Client ?? globalThis.StompJs.Client;
			const client = new Client({
				brokerURL: url.href, connectHeaders: entetesIdentite(), reconnectDelay: 0,
				connectionTimeout: 10000, heartbeatIncoming: 10000, heartbeatOutgoing: 10000,
				onConnect: surConnexion, onWebSocketClose: surDeconnexion,
				onStompError: surErreur, onWebSocketError: surErreur,
			});
			client.activate();
			return {
				abonner(destination, id) {
					client.subscribe(destination, trame => {
						try { surMessage(JSON.parse(trame.body)); } catch { surErreur(); }
					}, { id });
				},
				fermer() { void client.deactivate(); },
			};
		},
		capturer(route, abonnementId, signal) {
			const url = new URL(route, adresse);
			url.searchParams.set('abonnementId', abonnementId);
			return envoyer('GET', url, undefined, { signal });
		},
	};
}

// Compatibilité avec les pages du prototype, qui n'ont pas d'identité.
export const transportStomp = {
	ouvrir: rappels => creerTransport().ouvrir(rappels),
	capturer: (...args) => creerTransport().capturer(...args),
};
