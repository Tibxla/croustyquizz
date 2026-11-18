package fr.croustyquizz.tempsreel;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Une page consulte {@code GET /api/heure} pour recevoir l'heure du serveur.
 * Elle s'en sert pour caler son affichage sur le serveur (minuteurs, paroles du
 * Karaoké). Cette consultation ne prouve pas l'installation d'un abonnement STOMP.
 */
@RestController
class HeureControleur {

	record HeureServeur(Instant maintenant) {
	}

	private final Clock horloge;

	HeureControleur(Clock horloge) {
		this.horloge = horloge;
	}

	@GetMapping("/api/heure")
	HeureServeur heure() {
		return new HeureServeur(horloge.instant());
	}

}
