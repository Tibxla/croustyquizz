/** Le programme monte ces réglages avant le démarrage de la Manche. */
export function monterReglagesFreestyle(zone, contexte, actualiser) {
	const element = nom => zone.querySelector(`[data-${nom}]`);
	const fiches = element('instrumentales');
	const message = element('catalogue-etat');
	const rafraichir = element('catalogue-actualiser');
	const route = `/api/soirees/${encodeURIComponent(contexte.soireeId)}/freestyle/instrumentales`;
	let affiche = false, synchronisee = false, lecture, prete = false;

	function selection() {
		return [...fiches.querySelectorAll('input')].filter(champ => champ.checked).map(champ => champ.value);
	}
	function rendre() {
		zone.hidden = !affiche;
		zone.disabled = !affiche || !synchronisee;
		rafraichir.disabled = !synchronisee || Boolean(lecture);
		actualiser();
	}
	async function charger() {
		if (!affiche || !synchronisee || lecture) return;
		const controle = lecture = new AbortController();
		message.textContent = 'Consultation des Instrumentales…';
		rendre();
		try {
			const { statut, corps } = await contexte.envoyer('GET', route, undefined, { signal: controle.signal });
			if (controle.signal.aborted) return;
			if (statut !== 200) {
				prete = false;
				message.textContent = corps?.detail ?? 'Le catalogue est indisponible.';
				return;
			}
			const avant = new Set(selection());
			const document = zone.ownerDocument;
			fiches.replaceChildren(...corps.instrumentales.map(fiche => {
				const label = document.createElement('label');
				const choix = document.createElement('input');
				choix.type = 'checkbox';
				choix.value = fiche.instrumentaleId;
				choix.checked = avant.has(choix.value);
				const texte = document.createElement('span');
				texte.textContent = `${fiche.titre} · ${fiche.tempoBpm} BPM`;
				label.append(choix, texte);
				return label;
			}));
			prete = true;
			message.textContent = corps.instrumentales.length ? '' : 'Choisis le dossier de musique sur l’Écran de salle, puis actualise le catalogue.';
		} catch {
			if (!controle.signal.aborted) {
				prete = false;
				message.textContent = 'Le catalogue est indisponible. Actualise pour réessayer.';
			}
		} finally {
			if (!controle.signal.aborted) { lecture = undefined; rendre(); }
		}
	}
	function annuler() {
		lecture?.abort();
		lecture = undefined;
	}
	fiches.addEventListener('change', actualiser);
	rafraichir.addEventListener('click', charger);
	return {
		afficher(valeur, connectee) {
			const venaitDeChanger = valeur !== affiche || connectee !== synchronisee;
			affiche = valeur;
			synchronisee = connectee;
			if (!affiche || !synchronisee) annuler();
			else if (venaitDeChanger) void charger();
			rendre();
		},
		peutAjouter: () => prete && !lecture && selection().length > 0,
		valeurs: () => ({ theme: element('theme-rimes').value, schemaDeRimes: element('schema-rimes').value,
			difficulte: element('difficulte-rimes').value, nombreMesures: Number(element('nombre-mesures').value),
			mesuresParCouplet: Number(element('mesures-couplet').value), instrumentaleIds: selection() }),
		demonter() {
			annuler();
			fiches.removeEventListener('change', actualiser);
			rafraichir.removeEventListener('click', charger);
		},
	};
}
