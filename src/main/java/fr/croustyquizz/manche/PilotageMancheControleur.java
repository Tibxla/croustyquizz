package fr.croustyquizz.manche;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import fr.croustyquizz.tempsreel.AccesAuxVues;

/** Adapte piloterManche au service du mode ; identité et accusé restent ceux du serveur. */
@RestController
public class PilotageMancheControleur {
	private final RegistreServicesDeManche services;
	private final EtatDeSoiree soirees;
	private final SocleDeManche socle;
	private final PouvoirsDelegues pouvoirs;

	/** Sans contrôle des Délégations (contexte partiel du socle), seul l'Animateur pilote. */
	public PilotageMancheControleur(RegistreServicesDeManche services, EtatDeSoiree soirees, SocleDeManche socle,
			ObjectProvider<PouvoirsDelegues> pouvoirs) {
		this.services = services;
		this.soirees = soirees;
		this.socle = socle;
		this.pouvoirs = pouvoirs.getIfAvailable(() -> PouvoirsDelegues.ANIMATEUR_SEUL);
	}
	@PostMapping("/api/soirees/{soireeId}/manches/{mancheId}/commandes")
	public ResponseEntity<?> piloter(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
			@RequestHeader("Idempotency-Key") UUID envoiId, @Valid @RequestBody InterventionDeManche intervention, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, acteur.type() == Acteur.Type.JOUEUR ? null : Acteur.Type.ANIMATEUR);
		String route = "/api/soirees/" + soireeId + "/manches/" + mancheId + "/commandes";
		var envoi = new EnvoiDeCommande(acteur, route, envoiId, intervention);
		ResultatDeCommande resultat = socle.verrous().executer(soireeId, () -> {
			var service = services.pourManche(soireeId, mancheId);
			if (acteur.type() == Acteur.Type.JOUEUR) {
				verifierDelegue(acteur, intervention, service.mode());
			}
			if (soirees.estEnCours(soireeId)) {
				return service.piloter(mancheId, envoi, intervention);
			}
			return service.refuserPilotage(mancheId, envoi, new Refuse(409, "ETAT_INCOMPATIBLE", "La Soirée doit être en cours."));
		});
		return ReponsesDeCommande.http(resultat, route);
	}

	/**
	 * Un Joueur délégué du pouvoir FILE_ATTENTE (#67) ne fait que passer le Passage en cours d'une Manche de
	 * Karaoké ; la pause, la reprise et la fin d'une Manche restent à l'Animateur. Refus sans reçu.
	 */
	private void verifierDelegue(Acteur acteur, InterventionDeManche intervention, ModeDeJeu mode) {
		pouvoirs.autoriser(acteur, Pouvoir.FILE_ATTENTE);
		if (intervention.action() != InterventionDeManche.Action.PASSER_ETAPE || mode != ModeDeJeu.KARAOKE) {
			throw new Refuse(403, "ACCES_REFUSE", "Ta Délégation permet seulement de passer le Passage en cours.").exception();
		}
	}
}
