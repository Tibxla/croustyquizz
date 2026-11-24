package fr.croustyquizz.manche;

import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Résultat de préparation externe : refus durable ou appel qui revalide sous le verrou. */
public record PreparationDeCommande(Refuse refus, BiFunction<DeroulementDeManche, Instant, ResultatDeCommande> appliquer) {
	public static PreparationDeCommande prete(Function<DeroulementDeManche, ResultatDeCommande> appliquer) {
		return prete((deroulement, recueLe) -> appliquer.apply(deroulement));
	}
	public static PreparationDeCommande prete(BiFunction<DeroulementDeManche, Instant, ResultatDeCommande> appliquer) {
		return new PreparationDeCommande(null, appliquer);
	}
	public static PreparationDeCommande refusee(Refuse refus) { return new PreparationDeCommande(refus, null); }
}
