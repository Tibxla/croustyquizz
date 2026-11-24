import { suivreVue, creerTransport } from './tempsreel.js';
import { creerGestionnaireDeMode } from './modules-de-mode.mjs';
import { creerContexte } from './contexte.mjs';
import { lireAcces } from './identite.mjs';
import { decrireVue } from './vue-commune.mjs';
import { appeler, appelerAvecCsrf, memoire } from './api.js';
import { consulterSession, renvoyerVersConnexion, ROUTE_SESSION } from './compte.mjs';
import { monterProgramme } from './programme-console.mjs';

const ecran = document.body.dataset.ecran;
const element = nom => document.querySelector(`[data-${nom}]`);
const texte = (nom, valeur) => { const cible = element(nom); if (cible) cible.textContent = valeur; };
const etatConnexion = element('connexion');
const zoneErreur = element('erreur');
const zones = { principale: element('principale'), ...(element('laterale') ? { laterale: element('laterale') } : {}) };
const boutonReprise = element('reprise');

function afficherErreur(message) {
	zoneErreur.hidden = false;
	zoneErreur.textContent = message;
}
function lister(nom, valeurs, vide, maximum = Infinity) {
	const cible = element(nom);
	if (!cible) return;
	const lignes = valeurs.length ? valeurs.slice(0, maximum) : [vide];
	if (valeurs.length > maximum) lignes.push(`et ${valeurs.length - maximum} autres`);
	cible.replaceChildren(...lignes.map(valeur => {
		const li = document.createElement('li');
		li.textContent = valeur;
		return li;
	}));
}
function afficherCommun(vue) {
	const description = decrireVue(vue);
	for (const nom of ['titre', 'resume', 'manche', 'buzz', 'personnel']) texte(nom, description[nom]);
	lister('joueurs', description.joueurs, 'Aucun Joueur pour le moment.', ecran === 'ecran' ? 4 : Infinity);
	lister('classement', description.classement, 'Aucun point attribué.', ecran === 'ecran' ? 3 : Infinity);
	texte('nombre-joueurs', `Joueurs (${vue.joueurs.length})`);
	const talon = element('talon');
	if (talon) {
		talon.hidden = !description.codeAcces;
		texte('code', description.codeAcces ?? '');
	}
	texte('attente', vue.modeEnCours === null ? 'La prochaine Manche s’affichera ici.' : '');
	zoneErreur.hidden = true;
}

function activerDeconnexion(arreter) {
	const bouton = element('deconnexion');
	bouton.hidden = false;
	bouton.addEventListener('click', async () => {
		bouton.disabled = true;
		try {
			const sortie = await appelerAvecCsrf('DELETE', ROUTE_SESSION);
			if (sortie.ok || sortie.statut === 401) {
				arreter();
				renvoyerVersConnexion();
			} else {
				afficherErreur(sortie.detail ?? 'Impossible de confirmer la déconnexion. Réessaie.');
			}
		} finally {
			bouton.disabled = false;
		}
	});
}

async function demarrer() {
	const memorise = memoire.lire('croustyquizz.jeton');
	const soireeDemandee = new URLSearchParams(location.search).get('soiree');
	const demoConsole = memorise?.startsWith('demo:ANIMATEUR:') && memorise.split(':')[2] === soireeDemandee;
	const jeton = ecran === 'console' && !demoConsole ? null : memorise;
	const consoleConnectee = ecran === 'console' && !jeton;
	let identite;
	let csrf;
	let suivi;
	let gestionnaire;
	if (consoleConnectee) {
		const session = await consulterSession();
		if (session.statut === 401) return;
		if (!session.ok || !session.corps?.id) throw new Error(session.detail ?? 'Impossible de vérifier ta session. Recharge la Console.');
		identite = session.corps;
		activerDeconnexion(() => {
			suivi?.arreter();
			gestionnaire?.demonter();
		});
		if (soireeDemandee === null) {
			location.replace('/preparer-soiree.html');
			return;
		}
	}
	const acces = lireAcces(ecran, location.search, jeton);
	if (consoleConnectee) {
		acces.acteur.identifiant = identite.id;
		const soiree = await appeler('GET', `/api/soirees/${acces.soireeId}`);
		if (soiree.statut === 401) {
			renvoyerVersConnexion();
			return;
		}
		if (!soiree.ok) throw new Error(soiree.detail ?? 'Impossible d’ouvrir la Soirée. Recharge la Console.');
		const reponse = await creerTransport().envoyer('GET', '/api/csrf');
		if (reponse.statut !== 200 || !reponse.corps?.token) throw new Error(reponse.corps?.detail ?? 'Impossible d’obtenir le jeton de session. Recharge la Console.');
		csrf = reponse.corps.token;
	}

	const transport = creerTransport({ jeton, csrf });
	const { contexte, changerEtat } = creerContexte({ ...acces,
		envoyer: transport.envoyer,
		resynchroniser: () => suivi?.resynchroniser(),
		maintenantServeur: () => suivi?.maintenantServeur() ?? Date.now(),
		surEnvoi(etat, detail) {
			const envoi = element('envoi');
			// Envoi annulé : plus rien n'est en cours, le bandeau disparaît.
			if (etat === null) { envoi.hidden = true; envoi.className = 'envoi'; envoi.textContent = ''; return; }
			envoi.hidden = false;
			envoi.className = `envoi envoi--${etat}`;
			envoi.textContent = detail;
		},
	});
	// Alias de recette explicite : le serveur continue à publier son vrai mode et son bloc.
	const factice = jeton?.startsWith('demo:') && new URLSearchParams(location.search).get('factice') === '1';
	gestionnaire = creerGestionnaireDeMode({ ecran, zones, contexte,
		...(factice ? { charger: () => import(`./modes/factice/${ecran}.mjs`) } : {}),
		surErreur: () => afficherErreur('Le module de ce Mode de jeu est indisponible. Réessaie la synchronisation.'),
	});
	const programme = ecran === 'console' ? monterProgramme(element('edition-programme'), contexte) : null;
	boutonReprise?.addEventListener('click', contexte.resynchroniser);
	if (boutonReprise) boutonReprise.disabled = false;
	suivi = suivreVue({ ...acces,
		surVue(vue, origine) {
			afficherCommun(vue);
			programme?.afficher(vue);
			void gestionnaire.afficher(vue, origine);
		},
		surEtat(etat) {
			if (etat === 'coupure' && consoleConnectee) void consulterSession();
			changerEtat(etat);
			etatConnexion.textContent = {
				synchronisation: 'Synchronisation…', synchronisee: 'Vue synchronisée',
				coupure: 'Connexion perdue. Reconnexion en cours… La vue affichée peut avoir changé.',
			}[etat];
		},
	}, { transport });
	addEventListener('pagehide', evenement => {
		if (evenement.persisted) return;
		suivi.arreter(); gestionnaire.demonter(); programme?.demonter();
	}, { once: true });
}

demarrer().catch(erreur => {
	etatConnexion.textContent = 'Connexion indisponible';
	afficherErreur(erreur.message);
});
