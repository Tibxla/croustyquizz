const FORMATS = ['audio/webm;codecs=opus', 'audio/mp4'];
const enPrise = etat => etat === 'enregistrement' || etat === 'pause';
const libererPistes = flux => flux?.getTracks().forEach(piste => piste.stop());

// Une instance par Passage. Aucune dépendance au DOM ou aux échanges réseau.
// Le micro ne dépend pas du réseau : une coupure ne touche pas la prise, la vue suivante la réconcilie.
// Seul l'événement passage-demarre lance la prise ; une capture ne relance jamais une prise arrêtée.
export function creerEnregistrement({ demanderMicro, Enregistreur, surEtat }) {
	let etat = 'inactif', motif, fichier, flux, recorder, fragments, format, generation = 0;
	let phase = 'termine', accord = false, detruit = false;
	const lire = () => ({ etat, motif, fichier });
	function changer(suivant, raison) {
		if (etat === suivant && motif === raison) return;
		etat = suivant;
		motif = raison;
		if (!detruit) surEtat(lire());
	}

	function liberer() {
		generation++;
		libererPistes(flux);
		flux = undefined;
	}

	function detacher() {
		if (!recorder) return;
		recorder.ondataavailable = recorder.onstop = recorder.onerror = null;
		recorder = fragments = undefined;
	}

	function refuser(raison) {
		detacher();
		liberer();
		changer('refus', raison);
	}

	function arreter(raison) {
		changer('finalisation', raison);
		try { recorder.stop(); }
		catch { refuser('capture-impossible'); }
		finally { liberer(); }
	}

	async function autoriserMicro() {
		if (detruit || !accord || phase !== 'avant' || !['accord', 'refus'].includes(etat)) return;
		format = demanderMicro && FORMATS.find(type => Enregistreur?.isTypeSupported?.(type));
		if (!format) { changer('refus', 'indisponible'); return; }
		const demande = ++generation;
		changer('demande-micro');
		let recu;
		try { recu = await demanderMicro({ audio: true }); }
		catch {
			if (demande === generation) refuser('micro-refuse');
			return;
		}
		if (demande !== generation) { libererPistes(recu); return; }
		flux = recu;
		try { recorder = new Enregistreur(flux, { mimeType: format }); }
		catch { refuser('indisponible'); return; }

		fragments = [];
		recorder.ondataavailable = ({ data }) => { if (data.size) fragments.push(data); };
		recorder.onerror = () => refuser('capture-impossible');
		recorder.onstop = () => {
			const audio = new Blob(fragments, { type: format });
			const raison = etat === 'finalisation' ? motif : 'interrompu';
			detacher();
			liberer();
			if (audio.size) {
				fichier = audio;
				changer('pret', raison);
			} else changer('refus', 'capture-impossible');
		};
		changer('micro');
	}

	function suivre(vue, origine) {
		if (detruit || ['finalisation', 'pret'].includes(etat)) return;
		({ phase, accord } = vue);
		if (!accord || phase === 'termine') {
			if (enPrise(etat)) arreter(phase === 'termine' ? undefined : 'interrompu');
			else {
				detacher();
				liberer();
				changer('inactif');
			}
			return;
		}
		if (phase === 'avant') {
			if (etat === 'inactif') changer('accord');
			return;
		}
		const evenement = origine?.source === 'evenement' ? origine.type : null;
		try {
			if (phase === 'actif' && etat === 'micro' && evenement === 'passage-demarre') {
				recorder.start();
				changer('enregistrement');
			} else if (phase === 'pause' && etat === 'enregistrement') {
				recorder.pause();
				changer('pause');
			} else if (phase === 'actif' && etat === 'pause') {
				// La reprise vient d'un événement ou d'une capture après coupure : la prise a déjà démarré.
				recorder.resume();
				changer('enregistrement');
			} else if (!enPrise(etat) && etat !== 'refus') refuser('depart-manque');
		} catch { refuser('capture-impossible'); }
	}

	return {
		lire, suivre, autoriserMicro,
		detruire() {
			if (detruit) return;
			detruit = true;
			const enCours = enPrise(etat) ? recorder : null;
			detacher();
			try { enCours?.stop(); }
			catch { /* L’appareil est déjà arrêté. */ }
			finally {
				liberer();
				fichier = undefined;
			}
		},
	};
}
