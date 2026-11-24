const modes = { QUIZ: 'Quiz', BLIND_TEST: 'Blind test', KARAOKE: 'Karaoké', FREESTYLE: 'Freestyle' };
const etats = { PREPARATION: 'En préparation', OUVERTE: 'Ouverte', EN_COURS: 'En cours',
	EN_PAUSE: 'En pause', TERMINEE: 'Terminée', EN_ATTENTE: 'En attente' };

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

// Aucune règle de jeu ici : l'éligibilité du retardataire vient du serveur (eligiblePourMancheActive).
export function decrireVue(vue) {
	const joueur = vue.joueurs.find(j => j.id === vue.joueurId);
	return {
		titre: vue.soiree.titre,
		resume: [etats[vue.soiree.etat] ?? vue.soiree.etat, vue.soiree.format === 'EQUIPE' ? 'Équipe' : 'Solo',
			vue.soiree.pilotage === 'AUTOMATIQUE' ? 'Pilotage automatique' : 'Pilotage manuel'].join(' · '),
		manche: vue.mancheActive ? `${modes[vue.modeEnCours] ?? vue.modeEnCours} · Manche ${vue.mancheActive.ordre + 1} · ${etats[vue.mancheActive.etat] ?? vue.mancheActive.etat}` : 'En attente de la prochaine Manche',
		joueurs: vue.joueurs.map(j => `${j.pseudo} · ${j.connecte ? 'connecté' : 'déconnecté'}`),
		classement: vue.classement.map(ligne => `${ligne.rang}. ${ligne.nom} · ${ligne.points} points`),
		palmares: (vue.palmaresFreestyle ?? vue.palmaresKaraoke)?.map(decrireLignePalmares) ?? null,
		buzz: vue.dernierBuzz ? `Dernier Buzz : ${vue.dernierBuzz.pseudo}` : 'Aucun Buzz reçu',
		programme: (vue.programme ?? []).map(decrireManche),
		personnel: joueur ? [`${joueur.pseudo}`, vue.mancheActive && !vue.eligiblePourMancheActive ?
			'Tu participeras à la prochaine Manche.' : '', vue.resultatPersonnel ?
			`${vue.resultatPersonnel.pointsGagnes} points à la dernière correction` : ''].filter(Boolean).join(' · ') : '',
		codeAcces: vue.soiree.codeAcces,
	};
}
