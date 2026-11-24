import { lireCorps, reponseIllisible } from './transport.mjs';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const roles = { manette: 'JOUEUR', console: 'ANIMATEUR', ecran: 'ECRAN' };

export function lireAcces(ecran, recherche, jeton, joueurId) {
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
	} else if (ecran === 'manette' && jeton && uuid.test(joueurId ?? '')) {
		acteur.identifiant = joueurId;
	} else if (ecran !== 'console' && !(ecran === 'ecran' && jeton)) {
		throw new Error('Une identité est requise. Rejoins la Soirée ou ouvre l’Écran depuis la préparation.');
	}
	return {
		acteur, soireeId,
		destination: ecran === 'manette' ? `/topic/joueurs/${acteur.identifiant}` :
			`/topic/soirees/${soireeId}/${ecran === 'ecran' ? 'salle' : 'console'}`,
		routeEtat: `/api/soirees/${soireeId}/etat/${ecran === 'ecran' ? 'salle' : ecran}`,
	};
}

export async function preparerIdentite(type, soireeId, acteurId, requete, rangJoueur = 1) {
	if (!Object.values(roles).includes(type) || !uuid.test(soireeId) || !uuid.test(acteurId)) {
		throw new Error('Choisis un rôle et un UUID de Soirée valide.');
	}
	const joueurAbsent = 'Ce Joueur de démonstration n’existe pas dans cette Soirée.';
	if (type === 'JOUEUR' && (!Number.isInteger(rangJoueur) || rangJoueur < 1 || rangJoueur > 4)) {
		throw new Error(joueurAbsent);
	}
	// Le fournisseur commun donne l’UUID du Joueur au rang choisi.
	const roleDeLecture = type === 'ANIMATEUR' ? type : 'ECRAN';
	const reponse = await requete(`/api/soirees/${soireeId}/etat/salle`, {
		headers: { Authorization: `Bearer demo:${roleDeLecture}:${soireeId}:${acteurId}` },
		cache: 'no-store', credentials: 'same-origin',
	});
	const { lisible, corps } = await lireCorps(reponse);
	if (!lisible) throw new Error(reponseIllisible);
	if (!reponse.ok) throw new Error(corps?.detail ?? `Identité refusée (HTTP ${reponse.status}).`);
	let identifiant = acteurId;
	if (type === 'JOUEUR') {
		const joueur = corps?.vue?.joueurs?.[rangJoueur - 1];
		if (joueur === undefined) throw new Error(joueurAbsent);
		identifiant = joueur?.id;
		if (typeof identifiant !== 'string' || !uuid.test(identifiant)) {
			throw new Error('Le serveur n’a pas fourni de Joueur de démonstration.');
		}
	}
	return { jeton: `demo:${type}:${soireeId}:${identifiant}`, page: { JOUEUR: 'manette', ANIMATEUR: 'console', ECRAN: 'ecran' }[type] };
}

export async function choisirPilotage(soireeId, jeton, pilotage, requete) {
	const reponse = await requete(`/api/test/soirees/${soireeId}/pilotage`, {
		method: 'PUT',
		headers: { Authorization: `Bearer ${jeton}`, 'Content-Type': 'application/json' },
		cache: 'no-store', credentials: 'same-origin',
		body: JSON.stringify({ pilotage }),
	});
	if (!reponse.ok) {
		const { corps } = await lireCorps(reponse);
		throw new Error(corps?.detail ?? `Pilotage refusé (HTTP ${reponse.status}).`);
	}
}
