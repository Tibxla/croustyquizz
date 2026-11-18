package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.PersistanceDuMode;
import fr.croustyquizz.passage.PersistancePassageJpa;
import jakarta.persistence.EntityManager;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** File et contenus partagent la transaction du service commun. */
@org.springframework.stereotype.Repository
public class PersistanceKaraokeJpa implements PersistanceDuMode<EtatKaraokePersiste> {
    private final EntityManager entites;
    private final JsonMapper json;
    private final PersistancePassageJpa passages;
    public PersistanceKaraokeJpa(EntityManager entites, JsonMapper json, PersistancePassageJpa passages) {
        this.entites = entites; this.json = json; this.passages = passages;
    }
    @Override public EtatKaraokePersiste charger(UUID mancheId) {
        var file = passages.charger(mancheId);
        if (file == null) { throw new IllegalStateException("File persistée introuvable."); }
        Map<UUID, ContenuKaraoke> contenus = new HashMap<>();
        entites.createQuery("select c from ContenuPassagePersistant c where c.passageId in "
                + "(select p.id from PassagePersistant p where p.mancheId = :manche)", ContenuPassagePersistant.class)
                .setParameter("manche", mancheId).getResultList().forEach(c -> contenus.put(c.getPassageId(), c.restaurer(json)));
        return new EtatKaraokePersiste(file, contenus);
    }
    @Override public void enregistrer(UUID mancheId, Object instantane) {
        var etat = (InstantaneKaraoke) instantane;
        passages.enregistrer(mancheId, etat.file());
        etat.contenus().forEach((id, contenu) -> entites.merge(new ContenuPassagePersistant(id, contenu, json)));
    }
}
