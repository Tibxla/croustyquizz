package fr.croustyquizz.blindtest;

import java.util.Objects;

/** Contenu d'une Proposition de titre : un champ absent vaut vide, et un champ demandé vide est faux. */
public record BrouillonProposition(String titre, String artiste) {

	public static final BrouillonProposition VIDE = new BrouillonProposition("", "");

	public BrouillonProposition {
		titre = Objects.requireNonNullElse(titre, "");
		artiste = Objects.requireNonNullElse(artiste, "");
	}

	String champ(PartieDuMorceau partie) {
		return partie == PartieDuMorceau.TITRE ? titre : artiste;
	}

}
