package fr.croustyquizz.manche;

import static java.time.temporal.ChronoUnit.MICROS;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Replace chaque expiration dans le même parcours transactionnel que les commandes. */
final class HorlogeDeManche implements Horloge {
	private static final org.slf4j.Logger JOURNAL = org.slf4j.LoggerFactory.getLogger(HorlogeDeManche.class);
	private final Horloge horloge;
	/** L'annulation est relue par le service sous le verrou de la Soirée. */
	record Rappel(Runnable action, BooleanSupplier annule) { }
	private final BiConsumer<Supplier<DeroulementDeManche>, Rappel> executer;
	private DeroulementDeManche deroulement;
	HorlogeDeManche(Horloge horloge, BiConsumer<Supplier<DeroulementDeManche>, Rappel> executer) {
		this.horloge = horloge; this.executer = executer;
	}
	void attacher(DeroulementDeManche valeur) { deroulement = valeur; }
	@Override
	public Instant maintenant() { return horloge.maintenant().truncatedTo(MICROS); }
	@Override
	public Minuteur programmer(Duration delai, Runnable action) {
		AtomicBoolean annule = new AtomicBoolean();
		Rappel rappel = new Rappel(action, annule::get);
		Minuteur minuteur = horloge.programmer(delai, () -> {
			// L'instance ne sera lue qu'une fois le verrou acquis, après son insertion dans le registre.
			try { executer.accept(() -> deroulement, rappel); }
			catch (RuntimeException erreur) { JOURNAL.error("Échec du traitement d'un minuteur de Manche", erreur); throw erreur; }
		});
		return () -> { annule.set(true); minuteur.annuler(); };
	}
}
