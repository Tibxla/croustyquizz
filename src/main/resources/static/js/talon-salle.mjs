export function monterTalonSalle(talon, { soireeId, chargerQrCode }, { url = URL } = {}) {
	const document = talon.ownerDocument;
	const code = talon.querySelector('[data-code]');
	const qr = talon.querySelector('[data-qr]');
	const image = qr.querySelector('img');
	const adresse = talon.querySelector('[data-adresse]');

	function afficherAdresse(adresseManette) {
		// Le schéma n'aide pas à 5 m : le téléphone complète l'adresse tapée, et le QR code porte l'adresse complète.
		const { host, pathname } = new URL(adresseManette);
		const segments = [...host.split(/(?=\.)/), ...pathname.split(/(?=\/)/)];
		adresse.replaceChildren(...segments.flatMap((segment, index) => {
			const texte = document.createElement('span');
			texte.textContent = segment;
			return index === 0 ? [texte] : [document.createElement('wbr'), texte];
		}));
	}

	let courant;
	function retirerImage(demande) {
		qr.hidden = adresse.hidden = true;
		image.removeAttribute('src');
		if (demande.urlImage) {
			url.revokeObjectURL(demande.urlImage);
			demande.urlImage = undefined;
		}
	}

	function retirerQrCode() {
		if (!courant) return;
		const ancien = courant;
		courant = undefined;
		ancien.annulation.abort();
		retirerImage(ancien);
	}

	async function afficher(soiree) {
		const ouverteAuxJoueurs = soiree.etat === 'OUVERTE' || soiree.etat === 'EN_COURS';
		const codeAcces = ouverteAuxJoueurs ? soiree.codeAcces : null;
		talon.hidden = !codeAcces;
		code.textContent = codeAcces ?? '';
		if (courant?.code === codeAcces) return;
		retirerQrCode();
		if (!codeAcces) return;

		const demande = { code: codeAcces, annulation: new AbortController() };
		courant = demande;
		const reponse = await chargerQrCode(soireeId, demande.annulation.signal).catch(() => {
			// Une panne réseau autorise un nouvel essai à la Vue suivante.
			if (courant === demande) retirerQrCode();
			return null;
		});
		if (courant !== demande || !reponse) return;
		try {
			demande.urlImage = url.createObjectURL(reponse.blob);
			image.setAttribute('src', demande.urlImage);
			await image.decode();
			if (courant !== demande) return;
			afficherAdresse(reponse.adresseManette);
			qr.hidden = adresse.hidden = false;
		} catch {
			if (courant === demande) retirerImage(demande);
		}
	}

	return { afficher, demonter: retirerQrCode };
}
