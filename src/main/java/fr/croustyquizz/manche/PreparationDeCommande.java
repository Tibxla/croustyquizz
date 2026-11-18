package fr.croustyquizz.manche;

import java.util.function.Function;

/** Résultat de préparation externe : refus durable ou appel qui revalide sous le verrou. */
public record PreparationDeCommande(Refuse refus, Function<DeroulementDeManche, ResultatDeCommande> appliquer) {
	public static PreparationDeCommande prete(Function<DeroulementDeManche, ResultatDeCommande> appliquer) {
		return new PreparationDeCommande(null, appliquer);
	}
	public static PreparationDeCommande refusee(Refuse refus) { return new PreparationDeCommande(refus, null); }
}
