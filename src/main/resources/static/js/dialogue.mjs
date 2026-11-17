// Le dialogue natif porte la modalité et Escape ; ce helper rend le focus au déclencheur.
export function ouvrirDialogue(dialogue, declencheur = dialogue.ownerDocument.activeElement) {
	if (dialogue.open) return;
	dialogue.addEventListener('close', () => {
		if (declencheur?.isConnected) declencheur.focus();
	}, { once: true });
	dialogue.showModal();
	(dialogue.querySelector('[autofocus], button, input, select, textarea, a[href]') ?? dialogue).focus();
}
