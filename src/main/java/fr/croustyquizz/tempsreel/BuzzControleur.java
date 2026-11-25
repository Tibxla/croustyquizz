package fr.croustyquizz.tempsreel;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.soiree.ServiceSoiree;

/**
 * Buzz du prototype, réservé aux Soirées fictives : une Manette envoie
 * un POST HTTP de démonstration, toute la salle reçoit une {@link VueSallePrototype}.
 * L'heure de réception est celle du serveur, seule à faire foi.
 */
@RestController
@Profile({"dev", "recette"})
public class BuzzControleur {

	static final int LONGUEUR_MAX_PSEUDO = 30;
	private static final int NOMBRE_MAX_BUZZ = 64;
	private final VerrousSoiree verrous;
	private final Map<UUID, List<BuzzRecu>> historiques = new ConcurrentHashMap<>();

	public record DemandeDeBuzz(String pseudo) {
	}

	private final DiffuseurTempsReel diffuseur;
	private final Clock horloge;
	private final ServiceSoiree soirees;

	BuzzControleur(DiffuseurTempsReel diffuseur, Clock horloge, VerrousSoiree verrous, ServiceSoiree soirees) {
		this.diffuseur = diffuseur;
		this.horloge = horloge;
		this.verrous = verrous;
		this.soirees = soirees;
	}

	@PostMapping("/api/test/soirees/{soireeId}/buzz")
	BuzzRecu buzzer(@PathVariable UUID soireeId, @RequestBody DemandeDeBuzz demande) {
		if (demande == null || demande.pseudo() == null || demande.pseudo().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un pseudo est requis");
		}
		return verrous.executer(soireeId, () -> {
			if (soirees.estEnregistree(soireeId)) {
				throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Soirée de démonstration introuvable.").exception();
			}
			BuzzRecu buzz = new BuzzRecu(demande.pseudo().strip().codePoints().limit(LONGUEUR_MAX_PSEUDO)
					.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString(), horloge.instant());
			List<BuzzRecu> historique = historiques.computeIfAbsent(soireeId, cle -> new ArrayList<>());
			historique.add(buzz);
			if (historique.size() > NOMBRE_MAX_BUZZ) {
				historique.removeFirst();
			}
			diffuseur.diffuserSalle(soireeId, new VueSallePrototype(historique));
			return buzz;
		});
	}

}
