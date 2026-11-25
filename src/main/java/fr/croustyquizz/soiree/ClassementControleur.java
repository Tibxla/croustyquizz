package fr.croustyquizz.soiree;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.soiree.ClassementDeSoiree.LigneClassement;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import fr.croustyquizz.tempsreel.VerrousSoiree;

@RestController
class ClassementControleur {

	private final FournisseurCommunSoiree commun;
	private final VerrousSoiree verrous;

	ClassementControleur(FournisseurCommunSoiree commun, VerrousSoiree verrous) {
		this.commun = commun;
		this.verrous = verrous;
	}

	@GetMapping("/api/soirees/{soireeId}/classement")
	ResponseEntity<Classement> consulter(@PathVariable UUID soireeId, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, null);
		var lignes = verrous.executer(soireeId, () -> commun.classement(soireeId));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Classement(soireeId, lignes));
	}

	record Classement(UUID soireeId, List<LigneClassement> classement) {
	}
}
