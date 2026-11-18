package fr.croustyquizz.tempsreel;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Fournit les destinations aux pages de démonstration depuis les constantes Java. */
@RestController
@Profile({"dev", "recette"})
class CanauxDeTestControleur {

	record CanauxDeTest(String connexion, String salle, String console, String buzz) {
	}

	@GetMapping("/api/test/soirees/{soireeId}/canaux")
	CanauxDeTest canaux(@PathVariable UUID soireeId) {
		return new CanauxDeTest(ConfigurationTempsReel.POINT_DE_CONNEXION,
				Canaux.salle(soireeId), Canaux.console(soireeId),
				"/api/test/soirees/" + soireeId + "/buzz");
	}
}
