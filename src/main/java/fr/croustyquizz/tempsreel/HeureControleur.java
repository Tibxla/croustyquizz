package fr.croustyquizz.tempsreel;

import java.time.Clock;
import java.time.Instant;

import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * Une page qui s'abonne à {@code /app/heure} reçoit aussitôt l'heure du serveur.
 * Elle s'en sert pour caler son affichage sur le serveur (minuteurs, paroles du
 * Karaoké), et comme signal que ses abonnements précédents sont enregistrés.
 */
@Controller
class HeureControleur {

	record HeureServeur(Instant maintenant) {
	}

	private final Clock horloge;

	HeureControleur(Clock horloge) {
		this.horloge = horloge;
	}

	@SubscribeMapping("/heure")
	HeureServeur heure() {
		return new HeureServeur(horloge.instant());
	}

}
