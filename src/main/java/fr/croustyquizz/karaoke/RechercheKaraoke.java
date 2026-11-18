package fr.croustyquizz.karaoke;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Nettoyage et classement déterministes des recherches de Chansons. */
public final class RechercheKaraoke {
    private RechercheKaraoke() { }

    public static String nettoyer(String rechercheBrute, int longueurRechercheMax) {
        if (rechercheBrute == null || rechercheBrute.codePointCount(0, rechercheBrute.length()) < 1
                || rechercheBrute.codePointCount(0, rechercheBrute.length()) > 120) {
            throw new IllegalArgumentException("La recherche brute doit contenir de 1 à 120 caractères");
        }
        String recherche = Normalizer.normalize(rechercheBrute, Normalizer.Form.NFC)
                .replaceAll("\\p{IsWhite_Space}+", " ").trim();
        if (longueurRechercheMax < 1 || longueurRechercheMax > 120
                || recherche.codePointCount(0, recherche.length()) > longueurRechercheMax
                || !recherche.matches("(?s).*[\\p{L}\\p{N}].*")) {
            throw new IllegalArgumentException("Recherche de Chanson invalide");
        }
        return recherche;
    }
    public static List<Chanson> classer(String rechercheNettoyee, List<Chanson> candidates) {
        String saisie = normaliser(rechercheNettoyee);
        Map<String, Chanson> uniques = new LinkedHashMap<>();
        candidates.forEach(chanson -> uniques.putIfAbsent(chanson.chansonId(), chanson));
        Comparator<String> pointsDeCode = (gauche, droite) ->
                Arrays.compare(gauche.codePoints().toArray(), droite.codePoints().toArray());
        return uniques.values().stream()
                .sorted(Comparator.comparingInt((Chanson chanson) -> rang(saisie, chanson))
                        .thenComparing(chanson -> normaliser(chanson.titre()), pointsDeCode)
                        .thenComparing(chanson -> normaliser(chanson.artiste()), pointsDeCode)
                        .thenComparing(Chanson::chansonId, pointsDeCode))
                .toList();
    }

    private static String normaliser(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replace("œ", "oe").replace("æ", "ae")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static int rang(String saisie, Chanson chanson) {
        String titre = normaliser(chanson.titre());
        String artiste = normaliser(chanson.artiste());
        if (saisie.equals(titre) || saisie.equals(artiste)) { return 0; }
        List<String> mots = List.of((titre + " " + artiste).split(" "));
        return Arrays.stream(saisie.split(" ")).allMatch(prefixe ->
                mots.stream().anyMatch(mot -> mot.startsWith(prefixe))) ? 1 : 2;
    }
}
