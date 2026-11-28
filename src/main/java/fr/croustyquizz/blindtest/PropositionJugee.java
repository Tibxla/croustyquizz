package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.AttributionDePoints;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Proposition de titre jugée sur le Morceau courant, conservée pour l'arbitrage de l'Animateur (ADR 0009, point 15).
 * Ses points se déduisent de ses champs justes : un arbitrage les recalcule sans autre état.
 *
 * @param propositionId identifiant attribué par le serveur, visé par l'arbitrage
 * @param tempsBuzzMs   temps d'extrait joué au Buzz qui a donné la main, compté au départage
 */
public record PropositionJugee(UUID propositionId, UUID joueurId, String pseudo, long tempsBuzzMs, ChampJuge titre,
		ChampJuge artiste) {

	public PropositionJugee {
		Objects.requireNonNull(propositionId, "propositionId");
		Objects.requireNonNull(joueurId, "joueurId");
		Objects.requireNonNull(pseudo, "pseudo");
		Objects.requireNonNull(titre, "titre");
		Objects.requireNonNull(artiste, "artiste");
	}

	ChampJuge champ(PartieDuMorceau partie) {
		return partie == PartieDuMorceau.TITRE ? titre : artiste;
	}

	PropositionJugee avec(PartieDuMorceau partie, ChampJuge champ) {
		return partie == PartieDuMorceau.TITRE
				? new PropositionJugee(propositionId, joueurId, pseudo, tempsBuzzMs, champ, artiste)
				: new PropositionJugee(propositionId, joueurId, pseudo, tempsBuzzMs, titre, champ);
	}

	/** Un champ demandé faux ou vide bloque le Joueur jusqu'au Morceau suivant. */
	boolean aUnChampFaux() {
		return titre.verdict() == VerdictChamp.FAUX || artiste.verdict() == VerdictChamp.FAUX;
	}

	int points() {
		int points = 0;
		for (PartieDuMorceau partie : PartieDuMorceau.values()) {
			if (champ(partie).verdict() == VerdictChamp.JUSTE) {
				points += partie.points();
			}
		}
		return points;
	}

	/** Une seule attribution par Proposition, qui compte une bonne réponse au départage dès qu'un champ est juste. */
	Optional<AttributionDePoints> attribution() {
		int points = points();
		if (points == 0) {
			return Optional.empty();
		}
		boolean titreJuste = titre.verdict() == VerdictChamp.JUSTE;
		boolean artisteJuste = artiste.verdict() == VerdictChamp.JUSTE;
		String motif = titreJuste && artisteJuste ? "Titre et artiste trouvés"
				: titreJuste ? "Titre trouvé" : "Artiste trouvé";
		return Optional.of(new AttributionDePoints(joueurId, points, motif, true, tempsBuzzMs));
	}

}
