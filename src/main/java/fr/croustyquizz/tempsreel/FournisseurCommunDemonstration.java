package fr.croustyquizz.tempsreel;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.PilotageDeSoiree;
import tools.jackson.databind.json.JsonMapper;

/**
 * Soirée et Joueurs fictifs pour #80, avec programme issu du socle en dev/recette seulement.
 * {@code @Fallback} : il ne sert directement que s'il est seul ; sinon le fournisseur de
 * {@code soiree} l'emporte et lui confie les Soirées absentes de la base.
 */
@Component
@Profile({"dev", "recette"})
@Fallback
public final class FournisseurCommunDemonstration implements FournisseurPartieCommune {
	public static final int NOMBRE_JOUEURS = 4;

	private final DepotDeManches depot;
	private final JsonMapper json;
	private final PilotageDeSoiree pilotage;

	public FournisseurCommunDemonstration(DepotDeManches depot, JsonMapper json) {
		this(depot, json, soireeId -> false);
	}

	@Autowired
	public FournisseurCommunDemonstration(DepotDeManches depot, JsonMapper json, PilotageDeSoiree pilotage) {
		this.depot = depot;
		this.json = json;
		this.pilotage = pilotage;
	}
	/** Identité stable par Soirée, partagée par les projections et les jetons de démonstration. */
	public static UUID joueurId(UUID soireeId) {
		return joueurId(soireeId, 1);
	}
	public static UUID joueurId(UUID soireeId, int rang) {
		if (rang < 1 || rang > NOMBRE_JOUEURS) {
			throw new IllegalArgumentException("Rang de Joueur de démonstration invalide : " + rang);
		}
		String nom = "croustyquizz:demo:joueur:" + soireeId;
		return UUID.nameUUIDFromBytes((rang == 1 ? nom : nom + ":" + rang).getBytes(StandardCharsets.UTF_8));
	}
	@Override
	public List<UUID> joueurs(UUID soireeId) {
		return IntStream.rangeClosed(1, NOMBRE_JOUEURS).mapToObj(rang -> joueurId(soireeId, rang)).toList();
	}
	@Override
	public Map<String, Object> fournir(UUID soireeId, Destination destination) {
		List<ManchePersistante> programme = depot.deLaSoiree(soireeId);
		ManchePersistante active = programme.stream().filter(m -> m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE)
				.findFirst().orElse(null);
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put("soiree", Map.of("id", soireeId, "titre", "Soirée de démonstration", "format", "SOLO",
				"pilotage", pilotage.estAutomatique(soireeId) ? "AUTOMATIQUE" : "MANUEL", "etat", "EN_COURS", "codeAcces", "DEMO01"));
		vue.put("mancheActive", active == null ? null : decrire(active, programme.indexOf(active)));
		vue.put("modeEnCours", active == null ? null : active.getMode());
		vue.put("joueurs", IntStream.rangeClosed(1, NOMBRE_JOUEURS)
				.mapToObj(rang -> Map.of("id", joueurId(soireeId, rang),
						"pseudo", "Joueur de démonstration " + rang, "connecte", true))
				.toList());
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
