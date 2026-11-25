package fr.croustyquizz.karaoke;

import fr.croustyquizz.passage.CorpsPassage;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Corps fermés, validés avant la résolution de l'identité HTTP. */
public final class CorpsKaraoke extends CorpsPassage {
    private CorpsKaraoke() { }
    static final int LONGUEUR_CHANSON_ID_MAX = 128;
    static void identifiantChanson(String valeur) {
        if (valeur == null || valeur.isEmpty() || valeur.codePointCount(0, valeur.length()) > LONGUEUR_CHANSON_ID_MAX) { invalide(); }
    }
    public record InscriptionPassage(UUID envoiId, String chansonId) {
        public InscriptionPassage { obligatoire(envoiId); identifiantChanson(chansonId); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }
    public record DesignationLecteur(UUID lecteurId) { }
    public record Fichier(String nom, Long tailleOctets, Long dureeMs) {
        public Fichier {
            obligatoire(nom);
            if (nom.isEmpty() || nom.indexOf('/') >= 0 || nom.indexOf('\\') >= 0) { invalide(); }
        }
        FichierAudioLocal metadonnees() {
            return new FichierAudioLocal(nom, tailleOctets, dureeMs == null || dureeMs == 0 ? null : dureeMs);
        }
    }
    public record DeclarationLectureChanson(UUID lecteurId, UUID etapeId, Long numeroModification, String source,
            Fichier fichier, String motifParolesSeules) {
        public DeclarationLectureChanson {
            switch (source) {
                case "FICHIER_LOCAL" -> {
                    if (fichier == null || motifParolesSeules != null) { invalide(); }
                }
                case "PAROLES_SEULES" -> {
                    if (fichier != null || !motifDeclarable(motifParolesSeules)) { invalide(); }
                }
                case null, default -> invalide();
            }
        }
        private static boolean motifDeclarable(String motif) {
            return switch (motif) {
                case "FICHIER_ABSENT", "FICHIER_AMBIGU", "FICHIER_ILLISIBLE" -> true;
                case null, default -> false;
            };
        }
    }
    public record SignalFinLectureChanson(UUID lecteurId, UUID etapeId, UUID lectureId, Long positionMs) { }
    public record SignalErreurLectureChanson(UUID lecteurId, UUID etapeId, UUID lectureId) { }
    public record RelevePositionLecture(UUID lecteurId, UUID etapeId, UUID lectureId, UUID demandeId, Long positionMs) { }
    public record RepriseParolesSeules(UUID etapeId, UUID lectureId) { }
    public record MarqueDebutParoles(UUID etapeId, UUID lectureId) { }
    public record CorrectionCalage(UUID etapeId, UUID lectureId, Long numeroModificationCalage, String sens) {
        public CorrectionCalage {
            if (!"AVANCER".equals(sens) && !"RETARDER".equals(sens)) { invalide(); }
        }
    }
}
