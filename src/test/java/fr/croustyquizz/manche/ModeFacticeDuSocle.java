package fr.croustyquizz.manche;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Fabrique et persistance de démonstration en test, sans installer de vrai Mode de jeu. */
public final class ModeFacticeDuSocle implements FabriqueDeDeroulement<ModeFacticeDuSocle.Reglages, DeroulementFactice.Etat>,
		PersistanceDuMode<DeroulementFactice.Etat> {
	public record Reglages(int nombreEtapes, int tempsParEtapeSecondes) { }
	final Map<UUID, DeroulementFactice.Etat> etats = new HashMap<>();
	@Override
	public ModeDeJeu mode() { return ModeDeJeu.QUIZ; }
	@Override
	public Class<Reglages> typeReglages() { return Reglages.class; }
	@Override
	public ReglagesDeManche reglagesCommuns(Reglages reglages) {
		return new ReglagesDeManche(reglages.nombreEtapes(), Duration.ofSeconds(reglages.tempsParEtapeSecondes()));
	}
	@Override
	public DeroulementDeManche creer(ContexteDeManche contexte, Reglages reglages) { return new DeroulementFactice(contexte); }
	@Override
	public DeroulementDeManche restaurer(ContexteDeManche contexte, Reglages reglages, DeroulementFactice.Etat etat, EtatManche cycle) {
		return DeroulementFactice.restaurer(contexte, etat, cycle);
	}
	@Override
	public DeroulementFactice.Etat charger(UUID mancheId) { return etats.get(mancheId); }
	@Override
	public void enregistrer(UUID mancheId, Object etat) { etats.put(mancheId, (DeroulementFactice.Etat) etat); }
}
