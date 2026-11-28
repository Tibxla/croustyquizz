import { appelerAvecCsrf, memoire } from './api.js';
import { adresseConnexion, retourApresConnexion, ROUTE_SESSION } from './compte.mjs';

const messages = {
	EMAIL_DEJA_UTILISE: { champ: 'email', texte: 'Cet e-mail est déjà utilisé. Tu peux te connecter.' },
	IDENTIFIANTS_INVALIDES: { champ: 'motDePasse', texte: 'Vérifie ton e-mail et ton mot de passe.' },
	CSRF_INVALIDE: { texte: 'La session a changé. Réessaie.' },
	RESEAU_INDISPONIBLE: { texte: 'Vérifie ta connexion au réseau et réessaie.' },
	DONNEES_INVALIDES: { texte: 'Vérifie les informations saisies, puis réessaie.' },
};

const champsTexte = {
	lieuNom: 'Indique le nom du Lieu (120 caractères au plus).',
	ville: 'Indique la ville du Lieu (120 caractères au plus).',
	nom: 'Indique ton nom (120 caractères au plus).',
};

export function erreurDeCompte(code) {
	return messages[code] ?? { texte: 'Le serveur n’a pas confirmé l’action. Réessaie dans un instant.' };
}

export function validerChamp(champ, inscription) {
	const { name, value } = champ;
	const taille = [...value].length;
	if (name === 'email') {
		return !value || champ.validity.typeMismatch || taille > 254 ? 'Indique une adresse e-mail valide.' : '';
	}
	if (name === 'motDePasse') {
		if (!inscription && !value) return 'Saisis ton mot de passe.';
		if ((inscription && taille < 12) || taille > 128 || /[\uD800-\uDFFF]/u.test(value)) {
			return inscription ? 'Choisis un mot de passe de 12 à 128 caractères.' : 'Le mot de passe est limité à 128 caractères.';
		}
		return '';
	}
	return !value.trim() || value.includes('\0') || taille > 120 ? champsTexte[name] : '';
}

export function afficherErreurChamp(champ, message) {
	const note = champ.ownerDocument.getElementById(`${champ.id}-erreur`);
	note.textContent = message;
	note.hidden = !message;
	if (message) champ.setAttribute('aria-invalid', 'true');
	else champ.removeAttribute('aria-invalid');
}

export function validerChamps(champs, inscription) {
	let premier;
	for (const champ of champs) {
		const message = validerChamp(champ, inscription);
		afficherErreurChamp(champ, message);
		if (message && !premier) premier = champ;
	}
	premier?.focus();
	return !premier;
}

function monterFormulaire(formulaire) {
	const inscription = formulaire.dataset.compte === 'inscription';
	const retour = retourApresConnexion(location.search);
	const champs = [...formulaire.querySelectorAll('input[name]')];
	const bouton = formulaire.querySelector('button[type="submit"]');
	const erreur = formulaire.querySelector('[role="alert"]');
	const confirmation = document.getElementById('compte-cree');
	const lien = document.querySelector('[data-autre-page]');
	const libelle = bouton.textContent;

	if (confirmation && new URLSearchParams(location.search).get('inscription') === '1') confirmation.hidden = false;
	lien.href = inscription ? adresseConnexion(retour) : `/inscription.html?${new URLSearchParams({ retour })}`;

	for (const champ of champs) {
		champ.addEventListener('input', () => afficherErreurChamp(champ, ''));
	}
	formulaire.addEventListener('submit', async evenement => {
		evenement.preventDefault();
		if (bouton.disabled) return;
		erreur.hidden = true;
		if (!validerChamps(champs, inscription)) return;

		const { email, motDePasse, lieuNom, ville, nom } = Object.fromEntries(new FormData(formulaire));
		const corps = inscription ? { email, motDePasse, nom: nom.trim(), lieu: { nom: lieuNom.trim(), ville: ville.trim() } }
			: { email, motDePasse };
		bouton.disabled = true;
		bouton.textContent = inscription ? 'Création du compte…' : 'Connexion…';
		try {
			const resultat = await appelerAvecCsrf('POST', inscription ? '/api/animateurs' : ROUTE_SESSION, { corps });
			const identite = inscription ? resultat.corps?.animateur : resultat.corps;
			if (resultat.statut === (inscription ? 201 : 200) && identite?.id) {
				if (!inscription && memoire.lire('croustyquizz.jeton')?.startsWith('demo:ANIMATEUR:')) {
					memoire.oublier('croustyquizz.jeton');
				}
				location.replace(inscription ? adresseConnexion(retour, { compteCree: true }) : retour);
				return;
			}
			const refus = erreurDeCompte(resultat.code);
			if (refus.champ) {
				const champ = formulaire.elements.namedItem(refus.champ);
				afficherErreurChamp(champ, refus.texte);
				champ.focus();
			} else {
				erreur.textContent = refus.texte;
				erreur.hidden = false;
			}
		} finally {
			bouton.disabled = false;
			bouton.textContent = libelle;
		}
	});
}

const formulaire = globalThis.document?.querySelector('form[data-compte]');
if (formulaire) monterFormulaire(formulaire);
