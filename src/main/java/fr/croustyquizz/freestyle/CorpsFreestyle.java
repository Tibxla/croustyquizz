package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.Refuse;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class CorpsFreestyle {
    private CorpsFreestyle() { }

    static List<Instrumentale> instrumentales(JsonNode corps, JsonMapper json) {
        JsonNode fiches = corps.path("instrumentales");
        if (!corps.isObject() || corps.size() != 1 || !fiches.isArray()) { throw invalide().exception(); }
        return fiches.valueStream().map(fiche -> {
            if (!fiche.isObject() || !fiche.propertyNames().equals(Set.of("instrumentaleId", "titre", "tempoBpm", "departBeatMs"))
                    || !fiche.path("titre").isString() || !fiche.path("tempoBpm").isIntegralNumber()
                    || !fiche.path("tempoBpm").canConvertToInt() || !fiche.path("departBeatMs").isIntegralNumber()
                    || !fiche.path("departBeatMs").canConvertToLong()) { throw invalide().exception(); }
            lireUuid(fiche.path("instrumentaleId"));
            try { return json.treeToValue(fiche, Instrumentale.class); }
            catch (tools.jackson.core.JacksonException | IllegalArgumentException erreur) { throw invalide().exception(erreur); }
        }).toList();
    }

    static ReglagesFreestyle reglages(JsonNode arbre, JsonMapper json) {
        var champs = new java.util.HashSet<>(arbre.propertyNames());
        boolean couplets = champs.remove("mesuresParCouplet");
        if (!arbre.isObject() || !champs.equals(Set.of("theme", "schemaDeRimes", "difficulte", "nombreMesures", "instrumentaleIds"))
                || (couplets && (!arbre.path("mesuresParCouplet").isIntegralNumber() || !arbre.path("mesuresParCouplet").canConvertToInt()))
                || !arbre.path("theme").isString() || !arbre.path("schemaDeRimes").isString() || !arbre.path("difficulte").isString()
                || !arbre.path("nombreMesures").isIntegralNumber() || !arbre.path("nombreMesures").canConvertToInt()
                || !arbre.path("instrumentaleIds").isArray()) { throw invalide().exception(); }
        arbre.path("instrumentaleIds").forEach(CorpsFreestyle::lireUuid);
        ReglagesFreestyle reglages;
        try { reglages = json.treeToValue(arbre, ReglagesFreestyle.class); }
        catch (tools.jackson.core.JacksonException | IllegalArgumentException erreur) { throw invalide().exception(erreur); }
        ReglagesFreestyle.refusDesCouplets(reglages.nombreMesures(), reglages.mesuresParCouplet())
                .ifPresent(refus -> { throw refus.exception(); });
        return reglages;
    }

    private static void lireUuid(JsonNode noeud) {
        if (!noeud.isString()) { throw invalide().exception(); }
        String texte = noeud.asString();
        try {
            if (!UUID.fromString(texte).toString().equalsIgnoreCase(texte)) { throw invalide().exception(); }
        } catch (IllegalArgumentException erreur) { throw invalide().exception(erreur); }
    }

    private static Refuse invalide() {
        return new Refuse(400, "REQUETE_INVALIDE", "Le corps de la requête Freestyle est invalide.");
    }
}
