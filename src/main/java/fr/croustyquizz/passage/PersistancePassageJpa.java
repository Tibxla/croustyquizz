package fr.croustyquizz.passage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;

/** Persistance appelée uniquement dans la transaction et sous le verrou de Soirée. */
@org.springframework.stereotype.Repository
public class PersistancePassageJpa {
    private final EntityManager entites;
    public PersistancePassageJpa(EntityManager entites) { this.entites = entites; }
    public EtatFileAttente charger(UUID mancheId) {
        var file = entites.find(FileAttentePersistante.class, mancheId);
        if (file == null) { return null; }
        var parPassage = interpretesParPassage(mancheId);
        var passages = entites.createQuery("select p from PassagePersistant p where p.mancheId = :manche order by p.ordreInscription", PassagePersistant.class)
                .setParameter("manche", mancheId).getResultList().stream()
                .map(p -> p.restaurer(parPassage.getOrDefault(p.getId(), List.of()).stream()
                        .map(InterpretePersistant::getJoueurId).toList())).toList();
        Map<UUID, Long> consommations = new HashMap<>();
        entites.createQuery("select t from TourConsommePersistant t where t.mancheId = :manche", TourConsommePersistant.class)
                .setParameter("manche", mancheId).getResultList().forEach(t -> consommations.put(t.getInterpreteId(), t.getDernierTourConsomme()));
        return file.restaurer(passages, consommations);
    }
    public void enregistrer(UUID mancheId, EtatFileAttente etat) {
        if (!mancheId.equals(etat.mancheId())) { throw new IllegalArgumentException("File d'une autre Manche."); }
        var parPassage = interpretesParPassage(mancheId);
        List<InterpretePersistant> aRetirer = new ArrayList<>();
        List<InterpretePersistant> aInscrire = new ArrayList<>();
        for (Passage passage : etat.passages()) {
            var anciens = parPassage.getOrDefault(passage.id(), List.of());
            var actuels = passage.interpretes();
            int ordre = 0;
            while (ordre < anciens.size() && ordre < actuels.size()
                    && anciens.get(ordre).getJoueurId().equals(actuels.get(ordre))) {
                ordre++;
            }
            aRetirer.addAll(anciens.subList(ordre, anciens.size()));
            for (; ordre < actuels.size(); ordre++) {
                aInscrire.add(new InterpretePersistant(passage.id(), actuels.get(ordre), ordre));
            }
        }
        if (!aRetirer.isEmpty()) {
            aRetirer.forEach(entites::remove);
            // Libère les anciennes positions et la contrainte d’unicité par Joueur avant toute réinsertion.
            entites.flush();
        }
        entites.merge(new FileAttentePersistante(etat));
        for (Passage passage : etat.passages()) {
            entites.merge(new PassagePersistant(passage));
        }
        aInscrire.forEach(entites::persist);
        etat.derniersToursConsommes().forEach((id, tour) -> entites.merge(new TourConsommePersistant(mancheId, id, tour)));
    }

    private Map<UUID, List<InterpretePersistant>> interpretesParPassage(UUID mancheId) {
        var interpretes = entites.createQuery("select i from InterpretePersistant i where i.passageId in "
                + "(select p.id from PassagePersistant p where p.mancheId = :manche) order by i.ordre", InterpretePersistant.class)
                .setParameter("manche", mancheId).getResultList();
        Map<UUID, List<InterpretePersistant>> parPassage = new HashMap<>();
        interpretes.forEach(i -> parPassage.computeIfAbsent(i.getPassageId(), cle -> new ArrayList<>()).add(i));
        return parPassage;
    }
    public EtatCyclePassage chargerCycle(UUID mancheId) {
        var cycle = entites.find(CyclePassagePersistant.class, mancheId);
        return cycle == null ? EtatCyclePassage.vide() : cycle.restaurer();
    }

    public void enregistrerCycle(UUID mancheId, EtatCyclePassage cycle) {
        entites.merge(new CyclePassagePersistant(mancheId, cycle));
    }

    public long numeroPropose(UUID soireeId) {
        var numero = entites.find(NumeroSoireePersistant.class, soireeId);
        return numero == null ? 1 : numero.getProchainNumero();
    }
    public void incrementerNumero(UUID soireeId, long numeroAccepte) {
        var numero = entites.find(NumeroSoireePersistant.class, soireeId, LockModeType.PESSIMISTIC_WRITE);
        if (numero == null) { numero = new NumeroSoireePersistant(soireeId, 1); entites.persist(numero); }
        if (numero.getProchainNumero() != numeroAccepte) { throw new IllegalStateException("Numéro de Passage périmé."); }
        numero.avancer();
    }
}
