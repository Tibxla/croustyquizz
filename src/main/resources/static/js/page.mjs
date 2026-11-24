import { suivreVue, creerTransport } from './tempsreel.js';
import { creerGestionnaireDeMode } from './modules-de-mode.mjs';
import { creerContexte } from './contexte.mjs';
import { lireAcces } from './identite.mjs';
import { decrireVue } from './vue-commune.mjs';
import { creerLignePalmaresSalle } from './palmares.mjs';

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
function afficherPalmares(palmares) {
	const zone = element('zone-palmares');
	if (!zone) return;
	zone.hidden = palmares === null;
	const liste = element('palmares');
	const vide = element('palmares-vide');
	vide.hidden = palmares?.length !== 0;
	liste.replaceChildren(...(palmares ?? []).map(ligne => {
		if (ecran === 'ecran') return creerLignePalmaresSalle(document, ligne);
		const li = document.createElement('li');
		li.textContent = ligne.texte;
		return li;
	}));
}
function afficherCommun(vue) {
	const description = decrireVue(vue);
	for (const nom of ['titre', 'resume', 'manche', 'buzz', 'personnel']) texte(nom, description[nom]);
	lister('joueurs', description.joueurs, 'Aucun Joueur pour le moment.', ecran === 'ecran' ? 4 : Infinity);
	lister('classement', description.classement, 'Aucun point attribué.', ecran === 'ecran' ? 3 : Infinity);
	lister('programme', description.programme, 'Aucune Manche préparée.');
	afficherPalmares(description.palmares);
	texte('nombre-joueurs', `Joueurs (${vue.joueurs.length})`);
	const talon = element('talon');
	if (talon) {
		talon.hidden = !description.codeAcces;
		texte('code', description.codeAcces ?? '');
	}
	texte('attente', vue.modeEnCours === null && description.palmares === null ? 'La prochaine Manche s’affichera ici.' : '');
	zoneErreur.hidden = true;
}

async function demarrer() {
	const jeton = sessionStorage.getItem('croustyquizz.jeton');
	const acces = lireAcces(ecran, location.search, jeton);
	let csrf;
	if (ecran === 'console' && !jeton?.startsWith('demo:')) {
		const reponse = await creerTransport().envoyer('GET', '/api/csrf');
		if (reponse.statut !== 200 || !reponse.corps?.token) throw new Error(reponse.corps?.detail ?? 'Impossible d’obtenir le jeton de session. Recharge la Console.');
		csrf = reponse.corps.token;
	}
	const transport = creerTransport({ jeton, csrf });
	let suivi;
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
	const gestionnaire = creerGestionnaireDeMode({ ecran, zones, contexte,
		...(factice ? { charger: () => import(`./modes/factice/${ecran}.mjs`) } : {}),
		surErreur: () => afficherErreur('Le module de ce Mode de jeu est indisponible. Réessaie la synchronisation.'),
	});
	boutonReprise?.addEventListener('click', contexte.resynchroniser);
	if (boutonReprise) boutonReprise.disabled = false;
	suivi = suivreVue({ ...acces,
		surVue(vue, origine) {
			afficherCommun(vue);
			void gestionnaire.afficher(vue, origine);
		},
		surEtat(etat) {
			changerEtat(etat);
			etatConnexion.textContent = {
				synchronisation: 'Synchronisation…', synchronisee: 'Vue synchronisée',
				coupure: 'Connexion perdue. Reconnexion en cours… La vue affichée peut avoir changé.',
			}[etat];
		},
	}, { transport });
	addEventListener('pagehide', evenement => {
		if (evenement.persisted) return;
		suivi.arreter(); gestionnaire.demonter();
	}, { once: true });
}

demarrer().catch(erreur => {
	etatConnexion.textContent = 'Connexion indisponible';
	afficherErreur(erreur.message);
});
