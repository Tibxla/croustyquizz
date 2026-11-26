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
	const local = trouverFichierDeChanson(fichiers ?? [], chanson);
	const libre = chanson.bibliotheque;
	if (local.etat === 'unique' || !libre) return local;
	return { etat: 'unique', bibliotheque: true,
		fichier: { name: libre.fichier.split('/').at(-1), size: libre.tailleOctets, url: libre.fichier } };
}
