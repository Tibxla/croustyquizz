import { creerImage, element, routePhotos } from './commun.mjs';

export function monter(zone, contexte, adaptations = {}) {
	const { cle = () => crypto.randomUUID() } = adaptations;
	const document = zone.ownerDocument;
	const titre = element(document, 'h2', { id: 'titre-photos' }, 'Photos de la salle');
	const interrupteur = element(document, 'input', { type: 'checkbox', role: 'switch', id: 'regler-photos', 'aria-labelledby': 'titre-photos' });
	const etiquette = element(document, 'label', { for: 'regler-photos' });
	const libelle = element(document, 'span', { 'aria-hidden': 'true' }, 'Envois ouverts');
	etiquette.append(interrupteur, libelle);
	const liste = element(document, 'ol', { class: 'photos__liste', role: 'list', 'aria-label': 'Photos en attente' });
	const vide = element(document, 'p', { class: 'console__note' }, 'Aucune photo en attente.');
	zone.setAttribute('aria-labelledby', 'titre-photos');
	zone.replaceChildren(titre, etiquette, liste, vide);
	zone.hidden = true;

	const lignes = new Map();
	const envois = new Map();
	const route = routePhotos(contexte.soireeId);
	const cibleReglage = 'reglages';
	let synchronisee = false;
	let ouvertes = false;

	function reglerCommandes() {
		interrupteur.disabled = !synchronisee || envois.has(cibleReglage);
		for (const [photoId, ligne] of lignes) {
			const indisponible = !synchronisee || envois.has(photoId);
			ligne.accepter.disabled = indisponible || !ligne.vignettePrete;
			ligne.refuser.disabled = indisponible;
		}
	}
	const desabonner = contexte.surEtatConnexion(etat => {
		synchronisee = etat === 'synchronisee';
		reglerCommandes();
	});

	async function envoyer(cible, methode, chemin, corps) {
		if (!synchronisee || envois.has(cible) || zone.hidden) return;
		const envoi = new AbortController();
		envois.set(cible, envoi);
		reglerCommandes();
		try {
			await contexte.envoyer(methode, chemin, corps, { entetes: { 'Idempotency-Key': cle() }, signal: envoi.signal });
		} catch {
			// Le bandeau de la Console porte l’état de la commande.
		} finally {
			envois.delete(cible);
			reglerCommandes();
		}
	}
	function changerReglage() {
		const demande = interrupteur.checked;
		interrupteur.checked = ouvertes;
		void envoyer(cibleReglage, 'PUT', `${route}/reglages`, { ouvertes: demande });
	}
	interrupteur.addEventListener('change', changerReglage);

	function ajouter({ photoId, pseudo }) {
		const noeud = element(document, 'li', { class: 'photos__ligne' });
		const image = element(document, 'img', { class: 'photos__vignette', alt: '' });
		const message = element(document, 'span', { class: 'photos__message', role: 'status' });
		const commandes = element(document, 'div', { class: 'photos__commandes' });
		const accepter = element(document, 'button', { type: 'button', class: 'bouton',
			'aria-label': `Accepter la photo de ${pseudo}` }, 'Accepter');
		const refuser = element(document, 'button', { type: 'button', class: 'bouton',
			'aria-label': `Refuser la photo de ${pseudo}` }, 'Refuser');
		commandes.append(accepter, refuser);
		noeud.append(image, element(document, 'strong', {}, pseudo), commandes, message);

		const echecVignette = () => {
			ligne.vignettePrete = false;
			message.textContent = 'Vignette indisponible.';
			reglerCommandes();
		};
		const chargeur = creerImage(image, contexte, { ...adaptations, surErreur: echecVignette });
		const ligne = { noeud, accepter, refuser, vignettePrete: false, chargeur };
		lignes.set(photoId, ligne);
		image.addEventListener('load', () => {
			ligne.vignettePrete = true;
			message.textContent = '';
			reglerCommandes();
		});
		image.addEventListener('error', echecVignette);
		const moderer = acceptee => envoyer(photoId, 'POST', `${route}/${photoId}/moderation`, { acceptee });
		accepter.addEventListener('click', () => { if (ligne.vignettePrete) void moderer(true); });
		refuser.addEventListener('click', () => void moderer(false));
		liste.append(noeud);
		void chargeur.charger(photoId);
	}

	return {
		afficher(vue) {
			const bloc = vue.photos;
			zone.hidden = !bloc || !['OUVERTE', 'EN_COURS'].includes(vue.soiree.etat);
			ouvertes = bloc?.ouvertes === true;
			interrupteur.checked = ouvertes;
			libelle.textContent = ouvertes ? 'Envois ouverts' : 'Envois coupés';
			const attente = zone.hidden ? [] : bloc.enAttente;
			const ids = new Set(attente.map(photo => photo.photoId));
			for (const photo of attente) if (!lignes.has(photo.photoId)) ajouter(photo);
			let rendreFocus = false;
			for (const [id, ligne] of lignes) {
				if (ids.has(id)) continue;
				rendreFocus ||= ligne.noeud.contains(document.activeElement);
				ligne.chargeur.effacer();
				ligne.noeud.remove();
				lignes.delete(id);
			}
			liste.hidden = attente.length === 0;
			vide.hidden = !liste.hidden;
			reglerCommandes();
			if (rendreFocus && !zone.hidden && synchronisee) interrupteur.focus();
		},
		demonter() {
			for (const envoi of envois.values()) envoi.abort();
			for (const ligne of lignes.values()) ligne.chargeur.effacer();
			lignes.clear();
			interrupteur.removeEventListener('change', changerReglage);
			desabonner();
			zone.replaceChildren();
			zone.hidden = true;
		},
	};
}
