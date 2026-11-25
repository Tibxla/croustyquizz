package fr.croustyquizz.passage;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import fr.croustyquizz.manche.Refuse;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Corps des routes partagées par les Modes à Passages. */
public class CorpsPassage {
    protected CorpsPassage() { }
    public record EmpreinteHttp(String methode, Object corps) { }
    public static void invalide() { throw new Refuse(400, "REQUETE_INVALIDE", "Le corps de la requête est invalide.").exception(); }
    public static void entier(Long valeur, boolean zero) {
        if (valeur == null || valeur < (zero ? 0 : 1) || valeur > 9007199254740991L) { invalide(); }
    }
    protected static void obligatoire(Object valeur) { if (valeur == null) { invalide(); } }
    /** Lit un UUID JSON sous sa forme canonique, en minuscules ou en majuscules. */
    public static UUID uuid(JsonNode valeur) {
        String texte = valeur.asString("");
        try {
            if (valeur.isString()) {
                UUID id = UUID.fromString(texte);
                if (id.toString().equalsIgnoreCase(texte)) { return id; }
            }
        } catch (IllegalArgumentException erreur) { invalide(); }
        throw new Refuse(400, "REQUETE_INVALIDE", "Identifiant invalide.").exception();
    }
    public static void limite(Long valeur) {
        entier(valeur, false);
        if (valeur > EtatFileAttente.LIMITE_PASSAGES_MAX) { invalide(); }
    }
    public record Cible(UUID passageId, Long numeroModification) {
        public Cible { obligatoire(passageId); entier(numeroModification, true); }
        public CiblePassage cible() { return new CiblePassage(passageId, numeroModification); }
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
    /** La liaison garantit que seul le champ du Mode choisi apparaît dans le JSON. */
    public record Inscription(UUID envoiId, String chansonId, UUID instrumentaleId) {
        public boolean concerneLeKaraoke() { return chansonId != null; }
    }
}
