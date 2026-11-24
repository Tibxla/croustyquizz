import { texteEnvoi } from './commun.mjs';
import { creerEnregistrement } from './enregistrement.mjs';

const occupeMicro = etat => ['demande-micro', 'micro', 'enregistrement', 'pause', 'finalisation'].includes(etat);
const messages = {
	accord: 'Autorise le micro avant le départ. Ton Enregistrement reste sur ce téléphone.',
	'demande-micro': 'En attente de ton autorisation pour le micro…',
	micro: 'Micro prêt. La prise est perdue si tu recharges la page.',
	enregistrement: 'Enregistrement en cours. La prise est perdue si tu recharges la page.',
	pause: 'Enregistrement en pause. La prise est perdue si tu recharges la page.',
	finalisation: 'Préparation de ton Enregistrement…',
	'micro-refuse': 'Le micro est refusé. Ton Passage aura lieu sans Enregistrement.',
	indisponible: 'Ce navigateur ne peut pas enregistrer le micro. Ton Passage peut continuer.',
	'depart-manque': 'Le Passage a déjà commencé. Une prise ne redémarre pas après un rechargement.',
	'capture-impossible': 'La prise a échoué. Ton Passage peut continuer.',
};

export function nomFichier(titre, depart, fichier, maintenant = Date.now()) {
	const chanson = (titre ?? '').replace(/[<>:"/\\|?*\u0000-\u001F]/g, ' ').trim().replace(/\s+/g, ' ').slice(0, 100);
	const instant = Date.parse(depart ?? '');
	const date = new Date(Number.isNaN(instant) ? maintenant : instant);
	const jour = [date.getFullYear(), date.getMonth() + 1, date.getDate()]
		.map(nombre => String(nombre).padStart(2, '0')).join('-');
	return `${chanson || 'Passage'} - ${jour}.${fichier.type.startsWith('audio/mp4') ? 'mp4' : 'webm'}`;
}

export function installerEnregistrementsManette(ui) {
	const { e, bouton, contexte } = ui;
	const prises = new Map();
	const resultats = e('section', 'karaoke__enregistrements');
	resultats.hidden = true;
	const titreResultats = e('h2', '', 'Tes Enregistrements');
	resultats.append(titreResultats);
	ui.principale.append(resultats);
	let renduPrevu = false;
	function rafraichir() {
		if (renduPrevu) return;
		renduPrevu = true;
		queueMicrotask(() => { renduPrevu = false; ui.rendre(); });
	}

	const envoiAccord = id => ui.dernierEnvoi(e => e.commande.type === 'ACCORD_ENREGISTREMENT' && e.commande.passageId === id);
	const accordDe = id => ui.bloc.mesAccordsEnregistrement?.includes(id) ?? false;
	const microOccupeParUnAutre = id => [...prises].some(([autre, { prise }]) => autre !== id && occupeMicro(prise.lire().etat));
	function creerFiche(passage) {
		const id = passage.passageId;
		const documentFenetre = ui.document.defaultView ?? globalThis;
		const appareils = documentFenetre.navigator?.mediaDevices;
		const fiche = {
			passage,
			prise: creerEnregistrement({
				demanderMicro: appareils?.getUserMedia?.bind(appareils),
				Enregistreur: documentFenetre.MediaRecorder,
				surEtat: rafraichir,
			}),
		};
		const commandes = e('div', 'karaoke__commandes karaoke__enregistrement');
		const choix = bouton('', () => {
			ui.commande({
				methode: 'PUT', chemin: `${ui.racine()}/passages/${encodeURIComponent(id)}/accord-enregistrement`,
				type: 'ACCORD_ENREGISTREMENT', mancheId: ui.bloc.mancheId, acteurType: 'JOUEUR', passageId: id,
				corps: { accord: !accordDe(id), numeroModification: fiche.passage.numeroModification },
			});
		});
		const micro = bouton('Autoriser le micro', () => {
			if (ui.mutationsPermises && !microOccupeParUnAutre(id)) void fiche.prise.autoriserMicro();
		});
		const reessayer = bouton('Réessayer mon accord', () => ui.reessayer(envoiAccord(id)?.cle));
		const statut = e('p');
		statut.setAttribute('role', 'status');
		commandes.append(choix, micro, reessayer, statut);
		return Object.assign(fiche, { commandes, choix, micro, reessayer, statut });
	}

	function rendreFiche(fiche, phase, accord, autorise) {
		const { passage, prise, commandes, choix, micro, reessayer, statut } = fiche;
		const { etat, motif } = prise.lire();
		const envoi = envoiAccord(passage.passageId);
		const rejouable = ui.reessaiPossible(envoi);
		const occupe = microOccupeParUnAutre(passage.passageId);
		choix.hidden = phase !== 'avant';
		choix.textContent = accord ? 'Retirer mon accord' : 'Enregistrer mon Passage';
		choix.disabled = !ui.mutationsPermises || envoi?.etat === 'en-cours' || rejouable;
		micro.hidden = phase !== 'avant' || !accord || !['accord', 'refus'].includes(etat);
		micro.disabled = !ui.mutationsPermises || occupe;
		reessayer.hidden = phase !== 'avant' || !rejouable;
		const texte = envoi && envoi.etat !== 'accepte' && phase === 'avant' ? texteEnvoi(envoi)
			: !micro.hidden && occupe ? 'Le micro est réservé à un autre de tes Passages.'
				: messages[motif ?? etat] ?? '';
		if (statut.textContent !== texte) statut.textContent = texte;
		statut.hidden = !texte;
		commandes.hidden = !autorise || (choix.hidden && micro.hidden && reessayer.hidden && statut.hidden);
		if (commandes.hidden) commandes.remove();
	}

	function publierFichier(fiche) {
		const { fichier, motif } = fiche.prise.lire();
		if (!fichier || fiche.url) return;
		const titre = fiche.passage.chanson.titre;
		fiche.url = URL.createObjectURL(fichier);
		const lien = e('a', 'bouton', 'Télécharger mon Enregistrement');
		lien.href = fiche.url;
		lien.download = nomFichier(titre, fiche.passage.demarreLe, fichier, contexte.maintenantServeur?.());
		const ecoute = e('audio');
		ecoute.src = fiche.url;
		ecoute.controls = true;
		ecoute.preload = 'metadata';
		ecoute.setAttribute('aria-label', `Écouter ton Enregistrement de ${titre}`);
		const resultat = e('div', 'karaoke__enregistrement-fichier');
		resultat.append(e('h3', '', titre), lien, e('p', '', 'Écouter'), ecoute);
		if (motif === 'interrompu') resultat.append(e('p', '', 'Cette prise a été interrompue.'));
		resultat.append(e('p', '', 'Télécharge-le avant la fin de la Manche : il disparaît ensuite, comme après un rechargement.'));
		resultats.append(resultat);
		resultats.hidden = false;
	}

	function liberer(fiche) {
		fiche.prise.detruire();
		fiche.commandes.remove();
		if (fiche.url) URL.revokeObjectURL(fiche.url);
	}

	return {
		rendre(origine) {
			const bloc = ui.bloc;
			const courant = bloc.courant;
			const personnel = courant?.interpretes.some(i => i.joueurId === contexte.acteur.identifiant);
			const autorise = bloc.enregistrementAutorise !== false;
			const passages = new Map(bloc.mesPassagesEnAttente.map(p => [p.passageId, { passage: p, phase: 'avant' }]));
			if (personnel) {
				const phase = { APPELE: 'avant', EN_COURS: 'actif', EN_PAUSE: 'pause' }[courant.etat] ?? 'termine';
				passages.set(courant.passageId, { passage: courant, phase });
			}
			if (autorise) for (const [id, { passage, phase }] of passages) {
				if (phase !== 'termine' && !prises.has(id)) prises.set(id, creerFiche(passage));
			}
			for (const [id, fiche] of prises) {
				const visible = passages.get(id);
				const phase = visible?.phase ?? 'termine';
				const accord = autorise && accordDe(id);
				if (visible) fiche.passage = visible.passage;
				fiche.prise.suivre({ phase, accord }, origine);
				if (phase === 'termine' && !['finalisation', 'pret'].includes(fiche.prise.lire().etat)) {
					liberer(fiche);
					prises.delete(id);
				}
			}
			for (const [id, fiche] of prises) {
				const phase = passages.get(id)?.phase ?? 'termine';
				rendreFiche(fiche, phase, autorise && accordDe(id), autorise && phase !== 'termine');
				publierFichier(fiche);
			}
		},
		placer(passage, parent) {
			const commandes = prises.get(passage.passageId)?.commandes;
			if (commandes && !commandes.hidden && commandes.parentNode !== parent) parent.append(commandes);
		},
		detruire() {
			for (const fiche of prises.values()) liberer(fiche);
			prises.clear();
			resultats.replaceChildren(titreResultats);
			resultats.hidden = true;
		},
	};
}
