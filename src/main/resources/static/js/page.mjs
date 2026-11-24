import { suivreVue, creerTransport } from './tempsreel.js';
import { creerGestionnaireDeMode } from './modules-de-mode.mjs';
import { creerContexte } from './contexte.mjs';
import { lireAcces } from './identite.mjs';
import { adresseRetourArrivee, controlerJeton, creerAccesManette } from './acces-manette.mjs';
import { decrireVue } from './vue-commune.mjs';
import { monterAudioSalle } from './audio-salle.mjs';
import { monterProgramme } from './programme-console.mjs';
import { monterPilotage } from './pilotage-console.mjs';
import { monterTalonSalle } from './talon-salle.mjs';
import { memoire } from './api.js';

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
	texte('attente', vue.modeEnCours === null && vue.soiree.etat !== 'TERMINEE' ? 'La prochaine Manche s’affichera ici.' : '');
	zoneErreur.hidden = true;
}

async function demarrer() {
	const soireeId = new URLSearchParams(location.search).get('soiree');
	const stockageManette = ecran === 'manette' ? creerAccesManette() : null;
	const accesManette = stockageManette?.lire(soireeId);
	const cleEnMemoire = ecran === 'ecran' && memoire.lire('croustyquizz.soireeEcran') === soireeId
		? memoire.lire('croustyquizz.cleEcran') : null;
	const jetonEnMemoire = accesManette?.jeton ?? cleEnMemoire ?? memoire.lire('croustyquizz.jeton');
	const { cleEcran, ...acces } = lireAcces(ecran, location.search, jetonEnMemoire, accesManette?.joueurId, location.hash);
	const jeton = cleEcran ?? jetonEnMemoire;
	if (cleEcran !== null) {
		memoire.ecrire('croustyquizz.cleEcran', cleEcran);
		memoire.ecrire('croustyquizz.soireeEcran', soireeId);
		history.replaceState(null, '', location.pathname + location.search);
	}
	let csrf;
	if (ecran === 'console' && !jeton?.startsWith('demo:')) {
		const reponse = await creerTransport().envoyer('GET', '/api/csrf');
		if (reponse.statut !== 200 || !reponse.corps?.token) throw new Error(reponse.corps?.detail ?? 'Impossible d’obtenir le jeton de session. Recharge la Console.');
		csrf = reponse.corps.token;
	}
	const transport = creerTransport({ jeton, csrf });
	// Un jeton que le serveur ne reconnaît plus renvoie à l'arrivée, au lieu de boucler en reconnexion.
	if (accesManette && await controlerJeton(transport.envoyer, acces.soireeId) === 'refuse') {
		stockageManette.oublier(acces.soireeId);
		location.replace(adresseRetourArrivee(accesManette.codeAcces));
		return;
	}
	const zoneTalon = element('talon');
	const talon = zoneTalon ? monterTalonSalle(zoneTalon, { soireeId, chargerQrCode: transport.chargerQrCode }) : null;
	const zoneAudio = element('audio-salle');
	const audioSalle = zoneAudio ? monterAudioSalle(zoneAudio) : null;
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
	if (audioSalle) Object.assign(contexte, { audioSalle, signalerSonDeManche: audioSalle.signalerSonDeManche });
	// Alias de recette explicite : le serveur continue à publier son vrai mode et son bloc.
	const factice = jeton?.startsWith('demo:') && new URLSearchParams(location.search).get('factice') === '1';
	const gestionnaire = creerGestionnaireDeMode({ ecran, zones, contexte,
		...(factice ? { charger: () => import(`./modes/factice/${ecran}.mjs`) } : {}),
		surErreur: () => afficherErreur('Le module de ce Mode de jeu est indisponible. Réessaie la synchronisation.'),
	});
	const programme = ecran === 'console' ? monterProgramme(element('edition-programme'), contexte) : null;
	const pilotage = ecran === 'console' ? monterPilotage(element('pilotage-soiree'), contexte) : null;
	boutonReprise?.addEventListener('click', contexte.resynchroniser);
	if (boutonReprise) boutonReprise.disabled = false;
	suivi = suivreVue({ ...acces,
		surVue(vue, origine) {
			afficherCommun(vue);
			void talon?.afficher(vue.soiree);
			programme?.afficher(vue);
			pilotage?.afficher(vue);
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
		if (evenement.persisted) { audioSalle?.suspendre(true); return; }
		talon?.demonter();
		suivi.arreter(); gestionnaire.demonter(); programme?.demonter();
		pilotage?.demonter();
		void audioSalle?.detruire();
	});
	addEventListener('pageshow', evenement => {
		if (evenement.persisted) audioSalle?.suspendre(false);
	});
}

demarrer().catch(erreur => {
	etatConnexion.textContent = 'Connexion indisponible';
	afficherErreur(erreur.message);
});
