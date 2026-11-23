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

    private FileAttente(EtatFileAttente etat) {
        soireeId = etat.soireeId(); mancheId = etat.mancheId();
        limite = etat.limitePassagesEnAttente(); tourOuvert = etat.tourOuvert();
        prochainOrdre = etat.prochainOrdreInscription(); prochaineCle = etat.prochaineCleOrdre();
        prioritaire = etat.passagePrioritaireId();
        passages = new ArrayList<>(etat.passages());
        consommations = new HashMap<>(etat.derniersToursConsommes());
        if (passages.stream().anyMatch(p -> p.interpretes().size() != 1)) {
            throw new IllegalArgumentException("Cette tranche exige exactement un Interprète.");
        }
    }

    public static FileAttente depuis(EtatFileAttente etat) { return new FileAttente(etat); }

    public EtatFileAttente etat() {
        return new EtatFileAttente(soireeId, mancheId, limite, tourOuvert,
                prochainOrdre, prochaineCle, prioritaire, passages, consommations);
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
        UUID interprete = nouveau.interpretes().getFirst();
        Optional<Refuse> refus = verifierInscription(interprete, nouveau.contenuId());
        if (refus.isPresent()) { return new ResultatFile.Refus(refus.get()); }
        long tour = Math.max(tourOuvert, consommations.getOrDefault(interprete, 0L) + 1);
        tour += passages.stream().filter(p -> p.etat() == EtatPassage.EN_ATTENTE
                && p.interpretes().contains(interprete)).count();
        passages.add(new Passage(nouveau.id(), nouveau.soireeId(), nouveau.mancheId(),
                nouveau.inscripteurId(), nouveau.interpretes(), nouveau.contenuId(), nouveau.duree(),
                nouveau.numero(), 0, EtatPassage.EN_ATTENTE, nouveau.recueLe(), prochainOrdre,
                tour, prochaineCle, null, null));
        prochainOrdre++;
        prochaineCle++;
        return new ResultatFile.Modifiee();
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

    /** Réserve par ordre d’inscription ; seules les attentes changeant de Tour rejoignent sa fin. */
    private void recalculerReservations() {
        Map<UUID, Long> prochainsTours = new HashMap<>();
        for (Passage passage : List.copyOf(passages)) {
            if (passage.etat() != EtatPassage.EN_ATTENTE) { continue; }
            UUID interprete = passage.interpretes().getFirst();
            long tour = prochainsTours.getOrDefault(interprete,
                    Math.max(tourOuvert, consommations.getOrDefault(interprete, 0L) + 1));
            prochainsTours.put(interprete, tour + 1);
            if (passage.tourNormal() != tour) {
                remplacer(modifier(passage, tour, prochaineCle, passage.etat(),
                        passage.tourConsomme(), passage.traceTerminale(), true));
                prochaineCle++;
            }
        }
    }

    public ResultatFile desinscrire(UUID joueurId, CiblePassage cible, Instant recueLe) {
        Passage passage = trouver(cible.passageId());
        if (passage == null || !passage.inscripteurId().equals(joueurId)) {
            return new ResultatFile.Refus(new Refuse(404, "RESSOURCE_INTROUVABLE", "Ce Passage est introuvable."));
        }
        return retirerAvecTrace(joueurId, cible, recueLe, "DESINSCRIPTION");
    }

    public ResultatFile retirer(UUID acteurId, CiblePassage cible, Instant recueLe) {
        return retirerAvecTrace(acteurId, cible, recueLe, "RETRAIT_ANIMATEUR");
    }

    private ResultatFile retirerAvecTrace(UUID acteurId, CiblePassage cible, Instant recueLe, String motif) {
        Refuse refus = verifierCible(cible);
        if (refus != null) { return new ResultatFile.Refus(refus); }
        Passage passage = trouver(cible.passageId());
        remplacer(modifier(passage, passage.tourNormal(), passage.cleOrdre(), EtatPassage.RETIRE,
                null, new TraceTerminale(motif, acteurId, recueLe), true));
        if (passage.id().equals(prioritaire)) { leverPriorite(); }
        recalculerReservations();
        return new ResultatFile.Modifiee();
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
        UUID interprete = passage.interpretes().getFirst();
        boolean estPrioritaire = passage.id().equals(prioritaire);
        long tour = estPrioritaire ? Math.max(tourOuvert, consommations.getOrDefault(interprete, 0L) + 1) : passage.tourNormal();
        if (!estPrioritaire) { tourOuvert = tour; }
        consommations.put(interprete, tour);
        Passage appele = modifier(passage, passage.tourNormal(), passage.cleOrdre(), EtatPassage.APPELE, tour, null, true);
        remplacer(appele);
        if (estPrioritaire) { leverPriorite(); }
        recalculerReservations();
        return Optional.of(appele);
    }

    public ResultatFile appelerPassage(CiblePassage cible) {
        Refuse refus = verifierCible(cible);
        if (refus != null) { return new ResultatFile.Refus(refus); }
        if (!ordreEffectif().getFirst().id().equals(cible.passageId())) { return new ResultatFile.Refus(perimee()); }
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
