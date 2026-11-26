const extensionAudio = /\.(mp3|m4a|ogg|wav)$/i;

export const estFichierAudio = fichier => extensionAudio.test(fichier.name);
const sansAccents = texte => texte.normalize('NFKD').replace(/\p{M}+/gu, '').toLowerCase();

function estDansAttente(fichier) {
	const chemin = fichier.webkitRelativePath?.split('/') ?? [];
	return chemin.length > 2 && sansAccents(chemin[1]) === 'attente';
}

export function fichiersDeMusiqueDAttente(fichiers) {
	return fichiers.filter(fichier => estFichierAudio(fichier) && estDansAttente(fichier));
}

export function normaliserNomAudio(texte) {
	return sansAccents(texte.replace(extensionAudio, '')).replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}

export function trouverFichierDeChanson(fichiers, { artiste, titre }) {
	const noms = [normaliserNomAudio(`${artiste} ${titre}`), normaliserNomAudio(`${titre} ${artiste}`)];
	let fichier;
	for (const candidat of fichiers) {
		if (!estFichierAudio(candidat) || estDansAttente(candidat) || !noms.includes(normaliserNomAudio(candidat.name))) continue;
		if (fichier) return { etat: 'ambigu' };
		fichier = candidat;
	}
	return fichier ? { etat: 'unique', fichier } : { etat: 'absent' };
}

// Ordre des sources (ADR 0011) : le fichier unique du Lieu, sinon celui de la Bibliothèque de base, lu par son URL.
export function choisirFichierDeChanson(fichiers, chanson) {
	const libre = chanson.bibliotheque;
	if (chanson.origine === 'BIBLIOTHEQUE' && !libre) return { etat: 'absent' };
	const local = trouverFichierDeChanson(fichiers ?? [], chanson);
	if (local.etat === 'unique' || !libre) return local;
	return { etat: 'unique', bibliotheque: true, fichier: fichierParUrl(libre.fichier, libre.tailleOctets) };
}

// Un fichier de la Bibliothèque de base, décrit comme un File du dossier : nom sans chemin, taille, et l'URL qui le lit.
export const fichierParUrl = (url, tailleOctets) => ({ name: url.split('/').at(-1), size: tailleOctets, url });
