package fr.croustyquizz.karaoke;

import static fr.croustyquizz.karaoke.ConfigurationTroisJoueurs.joueursDe;
import static org.assertj.core.api.Assertions.*;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Flyway et JPA réels ; ces tests exigent Docker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, ConfigurationTroisJoueurs.class})
class KaraokeJpaTest {
    @Autowired PersistancePassageJpa passages;
    @Autowired PersistanceKaraokeJpa contenus;
    @Autowired SocleDeManche socle;
    @Autowired ServiceKaraoke service;
    @Autowired MemoireDeCalageJpa calages;
    @Autowired JdbcTemplate jdbc;

    @Test void litLesVotesClosDesManchesDemandeesAvecLesInterpretesDansLeurOrdre() {
        UUID soiree = UUID.randomUUID(), manche = UUID.randomUUID(), autreManche = UUID.randomUUID();
        UUID premier = new UUID(0, 2), second = new UUID(0, 1);
        var interpretes = List.of(premier, second);
        var seul = List.of(premier);
        var instant = Instant.parse("2026-11-18T21:00:00Z");
        var notees = passagePourPalmares(soiree, manche, 1, interpretes, EtatPassage.TERMINE, instant);
        var sansVote = passagePourPalmares(soiree, manche, 2, seul, EtatPassage.TERMINE, instant);
        var voteOuvert = passagePourPalmares(soiree, manche, 3, seul, EtatPassage.TERMINE, instant);
        var saute = passagePourPalmares(soiree, manche, 4, seul, EtatPassage.SAUTE, instant);
        var ailleurs = passagePourPalmares(soiree, autreManche, 5, seul, EtatPassage.TERMINE, instant);
        var notes = List.of(new VoteRecu(UUID.randomUUID(), 4, instant.plusSeconds(1)),
                new VoteRecu(UUID.randomUUID(), 5, instant.plusSeconds(2)));
        var transaction = new TransactionTemplate(socle.transactions());
        transaction.executeWithoutResult(statut -> {
            enregistrerPourPalmares(contenus, List.of(notees, sansVote, voteOuvert, saute), List.of(
                    votePourPalmares(notees, instant.plusSeconds(10), notes),
                    votePourPalmares(sansVote, instant.plusSeconds(10), List.of()),
                    votePourPalmares(voteOuvert, null, notes)));
            enregistrerPourPalmares(contenus, List.of(ailleurs),
                    List.of(votePourPalmares(ailleurs, instant.plusSeconds(10), notes)));
        });
        var resultats = transaction.execute(statut -> passages.resultatsDeVote(List.of(manche, UUID.randomUUID())));
        assertThat(resultats).containsExactlyInAnyOrder(
                new ResultatVote(notees.id(), manche, 1, interpretes, 2, 9, 45),
                new ResultatVote(sansVote.id(), manche, 2, seul, 0, 0, null));
        assertThat(passages.resultatsDeVote(List.of())).isEmpty();
    }

    static Passage passagePourPalmares(UUID soiree, UUID manche, long numero, List<UUID> interpretes,
            EtatPassage etat, Instant instant) {
        return new Passage(UUID.randomUUID(), soiree, manche, interpretes.getFirst(), interpretes,
                IdentifiantsChanson.encoder(numero), Duration.ofMinutes(3), numero, 3, etat,
                instant.minusSeconds(180), numero, 1, numero, 1L, new TraceTerminale(etat.name(), null, instant));
    }

    static EtatVotePassage votePourPalmares(Passage passage, Instant cloture, List<VoteRecu> notes) {
        Instant ouverture = passage.traceTerminale().instant();
        return new EtatVotePassage(passage.id(), ouverture, ouverture.plusSeconds(30), null, cloture, notes);
    }

    static void enregistrerPourPalmares(PersistanceKaraokeJpa persistance, List<Passage> passages, List<EtatVotePassage> votes) {
        var premier = passages.getFirst();
        var file = new EtatFileAttente(premier.soireeId(), premier.mancheId(), 2, 1, 10, 10, null, passages, Map.of());
        Map<UUID, ContenuKaraoke> chansons = new HashMap<>();
        passages.forEach(passage -> chansons.put(passage.id(), ServiceKaraokeTest.contenu(passage.numero())));
        persistance.enregistrer(premier.mancheId(), new InstantaneKaraoke(EtatManche.TERMINEE, file, chansons,
                EtatCyclePassage.vide(), Map.of(), null, new EtatVoteDuPublic(votes)));
    }

    @Test void conserveLesTracesParolesEtNumerosDansUneSeuleTransaction() {
        UUID soiree = UUID.randomUUID(), manche = UUID.randomUUID(), id = UUID.randomUUID(), joueur = UUID.randomUUID();
        var instant = Instant.parse("2026-11-17T10:00:00Z");
        var passage = new Passage(id, soiree, manche, joueur, List.of(joueur), IdentifiantsChanson.encoder(1),
                Duration.ofSeconds(180), 1, 2, EtatPassage.RETIRE, instant, 1, 2, 7, null,
                new TraceTerminale("RETRAIT", null, instant));
        var file = new EtatFileAttente(soiree, manche, 3, 2, 2, 8, null, List.of(passage), Map.of(joueur, 1L));
        var contenu = new ContenuKaraoke(new Chanson(IdentifiantsChanson.encoder(1), 1, "Titre", "Artiste", null,
                180000L, EtatParoles.SYNCHRONISEES), List.of(new LigneParoles(0, "Un"), new LigneParoles(0, "Deux")));
        var transaction = new TransactionTemplate(socle.transactions());
        transaction.executeWithoutResult(s -> {
            assertThat(passages.numeroPropose(soiree)).isEqualTo(1);
            contenus.enregistrer(manche, new InstantaneKaraoke(EtatManche.EN_PAUSE, file, Map.of(id, contenu)));
            passages.incrementerNumero(soiree, 1);
        });
        transaction.executeWithoutResult(s -> {
            assertThat(contenus.charger(manche)).isEqualTo(new EtatKaraokePersiste(file, Map.of(id, contenu)));
            assertThat(passages.numeroPropose(soiree)).isEqualTo(2);
            passages.incrementerNumero(soiree, 2);
            s.setRollbackOnly();
        });
        transaction.executeWithoutResult(s -> assertThat(passages.numeroPropose(soiree)).isEqualTo(2));
    }

    @Test void restaureLesInterpretesOrdonnesEtLeNouvelInscripteurApresEviction() {
        UUID soiree = UUID.randomUUID();
        UUID ana = joueursDe(soiree).getFirst();
        UUID boris = joueursDe(soiree).get(1);
        UUID celia = joueursDe(soiree).get(2);
        var animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
        UUID manche = service.creer(soiree, Map.of());
        service.demarrer(manche, envoi(animateur, "/demarrer", Map.of()));
        UUID passage = inscrire(manche, new Acteur(Acteur.Type.JOUEUR, ana, soiree), 1);
        var solo = (InstantaneKaraoke) service.instantane(soiree, manche);
        var file = FileAttente.depuis(solo.file());
        file.ajouterInterprete(new CiblePassage(passage, 0), celia);
        file.ajouterInterprete(new CiblePassage(passage, 1), boris);
        var trio = enregistrerFilePuisRestaurer(file.etat());
        assertThat(trio.passages().getFirst().interpretes()).containsExactly(ana, celia, boris);

        var restauree = FileAttente.depuis(trio);
        restauree.retirerJoueurParti(ana, socle.horloge().maintenant());
        var duo = enregistrerFilePuisRestaurer(restauree.etat()).passages().getFirst();
        assertThat(duo.interpretes()).containsExactly(celia, boris);
        assertThat(duo.inscripteurId()).isEqualTo(celia);

        var appel = new CorpsKaraoke.AppelPassage(duo.numeroModification());
        assertThat(service.appelerPassage(manche, passage, envoi(animateur, "/appel", appel), appel))
                .isInstanceOf(Accepte.class);
        assertThat(verifierRestauration(soiree, manche).file().derniersToursConsommes())
                .containsExactlyInAnyOrderEntriesOf(Map.of(celia, 1L, boris, 1L));
    }

    @Test void restaureLesInvitationsDansLeurOrdreEtLeurEtatJusquALeurExpiration() {
        UUID soiree = UUID.randomUUID();
        List<UUID> joueurs = joueursDe(soiree);
        UUID ana = joueurs.getFirst();
        UUID boris = joueurs.get(1);
        UUID celia = joueurs.get(2);
        var animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
        UUID manche = service.creer(soiree, Map.of());
        service.demarrer(manche, envoi(animateur, "/demarrer", Map.of()));
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(1), List.of(celia, boris));
        var inscription = service.inscrirePassage(manche, envoi(new Acteur(Acteur.Type.JOUEUR, ana, soiree), "/passages", corps), corps);
        UUID passage = ((AccusePassage) ((Accepte) inscription).accuse()).passageId();
        socle.registre().retirer(manche);
        assertThat(verifierRestauration(soiree, manche).file().invitations()).containsExactly(
                new InvitationPassage(passage, celia, InvitationPassage.Etat.EN_ATTENTE),
                new InvitationPassage(passage, boris, InvitationPassage.Etat.EN_ATTENTE));

        var file = FileAttente.depuis(((InstantaneKaraoke) service.instantane(soiree, manche)).file());
        file.repondreInvitation(celia, new CiblePassage(passage, 0), true);
        file.repondreInvitation(boris, new CiblePassage(passage, 1), false);
        assertThat(enregistrerFilePuisRestaurer(file.etat()).invitations()).containsExactly(
                new InvitationPassage(passage, celia, InvitationPassage.Etat.ACCEPTEE),
                new InvitationPassage(passage, boris, InvitationPassage.Etat.REFUSEE));

        var appel = new CorpsKaraoke.AppelPassage(1L);
        assertThat(service.appelerPassage(manche, passage, envoi(animateur, "/appel", appel), appel)).isInstanceOf(Accepte.class);
        assertThat(verifierRestauration(soiree, manche).file().passages().getFirst().interpretes()).containsExactly(ana, celia);
    }

    private EtatFileAttente enregistrerFilePuisRestaurer(EtatFileAttente file) {
        UUID manche = file.mancheId();
        new TransactionTemplate(socle.transactions()).executeWithoutResult(s -> passages.enregistrer(manche, file));
        socle.registre().retirer(manche);
        var restauree = (InstantaneKaraoke) service.instantane(file.soireeId(), manche);
        assertThat(restauree.file()).isEqualTo(file);
        return restauree.file();
    }

    @Test void restaurePausePrioriteReservationsEtRecusPuisContinueLesNumerosSurUneAutreManche() {
        UUID soiree = UUID.randomUUID();
        var animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
        var joueur = new Acteur(Acteur.Type.JOUEUR, joueursDe(soiree).getFirst(), soiree);
        UUID manche = service.creer(soiree, Map.of("limitePassagesEnAttente", 2));
        service.demarrer(manche, envoi(animateur, "/demarrer", Map.of()));
        var premierCorps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(1));
        var premierEnvoi = new EnvoiDeCommande(joueur, "/passages", premierCorps.envoiId(), new CorpsKaraoke.EmpreinteHttp("POST", premierCorps));
        var premier = (AccusePassage) ((Accepte) service.inscrirePassage(manche, premierEnvoi, premierCorps)).accuse();
        var secondCorps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(2));
        var second = (AccusePassage) ((Accepte) service.inscrirePassage(manche,
                new EnvoiDeCommande(joueur, "/passages", secondCorps.envoiId(), secondCorps), secondCorps)).accuse();
        var priorite = new CorpsKaraoke.PrioritePassage(new CorpsKaraoke.Cible(second.passageId(), 0L), true);
        service.definirPrioritePassage(manche, envoi(animateur, "/priorite", priorite), priorite);
        var pause = new InterventionDeManche(InterventionDeManche.Action.METTRE_EN_PAUSE, null);
        service.piloter(manche, envoi(animateur, "/commandes", pause), pause);
        var avant = (InstantaneKaraoke) service.instantane(soiree, manche);
        assertThat(avant.file().passagePrioritaireId()).isEqualTo(second.passageId());
        assertThat(avant.file().passages()).extracting(Passage::tourNormal).containsExactly(1L, 2L);
        socle.registre().retirer(manche);
        assertThat(service.instantane(soiree, manche)).isEqualTo(avant);
        var rejeu = service.inscrirePassage(manche, premierEnvoi, premierCorps);
        assertThat(ReponsesDeCommande.statut(rejeu)).isEqualTo(201);
        assertThat(socle.json().readTree(socle.json().writeValueAsString(((Accepte) rejeu).accuse())).path("passageId").asString()).isEqualTo(premier.passageId().toString());
        var fin = new InterventionDeManche(InterventionDeManche.Action.TERMINER, null);
        service.piloter(manche, envoi(animateur, "/commandes", fin), fin);
        socle.registre().retirer(manche);
        var terminee = (InstantaneKaraoke) service.instantane(soiree, manche);
        assertThat(terminee.contenus()).hasSize(2);
        assertThat(terminee.file().passages()).allSatisfy(p -> {
            assertThat(p.etat()).isEqualTo(EtatPassage.ANNULE);
            assertThat(p.traceTerminale().motif()).isEqualTo("MANCHE_TERMINEE");
        });
        UUID suivante = service.creer(soiree, Map.of());
        service.demarrer(suivante, envoi(animateur, "/demarrer-suivante", Map.of()));
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(3));
        service.inscrirePassage(suivante, envoi(joueur, "/passages-suivante", corps), corps);
        assertThat(((InstantaneKaraoke) service.instantane(soiree, suivante)).file().passages().getFirst().numero()).isEqualTo(3);
    }
    @Test void restaureLeCycleLaLectureEtLeLecteurSansRejouerLeDepart() {
        UUID soiree = UUID.randomUUID();
        var animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
        var joueur = new Acteur(Acteur.Type.JOUEUR, joueursDe(soiree).getFirst(), soiree);
        UUID manche = service.creer(soiree, Map.of());
        service.demarrer(manche, envoi(animateur, "/demarrer", Map.of()));
        var inscription = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(1));
        var accuse = (AccusePassage) ((Accepte) service.inscrirePassage(manche,
                envoi(joueur, "/passages", inscription), inscription)).accuse();
        var transaction = new TransactionTemplate(socle.transactions());
        var initial = (InstantaneKaraoke) service.instantane(soiree, manche);
        UUID passage = accuse.passageId();
        UUID lecteur = UUID.randomUUID();
        Instant appel = Instant.parse("2026-11-17T10:00:00Z");
        var cycle = new EtatCyclePassage(passage, UUID.randomUUID(), appel, appel.plusSeconds(30),
                null, null, Duration.ZERO, null, null);
        var parole = new ParolesDuPassage(UUID.randomUUID(), 180_000, List.of(new LigneParoles(0, "Un")));
        var lecture = new LecturePassage(UUID.randomUUID(), passage, lecteur, EtatLecture.PREPAREE,
                SourceLecture.FICHIER_LOCAL, new FichierAudioLocal("Un.mp3", 120, null), null,
                OrigineParoles.REPLI_INSCRIPTION, parole, 1, 100, 0, null, null);
        var prepare = new InstantaneKaraoke(EtatManche.EN_COURS, initial.file(), initial.contenus(),
                cycle, Map.of(passage, lecture), lecteur);
        transaction.executeWithoutResult(s -> contenus.enregistrer(manche, prepare));
        transaction.executeWithoutResult(s -> assertThat(contenus.charger(manche))
                .isEqualTo(new EtatKaraokePersiste(initial.file(), initial.contenus(), cycle, Map.of(passage, lecture), lecteur)));
        var remplace = new InstantaneKaraoke(EtatManche.EN_COURS, initial.file(), initial.contenus(),
                cycle, Map.of(), UUID.randomUUID());
        transaction.executeWithoutResult(s -> contenus.enregistrer(manche, remplace));
        transaction.executeWithoutResult(s -> assertThat(contenus.charger(manche))
                .isEqualTo(new EtatKaraokePersiste(initial.file(), initial.contenus(), cycle, Map.of(), remplace.lecteurDesigneId())));
    }

    @Test void restaureUnAppelPuisUnChantAvecDemandeDePositionEnPauseEtClos() {
        UUID soiree = UUID.randomUUID();
        var animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        var joueur = new Acteur(Acteur.Type.JOUEUR, joueursDe(soiree).getFirst(), soiree);
        UUID manche = service.creer(soiree, Map.of("limitePassagesEnAttente", 2));
        service.demarrer(manche, envoi(animateur, "/demarrer", Map.of()));
        UUID premier = inscrire(manche, joueur, 1);
        UUID second = inscrire(manche, joueur, 2);
        var appel = new CorpsKaraoke.AppelPassage(0L);
        var accuse = (AccuseCyclePassage) ((Accepte) service.appelerPassage(manche, premier,
                envoi(animateur, "/premier/appel", appel), appel)).accuse();
        UUID etape = accuse.etapeId();
        verifierRestauration(soiree, manche);
        assertThat(service.appelerPassage(manche, second, envoi(animateur, "/second/appel", appel), appel))
                .isInstanceOfSatisfying(Refuse.class, r -> assertThat(r.code()).isEqualTo("ETAT_INCOMPATIBLE"));

        var designation = new CorpsKaraoke.DesignationLecteur(UUID.randomUUID());
        service.designerLecteurChanson(manche, envoi(ecran, "/lecteur", designation), designation);
        var corps = new CorpsKaraoke.DeclarationLectureChanson(designation.lecteurId(), etape, 1L, "FICHIER_LOCAL",
                new CorpsKaraoke.Fichier("Un.mp3", 120L, 186_750L), null);
        var lecture = (AccuseLectureChanson) ((Accepte) service.declarerLectureChanson(manche, premier,
                envoi(ecran, "/premier/lecture", corps), corps)).accuse();
        var presence = new CorpsKaraoke.ConfirmationPresencePassage(UUID.randomUUID(), etape, 1L);
        service.confirmerPresencePassage(manche, premier, envoi(joueur, "/premier/presence", presence), presence);
        var geste = new CorpsKaraoke.MarqueDebutParoles(etape, lecture.lectureId());
        var demande = (AccuseCalage) ((Accepte) service.marquerDebutParoles(manche, premier,
                envoi(animateur, "/premier/calage/debut", geste), geste)).accuse();
        var chante = verifierRestauration(soiree, manche);
        assertThat(chante.cycle().demarreLe()).isNotNull();
        assertThat(chante.cycle().dureeReference()).isEqualTo(Duration.ofMillis(186_750));
        var lectureRestauree = chante.lectures().get(premier);
        assertThat(lectureRestauree.demandeId()).isEqualTo(demande.demandeId());
        assertThat(lectureRestauree.demandeeLe()).isEqualTo(demande.recueLe());
        assertThat(lectureRestauree.fichier().dureeMs()).isEqualTo(186_750L);

        var pause = new InterventionDeManche(InterventionDeManche.Action.METTRE_EN_PAUSE, etape);
        service.piloter(manche, envoi(animateur, "/commandes", pause), pause);
        assertThat(verifierRestauration(soiree, manche).cycle().pauseDepuis()).isNotNull();
        ((HorlogeFactice) socle.horloge()).avancer(Duration.ofNanos(450_000));
        var reprendre = new InterventionDeManche(InterventionDeManche.Action.REPRENDRE, etape);
        service.piloter(manche, envoi(animateur, "/commandes", reprendre), reprendre);
        assertThat(verifierRestauration(soiree, manche).cycle().tempsPause()).isEqualTo(Duration.ofNanos(450_000));

        var fin = new CorpsKaraoke.SignalFinLectureChanson(designation.lecteurId(), etape, lecture.lectureId(), 160_000L);
        assertThat(service.signalerFinLectureChanson(manche, premier, envoi(ecran, "/premier/fin", fin), fin))
                .isInstanceOf(Accepte.class);
        var clos = verifierRestauration(soiree, manche);
        assertThat(clos.cycle().clotureLe()).isNotNull();
        assertThat(clos.lectures().get(premier).demandeId()).isNull();

        assertThatThrownBy(() -> jdbc.update("UPDATE passage_cycle SET echeance_appel_le = NULL WHERE manche_id = ?", manche))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE passage_cycle SET pause_depuis = cloture_le WHERE manche_id = ?", manche))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE karaoke_lecture_passage SET origine_paroles = 'INSCRIPTION' WHERE passage_id = ?", premier))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID inscrire(UUID manche, Acteur joueur, long chanson) {
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(chanson));
        return ((AccusePassage) ((Accepte) service.inscrirePassage(manche, envoi(joueur, "/passages", corps), corps)).accuse()).passageId();
    }

    private InstantaneKaraoke verifierRestauration(UUID soiree, UUID manche) {
        var avant = (InstantaneKaraoke) service.instantane(soiree, manche);
        var persiste = new TransactionTemplate(socle.transactions()).execute(s -> contenus.charger(manche));
        assertThat(persiste).isEqualTo(new EtatKaraokePersiste(
                avant.file(), avant.contenus(), avant.cycle(), avant.lectures(), avant.lecteurDesigneId(), avant.voteDuPublic()));
        socle.registre().retirer(manche);
        assertThat(service.instantane(soiree, manche)).isEqualTo(avant);
        return avant;
    }

    @Test void conserveUnVoteOuvertFigePuisClosAvecLeDemarrageSuivantEtLesContraintesDeNote() {
        UUID soiree = UUID.randomUUID(), manche = UUID.randomUUID(), passage = UUID.randomUUID();
        UUID interprete = UUID.randomUUID(), votant = UUID.randomUUID();
        Instant ouverture = Instant.parse("2026-11-18T21:00:00Z");
        var file = FileAttente.depuis(new EtatFileAttente(soiree, manche, 1, 1, 1, 1, null, List.of(), Map.of()));
        var contenu = ServiceKaraokeTest.contenu(1);
        file.inscrire(new NouveauPassage(passage, soiree, manche, interprete, List.of(interprete),
                contenu.chanson().chansonId(), Duration.ofMinutes(3), 1, ouverture.minusSeconds(180)));
        file.appelerPassage(new CiblePassage(passage, 0));
        file.demarrerPassage(new CiblePassage(passage, 1));
        file.clorePassage(new CiblePassage(passage, 2), EtatPassage.TERMINE,
                new TraceTerminale("FIN_PREVUE", null, ouverture));
        var note = new VoteRecu(votant, 4, ouverture.plusSeconds(2));
        var transaction = new TransactionTemplate(socle.transactions());
        List<EtatVotePassage> etats = List.of(
                new EtatVotePassage(passage, ouverture, ouverture.plusSeconds(30), null, null, List.of(note)),
                new EtatVotePassage(passage, ouverture, ouverture.plusSeconds(30), ouverture.plusSeconds(5), null, List.of(note), true),
                new EtatVotePassage(passage, ouverture, ouverture.plusSeconds(30), null, ouverture.plusSeconds(8), List.of(note), true));
        for (var etat : etats) {
            var votes = new EtatVoteDuPublic(List.of(etat));
            transaction.executeWithoutResult(suite -> contenus.enregistrer(manche, new InstantaneKaraoke(
                    etat.pauseDepuis() == null ? EtatManche.EN_COURS : EtatManche.EN_PAUSE, file.etat(),
                    Map.of(passage, contenu), EtatCyclePassage.vide(), Map.of(), null, votes)));
            assertThat(transaction.execute(suite -> contenus.charger(manche)).voteDuPublic()).isEqualTo(votes);
        }
        String insertion = "INSERT INTO passage_note(passage_id, joueur_id, note, recue_le) VALUES (?, ?, ?, ?)";
        for (int invalide : List.of(0, 6)) {
            assertThatThrownBy(() -> jdbc.update(insertion, passage, UUID.randomUUID(), invalide, java.sql.Timestamp.from(ouverture)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update(insertion, passage, votant, 5, java.sql.Timestamp.from(ouverture)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE passage_vote SET pause_depuis = cloture_le WHERE passage_id = ?", passage))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void laMemoireDistingueLesVersionsEtLeDernierCommitConcurrentRemplaceLeCalage() throws Exception {
        var transaction = new TransactionTemplate(socle.transactions());
        String chansonId = UUID.randomUUID().toString();
        var fichier = new FichierAudioLocal("Fichier.mp3", 123, null);
        Instant maintenant = Instant.parse("2026-11-17T10:00:00.123456789Z");
        transaction.executeWithoutResult(s -> calages.memoriser(chansonId, 2, fichier, 900, maintenant));
        var ecrit = new CountDownLatch(1);
        var liberer = new CountDownLatch(1);
        var concurrent = new CountDownLatch(1);
        try (var fils = Executors.newFixedThreadPool(2)) {
            try {
                var premier = fils.submit(() -> transaction.executeWithoutResult(s -> {
                    calages.memoriser(chansonId, 1, fichier, 100, maintenant);
                    ecrit.countDown();
                    ServiceKaraokeTest.attendre(liberer);
                }));
                ServiceKaraokeTest.attendre(ecrit);
                var second = fils.submit(() -> transaction.executeWithoutResult(s -> {
                    concurrent.countDown();
                    calages.memoriser(chansonId, 1, fichier, 200, maintenant.plusSeconds(1));
                }));
                ServiceKaraokeTest.attendre(concurrent);
                liberer.countDown();
                premier.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            } finally { liberer.countDown(); }
        }
        verifierMetadonneesCalage(chansonId, 2L, 1L, Instant.parse("2026-11-17T10:00:00.123456Z"));
        verifierMetadonneesCalage(chansonId, 1L, 2L, Instant.parse("2026-11-17T10:00:01.123456Z"));
        transaction.executeWithoutResult(s -> calages.memoriser(chansonId, 1, fichier, 200, maintenant.plusSeconds(2)));
        verifierMetadonneesCalage(chansonId, 1L, 2L, Instant.parse("2026-11-17T10:00:01.123456Z"));
        transaction.executeWithoutResult(s -> {
            assertThat(calages.lire(chansonId, 1, fichier.nom(), fichier.tailleOctets())).isEqualTo(200);
            assertThat(calages.lire(chansonId, 2, fichier.nom(), fichier.tailleOctets())).isEqualTo(900);
            calages.memoriser(chansonId, 1, fichier, 300, maintenant.plusSeconds(2));
            s.setRollbackOnly();
        });
        transaction.executeWithoutResult(s -> assertThat(calages.lire(chansonId, 1, fichier.nom(), fichier.tailleOctets())).isEqualTo(200));
    }

    private void verifierMetadonneesCalage(String chansonId, long version, long numero, Instant modification) {
        var lignes = jdbc.query("""
                SELECT numero_modification, modifie_le FROM karaoke_calage_chanson
                WHERE chanson_id = ? AND version_source_id = ?
                """, (ligne, index) -> {
                    assertThat(ligne.getLong("numero_modification")).isEqualTo(numero);
                    return ligne.getObject("modifie_le", OffsetDateTime.class).toInstant();
                }, chansonId, version);
        assertThat(lignes).containsExactly(modification);
    }

    static EnvoiDeCommande envoi(Acteur acteur, String route, Object corps) { return new EnvoiDeCommande(acteur, route, UUID.randomUUID(), corps); }

}
