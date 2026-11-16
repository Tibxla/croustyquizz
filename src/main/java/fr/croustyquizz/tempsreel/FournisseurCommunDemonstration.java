package fr.croustyquizz.tempsreel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import tools.jackson.databind.json.JsonMapper;

/** Soirée et Joueurs fictifs pour #80, avec programme issu du socle en dev/recette seulement. */
@Component
@Profile({"dev", "recette"})
public final class FournisseurCommunDemonstration implements FournisseurPartieCommune {
	public static final UUID JOUEUR = UUID.fromString("00000000-0000-4000-8000-000000000001");
	private final DepotDeManches depot;
	private final JsonMapper json;
	public FournisseurCommunDemonstration(DepotDeManches depot, JsonMapper json) { this.depot = depot; this.json = json; }
	@Override
	public List<UUID> joueurs(UUID soireeId) { return List.of(JOUEUR); }
	@Override
	public Map<String, Object> fournir(UUID soireeId, Destination destination) {
		List<ManchePersistante> programme = depot.deLaSoiree(soireeId);
		ManchePersistante active = programme.stream().filter(m -> m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE)
				.findFirst().orElse(null);
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put("soiree", Map.of("id", soireeId, "titre", "Soirée de démonstration", "format", "SOLO", "pilotage", "MANUEL",
				"etat", "EN_COURS", "codeAcces", "DEMO01"));
		vue.put("mancheActive", active == null ? null : decrire(active, programme.indexOf(active)));
		vue.put("modeEnCours", active == null ? null : active.getMode());
		vue.put("joueurs", List.of(Map.of("id", JOUEUR, "pseudo", "Joueur de démonstration", "connecte", true)));
		vue.put("classement", List.of()); vue.put("dernierBuzz", null);
		if (destination.type() == Destination.Type.CONSOLE) {
			vue.put("programme", programme.stream().map(m -> decrire(m, programme.indexOf(m))).toList());
		}
		if (destination.type() == Destination.Type.JOUEUR) {
			vue.put("joueurId", destination.joueurId()); vue.put("eligiblePourMancheActive", active != null);
			vue.put("derniereReponse", null); vue.put("resultatPersonnel", null);
		}
		return vue;
	}
	private Map<String, Object> decrire(ManchePersistante manche, int ordre) {
		return Map.of("id", manche.getMancheId(), "ordre", ordre, "mode", manche.getMode(),
				"etat", manche.getEtat() == EtatManche.PRETE ? "EN_ATTENTE" : manche.getEtat().name(),
				"reglages", json.readTree(manche.getReglages()));
	}
}
