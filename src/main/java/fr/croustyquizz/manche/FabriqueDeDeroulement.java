package fr.croustyquizz.manche;

/** Fabrique déclarée par chaque mode ; la restauration conserve notamment la pause. */
public interface FabriqueDeDeroulement<R, E> {
	ModeDeJeu mode();
	Class<R> typeReglages();
	ReglagesDeManche reglagesCommuns(R reglages);
	DeroulementDeManche creer(ContexteDeManche contexte, R reglages);
	DeroulementDeManche restaurer(ContexteDeManche contexte, R reglages, E etat, EtatManche etatManche);
	/** Le mode précise la cause d'une mutation de minuteur pour ses propres phases. */
	default String typeEvenementMinuteur(DeroulementDeManche deroulement) {
		return deroulement.estTerminee() ? "manche-terminee" : "question-ouverte";
	}
}
