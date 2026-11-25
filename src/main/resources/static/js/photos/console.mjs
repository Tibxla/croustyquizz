import { creerImage, element, routePhotos } from './commun.mjs';

export function monter(zone, contexte, adaptations = {}) {
	const { cle = () => crypto.randomUUID() } = adaptations;
	const document = zone.ownerDocument;
	// tabindex="-1" : le focus y revient quand la dernière photo est modérée.
	const titre = element(document, 'h2', { id: 'titre-photos', tabindex: '-1' }, 'Photos de la salle');
	const interrupteur = element(document, 'input', { type: 'checkbox', role: 'switch', id: 'regler-photos' });
	// Libellé fixe, nom accessible de l’interrupteur (WCAG 2.5.3) ; l’état est celui de la case.
	const etiquette = element(document, 'label', { for: 'regler-photos' });
	etiquette.append(interrupteur, element(document, 'span', {}, 'Recevoir les photos des Joueurs'));
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

	// aria-disabled plutôt que disabled : le focus reste sur la commande pendant l’envoi.
	const desactiver = (commande, valeur) => commande.setAttribute('aria-disabled', String(valeur));
	const indisponible = cible => !synchronisee || envois.has(cible);
	function reglerCommandes() {
		desactiver(interrupteur, indisponible(cibleReglage));
		for (const [photoId, ligne] of lignes) {
			desactiver(ligne.accepter, indisponible(photoId) || !ligne.vignettePrete);
			desactiver(ligne.refuser, indisponible(photoId));
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
		if (indisponible(cibleReglage)) return;
		void envoyer(cibleReglage, 'PUT', `${route}/reglages`, { ouvertes: demande });
	}
	function bloquerReglage(evenement) {
		if (indisponible(cibleReglage)) evenement.preventDefault();
	}
	interrupteur.addEventListener('click', bloquerReglage);
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
		accepter.addEventListener('click', () => { if (ligne.vignettePrete && !indisponible(photoId)) void moderer(true); });
		refuser.addEventListener('click', () => { if (!indisponible(photoId)) void moderer(false); });
		liste.append(noeud);
		void chargeur.charger(photoId);
	}

	return {
		afficher(vue) {
			const bloc = vue.photos;
			zone.hidden = !bloc || !['OUVERTE', 'EN_COURS'].includes(vue.soiree.etat);
			ouvertes = bloc?.ouvertes === true;
			interrupteur.checked = ouvertes;
			const attente = zone.hidden ? [] : bloc.enAttente;
			const ids = new Set(attente.map(photo => photo.photoId));
			for (const photo of attente) if (!lignes.has(photo.photoId)) ajouter(photo);
			// Ligne retirée qui avait le focus : la suivante, sinon la précédente, reprend la même commande.
			const actif = document.activeElement;
			const ordre = [...lignes.keys()];
			const position = ordre.findIndex(id => lignes.get(id).noeud.contains(actif));
			const perdue = position >= 0 && !ids.has(ordre[position]);
			const action = perdue && lignes.get(ordre[position]).refuser === actif ? 'refuser' : 'accepter';
			const suivante = perdue ? ordre.slice(position + 1).find(id => ids.has(id))
				?? ordre.slice(0, position).findLast(id => ids.has(id)) : undefined;
			for (const [id, ligne] of lignes) {
				if (ids.has(id)) continue;
				ligne.chargeur.effacer();
				ligne.noeud.remove();
				lignes.delete(id);
			}
			liste.hidden = attente.length === 0;
			vide.hidden = !liste.hidden;
			reglerCommandes();
			if (perdue && !zone.hidden) (suivante ? lignes.get(suivante)[action] : titre).focus();
		},
		demonter() {
			for (const envoi of envois.values()) envoi.abort();
			for (const ligne of lignes.values()) ligne.chargeur.effacer();
			lignes.clear();
			interrupteur.removeEventListener('click', bloquerReglage);
			interrupteur.removeEventListener('change', changerReglage);
			desabonner();
			zone.replaceChildren();
			zone.hidden = true;
		},
	};
}
