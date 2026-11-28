package fr.croustyquizz.passage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Tuple;
import java.util.*;

/** Persistance appelée uniquement dans la transaction et sous le verrou de Soirée. */
@org.springframework.stereotype.Repository
public class PersistancePassageJpa {
    private static final String PARAMETRE_MANCHE = "manche";
    private final EntityManager entites;
    public PersistancePassageJpa(EntityManager entites) { this.entites = entites; }
    public EtatFileAttente charger(UUID mancheId) {
        var file = entites.find(FileAttentePersistante.class, mancheId);
        if (file == null) { return null; }
        var parPassage = interpretesParPassage(mancheId);
        var passages = entites.createQuery("select p from PassagePersistant p where p.mancheId = :manche order by p.ordreInscription", PassagePersistant.class)
                .setParameter(PARAMETRE_MANCHE, mancheId).getResultList().stream()
                .map(p -> p.restaurer(identifiantsDesInterpretes(parPassage, p.getId()))).toList();
        Map<UUID, Long> consommations = new HashMap<>();
        entites.createQuery("select t from TourConsommePersistant t where t.mancheId = :manche", TourConsommePersistant.class)
                .setParameter(PARAMETRE_MANCHE, mancheId).getResultList().forEach(t -> consommations.put(t.getInterpreteId(), t.getDernierTourConsomme()));
        return file.restaurer(passages, consommations, invitations(mancheId).stream().map(InvitationPersistante::restaurer).toList());
    }

    private List<InvitationPersistante> invitations(UUID mancheId) {
        return entites.createQuery("select i from InvitationPersistante i where i.mancheId = :manche order by i.ordre",
                InvitationPersistante.class).setParameter(PARAMETRE_MANCHE, mancheId).getResultList();
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
        enregistrerInvitations(mancheId, etat.invitations());
    }

    /** Remplace les invitations de la Manche par celles de la capture, dans son ordre. */
    private void enregistrerInvitations(UUID mancheId, List<InvitationPassage> invitations) {
        Set<InvitationPersistante.Cle> actuelles = new HashSet<>();
        for (int ordre = 0; ordre < invitations.size(); ordre++) {
            var invitation = new InvitationPersistante(mancheId, invitations.get(ordre), ordre);
            actuelles.add(invitation.cle());
            entites.merge(invitation);
        }
        invitations(mancheId).stream().filter(i -> !actuelles.contains(i.cle())).forEach(entites::remove);
    }

    private Map<UUID, List<InterpretePersistant>> interpretesParPassage(UUID mancheId) {
        var interpretes = entites.createQuery("select i from InterpretePersistant i where i.passageId in "
                + "(select p.id from PassagePersistant p where p.mancheId = :manche) order by i.ordre", InterpretePersistant.class)
                .setParameter(PARAMETRE_MANCHE, mancheId).getResultList();
        return grouperInterpretes(interpretes);
    }

    private static Map<UUID, List<InterpretePersistant>> grouperInterpretes(List<InterpretePersistant> interpretes) {
        Map<UUID, List<InterpretePersistant>> parPassage = new HashMap<>();
        interpretes.forEach(i -> parPassage.computeIfAbsent(i.getPassageId(), cle -> new ArrayList<>()).add(i));
        return parPassage;
    }

    private static List<UUID> identifiantsDesInterpretes(Map<UUID, List<InterpretePersistant>> parPassage, UUID passageId) {
        return parPassage.getOrDefault(passageId, List.of()).stream().map(InterpretePersistant::getJoueurId).toList();
    }

    public EtatCyclePassage chargerCycle(UUID mancheId) {
        var cycle = entites.find(CyclePassagePersistant.class, mancheId);
        return cycle == null ? EtatCyclePassage.vide() : cycle.restaurer();
    }

    public void enregistrerCycle(UUID mancheId, EtatCyclePassage cycle) {
        entites.merge(new CyclePassagePersistant(mancheId, cycle));
    }

    public EtatVoteDuPublic chargerVotes(UUID mancheId) {
        var votes = entites.createQuery("""
                select v from VotePersistant v where v.mancheId = :manche order by v.ouvertLe, v.passageId
                """, VotePersistant.class).setParameter(PARAMETRE_MANCHE, mancheId).getResultList();
        if (votes.isEmpty()) { return EtatVoteDuPublic.vide(); }
        var notes = entites.createQuery("""
                select n from NotePersistante n where n.passageId in
                (select v.passageId from VotePersistant v where v.mancheId = :manche)
                order by n.recueLe, n.joueurId
                """, NotePersistante.class).setParameter(PARAMETRE_MANCHE, mancheId).getResultList();
        Map<UUID, List<VoteRecu>> parPassage = new HashMap<>();
        notes.forEach(note -> parPassage.computeIfAbsent(note.getPassageId(), id -> new ArrayList<>()).add(note.restaurer()));
        return new EtatVoteDuPublic(votes.stream()
                .map(vote -> vote.restaurer(parPassage.getOrDefault(vote.getPassageId(), List.of()))).toList());
    }

    public void enregistrerVotes(UUID mancheId, EtatVoteDuPublic etat) {
        for (EtatVotePassage vote : etat.votes()) {
            entites.merge(new VotePersistant(mancheId, vote));
            for (VoteRecu note : vote.votes()) {
                var cle = new NotePersistante.Cle(vote.passageId(), note.joueurId());
                if (entites.find(NotePersistante.class, cle) == null) {
                    entites.persist(new NotePersistante(vote.passageId(), note));
                }
            }
        }
    }

    public List<ResultatVote> resultatsDeVote(Collection<UUID> mancheIds) {
        if (mancheIds.isEmpty()) { return List.of(); }
        var votes = entites.createQuery("""
                select p.id as passageId, p.mancheId as mancheId, p.numero as numero,
                       count(n.joueurId) as nombre, coalesce(sum(n.note), 0) as somme
                from VotePersistant v
                join PassagePersistant p on p.id = v.passageId and p.mancheId = v.mancheId
                left join NotePersistante n on n.passageId = v.passageId
                where v.mancheId in :manches and v.clotureLe is not null and p.etat = :etat
                group by p.id, p.mancheId, p.numero
                """, Tuple.class).setParameter("manches", mancheIds).setParameter("etat", EtatPassage.TERMINE)
                .getResultList();
        if (votes.isEmpty()) { return List.of(); }
        var ids = votes.stream().map(v -> v.get("passageId", UUID.class)).toList();
        var interpretes = grouperInterpretes(entites.createQuery("""
                select i from InterpretePersistant i where i.passageId in :passages order by i.ordre
                """, InterpretePersistant.class).setParameter("passages", ids).getResultList());
        return votes.stream().map(vote -> {
            UUID id = vote.get("passageId", UUID.class);
            long nombre = vote.get("nombre", Long.class);
            long somme = vote.get("somme", Long.class);
            return new ResultatVote(id, vote.get("mancheId", UUID.class), vote.get("numero", Long.class),
                    identifiantsDesInterpretes(interpretes, id), nombre, somme, VoteDuPublic.moyenneDixiemes(nombre, somme));
        }).toList();
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
