import { creerUuid } from '../karaoke/commun.mjs';
import { lireInstrumentales } from './instrumentales.mjs';

export function monterCatalogueInstrumentales(audio, contexte, statut, redeclarer) {
	const abonnes = new Set();
	const route = `/api/soirees/${encodeURIComponent(contexte.soireeId)}/freestyle/instrumentales`;
	let selection, catalogue, synchronisee, annulation, generation = 0, envoi, confirmation;

	function rendre() {
		redeclarer.hidden = !envoi || envoi.cle === confirmation;
		redeclarer.disabled = !synchronisee || Boolean(annulation);
	}

	async function declarer() {
		if (!synchronisee || annulation || !envoi || envoi.cle === confirmation) return;
		const courant = envoi;
		courant.tente = true;
		const controle = annulation = new AbortController();
		statut.textContent = 'Déclaration des Instrumentales…';
		rendre();
		try {
			const { statut: code, corps } = await contexte.envoyer('PUT', route, { instrumentales: courant.fiches },
				{ entetes: { 'Idempotency-Key': courant.cle }, signal: controle.signal, annoncer: false });
			if (controle.signal.aborted) return;
			if (code === 200) {
				confirmation = courant.cle;
				const n = courant.fiches.length;
				statut.textContent = n ? `${n} Instrumentale${n > 1 ? 's déclarées' : ' déclarée'}.` : 'Aucune Instrumentale déclarée.';
			} else statut.textContent = corps?.detail ?? 'La déclaration des Instrumentales a échoué.';
		} catch {
			if (!controle.signal.aborted) statut.textContent = 'Confirmation de la déclaration indisponible.';
		} finally {
			if (!controle.signal.aborted) { annulation = undefined; rendre(); }
		}
	}

	async function detecter() {
		const nouvelle = audio.selection();
		if (nouvelle === selection) return;
		selection = nouvelle;
		const lecture = ++generation;
		annulation?.abort();
		annulation = envoi = undefined;
		rendre();
		if (!selection) return;
		statut.textContent = 'Lecture des fiches d’Instrumentales…';
		try {
			const fiches = await lireInstrumentales(selection);
			if (lecture !== generation) return;
			catalogue = new Map(fiches.map(({ fiche, fichier }) => [fiche.instrumentaleId, fichier]));
			envoi = { cle: creerUuid(), fiches: fiches.map(({ fiche }) => fiche) };
			for (const rappel of abonnes) rappel();
			if (synchronisee) void declarer();
			else {
				statut.textContent = 'Déclaration en attente de synchronisation.';
				rendre();
			}
		} catch {
			if (lecture === generation) statut.textContent = 'Impossible de lire instrumentales.json. Vérifie le fichier puis choisis à nouveau le dossier.';
		}
	}

	const quitterAudio = audio.surChangement(detecter);
	const quitterConnexion = contexte.surEtatConnexion(etat => {
		synchronisee = etat === 'synchronisee';
		rendre();
		if (synchronisee && envoi && !envoi.tente) void declarer();
	});
	redeclarer.addEventListener('click', declarer);
	void detecter();
	return {
		fichier: id => catalogue?.get(id),
		surChangement(rappel) { abonnes.add(rappel); return () => abonnes.delete(rappel); },
		demonter() {
			generation++;
			annulation?.abort();
			quitterAudio();
			quitterConnexion();
			redeclarer.removeEventListener('click', declarer);
			abonnes.clear();
		},
	};
}
