// Safari n'enregistre qu'en MP4 ; Chrome et Firefox savent aussi le WebM. MP4 d'abord : il se lit partout.
export const FORMATS = ['audio/mp4', 'audio/webm;codecs=opus'];
// Sans intervalle, un vrai navigateur ne livre rien avant l'arrêt : une erreur perdrait toute la prise.
export const INTERVALLE_FRAGMENTS_MS = 1000;
const enPrise = etat => etat === 'enregistrement' || etat === 'pause';
const conclu = etat => ['finalisation', 'pret', 'echec'].includes(etat);
const libererPistes = flux => flux?.getTracks().forEach(piste => piste.stop());

export const choisirFormat = Enregistreur => FORMATS.find(type => Enregistreur?.isTypeSupported?.(type));

// Motif qui rend l'Enregistrement impossible sur ce téléphone, ou null.
export function diagnostiquer({ securise, demanderMicro, Enregistreur }) {
	if (!securise) return 'page-non-securisee';
	if (!demanderMicro || !Enregistreur) return 'navigateur-incompatible';
	return choisirFormat(Enregistreur) ? null : 'format-absent';
}

// Une instance par Passage. Aucune dépendance au DOM ou aux échanges réseau.
// Le micro ne dépend pas du réseau : une coupure ne touche pas la prise, la vue suivante la réconcilie.
// Seul l'événement passage-demarre lance la prise ; une capture ne relance jamais une prise arrêtée.
// surResultat reçoit le fichier final, ou l'échec, même après detruire() : la prise survit au module.
export function creerEnregistrement({ demanderMicro, Enregistreur, surEtat, surResultat = () => {} }) {
	let etat = 'inactif', motif, fichier, flux, recorder, fragments, format, generation = 0;
	let phase = 'termine', accord = false, detruit = false, vuAvant = false;
	const lire = () => ({ etat, motif, fichier });
	function changer(suivant, raison) {
		if (etat === suivant && motif === raison) return;
		etat = suivant;
		motif = raison;
		if (!detruit) surEtat(lire());
	}

	function liberer() {
		generation++;
		flux?.getTracks().forEach(piste => piste.removeEventListener?.('ended', surFinDePiste));
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

	// Seule issue d'une prise démarrée, appelée une fois : les fragments reçus, ou l'échec.
	function conclure(raison) {
		if (!recorder) return;
		const audio = new Blob(fragments, { type: format });
		detacher();
		liberer();
		if (audio.size) {
			fichier = audio;
			changer('pret', raison);
			surResultat({ fichier: audio, motif: raison });
		} else {
			changer('echec', 'capture-vide');
			surResultat({ echec: true, motif: 'capture-vide' });
		}
	}

	function arreter(raison) {
		changer('finalisation', raison);
		try {
			if (recorder.state !== 'inactive') recorder.stop();
		} catch {
			// L'appareil s'est déjà arrêté seul (piste morte) : on garde ce qu'il a livré.
			conclure(raison);
		} finally { liberer(); }
	}

	// Le système coupe le micro (écran verrouillé, appel entrant, autre application).
	function surFinDePiste() {
		if (etat === 'micro') {
			detacher();
			liberer();
			changer('accord', 'micro-coupe');
		} else if (enPrise(etat)) arreter('interrompu');
	}

	async function autoriserMicro() {
		if (detruit || !accord || phase !== 'avant' || !['accord', 'refus'].includes(etat)) return;
		format = demanderMicro && choisirFormat(Enregistreur);
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
		flux.getTracks().forEach(piste => piste.addEventListener?.('ended', surFinDePiste));
		try { recorder = new Enregistreur(flux, { mimeType: format }); }
		catch { refuser('indisponible'); return; }

		fragments = [];
		recorder.ondataavailable = ({ data }) => { if (data.size) fragments.push(data); };
		recorder.onerror = () => {
			if (!enPrise(etat) && etat !== 'finalisation') { refuser('capture-impossible'); return; }
			const appareil = recorder;
			conclure('interrompu');
			try { if (appareil.state !== 'inactive') appareil.stop(); }
			catch { /* L’appareil en erreur est déjà arrêté. */ }
		};
		recorder.onstop = () => conclure(etat === 'finalisation' ? motif : 'interrompu');
		changer('micro');
	}

	// Pourquoi la prise ne démarre pas avec le Passage, selon l'état au départ.
	function motifDepartManque() {
		if (etat === 'accord') return 'micro-non-autorise';
		if (etat === 'demande-micro') return 'demande-en-cours';
		// Une instance qui n'a jamais vu le Passage en attente vient d'un rechargement.
		return etat === 'inactif' && !vuAvant ? 'rechargement' : 'depart-manque';
	}

	function suivre(vue, origine) {
		if (detruit || conclu(etat)) return;
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
			vuAvant = true;
			if (etat === 'inactif') changer('accord');
			return;
		}
		const evenement = origine?.source === 'evenement' ? origine.type : null;
		try {
			if (phase === 'actif' && etat === 'micro' && evenement === 'passage-demarre') {
				recorder.start(INTERVALLE_FRAGMENTS_MS);
				changer('enregistrement');
			} else if (phase === 'pause' && etat === 'enregistrement') {
				recorder.pause();
				changer('pause');
			} else if (phase === 'actif' && etat === 'pause') {
				// La reprise vient d'un événement ou d'une capture après coupure : la prise a déjà démarré.
				recorder.resume();
				changer('enregistrement');
			} else if (!enPrise(etat) && etat !== 'refus') refuser(motifDepartManque());
		} catch {
			if (enPrise(etat)) arreter('interrompu');
			else refuser('capture-impossible');
		}
	}

	return {
		lire, suivre, autoriserMicro,
		// La prise en cours s'arrête, mais onstop livre encore son fichier à surResultat.
		detruire() {
			if (detruit) return;
			detruit = true;
			if (enPrise(etat)) arreter('interrompu');
			else if (etat !== 'finalisation') {
				detacher();
				liberer();
			}
		},
	};
}
