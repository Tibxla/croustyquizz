package fr.croustyquizz.manche;

import java.time.Instant;
import java.util.UUID;

/**
 * Prévenu quand une Manche passe à {@code TERMINEE} (fin naturelle, minuteur ou {@code piloterManche}
 * {@code TERMINER}), dans la transaction et sous le verrou de la Soirée, avant la persistance du mode et la
 * publication : la vue publiée voit déjà ce qu'il a enregistré. La clôture d'une Manche par la fin de la
 * Soirée ne le prévient pas (#123). Il apprend aussi chaque démarrage de Manche, dans la transaction du démarrage.
 */
@FunctionalInterface
public interface ObservateurDeFinDeManche {
	void surFinDeManche(UUID soireeId, UUID mancheId, Instant maintenant);

	/** Démarrage d'une Manche, au même moment de la transaction que la fin ; par défaut, rien. */
	default void surDebutDeManche(UUID soireeId, UUID mancheId) {
		// La plupart des observateurs ne suivent que la fin.
	}
}
