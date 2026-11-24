const extensionAudio = /\.(mp3|m4a|ogg|wav)$/i;

export const estFichierAudio = fichier => extensionAudio.test(fichier.name);

export function normaliserNomAudio(texte) {
	return texte.replace(extensionAudio, '').normalize('NFKD').replace(/\p{M}+/gu, '')
		.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}

export function trouverFichierDeChanson(fichiers, { artiste, titre }) {
	const noms = [normaliserNomAudio(`${artiste} ${titre}`), normaliserNomAudio(`${titre} ${artiste}`)];
	let fichier;
	for (const candidat of fichiers) {
		if (!estFichierAudio(candidat) || !noms.includes(normaliserNomAudio(candidat.name))) continue;
		if (fichier) return { etat: 'ambigu' };
		fichier = candidat;
	}
	return fichier ? { etat: 'unique', fichier } : { etat: 'absent' };
}
