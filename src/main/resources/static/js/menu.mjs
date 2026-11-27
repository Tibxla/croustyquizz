// Menu replié de la coquille (#282, #280) : le « Plus » de la Console, le menu de la Manette.
// Un bouton (aria-expanded, aria-controls) ouvre un panneau voisin. À l'ouverture, le focus va au premier
// contrôle ; Échap ou le choix d'une commande referme et rend le focus au bouton ; un appui ailleurs referme
// sans déplacer le focus. Un contrôle marqué data-garder-menu laisse le menu ouvert.
const CONTROLES = 'button, a[href], input, select, textarea, summary';

export function monterMenu(bouton, panneau) {
	const document = bouton.ownerDocument;
	let ouvert = false;
	bouton.setAttribute('aria-expanded', 'false');
	if (panneau.id) bouton.setAttribute('aria-controls', panneau.id);
	panneau.hidden = true;

	function ouvrir() {
		ouvert = true;
		panneau.hidden = false;
		bouton.setAttribute('aria-expanded', 'true');
		const premier = Array.from(panneau.querySelectorAll(CONTROLES)).find(controle => !controle.hidden && !controle.disabled);
		premier?.focus();
	}
	function fermer(rendreFocus = false) {
		if (!ouvert) return;
		ouvert = false;
		panneau.hidden = true;
		bouton.setAttribute('aria-expanded', 'false');
		if (rendreFocus) bouton.focus();
	}
	const basculer = () => (ouvert ? fermer() : ouvrir());
	function touche(evenement) {
		if (evenement.key !== 'Escape' || !ouvert) return;
		evenement.preventDefault();
		fermer(true);
	}
	function choisir(evenement) {
		const cible = evenement.target;
		if (!cible || cible === panneau || !['BUTTON', 'A'].includes(cible.tagName)) return;
		if (cible.getAttribute('data-garder-menu') !== null) return;
		fermer(true);
	}
	function dehors(evenement) {
		if (!ouvert || bouton.contains(evenement.target) || panneau.contains(evenement.target)) return;
		fermer();
	}
	bouton.addEventListener('click', basculer);
	bouton.addEventListener('keydown', touche);
	panneau.addEventListener('keydown', touche);
	panneau.addEventListener('click', choisir);
	document.addEventListener('click', dehors);
	return {
		get ouvert() { return ouvert; },
		ouvrir, fermer,
		demonter() {
			bouton.removeEventListener('click', basculer);
			bouton.removeEventListener('keydown', touche);
			panneau.removeEventListener('keydown', touche);
			panneau.removeEventListener('click', choisir);
			document.removeEventListener('click', dehors);
		},
	};
}
