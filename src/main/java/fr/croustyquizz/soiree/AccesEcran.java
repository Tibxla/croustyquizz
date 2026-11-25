package fr.croustyquizz.soiree;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Droit de lecture d'un Écran de salle sur une seule Soirée, prouvé par une clé
 * dont seule l'empreinte est conservée. Il ne permet ni commande ni vue privée.
 */
@Entity
public class AccesEcran {

	/** Une Soirée tient dans une nuit : la clé expire avant le lendemain midi. */
	static final Duration DUREE = Duration.ofHours(12);

	@Id
	private UUID id;

	private UUID soireeId;

	private byte[] empreinteCle;

	private Instant creeLe;

	private Instant expireLe;

	protected AccesEcran() {
		// Pour JPA.
	}

	AccesEcran(UUID soireeId, String cle, Instant maintenant) {
		this.id = UUID.randomUUID();
		this.soireeId = Objects.requireNonNull(soireeId, "soireeId");
		this.empreinteCle = Secrets.empreinte(Objects.requireNonNull(cle, "cle"));
		this.creeLe = Objects.requireNonNull(maintenant, "maintenant");
		this.expireLe = maintenant.plus(DUREE);
	}

	boolean estValideA(Instant instant) {
		return instant.isBefore(expireLe);
	}

	public UUID getId() {
		return id;
	}

	public UUID getSoireeId() {
		return soireeId;
	}

	public Instant getExpireLe() {
		return expireLe;
	}

}
