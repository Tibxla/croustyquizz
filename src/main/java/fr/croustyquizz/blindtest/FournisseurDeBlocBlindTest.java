package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.tempsreel.FournisseurDeBloc;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;

/** Projection de la seule Manche de Blind test active, sous le verrou de l'assembleur. */
// « BlindTest » nomme le Mode de jeu : PMD prend à tort la classe pour une classe de test.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class FournisseurDeBlocBlindTest implements FournisseurDeBloc {

	private final DepotDeManches depot;
	private final ObjectProvider<ServiceBlindTest> service;

	public FournisseurDeBlocBlindTest(DepotDeManches depot, ObjectProvider<ServiceBlindTest> service) {
		this.depot = depot;
		this.service = service;
	}

	@Override
	public ModeDeJeu mode() {
		return ModeDeJeu.BLIND_TEST;
	}

	@Override
	public Object fournir(UUID soireeId, Destination destination) {
		return preparer(soireeId).apply(destination);
	}

	@Override
	public Function<Destination, Object> preparer(UUID soireeId) {
		var active = depot.deLaSoiree(soireeId).stream().filter(manche -> manche.getMode() == mode()
				&& (manche.getEtat() == EtatManche.EN_COURS || manche.getEtat() == EtatManche.EN_PAUSE)).findFirst();
		if (active.isEmpty()) {
			return destination -> null;
		}
		UUID mancheId = active.get().getMancheId();
		var instantane = (InstantaneBlindTest) service.getObject().instantane(soireeId, mancheId);
		return destination -> VuesBlindTest.projeter(mancheId, instantane, destination);
	}

}
