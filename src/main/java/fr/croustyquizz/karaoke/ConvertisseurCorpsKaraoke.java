package fr.croustyquizz.karaoke;

import fr.croustyquizz.passage.CorpsPassage;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Liaison des objets fermés, y compris leurs composants nullables obligatoires. */
public final class ConvertisseurCorpsKaraoke extends AbstractHttpMessageConverter<Object> {
    private static final Set<String> CHAMPS_NULLABLES = Set.of("avant", "fichier", "motifParolesSeules", "dureeMs");
    /** Champs que le contrat permet d’omettre : les invités de l’inscription (#138). */
    private static final String INVITES = "invitesIds";
    private static final Set<String> CHAMPS_FACULTATIFS = Set.of(INVITES);
    private static final Set<String> ENTIERS_AVEC_ZERO = Set.of("numeroModification", "numeroModificationCalage", "positionMs", "dureeMs");
    private final JsonMapper json;
    public ConvertisseurCorpsKaraoke(JsonMapper json) { super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json")); this.json = json; }
    @Override protected boolean supports(Class<?> type) {
        return type.getEnclosingClass() == CorpsKaraoke.class
                || (type.getEnclosingClass() == CorpsPassage.class && type != CorpsPassage.EmpreinteHttp.class);
    }
    @Override protected Object readInternal(Class<?> type, HttpInputMessage entree) throws IOException {
        try {
            JsonNode arbre = json.readTree(entree.getBody());
            if (type == CorpsPassage.Inscription.class) { return inscription(arbre); }
            verifier(arbre, type);
            return json.treeToValue(arbre, type);
        } catch (tools.jackson.core.JacksonException erreur) {
            throw new org.springframework.http.converter.HttpMessageNotReadableException("Corps Karaoké invalide.", erreur, entree);
        }
    }
    private CorpsPassage.Inscription inscription(JsonNode arbre) {
        if (arbre == null || !arbre.isObject()) { CorpsPassage.invalide(); }
        if (arbre.has("chansonId")) {
            verifier(arbre, CorpsKaraoke.InscriptionPassage.class);
            var corps = json.treeToValue(arbre, CorpsKaraoke.InscriptionPassage.class);
            return new CorpsPassage.Inscription(corps.envoiId(), corps.chansonId(), null, corps.invitesIds());
        }
        // Freestyle : envoiId et instrumentaleId, puis les invités facultatifs (#74).
        boolean invites = arbre.has(INVITES);
        if (arbre.size() != (invites ? 3 : 2) || !arbre.has("instrumentaleId")) { CorpsPassage.invalide(); }
        var envoi = CorpsPassage.uuid(arbre.path("envoiId"));
        var choix = arbre.get("instrumentaleId");
        List<UUID> invitesIds = null;
        if (invites) {
            if (!arbre.get(INVITES).isArray()) { CorpsPassage.invalide(); }
            invitesIds = arbre.get(INVITES).valueStream().map(CorpsPassage::uuid).toList();
        }
        return new CorpsPassage.Inscription(envoi, null, choix.isNull() ? null : CorpsPassage.uuid(choix), invitesIds);
    }
    private void verifier(JsonNode arbre, Class<?> type) {
        if (arbre == null || !arbre.isObject()) { CorpsKaraoke.invalide(); }
        var champs = type.getRecordComponents();
        Set<String> autorises = new HashSet<>();
        for (var champ : champs) {
            autorises.add(champ.getName());
            JsonNode valeur = arbre.get(champ.getName());
            if (valeur == null && CHAMPS_FACULTATIFS.contains(champ.getName())) { continue; }
            if (valeur == null) { CorpsKaraoke.invalide(); }
            if (champ.getType() == List.class) {
                if (!valeur.isArray()) { CorpsKaraoke.invalide(); }
                valeur.values().forEach(CorpsPassage::uuid);
                continue;
            }
            if (valeur.isNull() && CHAMPS_NULLABLES.contains(champ.getName())) { continue; }
            if (champ.getType() == Long.class) {
                if (!valeur.isIntegralNumber() || !valeur.canConvertToLong()) { CorpsKaraoke.invalide(); }
                CorpsKaraoke.entier(valeur.longValue(), ENTIERS_AVEC_ZERO.contains(champ.getName()));
            } else if (champ.getType() == Boolean.class) {
                if (!valeur.isBoolean()) { CorpsKaraoke.invalide(); }
            } else if (champ.getType().isRecord()) {
                verifier(valeur, champ.getType());
            } else {
                if (!valeur.isString() || valeur.asString().isEmpty()) { CorpsKaraoke.invalide(); }
                if (champ.getType() == UUID.class) { CorpsPassage.uuid(valeur); }
            }
        }
        if (arbre.propertyNames().stream().anyMatch(nom -> !autorises.contains(nom))) { CorpsKaraoke.invalide(); }
    }
    @Override protected void writeInternal(Object valeur, HttpOutputMessage sortie) throws IOException {
        json.writeValue(sortie.getBody(), valeur);
    }
    @Override public boolean canWrite(Class<?> type, MediaType mediaType) { return false; }
}
