package fr.croustyquizz.manche;

import java.util.Set;

/** Commande acceptée ; l'accusé est sérialisé tel quel, sans recalcul métier. */
public record Accepte(Object accuse, Set<Destination> aRepublier, String typeEvenement, int statutHttp) implements ResultatDeCommande {
	private static final Set<String> TYPES = Set.of("soiree-ouverte", "programme-modifie", "joueurs-modifies",
			"manche-demarree", "question-ouverte", "manche-en-pause", "manche-reprise", "question-corrigee",
			"file-attente-modifiee", "limite-passages-modifiee", "manche-terminee", "classement-modifie", "soiree-terminee", "buzz-recu", "reponse-enregistree", "resultat-personnel",
			"passage-appele", "passage-demarre", "passage-termine", "vote-enregistre", "vote-clos", "passage-saute",
			"lecture-chanson-modifiee", "calage-chanson-modifie", "morceau-en-preparation", "morceau-ouvert",
			"proposition-jugee", "morceau-revele", "morceau-saute", "brouillon-depose",
			"photos-modifiees");
	public Accepte(Object accuse, Set<Destination> aRepublier, String typeEvenement) {
		this(accuse, aRepublier, typeEvenement, accuse == null ? 204 : 200);
	}
	public Accepte {
		aRepublier = Set.copyOf(aRepublier);
		if (!TYPES.contains(typeEvenement)) { throw new IllegalArgumentException("Type d'événement inconnu."); }
		if (Set.of("reponse-enregistree", "resultat-personnel").contains(typeEvenement)
				&& (aRepublier.contains(Destination.SALLE) || aRepublier.contains(Destination.CONSOLE))) {
			throw new IllegalArgumentException("Cet événement est réservé aux Manettes.");
		}
	}
}
