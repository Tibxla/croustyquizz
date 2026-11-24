package fr.croustyquizz.manche;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Choix conservé en mémoire par Soirée. Après un redémarrage, la Soirée
 * redevient Manuelle et ses déroulements restaurés reçoivent {@code false}.
 */
@Component
@Profile({"dev", "recette"})
@Fallback
public final class PilotageDeSoireeDemonstration implements PilotageDeSoiree {
	private final Set<UUID> soireesAutomatiques = ConcurrentHashMap.newKeySet();

	public void choisir(UUID soireeId, boolean automatique) {
		if (automatique) {
			soireesAutomatiques.add(soireeId);
		} else {
			soireesAutomatiques.remove(soireeId);
		}
	}

	@Override
	public boolean estAutomatique(UUID soireeId) {
		return soireesAutomatiques.contains(soireeId);
	}
}
