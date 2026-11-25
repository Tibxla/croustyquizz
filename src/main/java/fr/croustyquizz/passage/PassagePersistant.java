package fr.croustyquizz.passage;

import static java.time.temporal.ChronoUnit.MICROS;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** Historique durable de chaque Passage et de sa trace terminale. */
@Entity
@Table(name = "passage")
public class PassagePersistant {
    @Id private UUID id;
    private UUID soireeId;
    private UUID mancheId;
    private UUID inscripteurId;
    private String contenuId;
    // La durée d’inscription provient du contenu et se mesure en millisecondes.
    private long dureeMs;
    private long numero;
    private long numeroModification;
    @Enumerated(EnumType.STRING) private EtatPassage etat;
    private Instant inscriptionLe;
    private long ordreInscription;
    private long tourNormal;
    private long cleOrdre;
    private Long tourConsomme;
    private String traceMotif;
    private UUID traceActeurId;
    private Instant traceInstant;
    protected PassagePersistant() { }
    public PassagePersistant(Passage p) {
        id = p.id(); soireeId = p.soireeId(); mancheId = p.mancheId(); inscripteurId = p.inscripteurId();
        contenuId = p.contenuId(); dureeMs = p.duree().toMillis(); numero = p.numero(); numeroModification = p.numeroModification();
        etat = p.etat(); inscriptionLe = p.inscriptionLe().truncatedTo(MICROS); ordreInscription = p.ordreInscription();
        tourNormal = p.tourNormal(); cleOrdre = p.cleOrdre(); tourConsomme = p.tourConsomme();
        if (p.traceTerminale() != null) {
            traceMotif = p.traceTerminale().motif(); traceActeurId = p.traceTerminale().acteurId(); traceInstant = p.traceTerminale().instant().truncatedTo(MICROS);
        }
    }
    public UUID getId() { return id; }
    public Passage restaurer(List<UUID> interpretes) {
        return new Passage(id, soireeId, mancheId, inscripteurId, interpretes, contenuId, Duration.ofMillis(dureeMs),
                numero, numeroModification, etat, inscriptionLe, ordreInscription, tourNormal, cleOrdre, tourConsomme,
                traceMotif == null ? null : new TraceTerminale(traceMotif, traceActeurId, traceInstant));
    }
}
