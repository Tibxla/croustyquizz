import { lireCorps, reponseIllisible } from './transport.mjs';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const roles = { manette: 'JOUEUR', console: 'ANIMATEUR', ecran: 'ECRAN' };
// Jeton de reconnexion ou clé d'Écran remis par le serveur (format du contrat) : opaque, il ne contient pas l'identité.
const opaque = /^[A-Za-z0-9_-]{43,128}$/;

// identifiant : l'UUID du Joueur, rangé à côté de son jeton opaque par la page d'arrivée (rejoindre.html).
export function lireAcces(ecran, recherche, jeton, identifiant) {
	const soireeId = new URLSearchParams(recherche).get('soiree');
	if (!uuid.test(soireeId ?? '')) throw new Error('Lien incomplet : une Soirée valide est requise (?soiree=UUID).');
	let acteur = { type: roles[ecran], soireeId };
	if (jeton?.startsWith('demo:')) {
		const [prefixe, type, portee, identifiant, surplus] = jeton.split(':');
		if (prefixe !== 'demo' || surplus !== undefined || type !== roles[ecran] || !uuid.test(identifiant)) {
			throw new Error('Cette identité ne correspond pas à cet écran. Choisis une identité de démonstration.');
		}
		if (portee !== soireeId) throw new Error('Cette identité appartient à une autre Soirée.');
		acteur = { type, identifiant, soireeId };
	} else if (opaque.test(jeton ?? '') && ecran !== 'console') {
		if (ecran === 'manette' && !uuid.test(identifiant ?? '')) {
			throw new Error('Ton accès est incomplet. Rejoins à nouveau la Soirée avec son code.');
		}
		acteur = ecran === 'manette' ? { type: roles[ecran], identifiant, soireeId } : acteur;
	} else if (ecran !== 'console') {
		throw new Error('Une identité est requise. Rejoins la Soirée avec son code, ou ouvre la page d’identité de démonstration en dev ou recette.');
	}
	return {
		acteur, soireeId,
		destination: ecran === 'manette' ? `/topic/joueurs/${acteur.identifiant}` :
			`/topic/soirees/${soireeId}/${ecran === 'ecran' ? 'salle' : 'console'}`,
		routeEtat: `/api/soirees/${soireeId}/etat/${ecran === 'ecran' ? 'salle' : ecran}`,
	};
}

export async function preparerIdentite(type, soireeId, acteurId, requete) {
	if (!Object.values(roles).includes(type) || !uuid.test(soireeId) || !uuid.test(acteurId)) {
		throw new Error('Choisis un rôle et un UUID de Soirée valide.');
	}
	// Le fournisseur commun est l'autorité pour l'UUID Java nameUUIDFromBytes du Joueur.
	const reponse = await requete(`/api/soirees/${soireeId}/etat/salle`, {
		headers: { Authorization: `Bearer demo:ECRAN:${soireeId}:${acteurId}` },
		cache: 'no-store', credentials: 'same-origin',
	});
	const { lisible, corps } = await lireCorps(reponse);
	if (!lisible) throw new Error(reponseIllisible);
	if (!reponse.ok) throw new Error(corps?.detail ?? `Identité refusée (HTTP ${reponse.status}).`);
	const identifiant = type === 'JOUEUR' ? corps.vue?.joueurs?.[0]?.id : acteurId;
	if (!uuid.test(identifiant ?? '')) throw new Error('Le serveur n’a pas fourni de Joueur de démonstration.');
	return { jeton: `demo:${type}:${soireeId}:${identifiant}`, page: { JOUEUR: 'manette', ANIMATEUR: 'console', ECRAN: 'ecran' }[type] };
}
