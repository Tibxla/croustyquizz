const modes = { QUIZ: 'Quiz', BLIND_TEST: 'Blind test', KARAOKE: 'Karaoké', FREESTYLE: 'Freestyle' };
const etats = { PREPARATION: 'En préparation', OUVERTE: 'Ouverte', EN_COURS: 'En cours',
	EN_PAUSE: 'En pause', TERMINEE: 'Terminée', EN_ATTENTE: 'En attente' };
const libellePoints = points => `${points} ${points === 1 ? 'point' : 'points'}`;
const decrireLigne = ligne => `${ligne.rang}. ${ligne.nom} · ${libellePoints(ligne.points)}`;

export function decrireManche(manche) {
	return `${manche.ordre + 1}. ${modes[manche.mode] ?? manche.mode} · ${etats[manche.etat] ?? manche.etat}`;
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
	return {
		titre: vue.soiree.titre,
		resume: [etats[vue.soiree.etat] ?? vue.soiree.etat, vue.soiree.format === 'EQUIPE' ? 'Équipe' : 'Solo',
			vue.soiree.pilotage === 'AUTOMATIQUE' ? 'Pilotage automatique' : 'Pilotage manuel'].join(' · '),
		manche: vue.mancheActive ? `${modes[vue.modeEnCours] ?? vue.modeEnCours} · Manche ${vue.mancheActive.ordre + 1} · ${etats[vue.mancheActive.etat] ?? vue.mancheActive.etat}`
			: terminee ? '' : 'En attente de la prochaine Manche',
		attente: !terminee && vue.modeEnCours === null ? 'La prochaine Manche s’affichera ici.' : '',
		joueurs: vue.joueurs.map(j => `${j.pseudo} · ${j.connecte ? 'connecté' : 'déconnecté'}`),
		classement: vue.classement.map(decrireLigne),
		buzz: vue.dernierBuzz ? `Dernier Buzz : ${vue.dernierBuzz.pseudo}` : 'Aucun Buzz reçu',
		programme: (vue.programme ?? []).map(decrireManche),
		personnel: joueur ? [joueur.pseudo, decrireRangPersonnel(vue), vue.mancheActive && !vue.eligiblePourMancheActive ?
			'Tu participeras à la prochaine Manche.' : '', vue.resultatPersonnel ?
			`${vue.resultatPersonnel.pointsGagnes} points à la dernière correction` : ''].filter(Boolean).join(' · ') : '',
		codeAcces: terminee ? null : vue.soiree.codeAcces,
	};
}

export function decrireClassementDeSalle(vue) {
	if (vue.soiree.etat === 'TERMINEE') {
		const marches = [1, 2, 3]
			.map(rang => ({ rang, concurrents: vue.classement.filter(ligne => ligne.rang === rang) }))
			.filter(marche => marche.concurrents.length > 0);
		return { titre: 'Podium final', podium: true, marches };
	}
	if (vue.mancheActive || (vue.soiree.etat !== 'EN_COURS' && vue.classement.length === 0)) return null;
	return { titre: 'Classement général · Top 10', podium: false, lignes: vue.classement.slice(0, 10).map(decrireLigne) };
}

function rendreMarche(document, { rang, concurrents }) {
	const marche = document.createElement('li');
	marche.className = 'salle__podium-marche';
	marche.setAttribute('data-rang', rang);
	const numero = document.createElement('h3');
	numero.className = 'salle__podium-rang';
	numero.textContent = rang;
	numero.setAttribute('aria-label', `Rang ${rang}`);
	const liste = document.createElement('ul');
	liste.className = 'salle__podium-concurrents';
	liste.setAttribute('role', 'list');
	for (const concurrent of concurrents) {
		const ligne = document.createElement('li');
		const nom = document.createElement('span');
		nom.className = 'salle__podium-nom';
		nom.textContent = concurrent.nom;
		const points = document.createElement('span');
		points.className = 'salle__podium-points';
		points.textContent = libellePoints(concurrent.points);
		ligne.append(nom, points);
		liste.append(ligne);
	}
	marche.append(numero, liste);
	return marche;
}

export function afficherClassementDeSalle(zone, vue, origine) {
	const classement = decrireClassementDeSalle(vue);
	const podium = classement?.podium ?? false;
	const entreSurEvenement = podium && origine?.source === 'evenement' && !zone.classList.contains('salle__classement--podium');
	zone.hidden = classement === null;
	zone.classList.toggle('salle__classement--podium', podium);
	zone.classList.toggle('salle__classement--entree', entreSurEvenement);
	if (!classement) return false;
	zone.querySelector('h2').textContent = classement.titre;
	const liste = zone.querySelector('ol');
	const document = liste.ownerDocument;
	const elements = classement.podium
		? classement.marches.map(marche => rendreMarche(document, marche))
		: classement.lignes.map(texte => {
			const li = document.createElement('li');
			li.textContent = texte;
			return li;
		});
	liste.replaceChildren(...elements);
	liste.hidden = elements.length === 0;
	zone.querySelector('p').hidden = elements.length > 0;
	return true;
}
