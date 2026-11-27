// Préparation d'une Soirée (#5, #9, #282) : création, ouverture aux Joueurs, puis les deux chemins vers l'Écran de
// salle et la Console d'animation. Une seule action citron selon l'état ; aucune règle de jeu.
import { appeler, appelerAvecCsrf, nouvelleCle } from './api.js';
import { consulterSession, renvoyerVersConnexion } from './compte.mjs';
import { monterMenuCompte } from './menu-compte.mjs';

const FORMATS = { SOLO: 'Solo', EQUIPE: 'Équipe' };
const PILOTAGES = { MANUEL: 'Pilotage manuel', AUTOMATIQUE: 'Pilotage automatique' };
const ETATS = { PREPARATION: 'En préparation', OUVERTE: 'Ouverte', EN_COURS: 'En cours', TERMINEE: 'Terminée' };
const MESSAGES = {
	DONNEES_INVALIDES: 'Donne un titre de 1 à 150 caractères.',
	ETAT_INCOMPATIBLE: 'La Soirée est déjà lancée ou terminée.',
	RESSOURCE_INTROUVABLE: 'Cette Soirée est introuvable.',
	CSRF_INVALIDE: 'La session a changé. Réessaie.',
	RESEAU_INDISPONIBLE: 'Pas de connexion au serveur. Réessaie dans un instant.',
};
const JOIGNABLE = new Set(['OUVERTE', 'EN_COURS']);

/** Ce que la préparation montre d'une Soirée : état, code, actions visibles et la seule action citron. */
export function decrirePreparation({ format, pilotage, etat, codeAcces }) {
	const terminee = etat === 'TERMINEE';
	const code = JOIGNABLE.has(etat) ? codeAcces ?? null : null;
	let consigneCode = 'Ouvre la Soirée aux Joueurs pour obtenir son code d’accès.';
	if (code) consigneCode = 'Les Joueurs le saisissent sur leur téléphone, ou scannent le QR code de l’Écran de salle.';
	if (terminee) consigneCode = 'La Soirée est terminée : son code ne permet plus de la rejoindre.';
	let action = null;
	if (etat === 'PREPARATION') action = 'ouvrir';
	else if (JOIGNABLE.has(etat)) action = 'console';
	return {
		etat: ETATS[etat] ?? etat,
		resume: `${FORMATS[format] ?? format} · ${PILOTAGES[pilotage] ?? pilotage}`,
		code,
		consigneCode,
		ouvrir: etat === 'PREPARATION',
		ecran: !terminee,
		libelleConsole: terminee ? 'Revoir dans la Console' : 'Ouvrir la Console d’animation',
		action,
	};
}

export async function monterPreparation(document) {
	const element = id => document.getElementById(id);
	const erreur = element('erreur');
	let soiree = null;

	function signaler(resultat) {
		if (resultat.statut === 401) {
			renvoyerVersConnexion();
			return;
		}
		erreur.textContent = MESSAGES[resultat.code] ?? 'L’action n’a pas abouti. Réessaie dans un instant.';
		erreur.hidden = false;
	}

	function afficher(nouvelle) {
		soiree = nouvelle;
		const vue = decrirePreparation(soiree);
		erreur.hidden = true;
		element('creation').hidden = true;
		element('soiree').hidden = false;
		document.title = `${soiree.titre} · CroustyQuizz`;
		element('titre-page').textContent = soiree.titre;
		element('etat').textContent = vue.etat;
		element('resume').textContent = vue.resume;
		element('soiree-statut').hidden = false;
		element('code').textContent = vue.code ?? '';
		element('code').hidden = !vue.code;
		element('consigne-code').textContent = vue.consigneCode;
		element('ouvrir').hidden = !vue.ouvrir;
		element('etape-ecran').hidden = !vue.ecran;
		const lien = element('console');
		lien.href = `/console.html?${new URLSearchParams({ soiree: soiree.id })}`;
		lien.textContent = vue.libelleConsole;
		for (const [id, nom] of [['ouvrir', 'ouvrir'], ['console', 'console']]) {
			element(id).classList.toggle('bouton--action', vue.action === nom);
		}
		const adresse = new URL(location.href);
		adresse.searchParams.set('soiree', soiree.id);
		history.replaceState(null, '', adresse);
	}

	element('creation').addEventListener('submit', async evenement => {
		evenement.preventDefault();
		const donnees = new FormData(evenement.target);
		const creation = await appelerAvecCsrf('POST', '/api/soirees', { corps: {
			titre: element('titre').value,
			format: donnees.get('format'),
			pilotage: donnees.get('pilotage'),
		} });
		if (creation.ok) {
			afficher(creation.corps);
			element('titre-page').focus();
		} else signaler(creation);
	});

	element('ouvrir').addEventListener('click', async () => {
		const ouverture = await appelerAvecCsrf('POST', `/api/soirees/${encodeURIComponent(soiree.id)}/ouverture`,
			{ entetes: { 'Idempotency-Key': nouvelleCle() } });
		if (ouverture.ok) {
			afficher(ouverture.corps);
			// Le bouton disparaît : le focus va au code qui vient d'apparaître, plutôt qu'en haut de page.
			element('consigne-code').focus();
		} else signaler(ouverture);
	});

	element('ecran').addEventListener('click', async () => {
		// La fenêtre s'ouvre pendant le clic, sinon le navigateur la bloquerait après l'appel.
		const fenetre = window.open('', 'croustyquizz-ecran-de-salle');
		const acces = await appelerAvecCsrf('POST', `/api/soirees/${encodeURIComponent(soiree.id)}/acces-ecran`);
		if (!acces.ok) {
			fenetre?.close();
			signaler(acces);
			return;
		}
		// La clé passe par le fragment, que l'Écran retire dès sa lecture.
		const parametres = new URLSearchParams({ soiree: acces.corps.soireeId });
		const fragment = new URLSearchParams({ cle: acces.corps.cleEcran });
		const adresse = `/ecran.html?${parametres}#${fragment}`;
		if (fenetre) fenetre.location.href = adresse; else location.href = adresse;
	});

	const session = await consulterSession();
	if (session.ok) {
		if (session.corps?.nom) monterMenuCompte(document.querySelector('[data-menu-compte]'), { nom: session.corps.nom });
		const existante = new URLSearchParams(location.search).get('soiree');
		if (existante) {
			const lecture = await appeler('GET', `/api/soirees/${encodeURIComponent(existante)}`);
			if (lecture.ok) afficher(lecture.corps);
			else {
				signaler(lecture);
				if (lecture.statut === 404) element('creation').hidden = false;
			}
		} else {
			element('creation').hidden = false;
		}
	} else if (session.statut !== 401) {
		signaler(session);
	}
}

if (globalThis.document?.getElementById('creation')) void monterPreparation(globalThis.document);
