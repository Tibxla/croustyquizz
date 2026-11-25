package fr.croustyquizz.passage;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import fr.croustyquizz.manche.Refuse;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
    /** Choix du meilleur Interprète d’un Passage de Freestyle à plusieurs (#74). */
    public record DesignationMeilleurInterprete(UUID envoiId, Long numeroModification, UUID interpreteId) {
        public DesignationMeilleurInterprete { obligatoire(envoiId); entier(numeroModification, true); obligatoire(interpreteId); }
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

    /** Trois invités au plus, distincts (#66). */
    public static final int NOMBRE_INVITES_MAX = 3;
    public static void verifierInvites(List<UUID> invitesIds) {
        if (invitesIds != null && (invitesIds.size() > NOMBRE_INVITES_MAX || invitesIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(invitesIds).size() != invitesIds.size())) { invalide(); }
    }
    /** Acceptation ou refus de son Invitation par le Joueur invité (#138). */
    public record ReponseInvitation(UUID envoiId, Long numeroModification, String reponse) {
        public ReponseInvitation {
            obligatoire(envoiId); entier(numeroModification, true);
            if (!"ACCEPTER".equals(reponse) && !"REFUSER".equals(reponse)) { invalide(); }
        }
        public boolean accepte() { return "ACCEPTER".equals(reponse); }
        @JsonAnySetter public void refuserChampInconnu(String nom, Object valeur) { invalide(); }
    }

    public record AppelPassage(Long numeroModification) { }
    public record ConfirmationPresencePassage(UUID envoiId, UUID etapeId, Long numeroModification) { }
    /** La liaison garantit que seul le champ du Mode choisi apparaît dans le JSON. */
    /** Sans invité, l’empreinte reste celle d’avant #66. */
    public record Inscription(UUID envoiId, String chansonId, UUID instrumentaleId,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<UUID> invitesIds) {
        public Inscription {
            verifierInvites(invitesIds);
            if (invitesIds != null) { invitesIds = List.copyOf(invitesIds); }
        }
        public Inscription(UUID envoiId, String chansonId, UUID instrumentaleId) { this(envoiId, chansonId, instrumentaleId, null); }
        public boolean concerneLeKaraoke() { return chansonId != null; }
        public List<UUID> invites() { return invitesIds == null ? List.of() : invitesIds; }
    }
}
