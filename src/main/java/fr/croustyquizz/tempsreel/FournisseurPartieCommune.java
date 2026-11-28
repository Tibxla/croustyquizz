package fr.croustyquizz.tempsreel;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import fr.croustyquizz.manche.Destination;

/** Projection commune immuable, sans bloc de mode ; implémentée par FournisseurCommunSoiree (package soiree). */
public interface FournisseurPartieCommune {
	record VuesCommunes(Map<Destination, Map<String, Object>> parties, Instant soireeTermineeLe) { }

	Map<String, Object> fournir(UUID soireeId, Destination destination);

	/** Permet de lire une seule fois ce que les destinations ont en commun. */
	default VuesCommunes fournir(UUID soireeId, Set<Destination> destinations) {
		Map<Destination, Map<String, Object>> parties = new LinkedHashMap<>();
		for (Destination destination : destinations) {
			parties.put(destination, fournir(soireeId, destination));
		}
		return new VuesCommunes(parties, null);
	}

	List<UUID> joueurs(UUID soireeId);
}
