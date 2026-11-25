package fr.croustyquizz.blindtest;

import java.util.Objects;

/** Playlist ou genre Deezer où tirer les Morceaux : visible comme un thème annoncé, ni la sélection ni l'ordre. */
public record SourceDeMorceaux(Type type, long idDeezer) {

	public enum Type { PLAYLIST, GENRE }

	public SourceDeMorceaux {
		Objects.requireNonNull(type, "type");
		if (idDeezer < 0) {
			throw new IllegalArgumentException("Un identifiant Deezer n'est jamais négatif.");
		}
	}

}
