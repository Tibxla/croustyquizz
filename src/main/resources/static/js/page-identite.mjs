import { choisirPilotage, preparerIdentite } from './identite.mjs';

const formulaire = document.querySelector('form');
const soiree = formulaire.elements.soiree;
const role = formulaire.elements.role;
const pilotage = formulaire.elements.pilotage;
const champJoueur = document.getElementById('champ-joueur');
const champPilotage = document.getElementById('champ-pilotage');
const erreur = document.getElementById('erreur-identite');
const envoi = document.querySelector('[data-envoi]');
const bouton = formulaire.querySelector('button');
soiree.value = new URLSearchParams(location.search).get('soiree') ?? '';
function afficherChampsDuRole() {
	champJoueur.hidden = role.value !== 'JOUEUR';
	champPilotage.hidden = role.value !== 'ANIMATEUR';
}
role.addEventListener('change', afficherChampsDuRole);
afficherChampsDuRole();

formulaire.addEventListener('submit', async evenement => {
	evenement.preventDefault();
	erreur.hidden = true;
	soiree.removeAttribute('aria-invalid');
	pilotage.removeAttribute('aria-invalid');
	bouton.disabled = true;
	envoi.hidden = false;
	envoi.className = 'envoi envoi--en-cours';
	envoi.textContent = 'Vérification de la Soirée…';
	let champEnErreur = soiree;
	try {
		// Également utilisable sur HTTP local, où randomUUID n'est pas toujours disponible.
		const octets = crypto.getRandomValues(new Uint8Array(16));
		octets[6] = (octets[6] & 15) | 64; octets[8] = (octets[8] & 63) | 128;
		const hex = Array.from(octets, octet => octet.toString(16).padStart(2, '0')).join('');
		const acteurId = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
		const soireeId = soiree.value.trim().toLowerCase();
		const type = role.value;
		const pilotageChoisi = type === 'ANIMATEUR' ? pilotage.value : '';
		const acces = await preparerIdentite(type, soireeId, acteurId, fetch, Number(formulaire.elements.joueur.value));
		if (pilotageChoisi) {
			champEnErreur = pilotage;
			envoi.textContent = 'Enregistrement du Pilotage…';
			await choisirPilotage(soireeId, acces.jeton, pilotageChoisi, fetch);
		}
		sessionStorage.setItem('croustyquizz.jeton', acces.jeton);
		const parametres = new URLSearchParams({ soiree: soireeId });
		if (formulaire.elements.factice.checked) parametres.set('factice', '1');
		location.assign(`/${acces.page}.html?${parametres}`);
	} catch (probleme) {
		envoi.hidden = true;
		erreur.textContent = probleme.message;
		erreur.hidden = false;
		champEnErreur.setAttribute('aria-invalid', 'true');
		champEnErreur.focus();
	} finally { bouton.disabled = false; }
});
