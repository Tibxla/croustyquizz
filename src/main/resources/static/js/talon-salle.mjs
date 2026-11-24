export function monterTalonSalle(talon, { soireeId, chargerQrCode }, {
	hote = globalThis.location.host, url = URL,
} = {}) {
	const document = talon.ownerDocument;
	const code = talon.querySelector('[data-code]');
	const qr = talon.querySelector('[data-qr]');
	const image = qr.querySelector('img');
	const adresse = talon.querySelector('[data-adresse]');
	const segments = [...hote.split(/(?=\.)/), '/rejoindre.html'];
	adresse.replaceChildren(...segments.flatMap((segment, index) => {
		const texte = document.createElement('span');
		texte.textContent = segment;
		return index === 0 ? [texte] : [document.createElement('wbr'), texte];
	}));

	let courant;
	function retirerQrCode() {
		if (!courant) return;
		const ancien = courant;
		courant = undefined;
		qr.hidden = adresse.hidden = true;
		image.removeAttribute('src');
		ancien.annulation.abort();
		if (ancien.adresse) url.revokeObjectURL(ancien.adresse);
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
		try {
			const blob = await chargerQrCode(soireeId, demande.annulation.signal);
			if (courant !== demande) return;
			demande.adresse = url.createObjectURL(blob);
			image.setAttribute('src', demande.adresse);
			await image.decode();
			if (courant === demande) qr.hidden = adresse.hidden = false;
		} catch {
			// La prochaine Vue synchronisée pourra réessayer. Le code d'accès reste lisible.
			if (courant === demande) retirerQrCode();
		}
	}

	return { afficher, demonter: retirerQrCode };
}
