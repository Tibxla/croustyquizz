package fr.croustyquizz.manche;

import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Table de mode factice, uniquement sur le classpath de test. */
@Entity
@Table(name = "test_manche_etat")
class EtatFacticePersistant {
	@Id private UUID mancheId;
	@JdbcTypeCode(SqlTypes.JSON) private String donnees;
	protected EtatFacticePersistant() { }
	EtatFacticePersistant(UUID mancheId, String donnees) { this.mancheId = mancheId; this.donnees = donnees; }
	String getDonnees() { return donnees; }
}
