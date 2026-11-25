const modes = { QUIZ: 'Quiz', BLIND_TEST: 'Blind test', KARAOKE: 'Karaoké', FREESTYLE: 'Freestyle' };
const etats = { PREPARATION: 'En préparation', OUVERTE: 'Ouverte', EN_COURS: 'En cours',
	EN_PAUSE: 'En pause', TERMINEE: 'Terminée', EN_ATTENTE: 'En attente' };
const libellePoints = points => `${points} ${points === 1 ? 'point' : 'points'}`;
const decrireLigne = ligne => `${ligne.rang}. ${ligne.nom} · ${libellePoints(ligne.points)}`;

export function decrireManche(manche) {
	return `${manche.ordre + 1}. ${modes[manche.mode] ?? manche.mode} · ${etats[manche.etat] ?? manche.etat}`;
}

export const formaterNote = note => `${note} sur 5`;
export const formaterValeurMoyenne = moyenneDixiemes => (moyenneDixiemes / 10).toFixed(1).replace('.', ',');
export const formaterMoyenne = moyenneDixiemes => formaterNote(formaterValeurMoyenne(moyenneDixiemes));
export const formaterNombreVotes = nombre => nombre === 0 ? 'Aucun vote' : `${nombre} vote${nombre > 1 ? 's' : ''}`;

const listeInterpretes = new Intl.ListFormat('fr', { type: 'conjunction' });

function decrireLignePalmares({ numero, rang, chanson, interpretes, nombreVotes, moyenneDixiemes }) {
	const position = rang === null ? `n° ${numero}` : `${rang}. n° ${numero}`;
	const pseudos = listeInterpretes.format(interpretes.map(interprete => interprete.pseudo));
	const votes = formaterNombreVotes(nombreVotes);
	const moyenne = moyenneDixiemes === null ? null : formaterValeurMoyenne(moyenneDixiemes);
	const resultat = moyenne === null ? votes : `${formaterNote(moyenne)} (${votes})`;
	return { numero, rang, titre: chanson.titre, interpretes: pseudos, moyenne, votes,
		texte: `${position} · ${chanson.titre} · ${pseudos} · ${resultat}` };
}

const decrirePalmares = vue => vue.palmaresKaraoke?.map(decrireLignePalmares) ?? null;

function decrireRangPersonnel(vue) {
	if (vue.soiree.format !== 'SOLO') return '';
	const ligne = vue.classement.find(ligne => ligne.concurrentId === vue.joueurId);
	return ligne ? `Rang ${ligne.rang} · ${libellePoints(ligne.points)}` : 'Non classé · 0 point';
}

// Aucune règle de jeu ici : l'éligibilité du retardataire vient du serveur (eligiblePourMancheActive).
export function decrireVue(vue) {
	const joueur = vue.joueurs.find(j => j.id === vue.joueurId);
	const terminee = vue.soiree.etat === 'TERMINEE';
	const palmares = decrirePalmares(vue);
	return {
		titre: vue.soiree.titre,
		resume: [etats[vue.soiree.etat] ?? vue.soiree.etat, vue.soiree.format === 'EQUIPE' ? 'Équipe' : 'Solo',
			vue.soiree.pilotage === 'AUTOMATIQUE' ? 'Pilotage automatique' : 'Pilotage manuel'].join(' · '),
		manche: terminee ? 'Soirée terminée' :
			vue.mancheActive ? `${modes[vue.modeEnCours] ?? vue.modeEnCours} · Manche ${vue.mancheActive.ordre + 1} · ${etats[vue.mancheActive.etat] ?? vue.mancheActive.etat}` : 'En attente de la prochaine Manche',
		attente: !terminee && vue.modeEnCours === null && palmares === null && !vue.transitionManche
			? 'La prochaine Manche s’affichera ici.' : '',
		joueurs: vue.joueurs.map(j => `${j.pseudo} · ${j.connecte ? 'connecté' : 'déconnecté'}`),
		classement: vue.classement.map(decrireLigne),
		palmares,
		buzz: vue.dernierBuzz ? `Dernier Buzz : ${vue.dernierBuzz.pseudo}` : 'Aucun Buzz reçu',
		programme: (vue.programme ?? []).map(decrireManche),
		personnel: joueur ? [joueur.pseudo, decrireRangPersonnel(vue), vue.mancheActive && !vue.eligiblePourMancheActive ?
			'Tu participeras à la prochaine Manche.' : '', vue.resultatPersonnel ?
			`${vue.resultatPersonnel.pointsGagnes} points à la dernière correction` : ''].filter(Boolean).join(' · ') : '',
		codeAcces: terminee ? null : vue.soiree.codeAcces,
	};
}

// Sur la salle, le Podium final de la Soirée terminée passe avant le Palmarès (#224) ; la Console garde le Palmarès.
const podiumFinal = vue => vue.soiree.etat === 'TERMINEE';
export const decrirePalmaresDeSalle = vue => podiumFinal(vue) ? null : decrirePalmares(vue);

// Le Palmarès du Karaoké, quand il est présent, prend la place du Classement de salle (Q24, #123), sauf devant le Podium final.
// Pendant une transition, le Classement passe sous l'annonce : trois lignes, et rien s'il est vide.
export function decrireClassementDeSalle(vue) {
	if (podiumFinal(vue)) {
		return { titre: 'Podium final', podium: true, lignes: vue.classement.filter(ligne => ligne.rang <= 3).map(decrireLigne) };
	}
	if (vue.palmaresKaraoke != null) return null;
	if (vue.transitionManche) {
		if (vue.classement.length === 0) return null;
		return { titre: 'Classement général', podium: false, lignes: vue.classement.slice(0, 3).map(decrireLigne) };
	}
	if (vue.mancheActive || (vue.soiree.etat !== 'EN_COURS' && vue.classement.length === 0)) return null;
	return { titre: 'Classement général · Top 10', podium: false, lignes: vue.classement.slice(0, 10).map(decrireLigne) };
}

export function afficherClassementDeSalle(zone, vue) {
	const classement = decrireClassementDeSalle(vue);
	zone.hidden = classement === null;
	if (!classement) return false;
	zone.classList.toggle('salle__classement--podium', classement.podium);
	zone.querySelector('h2').textContent = classement.titre;
	zone.querySelector('p').hidden = classement.lignes.length > 0;
	const liste = zone.querySelector('ol');
	liste.replaceChildren(...classement.lignes.map(texte => {
		const li = liste.ownerDocument.createElement('li');
		li.textContent = texte;
		return li;
	}));
	return true;
}

// Transition du Pilotage Automatique (#123). Le compte à rebours se cale sur l'heure du serveur : `maintenant`
// vient de contexte.maintenantServeur(), jamais de l'horloge du téléphone.
export function decrireTransition(vue, maintenant) {
	const transition = vue.transitionManche;
	if (!transition) return null;
	const finDeSoiree = transition.modeSuivant === null;
	return {
		annonce: finDeSoiree ? 'Fin de la Soirée' : `Prochaine Manche : ${modes[transition.modeSuivant] ?? transition.modeSuivant}`,
		restantSecondes: Math.max(0, Math.ceil((Date.parse(transition.finPrevueLe) - maintenant) / 1000)),
		finDeSoiree,
	};
}

// Textes de chaque écran : l'annonce, puis le délai qui change chaque seconde.
export function libellesTransition(ecran, { annonce, restantSecondes, finDeSoiree }) {
	const delai = `dans ${restantSecondes} s`;
	if (ecran === 'console') return { annonce: `Transition : ${annonce}`, delai };
	if (ecran === 'manette') return { annonce: finDeSoiree ? 'Fin de la Soirée' : 'Prochaine Manche', delai };
	return { annonce, delai };
}
