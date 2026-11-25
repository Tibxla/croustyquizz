// Ligne du Palmarès sur l'Écran de salle : la présentation est masquée aux lecteurs d'écran, qui lisent la phrase complète.
function creerElement(document, champ, valeur) {
	const span = document.createElement('span');
	span.className = `palmares__${champ}`;
	span.textContent = valeur;
	return span;
}

export function creerLignePalmaresSalle(document, { numero, rang, titre, interpretes, moyenne, votes, texte }) {
	const li = document.createElement('li');
	li.setAttribute('data-teinte', String(numero % 4));
	const presentation = document.createElement('div');
	presentation.className = 'palmares__ligne';
	presentation.setAttribute('aria-hidden', 'true');
	if (rang !== null) presentation.append(creerElement(document, 'rang', `${rang}.`));
	presentation.append(creerElement(document, 'numero', `n° ${numero}`), creerElement(document, 'interpretes', interpretes),
		creerElement(document, 'chanson', titre));
	if (moyenne !== null) {
		// Le chiffre en grand, « sur 5 » en petit : la note tient dans sa colonne.
		const note = document.createElement('span');
		note.className = 'palmares__note';
		note.append(creerElement(document, 'moyenne', moyenne), creerElement(document, 'sur', 'sur 5'));
		presentation.append(note);
	}
	presentation.append(creerElement(document, 'votes', votes));
	const phrase = document.createElement('span');
	phrase.className = 'visuellement-cache';
	phrase.textContent = texte;
	li.append(presentation, phrase);
	return li;
}

// Sur la salle, plein écran sans défilement : les trois premiers Passages classés, puis le nombre des autres (#236).
// La Console garde la liste complète.
const LIGNES_DE_SALLE = 3;
const texteAutresPassages = nombre => `et ${nombre} ${nombre > 1 ? 'autres Passages' : 'autre Passage'}`;

export function resumerPalmaresDeSalle(palmares) {
	const lignes = palmares.filter(ligne => ligne.rang !== null).slice(0, LIGNES_DE_SALLE);
	const autres = palmares.length - lignes.length;
	return { lignes, suite: lignes.length > 0 && autres > 0 ? texteAutresPassages(autres) : '' };
}

export function afficherPalmaresDeSalle(document, { liste, suite, vide }, palmares) {
	const { lignes, suite: texteSuite } = resumerPalmaresDeSalle(palmares ?? []);
	liste.replaceChildren(...lignes.map(ligne => creerLignePalmaresSalle(document, ligne)));
	suite.textContent = texteSuite;
	suite.hidden = texteSuite === '';
	vide.hidden = palmares === null || lignes.length > 0;
}
