package fr.croustyquizz.karaoke;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Entity
@Table(name = "karaoke_lecture_passage")
public class LecturePassagePersistante {
    @Id private UUID passageId;
    private UUID lectureId;
    private UUID lecteurId;
    @Enumerated(EnumType.STRING) private EtatLecture etat;
    @Enumerated(EnumType.STRING) private SourceLecture source;
    @Enumerated(EnumType.STRING) private MotifParolesSeules motifParolesSeules;
    @Enumerated(EnumType.STRING) private OrigineParoles origineParoles;
    private String fichierNom;
    private Long fichierTailleOctets;
    private Long fichierDureeMs;
    private UUID parolesId;
    private long dureeParolesMs;
    @JdbcTypeCode(SqlTypes.JSON) private String lignesParoles;
    private long versionSourceId;
    private long decalageMs;
    private long numeroModificationCalage;
    private UUID demandeId;
    private Instant demandeeLe;

    protected LecturePassagePersistante() { }

    LecturePassagePersistante(LecturePassage lecture, JsonMapper json) {
        passageId = lecture.passageId();
        lectureId = lecture.lectureId();
        lecteurId = lecture.lecteurId();
        etat = lecture.etat();
        source = lecture.source();
        motifParolesSeules = lecture.motifParolesSeules();
        origineParoles = lecture.origineParoles();
        if (lecture.fichier() != null) {
            fichierNom = lecture.fichier().nom();
            fichierTailleOctets = lecture.fichier().tailleOctets();
            fichierDureeMs = lecture.fichier().dureeMs();
        }
        parolesId = lecture.paroles().parolesId();
        dureeParolesMs = lecture.paroles().dureeMs();
        lignesParoles = json.writeValueAsString(lecture.paroles().lignes());
        versionSourceId = lecture.versionSourceId();
        decalageMs = lecture.decalageMs();
        numeroModificationCalage = lecture.numeroModificationCalage();
        demandeId = lecture.demandeId();
        demandeeLe = lecture.demandeeLe();
    }

    UUID getPassageId() { return passageId; }

    LecturePassage restaurer(JsonMapper json) {
        var fichier = fichierNom == null ? null : new FichierAudioLocal(fichierNom, fichierTailleOctets, fichierDureeMs);
        var paroles = new ParolesDuPassage(parolesId, dureeParolesMs,
                json.readValue(lignesParoles, new TypeReference<List<LigneParoles>>() { }));
        return new LecturePassage(lectureId, passageId, lecteurId, etat, source, fichier, motifParolesSeules,
                origineParoles, paroles, versionSourceId, decalageMs, numeroModificationCalage, demandeId, demandeeLe);
    }
}
