package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.PersistanceDuMode;
import fr.croustyquizz.passage.PersistancePassageJpa;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class PersistanceFreestyleJpa implements PersistanceDuMode<EtatFreestyle> {
    private final EntityManager entites;
    private final JsonMapper json;
    private final PersistancePassageJpa passages;

    public PersistanceFreestyleJpa(EntityManager entites, JsonMapper json, PersistancePassageJpa passages) {
        this.entites = entites;
        this.json = json;
        this.passages = passages;
    }

    @Override public EtatFreestyle charger(UUID mancheId) {
        var file = passages.charger(mancheId);
        if (file == null) { throw new IllegalStateException("File persistée introuvable."); }
        return new EtatFreestyle(file, passages.chargerCycle(mancheId), passages.chargerVotes(mancheId), contenus(mancheId));
    }

    @Override public void enregistrer(UUID mancheId, Object instantane) {
        var etat = ((InstantaneFreestyle) instantane).etat();
        passages.enregistrer(mancheId, etat.file());
        etat.contenus().forEach((id, contenu) ->
                entites.merge(new ContenuFreestylePersistant(id, json.writeValueAsString(contenu))));
        passages.enregistrerCycle(mancheId, etat.cycle());
        passages.enregistrerVotes(mancheId, etat.voteDuPublic());
    }

    public Map<UUID, EtatFreestyle.Contenu> contenus(UUID mancheId) {
        Map<UUID, EtatFreestyle.Contenu> resultat = new HashMap<>();
        entites.createQuery("select c from ContenuFreestylePersistant c where c.passageId in "
                + "(select p.id from PassagePersistant p where p.mancheId = :manche)", ContenuFreestylePersistant.class)
                .setParameter("manche", mancheId).getResultList().forEach(c ->
                        resultat.put(c.getPassageId(), json.readValue(c.getContenu(), EtatFreestyle.Contenu.class)));
        return resultat;
    }

    public List<Instrumentale> instrumentales(UUID soireeId) {
        var catalogue = entites.find(CatalogueFreestylePersistant.class, soireeId);
        return catalogue == null ? List.of() : json.readValue(catalogue.getFiches(), new TypeReference<List<Instrumentale>>() { });
    }

    /** Fenêtre d’Écran de salle qui a déclaré les fiches en dernier, ou null avant toute déclaration. */
    public UUID lecteurDesigne(UUID soireeId) {
        var catalogue = entites.find(CatalogueFreestylePersistant.class, soireeId);
        return catalogue == null ? null : catalogue.getLecteurId();
    }

    public void declarer(UUID soireeId, List<Instrumentale> fiches, UUID lecteurId) {
        entites.merge(new CatalogueFreestylePersistant(soireeId, json.writeValueAsString(fiches), lecteurId));
    }
}
