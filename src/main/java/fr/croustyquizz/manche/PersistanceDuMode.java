package fr.croustyquizz.manche;

import java.util.UUID;

/** Port de persistance JPA du mode, exécuté dans la transaction du service commun. */
public interface PersistanceDuMode<E> {
	E charger(UUID mancheId);
	void enregistrer(UUID mancheId, Object instantane);
}
