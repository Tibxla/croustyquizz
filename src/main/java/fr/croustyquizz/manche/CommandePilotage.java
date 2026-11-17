package fr.croustyquizz.manche;

import java.time.Instant;
import java.util.UUID;

/** Identité et reçu communs aux interventions sur une Manche. */
public record CommandePilotage(Acteur acteur, UUID envoiId, Instant recueLe) implements CommandeDeManche {
	public AccuseCommande accuse() { return new AccuseCommande(envoiId, recueLe, acteur.soireeId()); }
}
