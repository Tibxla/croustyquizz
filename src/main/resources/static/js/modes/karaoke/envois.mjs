// Le reçu ne remplace jamais la Vue synchronisée. Les métadonnées restent locales.
const canonique = valeur => JSON.stringify(valeur, (_, v) => v && typeof v === 'object' && !Array.isArray(v) ?
	Object.fromEntries(Object.keys(v).sort().map(cle => [cle, v[cle]])) : v);
export const peutReessayer = etat => etat && (['inconnu', 'indisponible'].includes(etat.etat) ||
	(etat.etat === 'refuse' && etat.code !== 'CSRF_INVALIDE' && (etat.code === 'ETAPE_INACTIVE' || [401, 403].includes(etat.statut))));

export function creerMachineEnvoi({ envoyer, creerUuid, programmerDelai, annulerDelai,
	delaiConfirmationMs, resynchroniser, surEtat }) {
	const envois = new Map();
	let detruite = false, generation = 0, capturePrevue = false, mancheCourante;
	function demanderCapture() {
		if (capturePrevue) return;
		capturePrevue = true;
		const tour = generation;
		queueMicrotask(() => {
			if (detruite || tour !== generation) return;
			capturePrevue = false; resynchroniser();
		});
	}
	function transition(envoi, etat) {
		if (detruite || envois.get(envoi.cle) !== envoi || envoi.definitif) return;
		const precedent = envoi.etat?.etat;
		envoi.etat = { cle: envoi.cle, ...etat };
		envoi.definitif = etat.etat === 'accepte' || (etat.etat === 'refuse' && !peutReessayer(etat));
		surEtat(envoi.etat);
		const capture = ['inconnu', 'indisponible'].includes(etat.etat) || (etat.etat === 'refuse' && etat.code !== 'CSRF_INVALIDE' &&
			(['COMMANDE_PERIMEE', 'ETAPE_INACTIVE', 'ETAT_INCOMPATIBLE'].includes(etat.code) || [401, 403].includes(etat.statut)));
		if (capture && precedent !== etat.etat) demanderCapture();
	}
	function partir(envoi) {
		const tentative = { controleur: new AbortController(), active: true };
		const actualiserEnCours = () => { envoi.enCours = [...envoi.tentatives].some(t => t.active); };
		envoi.tentatives.add(tentative); envoi.enCours = true;
		transition(envoi, { etat: 'en-cours' });
		tentative.delai = programmerDelai(() => {
			annulerDelai(tentative.delai); tentative.active = false; actualiserEnCours();
			if (!envoi.enCours) transition(envoi, { etat: 'inconnu', detail: 'Confirmation à vérifier.' });
		}, delaiConfirmationMs);
		const terminer = () => { annulerDelai(tentative.delai); envoi.tentatives.delete(tentative); };
		const recevoir = reponse => {
			terminer();
			// Un ancien échec ne doit pas rendre disponible un réessai déjà en cours.
			actualiserEnCours();
			const { statut, corps } = reponse;
			if (statut >= 200 && statut < 300) transition(envoi, { etat: 'accepte', statut, corps });
			else {
				const etat = { etat: statut === 503 ? 'indisponible' : 'refuse', statut, corps, code: corps?.code, detail: corps?.detail };
				if (!envoi.enCours || (statut !== 503 && !peutReessayer(etat))) transition(envoi, etat);
			}
		};
		const echouer = () => {
			terminer(); actualiserEnCours();
			if (!envoi.enCours) transition(envoi, { etat: 'inconnu', detail: 'Confirmation à vérifier.' });
		};
		try {
			Promise.resolve(envoyer(envoi.methode, envoi.chemin, structuredClone(envoi.corps),
				{ entetes: { ...envoi.entetes }, signal: tentative.controleur.signal })).then(recevoir, echouer);
		} catch { echouer(); }
		return { cle: envoi.cle };
	}
	function oublier(cle) {
		const envoi = envois.get(cle);
		if (!envoi) return;
		envois.delete(cle);
		for (const t of envoi.tentatives) { annulerDelai(t.delai); t.controleur.abort(); }
	}
	function vider() {
		generation++; capturePrevue = false;
		for (const cle of envois.keys()) oublier(cle);
	}
	function inscription(envoi) { return envoi.acteurType === 'JOUEUR' && envoi.chansonId !== undefined && !envoi.passageId; }
	function retrouverInscription(mancheId, chansonId) {
		for (const e of envois.values()) if (inscription(e) && e.mancheId === mancheId && e.chansonId === chansonId &&
			(e.etat.etat !== 'refuse' || peutReessayer(e.etat))) return e.cle;
		return null;
	}
	return {
		envoyerCommande(commande) {
			if (detruite) return null;
			const empreinte = canonique([commande.methode, commande.chemin, commande.corps]);
			for (const e of envois.values()) if (e.empreinte === empreinte && e.enCours) return { cle: e.cle };
			if (commande.acteurType === 'JOUEUR' && commande.chansonId && !commande.passageId) {
				const cle = retrouverInscription(commande.mancheId, commande.chansonId);
				if (cle) return { cle };
			}
			const cle = creerUuid();
			const e = { ...commande, cle, empreinte, corps: structuredClone(commande.corps), entetes: { ...commande.entetes }, tentatives: new Set() };
			if (commande.acteurType === 'JOUEUR') e.corps.envoiId = cle;
			else e.entetes['Idempotency-Key'] = cle;
			envois.set(cle, e); return partir(e);
		},
		reessayer(cle) {
			const e = envois.get(cle);
			if (!e || detruite || e.definitif) return null;
			if (e.enCours) return { cle };
			return peutReessayer(e.etat) ? partir(e) : null;
		},
		retrouverInscription,
		reconcilier(clesVisibles) {
			const visibles = new Set(clesVisibles), acceptees = [], oubliees = [];
			for (const e of envois.values()) {
				if (!inscription(e)) continue;
				if (visibles.has(e.cle) && ['en-cours', 'inconnu', 'indisponible'].includes(e.etat.etat)) {
					transition(e, { etat: 'accepte' }); acceptees.push(e.cle);
				} else if (!visibles.has(e.cle) && e.etat.etat === 'accepte') { oublier(e.cle); oubliees.push(e.cle); }
			}
			return { acceptees, oubliees };
		},
		oublier,
		changerManche(mancheId) { if (mancheId !== mancheCourante) { mancheCourante = mancheId; vider(); } },
		detruire() { detruite = true; vider(); },
	};
}
