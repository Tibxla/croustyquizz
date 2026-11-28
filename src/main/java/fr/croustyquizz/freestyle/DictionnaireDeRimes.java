package fr.croustyquizz.freestyle;

import java.util.List;

/** Cherche des mots à proposer dans une Grille de rimes de Freestyle. */
public interface DictionnaireDeRimes {
    /**
     * Donne les rimes du mot, sans le mot lui-même, sans doublon et sans mot filtré.
     * Les rimes riches précèdent les autres rimes, puis la fréquence d'usage décroissante
     * départage les mots. Le lexique général passe d'abord, puis les mots des listes thématiques
     * qu'il ne contient pas. Un mot absent du dictionnaire ou filtré donne une liste vide.
     */
    List<String> rimesDe(String mot);

    /**
     * Applique le même classement aux suggestions du thème demandé, qui passent d'abord. Si elles
     * ne remplissent pas une Grille de 32 mesures, le lexique général les complète.
     */
    List<String> rimesDe(String mot, ThemeDeRimes theme);
}
