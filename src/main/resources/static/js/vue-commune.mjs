const modes = { QUIZ: 'Quiz', BLIND_TEST: 'Blind test', KARAOKE: 'Karaoké', FREESTYLE: 'Freestyle' };
const etats = { PREPARATION: 'En préparation', OUVERTE: 'Ouverte', EN_COURS: 'En cours',
	EN_PAUSE: 'En pause', TERMINEE: 'Terminée', EN_ATTENTE: 'En attente' };
const libellePoints = points => `${points} ${points === 1 ? 'point' : 'points'}`;
const decrireLigne = ligne => `${ligne.rang}. ${ligne.nom} · ${libellePoints(ligne.points)}`;
const maximumParMarche = 3;
const contenusDesMarches = new WeakMap();
const finsDEntree = new WeakMap();
const classeEntree = 'salle__classement--entree';
const classeMarcheEnEntree = 'salle__podium-marche--entree';

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
		manche: terminee ? 'Soirée terminée' :
			vue.mancheActive ? `${modes[vue.modeEnCours] ?? vue.modeEnCours} · Manche ${vue.mancheActive.ordre + 1} · ${etats[vue.mancheActive.etat] ?? vue.mancheActive.etat}` : 'En attente de la prochaine Manche',
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

function creerMarche(document, rang) {
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
	marche.append(numero, liste);
	return marche;
}

function actualiserMarche(marche, concurrents) {
	const visibles = concurrents.slice(0, maximumParMarche);
	const contenu = JSON.stringify([concurrents.length, visibles.map(({ nom, points }) => [nom, points])]);
	if (contenusDesMarches.get(marche) === contenu) return;
	contenusDesMarches.set(marche, contenu);
	marche.setAttribute('data-nombre', concurrents.length);
	const document = marche.ownerDocument;
	const lignes = visibles.map(concurrent => {
		const ligne = document.createElement('li');
		const nom = document.createElement('span');
		nom.className = 'salle__podium-nom';
		nom.textContent = concurrent.nom;
		const points = document.createElement('span');
		points.className = 'salle__podium-points';
		points.textContent = libellePoints(concurrent.points);
		ligne.append(nom, points);
		return ligne;
	});
	const restants = concurrents.length - visibles.length;
	if (restants > 0) {
		const ligne = document.createElement('li');
		ligne.className = 'salle__podium-restants';
		ligne.textContent = `et ${restants} ${restants === 1 ? 'autre' : 'autres'}`;
		lignes.push(ligne);
	}
	marche.querySelector('ul').replaceChildren(...lignes);
}

function afficherMarches(liste, marches) {
	const enPlace = new Map(Array.from(liste.children, marche => [Number(marche.getAttribute('data-rang')), marche]));
	for (const [rang, marche] of enPlace) {
		if (!marches.some(description => description.rang === rang)) marche.remove();
	}
	marches.forEach(({ rang, concurrents }, index) => {
		const marche = enPlace.get(rang) ?? creerMarche(liste.ownerDocument, rang);
		actualiserMarche(marche, concurrents);
		if (!enPlace.has(rang)) liste.insertBefore(marche, liste.children[index] ?? null);
	});
}

function lancerEntree(zone, liste) {
	if (!liste.children.length || zone.ownerDocument.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
	for (const marche of liste.children) marche.classList.add(classeMarcheEnEntree);
	zone.classList.add(classeEntree);

	function terminer() {
		zone.classList.remove(classeEntree);
		liste.removeEventListener('animationend', finirMarche);
		liste.removeEventListener('animationcancel', finirMarche);
		finsDEntree.delete(zone);
	}
	function finirMarche(evenement) {
		if (evenement.animationName !== 'entree-marche' || evenement.target.parentNode !== liste) return;
		evenement.target.classList.remove(classeMarcheEnEntree);
		if (!liste.querySelector(`.${classeMarcheEnEntree}`)) terminer();
	}
	finsDEntree.set(zone, terminer);
	liste.addEventListener('animationend', finirMarche);
	liste.addEventListener('animationcancel', finirMarche);
}

export function afficherClassementDeSalle(zone, vue, origine) {
	const classement = decrireClassementDeSalle(vue);
	const podium = classement?.podium ?? false;
	const etaitPodium = zone.classList.contains('salle__classement--podium');
	zone.hidden = classement === null;
	zone.classList.toggle('salle__classement--podium', podium);
	if (!podium) finsDEntree.get(zone)?.();
	if (!classement) return false;
	const titre = zone.querySelector('h2');
	if (titre.textContent !== classement.titre) titre.textContent = classement.titre;
	const liste = zone.querySelector('ol');
	if (podium) {
		if (!etaitPodium) liste.replaceChildren();
		afficherMarches(liste, classement.marches);
		if (!liste.querySelector(`.${classeMarcheEnEntree}`)) finsDEntree.get(zone)?.();
		if (!etaitPodium && origine?.source === 'evenement') lancerEntree(zone, liste);
	} else {
		liste.replaceChildren(...classement.lignes.map(texte => {
			const li = liste.ownerDocument.createElement('li');
			li.textContent = texte;
			return li;
		}));
	}
	liste.hidden = liste.childElementCount === 0;
	zone.querySelector('p').hidden = !liste.hidden;
	return true;
}
