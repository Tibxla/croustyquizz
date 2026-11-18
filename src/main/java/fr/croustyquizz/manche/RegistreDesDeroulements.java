package fr.croustyquizz.manche;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Cache vide au démarrage ; accès composés uniquement sous le verrou de la Soirée. */
@Component
public final class RegistreDesDeroulements {
	private final Map<UUID, DeroulementDeManche> deroulements = new ConcurrentHashMap<>();
	public DeroulementDeManche obtenir(UUID id, Supplier<DeroulementDeManche> restaurer) {
		return deroulements.computeIfAbsent(id, cle -> restaurer.get());
	}
	public void retirer(UUID id) { deroulements.remove(id); }
	// L’identité de l’instance invalide les callbacks d’un déroulement évincé, même s’il redéfinit equals.
	@SuppressWarnings("PMD.CompareObjectsWithEquals")
	public boolean contient(UUID id, DeroulementDeManche deroulement) { return deroulement != null && deroulements.get(id) == deroulement; }
}
