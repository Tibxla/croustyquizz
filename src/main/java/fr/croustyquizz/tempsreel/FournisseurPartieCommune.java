package fr.croustyquizz.tempsreel;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import fr.croustyquizz.manche.Destination;

/** Projection commune immuable, sans bloc de mode ; implémentée par FournisseurCommunSoiree (package soiree). */
public interface FournisseurPartieCommune {
	Map<String, Object> fournir(UUID soireeId, Destination destination);
	List<UUID> joueurs(UUID soireeId);
}
