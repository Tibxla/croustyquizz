package fr.croustyquizz.manche;

import java.util.Objects;
import java.util.UUID;

/** Identité idempotente, route canonique et corps désérialisé d'une commande. */
public record EnvoiDeCommande(Acteur acteur, String route, UUID envoiId, Object corps) {
	public EnvoiDeCommande {
		Objects.requireNonNull(acteur); Objects.requireNonNull(route); Objects.requireNonNull(envoiId);
	}
}
