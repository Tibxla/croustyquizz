package fr.croustyquizz.manche;

import java.util.Objects;
import java.util.UUID;

/** Destination logique dont le service republie la vue complète. */
public record Destination(Type type, UUID joueurId) {
	public enum Type { SALLE, CONSOLE, TOUTES_LES_MANETTES, JOUEUR }
	public static final Destination SALLE = new Destination(Type.SALLE, null);
	public static final Destination CONSOLE = new Destination(Type.CONSOLE, null);
	public static final Destination TOUTES_LES_MANETTES = new Destination(Type.TOUTES_LES_MANETTES, null);
	public Destination {
		Objects.requireNonNull(type);
		if ((type == Type.JOUEUR) != (joueurId != null)) {
			throw new IllegalArgumentException("Une destination Joueur exige son identifiant.");
		}
	}
	public static Destination joueur(UUID id) { return new Destination(Type.JOUEUR, id); }
}
