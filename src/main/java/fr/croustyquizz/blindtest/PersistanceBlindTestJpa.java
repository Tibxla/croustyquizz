package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.PersistanceDuMode;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persistance du Blind test dans la transaction du service commun : la sélection est écrite à l'ajout de la Manche,
 * l'état après chaque mutation.
 */
@Repository
public class PersistanceBlindTestJpa implements PersistanceDuMode<EtatBlindTestPersiste>, SelectionsDeMorceaux {

	private final EntityManager entites;
	private final JsonMapper json;

	public PersistanceBlindTestJpa(EntityManager entites, JsonMapper json) {
		this.entites = entites;
		this.json = json;
	}

	/** À appeler dans la transaction qui ajoute la Manche, juste après elle. */
	void enregistrerSelection(UUID mancheId, List<Morceau> selection) {
		entites.persist(new MancheBlindTestPersistante(mancheId, json.writeValueAsString(selection)));
	}

	@Override
	public List<Morceau> de(UUID mancheId) {
		return json.readValue(trouver(mancheId).getSelection(), new TypeReference<List<Morceau>>() { });
	}

	@Override
	public EtatBlindTestPersiste charger(UUID mancheId) {
		String etat = trouver(mancheId).getEtat();
		if (etat == null) {
			throw new IllegalStateException("État du Blind test introuvable.");
		}
		return json.readValue(etat, EtatBlindTestPersiste.class);
	}

	@Override
	public void enregistrer(UUID mancheId, Object instantane) {
		trouver(mancheId).changerEtat(json.writeValueAsString(((InstantaneBlindTest) instantane).etat()));
	}

	private MancheBlindTestPersistante trouver(UUID mancheId) {
		MancheBlindTestPersistante manche = entites.find(MancheBlindTestPersistante.class, mancheId);
		if (manche == null) {
			throw new IllegalStateException("Sélection du Blind test introuvable.");
		}
		return manche;
	}

}
