package fr.croustyquizz.freestyle;

import java.util.List;

/** Cherche des mots à proposer dans une Grille de rimes de Freestyle. */
public interface DictionnaireDeRimes {
    /**
     * Donne les rimes du mot, sans le mot lui-même, sans doublon et sans mot filtré.
     * Les rimes riches précèdent les autres rimes, puis la fréquence d'usage décroissante
     * départage les mots. Un mot absent du dictionnaire ou filtré donne une liste vide.
     */
    List<String> rimesDe(String mot);

    /** Applique le même classement aux seules suggestions du thème demandé. */
    List<String> rimesDe(String mot, ThemeDeRimes theme);

    /** Mots de départ du thème, sans doublon. Le compositeur les mélange avant d’y chercher des familles. */
    List<String> amorces(ThemeDeRimes theme);
}
