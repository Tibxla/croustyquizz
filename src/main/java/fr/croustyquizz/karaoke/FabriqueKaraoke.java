package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.ContexteDeManche;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.FabriqueDeDeroulement;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.ReglagesDeManche;
import java.time.Duration;

/** Création et restauration sans relancer de Passage ni programmer de minuteur. */
public final class FabriqueKaraoke implements FabriqueDeDeroulement<ReglagesKaraoke, EtatKaraokePersiste> {
    private final Duration delaiAppel;

    public FabriqueKaraoke(Duration delaiAppel) { this.delaiAppel = delaiAppel; }

    @Override public ModeDeJeu mode() { return ModeDeJeu.KARAOKE; }
    @Override public Class<ReglagesKaraoke> typeReglages() { return ReglagesKaraoke.class; }
    @Override public ReglagesDeManche reglagesCommuns(ReglagesKaraoke reglages) {
        return new ReglagesDeManche(1, delaiAppel);
    }
    @Override public DeroulementKaraoke creer(ContexteDeManche contexte, ReglagesKaraoke reglages) {
        return new DeroulementKaraoke(contexte, reglages, delaiAppel);
    }
    @Override public DeroulementKaraoke restaurer(ContexteDeManche contexte, ReglagesKaraoke reglages,
            EtatKaraokePersiste etat, EtatManche etatManche) {
        return new DeroulementKaraoke(contexte, delaiAppel, etat, etatManche);
    }
}
