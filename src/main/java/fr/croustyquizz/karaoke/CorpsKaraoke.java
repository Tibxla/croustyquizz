package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.passage.CiblePassage;
import fr.croustyquizz.passage.VoteRecu;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Corps fermés, validés avant la résolution de l'identité HTTP. */
public final class CorpsKaraoke {
    private CorpsKaraoke() { }
    public record EmpreinteHttp(String methode, Object corps) { }
    public static void invalide() { throw new Refuse(400, "REQUETE_INVALIDE", "Le corps de la requête est invalide.").exception(); }
    static void entier(Long valeur, boolean zero) {
        if (valeur == null || valeur < (zero ? 0 : 1) || valeur > 9007199254740991L) { invalide(); }
    }
    static void obligatoire(Object valeur) { if (valeur == null) { invalide(); } }
    /**
     * Bornes du contrat fixées par l'issue #96 : 20 Passages en attente au plus, chansonId de 128 caractères au plus.
     * Le domaine porte la même limite (EtatFileAttente.LIMITE_PASSAGES_MAX, PR #97) ; la couche HTTP la refuse en 400.
     */
    static final long LIMITE_PASSAGES_MAX = 20;
    static final int LONGUEUR_CHANSON_ID_MAX = 128;
    static void limite(Long valeur) {
        entier(valeur, false);
        if (valeur > LIMITE_PASSAGES_MAX) { invalide(); }
    }
    static void identifiantChanson(String valeur) {
        if (valeur == null || valeur.isEmpty() || valeur.codePointCount(0, valeur.length()) > LONGUEUR_CHANSON_ID_MAX) { invalide(); }
    }
    public record Cible(UUID passageId, Long numeroModification) {
        public Cible { obligatoire(passageId); entier(numeroModification, true); }
        public CiblePassage cible() { return new CiblePassage(passageId, numeroModification); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }
    /** Trois invités au plus, distincts (#66). Sans invité, l’empreinte reste celle d’avant #66. */
    static final int NOMBRE_INVITES_MAX = 3;
    public record InscriptionPassage(UUID envoiId, String chansonId,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<UUID> invitesIds) {
        @JsonCreator public InscriptionPassage {
            obligatoire(envoiId); identifiantChanson(chansonId);
            if (invitesIds != null && (invitesIds.size() > NOMBRE_INVITES_MAX || invitesIds.stream().anyMatch(java.util.Objects::isNull)
                    || new HashSet<>(invitesIds).size() != invitesIds.size())) { invalide(); }
        }
        public InscriptionPassage(UUID envoiId, String chansonId) { this(envoiId, chansonId, null); }
        public List<UUID> invites() { return invitesIds == null ? List.of() : List.copyOf(invitesIds); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }
    public record ReponseInvitation(UUID envoiId, Long numeroModification, String reponse) {
        public ReponseInvitation {
            obligatoire(envoiId); entier(numeroModification, true);
            if (!"ACCEPTER".equals(reponse) && !"REFUSER".equals(reponse)) { invalide(); }
        }
        public boolean accepte() { return "ACCEPTER".equals(reponse); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }
    public record DesinscriptionPassage(UUID envoiId, Long numeroModification) {
        public DesinscriptionPassage { obligatoire(envoiId); entier(numeroModification, true); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }
    public record VotePassage(UUID envoiId, Long numeroModification, Long note) {
        public VotePassage {
            obligatoire(envoiId);
            entier(numeroModification, true);
            entier(note, false);
            if (note > VoteRecu.NOTE_MAXIMALE) { invalide(); }
        }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }
    public record RetraitPassage(Long numeroModification) {
        public RetraitPassage { entier(numeroModification, true); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }
    public record PlacementPassage(Long numeroModification, Long tour, Cible avant) {
        public PlacementPassage { entier(numeroModification, true); entier(tour, false); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }
    public record PrioritePassage(Cible passage, Boolean prioritaire) {
        public PrioritePassage { obligatoire(passage); obligatoire(prioritaire); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }
    public record LimitePassages(Long limitePassagesEnAttente) {
        public LimitePassages { limite(limitePassagesEnAttente); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }

    public record AppelPassage(Long numeroModification) { }
    public record ConfirmationPresencePassage(UUID envoiId, UUID etapeId, Long numeroModification) { }
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
