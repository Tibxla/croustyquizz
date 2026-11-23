package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.*;
import fr.croustyquizz.ContratApi;
import fr.croustyquizz.manche.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

/** Projections par destination avec vrai service et assembleur, sans base ni réseau. */
class FournisseurDeBlocKaraokeTest {
    final ServiceKaraokeTest parcours = new ServiceKaraokeTest();
    final ServiceKaraokeTest.BancKaraoke banc = parcours.banc;

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

    void verifierCapture(Destination destination, String operation) throws Exception {
        var evenement = banc.assembleur.assembler(parcours.soiree, destination, "file-attente-modifiee");
        var valeur = new java.util.LinkedHashMap<String, Object>();
        valeur.put("revision", 1); valeur.put("abonnementId", null);
        valeur.put("heureServeur", banc.horloge.maintenant()); valeur.put("vue", evenement.champs());
        ContratApi.verifier("docs/api/openapi.yaml", operation, ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(banc.json.writeValueAsString(valeur)));
    }
}
