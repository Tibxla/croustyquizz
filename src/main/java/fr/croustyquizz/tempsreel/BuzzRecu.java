package fr.croustyquizz.tempsreel;

import java.time.Instant;

/** Un Buzz reçu par le serveur, diffusé à toute la salle. */
public record BuzzRecu(String pseudo, Instant recuLe) implements Evenement {

	@Override
	public String type() {
		return "buzz-recu";
	}

}
