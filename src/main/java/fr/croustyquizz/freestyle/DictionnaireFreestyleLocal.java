package fr.croustyquizz.freestyle;

import java.util.List;

/** Familles écrites pour la première Manche, en attendant le dictionnaire de #69. */
final class DictionnaireFreestyleLocal implements DictionnaireDeRimes {
    private static final List<String> SON_ER = List.of(
            "danser", "tracer", "glisser", "sauter", "jouer", "marcher", "tourner", "lancer",
            "viser", "frapper", "gagner", "passer", "chanter", "rêver", "crier", "parler",
            "bouger", "lever", "porter", "pousser", "monter", "rouler", "ramer", "nager",
            "tester", "tenter", "oser", "lutter", "briller", "vibrer", "sonner", "rentrer");
    private static final List<String> SON_ION = List.of(
            "station", "champion", "émotion", "action", "attention", "direction", "circulation", "animation",
            "invitation", "réaction", "destination", "vibration", "projection", "évasion", "connexion", "participation",
            "sensation", "concentration", "compétition", "formation", "rotation", "sélection", "scansion", "respiration",
            "ascension", "impulsion", "expédition", "transmission", "inspiration", "improvisation", "décision", "mission");

    static List<String> amorces(ThemeDeRimes theme) {
        return switch (theme) {
            case SOIREE -> List.of("danser", "émotion");
            case VILLE -> List.of("tracer", "station");
            case SPORT -> List.of("sauter", "champion");
        };
    }

    @Override public List<String> rimesDe(String mot) {
        return List.of(SON_ER, SON_ION).stream().filter(famille -> famille.contains(mot))
                .flatMap(List::stream).filter(rime -> !rime.equals(mot)).toList();
    }

    @Override public List<String> rimesDe(String mot, ThemeDeRimes theme) {
        return rimesDe(mot).stream().filter(amorce -> amorces(theme).contains(amorce)).toList();
    }
}
