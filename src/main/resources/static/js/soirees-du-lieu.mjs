// Soirées du Lieu de l'Animateur connecté (listerSoirees, #9) : rubriques et liens, sans règle de jeu.
import { appeler } from './api.js';
import { consulterSession, renvoyerVersConnexion } from './compte.mjs';
import { monterMenuCompte } from './menu-compte.mjs';

const ETATS = { PREPARATION: 'En préparation', OUVERTE: 'Ouverte', EN_COURS: 'En cours', TERMINEE: 'Terminée' };
const FORMATS = { SOLO: 'Solo', EQUIPE: 'Équipe' };
const PILOTAGES = { MANUEL: 'Pilotage manuel', AUTOMATIQUE: 'Pilotage automatique' };
const RUBRIQUES = [
	{ titre: 'En cours', etats: ['EN_COURS'] },
	{ titre: 'À venir', etats: ['PREPARATION', 'OUVERTE'] },
	{ titre: 'Passées', etats: ['TERMINEE'] },
];

/** Rubriques non vides ; chacune garde l'ordre du serveur, de la plus récemment créée à la plus ancienne. */
export function regrouperSoirees(soirees) {
	return RUBRIQUES.map(({ titre, etats }) => ({ titre, soirees: soirees.filter(({ etat }) => etats.includes(etat)) }))
		.filter(({ soirees: rubrique }) => rubrique.length > 0);
}

/** Avant le lancement, la préparation (ouverture, Écran de salle) ; ensuite, la Console. */
export function destinationDeSoiree({ id, etat }) {
	const parametres = new URLSearchParams({ soiree: id });
	if (etat === 'PREPARATION' || etat === 'OUVERTE') {
		return { adresse: `/preparer-soiree.html?${parametres}`, action: 'Préparer' };
	}
	return { adresse: `/console.html?${parametres}`, action: etat === 'EN_COURS' ? 'Ouvrir la Console' : 'Revoir dans la Console' };
}

// Le serveur garde le code après la fin, mais il ne sert à rejoindre qu'une Soirée ouverte ou en cours.
const ETATS_JOIGNABLES = new Set(['OUVERTE', 'EN_COURS']);

function decrire({ etat, format, pilotage, codeAcces }) {
	const code = codeAcces && ETATS_JOIGNABLES.has(etat) ? [`Code ${codeAcces}`] : [];
	return [ETATS[etat], FORMATS[format], PILOTAGES[pilotage], ...code].join(' · ');
}

function ligne(document, soiree) {
	const li = document.createElement('li');
	li.className = 'soirees__ligne';
	const texte = document.createElement('div');
	const titre = document.createElement('strong');
	titre.textContent = soiree.titre;
	const detail = document.createElement('small');
	detail.textContent = decrire(soiree);
	texte.append(titre, detail);
	const { adresse, action } = destinationDeSoiree(soiree);
	const lien = document.createElement('a');
	lien.className = 'bouton';
	lien.setAttribute('href', adresse);
	lien.setAttribute('aria-label', `${action} la Soirée « ${soiree.titre} »`);
	lien.textContent = action;
	li.append(texte, lien);
	return li;
}

/** Les titres sont saisis par les Animateurs : ils passent par textContent, jamais par innerHTML. */
export function afficherSoireesDuLieu(zone, soirees) {
	const document = zone.ownerDocument;
	const groupes = regrouperSoirees(soirees);
	if (groupes.length === 0) {
		const vide = document.createElement('p');
		vide.className = 'soirees__vide';
		vide.textContent = 'Aucune Soirée pour ce Lieu. Prépare la première.';
		zone.replaceChildren(vide);
		return;
	}
	zone.replaceChildren(...groupes.map(({ titre, soirees: rubrique }) => {
		const section = document.createElement('section');
		section.className = 'soirees__rubrique';
		const entete = document.createElement('h2');
		entete.textContent = titre;
		const liste = document.createElement('ul');
		liste.className = 'soirees__liste';
		liste.setAttribute('role', 'list');
		liste.append(...rubrique.map(soiree => ligne(document, soiree)));
		section.append(entete, liste);
		return section;
	}));
}

export async function monterSoireesDuLieu(document) {
	const zone = document.querySelector('[data-soirees]');
	try {
		await chargerSoirees(document, zone);
	} finally {
		// Succès, refus ou panne : la zone ne reste jamais annoncée comme en cours de chargement.
		zone.setAttribute('aria-busy', 'false');
	}
}

async function chargerSoirees(document, zone) {
	const statut = document.querySelector('[data-statut-soirees]');
	const session = await consulterSession();
	if (session.statut === 401) return;
	const menu = document.querySelector('[data-menu-compte]');
	if (menu && session.ok && session.corps?.nom) monterMenuCompte(menu, { nom: session.corps.nom });
	const liste = session.ok ? await appeler('GET', '/api/soirees') : session;
	if (liste.statut === 401) {
		renvoyerVersConnexion();
		return;
	}
	if (!liste.ok || !Array.isArray(liste.corps?.soirees)) {
		statut.className = 'envoi envoi--refuse';
		statut.textContent = liste.code === 'RESEAU_INDISPONIBLE'
			? 'Pas de connexion au serveur. Recharge la page dans un instant.'
			: 'Impossible de charger les Soirées du Lieu. Recharge la page.';
		statut.hidden = false;
		return;
	}
	statut.hidden = true;
	afficherSoireesDuLieu(zone, liste.corps.soirees);
}

const zonePage = globalThis.document?.querySelector('[data-soirees]');
if (zonePage) void monterSoireesDuLieu(globalThis.document);
