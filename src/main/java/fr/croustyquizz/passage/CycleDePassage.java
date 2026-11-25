package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Minuteur;
import fr.croustyquizz.manche.Refuse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Ordonne les transitions de la File et gère la chronologie du Passage courant. */
public final class CycleDePassage {
    private final FileAttente file;
    private final Horloge horloge;
    private final Duration delaiAppel;
    private final Consumer<CloturePassage> surCloture;
    private EtatCyclePassage chronologie;
    private Minuteur fin = () -> { };
    private long generation;

    public CycleDePassage(FileAttente file, Horloge horloge, Duration delaiAppel, EtatCyclePassage etat,
            Consumer<CloturePassage> surCloture) {
        this.file = file;
        this.horloge = horloge;
        this.delaiAppel = delaiAppel;
        this.chronologie = etat;
        this.surCloture = surCloture;
    }

    public EtatCyclePassage etat() { return chronologie; }

    public Passage courantActif() {
        Passage passage = file.trouver(chronologie.passageId());
        return estActif(passage) ? passage : null;
    }

    private static boolean estActif(Passage passage) {
        return passage != null && (passage.etat() == EtatPassage.APPELE || passage.etat() == EtatPassage.EN_COURS);
    }

    public ResultatFile appeler(CiblePassage cible, UUID etapeId, Instant maintenant) {
        Objects.requireNonNull(etapeId);
        return appeler(cible, maintenant, () -> etapeId);
    }

    /** L’étape n’est créée que si la File accepte l’appel. */
    public ResultatFile appeler(CiblePassage cible, Instant maintenant, Supplier<UUID> identifiants) {
        if (courantActif() != null) { return incompatible(); }
        Instant echeance = maintenant.plus(delaiAppel);
        return file.appelerPassage(cible, () -> {
            chronologie = new EtatCyclePassage(cible.passageId(), Objects.requireNonNull(identifiants.get()),
                    maintenant, echeance, null, null, Duration.ZERO, null, null);
        });
    }

    public boolean appelerSuivantSiLibre(Supplier<UUID> etapes, Instant maintenant) {
        if (courantActif() != null) { return false; }
        var ordre = file.ordreEffectif();
        if (ordre.isEmpty()) { return false; }
        Passage suivant = ordre.getFirst();
        var cible = new CiblePassage(suivant.id(), suivant.numeroModification());
        if (!(appeler(cible, maintenant, etapes) instanceof ResultatFile.Modifiee)) {
            throw new IllegalStateException("Le premier Passage en attente doit pouvoir être appelé.");
        }
        return true;
    }

    public ResultatFile demarrer(CiblePassage cible, UUID etapeId, Duration dureeReference, Instant maintenant) {
        Passage courant = courantActif();
        if (courant == null || courant.etat() != EtatPassage.APPELE || !courant.id().equals(cible.passageId())
                || !chronologie.etapeId().equals(etapeId)) {
            return new ResultatFile.Refus(FileAttente.perimee());
        }
        var depart = new EtatCyclePassage(chronologie.passageId(), chronologie.etapeId(), chronologie.appeleLe(), chronologie.echeanceAppelLe(),
                maintenant, dureeReference, Duration.ZERO, null, null);
        ResultatFile resultat = file.demarrerPassage(cible);
        if (resultat instanceof ResultatFile.Modifiee) {
            chronologie = depart;
            reprogrammerFin();
        }
        return resultat;
    }

    public ResultatFile clore(EtatPassage etatTerminal, String motif, UUID acteurId, Instant maintenant) {
        Passage courant = courantActif();
        if (courant == null) { return incompatible(); }
        var trace = new TraceTerminale(motif, acteurId, maintenant);
        var cloture = new EtatCyclePassage(chronologie.passageId(), chronologie.etapeId(), chronologie.appeleLe(), chronologie.echeanceAppelLe(),
                chronologie.demarreLe(), chronologie.dureeReference(), chronologie.tempsPause(), null, Objects.requireNonNull(maintenant));
        ResultatFile resultat = file.clorePassage(new CiblePassage(courant.id(), courant.numeroModification()), etatTerminal, trace);
        if (resultat instanceof ResultatFile.Modifiee) {
            chronologie = cloture;
            annulerFin();
            surCloture.accept(new CloturePassage(chronologie.passageId(), chronologie.etapeId(), etatTerminal, motif, maintenant));
        }
        return resultat;
    }

    public void suspendre(Instant maintenant) {
        if (courantActif() == null || chronologie.pauseDepuis() != null) { return; }
        chronologie = new EtatCyclePassage(chronologie.passageId(), chronologie.etapeId(), chronologie.appeleLe(), chronologie.echeanceAppelLe(),
                chronologie.demarreLe(), chronologie.dureeReference(), chronologie.tempsPause(), maintenant, null);
        annulerFin();
    }

    public void reprendre(Instant maintenant) {
        if (chronologie.pauseDepuis() == null) { return; }
        Passage courant = courantActif();
        if (courant == null) { return; }
        Duration pause = dureePositive(Duration.between(chronologie.pauseDepuis(), maintenant));
        boolean enAppel = courant.etat() == EtatPassage.APPELE;
        Instant echeance = enAppel ? chronologie.echeanceAppelLe().plus(pause) : chronologie.echeanceAppelLe();
        Duration tempsPause = enAppel ? chronologie.tempsPause() : chronologie.tempsPause().plus(pause);
        chronologie = new EtatCyclePassage(chronologie.passageId(), chronologie.etapeId(), chronologie.appeleLe(), echeance,
                chronologie.demarreLe(), chronologie.dureeReference(), tempsPause, null, null);
        reprogrammerFin();
    }

    /** Réarme uniquement la fin d’un Passage chanté et hors pause, notamment après restauration. */
    public void reprogrammerFin() {
        Passage courant = courantActif();
        if (courant == null || courant.etat() != EtatPassage.EN_COURS || chronologie.pauseDepuis() != null) { return; }
        annulerFin();
        long rappel = generation;
        UUID etapeId = chronologie.etapeId();
        Duration reste = dureePositive(Duration.between(horloge.maintenant(), chronologie.finPrevue()));
        fin = horloge.programmer(reste, () -> {
            if (rappel == generation && Objects.equals(chronologie.etapeId(), etapeId)) {
                clore(EtatPassage.TERMINE, TraceTerminale.FIN_PREVUE, null, horloge.maintenant());
            }
        });
    }

    private void annulerFin() {
        generation++;
        fin.annuler();
    }

    private static Duration dureePositive(Duration duree) { return duree.isNegative() ? Duration.ZERO : duree; }

    private static ResultatFile.Refus incompatible() {
        return new ResultatFile.Refus(new Refuse(409, "ETAT_INCOMPATIBLE",
                "Le courant de la File d’attente ne permet pas cette commande."));
    }

    public static ProjectionCycle projeter(EtatFileAttente file, EtatCyclePassage cycle, boolean enPause, Instant maintenant) {
        Passage courant = file.passages().stream().filter(p -> p.id().equals(cycle.passageId())).findFirst().orElse(null);
        if (courant == null) { return sansCourant(maintenant); }
        return switch (courant.etat()) {
            case APPELE -> projeterAppel(courant, cycle, enPause, maintenant);
            case EN_COURS -> projeterChant(courant, cycle, enPause, maintenant);
            case TERMINE, SAUTE -> new ProjectionCycle(courant, false, courant.etat().name(), cycle.etapeId(),
                    cycle.appeleLe(), null, null, cycle.demarreLe(), null, null, cycle.tempsPause().toMillis(),
                    cycle.clotureLe(), null, maintenant);
            default -> sansCourant(maintenant);
        };
    }

    private static ProjectionCycle sansCourant(Instant maintenant) {
        return new ProjectionCycle(null, false, null, null, null, null, null, null, null, null, 0, null, null, maintenant);
    }

    private static ProjectionCycle projeterAppel(Passage courant, EtatCyclePassage cycle, boolean enPause, Instant maintenant) {
        Instant reference = enPause && cycle.pauseDepuis() != null ? cycle.pauseDepuis() : maintenant;
        long reste = dureePositive(Duration.between(reference, cycle.echeanceAppelLe())).toMillis();
        Instant echeance = enPause ? null : cycle.echeanceAppelLe();
        Instant prochain = echeance != null && reste > 0 ? echeance.plus(courant.duree()) : null;
        return new ProjectionCycle(courant, true, "APPELE", cycle.etapeId(), cycle.appeleLe(), echeance, reste,
                null, null, null, 0, null, prochain, maintenant);
    }

    private static ProjectionCycle projeterChant(Passage courant, EtatCyclePassage cycle, boolean enPause, Instant maintenant) {
        Instant reference = enPause && cycle.pauseDepuis() != null ? cycle.pauseDepuis() : maintenant;
        Duration ecoulee = Duration.between(cycle.demarreLe(), reference).minus(cycle.tempsPause());
        long position = Math.min(cycle.dureeReference().toMillis(), dureePositive(ecoulee).toMillis());
        Instant finPrevue = enPause ? null : cycle.finPrevue();
        return new ProjectionCycle(courant, true, enPause ? "EN_PAUSE" : "EN_COURS", cycle.etapeId(), cycle.appeleLe(),
                null, null, cycle.demarreLe(), finPrevue, position, cycle.tempsPause().toMillis(), null, finPrevue, maintenant);
    }
}
