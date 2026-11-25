package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.ContexteDeManche;
import fr.croustyquizz.manche.DeroulementDeManche;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.FabriqueDeDeroulement;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.ReglagesDeManche;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Création et restauration du Blind test ; la sélection tirée à l'ajout de la Manche est lue au démarrage. */
// « BlindTest » nomme le Mode de jeu : PMD prend à tort la classe pour une classe de test.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class FabriqueBlindTest implements FabriqueDeDeroulement<ReglagesBlindTest, EtatBlindTestPersiste> {

	private final SelectionsDeMorceaux selections;
	private final JoueursPresents joueursPresents;
	private final Supplier<UUID> identifiants;

	public FabriqueBlindTest(SelectionsDeMorceaux selections, JoueursPresents joueursPresents) {
		this(selections, joueursPresents, UUID::randomUUID);
	}

	public FabriqueBlindTest(SelectionsDeMorceaux selections, JoueursPresents joueursPresents,
			Supplier<UUID> identifiants) {
		this.selections = Objects.requireNonNull(selections, "selections");
		this.joueursPresents = Objects.requireNonNull(joueursPresents, "joueursPresents");
		this.identifiants = Objects.requireNonNull(identifiants, "identifiants");
	}

	@Override
	public ModeDeJeu mode() {
		return ModeDeJeu.BLIND_TEST;
	}

	@Override
	public Class<ReglagesBlindTest> typeReglages() {
		return ReglagesBlindTest.class;
	}

	@Override
	public ReglagesDeManche reglagesCommuns(ReglagesBlindTest reglages) {
		return new ReglagesDeManche(reglages.nombreMorceaux(), Duration.ofMillis(LectureExtrait.DUREE_MS));
	}

	@Override
	public DeroulementBlindTest creer(ContexteDeManche contexte, ReglagesBlindTest reglages) {
		List<Morceau> selection = selections.de(contexte.mancheId());
		if (selection.size() != reglages.nombreMorceaux()) {
			throw new IllegalStateException("La sélection ne correspond pas aux réglages de la Manche.");
		}
		return new DeroulementBlindTest(contexte, selection, joueursPresents, identifiants);
	}

	@Override
	public DeroulementBlindTest restaurer(ContexteDeManche contexte, ReglagesBlindTest reglages,
			EtatBlindTestPersiste etat, EtatManche etatManche) {
		if (etatManche == EtatManche.PRETE) {
			throw new IllegalStateException("Une Manche prête ne se restaure pas.");
		}
		return new DeroulementBlindTest(contexte, joueursPresents, identifiants, etat, etatManche);
	}

	@Override
	public String typeEvenementMinuteur(DeroulementDeManche deroulement) {
		return deroulement.estTerminee() ? DeroulementBlindTest.MANCHE_TERMINEE
				: ((DeroulementBlindTest) deroulement).causeDernierMinuteur();
	}

}
