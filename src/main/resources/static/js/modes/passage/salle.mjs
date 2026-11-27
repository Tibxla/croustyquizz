// Zone principale de l'Écran de salle, commune aux Modes à Passages (Karaoké, Freestyle).
// À l'appel (#277), la salle écrit en grand qui monte sur scène ; le compte à rebours passe en second.
import { formaterTempsRestant, teintePassage } from '../karaoke/presentation.mjs';
import { delaiAppelRestant } from '../karaoke/paroles.mjs';

const enumeration = new Intl.ListFormat('fr', { type: 'conjunction' });

/** « À toi, Léa ! », ou « À vous, Léa et Max ! » pour un Passage à plusieurs. L'espace avant « ! » est insécable. */
export function texteAppel(interpretes) {
	const liste = interpretes ?? [];
	return { debut: liste.length > 1 ? 'À vous, ' : 'À toi, ', noms: enumeration.format(liste.map(i => i.pseudo)), fin: ' !' };
}

/** Bloc d'appel : les noms en grand, la Chanson ou l'Instrumentale, puis le temps restant. */
export function creerAppelSalle(ui) {
	const { e } = ui;
	const element = e('div', 'passage-salle passage-salle--appel');
	const titre = e('h2', 'passage-salle__appel');
	const debut = e('span'), noms = e('span', 'passage-salle__noms'), fin = e('span');
	titre.append(debut, noms, fin);
	const quoi = e('p', 'passage-salle__quoi');
	const temps = e('p', 'passage-salle__temps');
	const libelle = e('span', '', 'Prépare-toi'), separateur = e('span', '', ' · '), reste = e('span', 'chiffres');
	temps.append(libelle, separateur, reste);
	element.append(titre, quoi, temps);
	element.hidden = true;
	let description;
	return {
		element,
		/** courant : Passage APPELE ou null ; contenu : texte de la Chanson ou de l'Instrumentale. */
		rendre(courant, { contenu, enPause, maintenant }) {
			element.hidden = !courant;
			if (!courant) { description = undefined; return; }
			const texte = texteAppel(courant.interpretes);
			const cle = JSON.stringify([courant.passageId, texte, contenu, courant.numero]);
			if (cle !== description) {
				description = cle;
				debut.textContent = texte.debut;
				noms.textContent = texte.noms;
				fin.textContent = texte.fin;
				quoi.textContent = contenu;
				// Aucun Interprète ne doit disparaître : des noms longs passent dans un corps plus petit.
				const longueur = texte.noms.length;
				element.classList.toggle('passage-salle--noms-longs', longueur > 24 && longueur <= 48);
				element.classList.toggle('passage-salle--noms-tres-longs', longueur > 48);
				element.style.setProperty('--teinte-passage', `var(--${teintePassage(courant.numero)})`);
			}
			this.decompter(courant, { enPause, maintenant });
		},
		decompter(courant, { enPause, maintenant }) {
			if (!courant) return;
			const sansDelai = courant.delaiAppelRestantMs == null;
			const texte = enPause ? 'Pause' : sansDelai ? '' : formaterTempsRestant(delaiAppelRestant({ ...courant, maintenant, enPause }));
			separateur.hidden = reste.hidden = !texte;
			if (reste.textContent !== texte) reste.textContent = texte;
		},
	};
}
