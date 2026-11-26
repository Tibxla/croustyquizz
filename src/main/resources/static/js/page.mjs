import { suivreVue, creerTransport } from './tempsreel.js';
import { creerGestionnaireDeMode } from './modules-de-mode.mjs';
import { creerContexte } from './contexte.mjs';
import { lireAcces } from './identite.mjs';
import { adresseRetourArrivee, controlerJeton, creerAccesManette } from './acces-manette.mjs';
import { afficherClassementDeSalle, decrireVue } from './vue-commune.mjs';
import { monterAudioSalle } from './audio-salle.mjs';
import { monterMusiqueDAttenteConsole, monterMusiqueDAttenteSalle } from './pilotage-musique-attente.mjs';
import { monterProgramme } from './programme-console.mjs';
import { monterBuzz } from './buzz-manette.mjs';
import { monterPilotage } from './pilotage-console.mjs';
import { monterTalonSalle } from './talon-salle.mjs';
import { appeler, memoire } from './api.js';
import { consulterSession, maintenirSession, renvoyerVersConnexion, ROUTE_SESSION } from './compte.mjs';

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
	lister('classement', description.classement, 'Aucun point attribué.');
	const classementVisible = ecran === 'ecran' && afficherClassementDeSalle(element('classement-salle'), vue);
	texte('nombre-joueurs', `Joueurs (${vue.joueurs.length})`);
	texte('attente', classementVisible ? '' : description.attente);
	zoneErreur.hidden = true;
}

function activerDeconnexion(envoyer, arreter) {
	const bouton = element('deconnexion');
	bouton.hidden = false;
	bouton.addEventListener('click', async () => {
		bouton.disabled = true;
		const erreur = 'Impossible de confirmer la déconnexion. Réessaie.';
		try {
			const { statut, corps } = await envoyer('DELETE', ROUTE_SESSION);
			if (statut === 204 || statut === 401) {
				arreter();
				renvoyerVersConnexion();
			} else {
				afficherErreur(corps?.detail ?? erreur);
			}
		} catch {
			afficherErreur(erreur);
		} finally {
			bouton.disabled = false;
		}
	});
}
async function demarrer() {
	const soireeId = new URLSearchParams(location.search).get('soiree');
	const stockageManette = ecran === 'manette' ? creerAccesManette() : null;
	const accesManette = stockageManette?.lire(soireeId);
	const cleEnMemoire = ecran === 'ecran' && memoire.lire('croustyquizz.soireeEcran') === soireeId
		? memoire.lire('croustyquizz.cleEcran') : null;
	const memorise = memoire.lire('croustyquizz.jeton');
	// La Console n'emploie le jeton de démonstration que pour sa Soirée ; sinon, la session Animateur.
	const demoConsole = memorise?.startsWith('demo:ANIMATEUR:') && memorise.split(':')[2] === soireeId;
	const jetonEnMemoire = accesManette?.jeton ?? cleEnMemoire ?? (ecran === 'console' && !demoConsole ? null : memorise);
	const consoleConnectee = ecran === 'console' && !jetonEnMemoire;
	let identite;
	let suivi;
	let arreterTout;
	let arreterMaintien;
	if (consoleConnectee) {
		const session = await consulterSession();
		if (session.statut === 401) return;
		if (!session.ok || !session.corps?.id) throw new Error(session.detail ?? 'Impossible de vérifier ta session. Recharge la Console.');
		identite = session.corps;
		if (soireeId === null) {
			location.replace('/preparer-soiree.html');
			return;
		}
	}
	const { cleEcran, ...acces } = lireAcces(ecran, location.search, jetonEnMemoire, accesManette?.joueurId, location.hash);
	const jeton = cleEcran ?? jetonEnMemoire;
	const transport = creerTransport({ jeton, sessionAnimateur: consoleConnectee });
	if (cleEcran !== null) {
		memoire.ecrire('croustyquizz.cleEcran', cleEcran);
		memoire.ecrire('croustyquizz.soireeEcran', soireeId);
		history.replaceState(null, '', location.pathname + location.search);
	}
	if (consoleConnectee) {
		acces.acteur.identifiant = identite.id;
		const soiree = await appeler('GET', `/api/soirees/${acces.soireeId}`);
		if (soiree.statut === 401) {
			renvoyerVersConnexion();
			return;
		}
		if (!soiree.ok) throw new Error(soiree.detail ?? 'Impossible d’ouvrir la Soirée. Recharge la Console.');
		activerDeconnexion(transport.envoyer, () => arreterTout?.());
		arreterMaintien = maintenirSession();
	}

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
	const boutonMusique = element('musique-attente');
	const musiqueConsole = boutonMusique
		? monterMusiqueDAttenteConsole(boutonMusique, element('musique-attente-statut'), contexte) : null;
	const musiqueSalle = audioSalle ? monterMusiqueDAttenteSalle(audioSalle, contexte) : null;
	// Alias de recette explicite : le serveur continue à publier son vrai mode et son bloc.
	const factice = jeton?.startsWith('demo:') && new URLSearchParams(location.search).get('factice') === '1';
	const gestionnaire = creerGestionnaireDeMode({ ecran, zones, contexte,
		...(factice ? { charger: () => import(`./modes/factice/${ecran}.mjs`) } : {}),
		surErreur: () => afficherErreur('Le module de ce Mode de jeu est indisponible. Réessaie la synchronisation.'),
	});
	const zonePhotos = element('photos');
	const photos = zonePhotos ? (await import(`./photos/${ecran}.mjs`))
		.monter(zonePhotos, { ...contexte, chargerImage: transport.chargerImage }) : null;
	const programme = ecran === 'console' ? monterProgramme(element('edition-programme'), contexte) : null;
	const boutonBuzz = element('buzzer');
	const buzz = accesManette && boutonBuzz ? monterBuzz(boutonBuzz, contexte) : null;
	const pilotage = ecran === 'console' ? monterPilotage(element('pilotage-soiree'), contexte) : null;
	boutonReprise?.addEventListener('click', contexte.resynchroniser);
	if (boutonReprise) boutonReprise.disabled = false;
	suivi = suivreVue({ ...acces,
		surVue(vue, origine) {
			afficherCommun(vue);
			void talon?.afficher(vue.soiree);
			programme?.afficher(vue);
			photos?.afficher(vue);
			buzz?.afficher(vue);
			pilotage?.afficher(vue);
			const modeAffiche = gestionnaire.afficher(vue, origine);
			musiqueConsole?.afficher(vue);
			musiqueSalle?.afficher(vue, modeAffiche);
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
	let arrete = false;
	// Appelé au départ de la page comme à la déconnexion de l'Animateur, une seule fois.
	arreterTout = () => {
		if (arrete) return;
		arrete = true;
		arreterMaintien?.();
		talon?.demonter();
		suivi.arreter(); gestionnaire.demonter(); programme?.demonter(); buzz?.demonter(); photos?.demonter();
		pilotage?.demonter();
		musiqueConsole?.demonter(); musiqueSalle?.demonter();
		void audioSalle?.detruire();
	};
	addEventListener('pagehide', evenement => {
		if (evenement.persisted) { audioSalle?.suspendre(true); return; }
		arreterTout();
	});
	addEventListener('pageshow', evenement => {
		if (evenement.persisted && audioSalle) {
			suivi.resynchroniser();
			audioSalle.suspendre(false);
		}
	});
}

demarrer().catch(erreur => {
	etatConnexion.textContent = 'Connexion indisponible';
	afficherErreur(erreur.message);
});
