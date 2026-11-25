package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.passage.CiblePassage;
import fr.croustyquizz.passage.VoteRecu;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;

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
    static final int LONGUEUR_SUGGESTION_MAX = 120;
    static String nettoyerSuggestion(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFC).replaceAll("\\p{IsWhite_Space}+", " ").strip();
    }

    /**
     * Clé de regroupement seule : NFKC, sans casse ni marques d'accent, apostrophes et guillemets typographiques
     * ramenés à l'ASCII, ponctuation Unicode supprimée, puis blancs regroupés à nouveau.
     */
    static String normaliserSuggestion(String texte) {
        String compatible = Normalizer.normalize(texte, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        String sansAccent = Normalizer.normalize(compatible, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String ascii = sansAccent.replaceAll("[\u2018\u2019]", "'").replaceAll("[\u00AB\u00BB\u201C\u201D]", "\"");
        return ascii.replaceAll("\\p{P}+", "").replaceAll("\\p{IsWhite_Space}+", " ").strip();
    }

    private static int longueur(String texte) { return texte.codePointCount(0, texte.length()); }

    /** Contrôles et caractères de format (Cf, comme U+200B) ; le pilote PostgreSQL refuse aussi les demi-surrogates. */
    private static boolean interdit(int c) {
        int type = Character.getType(c);
        return Character.isISOControl(c) || type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE;
    }

    private static void verifierChampSuggestion(String texte) {
        if (texte == null || longueur(texte) > LONGUEUR_SUGGESTION_MAX) { invalide(); }
        // Les blancs, tabulations comprises, sont regroupés avant de chercher un caractère interdit.
        String nettoye = nettoyerSuggestion(texte);
        if (longueur(nettoye) > LONGUEUR_SUGGESTION_MAX || nettoye.codePoints().anyMatch(CorpsKaraoke::interdit)
                || normaliserSuggestion(nettoye).isEmpty()) { invalide(); }
    }

    public record SuggestionChanson(String titre, String artiste) {
        public SuggestionChanson { verifierChampSuggestion(titre); verifierChampSuggestion(artiste); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }

    public record RetraitSuggestionChanson(Long nombreJoueurs) {
        public RetraitSuggestionChanson { entier(nombreJoueurs, false); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }

    public record ChoixFavoriChanson(Boolean favorite) {
        public ChoixFavoriChanson { obligatoire(favorite); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }

    public record Cible(UUID passageId, Long numeroModification) {
        public Cible { obligatoire(passageId); entier(numeroModification, true); }
        public CiblePassage cible() { return new CiblePassage(passageId, numeroModification); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }
    public record InscriptionPassage(UUID envoiId, String chansonId) {
        public InscriptionPassage { obligatoire(envoiId); identifiantChanson(chansonId); }
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
