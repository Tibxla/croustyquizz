package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Déroulement des Passages de Freestyle sur des Instrumentales locales, à un Interprète ou à plusieurs (#74). Les
 * Couplets d’un Passage à plusieurs suivent la chronologie du Passage : l’Horloge annonce le suivant une mesure
 * avant chaque changement, puis fait commencer son Couplet.
 */
public final class DeroulementFreestyle implements DeroulementDeManche {
    private static final String PASSAGE_APPELE = "passage-appele";
    private static final Set<Destination> ECRANS = Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);

    private final ContexteDeManche contexte;
    private final ReglagesFreestyle reglages;
    private final CompositeurDeGrille compositeur;
    private final Supplier<UUID> identifiants;
    private final RandomGenerator hasard;
    private final FileAttente file;
    private final CommandesDeFileAttente commandesDeFile;
    private final CycleDePassage cycle;
    private final VoteDuPublic vote;
    private final Map<UUID, EtatFreestyle.Contenu> contenus;
    private EtatManche etatManche;
    private String evenementDuMinuteur;
    private Minuteur prochainCouplet = () -> { };
    private long generationCouplet;

    DeroulementFreestyle(ContexteDeManche contexte, ReglagesFreestyle reglages, EtatFreestyle etat,
            EtatManche etatManche, CompositeurDeGrille compositeur, Duration delaiAppel, Duration dureeVote,
            Supplier<UUID> identifiants, RandomGenerator hasard) {
        this.contexte = contexte;
        this.reglages = reglages;
        this.etatManche = etatManche;
        this.compositeur = compositeur;
        this.identifiants = identifiants;
        this.hasard = hasard;
        this.contenus = new HashMap<>(etat.contenus());
        this.file = FileAttente.depuis(etat.file());
        this.commandesDeFile = new CommandesDeFileAttente(file, contexte.soireeId(), contexte.mancheId());
        this.cycle = new CycleDePassage(file, contexte.horloge(), delaiAppel, etat.cycle(), this::surCloturePassage);
        this.vote = new VoteDuPublic(file, contexte.horloge(), dureeVote, etat.voteDuPublic(),
                cloture -> { evenementDuMinuteur = "vote-clos"; }, passage -> passage.interpretes().size() > 1);
        if (etatManche == EtatManche.EN_COURS) {
            cycle.reprogrammerFin();
            vote.reprogrammer(contexte.horloge().maintenant());
            programmerCouplet();
        }
    }

    @Override public void demarrer() {
        if (etatManche != EtatManche.PRETE) { throw new IllegalStateException("La Manche a déjà démarré."); }
        etatManche = EtatManche.EN_COURS;
    }

    @Override public ResultatDeCommande recevoir(ActionJoueur action) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        return switch (action) {
            case InscrireFreestyle inscription -> apres(inscrire(inscription), inscription.recueLe());
            case DesinscrirePassage retrait -> apres(commandesDeFile.desinscrire(retrait), retrait.recueLe());
            case RepondreInvitation reponse -> apres(commandesDeFile.repondreInvitation(reponse), reponse.recueLe());
            case DesignerMeilleurInterprete choix -> vote.recevoir(choix, etatManche == EtatManche.EN_PAUSE);
            case ConfirmerPresencePassage presence -> confirmer(presence);
            case VoterPassage notation -> vote.recevoir(notation, etatManche == EtatManche.EN_PAUSE);
            case null, default -> incompatible();
        };
    }

    private ResultatDeCommande inscrire(InscrireFreestyle inscription) {
        var disponibles = reglages.selectionDisponible(inscription.instrumentalesDisponibles());
        if (inscription.instrumentaleId() != null) {
            disponibles = disponibles.stream().filter(fiche -> fiche.instrumentaleId().equals(inscription.instrumentaleId())).toList();
        }
        if (disponibles.isEmpty()) {
            return new Refuse(422, "INSTRUMENTALE_INDISPONIBLE", "Aucune Instrumentale ne correspond à ce choix dans la sélection disponible.");
        }
        List<Instrumentale> admissibles = disponibles.stream()
                .filter(fiche -> refusInscription(inscription.joueurId(), fiche) == null).toList();
        if (admissibles.isEmpty()) { return refusInscription(inscription.joueurId(), disponibles.getFirst()); }

        Instrumentale instrumentale = admissibles.size() == 1 ? admissibles.getFirst()
                : admissibles.get(hasard.nextInt(admissibles.size()));
        var grille = compositeur.composer(reglages, instrumentale, inscription.motsDuTheme());
        if (grille.isEmpty()) {
            return new Refuse(422, "GRILLE_INDISPONIBLE", "Impossible de composer cette Grille de rimes.");
        }
        long finMs = instrumentale.positionSurLeFichier(reglages.nombreMesures() * GrilleDeRimes.TEMPS_PAR_MESURE);
        UUID joueur = inscription.joueurId();
        var nouveau = new NouveauPassage(inscription.passageId(), contexte.soireeId(), contexte.mancheId(),
                joueur, List.of(joueur), instrumentale.instrumentaleId().toString(), Duration.ofMillis(finMs),
                inscription.numeroPropose(), inscription.recueLe());
        var resultat = commandesDeFile.inscrire(nouveau, inscription.invitesIds(), inscription.invitesPresents(),
                inscription.envoiId());
        if (resultat instanceof Accepte) { contenus.put(nouveau.id(), new EtatFreestyle.Contenu(instrumentale, grille.get())); }
        return resultat;
    }

    private Refuse refusInscription(UUID joueur, Instrumentale fiche) {
        return file.verifierInscription(joueur, fiche.instrumentaleId().toString())
                .map(refus -> "PASSAGE_DEJA_INSCRIT".equals(refus.code())
                        ? new Refuse(409, refus.code(), "Cette Instrumentale est déjà en attente pour toi.") : refus)
                .orElse(null);
    }

    @Override public ResultatDeCommande commander(CommandeDeManche commande) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        return switch (commande) {
            case AppelerPassage appel -> appeler(appel);
            case RetirerPassage retrait -> apres(commandesDeFile.retirer(retrait), retrait.recueLe());
            case DeplacerPassage placement -> apres(commandesDeFile.deplacer(placement), placement.recueLe());
            case DefinirPrioritePassage priorite -> apres(commandesDeFile.definirPriorite(priorite), priorite.recueLe());
            case ReglerLimitePassages limite -> apres(commandesDeFile.reglerLimite(limite), limite.recueLe());
            case null, default -> incompatible();
        };
    }

    private ResultatDeCommande appeler(AppelerPassage appel) {
        if (file.trouver(appel.cible().passageId()) == null) { return introuvable(); }
        if (etatManche != EtatManche.EN_COURS || contexte.pilotageAutomatique()) { return incompatible(); }
        var resultat = cycle.appeler(appel.cible(), appel.recueLe(), identifiants);
        return acquitterCycle(resultat, appel.envoiId(), appel.recueLe(), "APPELER", PASSAGE_APPELE);
    }

    private ResultatDeCommande confirmer(ConfirmerPresencePassage presence) {
        Passage passage = file.trouver(presence.cible().passageId());
        if (passage == null || !passage.interpretes().contains(presence.joueurId())) { return introuvable(); }
        if (etatManche != EtatManche.EN_COURS) { return incompatible(); }
        var resultat = cycle.demarrer(presence.cible(), presence.etapeId(), passage.duree(), presence.recueLe());
        if (resultat instanceof ResultatFile.Modifiee && passage.interpretes().size() > 1) {
            contenus.put(passage.id(), contenus.get(passage.id()).avec(CoupletsDuPassage.tirer(reglages, tirerOrdre(passage.interpretes()))));
            programmerCouplet();
        }
        return acquitterCycle(resultat, presence.envoiId(), presence.recueLe(), "CONFIRMER_PRESENCE", "passage-demarre");
    }

    /** Tirage au sort de l’ordre des Interprètes, une seule fois par Passage, côté serveur. */
    private List<UUID> tirerOrdre(List<UUID> interpretes) {
        var ordre = new java.util.ArrayList<>(interpretes);
        for (int i = ordre.size() - 1; i > 0; i--) {
            java.util.Collections.swap(ordre, i, hasard.nextInt(i + 1));
        }
        return ordre;
    }

    /**
     * Arme le prochain événement des Couplets du courant chanté, hors pause : même chronologie que la fin prévue,
     * positions calculées comme la Grille par {@link Instrumentale#positionSurLeFichier}.
     */
    private void programmerCouplet() {
        annulerCouplet();
        Passage courant = cycle.courantActif();
        if (courant == null || courant.etat() != EtatPassage.EN_COURS || cycle.etat().pauseDepuis() != null) { return; }
        var contenu = contenus.get(courant.id());
        var couplets = contenu.couplets();
        if (couplets == null || couplets.tempsDuProchainEvenement() < 0) { return; }
        long positionMs = contenu.instrumentale().positionSurLeFichier(couplets.tempsDuProchainEvenement());
        var chronologie = cycle.etat();
        Instant echeance = chronologie.demarreLe().plus(chronologie.tempsPause()).plusMillis(positionMs);
        Duration reste = Duration.between(contexte.horloge().maintenant(), echeance);
        long rappel = generationCouplet;
        UUID etapeId = chronologie.etapeId();
        prochainCouplet = contexte.horloge().programmer(reste.isNegative() ? Duration.ZERO : reste, () -> {
            if (rappel != generationCouplet || !etapeId.equals(cycle.etat().etapeId())) { return; }
            var suivants = contenus.get(courant.id()).couplets().avancer();
            contenus.put(courant.id(), contenus.get(courant.id()).avec(suivants));
            evenementDuMinuteur = suivants.suivantAnnonce() ? "couplet-annonce" : "couplet-commence";
            programmerCouplet();
        });
    }

    private void annulerCouplet() {
        generationCouplet++;
        prochainCouplet.annuler();
        prochainCouplet = () -> { };
    }

    private ResultatDeCommande acquitterCycle(ResultatFile resultat, UUID envoiId, Instant recueLe, String action, String cause) {
        if (resultat instanceof ResultatFile.Refus refus) { return refus.refus(); }
        return cycle.acquitter(envoiId, recueLe, action, cause);
    }

    @Override public ResultatDeCommande mettreEnPause(CommandePilotage commande) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        if (etatManche != EtatManche.EN_COURS) { return incompatible(); }
        etatManche = EtatManche.EN_PAUSE;
        cycle.suspendre(commande.recueLe());
        annulerCouplet();
        vote.suspendre(commande.recueLe());
        return new Accepte(commande.accuse(), ECRANS, "manche-en-pause");
    }

    @Override public ResultatDeCommande reprendre(CommandePilotage commande) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        if (etatManche != EtatManche.EN_PAUSE) { return incompatible(); }
        etatManche = EtatManche.EN_COURS;
        cycle.reprendre(commande.recueLe());
        vote.reprendre(commande.recueLe());
        programmerCouplet();
        return apres(new Accepte(commande.accuse(), ECRANS, "manche-reprise"), commande.recueLe());
    }

    @Override public ResultatDeCommande passerEtape(CommandePilotage commande) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        if (cycle.courantActif() == null) { return inactive(); }
        cloreCourant(EtatPassage.SAUTE, TraceTerminale.PASSE_PAR_ANIMATEUR, commande);
        return apres(new Accepte(commande.accuse(), ECRANS, "passage-saute"), commande.recueLe());
    }

    @Override public ResultatDeCommande terminer(CommandePilotage commande) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        if (cycle.courantActif() != null) {
            cloreCourant(EtatPassage.ANNULE, TraceTerminale.MANCHE_TERMINEE, commande);
        }
        file.annulerAttentes(commande.recueLe(), commande.acteur().identifiant());
        vote.cloreTout(commande.recueLe());
        etatManche = EtatManche.TERMINEE;
        return new Accepte(commande.accuse(), ECRANS, "manche-terminee");
    }

    private void cloreCourant(EtatPassage etat, String motif, CommandePilotage commande) {
        cycle.clore(etat, motif, commande.acteur().identifiant(), commande.recueLe());
    }

    private void surCloturePassage(CloturePassage cloture) {
        annulerCouplet();
        var contenu = contenus.get(cloture.passageId());
        if (contenu != null && contenu.couplets() != null) {
            contenus.put(cloture.passageId(), contenu.avec(contenu.couplets().sansAnnonce()));
        }
        if (cloture.etat() == EtatPassage.TERMINE) {
            vote.ouvrir(cloture.passageId(), cloture.instant(), false);
            evenementDuMinuteur = enchainer(cloture.instant()) ? PASSAGE_APPELE : "passage-termine";
        }
    }

    private boolean enchainer(Instant maintenant) {
        return contexte.pilotageAutomatique() && etatManche == EtatManche.EN_COURS
                && cycle.appelerSuivantSiLibre(identifiants, maintenant);
    }

    private ResultatDeCommande apres(ResultatDeCommande resultat, Instant maintenant) {
        if (resultat instanceof Accepte accepte && enchainer(maintenant)) {
            return new Accepte(accepte.accuse(), ECRANS, PASSAGE_APPELE, accepte.statutHttp());
        }
        return resultat;
    }

    private Refuse gardeCommune() {
        return switch (etatManche) {
            case PRETE -> inactive();
            case TERMINEE -> incompatible();
            case EN_COURS, EN_PAUSE -> null;
        };
    }

    private static Refuse inactive() {
        return new Refuse(409, "ETAPE_INACTIVE", "Aucune étape active ne permet cette commande.");
    }

    private static Refuse incompatible() {
        return new Refuse(409, "ETAT_INCOMPATIBLE", "Cette commande n’est pas compatible avec l’état de la Manche.");
    }

    private static Refuse introuvable() {
        return new Refuse(404, "RESSOURCE_INTROUVABLE", "Ce Passage est introuvable.");
    }

    String causeDernierMinuteur() { return evenementDuMinuteur; }

    @Override public boolean viseEtape(UUID etapeId) {
        return cycle.courantActif() == null ? etapeId == null : cycle.etat().etapeId().equals(etapeId);
    }

    @Override public InstantaneFreestyle instantane() {
        var etat = new EtatFreestyle(file.etat(), cycle.etat(), vote.etat(), contenus);
        return new InstantaneFreestyle(etatManche, reglages, etat, contexte.pilotageAutomatique());
    }

    @Override public boolean estTerminee() { return etatManche == EtatManche.TERMINEE; }

    @Override public List<AttributionDePoints> points() { return List.of(); }
}
