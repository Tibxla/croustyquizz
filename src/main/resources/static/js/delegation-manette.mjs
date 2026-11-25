// Section « Ta Délégation » de la Manette (#67) : les pouvoirs que l'Animateur a confiés à ce Joueur et
// leurs commandes, sur les routes de l'Animateur. Aucune règle de jeu : les droits (peutAppeler, peutPasser,
// peutGererFile) viennent de la vue, le serveur tranche chaque commande et la coquille annonce le reçu.
import { preparerPlacement } from './modes/karaoke/placement.mjs';
import { creerImage, element } from './photos/commun.mjs';

const NOMS = { FILE_ATTENTE: 'la File d’attente', MUSIQUE_ATTENTE: 'la Musique d’attente', PHOTOS: 'les photos' };

/** Phrase qui nomme les pouvoirs reçus, dans l'ordre du serveur. */
export function decrirePouvoirs(pouvoirs) {
	const noms = pouvoirs.map(pouvoir => NOMS[pouvoir]).filter(Boolean);
	if (!noms.length) return '';
	const liste = noms.length === 1 ? noms[0] : `${noms.slice(0, -1).join(', ')} et ${noms.at(-1)}`;
	return `L’Animateur t’a confié ${liste}.`;
}

/** Commandes de la File d'attente que la vue autorise, sans règle de jeu ajoutée. */
export function commandesFile(file, soireeId) {
	if (!file) return { appel: null, passage: null, gestes: new Map() };
	const racine = `/api/soirees/${soireeId}/manches/${file.mancheId}`;
	// Comme la Console, seul le Passage en position 1 s'appelle : sans lui, pas de bouton plutôt qu'un 409.
	const tete = file.passagesEnAttente.find(p => p.position === 1);
	const appel = file.peutAppeler && tete ? { methode: 'POST', chemin: `${racine}/passages/${tete.passageId}/appel`,
		corps: { numeroModification: tete.numeroModification }, cible: `appel:${tete.passageId}` } : null;
	const passage = file.peutPasser && file.courant?.etapeId ? { methode: 'POST', chemin: `${racine}/commandes`,
		corps: { action: 'PASSER_ETAPE', etapeId: file.courant.etapeId }, cible: `passer:${file.courant.etapeId}` } : null;
	const gestes = new Map();
	if (file.peutGererFile) {
		for (const p of file.passagesEnAttente) {
			const liste = [];
			for (const [geste, libelle] of [['monter', 'Monter'], ['descendre', 'Descendre']]) {
				const corps = preparerPlacement(file.passagesEnAttente, p.passageId, geste);
				if (corps) liste.push({ libelle, methode: 'PUT', chemin: `${racine}/passages/${p.passageId}/placement`, corps,
					cible: `${geste}:${p.passageId}` });
			}
			liste.push({ libelle: p.prioritaire ? 'Lever la priorité' : 'Priorité', methode: 'PUT',
				chemin: `${racine}/file-attente/priorite`, cible: `priorite:${p.passageId}`,
				corps: { passage: { passageId: p.passageId, numeroModification: p.numeroModification }, prioritaire: !p.prioritaire } });
			gestes.set(p.passageId, liste);
		}
	}
	return { appel, passage, gestes };
}

const titreChanson = p => `n° ${p.numero} · ${p.chanson.titre} · ${p.interpretes.map(i => i.pseudo).join(', ')}`;
const ETATS_COURANT = { APPELE: 'appelé', EN_COURS: 'sur scène', EN_PAUSE: 'en pause', TERMINE: 'terminé', SAUTE: 'passé' };

/**
 * Monte la section dans `zone`. `refuge` reçoit le focus quand la Délégation est retirée alors que le focus
 * était dans la section. Rend `{ afficher(vue), demonter() }`.
 */
export function monterDelegation(zone, contexte, { refuge = null, cle = () => crypto.randomUUID(), ...adaptations } = {}) {
	const document = zone.ownerDocument;
	const titre = element(document, 'h2', { id: 'titre-delegation', tabindex: '-1' }, 'Ta Délégation');
	const resume = element(document, 'p', { class: 'manette__aide' });
	const corps = element(document, 'div', { class: 'delegation__corps' });
	zone.setAttribute('aria-labelledby', 'titre-delegation');
	zone.replaceChildren(titre, resume, corps);
	zone.hidden = true;

	const envois = new Map();
	const photos = new Map();
	let synchronisee = false;

	const desabonner = contexte.surEtatConnexion(etat => {
		synchronisee = etat === 'synchronisee';
		reglerCommandes();
	});

	function reglerCommandes() {
		for (const bouton of zone.querySelectorAll('[data-cible]')) {
			const cible = bouton.getAttribute('data-cible');
			const pretePhoto = !cible.startsWith('accepter:') || photos.get(cible.slice('accepter:'.length))?.prete;
			bouton.setAttribute('aria-disabled', String(!synchronisee || envois.has(cible) || !pretePhoto));
		}
	}

	async function envoyer({ methode, chemin, corps: contenu, cible }) {
		if (!synchronisee || envois.has(cible) || zone.hidden) return;
		const envoi = new AbortController();
		envois.set(cible, envoi);
		reglerCommandes();
		try {
			await contexte.envoyer(methode, chemin, contenu, { entetes: { 'Idempotency-Key': cle() }, signal: envoi.signal });
		} catch {
			// La coquille annonce l'envoi, son reçu ou son refus ; aucune commande n'est rejouée seule.
		} finally {
			envois.delete(cible);
			reglerCommandes();
		}
	}

	// Boutons ordinaires : l'action citron de la Manette reste celle du jeu (« Je suis prêt », le vote).
	function bouton(commande, { etiquette } = {}) {
		const noeud = element(document, 'button', { type: 'button', class: 'bouton',
			'data-cible': commande.cible, ...(etiquette ? { 'aria-label': etiquette } : {}) }, commande.libelle);
		noeud.addEventListener('click', () => {
			if (noeud.getAttribute('aria-disabled') !== 'true') void envoyer(commande);
		});
		return noeud;
	}

	function blocFile(file) {
		const bloc = element(document, 'section', { class: 'delegation__bloc', 'aria-labelledby': 'delegation-file' });
		bloc.append(element(document, 'h3', { id: 'delegation-file' }, 'File d’attente'));
		if (!file) {
			bloc.append(element(document, 'p', { class: 'manette__aide' }, 'Rien à gérer hors d’une Manche de Karaoké.'));
			return bloc;
		}
		const { appel, passage, gestes } = commandesFile(file, contexte.soireeId);
		const courant = file.courant;
		bloc.append(element(document, 'p', {}, courant
			? `En scène : ${titreChanson(courant)} (${ETATS_COURANT[courant.etat] ?? courant.etat})` : 'Personne sur scène.'));
		const commandes = element(document, 'div', { class: 'photos__commandes' });
		if (appel) commandes.append(bouton({ ...appel, libelle: 'Appeler le suivant' }));
		if (passage) commandes.append(bouton({ ...passage, libelle: 'Passer' }, { etiquette: `Passer ${courant.chanson.titre}` }));
		if (commandes.children.length) bloc.append(commandes);
		if (!file.passagesEnAttente.length) {
			bloc.append(element(document, 'p', { class: 'manette__aide' }, 'Aucun Passage en attente.'));
			return bloc;
		}
		const liste = element(document, 'ol', { class: 'photos__liste', role: 'list', 'aria-label': 'Passages en attente' });
		for (const p of file.passagesEnAttente) {
			const ligne = element(document, 'li', { class: 'photos__ligne delegation__passage' });
			ligne.append(element(document, 'strong', {}, `${p.position}. ${titreChanson(p)}${p.prioritaire ? ' · prioritaire' : ''}`));
			const actions = element(document, 'div', { class: 'photos__commandes' });
			for (const geste of gestes.get(p.passageId) ?? []) {
				actions.append(bouton(geste, { etiquette: `${geste.libelle} : ${p.chanson.titre}` }));
			}
			if (actions.children.length) ligne.append(actions);
			liste.append(ligne);
		}
		bloc.append(liste);
		return bloc;
	}

	function blocMusique(musique) {
		const bloc = element(document, 'section', { class: 'delegation__bloc', 'aria-labelledby': 'delegation-musique' });
		bloc.append(element(document, 'h3', { id: 'delegation-musique' }, 'Musique d’attente'));
		const joue = musique?.etat === 'JOUE';
		bloc.append(element(document, 'p', {}, joue ? 'La musique d’attente joue entre les sons de Manche.' : 'Musique d’attente coupée.'));
		bloc.append(bouton({ libelle: joue ? 'Couper la musique' : 'Relancer la musique', methode: 'PUT',
			chemin: `/api/soirees/${contexte.soireeId}/musique-attente`, corps: { etat: joue ? 'COUPEE' : 'JOUE' },
			cible: 'musique' }));
		return bloc;
	}

	function lignePhoto({ photoId, pseudo }) {
		const existante = photos.get(photoId);
		if (existante) return existante.noeud;
		const noeud = element(document, 'li', { class: 'photos__ligne' });
		const image = element(document, 'img', { class: 'photos__vignette', alt: `Photo de ${pseudo}` });
		const message = element(document, 'span', { class: 'photos__message' });
		const route = `/api/soirees/${contexte.soireeId}/photos/${photoId}/moderation`;
		const commandes = element(document, 'div', { class: 'photos__commandes' });
		commandes.append(
			bouton({ libelle: 'Accepter', methode: 'POST', chemin: route, corps: { acceptee: true }, cible: `accepter:${photoId}` },
				{ etiquette: `Accepter la photo de ${pseudo}` }),
			bouton({ libelle: 'Refuser', methode: 'POST', chemin: route, corps: { acceptee: false }, cible: `refuser:${photoId}` },
				{ etiquette: `Refuser la photo de ${pseudo}` }));
		noeud.append(image, element(document, 'strong', {}, pseudo), commandes, message);
		const ligne = { noeud, prete: false };
		const echec = () => { ligne.prete = false; message.textContent = 'Photo indisponible.'; reglerCommandes(); };
		ligne.chargeur = creerImage(image, contexte, { ...adaptations, surErreur: echec });
		image.addEventListener('load', () => { ligne.prete = true; message.textContent = ''; reglerCommandes(); });
		image.addEventListener('error', echec);
		photos.set(photoId, ligne);
		void ligne.chargeur.charger(photoId);
		return noeud;
	}

	function blocPhotos(enAttente) {
		const bloc = element(document, 'section', { class: 'delegation__bloc', 'aria-labelledby': 'delegation-photos' });
		bloc.append(element(document, 'h3', { id: 'delegation-photos' }, 'Photos à valider'));
		const visibles = new Set(enAttente.map(photo => photo.photoId));
		for (const [photoId, ligne] of photos) {
			if (!visibles.has(photoId)) { ligne.chargeur.effacer(); photos.delete(photoId); }
		}
		if (!enAttente.length) {
			bloc.append(element(document, 'p', { class: 'manette__aide' }, 'Aucune photo en attente.'));
			return bloc;
		}
		const liste = element(document, 'ol', { class: 'photos__liste', role: 'list', 'aria-label': 'Photos en attente' });
		for (const photo of enAttente) liste.append(lignePhoto(photo));
		bloc.append(liste);
		return bloc;
	}

	function viderPhotos() {
		for (const ligne of photos.values()) ligne.chargeur.effacer();
		photos.clear();
	}

	return {
		afficher(vue) {
			const delegation = vue.delegation ?? null;
			const actif = document.activeElement;
			const avaitFocus = zone.contains(actif) && actif !== zone;
			const cibleFocus = avaitFocus ? actif.getAttribute?.('data-cible') : null;
			if (!delegation) {
				const etaitVisible = !zone.hidden;
				for (const envoi of envois.values()) envoi.abort();
				viderPhotos();
				corps.replaceChildren();
				resume.textContent = '';
				zone.hidden = true;
				// La section disparaît au retrait : le focus revient à un endroit sûr, hors de la section.
				if (etaitVisible && avaitFocus) refuge?.focus();
				return;
			}
			zone.hidden = false;
			resume.textContent = decrirePouvoirs(delegation.pouvoirs);
			const blocs = [];
			if (delegation.pouvoirs.includes('FILE_ATTENTE')) blocs.push(blocFile(delegation.fileAttente ?? null));
			if (delegation.pouvoirs.includes('MUSIQUE_ATTENTE')) blocs.push(blocMusique(delegation.musiqueAttente));
			if (delegation.pouvoirs.includes('PHOTOS')) blocs.push(blocPhotos(delegation.photos?.enAttente ?? []));
			else viderPhotos();
			corps.replaceChildren(...blocs);
			reglerCommandes();
			if (avaitFocus) {
				const meme = cibleFocus && zone.querySelector(`[data-cible="${cibleFocus}"]`);
				(meme || titre).focus();
			}
		},
		demonter() {
			for (const envoi of envois.values()) envoi.abort();
			envois.clear();
			viderPhotos();
			desabonner();
			zone.replaceChildren();
			zone.hidden = true;
		},
	};
}
