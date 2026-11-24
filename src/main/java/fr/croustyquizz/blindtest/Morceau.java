package fr.croustyquizz.blindtest;

import java.net.URI;
import java.util.Objects;

/**
 * Morceau du catalogue Deezer, tel que le serveur le garde : jamais publié avant sa Révélation (ADR 0009).
 *
 * @param idDeezer identifiant Deezer, qui sert à redemander le lien d'extrait au moment de jouer
 * @param titre    titre court Deezer ({@code title_short}) : « Halo », pas « Halo (Remastered) »
 * @param artiste  nom de l'artiste principal
 * @param pochette image de l'album, ou {@code null}
 */
public record Morceau(long idDeezer, String titre, String artiste, URI pochette) {

	public Morceau {
		Objects.requireNonNull(titre, "titre");
		Objects.requireNonNull(artiste, "artiste");
		if (titre.isBlank() || artiste.isBlank()) {
			throw new IllegalArgumentException("Un Morceau a un titre et un artiste.");
		}
	}

}
