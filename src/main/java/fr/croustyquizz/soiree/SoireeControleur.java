package fr.croustyquizz.soiree;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import fr.croustyquizz.animateur.AnimateurConnecte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.tempsreel.AccesAuxVues;

/**
 * Routes de Soirée du contrat docs/api/openapi.yaml : creerSoiree,
 * consulterSoiree, ouvrirSoiree, creerAccesEcran, consulterQrCodeSoiree et
 * rejoindreSoiree. Toutes passent par
 * {@link ServiceSoiree} et répondent en {@code Cache-Control: no-store}.
 * <p>
 * L'en-tête Idempotency-Key de l'ouverture est accepté sans être
 * stocké : rouvrir une Soirée déjà ouverte la rend inchangée.
 */
@RestController
class SoireeControleur {

	private final ServiceSoiree service;
	private final ServiceDesAcces acces;

	SoireeControleur(ServiceSoiree service, ServiceDesAcces acces) {
		this.service = service;
		this.acces = acces;
	}

	@PostMapping("/api/soirees")
	ResponseEntity<ProjectionSoiree> creer(AnimateurConnecte animateur, @RequestBody DemandeDeCreation demande) {
		// Un champ requis absent viole le schéma (400) ; un titre vide après nettoyage est refusé par la Soirée (422).
		if (demande.titre() == null || demande.format() == null || demande.pilotage() == null) {
			throw new Refuse(400, "REQUETE_INVALIDE", "Le titre, le Format et le Pilotage sont obligatoires.").exception();
		}
		Soiree soiree = service.creer(demande.titre(), demande.format(), demande.pilotage(), animateur.lieuId());
		return ResponseEntity.created(URI.create("/api/soirees/" + soiree.getId()))
				.cacheControl(CacheControl.noStore())
				.body(ProjectionSoiree.de(soiree));
	}

	@GetMapping("/api/soirees/{soireeId}")
	ResponseEntity<ProjectionSoiree> consulter(@PathVariable UUID soireeId, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		return ok(ProjectionSoiree.de(service.consulter(soireeId)));
	}

	@PostMapping("/api/soirees/{soireeId}/ouverture")
	ResponseEntity<ProjectionSoiree> ouvrir(@PathVariable UUID soireeId, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		return ok(ProjectionSoiree.de(service.ouvrir(soireeId)));
	}

	@PostMapping("/api/soirees/rejoindre")
	ResponseEntity<ProjectionAccesJoueur> rejoindre(@RequestBody DemandeArrivee demande) {
		ArriveeDeJoueur arrivee = service.rejoindre(demande.codeAcces(), demande.pseudo());
		UUID soireeId = arrivee.soiree().getId();
		return ResponseEntity.created(URI.create("/api/soirees/" + soireeId + "/etat/manette"))
				.cacheControl(CacheControl.noStore())
				.body(ProjectionAccesJoueur.de(arrivee));
	}

	@PostMapping("/api/soirees/{soireeId}/acces-ecran")
	ResponseEntity<ProjectionAccesEcran> ouvrirEcran(@PathVariable UUID soireeId, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		OuvertureEcran ouverture = acces.ouvrirEcran(soireeId);
		return ok(new ProjectionAccesEcran(soireeId, ouverture.cle(), ouverture.acces().getExpireLe()));
	}

	/**
	 * QR code qui mène à la page d'arrivée de la Manette avec le code d'accès dans le fragment : le
	 * code n'est pas un secret, et un fragment n'est jamais envoyé au serveur. L'adresse reprend le
	 * schéma et l'hôte publics transmis par le relais HTTPS ({@code server.forward-headers-strategy})
	 * ainsi que le chemin de contexte de l'application. Le talon reçoit la même adresse sans le fragment.
	 * Accessible à l'Écran de salle de cette Soirée et à l'Animateur du Lieu ; un Joueur reçoit 403.
	 */
	@GetMapping("/api/soirees/{soireeId}/qrcode")
	ResponseEntity<String> qrCode(@PathVariable UUID soireeId, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, null);
		if (acteur.type() == Acteur.Type.JOUEUR) {
			throw new Refuse(403, "ACCES_REFUSE", "Le QR code est réservé à l’Écran de salle et à l’Animateur.").exception();
		}
		Soiree soiree = service.consulter(soireeId);
		if (!soiree.accepteLesArrivees()) {
			throw new Refuse(409, "SOIREE_NON_OUVERTE", "La Soirée n'est pas ouverte aux Joueurs.").exception();
		}
		var pageArrivee = ServletUriComponentsBuilder.fromCurrentContextPath().path("/rejoindre.html");
		String adresseManette = pageArrivee.toUriString();
		String cibleQrCode = pageArrivee.fragment("code=" + soiree.getCodeAcces()).toUriString();
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.header("Adresse-Manette", adresseManette)
				.contentType(MediaType.valueOf("image/svg+xml"))
				.body(QrCodeSvg.de(cibleQrCode));
	}

	private static <T> ResponseEntity<T> ok(T corps) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corps);
	}

	/** Corps de creerSoiree ({@code CreationSoiree} dans schemas.yaml). */
	record DemandeDeCreation(String titre, Format format, Pilotage pilotage) {
	}

	/** Réponse de creerAccesEcran ({@code AccesEcran} dans schemas.yaml). */
	record ProjectionAccesEcran(UUID soireeId, String cleEcran, Instant expireLe) {

		/** La clé ne doit jamais finir dans un journal. */
		@Override
		public String toString() {
			return "ProjectionAccesEcran[soireeId=" + soireeId + ", cleEcran=***, expireLe=" + expireLe + "]";
		}

	}

	/** Corps de rejoindreSoiree ({@code Rejoindre} dans schemas.yaml). */
	record DemandeArrivee(String codeAcces, String pseudo) {
	}

}
