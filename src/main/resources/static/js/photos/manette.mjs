import { element, routePhotos } from './commun.mjs';

const COTE_MAX = 1600;
const OCTETS_MAX = 1_500_000;
const QUALITE_JPEG = 0.82;
const REDUCTION = 0.8;
const ILLISIBLE = 'Cette image est illisible. Choisis une autre photo.';

export async function preparerJpeg(fichier, {
	bitmap = (source, options) => createImageBitmap(source, options),
	canvas = () => document.createElement('canvas'),
} = {}) {
	let image;
	try {
		image = await bitmap(fichier, { imageOrientation: 'from-image' });
		const toile = canvas();
		const dessin = toile.getContext('2d', { alpha: false });
		let echelle = Math.min(1, COTE_MAX / Math.max(image.width, image.height));
		do {
			toile.width = Math.max(1, Math.floor(image.width * echelle));
			toile.height = Math.max(1, Math.floor(image.height * echelle));
			dessin.drawImage(image, 0, 0, toile.width, toile.height);
			const jpeg = await new Promise(resolve => toile.toBlob(resolve, 'image/jpeg', QUALITE_JPEG));
			if (!jpeg) throw new Error(ILLISIBLE);
			if (jpeg.size <= OCTETS_MAX) return jpeg;
			echelle *= REDUCTION;
		} while (Math.max(toile.width, toile.height) > 1);
	} catch {
		throw new Error(ILLISIBLE);
	} finally {
		image?.close();
	}
	throw new Error('Cette photo reste trop lourde. Choisis une autre image.');
}

function memeFichier(a, b) {
	return a === b || (a?.name !== undefined && a.name === b?.name && a.size === b?.size
		&& a.lastModified === b?.lastModified && a.type === b?.type);
}

export function monter(zone, contexte, { cle = () => crypto.randomUUID(), preparer = preparerJpeg } = {}) {
	const document = zone.ownerDocument;
	// Sans capture : le téléphone propose l’appareil photo et la photothèque.
	const champ = element(document, 'input', {
		type: 'file', id: 'photo-a-envoyer', accept: 'image/*', 'aria-describedby': 'etat-photo',
	});
	const etiquette = element(document, 'label', { for: 'photo-a-envoyer' }, 'Envoyer une photo');
	const statut = element(document, 'p', { id: 'etat-photo', role: 'status' });
	const groupe = element(document, 'div', { class: 'champ photos__champ' });
	groupe.append(etiquette, champ, statut);
	zone.replaceChildren(groupe);
	zone.hidden = true;

	let synchronisee = false;
	let ouvertes = false;
	let envoi;
	// Envoi sans confirmation : même fichier, même JPEG et même clé jusqu’au succès.
	let enSuspens = null;
	const indisponible = () => !synchronisee || Boolean(envoi);
	function reglerChamp() {
		// aria-disabled garde le focus sur le champ pendant l’envoi.
		champ.setAttribute('aria-disabled', String(indisponible()));
		etiquette.hidden = champ.hidden = !ouvertes;
		// Envois coupés : la réponse d’un envoi déjà parti reste lisible.
		zone.hidden = !ouvertes && !envoi && !statut.textContent;
	}
	function annoncer(texte = '', classe = '') {
		statut.className = classe;
		statut.textContent = texte;
		if (classe === 'champ--erreur') champ.setAttribute('aria-invalid', 'true');
		else champ.removeAttribute('aria-invalid');
	}
	const desabonner = contexte.surEtatConnexion(etat => {
		synchronisee = etat === 'synchronisee';
		reglerChamp();
	});

	function bloquerSelecteur(evenement) {
		if (indisponible() || !ouvertes) evenement.preventDefault();
	}
	async function envoyer() {
		const fichier = champ.files?.[0];
		champ.value = '';
		if (!fichier || indisponible() || !ouvertes) return;
		const operation = new AbortController();
		envoi = operation;
		reglerChamp();
		try {
			let tentative = enSuspens && memeFichier(enSuspens.fichier, fichier) ? enSuspens : null;
			if (!tentative) {
				enSuspens = null;
				annoncer('Préparation de la photo…');
				const jpeg = await preparer(fichier);
				if (operation.signal.aborted) return;
				tentative = { fichier, jpeg, cle: cle() };
			}
			if (!synchronisee) throw new Error('Connexion perdue. Choisis à nouveau ta photo après la reconnexion.');
			if (!ouvertes) throw new Error('L’Animateur a coupé les envois de photos.');
			enSuspens = tentative;
			annoncer('Envoi de la photo…');
			let reponse;
			try {
				reponse = await contexte.envoyer('POST', routePhotos(contexte.soireeId), tentative.jpeg, {
					entetes: { 'Idempotency-Key': tentative.cle }, signal: operation.signal, annoncer: false,
				});
			} catch {
				throw new Error('Impossible de confirmer l’envoi de la photo. Choisis la même photo pour réessayer.');
			}
			if (operation.signal.aborted) return;
			if (reponse.statut !== 201 || !reponse.corps?.photoId) {
				throw new Error(reponse.corps?.detail ?? 'Impossible de confirmer l’envoi de la photo.');
			}
			enSuspens = null;
			annoncer('Photo envoyée. En attente de validation.', 'envoi envoi--accepte');
		} catch (erreur) {
			if (!operation.signal.aborted) annoncer(erreur.message, 'champ--erreur');
		} finally {
			if (envoi === operation) {
				envoi = null;
				reglerChamp();
			}
		}
	}
	champ.addEventListener('click', bloquerSelecteur);
	champ.addEventListener('change', envoyer);
	return {
		afficher(vue) {
			const etaient = ouvertes;
			ouvertes = vue.photos?.ouvertes === true;
			// Couper ne retire pas un envoi déjà parti : seul le champ disparaît.
			if (etaient !== ouvertes && !envoi) annoncer();
			reglerChamp();
		},
		demonter() {
			envoi?.abort();
			champ.removeEventListener('click', bloquerSelecteur);
			champ.removeEventListener('change', envoyer);
			desabonner();
			zone.replaceChildren();
			zone.hidden = true;
		},
	};
}
