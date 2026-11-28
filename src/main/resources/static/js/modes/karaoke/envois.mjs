// Le reçu ne remplace jamais la Vue synchronisée. Les métadonnées restent locales.
const canonique = valeur => JSON.stringify(valeur, (_, v) => v && typeof v === 'object' && !Array.isArray(v) ?
	Object.fromEntries(Object.keys(v).sort().map(cle => [cle, v[cle]])) : v);
const etapePerimee = (etat, corpsCommande) => etat.code === 'ETAPE_INACTIVE' && Object.hasOwn(corpsCommande ?? {}, 'etapeId');
export const peutReessayer = (etat, corpsCommande = etat?.commande?.corps) => etat && (['inconnu', 'indisponible'].includes(etat.etat) ||
	(etat.etat === 'refuse' && etat.code !== 'CSRF_INVALIDE' && !etapePerimee(etat, corpsCommande) &&
		(etat.code === 'ETAPE_INACTIVE' || [401, 403].includes(etat.statut))));
export const exigeCapture = etat => ['inconnu', 'indisponible'].includes(etat.etat) || (etat.etat === 'refuse' &&
	etat.code !== 'CSRF_INVALIDE' && (['COMMANDE_PERIMEE', 'ETAPE_INACTIVE', 'ETAT_INCOMPATIBLE'].includes(etat.code) ||
		[401, 403].includes(etat.statut)));

export function creerMachineEnvoi({ envoyer, creerUuid, programmerDelai, annulerDelai,
	delaiConfirmationMs, resynchroniser, surEtat }) {
	const envois = new Map();
	let detruite = false, capturePrevue = false;
	function demanderCapture() {
		if (capturePrevue) return;
		capturePrevue = true;
		queueMicrotask(() => {
			if (detruite) return;
			capturePrevue = false; resynchroniser();
		});
	}
	function transition(envoi, etat) {
		if (detruite || envois.get(envoi.cle) !== envoi || envoi.definitif) return;
		const precedent = envoi.etat?.etat;
		envoi.etat = { cle: envoi.cle, ...etat };
		const refusDefinitif = etat.etat === 'refuse' && !peutReessayer(etat, envoi.corps);
		envoi.definitif = etat.etat === 'accepte' || refusDefinitif;
		surEtat(envoi.etat);
		if (exigeCapture(etat) && precedent !== etat.etat) demanderCapture();
		if (refusDefinitif && (['CONFIRMER_PRESENCE', 'VOTER'].includes(envoi.type) || etapePerimee(etat, envoi.corps))) oublier(envoi.cle);
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
			const succes = envoi.type === 'VOTER' ? statut === 200 : statut >= 200 && statut < 300;
			if (succes) transition(envoi, { etat: 'accepte', statut, corps });
			else {
				// 503, ou erreur 5xx sans code du contrat (page d'erreur d'un relais) : panne passagère, réessayable.
				const transitoire = statut === 503 || (statut >= 500 && !corps?.code);
				const etat = { etat: transitoire ? 'indisponible' : 'refuse', statut, corps, code: corps?.code, detail: corps?.detail };
				if (!envoi.enCours || (!transitoire && !peutReessayer(etat, envoi.corps))) transition(envoi, etat);
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
	function inscription(envoi) { return envoi.acteurType === 'JOUEUR' && envoi.chansonId !== undefined && !envoi.passageId; }
	function retrouverInscription(mancheId, chansonId) {
		for (const e of envois.values()) if (inscription(e) && e.mancheId === mancheId && e.chansonId === chansonId &&
			(e.etat.etat !== 'refuse' || peutReessayer(e.etat, e.corps))) return e.cle;
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
			const accordEnregistrement = commande.type === 'ACCORD_ENREGISTREMENT';
			if (commande.acteurType === 'JOUEUR' && !accordEnregistrement) e.corps.envoiId = cle;
			if (commande.acteurType !== 'JOUEUR' || commande.type === 'VOTER' || accordEnregistrement) {
				e.entetes['Idempotency-Key'] = cle;
			}
			envois.set(cle, e); return partir(e);
		},
		reessayer(cle) {
			const e = envois.get(cle);
			if (!e || detruite || e.definitif) return null;
			if (e.enCours) return { cle };
			return peutReessayer(e.etat, e.corps) ? partir(e) : null;
		},
		retrouverInscription,
		reconcilier(clesVisibles) {
			const visibles = new Set(clesVisibles), oubliees = [];
			for (const e of envois.values()) {
				if (!inscription(e)) continue;
				const visible = visibles.has(e.cle);
				if (visible && ['en-cours', 'inconnu', 'indisponible'].includes(e.etat.etat)) {
					transition(e, { etat: 'accepte' });
				// Le 2xx peut précéder l'événement qui ajoute le Passage : on n'oublie qu'un Passage déjà vu puis disparu.
				} else if (!visible && e.etat.etat === 'accepte' && e.vu) { oublier(e.cle); oubliees.push(e.cle); }
				if (visible) e.vu = true;
			}
			return oubliees;
		},
		oublier,
		detruire() {
			detruite = true;
			for (const cle of envois.keys()) oublier(cle);
		},
	};
}
