package fr.croustyquizz.manche;

import static java.time.temporal.ChronoUnit.MICROS;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Réponse durable d'un envoi, y compris un refus calculé avant le verrou. */
@Entity
@Table(name = "manche_envoi")
public class EnvoiDeManche {
	@Id private UUID id;
	private String acteur;
	private String route;
	private UUID envoiId;
	private String empreinte;
	private int statut;
	@JdbcTypeCode(SqlTypes.JSON) private String corps;
	private Instant creeLe;
	protected EnvoiDeManche() { }
	public EnvoiDeManche(Acteur acteur, String route, UUID envoiId, String empreinte,
			int statut, String corps, Instant creeLe) {
		this.id = cle(acteur, route, envoiId); this.acteur = acteur.getName(); this.route = route;
		this.envoiId = envoiId; this.empreinte = empreinte; this.statut = statut; this.corps = corps; this.creeLe = creeLe.truncatedTo(MICROS);
	}
	public static UUID cle(Acteur acteur, String route, UUID envoiId) {
		return UUID.nameUUIDFromBytes((acteur.getName() + "\n" + route + "\n" + envoiId).getBytes(StandardCharsets.UTF_8));
	}
	public UUID getId() { return id; }
	public String getEmpreinte() { return empreinte; }
	public int getStatut() { return statut; }
	public String getCorps() { return corps; }
}
