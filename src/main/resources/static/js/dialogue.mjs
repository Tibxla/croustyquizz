// Le dialogue natif porte la modalité et Escape ; ce helper rend le focus au déclencheur.
// oublierDeclencheur() ferme sans ce retour : quand la page amène elle-même le focus ailleurs (#277).
export function ouvrirDialogue(dialogue, declencheur = dialogue.ownerDocument.activeElement) {
	if (dialogue.open) return undefined;
	dialogue.addEventListener('close', () => {
		if (declencheur?.isConnected) declencheur.focus();
	}, { once: true });
	dialogue.showModal();
	(dialogue.querySelector('[autofocus], button, input, select, textarea, a[href]') ?? dialogue).focus();
	return { oublierDeclencheur() { declencheur = null; } };
}
