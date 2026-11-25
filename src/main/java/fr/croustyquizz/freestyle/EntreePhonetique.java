package fr.croustyquizz.freestyle;

import java.util.List;
import java.util.Set;

/**
 * Prononciation normalisée, commune aux sources du dictionnaire. Une entrée d'une liste thématique porte son thème.
 * Une entrée du lexique général (Lexique 3.83) n'a pas de thème et porte ses lemmes, que le filtre de vulgarité
 * examine aussi pour écarter les formes fléchies.
 */
record EntreePhonetique(String mot, List<String> sons, double frequence, ThemeDeRimes theme, List<String> lemmes) {
    private static final Set<String> VOYELLES = Set.of(
            "a", "ɑ", "e", "ɛ", "ə", "i", "o", "ɔ", "u", "y", "ø", "œ", "ɑ̃", "ɛ̃", "ɔ̃", "œ̃");

    EntreePhonetique {
        if (mot.isBlank() || !Double.isFinite(frequence) || frequence < 0) {
            throw new IllegalArgumentException("Mot vide ou fréquence invalide : " + mot);
        }
        sons = List.copyOf(sons);
        lemmes = List.copyOf(lemmes);
    }

    EntreePhonetique(String mot, List<String> sons, double frequence, ThemeDeRimes theme) {
        this(mot, sons, frequence, theme, List.of());
    }

    static boolean estVoyelle(String son) {
        return VOYELLES.contains(son);
    }

    boolean generale() {
        return theme == null;
    }

    List<String> famille() {
        for (int i = sons.size() - 1; i >= 0; i--) {
            if (estVoyelle(sons.get(i))) {
                return sons.subList(i, sons.size());
            }
        }
        throw new IllegalArgumentException("Terminaison sans voyelle pour « " + mot + " »");
    }

    boolean rimeRicheAvec(EntreePhonetique autre) {
        int taille = sons.size();
        int tailleAutre = autre.sons.size();
        return taille >= 3 && tailleAutre >= 3
                && sons.subList(taille - 3, taille).equals(autre.sons.subList(tailleAutre - 3, tailleAutre));
    }
}
