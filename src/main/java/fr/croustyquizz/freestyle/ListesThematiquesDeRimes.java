package fr.croustyquizz.freestyle;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lit les listes thématiques écrites pour le Freestyle, une par {@link ThemeDeRimes}. Une ligne invalide, y compris une
 * terminaison sans voyelle ou avec un son vide, arrête la lecture avec son fichier et son numéro.
 */
final class ListesThematiquesDeRimes {
    private static final int NOMBRE_COLONNES = 3;

    private ListesThematiquesDeRimes() {
    }

    static List<EntreePhonetique> lire() throws IOException {
        List<EntreePhonetique> entrees = new ArrayList<>();
        for (ThemeDeRimes theme : ThemeDeRimes.values()) {
            String fichier = theme.name().toLowerCase(Locale.ROOT) + ".tsv";
            try (BufferedReader lecteur = DictionnaireDeRimesFourni.ouvrir(fichier)) {
                entrees.addAll(lire(lecteur, fichier, theme));
            }
        }
        return entrees;
    }

    static List<EntreePhonetique> lire(BufferedReader lecteur, String source, ThemeDeRimes theme) throws IOException {
        if (!"mot\tterminaison\tfréquence".equals(lecteur.readLine())) {
            throw new IOException("En-tête du dictionnaire invalide dans " + source);
        }
        List<EntreePhonetique> entrees = new ArrayList<>();
        int numero = 1;
        for (String ligne = lecteur.readLine(); ligne != null; ligne = lecteur.readLine()) {
            numero++;
            try {
                String[] colonnes = ligne.split("\t", -1);
                if (colonnes.length != NOMBRE_COLONNES) {
                    throw new IllegalArgumentException("Trois colonnes attendues");
                }
                List<String> sons = List.of(colonnes[1].split(" ", -1));
                if (sons.contains("")) {
                    throw new IllegalArgumentException("Son vide dans la terminaison de « " + colonnes[0] + " »");
                }
                EntreePhonetique entree = new EntreePhonetique(colonnes[0], sons, Double.parseDouble(colonnes[2]),
                        theme);
                entree.famille();
                entrees.add(entree);
            } catch (IllegalArgumentException erreur) {
                throw new IOException("Entrée du dictionnaire invalide dans " + source + ":" + numero, erreur);
            }
        }
        return entrees;
    }
}
