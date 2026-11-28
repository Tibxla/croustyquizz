package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.*;
import java.util.*;

/**
 * Projections par destination, sans identifiant de fournisseur. Les paroles ne vont qu’à la salle, le fichier
 * local qu’à la Console.
 */
public final class VuesKaraoke {
    private VuesKaraoke() { }
    /** L'origine se lit dans le chansonId ; fichier et crédit viennent du catalogue, sans rien persister (ADR 0011). */
    public record ResumeChanson(String chansonId, String titre, String artiste, String album, Long dureeMs, EtatParoles etatParoles,
            OrigineChanson origine, ChansonDeBibliotheque bibliotheque) {
        public static ResumeChanson de(Chanson chanson, CatalogueDeBase catalogue) {
            return new ResumeChanson(chanson.chansonId(), chanson.titre(), chanson.artiste(), chanson.album(),
                    chanson.dureeMs() != null && chanson.dureeMs() > 0 ? chanson.dureeMs() : null, chanson.etatParoles(),
                    OrigineChanson.de(chanson.chansonId()), catalogue.bibliotheque(chanson.chansonId()).orElse(null));
        }
    }
    public record ResultatsChansons(UUID rechercheId, String recherche, List<ResumeChanson> chansons) {
        public ResultatsChansons { chansons = List.copyOf(chansons); }
    }
    public record FicheChanson(ResumeChanson chanson, boolean inscriptionPossible, MotifIndisponibilite motifIndisponibilite) { }
    public record InterpretePublic(UUID joueurId, String pseudo) { }
    public record LignePalmaresKaraoke(UUID passageId, long numero, Long rang, ResumeChanson chanson,
            List<InterpretePublic> interpretes, long nombreVotes, Integer moyenneDixiemes) { }
    public record ConfigurationInterfaceKaraoke(int longueurRechercheMax, long delaiConsultationNavigateurMs, long delaiConfirmationNavigateurMs,
            long delaiAppelMs, long dureeVoteMs, long dureeAffichageMoyenneMs) { }
    public record PassageEnAttentePublic(UUID passageId, long numeroModification, long numero, ResumeChanson chanson,
            List<InterpretePublic> interpretes, long position, long tour, boolean prioritaire, AttenteEstimee attente) { }
    public record CourantPourFile(UUID passageId, long numeroModification, long numero, ResumeChanson chanson,
            List<InterpretePublic> interpretes, UUID etapeId, String etat, Instant prochainAppelPrevuLe,
            Instant appeleLe, Instant echeanceAppelLe, Long delaiAppelRestantMs, Instant demarreLe, Instant finPrevueLe,
            Long positionChronologiqueMs, long tempsPauseMs, Instant calculeLe, Instant clotureLe) { }
    public record VoteDuPassage(UUID passageId, long numero, ResumeChanson chanson, List<InterpretePublic> interpretes,
            String etat, long delaiRestantMs, Instant calculeLe, Instant clotureLe, long nombreVotes,
            boolean suivantDemarre, Integer moyenneDixiemes) { }
    public record VoteDuPassageManette(UUID passageId, long numeroModification, long numero, ResumeChanson chanson,
            List<InterpretePublic> interpretes, String etat, long delaiRestantMs, Instant calculeLe, Instant clotureLe,
            Integer maNote, boolean peutVoter, Long nombreVotes, Integer moyenneDixiemes) { }
    public record BlocKaraokeSalle(UUID mancheId, EtatManche etat, long limitePassagesEnAttente, long nombreEnAttente, long tourOuvert,
            UUID passagePrioritaireId, CourantPourFile courant, ConfigurationInterfaceKaraoke configurationInterface,
            List<PassageEnAttentePublic> prochainsPassages, long nombreAutresPassages,
            UUID lecteurDesigneId, LectureDeChanson lecture, ParolesDuPassage paroles, List<VoteDuPassage> votes) { }
    public record BlocKaraokeConsole(UUID mancheId, EtatManche etat, long limitePassagesEnAttente, long nombreEnAttente, long tourOuvert,
            UUID passagePrioritaireId, CourantPourFile courant, ConfigurationInterfaceKaraoke configurationInterface,
            List<PassageEnAttentePublic> passagesEnAttente, boolean peutGererFile, boolean peutReglerLimite, boolean peutRechercher,
            boolean lecteurDesigne, boolean peutAppeler, boolean peutPasser, boolean peutReprendre, boolean peutCaler,
            boolean peutReprendreEnParolesSeules, LectureDeChanson lecture, FichierAudioLocal fichierLocal,
            List<VoteDuPassage> votes) { }
    public record BlocKaraokeManette(UUID mancheId, EtatManche etat, long limitePassagesEnAttente, long nombreEnAttente, long tourOuvert,
            UUID passagePrioritaireId, CourantPourFile courant, ConfigurationInterfaceKaraoke configurationInterface,
            List<PassageEnAttentePublic> prochainsPassages, long nombreAutresPassages,
            List<PassageEnAttentePublic> mesPassagesEnAttente, long nombrePersonnelEnAttente,
            boolean peutRechercher, boolean peutInscrire, String motifInscriptionRefusee, boolean peutConfirmerPresence,
            List<VoteDuPassageManette> votes) { }
    public record DemandePositionLecture(UUID demandeId, Instant demandeeLe) { }
    public record LectureDeChanson(UUID lectureId, UUID lecteurId, EtatLecture etat, SourceLecture source,
            MotifParolesSeules motifParolesSeules, OrigineParoles origineParoles, long decalageMs,
            long numeroModificationCalage, DemandePositionLecture demandePosition) { }

    public static Object projeter(InstantaneKaraoke instantane, Destination destination, Map<UUID, String> pseudos,
            Instant maintenant, ProprietesKaraoke proprietes, ProprietesSourceKaraoke source, CatalogueDeBase catalogue) {
        var etat = instantane.file();
        if (destination.type() == Destination.Type.JOUEUR && !pseudos.containsKey(destination.joueurId())) {
            throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Joueur introuvable.").exception();
        }
        var file = FileAttente.depuis(etat);
        boolean enPause = instantane.etatManche() == EtatManche.EN_PAUSE;
        boolean enCours = instantane.etatManche() == EtatManche.EN_COURS;
        var projection = CycleDePassage.projeter(etat, instantane.cycle(), enPause, maintenant);
        var courant = courant(instantane, projection, pseudos, catalogue);
        var positions = file.positions();
        var delaiAppel = Duration.ofSeconds(proprietes.passage().delaiAppelSecondes());
        var attentes = file.estimerAttentes(maintenant, enPause, projection.prochainAppelPrevuLe(), projection.actif(), delaiAppel);
        List<PassageEnAttentePublic> publics = file.ordreEffectif().stream()
                .map(p -> new PassageEnAttentePublic(p.id(), p.numeroModification(), p.numero(),
                        ResumeChanson.de(instantane.contenus().get(p.id()).chanson(), catalogue), interpretes(p.interpretes(), pseudos),
                        positions.get(p.id()), p.tourNormal(), p.id().equals(etat.passagePrioritaireId()), attentes.get(p.id())))
                .toList();
        var configuration = new ConfigurationInterfaceKaraoke(source.longueurRechercheMax(),
                proprietes.karaoke().delaiConsultationNavigateurMs(), proprietes.karaoke().delaiConfirmationNavigateurMs(),
                delaiAppel.toMillis(), Duration.ofSeconds(proprietes.passage().dureeVoteSecondes()).toMillis(),
                Duration.ofSeconds(proprietes.passage().dureeAffichageMoyenneSecondes()).toMillis());
        var trois = publics.stream().limit(3).toList();
        LecturePassage lecture = projection.actif() ? instantane.lectures().get(projection.courant().id()) : null;
        boolean lectureEnErreur = lecture != null && lecture.etat() == EtatLecture.EN_ERREUR;
        var lecturePublique = lecturePublique(lecture);
        return switch (destination.type()) {
            case SALLE -> new BlocKaraokeSalle(etat.mancheId(), instantane.etatManche(), etat.limitePassagesEnAttente(), publics.size(),
                    etat.tourOuvert(), etat.passagePrioritaireId(), courant, configuration, trois, publics.size() - trois.size(),
                    instantane.lecteurDesigneId(), lecturePublique,
                    lecture != null && projection.courant().etat() == EtatPassage.EN_COURS ? lecture.paroles() : null,
                    projeterVotesPublics(instantane, file, pseudos, maintenant, catalogue));
            case CONSOLE -> new BlocKaraokeConsole(etat.mancheId(), instantane.etatManche(), etat.limitePassagesEnAttente(), publics.size(),
                    etat.tourOuvert(), etat.passagePrioritaireId(), courant, configuration, publics, true, true, true,
                    instantane.lecteurDesigneId() != null,
                    !instantane.pilotageAutomatique() && enCours && !projection.actif() && !publics.isEmpty(),
                    projection.actif(), enPause && !lectureEnErreur, enCours && lecture != null
                            && projection.courant().etat() == EtatPassage.EN_COURS && lecture.etat() == EtatLecture.DEMARREE,
                    lectureEnErreur, lecturePublique, lecture == null ? null : lecture.fichier(),
                    projeterVotesPublics(instantane, file, pseudos, maintenant, catalogue));
            case JOUEUR -> {
                UUID joueur = destination.joueurId();
                var personnels = publics.stream().filter(p -> p.interpretes().stream().anyMatch(i -> i.joueurId().equals(joueur))).toList();
                boolean peutInscrire = personnels.size() < etat.limitePassagesEnAttente();
                boolean peutConfirmer = enCours && projection.actif() && projection.courant().etat() == EtatPassage.APPELE
                        && projection.courant().interpretes().contains(joueur);
                yield new BlocKaraokeManette(etat.mancheId(), instantane.etatManche(), etat.limitePassagesEnAttente(), publics.size(),
                        etat.tourOuvert(), etat.passagePrioritaireId(), courant, configuration, trois, publics.size() - trois.size(),
                        personnels, personnels.size(), true, peutInscrire, peutInscrire ? null : "LIMITE_ATTEINTE", peutConfirmer,
                        votesVisibles(instantane, file).stream()
                                .map(vote -> projeterVoteManette(instantane, file, pseudos, maintenant, vote, joueur, catalogue)).toList());
            }
            default -> throw new IllegalArgumentException("Une destination concrète est requise.");
        };
    }

    private static List<VoteDuPassage> projeterVotesPublics(InstantaneKaraoke instantane, FileAttente file,
            Map<UUID, String> pseudos, Instant maintenant, CatalogueDeBase catalogue) {
        return votesVisibles(instantane, file).stream()
                .map(vote -> projeterVote(instantane, file, pseudos, maintenant, vote, catalogue)).toList();
    }

    private static List<EtatVotePassage> votesVisibles(InstantaneKaraoke instantane, FileAttente file) {
        var votes = instantane.voteDuPublic().votes();
        var visibles = new ArrayList<>(votes.stream().filter(EtatVotePassage::ouvert).toList());
        votes.stream().filter(vote -> !vote.ouvert())
                .max(Comparator.comparing(EtatVotePassage::clotureLe)
                        .thenComparingLong(vote -> file.trouver(vote.passageId()).numero()))
                .ifPresent(visibles::add);
        visibles.sort(Comparator.comparing(EtatVotePassage::ouvertLe).reversed()
                .thenComparing(Comparator.comparingLong((EtatVotePassage vote) -> file.trouver(vote.passageId()).numero()).reversed()));
        return visibles;
    }

    private static VoteDuPassage projeterVote(InstantaneKaraoke instantane, FileAttente file,
            Map<UUID, String> pseudos, Instant maintenant, EtatVotePassage vote, CatalogueDeBase catalogue) {
        Passage passage = file.trouver(vote.passageId());
        var projection = VoteDuPublic.projeter(vote, maintenant);
        return new VoteDuPassage(passage.id(), passage.numero(),
                ResumeChanson.de(instantane.contenus().get(passage.id()).chanson(), catalogue), interpretes(passage.interpretes(), pseudos),
                projection.ouvert() ? "OUVERT" : "CLOS", projection.delaiRestantMs(), projection.calculeLe(),
                projection.clotureLe(), projection.nombreVotes(), projection.suivantDemarre(), projection.moyenneDixiemes());
    }

    private static VoteDuPassageManette projeterVoteManette(InstantaneKaraoke instantane, FileAttente file,
            Map<UUID, String> pseudos, Instant maintenant, EtatVotePassage vote, UUID joueur, CatalogueDeBase catalogue) {
        Passage passage = file.trouver(vote.passageId());
        var publicVote = projeterVote(instantane, file, pseudos, maintenant, vote, catalogue);
        Integer maNote = vote.votes().stream().filter(note -> note.joueurId().equals(joueur))
                .map(VoteRecu::note).findFirst().orElse(null);
        boolean peutVoter = vote.ouvert() && instantane.etatManche() == EtatManche.EN_COURS
                && !passage.interpretes().contains(joueur) && maNote == null;
        return new VoteDuPassageManette(passage.id(), passage.numeroModification(), publicVote.numero(), publicVote.chanson(),
                publicVote.interpretes(), publicVote.etat(), publicVote.delaiRestantMs(), publicVote.calculeLe(),
                publicVote.clotureLe(), maNote, peutVoter, vote.ouvert() ? null : publicVote.nombreVotes(),
                publicVote.moyenneDixiemes());
    }

    private static CourantPourFile courant(InstantaneKaraoke instantane, ProjectionCycle cycle, Map<UUID, String> pseudos,
            CatalogueDeBase catalogue) {
        Passage passage = cycle.courant();
        if (passage == null) { return null; }
        return new CourantPourFile(passage.id(), passage.numeroModification(), passage.numero(),
                ResumeChanson.de(instantane.contenus().get(passage.id()).chanson(), catalogue), interpretes(passage.interpretes(), pseudos),
                cycle.etapeId(), cycle.etat(), cycle.prochainAppelPrevuLe(), cycle.appeleLe(), cycle.echeanceAppelLe(),
                cycle.delaiAppelRestantMs(), cycle.demarreLe(), cycle.finPrevueLe(), cycle.positionChronologiqueMs(),
                cycle.tempsPauseMs(), cycle.calculeLe(), cycle.clotureLe());
    }

    static LignePalmaresKaraoke lignePalmares(RangPassage ligne, ContenuKaraoke contenu, Map<UUID, String> pseudos,
            CatalogueDeBase catalogue) {
        var resultat = ligne.resultat();
        return new LignePalmaresKaraoke(resultat.passageId(), resultat.numero(), ligne.rang(),
                ResumeChanson.de(contenu.chanson(), catalogue), interpretes(resultat.interpretes(), pseudos),
                resultat.nombreVotes(), resultat.moyenneDixiemes());
    }

    private static List<InterpretePublic> interpretes(List<UUID> interpretes, Map<UUID, String> pseudos) {
        return interpretes.stream()
                .map(id -> new InterpretePublic(id, pseudos.getOrDefault(id, ParticipantsDePassage.JOUEUR_PARTI))).toList();
    }

    private static LectureDeChanson lecturePublique(LecturePassage lecture) {
        if (lecture == null) { return null; }
        return new LectureDeChanson(lecture.lectureId(), lecture.lecteurId(), lecture.etat(), lecture.source(),
                lecture.motifParolesSeules(), lecture.origineParoles(), lecture.decalageMs(), lecture.numeroModificationCalage(),
                lecture.demandeId() == null ? null : new DemandePositionLecture(lecture.demandeId(), lecture.demandeeLe()));
    }
}
