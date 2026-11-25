package fr.croustyquizz.manche;

import static java.time.temporal.ChronoUnit.MICROS;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Transition en cours d'une Soirée en Pilotage Automatique (#123) : la Manche qui vient de finir et l'échéance de
 * la suite. Une nouvelle fin de Manche remplace la ligne ; l'échéance identifie le rappel qui peut la consommer.
 */
@Entity
@Table(name = "manche_transition")
class TransitionPersistante {
	@Id private UUID soireeId;
	private UUID mancheTermineeId;
	private Instant finPrevueLe;

	protected TransitionPersistante() { }

	/** L'échéance est tronquée à la microseconde, la précision de PostgreSQL, pour se comparer après relecture. */
	TransitionPersistante(UUID soireeId, UUID mancheTermineeId, Instant finPrevueLe) {
		this.soireeId = soireeId;
		this.mancheTermineeId = mancheTermineeId;
		this.finPrevueLe = finPrevueLe.truncatedTo(MICROS);
	}

	UUID getSoireeId() { return soireeId; }
	UUID getMancheTermineeId() { return mancheTermineeId; }
	Instant getFinPrevueLe() { return finPrevueLe; }
}
