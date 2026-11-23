package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.*;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
    @Test void restaurePausePrioriteReservationsEtRecusPuisContinueLesNumerosSurUneAutreManche() {
        UUID soiree = UUID.randomUUID();
        var animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
        var joueur = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.getFirst(), soiree);
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
        var joueur = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.getFirst(), soiree);
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

    static EnvoiDeCommande envoi(Acteur acteur, String route, Object corps) { return new EnvoiDeCommande(acteur, route, UUID.randomUUID(), corps); }

}
