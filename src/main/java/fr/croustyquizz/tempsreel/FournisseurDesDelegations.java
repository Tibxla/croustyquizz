package fr.croustyquizz.tempsreel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Pouvoir;
import tools.jackson.databind.json.JsonMapper;

/**
 * Champ {@code delegations} de la Console (#67) : les Délégations en cours des Joueurs présents, dans l'ordre
 * d'arrivée de la partie commune. Vide une fois la Soirée terminée.
 */
@Component
class FournisseurDesDelegations implements FournisseurDeChampCommun {
	private final ControleDesDelegations delegations;
	private final JsonMapper json;

	FournisseurDesDelegations(ControleDesDelegations delegations, JsonMapper json) {
		this.delegations = delegations;
		this.json = json;
	}

	@Override
	public String champ() { return "delegations"; }

	@Override
	public boolean concerne(Destination destination) { return Destination.CONSOLE.equals(destination); }

	@Override
	public Object fournir(UUID soireeId, Destination destination, Map<String, Object> partieCommune) {
		List<ServiceDesDelegations.Delegation> liste = new ArrayList<>();
		Map<UUID, Set<Pouvoir>> enCours = concerne(destination) ? delegations.deLaSoiree(soireeId) : Map.of();
		if (enCours.isEmpty()) {
			return liste;
		}
		var commun = json.valueToTree(partieCommune);
		if ("TERMINEE".equals(commun.path("soiree").path("etat").asString())) {
			return liste;
		}
		for (var joueur : commun.path("joueurs")) {
			UUID id = UUID.fromString(joueur.path("id").asString());
			var pouvoirs = enCours.get(id);
			if (pouvoirs != null) {
				liste.add(new ServiceDesDelegations.Delegation(id, joueur.path("pseudo").asString(), List.copyOf(pouvoirs)));
			}
		}
		return liste;
	}
}
