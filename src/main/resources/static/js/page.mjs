import { suivreVue, creerTransport } from './tempsreel.js';
import { creerGestionnaireDeMode } from './modules-de-mode.mjs';
import { creerContexte } from './contexte.mjs';
import { lireAcces } from './identite.mjs';
import { adresseRetourArrivee, controlerJeton, creerAccesManette } from './acces-manette.mjs';
import { afficherClassementDeSalle, decrireBarre, decrireConnexion, decrirePalmaresDeSalle, decrireVue } from './vue-commune.mjs';
import { monterMenu } from './menu.mjs';
import { creerAnnonceBuzz, creerArrivees, estAccueil } from './salle-accueil.mjs';
import { monterAudioSalle } from './audio-salle.mjs';
import { monterMusiqueDAttenteConsole, monterMusiqueDAttenteSalle } from './pilotage-musique-attente.mjs';
import { monterProgramme } from './programme-console.mjs';
import { monterBuzz } from './buzz-manette.mjs';
import { monterPilotage } from './pilotage-console.mjs';
import { monterTalonSalle } from './talon-salle.mjs';
import { memoire } from './api.js';
import { monterCatalogueInstrumentales } from './modes/freestyle/catalogue-salle.mjs';
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
	const description = decrireVue(vue, { ecran });
	for (const nom of ['titre', 'resume', 'manche', 'buzz', 'personnel', 'pseudo']) texte(nom, description[nom]);
	// Une ligne vide ne prend pas de place : le Buzz, le résumé ou le rang ne parlent que s'ils ont quelque chose à dire.
	for (const nom of ['resume', 'buzz', 'personnel']) { const cible = element(nom); if (cible) cible.hidden = !description[nom]; }
	lister('joueurs', description.joueurs, 'Aucun Joueur pour le moment.', ecran === 'ecran' ? 4 : Infinity);
	lister('classement', description.classement, 'Aucun point attribué.');
	texte('titre-palmares', vue.palmaresFreestyle != null ? 'Palmarès du Freestyle' : 'Palmarès du Karaoké');
	afficherPalmares(ecran === 'ecran' ? decrirePalmaresDeSalle(vue) : description.palmares);
	const classementVisible = ecran === 'ecran' && afficherClassementDeSalle(element('classement-salle'), vue);
	texte('titre-pied', vue.soiree.titre);
	texte('nombre-joueurs', `Joueurs (${vue.joueurs.length})`);
	if (element('barre-mode')) {
		// La barre de la Console dit d'un coup d'œil l'étape, le code d'accès et le nombre de Joueurs (#282).
		const barre = decrireBarre(vue);
		texte('barre-mode', barre.mode);
		texte('barre-code-valeur', barre.code ?? '');
		element('barre-code').hidden = !barre.code;
		texte('barre-joueurs', barre.joueurs);
	}
	texte('attente', classementVisible ? '' : description.attente);
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
	// Salle : hors Manche, le talon passe au centre avec les arrivées ; en Manche, il revient en bas de colonne (#281).
	const accueil = element('accueil');
	const arrivees = accueil ? creerArrivees(element('arrivees')) : null;
	const annonceBuzz = element('annonce-buzz') ? creerAnnonceBuzz(element('annonce-buzz')) : null;
	const colonneTalon = zoneTalon?.parentNode;
	function afficherScene(vue, origine) {
		if (!accueil) return;
		const enAccueil = estAccueil(vue, { photo: element('photos')?.hidden === false });
		document.body.classList.toggle('salle--accueil', enAccueil);
		document.body.classList.toggle('salle--manche', Boolean(vue.modeEnCours));
		accueil.hidden = !enAccueil;
		const place = enAccueil ? element('accueil-talon') : colonneTalon;
		if (zoneTalon.parentNode !== place) place.append(zoneTalon);
		arrivees.afficher(vue.joueurs, origine);
		element('arrivees-vide').hidden = vue.joueurs.length > 0;
		texte('arrivees-titre', vue.joueurs.length ? `Déjà là · ${vue.joueurs.length}` : 'Déjà là');
		if (enAccueil) texte('attente', '');
		annonceBuzz.afficher(vue, origine);
	}
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
	const catalogueInstrumentales = audioSalle ? monterCatalogueInstrumentales(audioSalle, contexte,
		element('etat-instrumentales'), element('redeclarer-instrumentales')) : null;
	if (audioSalle) Object.assign(contexte, { audioSalle, catalogueInstrumentales, signalerSonDeManche: audioSalle.signalerSonDeManche });
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
	const pilotage = ecran === 'console' ? monterPilotage(element('pilotage-soiree'), contexte, { raison: element('raison-lancer') }) : null;
	const menu = element('menu-bouton') ? monterMenu(element('menu-bouton'), element('menu')) : null;
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
			afficherScene(vue, origine);
		},
		surEtat(etat) {
			changerEtat(etat);
			// Une pastille discrète tant que tout va bien ; un bandeau seulement en cas de coupure (#279).
			const { texte: libelle, coupure } = decrireConnexion(etat);
			etatConnexion.textContent = libelle;
			etatConnexion.className = coupure ? 'coupure' : `connexion connexion--${etat}`;
		},
	}, { transport });
	addEventListener('pagehide', evenement => {
		if (evenement.persisted) { audioSalle?.suspendre(true); return; }
		talon?.demonter(); annonceBuzz?.demonter();
		suivi.arreter(); gestionnaire.demonter(); programme?.demonter(); buzz?.demonter(); photos?.demonter();
		pilotage?.demonter(); menu?.demonter();
		musiqueConsole?.demonter(); musiqueSalle?.demonter(); catalogueInstrumentales?.demonter();
		void audioSalle?.detruire();
	});
	addEventListener('pageshow', evenement => {
		if (evenement.persisted && audioSalle) {
			suivi.resynchroniser();
			audioSalle.suspendre(false);
		}
	});
}

demarrer().catch(erreur => {
	etatConnexion.textContent = 'Connexion indisponible'; etatConnexion.className = 'coupure';
	afficherErreur(erreur.message);
});
