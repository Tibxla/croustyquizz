package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import fr.croustyquizz.ContratApi;
import fr.croustyquizz.manche.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

/** Projections par destination avec vrai service et assembleur, sans base ni réseau. */
class FournisseurDeBlocKaraokeTest {
    final ServiceKaraokeTest parcours = new ServiceKaraokeTest();
    final ServiceKaraokeTest.BancKaraoke banc = parcours.banc;

    @Test void unePublicationLitLesParticipantsUneFoisPourTousLesPassagesEtToutesLesManettes() {
        UUID manche = parcours.demarrer();
        var limite = new CorpsKaraoke.LimitePassages(4L);
        banc.service.reglerLimitePassages(manche, parcours.envoi(parcours.animateur, UUID.randomUUID(), "PUT", "/limite", limite), limite);
        parcours.inscrire(manche, 1);
        parcours.inscrire(manche, 2);
        parcours.inscrire(manche, 3);
        clearInvocations(banc.participantsDesVues);
        int publications = banc.diffuseur.envois().size();

        parcours.inscrire(manche, 4);

        assertThat(banc.diffuseur.envois()).hasSize(publications + 5);
        assertThat(mockingDetails(banc.participantsDesVues).getInvocations()).hasSize(1);
    }

    @Test void ordreEffectifPersonnelEtTroisProchainesSansSecrets() throws Exception {
        UUID manche = parcours.demarrer();
        var limite = new CorpsKaraoke.LimitePassages(3L);
        banc.service.reglerLimitePassages(manche, parcours.envoi(parcours.animateur, UUID.randomUUID(), "PUT", "/limite", limite), limite);
        var premier = parcours.inscrire(manche, 1);
        var second = parcours.inscrire(manche, 2);
        var troisieme = parcours.inscrire(manche, 3);
        var autre = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.get(1), parcours.soiree);
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(4));
        banc.service.inscrirePassage(manche, parcours.envoi(autre, corps.envoiId(), "POST", "/passages", corps), corps);
        var priorite = new CorpsKaraoke.PrioritePassage(new CorpsKaraoke.Cible(troisieme.passageId(), 0L), true);
        banc.service.definirPrioritePassage(manche, parcours.envoi(parcours.animateur, UUID.randomUUID(), "PUT", "/priorite", priorite), priorite);
        var manette = (VuesKaraoke.BlocKaraokeManette) banc.bloc.fournir(parcours.soiree, Destination.joueur(parcours.joueur.identifiant()));
        assertThat(manette.mesPassagesEnAttente()).extracting(VuesKaraoke.PassageEnAttentePublic::passageId)
                .containsExactly(troisieme.passageId(), premier.passageId(), second.passageId());
        assertThat(manette.prochainsPassages()).hasSize(3); assertThat(manette.nombreAutresPassages()).isEqualTo(1);
        assertThat(manette.peutRechercher()).isTrue(); assertThat(manette.peutInscrire()).isFalse();
        assertThat(manette.motifInscriptionRefusee()).isEqualTo("LIMITE_ATTEINTE");
        var console = (VuesKaraoke.BlocKaraokeConsole) banc.bloc.fournir(parcours.soiree, Destination.CONSOLE);
        assertThat(console.passagesEnAttente()).hasSize(4);
        assertThat(console.passagesEnAttente()).extracting(VuesKaraoke.PassageEnAttentePublic::position).containsExactly(1L, 2L, 3L, 4L);
        assertThat(console.passagesEnAttente().getFirst().interpretes().getFirst().pseudo()).isEqualTo("Joueur " + parcours.joueur.identifiant());
        assertThat(console.courant()).isNull();
        String texte = banc.json.writeValueAsString(console);
        assertThat(texte).doesNotContain("idLrclib", "lignes", "source", "jeton", "points", "Une ligne");
        verifierCapture(Destination.SALLE, "retrouverEtatSalle");
        verifierCapture(Destination.CONSOLE, "retrouverEtatConsole");
        verifierCapture(Destination.joueur(parcours.joueur.identifiant()), "retrouverEtatManette");
    }

    @Test void capacitesVraiesVideOuEnPauseEtInitialeDistincteDeLaLimiteCourante() {
        UUID manche = parcours.demarrer();
        var vide = (VuesKaraoke.BlocKaraokeConsole) banc.bloc.fournir(parcours.soiree, Destination.CONSOLE);
        assertThat(vide.peutGererFile() && vide.peutReglerLimite() && vide.peutRechercher()).isTrue();
        parcours.piloter(manche, InterventionDeManche.Action.METTRE_EN_PAUSE);
        var limite = new CorpsKaraoke.LimitePassages(2L);
        banc.service.reglerLimitePassages(manche, parcours.envoi(parcours.animateur, UUID.randomUUID(), "PUT", "/limite", limite), limite);
        parcours.inscrire(manche, 1);
        var pause = (VuesKaraoke.BlocKaraokeConsole) banc.bloc.fournir(parcours.soiree, Destination.CONSOLE);
        assertThat(pause.peutGererFile() && pause.peutReglerLimite() && pause.peutRechercher()).isTrue();
        assertThat(pause.limitePassagesEnAttente()).isEqualTo(2);
        assertThat(pause.passagesEnAttente().getFirst().attente().etat().name()).isEqualTo("SUSPENDUE");
        assertThat(pause.passagesEnAttente().getFirst().attente().dureeMs()).isNull();
        assertThat(banc.json.readTree(banc.depot.charger(manche).getReglages()).path("limitePassagesEnAttente").asLong()).isEqualTo(1);
        banc.registre.retirer(manche);
        assertThat(banc.bloc.fournir(parcours.soiree, Destination.CONSOLE)).isEqualTo(pause);
    }

    @Test void retardataireAutoriseEtJoueurAbsentSousQuotaRefuse() {
        UUID manche = parcours.demarrer();
        UUID joueur = ConfigurationTroisJoueurs.JOUEURS.get(2);
        // Fournisseur commun : eligiblePourMancheActive=false pour tous les Joueurs.
        var retardataire = (VuesKaraoke.BlocKaraokeManette) banc.bloc.fournir(parcours.soiree, Destination.joueur(joueur));
        assertThat(retardataire.peutInscrire() && retardataire.peutRechercher()).isTrue();
        assertThat(retardataire.motifInscriptionRefusee()).isNull();
        banc.ports.absents.add(joueur);
        assertThatThrownBy(() -> banc.bloc.fournir(parcours.soiree, Destination.joueur(joueur)))
                .isInstanceOfSatisfying(RefusDeCommande.class, e -> assertThat(e.refus().statutHttp()).isEqualTo(404));
        parcours.piloter(manche, InterventionDeManche.Action.TERMINER);
        assertThat(banc.bloc.fournir(parcours.soiree, Destination.SALLE)).isNull();
    }

    @Test void estimationVientDeLHorlogeEtLePseudoDepartEstConserveSansAccesALaSource() {
        UUID manche = parcours.demarrer();
        var premier = parcours.inscrire(manche, 1);
        var autre = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.get(1), parcours.soiree);
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(2));
        banc.service.inscrirePassage(manche, parcours.envoi(autre, corps.envoiId(), "POST", "/passages", corps), corps);
        banc.ports.absents.add(parcours.joueur.identifiant());
        var salle = (VuesKaraoke.BlocKaraokeSalle) banc.bloc.fournir(parcours.soiree, Destination.SALLE);
        assertThat(salle.prochainsPassages().getFirst().passageId()).isEqualTo(premier.passageId());
        assertThat(salle.prochainsPassages().getFirst().interpretes().getFirst().pseudo()).isEqualTo("Joueur parti");
        assertThat(salle.prochainsPassages().get(1).attente().dureeMs()).isEqualTo(210000);
        assertThat(salle.prochainsPassages().get(1).attente().calculeeLe()).isEqualTo(banc.horloge.maintenant());
    }

    @Test void passageAuReposSansCourantNiLectureEtAppelSelonLaFileEtLaPause() throws Exception {
        UUID manche = parcours.demarrer();
        var vide = (VuesKaraoke.BlocKaraokeConsole) banc.bloc.fournir(parcours.soiree, Destination.CONSOLE);
        assertThat(vide.peutAppeler()).isFalse();
        assertThat(vide.peutReprendre()).isFalse();
        assertThat(vide.configurationInterface().delaiAppelMs()).isEqualTo(30000);
        parcours.inscrire(manche, 1);
        var console = (VuesKaraoke.BlocKaraokeConsole) banc.bloc.fournir(parcours.soiree, Destination.CONSOLE);
        assertThat(console.peutAppeler()).isTrue();
        assertThat(console.peutReprendre()).isFalse();
        assertThat(console.lecteurDesigne() || console.peutPasser() || console.peutCaler()
                || console.peutReprendreEnParolesSeules()).isFalse();
        assertThat(console.courant()).isNull();
        assertThat(console.lecture()).isNull();
        assertThat(console.fichierLocal()).isNull();
        var salle = (VuesKaraoke.BlocKaraokeSalle) banc.bloc.fournir(parcours.soiree, Destination.SALLE);
        assertThat(salle.courant()).isNull();
        assertThat(salle.lecteurDesigneId()).isNull();
        assertThat(salle.lecture()).isNull();
        assertThat(salle.paroles()).isNull();
        var manette = (VuesKaraoke.BlocKaraokeManette) banc.bloc.fournir(parcours.soiree, Destination.joueur(parcours.joueur.identifiant()));
        assertThat(manette.peutConfirmerPresence()).isFalse();
        assertThat(manette.courant()).isNull();
        parcours.piloter(manche, InterventionDeManche.Action.METTRE_EN_PAUSE);
        var pause = (VuesKaraoke.BlocKaraokeConsole) banc.bloc.fournir(parcours.soiree, Destination.CONSOLE);
        assertThat(pause.peutAppeler()).isFalse();
        assertThat(pause.peutReprendre()).isTrue();
        verifierCapture(Destination.SALLE, "retrouverEtatSalle");
        verifierCapture(Destination.CONSOLE, "retrouverEtatConsole");
        verifierCapture(Destination.joueur(parcours.joueur.identifiant()), "retrouverEtatManette");
    }

    @Test void lAppelAlimenteLeCourantLesEstimationsEtLesCapacites() throws Exception {
        UUID manche = parcours.demarrer();
        var premier = parcours.inscrire(manche, 1);
        var autre = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.get(1), parcours.soiree);
        var inscription = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(2));
        banc.service.inscrirePassage(manche, parcours.envoi(autre, inscription.envoiId(), "POST", "/passages", inscription), inscription);
        var appel = new CorpsKaraoke.AppelPassage(0L);
        banc.service.appelerPassage(manche, premier.passageId(),
                parcours.envoi(parcours.animateur, UUID.randomUUID(), "POST", "/appel", appel), appel);
        var salle = (VuesKaraoke.BlocKaraokeSalle) banc.bloc.fournir(parcours.soiree, Destination.SALLE);
        assertThat(salle.courant().passageId()).isEqualTo(premier.passageId());
        assertThat(salle.courant().etat()).isEqualTo("APPELE");
        assertThat(salle.prochainsPassages().getFirst().attente().dureeMs()).isEqualTo(210_000L);
        var console = (VuesKaraoke.BlocKaraokeConsole) banc.bloc.fournir(parcours.soiree, Destination.CONSOLE);
        assertThat(console.peutAppeler()).isFalse();
        assertThat(console.peutPasser()).isTrue();
        var manette = (VuesKaraoke.BlocKaraokeManette) banc.bloc.fournir(parcours.soiree, Destination.joueur(parcours.joueur.identifiant()));
        assertThat(manette.peutConfirmerPresence()).isTrue();
        var autreManette = (VuesKaraoke.BlocKaraokeManette) banc.bloc.fournir(parcours.soiree, Destination.joueur(autre.identifiant()));
        assertThat(autreManette.peutConfirmerPresence()).isFalse();
        verifierCapture(Destination.SALLE, "retrouverEtatSalle");
        verifierCapture(Destination.CONSOLE, "retrouverEtatConsole");
        verifierCapture(Destination.joueur(parcours.joueur.identifiant()), "retrouverEtatManette");
    }

    @Test void lesParolesRestentEnSalleEnPauseEtLeFichierResteEnConsole() throws Exception {
        UUID manche = parcours.demarrer();
        var appel = parcours.appeler(manche, parcours.inscrire(manche, 1).passageId());
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), parcours.soiree);
        UUID lecteur = parcours.designer(manche, ecran);
        assertThat(parcours.declarer(manche, appel, ecran, lecteur, 186_750L)).isInstanceOf(Accepte.class);
        assertThat(parcours.confirmer(manche, appel)).isInstanceOf(Accepte.class);

        var enChant = console();
        assertThat(enChant.lecteurDesigne()).isTrue();
        assertThat(enChant.peutCaler()).isTrue();
        assertThat(enChant.peutReprendre()).isFalse();
        assertThat(enChant.peutReprendreEnParolesSeules()).isFalse();
        assertThat(enChant.fichierLocal()).isEqualTo(new FichierAudioLocal("Titre.mp3", 120, 186_750L));

        var pause = new InterventionDeManche(InterventionDeManche.Action.METTRE_EN_PAUSE, appel.etapeId());
        assertThat(banc.service.piloter(manche,
                parcours.envoi(parcours.animateur, UUID.randomUUID(), "POST", "/commandes", pause), pause)).isInstanceOf(Accepte.class);
        var salleEnPause = (VuesKaraoke.BlocKaraokeSalle) banc.bloc.fournir(parcours.soiree, Destination.SALLE);
        assertThat(salleEnPause.lecteurDesigneId()).isEqualTo(lecteur);
        assertThat(salleEnPause.courant().etat()).isEqualTo("EN_PAUSE");
        assertThat(salleEnPause.paroles().lignes()).extracting(LigneParoles::texte).contains("Une ligne");
        var consoleEnPause = console();
        assertThat(consoleEnPause.peutReprendre()).isTrue();
        assertThat(consoleEnPause.peutCaler()).isFalse();
        assertThat(consoleEnPause.peutReprendreEnParolesSeules()).isFalse();
        assertThat(consoleEnPause.fichierLocal()).isEqualTo(enChant.fichierLocal());
        var manette = banc.bloc.fournir(parcours.soiree, Destination.joueur(parcours.joueur.identifiant()));
        assertThat(banc.json.writeValueAsString(salleEnPause)).doesNotContain("Titre.mp3", "fichierLocal");
        assertThat(banc.json.writeValueAsString(manette)).doesNotContain("Titre.mp3", "fichierLocal", "Une ligne");
        assertThat(banc.json.writeValueAsString(consoleEnPause)).doesNotContain("Une ligne");

        var erreur = new CorpsKaraoke.SignalErreurLectureChanson(lecteur, appel.etapeId(), enChant.lecture().lectureId());
        assertThat(banc.service.signalerErreurLectureChanson(manche, appel.passageId(),
                parcours.envoi(ecran, UUID.randomUUID(), "POST", "/erreur-lecture", erreur), erreur)).isInstanceOf(Accepte.class);
        var consoleEnErreur = console();
        assertThat(consoleEnErreur.peutReprendre()).isFalse();
        assertThat(consoleEnErreur.peutCaler()).isFalse();
        assertThat(consoleEnErreur.peutReprendreEnParolesSeules()).isTrue();
        verifierCapture(Destination.SALLE, "retrouverEtatSalle");
        verifierCapture(Destination.CONSOLE, "retrouverEtatConsole");
        verifierCapture(Destination.joueur(parcours.joueur.identifiant()), "retrouverEtatManette");
    }

    private VuesKaraoke.BlocKaraokeConsole console() {
        return (VuesKaraoke.BlocKaraokeConsole) banc.bloc.fournir(parcours.soiree, Destination.CONSOLE);
    }

    void verifierCapture(Destination destination, String operation) throws Exception {
        var evenement = banc.assembleur.assembler(parcours.soiree, destination, "file-attente-modifiee");
        var valeur = new java.util.LinkedHashMap<String, Object>();
        valeur.put("revision", 1); valeur.put("abonnementId", null);
        valeur.put("heureServeur", banc.horloge.maintenant()); valeur.put("vue", evenement.champs());
        ContratApi.verifier("docs/api/openapi.yaml", operation, ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(banc.json.writeValueAsString(valeur)));
    }
}
