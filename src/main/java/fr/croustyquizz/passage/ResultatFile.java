package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Refuse;

/** Un refus et un geste sans effet préservent intégralement la capture. */
public sealed interface ResultatFile {
    record Refus(Refuse refus) implements ResultatFile {}
    record SansEffet() implements ResultatFile {}
    record Modifiee() implements ResultatFile {}
}
