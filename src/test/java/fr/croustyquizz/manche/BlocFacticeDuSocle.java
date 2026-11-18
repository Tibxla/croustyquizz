package fr.croustyquizz.manche;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import fr.croustyquizz.tempsreel.FournisseurDeBloc;

/** Projection du mode factice depuis son instantané, sans exposer son état interne. */
public final class BlocFacticeDuSocle implements FournisseurDeBloc {
	private final Supplier<ServiceDeManche<?, ?>> service;
	private final DepotDeManches depot;
	public BlocFacticeDuSocle(Supplier<ServiceDeManche<?, ?>> service, DepotDeManches depot) { this.service = service; this.depot = depot; }
	@Override
	public ModeDeJeu mode() { return ModeDeJeu.QUIZ; }
	@Override
	public Object fournir(UUID soiree, Destination destination) {
		var manche = depot.deLaSoiree(soiree).stream().filter(m -> m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE)
				.findFirst().orElseThrow();
		var etat = (DeroulementFactice.Etat) service.get().instantane(soiree, manche.getMancheId());
		UUID questionId = UUID.nameUUIDFromBytes((manche.getMancheId() + ":" + etat.etape()).getBytes(StandardCharsets.UTF_8));
		var propositions = IntStream.range(0, 4).mapToObj(i -> Map.of("id", new UUID(questionId.getMostSignificantBits(), i),
				"texte", "Proposition " + i, "position", i)).toList();
		var question = Map.of("etapeId", questionId, "questionId", questionId, "numero", etat.etape(), "total", 3,
				"enonce", "Étape factice " + etat.etape(), "propositions", propositions);
		if (etat.pause()) { return Map.of("phase", "EN_PAUSE", "question", question, "tempsRestantMs", etat.reste().toMillis()); }
		return Map.of("phase", "QUESTION_OUVERTE", "question", question, "ouverteLe", etat.fin().minusSeconds(20), "echeanceLe", etat.fin());
	}
}
