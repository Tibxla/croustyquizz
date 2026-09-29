package fr.croustyquizz.tempsreel;

import java.time.Clock;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * Buzz générique, sans Manche : une Manette envoie
 * {@code /app/soirees/{soireeId}/buzz}, toute la salle reçoit un {@link BuzzRecu}.
 * L'heure de réception est celle du serveur, seule à faire foi.
 */
@Controller
public class BuzzControleur {

	static final int LONGUEUR_MAX_PSEUDO = 30;

	public record DemandeDeBuzz(String pseudo) {
	}

	private final DiffuseurTempsReel diffuseur;
	private final Clock horloge;

	BuzzControleur(DiffuseurTempsReel diffuseur, Clock horloge) {
		this.diffuseur = diffuseur;
		this.horloge = horloge;
	}

	@MessageMapping("/soirees/{soireeId}/buzz")
	void buzzer(@DestinationVariable UUID soireeId, DemandeDeBuzz demande) {
		if (demande == null || demande.pseudo() == null || demande.pseudo().isBlank()) {
			return;
		}
		String pseudo = demande.pseudo().strip();
		if (pseudo.length() > LONGUEUR_MAX_PSEUDO) {
			pseudo = pseudo.substring(0, LONGUEUR_MAX_PSEUDO);
		}
		diffuseur.diffuserSalle(soireeId, new BuzzRecu(pseudo, horloge.instant()));
	}

}
