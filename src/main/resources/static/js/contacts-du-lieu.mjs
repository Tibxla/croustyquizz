import { appeler as appelerApi } from './api.js';
import { consulterSession, renvoyerVersConnexion } from './compte.mjs';
import { monterMenuCompte } from './menu-compte.mjs';

const calendrier = new Intl.DateTimeFormat('fr-FR', {
	dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Paris',
});

function ligne(document, contact) {
	const rangee = document.createElement('tr');
	const nom = document.createElement('th');
	nom.setAttribute('scope', 'row');
	nom.textContent = `${contact.prenom} ${contact.nom}`;
	const numero = document.createElement('td');
	numero.className = 'contacts-liste__numero';
	numero.textContent = contact.numero;
	const date = document.createElement('td');
	const instant = document.createElement('time');
	instant.setAttribute('datetime', contact.consentiLe);
	instant.textContent = calendrier.format(new Date(contact.consentiLe));
	date.append(instant);

	const preuve = document.createElement('td');
	const details = document.createElement('details');
	const titre = document.createElement('summary');
	titre.textContent = 'Texte accepté';
	const mention = document.createElement('p');
	mention.textContent = contact.mention;
	details.append(titre, mention);
	preuve.append(details);
	rangee.append(nom, numero, date, preuve);
	return rangee;
}

export async function monterContactsDuLieu(document, {
	consulter = consulterSession, appeler = appelerApi, versConnexion = renvoyerVersConnexion,
} = {}) {
	const statut = document.querySelector('[data-statut-contacts]');
	const tableau = document.querySelector('[data-tableau-contacts]');
	const corps = tableau.querySelector('tbody');
	const exportCsv = document.querySelector('[data-export-contacts]');
	statut.textContent = 'Chargement des Contacts…';
	tableau.hidden = true;
	exportCsv.hidden = true;
	corps.replaceChildren();

	const session = await consulter();
	if (session.statut === 401) return;
	const menu = document.querySelector('[data-menu-compte]');
	if (session.ok && menu && session.corps?.nom && !menu.querySelector('button')) {
		monterMenuCompte(menu, { nom: session.corps.nom });
	}
	const liste = session.ok ? await appeler('GET', '/api/contacts') : session;
	if (liste.statut === 401) {
		versConnexion();
		return;
	}
	if (!liste.ok || !Array.isArray(liste.corps?.contacts)) {
		statut.textContent = 'Impossible de charger les Contacts du Lieu. Recharge la page pour réessayer.';
		return;
	}

	const contacts = liste.corps.contacts;
	exportCsv.hidden = false;
	tableau.hidden = contacts.length === 0;
	if (contacts.length === 0) {
		statut.textContent = 'Aucun Contact actif. Les Joueurs peuvent s’inscrire depuis leur Manette.';
		return;
	}
	statut.textContent = contacts.length === 1 ? '1 Contact actif' : `${contacts.length} Contacts actifs`;
	for (const contact of contacts) corps.append(ligne(document, contact));
}

if (globalThis.document?.querySelector('[data-contacts-du-lieu]')) {
	void monterContactsDuLieu(document);
	addEventListener('pageshow', evenement => {
		if (evenement.persisted) void monterContactsDuLieu(document);
	});
}
