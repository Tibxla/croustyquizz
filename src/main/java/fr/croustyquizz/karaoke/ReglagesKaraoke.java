package fr.croustyquizz.karaoke;

import fr.croustyquizz.passage.EtatFileAttente;

/** Limite initiale par Interprète ; aucune limite sur le total des Passages. */
public record ReglagesKaraoke(long limitePassagesEnAttente) {
    public ReglagesKaraoke { EtatFileAttente.verifierLimite(limitePassagesEnAttente); }
}
