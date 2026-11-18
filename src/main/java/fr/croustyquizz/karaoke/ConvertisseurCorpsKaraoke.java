package fr.croustyquizz.karaoke;

import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Liaison stricte : ni coercition textuelle ni décimale, et avant doit être présent même nul. */
public final class ConvertisseurCorpsKaraoke extends AbstractHttpMessageConverter<Object> {
    private final JsonMapper json;
    public ConvertisseurCorpsKaraoke(JsonMapper json) { super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json")); this.json = json; }
    @Override protected boolean supports(Class<?> type) {
        return type.getEnclosingClass() == CorpsKaraoke.class && type != CorpsKaraoke.EmpreinteHttp.class;
    }
    @Override protected Object readInternal(Class<?> type, HttpInputMessage entree) throws IOException {
        try {
            JsonNode arbre = json.readTree(entree.getBody());
            verifier(arbre, type);
            return json.treeToValue(arbre, type);
        } catch (tools.jackson.core.JacksonException erreur) {
            throw new org.springframework.http.converter.HttpMessageNotReadableException("Corps Karaoké invalide.", erreur, entree);
        }
    }
    private void verifier(JsonNode arbre, Class<?> type) {
        if (arbre == null || !arbre.isObject()) { CorpsKaraoke.invalide(); }
        var champs = type.getRecordComponents();
        Set<String> autorises = new HashSet<>();
        for (var champ : champs) {
            autorises.add(champ.getName());
            JsonNode valeur = arbre.get(champ.getName());
            if (valeur == null) { CorpsKaraoke.invalide(); }
            if ("avant".equals(champ.getName()) && valeur.isNull()) { continue; }
            if (champ.getType() == Long.class) {
                if (!valeur.isIntegralNumber() || !valeur.canConvertToLong()) { CorpsKaraoke.invalide(); }
                CorpsKaraoke.entier(valeur.longValue(), "numeroModification".equals(champ.getName()));
            } else if (champ.getType() == Boolean.class) {
                if (!valeur.isBoolean()) { CorpsKaraoke.invalide(); }
            } else if (champ.getType() == CorpsKaraoke.Cible.class) {
                verifier(valeur, CorpsKaraoke.Cible.class);
            } else {
                if (!valeur.isString() || valeur.asString().isEmpty()) { CorpsKaraoke.invalide(); }
                if (champ.getType() == UUID.class) {
                    try {
                        if (!UUID.fromString(valeur.asString()).toString().equalsIgnoreCase(valeur.asString())) { CorpsKaraoke.invalide(); }
                    } catch (IllegalArgumentException erreur) { CorpsKaraoke.invalide(); }
                }
            }
        }
        if (arbre.propertyNames().stream().anyMatch(nom -> !autorises.contains(nom))) { CorpsKaraoke.invalide(); }
    }
    @Override protected void writeInternal(Object valeur, HttpOutputMessage sortie) throws IOException {
        json.writeValue(sortie.getBody(), valeur);
    }
    @Override public boolean canWrite(Class<?> type, MediaType mediaType) { return false; }
}
