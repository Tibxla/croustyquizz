import { appeler, appelerAvecCsrf, nouvelleCle } from './api.js';
import { consulterSession, renvoyerVersConnexion } from './compte.mjs';
import { ouvrirDialogue } from './dialogue.mjs';
import { monterMenuCompte } from './menu-compte.mjs';

const ROUTE = '/api/campagnes-sms';
const LIEN_EXEMPLE = 'https://croustyquizz.invalid/stop/exemple';
const GSM = '@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !"#¤%&\'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà';
const EXTENSION_GSM = '\f^{}\\[~]|€';
const PONCTUATION_GSM = {
	'‘': "'", '’': "'", '«': '"', '»': '"', '“': '"', '”': '"',
	'…': '...', '–': '-', '—': '-', '\u00a0': ' ', '\u202f': ' ',
};
const MODES_ENVOI = {
	SIMULE: 'Les envois sont simulés.',
	TWILIO: 'Envoi réel par Twilio.',
	TWILIO_ESSAI: "Compte d'essai Twilio : le SMS reçu est un message type de Twilio, pas ton texte, et seuls les numéros vérifiés le reçoivent.",
};
const ETATS = { PROGRAMMEE: 'Programmée', EN_COURS: 'En cours', TERMINEE: 'Terminée' };
const euros = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });
const calendrier = new Intl.DateTimeFormat('fr-FR', {
	timeZone: 'Europe/Paris', weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
	hour: '2-digit', minute: '2-digit',
});

function horsGsm(caractere) {
	return !GSM.includes(caractere) && !EXTENSION_GSM.includes(caractere);
}

export function remplacerCaracteresSpeciaux(texte) {
	return Array.from(texte, caractere => PONCTUATION_GSM[caractere] ?? caractere).join('');
}

export function compterSegments(texte) {
	const caracteres = Array.from(texte);
	const gsm = !caracteres.some(horsGsm);
	const poids = caracteres.map(caractere => gsm && EXTENSION_GSM.includes(caractere) ? 2 : caractere.length);
	if (poids.reduce((total, valeur) => total + valeur, 0) <= (gsm ? 160 : 70)) return 1;
	const capacite = gsm ? 153 : 67;
	let segments = 1;
	let reste = capacite;
	for (const valeur of poids) {
		if (valeur > reste) {
			segments += 1;
			reste = capacite;
		}
		reste -= valeur;
	}
	return segments;
}

export function decrireBrouillon(saisie) {
	const texte = saisie.trim();
	const segments = texte ? compterSegments(`${texte}\nSTOP : ${LIEN_EXEMPLE}`) : 0;
	return {
		caracteres: Array.from(saisie).length, segments, valide: segments > 0 && segments <= 6,
		unicode: Array.from(texte).some(horsGsm),
	};
}

function quantite(nombre, nom) {
	return `${nombre} ${nom}${nombre > 1 ? 's' : ''}`;
}

export function libelleEtat(etat) {
	return ETATS[etat];
}

export function libelleModeEnvoi(mode) {
	return MODES_ENVOI[mode] ?? '';
}

export function decrireEnvoi({ destinataires, coutEstimeCentimes, immediat, envoiPrevuLe }) {
	const action = immediat ? 'Envoyer' : 'Programmer';
	const creneau = immediat ? 'Dès maintenant' : calendrier.format(new Date(envoiPrevuLe));
	const cout = euros.format(coutEstimeCentimes / 100);
	const confirmation = `${action} le SMS à ${quantite(destinataires, 'destinataire')} ${creneau.toLowerCase()} ? Coût estimé : ${cout}.`;
	return { action, creneau, cout, confirmation, possible: destinataires > 0 };
}

function afficherHistorique(zone, vide, campagnes) {
	const document = zone.ownerDocument;
	vide.hidden = campagnes.length > 0;
	zone.replaceChildren(...campagnes.map(campagne => {
		const ligne = document.createElement('li');
		ligne.className = 'soirees__ligne';
		const contenu = document.createElement('div');
		const texte = document.createElement('strong');
		texte.className = 'campagnes__liste-texte';
		texte.textContent = campagne.texte;
		const detail = document.createElement('small');
		const date = calendrier.format(new Date(campagne.envoiPrevuLe));
		const compteurs = `${quantite(campagne.envoyes, 'envoyé')} · ${quantite(campagne.echoues, 'échec')}`;
		detail.textContent = `${libelleEtat(campagne.etat)} · ${date} · ${compteurs}`;
		contenu.append(texte, detail);
		ligne.append(contenu);
		return ligne;
	}));
}

export async function monterCampagnesSms(document) {
	const parId = id => document.getElementById(id);
	const texte = parId('texte-campagne');
	const compteur = parId('compteur-campagne');
	const modeEnvoi = parId('mode-envoi-campagnes');
	const conseilEncodage = parId('conseil-encodage');
	const boutonRemplacer = parId('remplacer-caracteres');
	const boutonApercu = parId('previsualiser-campagne');
	const boutonAction = parId('confirmer-campagne');
	const dialogue = parId('validation-campagne');
	const statut = parId('statut-campagnes');
	const contactsVides = parId('contacts-vides');
	const apercuVide = parId('apercu-vide');
	const zoneApercu = parId('apercu-campagne');

	let apercu;
	let previsualisationEnCours = false;
	let creationEnCours = false;
	let tentative;

	function annoncer(message, etat = '') {
		statut.className = etat ? `envoi envoi--${etat}` : 'envoi';
		statut.textContent = message;
		statut.hidden = !message;
	}

	function actualiserBrouillon() {
		const brouillon = decrireBrouillon(texte.value);
		compteur.textContent = `${quantite(brouillon.caracteres, 'caractère')} · ${quantite(brouillon.segments, 'segment')}`;
		compteur.toggleAttribute('data-hors-limite', brouillon.segments > 6);
		conseilEncodage.hidden = !brouillon.unicode;
		boutonRemplacer.hidden = !brouillon.unicode || remplacerCaracteresSpeciaux(texte.value) === texte.value;
		boutonRemplacer.disabled = creationEnCours;
		boutonApercu.disabled = !brouillon.valide || previsualisationEnCours || creationEnCours;
		boutonApercu.classList.toggle('bouton--action', !apercu?.possible);
	}

	function effacerApercu() {
		apercu = undefined;
		apercuVide.hidden = false;
		zoneApercu.hidden = true;
	}

	function afficherMode(mode) {
		modeEnvoi.textContent = libelleModeEnvoi(mode);
		modeEnvoi.hidden = !modeEnvoi.textContent;
	}

	async function chargerCampagnes() {
		const resultat = await appeler('GET', ROUTE);
		if (resultat.statut === 401) {
			renvoyerVersConnexion();
			return false;
		}
		if (!resultat.ok || !resultat.corps) return false;
		afficherMode(resultat.corps.modeEnvoi);
		contactsVides.hidden = resultat.corps.destinatairesEligibles > 0;
		afficherHistorique(parId('historique-campagnes'), parId('historique-vide'), resultat.corps.campagnes);
		return true;
	}

	function modifierBrouillon() {
		effacerApercu();
		annoncer('');
		actualiserBrouillon();
	}

	texte.addEventListener('input', modifierBrouillon);
	boutonRemplacer.addEventListener('click', () => {
		texte.value = remplacerCaracteresSpeciaux(texte.value);
		texte.focus();
		modifierBrouillon();
	});

	parId('redaction-campagne').addEventListener('submit', async evenement => {
		evenement.preventDefault();
		if (boutonApercu.disabled) return;
		const saisie = texte.value;
		previsualisationEnCours = true;
		effacerApercu();
		actualiserBrouillon();
		annoncer('Calcul de l’aperçu…');
		const resultat = await appelerAvecCsrf('POST', ROUTE + '/apercu', { corps: { texte: saisie } });
		previsualisationEnCours = false;
		actualiserBrouillon();
		if (resultat.statut === 401) {
			renvoyerVersConnexion();
			return;
		}
		if (saisie !== texte.value) return;
		if (!resultat.ok || !resultat.corps) {
			annoncer(resultat.detail ?? 'Impossible de calculer l’aperçu. Réessaie.', 'refuse');
			return;
		}
		apercu = decrireEnvoi(resultat.corps);
		afficherMode(resultat.corps.modeEnvoi);
		contactsVides.hidden = apercu.possible;
		parId('exemple-sms').textContent = resultat.corps.exemple;
		parId('apercu-destinataires').textContent = resultat.corps.destinataires;
		parId('apercu-segments').textContent = resultat.corps.segments;
		parId('apercu-cout').textContent = apercu.cout;
		parId('apercu-creneau').textContent = apercu.creneau;
		boutonAction.textContent = apercu.action;
		boutonAction.disabled = !apercu.possible;
		apercuVide.hidden = true;
		zoneApercu.hidden = false;
		annoncer('');
		actualiserBrouillon();
		parId('titre-apercu').focus({ preventScroll: true });
		zoneApercu.closest('section').scrollIntoView({ block: 'nearest' });
	});

	boutonAction.addEventListener('click', () => {
		if (!apercu?.possible || creationEnCours) return;
		parId('resume-validation').textContent = apercu.confirmation;
		parId('valider-campagne').textContent = apercu.action;
		ouvrirDialogue(dialogue, boutonAction);
	});

	dialogue.addEventListener('submit', async evenement => {
		if (evenement.submitter?.value !== 'valider') return;
		evenement.preventDefault();
		if (creationEnCours || !apercu?.possible) return;
		dialogue.close();
		creationEnCours = true;
		boutonAction.disabled = true;
		texte.readOnly = true;
		actualiserBrouillon();
		annoncer('Enregistrement de la Campagne…');
		const saisie = texte.value;
		if (tentative?.texte !== saisie) tentative = { texte: saisie, cle: nouvelleCle() };
		const resultat = await appelerAvecCsrf('POST', ROUTE, {
			corps: { texte: saisie }, entetes: { 'Idempotency-Key': tentative.cle },
		});
		texte.readOnly = false;
		if (resultat.statut === 401) {
			renvoyerVersConnexion();
			return;
		}
		if (resultat.ok && resultat.corps) {
			tentative = undefined;
			texte.value = '';
			effacerApercu();
			const chargees = await chargerCampagnes();
			const recu = resultat.corps.etat === 'PROGRAMMEE' ? 'Campagne programmée.' : 'Campagne enregistrée.';
			annoncer(chargees ? recu : `${recu} Recharge la page pour actualiser l’historique.`, 'accepte');
			parId('titre-campagnes').focus();
		} else {
			const incertain = resultat.statut === 0 || resultat.statut >= 500 || resultat.ok;
			annoncer(incertain ? 'Impossible de confirmer la Campagne. Réessaie avec ce texte pour retrouver la même demande.'
				: resultat.detail ?? 'Impossible d’enregistrer la Campagne. Réessaie.', 'refuse');
			if (resultat.code === 'AUCUN_CONTACT_ELIGIBLE') {
				contactsVides.hidden = false;
				effacerApercu();
				texte.focus();
			} else {
				boutonAction.disabled = false;
				boutonAction.focus();
			}
		}
		creationEnCours = false;
		actualiserBrouillon();
	});

	const session = await consulterSession();
	if (session.statut === 401) return;
	if (!session.ok || !session.corps) {
		annoncer('Impossible de vérifier ta session. Recharge la page.', 'refuse');
		return;
	}
	monterMenuCompte(document.querySelector('[data-menu-compte]'), { nom: session.corps.nom });
	if (await chargerCampagnes()) {
		parId('espace-campagnes').hidden = false;
		annoncer('');
		actualiserBrouillon();
	} else {
		annoncer('Impossible de charger les Campagnes. Recharge la page.', 'refuse');
	}
}

if (globalThis.document?.getElementById('espace-campagnes')) {
	void monterCampagnesSms(globalThis.document);
}
