package fr.croustyquizz.manche;

import java.time.Instant;
import java.util.UUID;

/**
 * Prévenu quand une Manche passe à {@code TERMINEE} (fin naturelle, minuteur ou {@code piloterManche}
 * {@code TERMINER}), dans la transaction et sous le verrou de la Soirée, avant la persistance du mode et la
 * publication : la vue publiée voit déjà ce qu'il a enregistré. La clôture d'une Manche par la fin de la
 * Soirée ne le prévient pas (#123).
 */
@FunctionalInterface
public interface ObservateurDeFinDeManche {
	void surFinDeManche(UUID soireeId, UUID mancheId, Instant maintenant);
}
