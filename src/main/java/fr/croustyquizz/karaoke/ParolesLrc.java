package fr.croustyquizz.karaoke;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture des paroles synchronisées au format LRC. LRCLIB est lu avec souplesse : une ligne mal formée
 * ou hors durée est ignorée. Le catalogue de la Bibliothèque de base est lu strictement : la moindre
 * ligne inexploitable fait échouer le chargement (ADR 0011).
 */
public final class ParolesLrc {
    private static final Pattern HORODATAGE = Pattern.compile("\\[(\\d+):([0-5]\\d)(?:\\.(\\d{2,3}))?\\]");
    /** Métadonnées usuelles d'un fichier LRC, sans effet sur les positions. `offset` en aurait : il est refusé. */
    private static final Pattern METADONNEE = Pattern.compile("\\[(ti|ar|al|au|by|re|ve|length|#):[^\\]]*\\]");

    private ParolesLrc() { }

    public static List<LigneParoles> lire(String paroles, Long dureeMs) {
        if (paroles == null) { return List.of(); }
        List<LigneParoles> lignes = new ArrayList<>();
        for (String ligne : paroles.split("\\R")) {
            Analyse analyse = analyser(ligne.trim());
            if (analyse == null || analyse.texte().isEmpty()) { continue; }
            for (long position : analyse.positions()) {
                if (dureeMs == null || position < dureeMs) { lignes.add(new LigneParoles(position, analyse.texte())); }
            }
        }
        return trier(lignes);
    }

    public static List<LigneParoles> lireStrictement(String paroles, long dureeMs) {
        List<LigneParoles> lignes = new ArrayList<>();
        String[] source = paroles.startsWith("\uFEFF") ? paroles.substring(1).split("\\R") : paroles.split("\\R");
        for (int numero = 1; numero <= source.length; numero++) {
            String ligne = source[numero - 1].trim();
            if (ligne.isEmpty() || METADONNEE.matcher(ligne).matches()) { continue; }
            Analyse analyse = analyser(ligne);
            if (analyse == null || analyse.positions().isEmpty()) {
                throw new IllegalArgumentException("LRC illisible à la ligne " + numero);
            }
            for (long position : analyse.positions()) {
                if (position >= dureeMs) {
                    throw new IllegalArgumentException("LRC au-delà de la durée à la ligne " + numero);
                }
                // Une balise sans texte marque un silence : elle est vérifiée, puis ignorée comme chez LRCLIB.
                if (!analyse.texte().isEmpty()) { lignes.add(new LigneParoles(position, analyse.texte())); }
            }
        }
        if (lignes.isEmpty()) { throw new IllegalArgumentException("LRC sans ligne chantée"); }
        return trier(lignes);
    }

    private static List<LigneParoles> trier(List<LigneParoles> lignes) {
        lignes.sort(Comparator.comparingLong(LigneParoles::positionMs));
        return List.copyOf(lignes);
    }

    private record Analyse(List<Long> positions, String texte) { }

    /** Balises de temps en tête de ligne, puis texte ; null si une balise est invalide. */
    private static Analyse analyser(String ligne) {
        List<Long> positions = new ArrayList<>();
        int debut = 0;
        while (debut < ligne.length() && ligne.charAt(debut) == '[') {
            int fin = ligne.indexOf(']', debut);
            if (fin < 0) { return null; }
            Matcher balise = HORODATAGE.matcher(ligne.substring(debut, fin + 1));
            if (!balise.matches()) { return null; }
            try {
                long minutes = Math.multiplyExact(Long.parseLong(balise.group(1)), 60000L);
                long secondes = Long.parseLong(balise.group(2)) * 1000;
                String fraction = balise.group(3);
                long millisecondes = fraction == null ? 0 : Long.parseLong(fraction) * (fraction.length() == 2 ? 10 : 1);
                positions.add(Math.addExact(Math.addExact(minutes, secondes), millisecondes));
            } catch (ArithmeticException | NumberFormatException erreur) {
                return null;
            }
            debut = fin + 1;
        }
        return new Analyse(positions, ligne.substring(debut).trim());
    }
}
