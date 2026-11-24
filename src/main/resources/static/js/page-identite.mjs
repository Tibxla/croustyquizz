import { preparerIdentite } from './identite.mjs';

const formulaire = document.querySelector('form');
const soiree = formulaire.elements.soiree;
const role = formulaire.elements.role;
const champJoueur = document.getElementById('champ-joueur');
const erreur = document.getElementById('erreur-identite');
const envoi = document.querySelector('[data-envoi]');
const bouton = formulaire.querySelector('button');
soiree.value = new URLSearchParams(location.search).get('soiree') ?? '';
function afficherChoixDuJoueur() {
	champJoueur.hidden = role.value !== 'JOUEUR';
}
role.addEventListener('change', afficherChoixDuJoueur);
afficherChoixDuJoueur();

formulaire.addEventListener('submit', async evenement => {
	evenement.preventDefault();
	erreur.hidden = true;
	soiree.removeAttribute('aria-invalid');
	bouton.disabled = true;
	envoi.hidden = false;
	envoi.className = 'envoi envoi--en-cours';
	envoi.textContent = 'Vérification de la Soirée…';
	try {
		// Également utilisable sur HTTP local, où randomUUID n'est pas toujours disponible.
		const octets = crypto.getRandomValues(new Uint8Array(16));
		octets[6] = (octets[6] & 15) | 64; octets[8] = (octets[8] & 63) | 128;
		const hex = Array.from(octets, octet => octet.toString(16).padStart(2, '0')).join('');
		const acteurId = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
		const soireeId = soiree.value.trim().toLowerCase();
		const acces = await preparerIdentite(role.value, soireeId, acteurId, fetch, Number(formulaire.elements.joueur.value));
		sessionStorage.setItem('croustyquizz.jeton', acces.jeton);
		const parametres = new URLSearchParams({ soiree: soireeId });
		if (formulaire.elements.factice.checked) parametres.set('factice', '1');
		location.assign(`/${acces.page}.html?${parametres}`);
	} catch (probleme) {
		envoi.hidden = true;
		erreur.textContent = probleme.message;
		erreur.hidden = false;
		soiree.setAttribute('aria-invalid', 'true');
		soiree.focus();
	} finally { bouton.disabled = false; }
});
