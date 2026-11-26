import { choisirPilotage, preparerIdentite } from './identite.mjs';
import { creerUuid } from './uuid.mjs';

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
		const acteurId = creerUuid();
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
