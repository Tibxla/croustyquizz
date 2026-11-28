package fr.croustyquizz.blindtest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.EnvoiDeCommande;
import fr.croustyquizz.manche.ReponsesDeCommande;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Proposition de titre du Joueur qui a la main ; le Buzz passe par la route commune {@code envoyerBuzz}. */
@RestController
@RequestMapping("/api/soirees/{soireeId}/manches/{mancheId}/morceaux/{etapeId}")
public class BlindTestControleur {

	private static final long NUMERO_MAX = 9_007_199_254_740_991L;
	private static final int LONGUEUR_MAX = 200;
	private final ServiceBlindTest service;

	public BlindTestControleur(ServiceBlindTest service) {
		this.service = service;
	}

	@PutMapping("/brouillon")
	public ResponseEntity<?> deposerBrouillonProposition(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
			@PathVariable UUID etapeId, @Valid @RequestBody Brouillon corps, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
		return ReponsesDeCommande.http(service.deposerBrouillon(mancheId, etapeId, acteur, corps.numero(),
				new BrouillonProposition(corps.titre(), corps.artiste())), route(soireeId, mancheId, etapeId) + "/brouillon");
	}

	@PostMapping("/proposition")
	public ResponseEntity<?> validerProposition(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
			@PathVariable UUID etapeId, @Valid @RequestBody Proposition corps, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
		String route = route(soireeId, mancheId, etapeId) + "/proposition";
		var envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(), corps);
		return ReponsesDeCommande.http(service.validerProposition(mancheId, etapeId, envoi,
				new BrouillonProposition(corps.titre(), corps.artiste())), route);
	}

	@PostMapping("/arbitrage")
	public ResponseEntity<?> arbitrerProposition(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
			@PathVariable UUID etapeId, @RequestHeader("Idempotency-Key") UUID envoiId,
			@Valid @RequestBody Arbitrage corps, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		String route = route(soireeId, mancheId, etapeId) + "/arbitrage";
		var envoi = new EnvoiDeCommande(acteur, route, envoiId, corps);
		return ReponsesDeCommande.http(service.arbitrer(mancheId, etapeId, envoi, corps.propositionId(),
				corps.partie(), corps.verdict()), route);
	}

	private static String route(UUID soireeId, UUID mancheId, UUID etapeId) {
		return "/api/soirees/" + soireeId + "/manches/" + mancheId + "/morceaux/" + etapeId;
	}

	/** Corps BrouillonProposition du contrat. */
	public record Brouillon(@NotNull @Min(1) @Max(NUMERO_MAX) Long numero,
			@NotNull @Size(max = LONGUEUR_MAX) String titre, @NotNull @Size(max = LONGUEUR_MAX) String artiste) {
		@JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) {
			throw new IllegalArgumentException("Champ non prévu dans un brouillon de Proposition de titre.");
		}
	}

	/** Corps ArbitrageSaisie du contrat ; NON_DEMANDE n'est pas un verdict qu'on puisse fixer. */
	public record Arbitrage(@NotNull UUID propositionId, @NotNull PartieDuMorceau partie,
			@NotNull VerdictChamp verdict) {
		public Arbitrage {
			if (verdict == VerdictChamp.NON_DEMANDE) {
				throw new IllegalArgumentException("Un arbitrage rend un champ juste ou faux.");
			}
		}

		@JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) {
			throw new IllegalArgumentException("Champ non prévu dans un arbitrage.");
		}
	}

	/** Corps PropositionSaisie du contrat : aucune identité, date ni valeur de points fournie par le navigateur. */
	public record Proposition(@NotNull UUID envoiId, @NotNull @Size(max = LONGUEUR_MAX) String titre,
			@NotNull @Size(max = LONGUEUR_MAX) String artiste) {
		@JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) {
			throw new IllegalArgumentException("Champ non prévu dans une Proposition de titre.");
		}
	}

}
