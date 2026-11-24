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
        Map<UUID, LecturePassage> lectures = new HashMap<>();
        lectures(mancheId).forEach(l -> lectures.put(l.getPassageId(), l.restaurer(json)));
        var lecteur = entites.find(LecteurChansonPersistant.class, mancheId);
        return new EtatKaraokePersiste(file, contenus, passages.chargerCycle(mancheId), lectures,
                lecteur == null ? null : lecteur.getLecteurId(), passages.chargerVotes(mancheId));
    }
    @Override public void enregistrer(UUID mancheId, Object instantane) {
        var etat = (InstantaneKaraoke) instantane;
        passages.enregistrer(mancheId, etat.file());
        etat.contenus().forEach((id, contenu) -> entites.merge(new ContenuPassagePersistant(id, contenu, json)));
        passages.enregistrerCycle(mancheId, etat.cycle());
        for (var lecture : lectures(mancheId)) {
            if (!etat.lectures().containsKey(lecture.getPassageId())) { entites.remove(lecture); }
        }
        etat.lectures().values().forEach(l -> entites.merge(new LecturePassagePersistante(l, json)));
        if (etat.lecteurDesigneId() != null) {
            entites.merge(new LecteurChansonPersistant(mancheId, etat.lecteurDesigneId()));
        } else {
            var ancien = entites.find(LecteurChansonPersistant.class, mancheId);
            if (ancien != null) { entites.remove(ancien); }
        }
        passages.enregistrerVotes(mancheId, etat.voteDuPublic());
    }

    public boolean enregistrementAutorise(UUID soireeId) {
        return !Boolean.FALSE.equals(entites.createNativeQuery("""
                SELECT autorise FROM autorisation_enregistrement WHERE soiree_id = :soiree
                """).setParameter("soiree", soireeId).getSingleResultOrNull());
    }

    public void reglerAutorisationEnregistrement(UUID soireeId, boolean autorise) {
        entites.createNativeQuery("""
                INSERT INTO autorisation_enregistrement (soiree_id, autorise) VALUES (:soiree, :autorise)
                ON CONFLICT (soiree_id) DO UPDATE SET autorise = EXCLUDED.autorise
                """).setParameter("soiree", soireeId).setParameter("autorise", autorise).executeUpdate();
    }

    public boolean accordEnregistrement(UUID passageId, UUID joueurId) {
        return Boolean.TRUE.equals(entites.createNativeQuery("""
                SELECT EXISTS (SELECT 1 FROM accord_enregistrement_passage
                    WHERE passage_id = :passage AND interprete_id = :joueur)
                """).setParameter("passage", passageId).setParameter("joueur", joueurId).getSingleResult());
    }

    public void enregistrerAccord(UUID passageId, UUID joueurId, boolean accord) {
        String requete = accord ? """
                INSERT INTO accord_enregistrement_passage (passage_id, interprete_id) VALUES (:passage, :joueur)
                """ : """
                DELETE FROM accord_enregistrement_passage WHERE passage_id = :passage AND interprete_id = :joueur
                """;
        entites.createNativeQuery(requete).setParameter("passage", passageId).setParameter("joueur", joueurId).executeUpdate();
    }

    AccordsEnregistrement accordsEnregistrement(UUID soireeId, UUID mancheId) {
        if (!enregistrementAutorise(soireeId)) { return new AccordsEnregistrement(false, Map.of()); }
        List<?> lignes = entites.createNativeQuery("""
                SELECT a.interprete_id, a.passage_id
                FROM accord_enregistrement_passage a JOIN passage p ON p.id = a.passage_id
                WHERE p.manche_id = :manche AND p.etat IN ('EN_ATTENTE', 'APPELE', 'EN_COURS')
                ORDER BY p.numero
                """).setParameter("manche", mancheId).getResultList();
        Map<UUID, List<UUID>> resultats = new HashMap<>();
        for (Object ligne : lignes) {
            var colonnes = (Object[]) ligne;
            resultats.computeIfAbsent((UUID) colonnes[0], id -> new ArrayList<>()).add((UUID) colonnes[1]);
        }
        return new AccordsEnregistrement(true, resultats);
    }

    public Map<UUID, ContenuKaraoke> contenus(Collection<UUID> passageIds) {
        if (passageIds.isEmpty()) { return Map.of(); }
        Map<UUID, ContenuKaraoke> resultat = new HashMap<>();
        entites.createQuery("select c from ContenuPassagePersistant c where c.passageId in :passages",
                ContenuPassagePersistant.class).setParameter("passages", passageIds).getResultList()
                .forEach(c -> resultat.put(c.getPassageId(), c.restaurer(json)));
        return resultat;
    }

    private List<LecturePassagePersistante> lectures(UUID mancheId) {
        return entites.createQuery("select l from LecturePassagePersistante l where l.passageId in "
                + "(select p.id from PassagePersistant p where p.mancheId = :manche)", LecturePassagePersistante.class)
                .setParameter("manche", mancheId).getResultList();
    }
}
