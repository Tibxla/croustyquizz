package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Refuse;
import java.util.*;
import java.time.Instant;
import java.time.Duration;

/** Agrégat de la File, reconstruit à partir d’une capture indépendante. */
public final class FileAttente {
    private final UUID soireeId;
    private final UUID mancheId;
    private long limite;
    private long tourOuvert;
    private long prochainOrdre;
    private long prochaineCle;
    private UUID prioritaire;
    private final List<Passage> passages;
    private final Map<UUID, Long> consommations;
    private final List<InvitationPassage> invitations;

    private FileAttente(EtatFileAttente etat) {
        soireeId = etat.soireeId(); mancheId = etat.mancheId();
        limite = etat.limitePassagesEnAttente(); tourOuvert = etat.tourOuvert();
        prochainOrdre = etat.prochainOrdreInscription(); prochaineCle = etat.prochaineCleOrdre();
        prioritaire = etat.passagePrioritaireId();
        passages = new ArrayList<>(etat.passages());
        consommations = new HashMap<>(etat.derniersToursConsommes());
        invitations = new ArrayList<>(etat.invitations());
    }

    public static FileAttente depuis(EtatFileAttente etat) { return new FileAttente(etat); }

    public EtatFileAttente etat() {
        return new EtatFileAttente(soireeId, mancheId, limite, tourOuvert,
                prochainOrdre, prochaineCle, prioritaire, passages, consommations, invitations);
    }

    public Optional<Refuse> verifierInscription(UUID interpreteId, String contenuId) {
        List<Passage> personnelles = passages.stream().filter(p -> p.etat() == EtatPassage.EN_ATTENTE
                && p.interpretes().contains(interpreteId)).toList();
        if (personnelles.stream().anyMatch(p -> p.contenuId().equals(contenuId))) {
            return Optional.of(new Refuse(409, "PASSAGE_DEJA_INSCRIT", "Cette Chanson est déjà en attente pour toi."));
        }
        if (personnelles.size() >= limite) {
            return Optional.of(new Refuse(409, "LIMITE_ATTEINTE", "Ta limite de Passages en attente est atteinte."));
        }
        return Optional.empty();
    }

    public ResultatFile inscrire(NouveauPassage nouveau) {
        return inscrire(nouveau, List.of());
    }

    /**
     * Inscrit le Passage puis invite les Joueurs cités, sans qu’ils deviennent Interprètes. La présence des
     * invités est vérifiée par l’appelant.
     */
    public ResultatFile inscrire(NouveauPassage nouveau, List<UUID> invitesIds) {
        long tour = tourOuvert;
        for (UUID interprete : nouveau.interpretes()) {
            Optional<Refuse> refus = verifierInscription(interprete, nouveau.contenuId());
            if (refus.isPresent()) { return new ResultatFile.Refus(refus.get()); }
            tour = Math.max(tour, tourReservable(interprete, null));
        }
        if (invitesIds.stream().anyMatch(nouveau.interpretes()::contains)) { return new ResultatFile.Refus(autoInvitation()); }
        if (new HashSet<>(invitesIds).size() != invitesIds.size()
                || nouveau.interpretes().size() + invitesIds.size() > Passage.NOMBRE_MAX_INTERPRETES) {
            throw new IllegalArgumentException("Au plus trois invités distincts, vérifiés par le contrat.");
        }
        passages.add(new Passage(nouveau.id(), nouveau.soireeId(), nouveau.mancheId(),
                nouveau.inscripteurId(), nouveau.interpretes(), nouveau.contenuId(), nouveau.duree(),
                nouveau.numero(), 0, EtatPassage.EN_ATTENTE, nouveau.recueLe(), prochainOrdre,
                tour, prochaineCle, null, null));
        prochainOrdre++;
        prochaineCle++;
        invitesIds.forEach(id -> invitations.add(new InvitationPassage(nouveau.id(), id, InvitationPassage.Etat.EN_ATTENTE)));
        return new ResultatFile.Modifiee();
    }

    private long premierTourDisponible(UUID interprete) {
        return Math.max(tourOuvert, consommations.getOrDefault(interprete, 0L) + 1);
    }

    /** Premier Tour que l’Interprète peut réserver après ses attentes, sans compter le Passage exclu. */
    private long tourReservable(UUID interprete, UUID passageExclu) {
        long dernierTourReserve = passages.stream().filter(p -> p.etat() == EtatPassage.EN_ATTENTE
                && !p.id().equals(passageExclu) && p.interpretes().contains(interprete))
                .mapToLong(Passage::tourNormal).max().orElse(0L);
        return Math.max(premierTourDisponible(interprete), dernierTourReserve + 1);
    }

    public ResultatFile ajouterInterprete(CiblePassage cible, UUID interpreteId) {
        Passage passage = trouver(cible.passageId());
        Refuse refusCible = verifierCible(cible, passage, Set.of(EtatPassage.EN_ATTENTE));
        if (refusCible != null) { return new ResultatFile.Refus(refusCible); }
        if (passage.interpretes().contains(interpreteId)) { return new ResultatFile.SansEffet(); }
        if (passage.interpretes().size() >= Passage.NOMBRE_MAX_INTERPRETES) {
            return new ResultatFile.Refus(new Refuse(422, "DONNEES_INVALIDES", "Ce Passage réunit déjà quatre Interprètes."));
        }
        Optional<Refuse> refus = verifierInscription(interpreteId, passage.contenuId());
        if (refus.isPresent()) { return new ResultatFile.Refus(refus.get()); }

        List<UUID> interpretes = new ArrayList<>(passage.interpretes());
        interpretes.add(interpreteId);
        // Seul le Passage accepté peut reculer ; les autres attentes gardent leur Tour et leur compteur (#135).
        long tour = passage.tourNormal();
        if (tourReservable(interpreteId, passage.id()) > tour) {
            for (UUID interprete : interpretes) { tour = Math.max(tour, tourReservable(interprete, passage.id())); }
        }
        long cle = passage.cleOrdre();
        if (tour != passage.tourNormal()) { cle = prochaineCle; prochaineCle++; }
        remplacer(modifier(avecInterpretes(passage, passage.inscripteurId(), interpretes), tour, cle,
                passage.etat(), passage.tourConsomme(), passage.traceTerminale(), true));
        return new ResultatFile.Modifiee();
    }

    /** Retire un Interprète du Passage visé avant son appel. */
    public ResultatFile retirerInterprete(CiblePassage cible, UUID interpreteId, Instant recueLe) {
        Passage passage = trouver(cible.passageId());
        if (passage == null || !passage.interpretes().contains(interpreteId)) {
            return new ResultatFile.Refus(introuvable());
        }
        Refuse refus = verifierCible(cible, passage, Set.of(EtatPassage.EN_ATTENTE));
        if (refus != null) { return new ResultatFile.Refus(refus); }
        retirerInterpreteDesAttentes(interpreteId, recueLe, List.of(passage));
        return new ResultatFile.Modifiee();
    }

    /**
     * Réponse d’un invité à son Invitation (#66, #138), dans l’ordre des refus du contrat. Seule l’acceptation fait
     * de l’invité un Interprète, sous réserve de son doublon personnel et de sa limite ; un refus de ces gardes
     * laisse l’Invitation en suspens. Le refus ne change ni les Interprètes ni le compteur du Passage.
     */
    public ResultatFile repondreInvitation(UUID inviteId, CiblePassage cible, boolean accepte) {
        Passage passage = trouver(cible.passageId());
        InvitationPassage invitation = passage == null ? null : invitationDe(passage.id(), inviteId);
        if (invitation == null) {
            return new ResultatFile.Refus(new Refuse(404, "RESSOURCE_INTROUVABLE", "Cette Invitation est introuvable."));
        }
        Refuse refus = verifierCible(cible, passage, Set.of(EtatPassage.EN_ATTENTE));
        if (refus != null) { return new ResultatFile.Refus(refus); }
        if (!invitation.enSuspens()) {
            return new ResultatFile.Refus(new Refuse(409, "COMMANDE_PERIMEE", "Cette Invitation est déjà close."));
        }
        if (!accepte) {
            changerEtat(invitation, InvitationPassage.Etat.REFUSEE);
            return new ResultatFile.Modifiee();
        }
        ResultatFile resultat = ajouterInterprete(cible, inviteId);
        if (resultat instanceof ResultatFile.Modifiee) { changerEtat(invitation, InvitationPassage.Etat.ACCEPTEE); }
        return resultat;
    }

    private InvitationPassage invitationDe(UUID passageId, UUID inviteId) {
        return invitations.stream().filter(i -> i.passageId().equals(passageId) && i.inviteId().equals(inviteId))
                .findFirst().orElse(null);
    }

    private void changerEtat(InvitationPassage invitation, InvitationPassage.Etat etat) {
        invitations.set(invitations.indexOf(invitation), invitation.avec(etat));
    }

    /** Fait expirer les Invitations restées en suspens d’un Passage, ou d’un Joueur parti. */
    private void expirerInvitations(java.util.function.Predicate<InvitationPassage> cible) {
        invitations.replaceAll(i -> i.enSuspens() && cible.test(i) ? i.avec(InvitationPassage.Etat.EXPIREE) : i);
    }

    private void expirerInvitations(UUID passageId) {
        expirerInvitations(i -> i.passageId().equals(passageId));
    }

    private static Refuse autoInvitation() {
        return new Refuse(422, "DONNEES_INVALIDES", "Tu ne peux pas t’inviter toi-même.");
    }

    /** Applique le départ ou l’Exclusion du Joueur à tous ses Passages encore en attente et à ses invitations. */
    public ResultatFile retirerJoueurParti(UUID joueurId, Instant recueLe) {
        List<Passage> attentes = passages.stream().filter(p -> p.etat() == EtatPassage.EN_ATTENTE
                && p.interpretes().contains(joueurId)).toList();
        boolean invite = invitations.stream().anyMatch(i -> i.enSuspens() && i.inviteId().equals(joueurId));
        expirerInvitations(i -> i.inviteId().equals(joueurId));
        if (attentes.isEmpty()) { return invite ? new ResultatFile.Modifiee() : new ResultatFile.SansEffet(); }
        retirerInterpreteDesAttentes(joueurId, recueLe, attentes);
        return new ResultatFile.Modifiee();
    }

    private void retirerInterpreteDesAttentes(UUID joueurId, Instant recueLe, List<Passage> attentes) {
        Set<UUID> passagesModifies = new HashSet<>();
        for (Passage passage : attentes) {
            List<UUID> restants = passage.interpretes().stream().filter(id -> !id.equals(joueurId)).toList();
            if (restants.isEmpty()) {
                retirerPassage(passage, new TraceTerminale("DEPART_INTERPRETE", joueurId, recueLe));
            } else {
                UUID inscripteur = passage.inscripteurId().equals(joueurId) ? restants.getFirst() : passage.inscripteurId();
                remplacer(avecInterpretes(passage, inscripteur, restants));
                passagesModifies.add(passage.id());
            }
        }
        recalculerReservations(passagesModifies);
    }

    private static Passage avecInterpretes(Passage p, UUID inscripteurId, List<UUID> interpretes) {
        return new Passage(p.id(), p.soireeId(), p.mancheId(), inscripteurId, interpretes,
                p.contenuId(), p.duree(), p.numero(), p.numeroModification(), p.etat(), p.inscriptionLe(),
                p.ordreInscription(), p.tourNormal(), p.cleOrdre(), p.tourConsomme(), p.traceTerminale());
    }

    private List<Passage> ordreNormal() {
        return passages.stream().filter(p -> p.etat() == EtatPassage.EN_ATTENTE)
                .sorted(Comparator.comparingLong(Passage::tourNormal).thenComparingLong(Passage::cleOrdre)).toList();
    }

    public List<Passage> ordreEffectif() {
        List<Passage> ordre = new ArrayList<>(ordreNormal());
        if (prioritaire != null) {
            Passage passage = ordre.stream().filter(p -> p.id().equals(prioritaire)).findFirst().orElseThrow();
            ordre.remove(passage); ordre.addFirst(passage);
        }
        return List.copyOf(ordre);
    }

    public Map<UUID, Long> positions() {
        Map<UUID, Long> positions = new LinkedHashMap<>();
        long position = 1;
        for (Passage passage : ordreEffectif()) {
            positions.put(passage.id(), position);
            position++;
        }
        return Collections.unmodifiableMap(positions);
    }

    public Passage trouver(UUID id) {
        return passages.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    private Refuse verifierCible(CiblePassage cible) {
        return verifierCible(cible, trouver(cible.passageId()), Set.of(EtatPassage.EN_ATTENTE));
    }

    private static Refuse verifierCible(CiblePassage cible, Passage passage, Set<EtatPassage> etats) {
        if (passage == null) { return introuvable(); }
        if (!etats.contains(passage.etat()) || passage.numeroModification() != cible.numeroModification()) { return perimee(); }
        return null;
    }

    private static Refuse introuvable() {
        return new Refuse(404, "RESSOURCE_INTROUVABLE", "Ce Passage est introuvable.");
    }

    static Refuse perimee() {
        return new Refuse(409, "COMMANDE_PERIMEE", "Ce Passage a changé ; consulte la File actualisée.");
    }

    private void remplacer(Passage passage) {
        for (int i = 0; i < passages.size(); i++) {
            if (passages.get(i).id().equals(passage.id())) { passages.set(i, passage); return; }
        }
        throw new IllegalArgumentException("Passage inconnu.");
    }

    private Passage modifier(Passage p, long tour, long cle, EtatPassage etat,
            Long tourConsomme, TraceTerminale trace, boolean incrementer) {
        return new Passage(p.id(), p.soireeId(), p.mancheId(), p.inscripteurId(), p.interpretes(),
                p.contenuId(), p.duree(), p.numero(), p.numeroModification() + (incrementer ? 1 : 0),
                etat, p.inscriptionLe(), p.ordreInscription(), tour, cle, tourConsomme, trace);
    }

    private void incrementer(Passage p) {
        remplacer(modifier(p, p.tourNormal(), p.cleOrdre(), p.etat(), p.tourConsomme(), p.traceTerminale(), true));
    }

    /**
     * Réserve dans l’ordre normal courant, et non dans l’ordre d’inscription : un Passage accepté reste derrière les
     * attentes qu’il a contournées. Seules les attentes changeant de Tour rejoignent sa fin.
     */
    private void recalculerReservations(Set<UUID> passagesModifies) {
        Map<UUID, Long> prochainsTours = new HashMap<>();
        for (Passage passage : ordreNormal()) {
            long tour = tourOuvert;
            for (UUID interprete : passage.interpretes()) {
                tour = Math.max(tour, prochainsTours.getOrDefault(interprete, premierTourDisponible(interprete)));
            }
            for (UUID interprete : passage.interpretes()) {
                prochainsTours.put(interprete, tour + 1);
            }
            boolean tourModifie = passage.tourNormal() != tour;
            if (tourModifie || passagesModifies.contains(passage.id())) {
                long cle = tourModifie ? prochaineCle : passage.cleOrdre();
                if (tourModifie) { prochaineCle++; }
                remplacer(modifier(passage, tour, cle, passage.etat(),
                        passage.tourConsomme(), passage.traceTerminale(), true));
            }
        }
    }

    /**
     * L’inscripteur retire tout son Passage ; un Interprète invité le quitte seulement, et le Passage reste dans
     * la File (#139). Un invité qui a quitté ou dont l’Invitation est close reste dans la portée du Passage.
     */
    public ResultatFile desinscrire(UUID joueurId, CiblePassage cible, Instant recueLe) {
        Passage passage = trouver(cible.passageId());
        if (passage == null || (!passage.interpretes().contains(joueurId) && invitationDe(passage.id(), joueurId) == null)) {
            return new ResultatFile.Refus(new Refuse(404, "RESSOURCE_INTROUVABLE", "Ce Passage est introuvable."));
        }
        if (passage.inscripteurId().equals(joueurId)) { return retirerAvecTrace(joueurId, cible, recueLe, "DESINSCRIPTION"); }
        Refuse refus = verifierCible(cible, passage, Set.of(EtatPassage.EN_ATTENTE));
        if (refus != null) { return new ResultatFile.Refus(refus); }
        if (!passage.interpretes().contains(joueurId)) { return new ResultatFile.Refus(perimee()); }
        retirerInterpreteDesAttentes(joueurId, recueLe, List.of(passage));
        // Quitter le Passage revient à retirer son accord : l’inscripteur le voit comme un refus.
        InvitationPassage invitation = invitationDe(passage.id(), joueurId);
        if (invitation != null) { changerEtat(invitation, InvitationPassage.Etat.REFUSEE); }
        return new ResultatFile.Modifiee();
    }

    public ResultatFile retirer(UUID acteurId, CiblePassage cible, Instant recueLe) {
        return retirerAvecTrace(acteurId, cible, recueLe, "RETRAIT_ANIMATEUR");
    }

    private ResultatFile retirerAvecTrace(UUID acteurId, CiblePassage cible, Instant recueLe, String motif) {
        Refuse refus = verifierCible(cible);
        if (refus != null) { return new ResultatFile.Refus(refus); }
        retirerPassage(trouver(cible.passageId()), new TraceTerminale(motif, acteurId, recueLe));
        recalculerReservations(Set.of());
        return new ResultatFile.Modifiee();
    }

    private void retirerPassage(Passage passage, TraceTerminale trace) {
        expirerInvitations(passage.id());
        remplacer(modifier(passage, passage.tourNormal(), passage.cleOrdre(), EtatPassage.RETIRE,
                null, trace, true));
        if (passage.id().equals(prioritaire)) { leverPriorite(); }
    }

    /** Le contrat persiste explicitement null quand aucune priorité n’est définie. */
    @SuppressWarnings("PMD.NullAssignment")
    private void leverPriorite() { prioritaire = null; }

    public ResultatFile definirPriorite(CiblePassage cible, boolean priorite) {
        Refuse refus = verifierCible(cible);
        if (refus != null) { return new ResultatFile.Refus(refus); }
        boolean dejaPrioritaire = cible.passageId().equals(prioritaire);
        if (priorite == dejaPrioritaire) { return new ResultatFile.SansEffet(); }
        if (prioritaire != null) { incrementer(trouver(prioritaire)); }
        if (priorite) { prioritaire = cible.passageId(); } else { leverPriorite(); }
        if (priorite) { incrementer(trouver(cible.passageId())); }
        return new ResultatFile.Modifiee();
    }

    public ResultatFile deplacer(CiblePassage cible, long tour, CiblePassage avant) {
        if (tour <= 0) { throw new IllegalArgumentException("Le Tour doit être positif."); }
        Refuse refus = verifierCible(cible);
        if (refus != null) { return new ResultatFile.Refus(refus); }
        Passage passage = trouver(cible.passageId());
        if (avant != null && avant.passageId().equals(cible.passageId())) {
            return new ResultatFile.Refus(new Refuse(422, "DONNEES_INVALIDES", "Un Passage ne peut pas être sa propre ancre."));
        }
        if (tour != passage.tourNormal() || passage.id().equals(prioritaire)) { return rotationProtegee(); }
        Passage ancre = null;
        if (avant != null) {
            refus = verifierCible(avant);
            if (refus != null) { return new ResultatFile.Refus(refus); }
            ancre = trouver(avant.passageId());
            if (ancre.tourNormal() != tour) {
                return new ResultatFile.Refus(new Refuse(409, "COMMANDE_PERIMEE", "L’ancre a quitté le Tour demandé."));
            }
            if (ancre.id().equals(prioritaire)) { return rotationProtegee(); }
        }
        List<Passage> ordre = new ArrayList<>(ordreNormal().stream().filter(p -> p.tourNormal() == tour).toList());
        int position = ordre.indexOf(passage);
        if ((ancre == null && position == ordre.size() - 1)
                || (ancre != null && position + 1 == ordre.indexOf(ancre))) { return new ResultatFile.SansEffet(); }
        ordre.remove(passage);
        ordre.add(ancre == null ? ordre.size() : ordre.indexOf(ancre), passage);
        for (Passage p : ordre) {
            remplacer(modifier(p, tour, prochaineCle, p.etat(), p.tourConsomme(), p.traceTerminale(), p.id().equals(passage.id())));
            prochaineCle++;
        }
        return new ResultatFile.Modifiee();
    }

    private ResultatFile rotationProtegee() {
        return new ResultatFile.Refus(new Refuse(409, "ROTATION_PROTEGEE", "Ce placement ne respecte pas les Tours ou la priorité."));
    }

    public Optional<Passage> extraireProchainPassage(boolean courantPresent) {
        if (courantPresent) { throw new IllegalStateException("Un Passage est déjà courant."); }
        List<Passage> ordre = ordreEffectif();
        if (ordre.isEmpty()) { return Optional.empty(); }
        Passage passage = ordre.getFirst();
        boolean estPrioritaire = passage.id().equals(prioritaire);
        long tour = estPrioritaire ? tourOuvert : passage.tourNormal();
        if (!estPrioritaire) { tourOuvert = tour; }
        // En priorité, la File mémorise le Tour de chacun et le Passage conserve le plus tardif.
        for (UUID interprete : passage.interpretes()) {
            long tourInterprete = estPrioritaire ? premierTourDisponible(interprete) : passage.tourNormal();
            consommations.put(interprete, tourInterprete);
            tour = Math.max(tour, tourInterprete);
        }
        Passage appele = modifier(passage, passage.tourNormal(), passage.cleOrdre(), EtatPassage.APPELE, tour, null, true);
        // À l’appel, les invitations restées en suspens expirent sans erreur (#66).
        expirerInvitations(passage.id());
        remplacer(appele);
        if (estPrioritaire) { leverPriorite(); }
        recalculerReservations(Set.of());
        return Optional.of(appele);
    }

    public ResultatFile appelerPassage(CiblePassage cible) {
        return appelerPassage(cible, () -> { });
    }

    /** Prépare la chronologie du cycle après les gardes et avant de consommer le Tour. */
    ResultatFile appelerPassage(CiblePassage cible, Runnable preparerAppel) {
        Refuse refus = verifierCible(cible);
        if (refus != null) { return new ResultatFile.Refus(refus); }
        if (!ordreEffectif().getFirst().id().equals(cible.passageId())) { return new ResultatFile.Refus(perimee()); }
        preparerAppel.run();
        extraireProchainPassage(false);
        return new ResultatFile.Modifiee();
    }

    public ResultatFile demarrerPassage(CiblePassage cible) {
        return transiter(cible, EtatPassage.EN_COURS, null, Set.of(EtatPassage.APPELE));
    }

    public ResultatFile clorePassage(CiblePassage cible, EtatPassage etatTerminal, TraceTerminale trace) {
        Set<EtatPassage> departs = switch (etatTerminal) {
            case TERMINE -> Set.of(EtatPassage.EN_COURS);
            case SAUTE, ANNULE -> Set.of(EtatPassage.APPELE, EtatPassage.EN_COURS);
            default -> Set.of();
        };
        return transiter(cible, etatTerminal, trace, departs);
    }

    private ResultatFile transiter(CiblePassage cible, EtatPassage etat, TraceTerminale trace, Set<EtatPassage> departs) {
        Passage passage = trouver(cible.passageId());
        Refuse refus = verifierCible(cible, passage, departs);
        if (refus != null) { return new ResultatFile.Refus(refus); }
        if (etat != EtatPassage.EN_COURS && (trace == null || !trace.correspondA(etat))) {
            throw new IllegalArgumentException("La clôture exige un motif, un instant et l’acteur correspondant.");
        }
        remplacer(modifier(passage, passage.tourNormal(), passage.cleOrdre(), etat, passage.tourConsomme(), trace, true));
        return new ResultatFile.Modifiee();
    }

    public ResultatFile reglerLimite(long limitePassagesEnAttente) {
        EtatFileAttente.verifierLimite(limitePassagesEnAttente);
        if (limite == limitePassagesEnAttente) { return new ResultatFile.SansEffet(); }
        limite = limitePassagesEnAttente;
        return new ResultatFile.Modifiee();
    }

    public ResultatFile annulerAttentes(Instant recueLe, UUID acteurId) {
        boolean modifiee = false;
        for (Passage passage : List.copyOf(passages)) {
            if (passage.etat() == EtatPassage.EN_ATTENTE) {
                expirerInvitations(passage.id());
                remplacer(modifier(passage, passage.tourNormal(), passage.cleOrdre(), EtatPassage.ANNULE,
                        null, new TraceTerminale(TraceTerminale.MANCHE_TERMINEE, acteurId, recueLe), true));
                modifiee = true;
            }
        }
        leverPriorite();
        return modifiee ? new ResultatFile.Modifiee() : new ResultatFile.SansEffet();
    }

    public Map<UUID, AttenteEstimee> estimerAttentes(Instant maintenant, boolean enPause,
            Instant prochainAppelPrevuLe, boolean courantPresent, Duration delaiAppel) {
        EtatAttente etat = enPause ? EtatAttente.SUSPENDUE
                : courantPresent && prochainAppelPrevuLe == null ? EtatAttente.INCONNUE : EtatAttente.ESTIMEE;
        long attente = courantPresent && prochainAppelPrevuLe != null
                ? Math.max(0, Duration.between(maintenant, prochainAppelPrevuLe).toMillis()) : 0;
        Map<UUID, AttenteEstimee> estimations = new LinkedHashMap<>();
        for (Passage passage : ordreEffectif()) {
            estimations.put(passage.id(), new AttenteEstimee(maintenant, etat,
                    etat == EtatAttente.ESTIMEE ? attente : null));
            if (etat == EtatAttente.ESTIMEE) { attente += passage.duree().plus(delaiAppel).toMillis(); }
        }
        return Collections.unmodifiableMap(estimations);
    }
}
