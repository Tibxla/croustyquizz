package fr.croustyquizz.freestyle;

import java.util.List;
import java.util.Set;

/** Prononciation normalisée, commune aux sources du dictionnaire. */
record EntreePhonetique(String mot, List<String> sons, double frequence, ThemeDeRimes theme) {
    private static final Set<String> VOYELLES = Set.of(
            "a", "ɑ", "e", "ɛ", "ə", "i", "o", "ɔ", "u", "y", "ø", "œ", "ɑ̃", "ɛ̃", "ɔ̃", "œ̃");

    EntreePhonetique {
        if (mot.isBlank() || !Double.isFinite(frequence) || frequence < 0) {
            throw new IllegalArgumentException("Mot vide ou fréquence invalide : " + mot);
        }
        sons = List.copyOf(sons);
    }

    List<String> famille() {
        for (int i = sons.size() - 1; i >= 0; i--) {
            if (VOYELLES.contains(sons.get(i))) {
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
