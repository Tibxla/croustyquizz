// Accès de la Manette à ses Soirées : jeton de reconnexion opaque et UUID du Joueur, rangés par Soirée dans
// le stockage durable du navigateur. Le PRD (#2) veut un jeton « conservé par sa Manette », que seul le vidage
// du navigateur fait perdre : il survit à la mise en veille, à la fermeture de l'onglet et à un QR code rescanné.
// Le stockage peut être refusé (navigation privée, réglages) : chaque accès se rabat alors sur « rien de gardé ».

const PREFIXE = 'croustyquizz.manette.';
const DERNIERE = 'croustyquizz.manette-derniere';
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const opaque = /^[A-Za-z0-9_-]{43,128}$/;
const code = /^[A-Z0-9]{6}$/;

export function creerAccesManette(stockage = globalThis.localStorage) {
	const lireBrut = (cle) => { try { return stockage?.getItem(cle) ?? null; } catch { return null; } };
	const ecrire = (cle, valeur) => { try { stockage?.setItem(cle, valeur); } catch { /* stockage indisponible */ } };
	const effacer = (cle) => { try { stockage?.removeItem(cle); } catch { /* stockage indisponible */ } };

	function lire(soireeId) {
		if (!uuid.test(soireeId ?? '')) return null;
		try {
			const { jeton, joueurId, codeAcces } = JSON.parse(lireBrut(PREFIXE + soireeId)) ?? {};
			if (!opaque.test(jeton ?? '') || !uuid.test(joueurId ?? '')) return null;
			return { soireeId, jeton, joueurId, codeAcces: code.test(codeAcces ?? '') ? codeAcces : null };
		} catch {
			return null;
		}
	}

	function soirees() {
		try {
			const ids = [];
			for (let i = 0; i < stockage.length; i++) {
				const cle = stockage.key(i);
				if (cle?.startsWith(PREFIXE)) ids.push(cle.slice(PREFIXE.length));
			}
			return ids;
		} catch {
			return [];
		}
	}

	return {
		lire,
		/** Accès de la dernière Soirée rejointe depuis ce navigateur, ou null. */
		derniere: () => lire(lireBrut(DERNIERE)),
		/** Accès gardés pour ce code d'accès : un code peut resservir après la fin d'une Soirée. */
		parCode: (codeAcces) => soirees().map(lire).filter((acces) => acces && acces.codeAcces === codeAcces),
		ranger(soireeId, { jeton, joueurId, codeAcces }) {
			ecrire(PREFIXE + soireeId, JSON.stringify({ jeton, joueurId, codeAcces }));
			ecrire(DERNIERE, soireeId);
		},
		oublier(soireeId) {
			effacer(PREFIXE + soireeId);
			if (lireBrut(DERNIERE) === soireeId) effacer(DERNIERE);
		},
	};
}

/**
 * Cherche l'accès gardé à reprendre : pour un code scanné, ceux de ce code ; sinon celui de la dernière Soirée.
 * consulter(soireeId, jeton) rend { statut, corps } de retrouverEtatManette, statut 0 sur une panne réseau.
 * Un jeton refusé (401, 404) ou une Soirée terminée est oublié ; une coupure ou un serveur indisponible le garde,
 * car rejoindre à nouveau créerait un second Joueur. Rend { resultat: 'reprise', soireeId }, 'aucune' ou 'coupure'.
 * L'Exclusion (#11) devra se distinguer d'un jeton inconnu : un Joueur exclu ne revient pas depuis sa Manette.
 */
export async function reprendreAcces(accesManette, { codeAcces, consulter }) {
	const candidats = codeAcces ? accesManette.parCode(codeAcces) : [accesManette.derniere()].filter(Boolean);
	for (const acces of candidats) {
		const { statut, corps } = await consulter(acces.soireeId, acces.jeton);
		if (statut === 200 && corps?.vue?.soiree?.etat !== 'TERMINEE') return { resultat: 'reprise', soireeId: acces.soireeId };
		if (statut === 200 || statut === 401 || statut === 404) {
			accesManette.oublier(acces.soireeId);
			continue;
		}
		return { resultat: 'coupure' };
	}
	return { resultat: 'aucune' };
}
