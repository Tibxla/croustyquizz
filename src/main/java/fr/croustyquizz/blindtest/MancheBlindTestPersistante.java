package fr.croustyquizz.blindtest;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Sélection tirée à l'ajout, puis état du déroulement, en JSON : privés au serveur. */
@Entity
@Table(name = "blindtest_manche")
public class MancheBlindTestPersistante {

	@Id
	private UUID mancheId;
	@JdbcTypeCode(SqlTypes.JSON)
	private String selection;
	@JdbcTypeCode(SqlTypes.JSON)
	private String etat;

	protected MancheBlindTestPersistante() {
		// Pour JPA.
	}

	MancheBlindTestPersistante(UUID mancheId, String selection) {
		this.mancheId = mancheId;
		this.selection = selection;
	}

	String getSelection() {
		return selection;
	}

	String getEtat() {
		return etat;
	}

	void changerEtat(String nouvelEtat) {
		etat = nouvelEtat;
	}

}
