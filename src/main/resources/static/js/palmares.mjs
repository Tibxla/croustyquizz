export function creerLignePalmaresSalle(document, { numero, rang, titre, interpretes, note, votes, texte }) {
	const li = document.createElement('li');
	li.setAttribute('data-teinte', String(numero % 4));
	const presentation = document.createElement('div');
	presentation.className = 'palmares__ligne';
	presentation.setAttribute('aria-hidden', 'true');
	for (const [champ, valeur] of [
		['rang', rang === null ? null : `${rang}.`], ['numero', `n° ${numero}`],
		['interpretes', interpretes], ['chanson', titre], ['note', note], ['votes', votes],
	]) {
		if (valeur === null) continue;
		const span = document.createElement('span');
		span.className = `palmares__${champ}`;
		span.textContent = valeur;
		presentation.append(span);
	}
	const phrase = document.createElement('span');
	phrase.className = 'visuellement-cache';
	phrase.textContent = texte;
	li.append(presentation, phrase);
	return li;
}
