package fr.croustyquizz.tempsreel;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonValue;

/** Vue complète figée à l'assemblage. Le JSON de la vue contient uniquement ses champs. */
public record VueComposee(String type, @JsonValue Map<String, Object> champs, Instant soireeTermineeLe) implements Evenement {
	public VueComposee { champs = figer(champs); }
	public VueComposee(String type, Map<String, Object> champs) { this(type, champs, null); }
	private static Map<String, Object> figer(Map<String, Object> source) {
		Map<String, Object> copie = new LinkedHashMap<>();
		source.forEach((cle, valeur) -> copie.put(cle, figerValeur(valeur)));
		return Collections.unmodifiableMap(copie);
	}
	private static Object figerValeur(Object valeur) {
		if (valeur instanceof Map<?, ?> objet) {
			Map<String, Object> copie = new LinkedHashMap<>();
			objet.forEach((cle, element) -> copie.put(cle.toString(), figerValeur(element)));
			return Collections.unmodifiableMap(copie);
		}
		if (valeur instanceof List<?> liste) { return liste.stream().map(VueComposee::figerValeur).toList(); }
		return valeur;
	}
}
