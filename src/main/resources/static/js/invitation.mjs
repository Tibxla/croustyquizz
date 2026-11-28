import { appeler, appelerAvecCsrf, memoire } from './api.js';
import { ouvrirDialogue } from './dialogue.mjs';
import { ROUTE_SESSION, SOIREES_DU_LIEU } from './compte.mjs';
import { afficherErreurChamp, erreurDeCompte, validerChamps } from './formulaire-compte.mjs';

const INVITATIONS = '/api/animateurs/invitations';
const FORME_CLE = /^[A-Za-z0-9_-]{43}$/;
const PLAFOND_ATTEINT = 'Ce Lieu a déjà vingt invitations en cours. Attends qu’une invitation soit utilisée ou expire.';
const LIEN_INVALIDE = 'Ce lien d’invitation est invalide, expiré ou déjà utilisé. Demande un nouveau lien à l’Animateur qui t’a invité.';

export function monterInvitationConsole(bouton, dialogue, origine = location.origin, pressePapiers = navigator.clipboard) {
	const formulaire = dialogue.querySelector('form');
	const email = formulaire.querySelector('input[name="email"]');
	const creer = formulaire.querySelector('button[type="submit"]');
	const resultat = dialogue.querySelector('[data-lien-invitation]');
	const champLien = resultat.querySelector('input');
	const copier = resultat.querySelector('button');
	const statut = dialogue.querySelector('[role="status"]');
	let cycle = 0;

	bouton.hidden = false;
	bouton.addEventListener('click', () => ouvrirDialogue(dialogue, bouton));
	dialogue.querySelector('[data-fermer-invitation]').addEventListener('click', () => dialogue.close());
	dialogue.addEventListener('close', () => {
		cycle++;
		formulaire.hidden = false;
		resultat.hidden = true;
		creer.disabled = false;
		email.value = '';
		champLien.value = '';
		statut.textContent = '';
		afficherErreurChamp(email, '');
	});
	email.addEventListener('input', () => afficherErreurChamp(email, ''));
	formulaire.addEventListener('submit', async evenement => {
		evenement.preventDefault();
		if (creer.disabled) return;
		statut.textContent = '';
		if (!validerChamps([email], false)) return;
		const envoi = cycle;
		creer.disabled = true;
		statut.textContent = 'Création du lien…';
		const reponse = await appelerAvecCsrf('POST', INVITATIONS, { corps: { email: email.value } });
		if (envoi !== cycle || !dialogue.open) return;
		creer.disabled = false;
		statut.textContent = '';
		if (reponse.statut === 201 && FORME_CLE.test(reponse.corps?.cle)) {
			const lien = new URL('/invitation.html', origine);
			lien.hash = new URLSearchParams({ cle: reponse.corps.cle });
			champLien.value = lien.href;
			formulaire.hidden = true;
			resultat.hidden = false;
			copier.focus();
		} else if (reponse.code === 'EMAIL_DEJA_UTILISE' || reponse.code === 'DONNEES_INVALIDES') {
			afficherErreurChamp(email, reponse.code === 'EMAIL_DEJA_UTILISE'
				? 'Cet e-mail est déjà utilisé.' : 'Indique une adresse e-mail valide.');
			email.focus();
		} else {
			statut.textContent = reponse.statut === 401 ? 'Reconnecte-toi pour créer une invitation.'
				: reponse.code === 'LIMITE_ATTEINTE' ? PLAFOND_ATTEINT : erreurDeCompte(reponse.code).texte;
		}
	});
	copier.addEventListener('click', async () => {
		const envoi = cycle;
		try {
			await pressePapiers.writeText(champLien.value);
			if (envoi === cycle && dialogue.open) statut.textContent = 'Lien copié.';
		} catch {
			if (envoi !== cycle || !dialogue.open) return;
			champLien.focus();
			champLien.select();
			statut.textContent = 'Copie le lien sélectionné.';
		}
	});
}

// La clé arrive dans le fragment : elle est rangée dans l'onglet puis retirée de l'adresse et de l'historique,
// comme la clé d'Écran ; un rechargement la retrouve dans l'onglet, jusqu'à ce qu'elle ne serve plus.
const CLE_ONGLET = 'croustyquizz.invitation';
export function lireCleInvitation(navigation, memoire = globalThis.sessionStorage, historique = globalThis.history) {
	const depuisFragment = new URLSearchParams(navigation.hash.slice(1)).get('cle');
	if (depuisFragment) {
		try { memoire?.setItem(CLE_ONGLET, depuisFragment); } catch { /* onglet sans stockage : la clé reste en mémoire */ }
		historique?.replaceState?.(null, '', navigation.pathname + navigation.search);
		return depuisFragment;
	}
	try { return memoire?.getItem(CLE_ONGLET) ?? null; } catch { return null; }
}

function oublierCleInvitation(memoire) {
	try { memoire?.removeItem(CLE_ONGLET); } catch { /* stockage indisponible */ }
}

export async function monterInvitationInvite(racine, navigation = location, onglet = globalThis.sessionStorage) {
	const statut = racine.querySelector('[role="status"]');
	const cle = lireCleInvitation(navigation, onglet);
	if (!FORME_CLE.test(cle)) {
		oublierCleInvitation(onglet);
		statut.textContent = LIEN_INVALIDE;
		return;
	}
	statut.textContent = 'Vérification de l’invitation…';
	// Accepter ouvrirait la session du nouveau compte à la place de celle-ci, Console ouverte comprise.
	const session = await appeler('GET', ROUTE_SESSION);
	if (session.statut === 200 && session.corps?.id) {
		demanderDeconnexion(racine, session.corps.nom, () => ouvrirInvitation(racine, navigation, onglet, cle));
		return;
	}
	await ouvrirInvitation(racine, navigation, onglet, cle);
}

function demanderDeconnexion(racine, nom, continuer) {
	const statut = racine.querySelector('[role="status"]');
	const bouton = racine.querySelector('[data-deconnexion]');
	statut.textContent = `Ce navigateur est connecté au compte de ${nom}. Déconnecte-toi pour rejoindre ce Lieu avec un nouveau compte.`;
	bouton.hidden = false;
	bouton.addEventListener('click', async () => {
		if (bouton.disabled) return;
		bouton.disabled = true;
		const sortie = await appelerAvecCsrf('DELETE', ROUTE_SESSION);
		bouton.disabled = false;
		if (!sortie.ok && sortie.statut !== 401) {
			statut.textContent = 'Impossible de confirmer la déconnexion. Réessaie.';
			return;
		}
		bouton.hidden = true;
		await continuer();
	});
}

async function ouvrirInvitation(racine, navigation, onglet, cle) {
	const statut = racine.querySelector('[role="status"]');
	statut.textContent = 'Vérification de l’invitation…';
	const invitation = await appeler('POST', `${INVITATIONS}/consultation`, { corps: { cle } });
	if (invitation.statut !== 200 || !invitation.corps?.nomLieu) {
		if (invitation.code === 'INVITATION_INVALIDE') oublierCleInvitation(onglet);
		statut.textContent = invitation.code === 'INVITATION_INVALIDE' ? LIEN_INVALIDE
			: 'Impossible de vérifier l’invitation. Recharge cette page pour réessayer.';
		return;
	}

	const { nomLieu, ville, email } = invitation.corps;
	racine.querySelector('h1').textContent = `Rejoindre ${nomLieu} (${ville})`;
	statut.hidden = true;
	const formulaire = racine.querySelector('form');
	const emailInvite = formulaire.querySelector('input[name="email"]');
	const nom = formulaire.querySelector('input[name="nom"]');
	const motDePasse = formulaire.querySelector('input[name="motDePasse"]');
	const bouton = formulaire.querySelector('button[type="submit"]');
	const erreur = formulaire.querySelector('[role="alert"]');
	emailInvite.value = email;
	formulaire.hidden = false;
	for (const champ of [nom, motDePasse]) {
		champ.addEventListener('input', () => afficherErreurChamp(champ, ''));
	}
	// La clé ne sert plus : le formulaire laisse la place au seul message, annoncé et focalisé.
	function conclure(texte, lien = null) {
		oublierCleInvitation(onglet);
		formulaire.hidden = true;
		motDePasse.value = '';
		statut.textContent = texte;
		if (lien) statut.append(lien);
		statut.hidden = false;
		statut.focus();
	}
	formulaire.addEventListener('submit', async evenement => {
		evenement.preventDefault();
		if (bouton.disabled) return;
		erreur.hidden = true;
		if (!validerChamps([nom, motDePasse], true)) return;
		bouton.disabled = true;
		bouton.textContent = 'Création du compte…';
		const reponse = await appelerAvecCsrf('POST', `${INVITATIONS}/acceptation`, {
			corps: { cle, nom: nom.value.trim(), motDePasse: motDePasse.value },
		});
		if (reponse.statut === 201 && reponse.corps?.id) {
			motDePasse.value = '';
			oublierCleInvitation(onglet);
			if (memoire.lire('croustyquizz.jeton')?.startsWith('demo:ANIMATEUR:')) memoire.oublier('croustyquizz.jeton');
			navigation.replace(SOIREES_DU_LIEU);
			return;
		}
		bouton.textContent = 'Rejoindre le Lieu';
		if (reponse.code === 'INVITATION_INVALIDE') {
			conclure(LIEN_INVALIDE);
		} else if (reponse.code === 'EMAIL_DEJA_UTILISE') {
			const connexion = statut.ownerDocument.createElement('a');
			connexion.href = '/connexion.html';
			connexion.textContent = 'te connecter avec ce compte';
			conclure(`Un compte existe déjà pour ${email} : `, connexion);
		} else {
			erreur.textContent = erreurDeCompte(reponse.code).texte;
			erreur.hidden = false;
			bouton.disabled = false;
		}
	});
}

const invitation = globalThis.document?.querySelector('[data-invitation-invite]');
if (invitation) void monterInvitationInvite(invitation);
