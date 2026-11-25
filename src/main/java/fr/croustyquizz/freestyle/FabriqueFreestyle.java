package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.EtatCyclePassage;
import fr.croustyquizz.passage.EtatFileAttente;
import fr.croustyquizz.passage.EtatVoteDuPublic;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

public final class FabriqueFreestyle implements FabriqueDeDeroulement<ReglagesFreestyle, EtatFreestyle> {
    private final CompositeurDeGrille compositeur;
    private final long limiteInitiale;
    private final Duration delaiAppel;
    private final Duration dureeVote;
    private final Supplier<UUID> identifiants;
    private final RandomGenerator hasard;

    public FabriqueFreestyle(DictionnaireDeRimes dictionnaire, long limiteInitiale, Duration delaiAppel, Duration dureeVote) {
        this(dictionnaire, limiteInitiale, delaiAppel, dureeVote, UUID::randomUUID, new Random());
    }

    FabriqueFreestyle(DictionnaireDeRimes dictionnaire, long limiteInitiale, Duration delaiAppel, Duration dureeVote,
            Supplier<UUID> identifiants, RandomGenerator hasard) {
        EtatFileAttente.verifierLimite(limiteInitiale);
        this.compositeur = new CompositeurDeGrille(dictionnaire, hasard);
        this.limiteInitiale = limiteInitiale;
        this.delaiAppel = delaiAppel;
        this.dureeVote = dureeVote;
        this.identifiants = identifiants;
        this.hasard = hasard;
    }

    @Override public ModeDeJeu mode() { return ModeDeJeu.FREESTYLE; }

    @Override public Class<ReglagesFreestyle> typeReglages() { return ReglagesFreestyle.class; }

    @Override public ReglagesDeManche reglagesCommuns(ReglagesFreestyle reglages) {
        return new ReglagesDeManche(1, delaiAppel);
    }

    @Override public DeroulementFreestyle creer(ContexteDeManche contexte, ReglagesFreestyle reglages) {
        var file = new EtatFileAttente(contexte.soireeId(), contexte.mancheId(), limiteInitiale, 1, 1, 1,
                null, List.of(), Map.of());
        var etat = new EtatFreestyle(file, EtatCyclePassage.vide(), EtatVoteDuPublic.vide(), Map.of());
        return deroulement(contexte, reglages, etat, EtatManche.PRETE);
    }

    @Override public DeroulementFreestyle restaurer(ContexteDeManche contexte, ReglagesFreestyle reglages,
            EtatFreestyle etat, EtatManche etatManche) {
        if (etatManche == EtatManche.PRETE) { throw new IllegalStateException("Une Manche prête ne se restaure pas."); }
        return deroulement(contexte, reglages, etat, etatManche);
    }

    private DeroulementFreestyle deroulement(ContexteDeManche contexte, ReglagesFreestyle reglages,
            EtatFreestyle etat, EtatManche etatManche) {
        return new DeroulementFreestyle(contexte, reglages, etat, etatManche,
                compositeur, delaiAppel, dureeVote, identifiants, hasard);
    }

    @Override public String typeEvenementMinuteur(DeroulementDeManche deroulement) {
        return ((DeroulementFreestyle) deroulement).causeDernierMinuteur();
    }
}
