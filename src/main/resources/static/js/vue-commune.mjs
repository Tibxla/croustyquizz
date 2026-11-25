const modes = { QUIZ: 'Quiz', BLIND_TEST: 'Blind test', KARAOKE: 'Karaoké', FREESTYLE: 'Freestyle' };
const etats = { PREPARATION: 'En préparation', OUVERTE: 'Ouverte', EN_COURS: 'En cours',
	EN_PAUSE: 'En pause', TERMINEE: 'Terminée', EN_ATTENTE: 'En attente' };
const libellePoints = points => `${points} ${points === 1 ? 'point' : 'points'}`;
const decrireLigne = ligne => `${ligne.rang}. ${ligne.nom} · ${libellePoints(ligne.points)}`;

export function decrireManche(manche) {
	return `${manche.ordre + 1}. ${modes[manche.mode] ?? manche.mode} · ${etats[manche.etat] ?? manche.etat}`;
}

export const formaterNote = note => `${note} sur 5`;
export const formaterMoyenne = moyenneDixiemes => formaterNote((moyenneDixiemes / 10).toFixed(1).replace('.', ','));
export const formaterNombreVotes = nombre => nombre === 0 ? 'Aucun vote' : `${nombre} vote${nombre > 1 ? 's' : ''}`;

const pluriel = (nombre, mot) => `${nombre} ${mot}${nombre > 1 ? 's' : ''}`;

// Palmarès du Freestyle (#74) : un Interprète des Passages à plusieurs, classé par victoires.
function decrireLigneVictoires({ rang, interprete, nombreVictoires, nombrePassages }) {
	const titre = nombreVictoires === 0 ? 'Aucune victoire' : pluriel(nombreVictoires, 'victoire');
	const votes = `${pluriel(nombrePassages, 'Passage')} à plusieurs`;
	return { numero: null, rang, titre, interpretes: interprete.pseudo, note: null, votes,
		texte: `${rang === null ? '' : `${rang}. `}${interprete.pseudo} · ${titre} · ${votes}` };
}

function decrireLignePalmares(ligne) {
	if ('nombreVictoires' in ligne) return decrireLigneVictoires(ligne);
	const { numero, rang, chanson, instrumentale, interpretes, interprete, nombreVotes, moyenneDixiemes } = ligne;
	const titre = (chanson ?? instrumentale).titre;
	const position = rang === null ? `n° ${numero}` : `${rang}. n° ${numero}`;
	const pseudos = interpretes?.map(i => i.pseudo).join(' et ') ?? interprete.pseudo;
	const votes = formaterNombreVotes(nombreVotes);
	const note = moyenneDixiemes === null ? null : formaterMoyenne(moyenneDixiemes);
	const resultat = note === null ? votes : `${note} (${votes})`;
	return { numero, rang, titre, interpretes: pseudos, note, votes,
		texte: `${position} · ${titre} · ${pseudos} · ${resultat}` };
}

function decrireRangPersonnel(vue) {
	if (vue.soiree.format !== 'SOLO') return '';
	const ligne = vue.classement.find(ligne => ligne.concurrentId === vue.joueurId);
	return ligne ? `Rang ${ligne.rang} · ${libellePoints(ligne.points)}` : 'Non classé · 0 point';
}

// Aucune règle de jeu ici : l'éligibilité du retardataire vient du serveur (eligiblePourMancheActive).
export function decrireVue(vue) {
	const joueur = vue.joueurs.find(j => j.id === vue.joueurId);
	const terminee = vue.soiree.etat === 'TERMINEE';
	const palmares = (vue.palmaresFreestyle ?? vue.palmaresKaraoke)?.map(decrireLignePalmares) ?? null;
	return {
		titre: vue.soiree.titre,
		resume: [etats[vue.soiree.etat] ?? vue.soiree.etat, vue.soiree.format === 'EQUIPE' ? 'Équipe' : 'Solo',
			vue.soiree.pilotage === 'AUTOMATIQUE' ? 'Pilotage automatique' : 'Pilotage manuel'].join(' · '),
		manche: terminee ? 'Soirée terminée' :
			vue.mancheActive ? `${modes[vue.modeEnCours] ?? vue.modeEnCours} · Manche ${vue.mancheActive.ordre + 1} · ${etats[vue.mancheActive.etat] ?? vue.mancheActive.etat}` : 'En attente de la prochaine Manche',
		attente: !terminee && vue.modeEnCours === null && palmares === null ? 'La prochaine Manche s’affichera ici.' : '',
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

// Le Palmarès du Karaoké ou du Freestyle, quand il est présent, prend la place du Classement de salle (Q24, #123).
export function decrireClassementDeSalle(vue) {
	if (vue.palmaresKaraoke != null || vue.palmaresFreestyle != null) return null;
	if (vue.soiree.etat === 'TERMINEE') {
		return { titre: 'Podium final', podium: true, lignes: vue.classement.filter(ligne => ligne.rang <= 3).map(decrireLigne) };
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
