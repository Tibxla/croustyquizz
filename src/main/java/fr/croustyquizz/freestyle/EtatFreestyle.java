package fr.croustyquizz.freestyle;

import fr.croustyquizz.passage.EtatCyclePassage;
import fr.croustyquizz.passage.EtatFileAttente;
import fr.croustyquizz.passage.EtatVoteDuPublic;
import java.util.Map;
import java.util.UUID;

/** État du Mode à restaurer avec les réglages et l’état de Manche du socle. */
public record EtatFreestyle(EtatFileAttente file, EtatCyclePassage cycle, EtatVoteDuPublic voteDuPublic,
        Map<UUID, Contenu> contenus) {
    public EtatFreestyle { contenus = Map.copyOf(contenus); }

    /** Fiche et Grille figées à l’inscription ; Couplets tirés à « Je suis prêt » d’un Passage à plusieurs (#74). */
    public record Contenu(Instrumentale instrumentale, GrilleDeRimes grille, CoupletsDuPassage couplets) {
        public Contenu(Instrumentale instrumentale, GrilleDeRimes grille) { this(instrumentale, grille, null); }

        Contenu avec(CoupletsDuPassage nouveaux) { return new Contenu(instrumentale, grille, nouveaux); }
    }
}
