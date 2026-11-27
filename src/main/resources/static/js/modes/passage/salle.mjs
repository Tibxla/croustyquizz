// Zone principale de l'Écran de salle, commune aux Modes à Passages (Karaoké, Freestyle).
// À l'appel (#277), la salle écrit en grand qui monte sur scène ; le compte à rebours passe en second.
// Pendant le vote (#278), elle montre le vote, puis seulement le Passage suivant.
import { formaterTempsRestant, teintePassage } from '../karaoke/presentation.mjs';
import { delaiAppelRestant } from '../karaoke/paroles.mjs';
import { delaiVoteRestant } from '../karaoke/vote.mjs';
import { formaterNombreVotes } from '../../vue-commune.mjs';

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

/** Vote montré au centre de la salle (#278) : le plus récent encore ouvert, tant qu'aucun Passage n'est en scène. */
export function voteAuCentre(votes, passageEnScene) {
	return passageEnScene ? null : (votes ?? []).find(vote => vote.etat === 'OUVERT') ?? null;
}

export const invitationVote = vote => vote.scrutin === 'MEILLEUR_INTERPRETE'
	? 'Désigne le meilleur Interprète sur ta Manette' : 'Note ce Passage sur ta Manette';

/** Bloc de vote : l'invitation, les Interprètes, la Chanson ou l'Instrumentale, le temps restant en grand et les votes. */
export function creerVoteSalle(ui) {
	const { e } = ui;
	const element = e('div', 'passage-salle passage-salle--vote');
	const titre = e('h2', 'passage-salle__invitation');
	const noms = e('p', 'passage-salle__noms passage-salle__qui');
	const quoi = e('p', 'passage-salle__quoi');
	const compte = e('p', 'passage-salle__compte');
	const secondes = e('span', 'passage-salle__secondes chiffres'), votes = e('span', 'passage-salle__votes');
	compte.append(secondes, votes);
	element.append(titre, noms, quoi, compte);
	element.hidden = true;
	return {
		element,
		rendre(vote, { contenu, enPause, maintenant }) {
			element.hidden = !vote;
			if (!vote) return;
			const listeNoms = enumeration.format(vote.interpretes.map(i => i.pseudo));
			// Comme à l'appel, des noms longs passent en corps réduit : aucun Interprète ne disparaît.
			element.classList.toggle('passage-salle--noms-longs', listeNoms.length > 48);
			const textes = [[titre, invitationVote(vote)], [noms, listeNoms],
				[quoi, contenu], [votes, formaterNombreVotes(vote.nombreVotes)]];
			for (const [cible, texte] of textes) if (cible.textContent !== texte) cible.textContent = texte;
			element.style.setProperty('--teinte-passage', `var(--${teintePassage(vote.numero)})`);
			this.decompter(vote, { enPause, maintenant });
		},
		decompter(vote, { enPause, maintenant }) {
			if (!vote) return;
			const texte = (enPause ? 'Pause · ' : '') + formaterTempsRestant(delaiVoteRestant(vote, maintenant, enPause));
			if (secondes.textContent !== texte) secondes.textContent = texte;
		},
	};
}
