package fr.croustyquizz.freestyle;

import java.util.List;

final class DictionnaireDeRimesFactice implements DictionnaireDeRimes {
    static final List<String> MOTS_DE_VILLE = List.of("lumière", "nuit");

    @Override public List<String> amorces(ThemeDeRimes theme) {
        return switch (theme) {
            case VILLE -> MOTS_DE_VILLE;
            case SOIREE -> List.of("voyage");
            case SPORT -> List.of();
        };
    }

    @Override public List<String> rimesDe(String mot, ThemeDeRimes theme) {
        return theme == ThemeDeRimes.VILLE ? rimesDe(mot) : List.of();
    }

    @Override public List<String> rimesDe(String mot) {
        return switch (mot) {
            case "lumière" -> List.of("pierre", "rivière", "poussière", "frontière", "clairière", "verrière", "ornière");
            case "nuit" -> List.of("bruit", "luit", "minuit", "ennui", "pluie", "fruit", "circuit");
            case "voyage" -> List.of("passage", "paysage", "visage", "rivage", "nuage", "orage", "barrage", "garage",
                    "village", "bagage", "virage", "ancrage", "mirage", "éclairage", "naufrage", "présage", "sillage",
                    "amarrage", "arrimage", "patinage", "assemblage", "montage", "étage", "grillage", "vitrage",
                    "cadrage", "décalage", "habillage", "ramage", "plumage", "hommage");
            default -> List.of();
        };
    }
}
