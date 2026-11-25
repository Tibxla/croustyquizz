package fr.croustyquizz.tempsreel;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Pouvoir;
import tools.jackson.databind.json.JsonMapper;

/**
 * Champ {@code delegation} de la Manette (#67) : les pouvoirs de ce seul Joueur, puis les seules données qu'ils
 * demandent. Null sans Délégation ou une fois la Soirée terminée ; une Manette ne voit jamais celle d'un autre.
 */
@Component
class FournisseurDeDelegation implements FournisseurDeChampCommun {
	private final ControleDesDelegations delegations;
	private final Map<Pouvoir, DonneesDeDelegation> donnees = new EnumMap<>(Pouvoir.class);
	private final JsonMapper json;

	FournisseurDeDelegation(ControleDesDelegations delegations, List<DonneesDeDelegation> donnees, JsonMapper json) {
		this.delegations = delegations;
		this.json = json;
		for (var fournisseur : donnees) {
			if (this.donnees.put(fournisseur.pouvoir(), fournisseur) != null) {
				throw new IllegalArgumentException("Deux fournisseurs pour le pouvoir " + fournisseur.pouvoir() + ".");
			}
		}
	}

	@Override
	public String champ() { return "delegation"; }

	@Override
	public boolean concerne(Destination destination) { return destination.type() == Destination.Type.JOUEUR; }

	@Override
	public Object fournir(UUID soireeId, Destination destination, Map<String, Object> partieCommune) {
		if (!concerne(destination) || soireeTerminee(partieCommune)) {
			return null;
		}
		Set<Pouvoir> pouvoirs = delegations.pouvoirs(new Acteur(Acteur.Type.JOUEUR, destination.joueurId(), soireeId));
		if (pouvoirs.isEmpty()) {
			return null;
		}
		Map<String, Object> delegation = new LinkedHashMap<>();
		delegation.put("pouvoirs", List.copyOf(pouvoirs));
		for (Pouvoir pouvoir : pouvoirs) {
			var fournisseur = donnees.get(pouvoir);
			if (fournisseur == null) {
				throw new IllegalStateException("Aucune donnée pour le pouvoir " + pouvoir + ".");
			}
			delegation.put(fournisseur.champ(), fournisseur.fournir(soireeId));
		}
		return delegation;
	}

	private boolean soireeTerminee(Map<String, Object> partieCommune) {
		Object soiree = partieCommune.get("soiree");
		return soiree != null && "TERMINEE".equals(json.valueToTree(soiree).path("etat").asString());
	}
}
