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
const pluriel = (nombre, mot) => `${nombre} ${mot}${nombre > 1 ? 's' : ''}`;

// Palmarès du Freestyle (#74) : un Interprète des Passages à plusieurs, classé par victoires.
function decrireLigneVictoires({ rang, interprete, nombreVictoires, nombrePassages }) {
	const titre = nombreVictoires === 0 ? 'Aucune victoire' : pluriel(nombreVictoires, 'victoire');
	const votes = `${pluriel(nombrePassages, 'Passage')} à plusieurs`;
	return { numero: null, rang, titre, interpretes: interprete.pseudo, moyenne: null, votes,
		texte: `${rang === null ? '' : `${rang}. `}${interprete.pseudo} · ${titre} · ${votes}` };
}

function decrireLignePalmares(ligne) {
	if ('nombreVictoires' in ligne) return decrireLigneVictoires(ligne);
	const { numero, rang, chanson, instrumentale, interpretes, interprete, nombreVotes, moyenneDixiemes } = ligne;
	const titre = (chanson ?? instrumentale).titre;
	const position = rang === null ? `n° ${numero}` : `${rang}. n° ${numero}`;
	const pseudos = listeInterpretes.format((interpretes ?? [interprete]).map(i => i.pseudo));
	const votes = formaterNombreVotes(nombreVotes);
	const moyenne = moyenneDixiemes === null ? null : formaterValeurMoyenne(moyenneDixiemes);
	const resultat = moyenne === null ? votes : `${formaterNote(moyenne)} (${votes})`;
	return { numero, rang, titre, interpretes: pseudos, moyenne, votes,
		texte: `${position} · ${titre} · ${pseudos} · ${resultat}` };
}

const decrirePalmares = vue => (vue.palmaresFreestyle ?? vue.palmaresKaraoke)?.map(decrireLignePalmares) ?? null;

// Le Karaoké et le Freestyle ne font marquer de points à personne : le rang n'a rien à dire pendant leurs Manches.
const MODES_SANS_POINTS = new Set(['KARAOKE', 'FREESTYLE']);

function decrireRangPersonnel(vue) {
	if (vue.soiree.format !== 'SOLO' || MODES_SANS_POINTS.has(vue.modeEnCours)) return '';
	const ligne = vue.classement.find(ligne => ligne.concurrentId === vue.joueurId);
	return ligne ? `Rang ${ligne.rang} · ${libellePoints(ligne.points)}` : '';
}

/** L'état de connexion ne prend la parole qu'en cas de coupure ; sinon, une pastille discrète (#279). */
export function decrireConnexion(etat) {
	if (etat === 'coupure') return { texte: 'Connexion perdue, on se reconnecte…', coupure: true };
	return { texte: etat === 'synchronisee' ? 'En direct' : 'Connexion…', coupure: false };
}

// Aucune règle de jeu ici : l'éligibilité du retardataire vient du serveur (eligiblePourMancheActive).
// Le Format et le Pilotage, comme la présence des Joueurs, ne servent qu'à l'Animateur (#279).
export function decrireVue(vue, { ecran = 'console' } = {}) {
	const animateur = ecran === 'console';
	const joueur = vue.joueurs.find(j => j.id === vue.joueurId);
	const terminee = vue.soiree.etat === 'TERMINEE';
	const palmares = decrirePalmares(vue);
	return {
		titre: vue.soiree.titre,
		resume: !animateur ? '' : [etats[vue.soiree.etat] ?? vue.soiree.etat, vue.soiree.format === 'EQUIPE' ? 'Équipe' : 'Solo',
			vue.soiree.pilotage === 'AUTOMATIQUE' ? 'Pilotage automatique' : 'Pilotage manuel'].join(' · '),
		manche: terminee ? 'Soirée terminée' :
			vue.mancheActive ? `${modes[vue.modeEnCours] ?? vue.modeEnCours} · Manche ${vue.mancheActive.ordre + 1} · ${etats[vue.mancheActive.etat] ?? vue.mancheActive.etat}` : 'En attente de la prochaine Manche',
		attente: !terminee && vue.modeEnCours === null && palmares === null ? 'La prochaine Manche s’affichera ici.' : '',
		joueurs: vue.joueurs.map(j => animateur ? `${j.pseudo} · ${j.connecte ? 'connecté' : 'déconnecté'}` : j.pseudo),
		classement: vue.classement.map(decrireLigne),
		palmares,
		// Le Buzz ne sert qu'entre deux Manches : pendant une Manche, il se tait (#281).
		buzz: vue.dernierBuzz && !vue.modeEnCours ? `Dernier Buzz : ${vue.dernierBuzz.pseudo}` : '',
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

// Le Palmarès du Karaoké ou du Freestyle, quand il est présent, prend la place du Classement de salle (Q24, #123),
// sauf devant le Podium final.
export function decrireClassementDeSalle(vue) {
	if (podiumFinal(vue)) {
		return { titre: 'Podium final', podium: true, lignes: vue.classement.filter(ligne => ligne.rang <= 3).map(decrireLigne) };
	}
	if (vue.palmaresKaraoke != null || vue.palmaresFreestyle != null) return null;
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

const nombreJoueurs = n => (n === 0 ? 'Aucun Joueur' : `${n} Joueur${n > 1 ? 's' : ''}`);

/** Ce que la barre de pilotage de la Console montre d'un coup d'œil (#282) : l'étape, le code d'accès, les Joueurs. */
export function decrireBarre(vue) {
	const { etat, codeAcces } = vue.soiree;
	const manche = vue.mancheActive;
	let mode;
	if (etat === 'TERMINEE') mode = 'Soirée terminée';
	else if (manche) mode = [modes[vue.modeEnCours] ?? vue.modeEnCours, `Manche ${manche.ordre + 1}`,
		...(manche.etat === 'EN_PAUSE' ? ['En pause'] : [])].join(' · ');
	else if (etat === 'PREPARATION') mode = 'Soirée pas encore ouverte';
	else if (etat === 'OUVERTE') mode = 'Avant le lancement';
	else mode = 'Entre deux Manches';
	const ouverte = etat === 'OUVERTE' || etat === 'EN_COURS';
	return { mode, code: ouverte ? codeAcces ?? null : null, joueurs: nombreJoueurs(vue.joueurs.length) };
}
