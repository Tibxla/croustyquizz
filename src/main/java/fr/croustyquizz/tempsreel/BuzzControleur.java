package fr.croustyquizz.tempsreel;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Buzz générique, sans Manche : une Manette envoie
 * un POST HTTP de démonstration, toute la salle reçoit un {@link BuzzRecu}.
 * L'heure de réception est celle du serveur, seule à faire foi.
 */
@RestController
@Profile({"dev", "recette"})
public class BuzzControleur {

	static final int LONGUEUR_MAX_PSEUDO = 30;
	private static final int NOMBRE_DE_VERROUS = 64;
	private final Object[] verrous = new Object[NOMBRE_DE_VERROUS];

	public record DemandeDeBuzz(String pseudo) {
	}

	private final DiffuseurTempsReel diffuseur;
	private final Clock horloge;

	BuzzControleur(DiffuseurTempsReel diffuseur, Clock horloge) {
		this.diffuseur = diffuseur;
		this.horloge = horloge;
		for (int i = 0; i < verrous.length; i++) {
			verrous[i] = new Object();
		}
	}

	@PostMapping("/api/test/soirees/{soireeId}/buzz")
	BuzzRecu buzzer(@PathVariable UUID soireeId, @RequestBody DemandeDeBuzz demande) {
		if (demande == null || demande.pseudo() == null || demande.pseudo().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un pseudo est requis");
		}
		String pseudo = demande.pseudo().strip();
		if (pseudo.codePointCount(0, pseudo.length()) > LONGUEUR_MAX_PSEUDO) {
			pseudo = pseudo.substring(0, pseudo.offsetByCodePoints(0, LONGUEUR_MAX_PSEUDO));
		}
		// Mémoire bornée même avec les UUID arbitraires des pages de démonstration.
		// Deux Soirées partageant une case sont simplement sérialisées ensemble.
		Object verrou = verrous[Math.floorMod(soireeId.hashCode(), verrous.length)];
		synchronized (verrou) {
			BuzzRecu buzz = new BuzzRecu(pseudo, horloge.instant());
			diffuseur.diffuserSalle(soireeId, buzz);
			return buzz;
		}
	}

}
