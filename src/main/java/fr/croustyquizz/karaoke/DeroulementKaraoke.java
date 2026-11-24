package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Domaine du Karaoké, sans source externe ni publication. */
public final class DeroulementKaraoke implements DeroulementDeManche {
    static final Duration DUREE_VOTE_PAR_DEFAUT = Duration.ofSeconds(30);
    private static final Set<Destination> ECRANS = Set.of(
            Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);
    private static final Set<Destination> SALLE_ET_CONSOLE = Set.of(Destination.SALLE, Destination.CONSOLE);
    private static final String LECTURE_MODIFIEE = "lecture-chanson-modifiee";
    private static final String CALAGE_MODIFIE = "calage-chanson-modifie";
    private static final String PASSAGE_APPELE = "passage-appele";

    private final ContexteDeManche contexte;
    private final Duration delaiAppel;
    private final Supplier<UUID> identifiants;
    private final FileAttente file;
    private final CycleDePassage cycle;
    private final VoteDuPublic vote;
    private final Map<UUID, ContenuKaraoke> contenus = new HashMap<>();
    private final Map<UUID, LecturePassage> lectures = new HashMap<>();
    private UUID lecteurDesigneId;
    private String derniereCauseMinuteur;
    private EtatManche etat;

    public DeroulementKaraoke(ContexteDeManche contexte, ReglagesKaraoke reglages, Duration delaiAppel) {
        this(contexte, reglages, delaiAppel, DUREE_VOTE_PAR_DEFAUT);
    }

    public DeroulementKaraoke(ContexteDeManche contexte, ReglagesKaraoke reglages, Duration delaiAppel,
            Supplier<UUID> identifiants) {
        this(contexte, reglages, delaiAppel, DUREE_VOTE_PAR_DEFAUT, identifiants);
    }

    public DeroulementKaraoke(ContexteDeManche contexte, ReglagesKaraoke reglages, Duration delaiAppel,
            Duration dureeVote) {
        this(contexte, reglages, delaiAppel, dureeVote, UUID::randomUUID);
    }

    public DeroulementKaraoke(ContexteDeManche contexte, ReglagesKaraoke reglages, Duration delaiAppel,
            Duration dureeVote, Supplier<UUID> identifiants) {
        this(contexte, delaiAppel, dureeVote, identifiants, new EtatKaraokePersiste(new EtatFileAttente(contexte.soireeId(), contexte.mancheId(),
                reglages.limitePassagesEnAttente(), 1, 1, 1, null, List.of(), Map.of()), Map.of()),
                EtatManche.PRETE);
    }

    DeroulementKaraoke(ContexteDeManche contexte, Duration delaiAppel,
            EtatKaraokePersiste persiste, EtatManche etatManche) {
        this(contexte, delaiAppel, DUREE_VOTE_PAR_DEFAUT, persiste, etatManche);
    }

    DeroulementKaraoke(ContexteDeManche contexte, Duration delaiAppel,
            EtatKaraokePersiste persiste, EtatManche etatManche, Supplier<UUID> identifiants) {
        this(contexte, delaiAppel, DUREE_VOTE_PAR_DEFAUT, persiste, etatManche, identifiants);
    }

    DeroulementKaraoke(ContexteDeManche contexte, Duration delaiAppel, Duration dureeVote,
            EtatKaraokePersiste persiste, EtatManche etatManche) {
        this(contexte, delaiAppel, dureeVote, persiste, etatManche, UUID::randomUUID);
    }

    DeroulementKaraoke(ContexteDeManche contexte, Duration delaiAppel, Duration dureeVote,
            EtatKaraokePersiste persiste, EtatManche etatManche, Supplier<UUID> identifiants) {
        this(contexte, delaiAppel, dureeVote, identifiants, persiste, verifierEtatRestaure(etatManche));
    }

    private static EtatManche verifierEtatRestaure(EtatManche etatManche) {
        if (etatManche == EtatManche.PRETE) {
            throw new IllegalStateException("Une Manche prête ne se restaure pas.");
        }
        return etatManche;
    }

    private DeroulementKaraoke(ContexteDeManche contexte, Duration delaiAppel, Duration dureeVote, Supplier<UUID> identifiants,
            EtatKaraokePersiste persiste, EtatManche etatManche) {
        this.contexte = contexte;
        this.delaiAppel = delaiAppel;
        this.identifiants = Objects.requireNonNull(identifiants);
        this.file = FileAttente.depuis(persiste.file());
        this.contenus.putAll(persiste.contenus());
        this.lectures.putAll(persiste.lectures());
        this.lecteurDesigneId = persiste.lecteurDesigneId();
        this.etat = Objects.requireNonNull(etatManche);
        Horloge horloge = horlogeAvecEnchainement(contexte.horloge());
        this.cycle = new CycleDePassage(file, horloge, delaiAppel, persiste.cycle(), this::surCloture,
                contexte.pilotageAutomatique());
        this.vote = new VoteDuPublic(file, horloge, dureeVote, persiste.voteDuPublic(),
                cloture -> { derniereCauseMinuteur = "vote-clos"; });
        if (etatManche == EtatManche.EN_COURS) {
            cycle.reprogrammerFin();
            cycle.reprogrammerEcheanceAppel();
            vote.reprogrammer(contexte.horloge().maintenant());
        }
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
        return apres(appliquerAction(action), action == null ? null : action.recueLe());
    }

    private ResultatDeCommande appliquerAction(ActionJoueur action) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        if (action instanceof VoterPassage notation) { return voter(notation); }
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

    private ResultatDeCommande apres(ResultatDeCommande resultat, Instant maintenant) {
        if (!(resultat instanceof Accepte accepte)) { return resultat; }
        boolean aAppele = enchainer(maintenant);
        if (estTerminee()) { return new Accepte(accepte.accuse(), ECRANS, "manche-terminee", accepte.statutHttp()); }
        if (aAppele) { return new Accepte(accepte.accuse(), ECRANS, PASSAGE_APPELE, accepte.statutHttp()); }
        return accepte;
    }

    boolean enchainer(Instant maintenant) {
        if (!contexte.pilotageAutomatique() || etat != EtatManche.EN_COURS) { return false; }
        if (cycle.appelerSuivantSiLibre(identifiants, maintenant)) {
            lectures.clear();
            return true;
        }
        // Un courant clos exclut un Passage actif. Sans appel, la File est alors vide.
        var chronologie = cycle.etat();
        Passage dernier = file.trouver(chronologie.passageId());
        if (chronologie.clotureLe() != null && dernier != null
                && (dernier.etat() == EtatPassage.TERMINE || dernier.etat() == EtatPassage.SAUTE)
                && !vote.aUnVoteOuvert()) {
            etat = EtatManche.TERMINEE;
            contexte.signalerFin().run();
        }
        return false;
    }

    private Horloge horlogeAvecEnchainement(Horloge horloge) {
        return new Horloge() {
            @Override public Instant maintenant() { return horloge.maintenant(); }

            @Override public Minuteur programmer(Duration delai, Runnable action) {
                return horloge.programmer(delai, () -> {
                    action.run();
                    apresMinuteur();
                });
            }
        };
    }

    private void apresMinuteur() {
        if (enchainer(contexte.horloge().maintenant())) { derniereCauseMinuteur = PASSAGE_APPELE; }
    }

    private ResultatDeCommande voter(VoterPassage action) {
        var refus = vote.voter(action.joueurId(), action.cible(), action.note(), action.recueLe(), etat == EtatManche.EN_PAUSE);
        if (refus.isPresent()) { return refus.get(); }
        var accuse = new AccuseVote(action.envoiId(), contexte.soireeId(), contexte.mancheId(),
                action.cible().passageId(), action.note(), action.recueLe());
        return new Accepte(accuse, Set.of(Destination.SALLE, Destination.CONSOLE, Destination.joueur(action.joueurId())),
                "vote-enregistre", 200);
    }

    @Override public ResultatDeCommande commander(CommandeDeManche commande) {
        return apres(appliquerCommande(commande), commande == null ? null : commande.recueLe());
    }

    private ResultatDeCommande appliquerCommande(CommandeDeManche commande) {
        if (commande instanceof ReprendreEnParolesSeules reprise) { return reprendreEnParoles(reprise); }
        if (commande instanceof MarquerDebutParoles geste) { return marquerDebut(geste); }
        if (commande instanceof CorrigerCalage correction) { return corriger(correction); }
        if (commande instanceof AppelerPassage appel) { return appeler(appel); }
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
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
        return switch (commande) {
            case DesignerLecteurChanson designation -> designer(designation);
            case DeclarerLectureChanson declaration -> declarer(declaration);
            case SignalerFinLectureChanson fin -> signalerFin(fin);
            case SignalerErreurLectureChanson erreur -> signalerErreur(erreur);
            case ReleverPositionLecture position -> relever(position);
            case null, default -> incompatible();
        };
    }

    private ResultatDeCommande appeler(AppelerPassage commande) {
        CiblePassage cible = commande.cible();
        if (contexte.pilotageAutomatique()) {
            Refuse refus = gardeHorsPause(cible.passageId());
            return refus != null ? refus : new Refuse(409, "ETAT_INCOMPATIBLE",
                    "En Pilotage Automatique, le Passage suivant est appelé seul.");
        }
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        if (file.trouver(cible.passageId()) == null) { return introuvable(); }
        if (etat == EtatManche.EN_PAUSE) { return incompatible(); }
        ResultatFile resultat = cycle.appeler(cible, commande.recueLe(), identifiants);
        if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
        lectures.clear();
        return accepterCycle(commande.envoiId(), commande.recueLe(), "APPELER", PASSAGE_APPELE);
    }

    private ResultatDeCommande confirmer(ConfirmerPresencePassage action) {
        Passage passage = file.trouver(action.cible().passageId());
        if (passage == null || !passage.interpretes().contains(action.joueurId())) { return introuvable(); }
        if (etat == EtatManche.EN_PAUSE) { return incompatible(); }
        if (!viseAppel(action.cible(), action.etapeId())) { return perimee(); }
        if (contexte.pilotageAutomatique() && !action.recueLe().isBefore(cycle.etat().echeanceAppelLe())) {
            return new Refuse(409, "HORS_DELAI", "Le temps pour confirmer est écoulé.");
        }
        LecturePassage lecture = lectures.get(passage.id());
        LecturePassage demarree = lecture != null && lecture.etat() == EtatLecture.PREPAREE
                ? lecture.passerA(EtatLecture.DEMARREE) : parolesDInscription(passage.id());
        ResultatFile resultat = cycle.demarrer(action.cible(), action.etapeId(),
                Duration.ofMillis(demarree.dureeReferenceMs()), action.recueLe());
        if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
        vote.signalerDemarrage(passage.id());
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

    private ResultatDeCommande designer(DesignerLecteurChanson commande) {
        UUID lecteurId = Objects.requireNonNull(commande.lecteurId());
        boolean change = !lecteurId.equals(lecteurDesigneId);
        if (change) {
            lecteurDesigneId = lecteurId;
            Passage courant = cycle.courantActif();
            if (courant != null && courant.etat() == EtatPassage.APPELE) {
                LecturePassage lecture = lectures.get(courant.id());
                if (lecture != null && lecture.etat() == EtatLecture.PREPAREE && !lecteurId.equals(lecture.lecteurId())) {
                    lectures.remove(courant.id());
                }
            }
        }
        return accepterLecture(commande.envoiId(), commande.recueLe(), lecteurId, null,
                "DESIGNER_LECTEUR", LECTURE_MODIFIEE, change ? SALLE_ET_CONSOLE : Set.of());
    }

    private ResultatDeCommande declarer(DeclarerLectureChanson commande) {
        UUID passageId = commande.cible().passageId();
        if (file.trouver(passageId) == null) { return introuvable(); }
        if (!viseAppel(commande.cible(), commande.etapeId())) { return perimee(); }
        if (lecteurDesigneId == null || !lecteurDesigneId.equals(commande.lecteurId())) { return lecteurNonDesigne(); }
        LecturePreparee preparation = commande.preparation();
        UUID lectureId = identifiants.get();
        var paroles = new ParolesDuPassage(identifiants.get(), preparation.dureeParolesMs(), preparation.lignes());
        var lecture = new LecturePassage(lectureId, passageId, commande.lecteurId(), EtatLecture.PREPAREE,
                preparation.source(), preparation.fichier(), preparation.motifParolesSeules(), preparation.origineParoles(),
                paroles, preparation.versionSourceId(), preparation.decalageInitialMs(), 0, null, null);
        lectures.put(passageId, lecture);
        return accepterLecture(commande.envoiId(), commande.recueLe(), commande.lecteurId(), lecture,
                "DECLARER_LECTURE", LECTURE_MODIFIEE, SALLE_ET_CONSOLE);
    }

    private ResultatDeCommande signalerFin(SignalerFinLectureChanson commande) {
        Refuse refus = verifierSignal(commande.passageId(), commande.etapeId(), commande.lectureId(), commande.lecteurId(), false);
        if (refus != null) { return refus; }
        ResultatFile resultat = cycle.clore(EtatPassage.TERMINE, TraceTerminale.FIN_LECTURE, null, commande.recueLe());
        if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
        return accepterLecture(commande.envoiId(), commande.recueLe(), commande.lecteurId(), lectures.get(commande.passageId()),
                "SIGNALER_FIN_LECTURE", "passage-termine", ECRANS);
    }

    private ResultatDeCommande signalerErreur(SignalerErreurLectureChanson commande) {
        Refuse refus = verifierSignal(commande.passageId(), commande.etapeId(), commande.lectureId(), commande.lecteurId(), true);
        if (refus != null) { return refus; }
        LecturePassage lecture = lectures.get(commande.passageId());
        boolean change = lecture.etat() != EtatLecture.EN_ERREUR;
        if (change) {
            lectures.put(lecture.passageId(), lecture.passerA(EtatLecture.EN_ERREUR));
            if (etat == EtatManche.EN_COURS) { suspendreManche(commande.recueLe()); }
        }
        return accepterLecture(commande.envoiId(), commande.recueLe(), commande.lecteurId(), lecture,
                "SIGNALER_ERREUR_LECTURE", LECTURE_MODIFIEE, change ? ECRANS : Set.of());
    }

    private ResultatDeCommande reprendreEnParoles(ReprendreEnParolesSeules commande) {
        Refuse refus = gardeCommune(commande.passageId());
        if (refus != null) { return refus; }
        LecturePassage lecture = lectureCourante(commande.passageId(), commande.etapeId(), commande.lectureId());
        if (lecture == null) { return perimee(); }
        if (lecture.etat() != EtatLecture.EN_ERREUR) { return incompatible(); }
        lectures.put(lecture.passageId(), lecture.reprendreEnParolesSeules());
        reprendreManche(commande.recueLe());
        return accepterCycle(commande.envoiId(), commande.recueLe(), "REPRENDRE_EN_PAROLES_SEULES", LECTURE_MODIFIEE);
    }

    private LecturePassage lectureCourante(UUID passageId, UUID etapeId, UUID lectureId) {
        Passage courant = cycle.courantActif();
        if (courant == null || !courant.id().equals(passageId) || !cycle.etat().etapeId().equals(etapeId)) { return null; }
        LecturePassage lecture = lectures.get(passageId);
        return lecture != null && lecture.lectureId().equals(lectureId) ? lecture : null;
    }

    private Refuse verifierSignal(UUID passageId, UUID etapeId, UUID lectureId, UUID lecteurId, boolean erreurAdmise) {
        if (file.trouver(passageId) == null) { return introuvable(); }
        LecturePassage lecture = lectureCourante(passageId, etapeId, lectureId);
        if (lecture == null) { return perimee(); }
        return verifierLecteurEtLecture(lecture, lecteurId, erreurAdmise);
    }

    private static Refuse verifierLecteurEtLecture(LecturePassage lecture, UUID lecteurId, boolean erreurAdmise) {
        if (!Objects.equals(lecture.lecteurId(), lecteurId)) { return lecteurNonDesigne(); }
        if (lecture.source() != SourceLecture.FICHIER_LOCAL || lecture.etat() == EtatLecture.PREPAREE
                || (!erreurAdmise && lecture.etat() == EtatLecture.EN_ERREUR)) { return incompatible(); }
        return null;
    }

    private static Refuse lecteurNonDesigne() {
        return new Refuse(409, "LECTEUR_NON_DESIGNE", "Ce lecteur ne peut pas commander cette lecture.");
    }

    private Accepte accepterLecture(UUID envoiId, Instant recueLe, UUID lecteurId, LecturePassage lecture,
            String action, String cause, Set<Destination> destinations) {
        return new Accepte(new AccuseLectureChanson(envoiId, contexte.soireeId(), contexte.mancheId(), lecteurId,
                lecture == null ? null : lecture.passageId(), lecture == null ? null : lecture.lectureId(), action, recueLe),
                destinations, cause);
    }

    private ResultatDeCommande marquerDebut(MarquerDebutParoles commande) {
        Refuse refus = gardeHorsPause(commande.passageId());
        if (refus != null) { return refus; }
        LecturePassage lecture = lectureCourante(commande.passageId(), commande.etapeId(), commande.lectureId());
        if (lecture == null) { return perimee(); }
        if (!calagePossible(lecture)) { return incompatible(); }
        LecturePassage cale;
        if (lecture.source() == SourceLecture.FICHIER_LOCAL) {
            cale = lecture.avecCalage(lecture.decalageMs(), identifiants.get(), commande.recueLe());
        } else {
            var projection = CycleDePassage.projeter(file.etat(), cycle.etat(), false, commande.recueLe());
            cale = lecture.avecCalage(projection.positionChronologiqueMs() - lecture.paroles().premierePosition(), null, null);
        }
        return accepterCalage(commande.envoiId(), commande.recueLe(), lecture, cale, "MARQUER_DEBUT", cale.demandeId());
    }

    private ResultatDeCommande corriger(CorrigerCalage commande) {
        Refuse refus = gardeHorsPause(commande.passageId());
        if (refus != null) { return refus; }
        LecturePassage lecture = lectureCourante(commande.passageId(), commande.etapeId(), commande.lectureId());
        if (lecture == null || lecture.numeroModificationCalage() != commande.numeroModificationCalage()) { return perimee(); }
        if (!calagePossible(lecture)) { return incompatible(); }
        long correction = switch (commande.sens()) {
            case AVANCER -> -100;
            case RETARDER -> 100;
        };
        LecturePassage cale = lecture.avecCalage(lecture.decalageMs() + correction, lecture.demandeId(), lecture.demandeeLe());
        return accepterCalage(commande.envoiId(), commande.recueLe(), lecture, cale, "CORRIGER", null);
    }

    private ResultatDeCommande relever(ReleverPositionLecture commande) {
        if (file.trouver(commande.passageId()) == null) { return introuvable(); }
        LecturePassage lecture = lectureCourante(commande.passageId(), commande.etapeId(), commande.lectureId());
        if (lecture == null || lecture.demandeId() == null || !lecture.demandeId().equals(commande.demandeId())) {
            return perimee();
        }
        Refuse refus = verifierLecteurEtLecture(lecture, commande.lecteurId(), false);
        if (refus != null) { return refus; }
        LecturePassage cale = lecture.avecCalage(commande.positionMs() - lecture.paroles().premierePosition(), null, null);
        lectures.put(lecture.passageId(), cale);
        return accepterLecture(commande.envoiId(), commande.recueLe(), commande.lecteurId(), cale,
                "RELEVER_POSITION", CALAGE_MODIFIE, SALLE_ET_CONSOLE);
    }

    private Refuse gardeCommune(UUID passageId) {
        return file.trouver(passageId) == null ? introuvable() : gardeCommune();
    }

    private Refuse gardeHorsPause(UUID passageId) {
        Refuse refus = gardeCommune(passageId);
        if (refus != null) { return refus; }
        return etat == EtatManche.EN_PAUSE ? incompatible() : null;
    }

    private boolean calagePossible(LecturePassage lecture) {
        return file.trouver(lecture.passageId()).etat() == EtatPassage.EN_COURS && lecture.etat() == EtatLecture.DEMARREE;
    }

    private Accepte accepterCalage(UUID envoiId, Instant recueLe, LecturePassage lecture, LecturePassage cale,
            String action, UUID demandeId) {
        boolean change = !lecture.equals(cale);
        if (change) { lectures.put(cale.passageId(), cale); }
        return new Accepte(new AccuseCalage(envoiId, contexte.soireeId(), contexte.mancheId(), cale.passageId(),
                cale.lectureId(), action, recueLe, demandeId, cale.numeroModificationCalage()),
                change ? SALLE_ET_CONSOLE : Set.of(), CALAGE_MODIFIE);
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
        suspendreManche(commande.recueLe());
        return new Accepte(commande.accuse(), ECRANS, "manche-en-pause");
    }

    private void suspendreManche(Instant recueLe) {
        etat = EtatManche.EN_PAUSE;
        cycle.suspendre(recueLe);
        vote.suspendre(recueLe);
    }

    @Override public ResultatDeCommande reprendre(CommandePilotage commande) {
        Refuse refus = gardeCommune(); if (refus != null) { return refus; }
        if (etat != EtatManche.EN_PAUSE) { return incompatible(); }
        Passage courant = cycle.courantActif();
        LecturePassage lecture = courant == null ? null : lectures.get(courant.id());
        if (lecture != null && lecture.etat() == EtatLecture.EN_ERREUR) { return incompatible(); }
        reprendreManche(commande.recueLe());
        return apres(new Accepte(commande.accuse(), ECRANS, "manche-reprise"), commande.recueLe());
    }

    private void reprendreManche(Instant recueLe) {
        etat = EtatManche.EN_COURS;
        cycle.reprendre(recueLe);
        vote.reprendre(recueLe);
    }

    @Override public ResultatDeCommande passerEtape(CommandePilotage commande) {
        Refuse refus = gardeCommune(); if (refus != null) { return refus; }
        if (cycle.courantActif() == null) { return inactive(); }
        cycle.clore(EtatPassage.SAUTE, TraceTerminale.PASSE_PAR_ANIMATEUR, commande.acteur().identifiant(), commande.recueLe());
        return apres(new Accepte(commande.accuse(), ECRANS, "passage-saute"), commande.recueLe());
    }

    @Override public ResultatDeCommande terminer(CommandePilotage commande) {
        Refuse refus = gardeCommune(); if (refus != null) { return refus; }
        if (cycle.courantActif() != null) {
            cycle.clore(EtatPassage.ANNULE, TraceTerminale.MANCHE_TERMINEE, commande.acteur().identifiant(), commande.recueLe());
        }
        file.annulerAttentes(commande.recueLe(), commande.acteur().identifiant());
        vote.cloreTout(commande.recueLe());
        etat = EtatManche.TERMINEE;
        return new Accepte(commande.accuse(), ECRANS, "manche-terminee");
    }

    private void surCloture(CloturePassage cloture) {
        lectures.computeIfPresent(cloture.passageId(), (id, lecture) -> lecture.sansDemande());
        if (cloture.etat() == EtatPassage.TERMINE) {
            vote.ouvrir(cloture.passageId(), cloture.instant(), etat == EtatManche.EN_PAUSE);
        }
        if (TraceTerminale.FIN_PREVUE.equals(cloture.motif())) { derniereCauseMinuteur = "passage-termine"; }
        if (TraceTerminale.ECHEANCE_APPEL.equals(cloture.motif())) { derniereCauseMinuteur = "passage-saute"; }
    }

    public String causeDernierMinuteur() { return derniereCauseMinuteur; }

    @Override public boolean viseEtape(UUID etapeId) {
        return cycle.courantActif() == null ? etapeId == null : cycle.etat().etapeId().equals(etapeId);
    }
    @Override public InstantaneKaraoke instantane() {
        return new InstantaneKaraoke(etat, file.etat(), contenus, cycle.etat(), lectures, lecteurDesigneId,
                vote.etat(), contexte.pilotageAutomatique());
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
