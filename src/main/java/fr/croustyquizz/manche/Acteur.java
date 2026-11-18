package fr.croustyquizz.manche;

import java.util.Objects;
import java.util.UUID;

/** Identité et portée déduites par le serveur, jamais par le corps d'une commande. */
public record Acteur(Type type, UUID identifiant, UUID soireeId) {
	public enum Type { ANIMATEUR, JOUEUR, ECRAN }
	public Acteur {
		Objects.requireNonNull(type);
		Objects.requireNonNull(identifiant);
		Objects.requireNonNull(soireeId);
	}
	public String getName() { return type + ":" + identifiant + ":" + soireeId; }
}
