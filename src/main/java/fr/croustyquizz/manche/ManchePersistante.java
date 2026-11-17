package fr.croustyquizz.manche;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** État commun durable ; les données internes d'un mode restent dans ses propres tables. */
@Entity
@Table(name = "manche_deroulement")
public class ManchePersistante {
	@Id private UUID mancheId;
	private UUID soireeId;
	@Enumerated(EnumType.STRING) private ModeDeJeu mode;
	@Enumerated(EnumType.STRING) private EtatManche etat;
	@JdbcTypeCode(SqlTypes.JSON) private String reglages;
	private Instant creeLe;
	private Instant modifieLe;
	protected ManchePersistante() { }
	public ManchePersistante(UUID mancheId, UUID soireeId, ModeDeJeu mode, String reglages, Instant maintenant) {
		this.mancheId = mancheId; this.soireeId = soireeId; this.mode = mode; this.reglages = reglages;
		this.etat = EtatManche.PRETE; this.creeLe = maintenant; this.modifieLe = maintenant;
	}
	public UUID getMancheId() { return mancheId; }
	public UUID getSoireeId() { return soireeId; }
	public ModeDeJeu getMode() { return mode; }
	public EtatManche getEtat() { return etat; }
	public String getReglages() { return reglages; }
	public void changerEtat(EtatManche valeur, Instant maintenant) { etat = valeur; modifieLe = maintenant; }
}
