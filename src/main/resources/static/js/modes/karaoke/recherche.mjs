export function validerRecherche(texteBrut, longueurRechercheMax) {
	const texteAffiche = texteBrut.normalize('NFC').replace(/\p{White_Space}+/gu, ' ').replace(/^[\u0000-\u0020]+|[\u0000-\u0020]+$/g, '');
	let message = null;
	if ([...texteBrut].length > 120 || [...texteAffiche].length > longueurRechercheMax) message = 'La recherche est trop longue.';
	else if (!texteAffiche) message = 'Saisis un titre ou un artiste.';
	else if (!/[\p{L}\p{N}]/u.test(texteAffiche)) message = 'Utilise une lettre ou un chiffre.';
	return { valide: message === null, texteAffiche, message };
}

export function creerMachineRecherche({ envoyer, creerUuid, programmerDelai, annulerDelai, surEtat }) {
	const lectures = new Map();
	let detruite = false;
	function annuler(genre) {
		const lecture = lectures.get(genre);
		if (!lecture) return;
		lectures.delete(genre);
		annulerDelai(lecture.delai); lecture.controleur.abort(); lecture.resoudre(null);
	}
	function invalider() { annuler('recherche'); annuler('fiche'); }
	function lire(genre, valeur, contexte) {
		if (detruite) return Promise.resolve(null);
		if (genre === 'recherche') invalider(); else annuler('fiche');
		const base = { genre, mancheId: contexte.mancheId };
		if (genre === 'recherche') {
			const validation = validerRecherche(valeur, contexte.configurationInterface.longueurRechercheMax);
			if (!validation.valide) {
				const etat = { ...base, etat: 'erreur', origineErreur: 'validation', detail: validation.message };
				surEtat(etat); return Promise.resolve(etat);
			}
			base.rechercheId = creerUuid();
		} else base.chansonId = valeur;
		const racine = `/api/soirees/${encodeURIComponent(contexte.soireeId)}/manches/${encodeURIComponent(contexte.mancheId)}/chansons`;
		const chemin = genre === 'recherche' ? `${racine}?${new URLSearchParams({ recherche: valeur, rechercheId: base.rechercheId })}` : `${racine}/${encodeURIComponent(valeur)}`;
		return new Promise(resoudre => {
			const lecture = { controleur: new AbortController(), resoudre };
			lectures.set(genre, lecture);
			function terminer(champs) {
				if (lectures.get(genre) !== lecture || detruite) return;
				lectures.delete(genre); annulerDelai(lecture.delai);
				const etat = { ...base, ...champs }; surEtat(etat); resoudre(etat);
			}
			lecture.delai = programmerDelai(() => {
				terminer({ etat: 'erreur', origineErreur: 'delai', detail: 'La consultation prend trop de temps.' });
				lecture.controleur.abort();
			}, contexte.configurationInterface.delaiConsultationNavigateurMs);
			surEtat({ ...base, etat: 'chargement' });
			try {
				Promise.resolve(envoyer('GET', chemin, undefined, { signal: lecture.controleur.signal })).then(reponse => {
					if (reponse.statut === 200) terminer({ etat: genre === 'recherche' && !reponse.corps.chansons.length ? 'vide' : 'resultats', donnees: reponse.corps });
					else terminer({ etat: 'erreur', origineErreur: 'http', statut: reponse.statut, code: reponse.corps?.code, detail: reponse.corps?.detail });
				}).catch(() => terminer({ etat: 'erreur', origineErreur: 'reseau', detail: 'La recherche est indisponible.' }));
			} catch {
				terminer({ etat: 'erreur', origineErreur: 'reseau', detail: 'La recherche est indisponible.' });
			}
		});
	}
	return {
		chercher: (texte, contexte) => lire('recherche', texte, contexte),
		consulter: (chansonId, contexte) => lire('fiche', chansonId, contexte),
		fermerFiche: () => annuler('fiche'), invalider,
		detruire() { detruite = true; invalider(); },
	};
}
