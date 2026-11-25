package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.ContexteDeManche;
import fr.croustyquizz.manche.DeroulementDeManche;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.FabriqueDeDeroulement;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.ReglagesDeManche;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/** Création et restauration du Karaoké, avec son Horloge et ses identifiants. */
public final class FabriqueKaraoke implements FabriqueDeDeroulement<ReglagesKaraoke, EtatKaraokePersiste> {
    private final Duration delaiAppel;
    private final Duration dureeVote;
    private final Supplier<UUID> identifiants;

    public FabriqueKaraoke(Duration delaiAppel) { this(delaiAppel, DeroulementKaraoke.DUREE_VOTE_PAR_DEFAUT); }

    public FabriqueKaraoke(Duration delaiAppel, Supplier<UUID> identifiants) {
        this(delaiAppel, DeroulementKaraoke.DUREE_VOTE_PAR_DEFAUT, identifiants);
    }

    public FabriqueKaraoke(Duration delaiAppel, Duration dureeVote) {
        this(delaiAppel, dureeVote, UUID::randomUUID);
    }

    public FabriqueKaraoke(Duration delaiAppel, Duration dureeVote, Supplier<UUID> identifiants) {
        this.delaiAppel = delaiAppel;
        this.dureeVote = dureeVote;
        this.identifiants = identifiants;
    }

    @Override public ModeDeJeu mode() { return ModeDeJeu.KARAOKE; }
    @Override public Class<ReglagesKaraoke> typeReglages() { return ReglagesKaraoke.class; }
    @Override public ReglagesDeManche reglagesCommuns(ReglagesKaraoke reglages) {
        return new ReglagesDeManche(1, delaiAppel);
    }
    @Override public DeroulementKaraoke creer(ContexteDeManche contexte, ReglagesKaraoke reglages) {
        return new DeroulementKaraoke(contexte, reglages, delaiAppel, dureeVote, identifiants);
    }
    @Override public DeroulementKaraoke restaurer(ContexteDeManche contexte, ReglagesKaraoke reglages,
            EtatKaraokePersiste etat, EtatManche etatManche) {
        return new DeroulementKaraoke(contexte, delaiAppel, dureeVote, etat, etatManche, identifiants);
    }

    @Override public String typeEvenementMinuteur(DeroulementDeManche deroulement) {
        return deroulement.estTerminee() ? "manche-terminee" : ((DeroulementKaraoke) deroulement).causeDernierMinuteur();
    }
}
