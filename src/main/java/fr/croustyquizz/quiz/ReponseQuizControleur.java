package fr.croustyquizz.quiz;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.EnvoiDeCommande;
import fr.croustyquizz.manche.RegistreServicesDeManche;
import fr.croustyquizz.manche.ReponsesDeCommande;
import fr.croustyquizz.manche.ResultatDeCommande;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReponseQuizControleur {

	private final RegistreServicesDeManche services;

	public ReponseQuizControleur(RegistreServicesDeManche services) {
		this.services = services;
	}

	@PostMapping("/api/soirees/{soireeId}/manches/{mancheId}/questions/{etapeId}/reponses")
	public ResponseEntity<?> envoyerReponse(
			@PathVariable UUID soireeId,
			@PathVariable UUID mancheId,
			@PathVariable UUID etapeId,
			@Valid @RequestBody ReponseSaisie saisie,
			Acteur acteur
	) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
		String route = "/api/soirees/" + soireeId + "/manches/" + mancheId + "/questions/" + etapeId + "/reponses";
		ServiceQuiz service = (ServiceQuiz) services.pourManche(soireeId, mancheId);
		ResultatDeCommande resultat = service.repondre(mancheId, new EnvoiDeCommande(acteur, route, saisie.envoiId(), saisie), etapeId, saisie.propositionId());
		return ReponsesDeCommande.http(resultat, route);
	}
}
