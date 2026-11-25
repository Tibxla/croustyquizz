package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.passage.CorpsPassage;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class CorpsFreestyle {
    private static final Set<String> CHAMPS_FICHE = Set.of("instrumentaleId", "titre", "tempoBpm", "departBeatMs", "dureeMs");

    private CorpsFreestyle() { }

    /** Fiches déclarées par une fenêtre d’Écran de salle, qui devient le lecteur désigné. */
    public record Declaration(UUID lecteurId, List<Instrumentale> fiches) { }

    static Declaration declaration(JsonNode corps, JsonMapper json) {
        JsonNode fiches = corps.path("instrumentales");
        if (!corps.isObject() || !corps.propertyNames().equals(Set.of("lecteurId", "instrumentales")) || !fiches.isArray()) {
            throw invalide().exception();
        }
        UUID lecteurId = CorpsPassage.uuid(corps.path("lecteurId"));
        return new Declaration(lecteurId, fiches.valueStream().map(fiche -> {
            if (!fiche.isObject() || !fiche.propertyNames().equals(CHAMPS_FICHE)
                    || !fiche.path("titre").isString() || !fiche.path("tempoBpm").isIntegralNumber()
                    || !fiche.path("tempoBpm").canConvertToInt() || !entierLong(fiche.path("departBeatMs"))
                    || !entierLong(fiche.path("dureeMs"))) { throw invalide().exception(); }
            CorpsPassage.uuid(fiche.path("instrumentaleId"));
            try { return json.treeToValue(fiche, Instrumentale.class); }
            catch (tools.jackson.core.JacksonException | IllegalArgumentException erreur) { throw invalide().exception(erreur); }
        }).toList());
    }

    static ReglagesFreestyle reglages(JsonNode arbre, JsonMapper json) {
        if (!arbre.isObject() || !arbre.propertyNames().equals(Set.of("theme", "schemaDeRimes", "difficulte", "nombreMesures", "instrumentaleIds"))
                || !arbre.path("theme").isString() || !arbre.path("schemaDeRimes").isString() || !arbre.path("difficulte").isString()
                || !arbre.path("nombreMesures").isIntegralNumber() || !arbre.path("nombreMesures").canConvertToInt()
                || !arbre.path("instrumentaleIds").isArray()) { throw invalide().exception(); }
        arbre.path("instrumentaleIds").forEach(CorpsPassage::uuid);
        try { return json.treeToValue(arbre, ReglagesFreestyle.class); }
        catch (tools.jackson.core.JacksonException | IllegalArgumentException erreur) { throw invalide().exception(erreur); }
    }

    private static boolean entierLong(JsonNode valeur) {
        return valeur.isIntegralNumber() && valeur.canConvertToLong();
    }

    private static Refuse invalide() {
        return new Refuse(400, "REQUETE_INVALIDE", "Le corps de la requête Freestyle est invalide.");
    }
}
