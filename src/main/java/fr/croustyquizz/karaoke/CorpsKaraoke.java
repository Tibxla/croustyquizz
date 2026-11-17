package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.passage.CiblePassage;
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
}
