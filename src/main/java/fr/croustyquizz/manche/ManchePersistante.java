package fr.croustyquizz.manche;

import static java.time.temporal.ChronoUnit.MICROS;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** État commun durable, dont les Points acquis. Le détail du jeu reste dans les tables du mode. */
@Entity
@Table(name = "manche_deroulement")
public class ManchePersistante {
	@Id private UUID mancheId;
	private UUID soireeId;
	@Enumerated(EnumType.STRING) private ModeDeJeu mode;
	@Enumerated(EnumType.STRING) private EtatManche etat;
	@JdbcTypeCode(SqlTypes.JSON) private String reglages;
	@JdbcTypeCode(SqlTypes.JSON) private List<AttributionDePoints> points = List.of();
	private Instant creeLe;
	private Instant modifieLe;
	private Integer positionProgramme;
	protected ManchePersistante() { }
	public ManchePersistante(UUID mancheId, UUID soireeId, ModeDeJeu mode, String reglages, Instant maintenant) {
		this.mancheId = mancheId; this.soireeId = soireeId; this.mode = mode; this.reglages = reglages;
		this.etat = EtatManche.PRETE; this.creeLe = maintenant.truncatedTo(MICROS); this.modifieLe = creeLe;
	}
	public UUID getMancheId() { return mancheId; }
	public UUID getSoireeId() { return soireeId; }
	public ModeDeJeu getMode() { return mode; }
	public EtatManche getEtat() { return etat; }
	public String getReglages() { return reglages; }
	public List<AttributionDePoints> getPoints() { return List.copyOf(points); }
	public Instant getModifieLe() { return modifieLe; }
	public Integer getPositionProgramme() { return positionProgramme; }
	public void placerDansLeProgramme(int position) { positionProgramme = position; }
	public void changerEtat(EtatManche valeur, Instant maintenant) { etat = valeur; modifieLe = maintenant.truncatedTo(MICROS); }

	/** Remplace le cumul de la Manche et signale une modification de ses attributions. */
	boolean actualiserPoints(List<AttributionDePoints> attributions) {
		if (points.equals(attributions)) {
			return false;
		}
		points = List.copyOf(attributions);
		return true;
	}
}
