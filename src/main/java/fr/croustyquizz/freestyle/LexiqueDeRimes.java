package fr.croustyquizz.freestyle;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lit l'extrait de Lexique 3.83 (voir {@code freestyle/LISEZMOI.md}) en entrées du lexique général. La colonne
 * {@code phon} suit la notation de Lexique, un caractère par son, que la lecture convertit en alphabet phonétique
 * international, comme les listes thématiques. Le e muet final est omis, sauf s'il est la seule voyelle du mot.
 */
final class LexiqueDeRimes {
    static final String EN_TETE = "ortho\tphon\tlemme\tcgram\tfreqfilms2";
    private static final int NOMBRE_COLONNES = 5;
    private static final String E_MUET = "ə";
    private static final Map<Character, String> SON_DE_SYMBOLE = Map.ofEntries(
            Map.entry('a', "a"), Map.entry('i', "i"), Map.entry('y', "y"), Map.entry('u', "u"),
            Map.entry('o', "o"), Map.entry('O', "ɔ"), Map.entry('e', "e"), Map.entry('E', "ɛ"),
            Map.entry('°', E_MUET), Map.entry('2', "ø"), Map.entry('9', "œ"), Map.entry('5', "ɛ̃"),
            Map.entry('1', "œ̃"), Map.entry('@', "ɑ̃"), Map.entry('§', "ɔ̃"),
            Map.entry('p', "p"), Map.entry('b', "b"), Map.entry('t', "t"), Map.entry('d', "d"),
            Map.entry('k', "k"), Map.entry('g', "g"), Map.entry('f', "f"), Map.entry('v', "v"),
            Map.entry('s', "s"), Map.entry('z', "z"), Map.entry('S', "ʃ"), Map.entry('Z', "ʒ"),
            Map.entry('m', "m"), Map.entry('n', "n"), Map.entry('N', "ɲ"), Map.entry('G', "ŋ"),
            Map.entry('l', "l"), Map.entry('R', "ʁ"), Map.entry('x', "x"),
            Map.entry('j', "j"), Map.entry('w', "w"), Map.entry('8', "ɥ"));

    private LexiqueDeRimes() {
    }

    static List<EntreePhonetique> lire(BufferedReader lecteur, String source) throws IOException {
        if (!EN_TETE.equals(lecteur.readLine())) {
            throw new IOException("En-tête du lexique invalide dans " + source);
        }
        List<EntreePhonetique> entrees = new ArrayList<>();
        int numero = 1;
        for (String ligne = lecteur.readLine(); ligne != null; ligne = lecteur.readLine()) {
            numero++;
            try {
                entrees.add(entree(ligne));
            } catch (IllegalArgumentException erreur) {
                throw new IOException("Entrée du lexique invalide dans " + source + ":" + numero, erreur);
            }
        }
        return entrees;
    }

    static List<String> sons(String phon) {
        List<String> sons = new ArrayList<>(phon.length());
        for (char symbole : phon.toCharArray()) {
            String son = SON_DE_SYMBOLE.get(symbole);
            if (son == null) {
                throw new IllegalArgumentException("Symbole phonétique inconnu : " + symbole);
            }
            sons.add(son);
        }
        int dernier = sons.size() - 1;
        if (dernier > 0 && E_MUET.equals(sons.get(dernier))
                && sons.subList(0, dernier).stream().anyMatch(EntreePhonetique::estVoyelle)) {
            sons.remove(dernier);
        }
        return sons;
    }

    private static EntreePhonetique entree(String ligne) {
        String[] colonnes = ligne.split("\t", -1);
        if (colonnes.length != NOMBRE_COLONNES) {
            throw new IllegalArgumentException("Cinq colonnes attendues");
        }
        EntreePhonetique entree = new EntreePhonetique(colonnes[0], sons(colonnes[1]),
                Double.parseDouble(colonnes[4]), null, List.of(colonnes[2].split(",")));
        entree.famille();
        return entree;
    }
}
