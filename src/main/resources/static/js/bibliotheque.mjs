// Bibliothèque de base (ADR 0011) : musique libre livrée avec l'application, décrite par son catalogue.
import { estFichierAudio, fichierParUrl, fichiersDeMusiqueDAttente, normaliserNomAudio } from './modes/karaoke/fichiers.mjs';

export const CATALOGUE_VIDE = Object.freeze({ version: 1, licences: {}, entrees: Object.freeze([]) });
const SECTIONS = [['attente', 'Musique d’attente'], ['instrumentale', 'Instrumentales du Freestyle'], ['chanson', 'Chansons du Karaoké']];

// Le catalogue est revalidé à chaque chargement ; sans lui, la bibliothèque est vide et le dossier du Lieu suffit.
export async function chargerBibliotheque(chercher = globalThis.fetch) {
	try {
		const reponse = await chercher('/bibliotheque/catalogue.json', { cache: 'no-cache' });
		if (!reponse.ok) return CATALOGUE_VIDE;
		const catalogue = await reponse.json();
		return Array.isArray(catalogue?.entrees) && catalogue.licences && typeof catalogue.licences === 'object' ? catalogue : CATALOGUE_VIDE;
	} catch {
		return CATALOGUE_VIDE;
	}
}

function credit(catalogue, morceau) {
	const licence = catalogue.licences[morceau.licence];
	return { titre: morceau.titre, artiste: morceau.artiste, licence: licence?.nom ?? morceau.licence, urlLicence: licence?.url ?? null };
}

// Chaque entrée devient un fichier lisible par son URL, comme un fichier du dossier l'est par son objet File.
export function morceauxDeLaBibliotheque(catalogue, type) {
	return catalogue.entrees.filter(morceau => morceau.type === type).map(morceau => ({
		...fichierParUrl(`/bibliotheque/${morceau.fichier}`, morceau.tailleOctets), credit: credit(catalogue, morceau),
	}));
}

const nomsPossibles = ({ name, credit: { titre, artiste } }) => new Set([normaliserNomAudio(name), normaliserNomAudio(titre),
	normaliserNomAudio(`${artiste} ${titre}`), normaliserNomAudio(`${titre} ${artiste}`)]);

// Les morceaux d'Attente du dossier s'ajoutent à ceux de la bibliothèque ; un morceau du dossier remplace son homonyme.
export function fusionnerMorceauxDAttente(bibliotheque, fichiersDuDossier) {
	const lieu = fichiersDeMusiqueDAttente(fichiersDuDossier.filter(estFichierAudio));
	const noms = new Set(lieu.map(fichier => normaliserNomAudio(fichier.name)));
	return [...bibliotheque.filter(morceau => ![...nomsPossibles(morceau)].some(nom => noms.has(nom))), ...lieu];
}

// Le crédit du pied de salle tient sur une ligne : seul le titre s'abrège, le guillemet fermant, l'auteur et la licence
// restent entiers. L'espace insécable avant le guillemet survit au début de sa boîte, où une espace ordinaire disparaît.
export const partiesDuCredit = ({ nature, titre, artiste, licence }) => [`${nature} : « ${titre}`, `\u00a0», ${artiste} · ${licence}`];

// Données de la page des crédits, section par section, dans l'ordre du catalogue.
export function decrireCredits(catalogue) {
	return SECTIONS.map(([type, titre]) => ({ type, titre, entrees: catalogue.entrees.filter(m => m.type === type).map(morceau => {
		const { licence, urlLicence } = credit(catalogue, morceau);
		return { titre: morceau.titre, artiste: morceau.artiste, licence, urlLicence, source: morceau.source,
			paroles: morceau.lrc ? `/bibliotheque/${morceau.lrc}` : null, parolesPartagees: /BY-SA/.test(morceau.licence) };
	}) })).filter(section => section.entrees.length);
}
