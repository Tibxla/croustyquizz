// Accès de la Manette à ses Soirées : jeton de reconnexion opaque et UUID du Joueur, rangés par Soirée dans
// le stockage durable du navigateur. Le PRD (#2) veut un jeton « conservé par sa Manette », que seul le vidage
// du navigateur fait perdre : il survit à la mise en veille, à la fermeture de l'onglet et à un QR code rescanné.
// Si le stockage durable est refusé ou plein (navigation privée, réglages), l'accès se replie sur le stockage
// de l'onglet : il survit alors au rechargement, pas à la fermeture. Sans aucun stockage, la page d'arrivée
// le dit avant de créer le Joueur, qui ne pourrait pas retrouver sa place.

const PREFIXE = 'croustyquizz.manette.';
const DERNIERE = 'croustyquizz.manette-derniere';
const SONDE = 'croustyquizz.manette-sonde';
// Sans code, la page reprend la dernière Soirée rejointe le temps d'une soirée seulement : au-delà, un ancien
// pseudo ne revient pas tout seul. Un QR code rescanné reprend la Soirée de son code à tout moment.
const REPRISE_SANS_CODE_MS = 12 * 3_600_000;
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const opaque = /^[A-Za-z0-9_-]{43,128}$/;
const code = /^[A-Z0-9]{6}$/;
// Emplacements de l'ancienne page d'arrivée, dans le stockage de l'onglet : jeton et Soirée, sans l'UUID du Joueur.
const ANCIEN_JETON = 'croustyquizz.jeton';
const ANCIENNE_SOIREE = 'croustyquizz.soiree';

/** Stockages de la fenêtre, durable d'abord ; lire localStorage peut lever (SecurityError) si le site est bloqué. */
export function stockagesDuNavigateur(fenetre = globalThis) {
	const stockages = [];
	for (const nom of ['localStorage', 'sessionStorage']) {
		try {
			if (fenetre[nom]) stockages.push(fenetre[nom]);
		} catch { /* stockage refusé par le navigateur */ }
	}
	return stockages;
}

/**
 * stockages : un stockage ou une liste, par ordre de préférence. Une écriture va au premier qui l'accepte.
 * maintenant : l'heure du navigateur en millisecondes, remplaçable dans les tests.
 */
export function creerAccesManette(stockages = stockagesDuNavigateur(), { maintenant = () => Date.now() } = {}) {
	const liste = (Array.isArray(stockages) ? stockages : [stockages]).filter(Boolean);
	const lireDans = (stockage, cle) => { try { return stockage.getItem(cle); } catch { return null; } };
	const lireBrut = (cle) => liste.map((stockage) => lireDans(stockage, cle)).find((valeur) => valeur != null) ?? null;
	const effacer = (cle) => liste.forEach((stockage) => { try { stockage.removeItem(cle); } catch { /* stockage indisponible */ } });
	// Une écriture n'est acquise que relue : un quota atteint peut lever, ou ne rien garder.
	const ecrireDans = (stockage, cle, valeur) => {
		try {
			stockage.setItem(cle, valeur);
			return stockage.getItem(cle) === valeur;
		} catch {
			return false;
		}
	};
	const ecrire = (cle, valeur) => liste.some((stockage) => ecrireDans(stockage, cle, valeur));

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

	// La dernière Soirée est rangée avec son heure ; une valeur sans heure vient d'une version précédente.
	function derniereRangee() {
		try {
			const { soireeId, rangeeLe } = JSON.parse(lireBrut(DERNIERE)) ?? {};
			return { soireeId, rangeeLe: Number.isFinite(rangeeLe) ? rangeeLe : null };
		} catch {
			return { soireeId: lireBrut(DERNIERE), rangeeLe: null };
		}
	}

	function soirees() {
		const ids = new Set();
		for (const stockage of liste) {
			try {
				for (let i = 0; i < stockage.length; i++) {
					const cle = stockage.key(i);
					if (cle?.startsWith(PREFIXE)) ids.add(cle.slice(PREFIXE.length));
				}
			} catch { /* stockage indisponible */ }
		}
		return [...ids];
	}

	return {
		lire,
		/** Accès de la dernière Soirée rejointe depuis ce navigateur il y a moins de 12 heures, ou null. */
		derniere() {
			const { soireeId, rangeeLe } = derniereRangee();
			const age = rangeeLe === null ? Infinity : maintenant() - rangeeLe;
			return age >= 0 && age < REPRISE_SANS_CODE_MS ? lire(soireeId) : null;
		},
		/** Accès gardés pour ce code d'accès : un code peut resservir après la fin d'une Soirée. */
		parCode: (codeAcces) => soirees().map(lire).filter((acces) => acces && acces.codeAcces === codeAcces),
		/** Vrai si un stockage accepte d'écrire : à vérifier avant de créer un Joueur. */
		disponible() {
			const accepte = liste.some((stockage) => ecrireDans(stockage, SONDE, '1'));
			effacer(SONDE);
			return accepte;
		},
		/** Range l'accès et rend vrai s'il a pu être relu ; la dernière Soirée n'est qu'une commodité. */
		ranger(soireeId, { jeton, joueurId, codeAcces }) {
			if (!ecrire(PREFIXE + soireeId, JSON.stringify({ jeton, joueurId, codeAcces }))) return false;
			ecrire(DERNIERE, JSON.stringify({ soireeId, rangeeLe: maintenant() }));
			return true;
		},
		oublier(soireeId) {
			effacer(PREFIXE + soireeId);
			if (derniereRangee().soireeId === soireeId) effacer(DERNIERE);
		},
	};
}

/**
 * Cherche l'accès gardé à reprendre : pour un code scanné, ceux de ce code ; sinon celui de la dernière Soirée.
 * consulter(soireeId, jeton) rend { statut, corps } de retrouverEtatManette, statut 0 sur une panne réseau.
 * Un jeton refusé (401, 404) ou une Soirée terminée est oublié ; une coupure ou un serveur indisponible le garde,
 * car rejoindre à nouveau créerait un second Joueur. Rend { resultat: 'reprise', soireeId }, 'aucune' ou 'coupure'.
 * L'Exclusion (#11) devra se distinguer d'un jeton inconnu : un Joueur exclu ne revient pas depuis sa Manette.
 * ancienStockage (sessionStorage) porte l'accès d'une Manette ouverte avant le stockage durable : il est d'abord
 * repris, puis rangé ici avec l'UUID du Joueur et le code lus dans la capture.
 */
export async function reprendreAcces(accesManette, { codeAcces, consulter, ancienStockage }) {
	const ancien = await reprendreAncienAcces(accesManette, ancienStockage, consulter);
	if (ancien === 'coupure') return { resultat: 'coupure' };
	if (ancien && (!codeAcces || ancien.codeAcces === codeAcces)) return { resultat: 'reprise', soireeId: ancien.soireeId };
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

/**
 * Arrivée par le formulaire : un code tapé à la main reprend d'abord le Joueur gardé pour ce code, comme un QR
 * code rescanné, même après les 12 heures de la reprise sans code. rejoindre() n'est appelé (POST) que sans accès
 * valable ; une coupure ne l'appelle pas non plus, car le jeton gardé vaut peut-être encore.
 * Rend { resultat: 'reprise', soireeId }, { resultat: 'coupure' } ou { resultat: 'arrivee', arrivee }.
 */
export async function arriver(accesManette, { codeAcces, consulter, rejoindre }) {
	const reprise = await reprendreAcces(accesManette, { codeAcces, consulter });
	if (reprise.resultat !== 'aucune') return reprise;
	return { resultat: 'arrivee', arrivee: await rejoindre() };
}

// Rend l'accès repris ({ soireeId, codeAcces }), null s'il n'y en a pas ou s'il est refusé, ou 'coupure'.
// L'ancien emplacement n'est effacé qu'une fois l'accès relu dans le nouveau : sinon le jeton serait perdu.
async function reprendreAncienAcces(accesManette, ancienStockage, consulter) {
	const lireBrut = (cle) => { try { return ancienStockage?.getItem(cle) ?? null; } catch { return null; } };
	const effacer = () => { try { ancienStockage.removeItem(ANCIEN_JETON); ancienStockage.removeItem(ANCIENNE_SOIREE); } catch { /* stockage indisponible */ } };
	const jeton = lireBrut(ANCIEN_JETON);
	const soireeId = lireBrut(ANCIENNE_SOIREE);
	// Une identité de démonstration occupe le même emplacement : elle n'est ni consultée ni effacée.
	if (!opaque.test(jeton ?? '') || !uuid.test(soireeId ?? '')) return null;
	const { statut, corps } = await consulter(soireeId, jeton);
	const vue = corps?.vue;
	if (statut === 200 && vue?.soiree?.etat !== 'TERMINEE' && uuid.test(vue?.joueurId ?? '')) {
		const codeAcces = code.test(vue.soiree.codeAcces ?? '') ? vue.soiree.codeAcces : null;
		if (!accesManette.ranger(soireeId, { jeton, joueurId: vue.joueurId, codeAcces })) return null;
		effacer();
		return { soireeId, codeAcces };
	}
	if (statut === 200 || statut === 401 || statut === 404) {
		effacer();
		return null;
	}
	return 'coupure';
}

/**
 * Au démarrage de la Manette, vérifie que le serveur reconnaît encore le jeton : sinon la coquille tournerait en
 * reconnexion sans fin, refusée au CONNECT STOMP comme à la capture. envoyer est le transport authentifié.
 * Rend 'valide', 'refuse' (401, 404) ou 'indetermine' (panne réseau, serveur indisponible : le suivi s'en charge).
 */
export async function controlerJeton(envoyer, soireeId) {
	try {
		const { statut } = await envoyer('GET', `/api/soirees/${encodeURIComponent(soireeId)}/etat/manette`);
		if (statut === 200) return 'valide';
		return statut === 401 || statut === 404 ? 'refuse' : 'indetermine';
	} catch {
		return 'indetermine';
	}
}

/** Retour à la page d'arrivée après un accès refusé, avec le code de la Soirée s'il est connu. */
export function adresseRetourArrivee(codeAcces) {
	return `/rejoindre.html#${new URLSearchParams({ ...(codeAcces ? { code: codeAcces } : {}), acces: 'refuse' })}`;
}
