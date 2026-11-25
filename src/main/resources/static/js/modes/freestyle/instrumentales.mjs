import { estFichierAudio } from '../karaoke/fichiers.mjs';

const normaliser = texte => texte.normalize('NFKD').replace(/\p{M}+/gu, '').toLowerCase();
const dansInstrumentales = fichier => {
	const chemin = fichier.webkitRelativePath?.split('/') ?? [];
	return chemin.length === 3 && normaliser(chemin[1]) === 'instrumentales';
};

function ficheDuFichier(fichier, index) {
	const nom = fichier.name.replace(/\.[^.]+$/, '');
	const valeurs = /^(.*?)\s*\[(\d+)\s*bpm\]$/i.exec(nom);
	const entree = Object.hasOwn(index, fichier.name) ? index[fichier.name] : undefined;
	if (entree !== undefined && (!entree || typeof entree !== 'object' || Array.isArray(entree))) return null;
	const titre = entree?.titre ?? valeurs?.[1].trim();
	const tempoBpm = entree?.tempoBpm ?? Number(valeurs?.[2]);
	const departBeatMs = entree?.departBeatMs ?? 0;
	if (typeof titre !== 'string' || !titre.trim() || [...titre].length > 120 ||
		!Number.isInteger(tempoBpm) || tempoBpm < 1 || tempoBpm > 1000 ||
		!Number.isSafeInteger(departBeatMs) || departBeatMs < 0) return null;
	return { titre, tempoBpm, departBeatMs };
}

// Le dossier choisi, la casse d'« Instrumentales », la date et la durée mesurée ne changent pas l'identifiant.
async function identifiantOpaque(fichier, fiche) {
	const cheminRelatif = fichier.webkitRelativePath.split('/').slice(2).join('/');
	const octets = new TextEncoder().encode(JSON.stringify([cheminRelatif, fichier.size, fiche]));
	let hex;
	if (globalThis.crypto?.subtle) {
		const empreinte = new Uint8Array(await globalThis.crypto.subtle.digest('SHA-256', octets), 0, 16);
		hex = Array.from(empreinte, octet => octet.toString(16).padStart(2, '0')).join('');
	} else {
		// FNV-1a 128 bits pour conserver les identifiants sur une recette HTTP sans Web Crypto.
		let empreinte = 0x6c62272e07bb014262b821756295c58dn;
		for (const octet of octets) empreinte = BigInt.asUintN(128, (empreinte ^ BigInt(octet)) * 0x1000000000000000000013bn);
		hex = empreinte.toString(16).padStart(32, '0');
	}
	const variante = ((parseInt(hex[16], 16) & 3) | 8).toString(16);
	return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-8${hex.slice(13, 16)}-${variante}${hex.slice(17, 20)}-${hex.slice(20)}`;
}

/** `mesurer(fichier)` rend la durée du fichier en millisecondes, ou null : une fiche sans durée est écartée. */
export async function lireInstrumentales(selection, { mesurer }) {
	const fichiers = selection.filter(dansInstrumentales);
	const description = fichiers.find(fichier => normaliser(fichier.name) === 'instrumentales.json');
	let index = {};
	if (description) {
		index = JSON.parse(await description.text());
		if (!index || typeof index !== 'object' || Array.isArray(index)) {
			throw new TypeError('Le fichier instrumentales.json doit contenir un objet.');
		}
	}
	const resultats = [];
	for (const fichier of fichiers.filter(estFichierAudio)) {
		const fiche = ficheDuFichier(fichier, index);
		if (!fiche) continue;
		const dureeMs = await mesurer(fichier);
		if (!Number.isSafeInteger(dureeMs) || dureeMs < 1) continue;
		resultats.push({ fiche: { instrumentaleId: await identifiantOpaque(fichier, fiche), ...fiche, dureeMs }, fichier });
	}
	return resultats.sort((a, b) =>
		a.fiche.titre.localeCompare(b.fiche.titre, 'fr') || a.fiche.instrumentaleId.localeCompare(b.fiche.instrumentaleId));
}
