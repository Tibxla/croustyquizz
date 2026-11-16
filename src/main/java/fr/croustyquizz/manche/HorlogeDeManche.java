package fr.croustyquizz.manche;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BiConsumer;

/** Replace chaque expiration dans le même parcours transactionnel que les commandes. */
final class HorlogeDeManche implements Horloge {
	private static final org.slf4j.Logger JOURNAL = org.slf4j.LoggerFactory.getLogger(HorlogeDeManche.class);
	private final Horloge horloge;
	private final BiConsumer<DeroulementDeManche, Runnable> executer;
	private DeroulementDeManche deroulement;
	HorlogeDeManche(Horloge horloge, BiConsumer<DeroulementDeManche, Runnable> executer) {
		this.horloge = horloge; this.executer = executer;
	}
	void attacher(DeroulementDeManche valeur) { deroulement = valeur; }
	@Override
	public Instant maintenant() { return horloge.maintenant(); }
	@Override
	public Minuteur programmer(Duration delai, Runnable action) {
		return horloge.programmer(delai, () -> {
			try { executer.accept(deroulement, action); }
			catch (RuntimeException erreur) { JOURNAL.error("Échec du traitement d'un minuteur de Manche", erreur); throw erreur; }
		});
	}
}
