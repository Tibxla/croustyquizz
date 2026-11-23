package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Domaine du Karaoké, sans source externe ni publication. */
public final class DeroulementKaraoke implements DeroulementDeManche {
    private static final Set<Destination> ECRANS = Set.of(
            Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);
    private final ContexteDeManche contexte;
    private final Duration delaiAppel;
    private final Supplier<UUID> identifiants;
    private final FileAttente file;
    private final CycleDePassage cycle;
    private final Map<UUID, ContenuKaraoke> contenus = new HashMap<>();
    private final Map<UUID, LecturePassage> lectures = new HashMap<>();
    private UUID lecteurDesigneId;
    private String derniereCauseMinuteur;
    private EtatManche etat;

    public DeroulementKaraoke(ContexteDeManche contexte, ReglagesKaraoke reglages, Duration delaiAppel) {
        this(contexte, reglages, delaiAppel, UUID::randomUUID);
    }

    public DeroulementKaraoke(ContexteDeManche contexte, ReglagesKaraoke reglages, Duration delaiAppel,
            Supplier<UUID> identifiants) {
        this(contexte, delaiAppel, identifiants, new EtatKaraokePersiste(new EtatFileAttente(contexte.soireeId(), contexte.mancheId(),
                reglages.limitePassagesEnAttente(), 1, 1, 1, null, List.of(), Map.of()), Map.of()),
                EtatManche.PRETE);
    }

    DeroulementKaraoke(ContexteDeManche contexte, Duration delaiAppel,
            EtatKaraokePersiste persiste, EtatManche etatManche) {
        this(contexte, delaiAppel, persiste, etatManche, UUID::randomUUID);
    }

    DeroulementKaraoke(ContexteDeManche contexte, Duration delaiAppel,
            EtatKaraokePersiste persiste, EtatManche etatManche, Supplier<UUID> identifiants) {
        this(contexte, delaiAppel, identifiants, persiste, verifierEtatRestaure(etatManche));
    }

    private static EtatManche verifierEtatRestaure(EtatManche etatManche) {
        if (etatManche == EtatManche.PRETE) {
            throw new IllegalStateException("Une Manche prête ne se restaure pas.");
        }
        return etatManche;
    }

    private DeroulementKaraoke(ContexteDeManche contexte, Duration delaiAppel, Supplier<UUID> identifiants,
            EtatKaraokePersiste persiste, EtatManche etatManche) {
        this.contexte = contexte;
        this.delaiAppel = delaiAppel;
        this.identifiants = Objects.requireNonNull(identifiants);
        this.file = FileAttente.depuis(persiste.file());
        this.contenus.putAll(persiste.contenus());
        this.lectures.putAll(persiste.lectures());
        this.lecteurDesigneId = persiste.lecteurDesigneId();
        this.etat = Objects.requireNonNull(etatManche);
        this.cycle = new CycleDePassage(file, contexte.horloge(), delaiAppel, persiste.cycle(), this::surCloture);
        if (etatManche == EtatManche.EN_COURS) { cycle.reprogrammerFin(); }
    }

    @Override public void demarrer() {
        if (etat != EtatManche.PRETE) { throw new IllegalStateException("La Manche a déjà démarré."); }
        etat = EtatManche.EN_COURS;
    }

    private Refuse gardeCommune() {
        if (etat == EtatManche.TERMINEE) { return incompatible(); }
        if (etat == EtatManche.PRETE) { return inactive(); }
        return null;
    }

    private static Refuse incompatible() {
        return new Refuse(409, "ETAT_INCOMPATIBLE", "Cette commande n’est pas compatible avec l’état de la Manche.");
    }

    private static Refuse inactive() {
        return new Refuse(409, "ETAPE_INACTIVE", "Aucune étape active ne permet cette commande.");
    }

    private static Refuse introuvable() {
        return new Refuse(404, "RESSOURCE_INTROUVABLE", "Ce Passage est introuvable.");
    }

    private static Refuse perimee() {
        return new Refuse(409, "COMMANDE_PERIMEE", "Ce Passage ou sa lecture a changé.");
    }

    public static Optional<MotifIndisponibilite> motifInscription(FileAttente file, UUID joueurId, Chanson chanson) {
        if (chanson.etatParoles() == EtatParoles.SANS_VOIX) { return Optional.of(MotifIndisponibilite.SANS_VOIX); }
        if (chanson.etatParoles() != EtatParoles.SYNCHRONISEES) {
            return Optional.of(MotifIndisponibilite.PAROLES_NON_SYNCHRONISEES);
        }
        if (chanson.dureeMs() == null || chanson.dureeMs() <= 0) { return Optional.of(MotifIndisponibilite.DUREE_INCONNUE); }
        return file.verifierInscription(joueurId, chanson.chansonId()).map(r -> MotifIndisponibilite.valueOf(r.code()));
    }

    @Override public ResultatDeCommande recevoir(ActionJoueur action) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        if (action instanceof ConfirmerPresencePassage confirmation) { return confirmer(confirmation); }
        if (action instanceof DesinscrirePassage desinscription) {
            return resultatPassage(file.desinscrire(desinscription.joueurId(), desinscription.cible(), desinscription.recueLe()),
                    desinscription.envoiId(), desinscription.recueLe(), desinscription.cible(), "DESINSCRIRE");
        }
        if (!(action instanceof InscrirePassage inscription)) { return incompatible(); }
        Chanson chanson = inscription.contenu().chanson();
        // Une fiche synchronisée sans ligne exploitable reste indisponible, avant la durée et le quota.
        if (chanson.etatParoles() == EtatParoles.SYNCHRONISEES && inscription.contenu().lignes().stream()
                .noneMatch(ParolesDuPassage::contientDuTexte)) {
            chanson = new Chanson(chanson.chansonId(), chanson.idLrclib(), chanson.titre(), chanson.artiste(),
                    chanson.album(), chanson.dureeMs(), EtatParoles.NON_SYNCHRONISEES);
        }
        Optional<MotifIndisponibilite> motif = motifInscription(file, inscription.joueurId(), chanson);
        if (motif.isPresent()) {
            MotifIndisponibilite raison = motif.get();
            boolean indisponible = raison == MotifIndisponibilite.SANS_VOIX
                    || raison == MotifIndisponibilite.PAROLES_NON_SYNCHRONISEES || raison == MotifIndisponibilite.DUREE_INCONNUE;
            return new Refuse(indisponible ? 422 : 409, indisponible ? "CHANSON_INDISPONIBLE" : raison.name(), raison.name());
        }
        ResultatFile resultat = file.inscrire(new NouveauPassage(inscription.faits().passageId(),
                contexte.soireeId(), contexte.mancheId(), inscription.joueurId(), List.of(inscription.joueurId()),
                inscription.chansonId(), Duration.ofMillis(chanson.dureeMs()), inscription.faits().numeroPropose(), inscription.recueLe()));
        if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
        contenus.put(inscription.faits().passageId(), inscription.contenu());
        return new Accepte(new AccusePassage(inscription.envoiId(), contexte.soireeId(), contexte.mancheId(),
                inscription.faits().passageId(), 0, "INSCRIRE", inscription.recueLe(), EtatPassage.EN_ATTENTE),
                ECRANS, "file-attente-modifiee", 201);
    }

    @Override public ResultatDeCommande commander(CommandeDeManche commande) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        if (commande instanceof AppelerPassage appel) { return appeler(appel); }
        if (commande instanceof RetirerPassage retrait) {
            return resultatPassage(file.retirer(retrait.acteur().identifiant(), retrait.cible(), retrait.recueLe()),
                    retrait.envoiId(), retrait.recueLe(), retrait.cible(), "RETIRER");
        }
        if (commande instanceof DeplacerPassage placement) {
            return resultatPassage(file.deplacer(placement.cible(), placement.tour(), placement.avant()),
                    placement.envoiId(), placement.recueLe(), placement.cible(), "DEPLACER");
        }
        if (commande instanceof DefinirPrioritePassage priorite) {
            ResultatFile resultat = file.definirPriorite(priorite.passage(), priorite.prioritaire());
            if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
            Passage passage = passage(priorite.passage());
            return accepter(resultat, new AccuseFileAttente(priorite.envoiId(), contexte.soireeId(), contexte.mancheId(),
                    "DEFINIR_PRIORITE", priorite.recueLe(), new CiblePassage(passage.id(), passage.numeroModification())),
                    "file-attente-modifiee");
        }
        if (commande instanceof ReglerLimitePassages limite) {
            ResultatFile resultat = file.reglerLimite(limite.limitePassagesEnAttente());
            return accepter(resultat, new AccuseFileAttente(limite.envoiId(), contexte.soireeId(), contexte.mancheId(),
                    "REGLER_LIMITE", limite.recueLe(), null), "limite-passages-modifiee");
        }
        return incompatible();
    }

    private ResultatDeCommande appeler(AppelerPassage commande) {
        CiblePassage cible = commande.cible();
        if (file.trouver(cible.passageId()) == null) { return introuvable(); }
        if (etat == EtatManche.EN_PAUSE) { return incompatible(); }
        ResultatFile resultat = cycle.appelerAvecIdentifiant(cible, identifiants, commande.recueLe());
        if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
        lectures.clear();
        return accepterCycle(commande.envoiId(), commande.recueLe(), "APPELER", "passage-appele");
    }

    private ResultatDeCommande confirmer(ConfirmerPresencePassage action) {
        Passage passage = file.trouver(action.cible().passageId());
        if (passage == null || !passage.interpretes().contains(action.joueurId())) { return introuvable(); }
        if (etat == EtatManche.EN_PAUSE) { return incompatible(); }
        if (!viseAppel(action.cible(), action.etapeId())) { return perimee(); }
        LecturePassage lecture = lectures.get(passage.id());
        LecturePassage demarree = lecture != null && lecture.etat() == EtatLecture.PREPAREE
                ? lecture.passerA(EtatLecture.DEMARREE) : parolesDInscription(passage.id());
        ResultatFile resultat = cycle.demarrer(action.cible(), action.etapeId(),
                Duration.ofMillis(demarree.dureeReferenceMs()), action.recueLe());
        if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
        lectures.put(passage.id(), demarree);
        return accepterCycle(action.envoiId(), action.recueLe(), "CONFIRMER_PRESENCE", "passage-demarre");
    }

    private boolean viseAppel(CiblePassage cible, UUID etapeId) {
        Passage courant = cycle.courantActif();
        return courant != null && courant.etat() == EtatPassage.APPELE && courant.id().equals(cible.passageId())
                && courant.numeroModification() == cible.numeroModification() && cycle.etat().etapeId().equals(etapeId);
    }

    private LecturePassage parolesDInscription(UUID passageId) {
        ContenuKaraoke contenu = contenus.get(passageId);
        UUID lectureId = identifiants.get();
        var paroles = new ParolesDuPassage(identifiants.get(), contenu.chanson().dureeMs(), contenu.lignes());
        return new LecturePassage(lectureId, passageId, null, EtatLecture.DEMARREE, SourceLecture.PAROLES_SEULES,
                null, MotifParolesSeules.ECRAN_NON_PRET, OrigineParoles.INSCRIPTION,
                paroles, contenu.chanson().idLrclib(), 0, 0, null, null);
    }

    private Accepte accepterCycle(UUID envoiId, Instant recueLe, String action, String cause) {
        Passage courant = cycle.courantActif();
        return new Accepte(new AccuseCyclePassage(envoiId, contexte.soireeId(), contexte.mancheId(), courant.id(),
                cycle.etat().etapeId(), courant.numeroModification(), action, recueLe, courant.etat()), ECRANS, cause);
    }

    private Passage passage(CiblePassage cible) {
        return file.etat().passages().stream().filter(p -> p.id().equals(cible.passageId())).findFirst().orElseThrow();
    }

    private ResultatDeCommande resultatPassage(ResultatFile resultat, UUID envoiId, Instant recueLe,
            CiblePassage cible, String action) {
        if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
        Passage passage = passage(cible);
        return accepter(resultat, new AccusePassage(envoiId, contexte.soireeId(), contexte.mancheId(), passage.id(),
                passage.numeroModification(), action, recueLe, passage.etat()), "file-attente-modifiee");
    }

    private Accepte accepter(ResultatFile resultat, Object accuse, String typeEvenement) {
        return new Accepte(accuse, resultat instanceof ResultatFile.Modifiee ? ECRANS : Set.of(), typeEvenement, 200);
    }

    @Override public ResultatDeCommande mettreEnPause(CommandePilotage commande) {
        Refuse refus = gardeCommune(); if (refus != null) { return refus; }
        if (etat != EtatManche.EN_COURS) { return incompatible(); }
        etat = EtatManche.EN_PAUSE;
        cycle.suspendre(commande.recueLe());
        return new Accepte(commande.accuse(), ECRANS, "manche-en-pause");
    }

    @Override public ResultatDeCommande reprendre(CommandePilotage commande) {
        Refuse refus = gardeCommune(); if (refus != null) { return refus; }
        if (etat != EtatManche.EN_PAUSE) { return incompatible(); }
        Passage courant = cycle.courantActif();
        LecturePassage lecture = courant == null ? null : lectures.get(courant.id());
        if (lecture != null && lecture.etat() == EtatLecture.EN_ERREUR) { return incompatible(); }
        etat = EtatManche.EN_COURS;
        cycle.reprendre(commande.recueLe());
        return new Accepte(commande.accuse(), ECRANS, "manche-reprise");
    }

    @Override public ResultatDeCommande passerEtape(CommandePilotage commande) {
        Refuse refus = gardeCommune(); if (refus != null) { return refus; }
        if (cycle.courantActif() == null) { return inactive(); }
        cycle.clore(EtatPassage.SAUTE, TraceTerminale.PASSE_PAR_ANIMATEUR, commande.acteur().identifiant(), commande.recueLe());
        return new Accepte(commande.accuse(), ECRANS, "passage-saute");
    }

    @Override public ResultatDeCommande terminer(CommandePilotage commande) {
        Refuse refus = gardeCommune(); if (refus != null) { return refus; }
        if (cycle.courantActif() != null) {
            cycle.clore(EtatPassage.ANNULE, TraceTerminale.MANCHE_TERMINEE, commande.acteur().identifiant(), commande.recueLe());
        }
        file.annulerAttentes(commande.recueLe(), commande.acteur().identifiant());
        etat = EtatManche.TERMINEE;
        return new Accepte(commande.accuse(), ECRANS, "manche-terminee");
    }

    private void surCloture(CloturePassage cloture) {
        lectures.computeIfPresent(cloture.passageId(), (id, lecture) -> lecture.sansDemande());
        if (TraceTerminale.FIN_PREVUE.equals(cloture.motif())) { derniereCauseMinuteur = "passage-termine"; }
    }

    public String causeDernierMinuteur() { return derniereCauseMinuteur; }

    @Override public boolean viseEtape(UUID etapeId) {
        return cycle.courantActif() == null ? etapeId == null : cycle.etat().etapeId().equals(etapeId);
    }
    @Override public InstantaneKaraoke instantane() {
        return new InstantaneKaraoke(etat, file.etat(), contenus, cycle.etat(), lectures, lecteurDesigneId);
    }
    @Override public boolean estTerminee() { return etat == EtatManche.TERMINEE; }
    @Override public List<AttributionDePoints> points() { return List.of(); }

    public Map<UUID, AttenteEstimee> estimerAttentes() {
        Instant maintenant = contexte.horloge().maintenant();
        boolean enPause = etat == EtatManche.EN_PAUSE;
        var projection = CycleDePassage.projeter(file.etat(), cycle.etat(), enPause, maintenant);
        return file.estimerAttentes(maintenant, enPause, projection.prochainAppelPrevuLe(), projection.actif(), delaiAppel);
    }
}
