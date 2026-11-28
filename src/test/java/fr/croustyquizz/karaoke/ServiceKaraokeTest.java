package fr.croustyquizz.karaoke;

import static fr.croustyquizz.karaoke.ConfigurationTroisJoueurs.joueursDe;
import static fr.croustyquizz.Attentes.PLAFOND_SECONDES;
import static fr.croustyquizz.Attentes.attendre;
import static fr.croustyquizz.Attentes.retenir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;
import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import fr.croustyquizz.tempsreel.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;

/** Service réel, transactions annulables, source et ports factices. */
class ServiceKaraokeTest {
    static final ProprietesKaraoke PROPRIETES = new ProprietesKaraoke(new ProprietesKaraoke.Passage(1, 30, 30, 10), new ProprietesKaraoke.Karaoke(8000, 10000));
    static final ProprietesSourceKaraoke SOURCE = new ProprietesSourceKaraoke(URI.create("https://source.invalid"), "Test", 5000, 2000000, 120);
    final BancKaraoke banc = new BancKaraoke();
    final UUID soiree = UUID.randomUUID();
    final Acteur joueur = new Acteur(Acteur.Type.JOUEUR, joueursDe(soiree).getFirst(), soiree);
    final Acteur animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);

    @Test void inscriptionEtRejeu201NeConsommentQuUnNumero() {
        UUID manche = demarrer();
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(1));
        var envoi = envoi(joueur, corps.envoiId(), "POST", "/passages", corps);
        var resultat = banc.service.inscrirePassage(manche, envoi, corps);
        assertThat(ReponsesDeCommande.statut(resultat)).isEqualTo(201);
        verify(banc.passages).incrementerNumero(soiree, 1);
        assertThat(banc.diffuseur.envois()).hasSize(10);
        banc.registre.retirer(manche);
        var rejeu = banc.service.inscrirePassage(manche, envoi, corps);
        assertThat(ReponsesDeCommande.statut(rejeu)).isEqualTo(201);
        assertThat(banc.json.writeValueAsString(((Accepte) rejeu).accuse())).isEqualTo(banc.json.writeValueAsString(((Accepte) resultat).accuse()));
        verify(banc.passages, times(1)).incrementerNumero(any(), anyLong());
        assertThat(banc.diffuseur.envois()).hasSize(10);
        var doublon = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), corps.chansonId());
        assertThat(banc.service.inscrirePassage(manche, envoi(joueur, doublon.envoiId(), "POST", "/passages", doublon), doublon))
                .isInstanceOfSatisfying(Refuse.class, r -> assertThat(r.code()).isEqualTo("PASSAGE_DEJA_INSCRIT"));
        verify(banc.passages, times(1)).incrementerNumero(any(), anyLong());
    }

    @Test void preteEtTermineeSontDecideesAvantLaSourceEtAvecLesBonsRecus() {
        UUID manche = banc.service.creer(soiree, Map.of());
        doThrow(new SourceParolesIndisponible("Panne")).when(banc.source).charger(anyLong());
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(1));
        var envoi = envoi(joueur, corps.envoiId(), "POST", "/passages", corps);
        assertThatThrownBy(() -> banc.service.inscrirePassage(manche, envoi, corps)).isInstanceOfSatisfying(RefusDeCommande.class,
                e -> assertThat(e.refus().code()).isEqualTo("ETAPE_INACTIVE"));
        assertThat(banc.socle.envois().retrouver(envoi)).isNull();
        banc.service.demarrer(manche, envoi(animateur, UUID.randomUUID(), "POST", "/demarrer", Map.of()));
        piloter(manche, InterventionDeManche.Action.TERMINER);
        assertThat(banc.service.inscrirePassage(manche, envoi, corps)).isInstanceOfSatisfying(Refuse.class,
                r -> assertThat(r.code()).isEqualTo("ETAT_INCOMPATIBLE"));
        assertThat(banc.socle.envois().retrouver(envoi)).isInstanceOf(Refuse.class);
        verify(banc.source, never()).charger(anyLong());
        verify(banc.passages, never()).incrementerNumero(any(), anyLong());
    }

    @Test void fileAbsenteEtIdentifiantIndecodableProduisentUn404DurableSansSource() {
        UUID manche = demarrer();
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), "indécodable");
        var envoi = envoi(joueur, corps.envoiId(), "POST", "/passages", corps);
        assertThat(banc.service.inscrirePassage(manche, envoi, corps)).isInstanceOfSatisfying(Refuse.class,
                r -> assertThat(r.statutHttp()).isEqualTo(404));
        assertThat(banc.socle.envois().retrouver(envoi)).isInstanceOf(Refuse.class);
        banc.passages.files.remove(manche);
        var retrait = new CorpsKaraoke.RetraitPassage(0L);
        var commande = envoi(animateur, UUID.randomUUID(), "POST", "/retrait", retrait);
        assertThat(banc.service.retirerPassage(manche, UUID.randomUUID(), commande, retrait)).isInstanceOf(Refuse.class);
        assertThat(banc.socle.envois().retrouver(commande)).isInstanceOf(Refuse.class);
        verify(banc.source, never()).charger(anyLong());
    }

    @Test void uneAttenteConnueDAutruiEstRefuseeAvantLeRecuSansRestaurerLeDeroulement() {
        UUID manche = demarrer();
        UUID passage = inscrire(manche, 1).passageId();
        var autre = new Acteur(Acteur.Type.JOUEUR, joueursDe(soiree).get(1), soiree);
        var corps = new CorpsKaraoke.DesinscriptionPassage(UUID.randomUUID(), 0L);
        var envoi = envoi(autre, corps.envoiId(), "POST", "/passages/" + passage + "/desinscription", corps);
        banc.registre.retirer(manche);
        assertThatThrownBy(() -> banc.service.desinscrirePassage(manche, passage, envoi, corps))
                .isInstanceOfSatisfying(RefusDeCommande.class, e -> assertThat(e.refus().statutHttp()).isEqualTo(404));
        assertThat(banc.socle.envois().retrouver(envoi)).isNull();
    }

    @Test void activiteEtAccesSontRevalidesApresLaSourceSansRecu() {
        UUID manche = demarrer();
        doAnswer(a -> { banc.ports.inactives.add(manche); return Optional.of(contenu(1)); }).when(banc.source).charger(1);
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(1));
        var envoi = envoi(joueur, corps.envoiId(), "POST", "/passages", corps);
        assertThatThrownBy(() -> banc.service.inscrirePassage(manche, envoi, corps)).isInstanceOfSatisfying(RefusDeCommande.class,
                e -> assertThat(e.refus().code()).isEqualTo("ETAPE_INACTIVE"));
        assertThat(banc.socle.envois().retrouver(envoi)).isNull();
        banc.ports.inactives.clear();
        doAnswer(a -> { banc.ports.absents.add(joueur.identifiant()); return Optional.of(contenu(1)); }).when(banc.source).charger(1);
        assertThatThrownBy(() -> banc.service.inscrirePassage(manche, envoi, corps)).isInstanceOfSatisfying(RefusDeCommande.class,
                e -> assertThat(e.refus().statutHttp()).isEqualTo(404));
        assertThat(banc.socle.envois().retrouver(envoi)).isNull();
        verify(banc.passages, never()).incrementerNumero(any(), anyLong());
    }

    @Test void finPendantLaSourcePrecedeLaPerteDAccesEtLAbsenceDeChanson() {
        UUID manche = demarrer();
        doAnswer(a -> {
            piloter(manche, InterventionDeManche.Action.TERMINER);
            banc.ports.absents.add(joueur.identifiant());
            return Optional.empty();
        }).when(banc.source).charger(1);
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(1));
        var envoi = envoi(joueur, corps.envoiId(), "POST", "/passages", corps);
        assertThat(banc.service.inscrirePassage(manche, envoi, corps)).isInstanceOfSatisfying(Refuse.class,
                r -> assertThat(r.code()).isEqualTo("ETAT_INCOMPATIBLE"));
        assertThat(banc.socle.envois().retrouver(envoi)).isInstanceOf(Refuse.class);
    }

    @Test void rollbackAnnuleFileContenusNumeroEtRecuPuisRestaure() {
        UUID manche = demarrer();
        var avant = banc.service.instantane(soiree, manche);
        int publications = banc.diffuseur.envois().size();
        doAnswer(a -> { banc.echouerCommit = true; return Optional.of(contenu(1)); }).when(banc.source).charger(1);
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(1));
        var envoi = envoi(joueur, corps.envoiId(), "POST", "/passages", corps);
        assertThatThrownBy(() -> banc.service.inscrirePassage(manche, envoi, corps)).isInstanceOf(TransactionSystemException.class);
        assertThat(banc.socle.envois().retrouver(envoi)).isNull();
        assertThat(banc.passages.numeroPropose(soiree)).isEqualTo(1);
        assertThat(banc.service.instantane(soiree, manche)).isEqualTo(avant);
        assertThat(banc.diffuseur.envois()).hasSize(publications);
        doReturn(Optional.of(contenu(1))).when(banc.source).charger(1);
        assertThat(ReponsesDeCommande.statut(banc.service.inscrirePassage(manche, envoi, corps))).isEqualTo(201);
    }

    @Test void unePanneSourceNeGardePasDeRecuEtLaClePeutEtreRetentee() {
        UUID manche = demarrer();
        doThrow(new SourceParolesIndisponible("Panne")).when(banc.source).charger(1);
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(1));
        var envoi = envoi(joueur, corps.envoiId(), "POST", "/passages", corps);
        assertThatThrownBy(() -> banc.service.inscrirePassage(manche, envoi, corps)).isInstanceOfSatisfying(RefusDeCommande.class,
                e -> assertThat(e.refus().code()).isEqualTo("SOURCE_PAROLES_INDISPONIBLE"));
        assertThat(banc.socle.envois().retrouver(envoi)).isNull();
        doReturn(Optional.of(contenu(1))).when(banc.source).charger(1);
        assertThat(banc.service.inscrirePassage(manche, envoi, corps)).isInstanceOf(Accepte.class);
    }

    @Test void lesDeuxInscriptionsConcurrentesDisputentLaDernierePlaceSousVerrou() throws Exception {
        UUID manche = demarrer();
        var entrees = new java.util.concurrent.CountDownLatch(2);
        var sortir = new java.util.concurrent.CountDownLatch(1);
        doAnswer(a -> {
            entrees.countDown(); retenir(sortir); return Optional.of(contenu(a.getArgument(0)));
        }).when(banc.source).charger(anyLong());
        try (var fils = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            try {
                var premier = fils.submit(() -> inscrireResultat(manche, 1, UUID.randomUUID()));
                var second = fils.submit(() -> inscrireResultat(manche, 2, UUID.randomUUID()));
                assertThat(entrees.await(PLAFOND_SECONDES, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                // La source lente n'empêche pas un autre pilotage de prendre le verrou.
                piloter(manche, InterventionDeManche.Action.METTRE_EN_PAUSE);
                sortir.countDown();
                var resultats = List.of(premier.get(PLAFOND_SECONDES, java.util.concurrent.TimeUnit.SECONDS), second.get(PLAFOND_SECONDES, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(resultats.stream().filter(Accepte.class::isInstance)).hasSize(1);
                assertThat(resultats.stream().filter(Refuse.class::isInstance).map(r -> ((Refuse) r).code())).containsExactly("LIMITE_ATTEINTE");
                verify(banc.passages, times(1)).incrementerNumero(soiree, 1);
            } finally { sortir.countDown(); }
        }
    }

    @Test void deuxInscriptionsConcurrentesNAppellentQuUnPassagePuisLeMinuteurPublieLeSuivant() throws Exception {
        banc.pilotage.choisir(soiree, true);
        UUID manche = demarrer();
        var entrees = new CountDownLatch(2);
        var sortir = new CountDownLatch(1);
        doAnswer(appel -> {
            entrees.countDown();
            retenir(sortir);
            return Optional.of(contenu(appel.getArgument(0)));
        }).when(banc.source).charger(anyLong());

        try (var fils = Executors.newFixedThreadPool(2)) {
            try {
                var premier = fils.submit(() -> inscrireResultat(manche, 1, UUID.randomUUID()));
                var second = fils.submit(() -> inscrireResultat(manche, 2, UUID.randomUUID()));
                assertThat(entrees.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
                sortir.countDown();
                assertThat(List.of(premier.get(PLAFOND_SECONDES, TimeUnit.SECONDS), second.get(PLAFOND_SECONDES, TimeUnit.SECONDS)))
                        .allSatisfy(recu -> assertThat(ReponsesDeCommande.statut(recu)).isEqualTo(201));
            } finally { sortir.countDown(); }
        }
        assertThat(banc.diffuseur.typesSalle())
                .containsExactly("manche-demarree", "passage-appele", "file-attente-modifiee");
        var console = (VuesKaraoke.BlocKaraokeConsole) banc.bloc.fournir(soiree, Destination.CONSOLE);
        assertThat(console.courant().etat()).isEqualTo("APPELE");
        assertThat(console.courant().numero()).isEqualTo(1);
        assertThat(console.passagesEnAttente()).singleElement().satisfies(passage -> assertThat(passage.numero()).isEqualTo(2));
        var suivant = console.passagesEnAttente().getFirst();
        var courant = console.courant();
        var presence = new CorpsKaraoke.ConfirmationPresencePassage(UUID.randomUUID(), courant.etapeId(),
                courant.numeroModification());
        assertThat(banc.service.confirmerPresencePassage(manche, courant.passageId(),
                envoi(joueur, presence.envoiId(), "POST", "/presence", presence), presence)).isInstanceOf(Accepte.class);

        int avant = banc.diffuseur.envois().size();
        banc.horloge.avancer(Duration.ofMinutes(3));
        var envois = banc.diffuseur.envois();
        var publications = envois.subList(avant, envois.size());
        assertThat(publications).extracting(DiffuseurFactice.Envoi::cible).containsExactlyInAnyOrder(
                DiffuseurFactice.Cible.SALLE, DiffuseurFactice.Cible.CONSOLE,
                DiffuseurFactice.Cible.JOUEUR, DiffuseurFactice.Cible.JOUEUR, DiffuseurFactice.Cible.JOUEUR);
        assertThat(publications.stream().filter(p -> p.cible() == DiffuseurFactice.Cible.JOUEUR)
                .map(DiffuseurFactice.Envoi::destinataire)).containsExactlyInAnyOrderElementsOf(ConfigurationTroisJoueurs.joueursDe(soiree));
        assertThat(publications).allSatisfy(publication -> {
            assertThat(publication.evenement().type()).isEqualTo("passage-appele");
            var vue = banc.json.valueToTree(((VueComposee) publication.evenement()).champs());
            assertThat(vue.at("/karaoke/courant/passageId").asString()).isEqualTo(suivant.passageId().toString());
            assertThat(vue.at("/karaoke/courant/etat").asString()).isEqualTo("APPELE");
        });
    }

    @Test void deuxPreparationsDeMemeCleNeConsommentQuUnNumeroEtUneAutreEmpreinteEstRefusee() throws Exception {
        UUID manche = demarrer(); UUID id = UUID.randomUUID();
        var entrees = new java.util.concurrent.CountDownLatch(2); var sortir = new java.util.concurrent.CountDownLatch(1);
        doAnswer(a -> { entrees.countDown(); retenir(sortir); return Optional.of(contenu(a.getArgument(0))); }).when(banc.source).charger(anyLong());
        try (var fils = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            try {
                var premier = fils.submit(() -> inscrireResultat(manche, 1, id));
                var second = fils.submit(() -> inscrireResultat(manche, 2, id));
                assertThat(entrees.await(PLAFOND_SECONDES, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); sortir.countDown();
                var resultats = List.of(premier.get(PLAFOND_SECONDES, java.util.concurrent.TimeUnit.SECONDS), second.get(PLAFOND_SECONDES, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(resultats.stream().filter(Accepte.class::isInstance)).hasSize(1);
                assertThat(resultats.stream().filter(Refuse.class::isInstance).map(r -> ((Refuse) r).code())).containsExactly("ENVOI_REUTILISE");
                verify(banc.passages, times(1)).incrementerNumero(soiree, 1);
            } finally { sortir.countDown(); }
        }
    }

    @Test void lesGetNecriventRienEtRevalidentLaPorteeApresLaSource() {
        UUID manche = demarrer();
        int recus = banc.depot.recus.size(), publications = banc.diffuseur.envois().size();
        UUID rechercheId = UUID.randomUUID();
        assertThat(banc.service.rechercherChansons(joueur, soiree, manche, "  Chanson  ", rechercheId).rechercheId()).isEqualTo(rechercheId);
        assertThat(banc.service.consulterChanson(animateur, soiree, manche, IdentifiantsChanson.encoder(1)).motifIndisponibilite()).isEqualTo(MotifIndisponibilite.ANIMATEUR);
        assertThat(banc.service.consulterChanson(joueur, soiree, manche, IdentifiantsChanson.encoder(1)).inscriptionPossible()).isTrue();
        assertThat(banc.depot.recus).hasSize(recus); assertThat(banc.diffuseur.envois()).hasSize(publications);
        doAnswer(a -> { banc.ports.inactives.add(manche); return List.of(); }).when(banc.source).rechercher(anyString());
        assertThatThrownBy(() -> banc.service.rechercherChansons(joueur, soiree, manche, "Chanson", rechercheId))
                .isInstanceOfSatisfying(RefusDeCommande.class, e -> assertThat(e.refus().code()).isEqualTo("ETAPE_INACTIVE"));
    }

    @Test void reglagesFermesEtEntiersStrictsAvecDefautSeulementSiAbsent() {
        UUID manche = banc.service.creer(soiree, Map.of());
        assertThat(banc.json.readTree(banc.depot.charger(manche).getReglages()).path("limitePassagesEnAttente").asLong()).isEqualTo(1);
        for (Object reglages : List.of(List.of(), "texte", Map.of("inconnu", 1), Map.of("limitePassagesEnAttente", 1.0),
                Map.of("limitePassagesEnAttente", "2"), Map.of("limitePassagesEnAttente", 0), Map.of("limitePassagesEnAttente", 21L), Map.of("limitePassagesEnAttente", 9007199254740992L))) {
            assertThatThrownBy(() -> banc.service.creer(soiree, reglages)).isInstanceOfSatisfying(RefusDeCommande.class,
                    e -> assertThat(e.refus().statutHttp()).isEqualTo(400));
        }
        UUID maximum = banc.service.creer(soiree, Map.of("limitePassagesEnAttente", 20));
        assertThat(banc.json.readTree(banc.depot.charger(maximum).getReglages()).path("limitePassagesEnAttente").asLong()).isEqualTo(20);
        var nul = new HashMap<String, Object>(); nul.put("limitePassagesEnAttente", null);
        assertThatThrownBy(() -> banc.service.creer(soiree, nul)).isInstanceOf(RefusDeCommande.class);
    }

    @Test void passerSansCourantNaPasDeRecuEtLeDemarrageVideNeTerminePas() {
        UUID manche = demarrer();
        var corps = new InterventionDeManche(InterventionDeManche.Action.PASSER_ETAPE, null);
        var envoi = envoi(animateur, UUID.randomUUID(), "POST", "/commandes", corps);
        assertThatThrownBy(() -> banc.service.piloter(manche, envoi, corps)).isInstanceOfSatisfying(RefusDeCommande.class,
                e -> assertThat(e.refus().code()).isEqualTo("ETAPE_INACTIVE"));
        assertThat(banc.socle.envois().retrouver(envoi)).isNull();
        assertThat(((InstantaneKaraoke) banc.service.instantane(soiree, manche)).etatManche()).isEqualTo(EtatManche.EN_COURS);
    }

    @Test void deuxPreparationsIdentiquesConcurrentesRejouentLeMeme201SansRepublier() throws Exception {
        UUID manche = demarrer(); UUID id = UUID.randomUUID();
        var entrees = new java.util.concurrent.CountDownLatch(2); var sortir = new java.util.concurrent.CountDownLatch(1);
        doAnswer(a -> { entrees.countDown(); retenir(sortir); return Optional.of(contenu(1)); }).when(banc.source).charger(1);
        try (var fils = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            try {
                var premier = fils.submit(() -> inscrireResultat(manche, 1, id));
                var second = fils.submit(() -> inscrireResultat(manche, 1, id));
                assertThat(entrees.await(PLAFOND_SECONDES, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); sortir.countDown();
                var initial = premier.get(PLAFOND_SECONDES, java.util.concurrent.TimeUnit.SECONDS);
                var repete = second.get(PLAFOND_SECONDES, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(ReponsesDeCommande.statut(initial)).isEqualTo(201);
                assertThat(ReponsesDeCommande.statut(repete)).isEqualTo(201);
                assertThat(banc.json.writeValueAsString(((Accepte) initial).accuse())).isEqualTo(banc.json.writeValueAsString(((Accepte) repete).accuse()));
                verify(banc.passages, times(1)).incrementerNumero(soiree, 1);
                assertThat(banc.diffuseur.envois()).hasSize(10);
            } finally { sortir.countDown(); }
        }
    }

    @Test void sansEffetEtRefusDeContenuNePublientRienEtNeConsommentPasDeNumero() {
        UUID manche = demarrer();
        int publications = banc.diffuseur.envois().size();
        var limite = new CorpsKaraoke.LimitePassages(1L);
        assertThat(banc.service.reglerLimitePassages(manche, envoi(animateur, UUID.randomUUID(), "PUT", "/limite", limite), limite)).isInstanceOf(Accepte.class);
        doReturn(Optional.of(new ContenuKaraoke(contenu(1).chanson(), List.of()))).when(banc.source).charger(1);
        var corps = new CorpsKaraoke.InscriptionPassage(UUID.randomUUID(), IdentifiantsChanson.encoder(1));
        var envoi = envoi(joueur, corps.envoiId(), "POST", "/passages", corps);
        assertThat(banc.service.inscrirePassage(manche, envoi, corps)).isInstanceOfSatisfying(Refuse.class,
                r -> assertThat(r.code()).isEqualTo("CHANSON_INDISPONIBLE"));
        assertThat(banc.socle.envois().retrouver(envoi)).isInstanceOf(Refuse.class);
        assertThat(banc.service.consulterChanson(joueur, soiree, manche, corps.chansonId()).motifIndisponibilite())
                .isEqualTo(MotifIndisponibilite.PAROLES_NON_SYNCHRONISEES);
        verify(banc.passages, never()).incrementerNumero(any(), anyLong());
        assertThat(banc.diffuseur.envois()).hasSize(publications);
    }

    @Test void unRappelSansClotureNeDemandeNiCauseNiPublication() {
        var fabrique = spy(new FabriqueKaraoke(Duration.ofSeconds(30)));
        var appels = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            ContexteDeManche contexte = invocation.getArgument(0);
            contexte.horloge().programmer(Duration.ofSeconds(1), appels::incrementAndGet);
            return invocation.callRealMethod();
        }).when(fabrique).creer(any(), any());
        var bancAvecRappel = new BancKaraoke(fabrique);
        UUID manche = bancAvecRappel.service.creer(soiree, Map.of());
        assertThat(bancAvecRappel.service.demarrer(manche, envoi(animateur, UUID.randomUUID(), "POST", "/demarrer", Map.of())))
                .isInstanceOf(Accepte.class);
        int publications = bancAvecRappel.diffuseur.envois().size();
        var avant = bancAvecRappel.service.instantane(soiree, manche);
        clearInvocations(bancAvecRappel.passages);
        bancAvecRappel.horloge.avancer(Duration.ofSeconds(1));
        assertThat(appels).hasValue(1);
        verify(fabrique, never()).typeEvenementMinuteur(any());
        verify(bancAvecRappel.passages, never()).enregistrer(any(), any());
        assertThat(bancAvecRappel.diffuseur.envois()).hasSize(publications);
        assertThat(bancAvecRappel.service.instantane(soiree, manche)).isEqualTo(avant);
    }

    @Test void laConfirmationPeutDevancerLaPreparationExterneEtRendreLaDeclarationPerimee() throws Exception {
        UUID manche = demarrer();
        var appel = appeler(manche, inscrire(manche, 1).passageId());
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        UUID lecteur = designer(manche, ecran);
        var sourceConsultee = new CountDownLatch(1);
        var libererSource = new CountDownLatch(1);
        when(banc.source.rechercher(anyString())).thenAnswer(a -> {
            sourceConsultee.countDown();
            retenir(libererSource);
            return List.of(contenu(1).chanson());
        });
        try (var fils = Executors.newFixedThreadPool(2)) {
            try {
                var declaration = fils.submit(() -> declarer(manche, appel, ecran, lecteur, 180_000L));
                attendre(sourceConsultee);
                assertThat(fils.submit(() -> confirmer(manche, appel)).get(PLAFOND_SECONDES, TimeUnit.SECONDS)).isInstanceOf(Accepte.class);
                libererSource.countDown();
                assertThat(declaration.get(PLAFOND_SECONDES, TimeUnit.SECONDS)).isInstanceOfSatisfying(Refuse.class,
                        refus -> assertThat(refus.code()).isEqualTo("COMMANDE_PERIMEE"));
            } finally { libererSource.countDown(); }
        }
        assertThat(lectureDe(manche, appel).motifParolesSeules()).isEqualTo(MotifParolesSeules.ECRAN_NON_PRET);
        verifyNoInteractions(banc.calages);
    }

    @Test void uneDesignationPendantLaPreparationNeValidePasUneDeclarationSansSelectionDesParoles() throws Exception {
        UUID manche = demarrer();
        var appel = appeler(manche, inscrire(manche, 1).passageId());
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        UUID lecteur = UUID.randomUUID();
        var corps = new CorpsKaraoke.DeclarationLectureChanson(lecteur, appel.etapeId(), 1L, "FICHIER_LOCAL",
                new CorpsKaraoke.Fichier("Titre.mp3", 120L, 180_000L), null);
        var envoi = envoi(ecran, UUID.randomUUID(), "POST", "/lecture", corps);
        var service = spy(banc.service);
        var capture = new CountDownLatch(1);
        var reprendre = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object instantane = invocation.callRealMethod();
            capture.countDown();
            retenir(reprendre);
            return instantane;
        }).when(service).instantane(soiree, manche);
        clearInvocations(banc.source);
        Refuse refus;
        try (var fils = Executors.newFixedThreadPool(2)) {
            try {
                var declaration = fils.submit(() -> service.declarerLectureChanson(manche, appel.passageId(), envoi, corps));
                attendre(capture);
                var designation = new CorpsKaraoke.DesignationLecteur(lecteur);
                assertThat(fils.submit(() -> banc.service.designerLecteurChanson(manche,
                        envoi(ecran, UUID.randomUUID(), "PUT", "/lecteur", designation), designation))
                        .get(PLAFOND_SECONDES, TimeUnit.SECONDS)).isInstanceOf(Accepte.class);
                reprendre.countDown();
                var resultat = declaration.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
                assertThat(resultat).isInstanceOf(Refuse.class);
                refus = (Refuse) resultat;
            } finally { reprendre.countDown(); }
        }
        assertThat(refus.statutHttp()).isEqualTo(409);
        assertThat(refus.code()).isEqualTo("COMMANDE_PERIMEE");
        assertThat(banc.service.declarerLectureChanson(manche, appel.passageId(), envoi, corps)).isEqualTo(refus);
        assertThat(lectureDe(manche, appel)).isNull();
        verifyNoInteractions(banc.source, banc.calages);
    }

    @Test void leBancRestaureLeCycleLaLectureEtLeLecteurApresEviction() {
        UUID manche = demarrer();
        var appel = appeler(manche, inscrire(manche, 1).passageId());
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        UUID lecteur = designer(manche, ecran);
        declarer(manche, appel, ecran, lecteur, 180_000L);
        var avant = banc.service.instantane(soiree, manche);
        banc.registre.retirer(manche);
        assertThat(banc.service.instantane(soiree, manche)).isEqualTo(avant);
    }

    @Test void uneMesureAuDecalageDejaMemoriseSoldeLaDemandeSansRecrireLaMemoire() {
        UUID manche = demarrer();
        var appel = appeler(manche, inscrire(manche, 1).passageId());
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        UUID lecteur = designer(manche, ecran);
        when(banc.calages.lire(anyString(), anyLong(), anyString(), anyLong())).thenReturn(500L);
        declarer(manche, appel, ecran, lecteur, 180_000L);
        confirmer(manche, appel);
        UUID lecture = lectureDe(manche, appel).lectureId();
        var geste = new CorpsKaraoke.MarqueDebutParoles(appel.etapeId(), lecture);
        var marque = (Accepte) banc.service.marquerDebutParoles(manche, appel.passageId(),
                envoi(animateur, UUID.randomUUID(), "POST", "/calage/debut", geste), geste);
        UUID demande = ((AccuseCalage) marque.accuse()).demandeId();
        var position = new CorpsKaraoke.RelevePositionLecture(lecteur, appel.etapeId(), lecture, demande, 500L);
        assertThat(banc.service.releverPositionLecture(manche, appel.passageId(),
                envoi(ecran, UUID.randomUUID(), "POST", "/calage/position", position), position))
                .isInstanceOfSatisfying(Accepte.class, accepte -> assertThat(accepte.typeEvenement()).isEqualTo("calage-chanson-modifie"));
        assertThat(lectureDe(manche, appel).demandeId()).isNull();
        assertThat(lectureDe(manche, appel).numeroModificationCalage()).isZero();
        verify(banc.calages, never()).memoriser(anyString(), anyLong(), any(), anyLong(), any());
    }

    @Test void deuxConfirmationsConcurrentesNePublientQuUnDepartEtLeursRecusSeRejouent() throws Exception {
        UUID manche = demarrer();
        var appel = appeler(manche, inscrire(manche, 1).passageId());
        var premiere = new CorpsKaraoke.ConfirmationPresencePassage(UUID.randomUUID(), appel.etapeId(), 1L);
        var seconde = new CorpsKaraoke.ConfirmationPresencePassage(UUID.randomUUID(), appel.etapeId(), 1L);
        var envoiInitial = envoi(joueur, premiere.envoiId(), "POST", "/presence", premiere);
        var envoiConcurrent = envoi(joueur, seconde.envoiId(), "POST", "/presence", seconde);
        var depart = new CountDownLatch(1);
        try (var fils = Executors.newFixedThreadPool(2)) {
            var premier = fils.submit(() -> {
                attendre(depart);
                return banc.service.confirmerPresencePassage(manche, appel.passageId(), envoiInitial, premiere);
            });
            var second = fils.submit(() -> {
                attendre(depart);
                return banc.service.confirmerPresencePassage(manche, appel.passageId(), envoiConcurrent, seconde);
            });
            depart.countDown();
            ResultatDeCommande resultatInitial = premier.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
            ResultatDeCommande resultatConcurrent = second.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
            assertThat(List.of(resultatInitial, resultatConcurrent))
                    .extracting(ReponsesDeCommande::statut).containsExactlyInAnyOrder(200, 409);
            assertThat(banc.json.writeValueAsString(ReponsesDeCommande.corps(banc.service.confirmerPresencePassage(
                    manche, appel.passageId(), envoiInitial, premiere), envoiInitial.route())))
                    .isEqualTo(banc.json.writeValueAsString(ReponsesDeCommande.corps(resultatInitial, envoiInitial.route())));
            assertThat(banc.json.writeValueAsString(ReponsesDeCommande.corps(banc.service.confirmerPresencePassage(
                    manche, appel.passageId(), envoiConcurrent, seconde), envoiConcurrent.route())))
                    .isEqualTo(banc.json.writeValueAsString(ReponsesDeCommande.corps(resultatConcurrent, envoiConcurrent.route())));
        }
        assertThat(banc.horloge.minuteursEnAttente()).isEqualTo(1);
        assertThat(banc.diffuseur.envois().stream().filter(e -> e.evenement().type().equals("passage-demarre"))).hasSize(5);
    }

    @Test void leRepliSansDureeUtiliseLaMemoireDeLInscriptionMemeApresUneVersionAdaptee() {
        UUID manche = demarrer();
        var appel = appeler(manche, inscrire(manche, 1).passageId());
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        UUID lecteur = designer(manche, ecran);
        String chanson = contenu(1).chanson().chansonId();
        var adaptee = new Chanson(IdentifiantsChanson.encoder(2), 2, "Chanson 1", "Artiste", null, 200_000L, EtatParoles.SYNCHRONISEES);
        when(banc.source.rechercher(anyString())).thenReturn(List.of(adaptee));
        when(banc.source.charger(2L)).thenReturn(Optional.of(new ContenuKaraoke(adaptee, contenu(1).lignes())));
        when(banc.calages.lire(chanson, 2L, "Titre.mp3", 120L)).thenReturn(500L);
        assertThat(declarer(manche, appel, ecran, lecteur, 200_000L)).isInstanceOf(Accepte.class);
        confirmer(manche, appel);
        var lecture = lectureDe(manche, appel);
        assertThat(lecture.decalageMs()).isEqualTo(500);
        var correction = new CorpsKaraoke.CorrectionCalage(appel.etapeId(), lecture.lectureId(), 0L, "RETARDER");
        assertThat(banc.service.corrigerCalage(manche, appel.passageId(),
                envoi(animateur, UUID.randomUUID(), "POST", "/correction", correction), correction)).isInstanceOf(Accepte.class);
        verify(banc.calages).memoriser(eq(chanson), eq(2L), eq(lecture.fichier()), eq(600L), any());

        var passer = new InterventionDeManche(InterventionDeManche.Action.PASSER_ETAPE, appel.etapeId());
        assertThat(banc.service.piloter(manche, envoi(animateur, UUID.randomUUID(), "POST", "/commandes", passer), passer))
                .isInstanceOf(Accepte.class);
        var second = appeler(manche, inscrire(manche, 1).passageId());
        clearInvocations(banc.source, banc.calages);
        for (Long duree : Arrays.asList(0L, null)) {
            assertThat(declarer(manche, second, ecran, lecteur, duree)).isInstanceOf(Accepte.class);
            var repli = lectureDe(manche, second);
            assertThat(repli.origineParoles()).isEqualTo(OrigineParoles.REPLI_INSCRIPTION);
            assertThat(repli.decalageMs()).isZero();
        }
        verifyNoInteractions(banc.source);
        verify(banc.calages, times(2)).lire(chanson, 1L, "Titre.mp3", 120L);
        verifyNoMoreInteractions(banc.calages);
    }

    @Test void lesVotesConcurrentsSontDurablesEtUnRejeuNeRepubliePas() throws Exception {
        UUID manche = demarrer(), passage = ouvrirVote(manche);
        var premier = votant(1);
        var second = votant(2);
        var note = new CorpsKaraoke.VotePassage(UUID.randomUUID(), 3L, 4L);
        var attente = new CountDownLatch(1);
        int avant = banc.diffuseur.envois().size();
        try (var fils = Executors.newFixedThreadPool(2)) {
            var uneNote = fils.submit(() -> { attendre(attente); return voter(manche, passage, premier, note); });
            var autreNote = fils.submit(() -> { attendre(attente); return voter(manche, passage, second,
                    new CorpsKaraoke.VotePassage(UUID.randomUUID(), 3L, 5L)); });
            attente.countDown();
            assertThat(ReponsesDeCommande.statut(uneNote.get(PLAFOND_SECONDES, TimeUnit.SECONDS))).isEqualTo(200);
            assertThat(ReponsesDeCommande.statut(autreNote.get(PLAFOND_SECONDES, TimeUnit.SECONDS))).isEqualTo(200);
        }
        assertThat(banc.diffuseur.envois()).hasSize(avant + 6);
        banc.registre.retirer(manche);
        var console = (VuesKaraoke.BlocKaraokeConsole) banc.bloc.fournir(soiree, Destination.CONSOLE);
        assertThat(console.votes().getFirst().nombreVotes()).isEqualTo(2L);
        assertThat(voter(manche, passage, premier, note)).isInstanceOfSatisfying(Accepte.class,
                recu -> assertThat(banc.json.valueToTree(recu.accuse()).path("note").asInt()).isEqualTo(4));
        assertThat(voter(manche, passage, premier, new CorpsKaraoke.VotePassage(note.envoiId(), 3L, 1L)))
                .isInstanceOfSatisfying(Refuse.class, refus -> assertThat(refus.code()).isEqualTo("ENVOI_REUTILISE"));
        assertThat(voter(manche, passage, premier, new CorpsKaraoke.VotePassage(UUID.randomUUID(), 3L, 1L)))
                .isInstanceOfSatisfying(Refuse.class, refus -> assertThat(refus.code()).isEqualTo("VOTE_DEJA_ENREGISTRE"));
        assertThat(banc.diffuseur.envois()).hasSize(avant + 6);
    }

    @Test void controleLeRoleEtLaPresencePuisAnnuleUnVoteSiLeCommitEchoue() {
        UUID manche = demarrer(), passage = ouvrirVote(manche);
        var votant = votant(1);
        var corps = new CorpsKaraoke.VotePassage(UUID.randomUUID(), 3L, 4L);
        assertThatThrownBy(() -> voter(manche, passage, animateur, corps)).isInstanceOfSatisfying(
                RefusDeCommande.class, refus -> assertThat(refus.refus().statutHttp()).isEqualTo(403));
        banc.ports.absents.add(votant.identifiant());
        assertThatThrownBy(() -> voter(manche, passage, votant, corps)).isInstanceOfSatisfying(
                RefusDeCommande.class, refus -> assertThat(refus.refus().statutHttp()).isEqualTo(404));
        banc.ports.absents.clear();
        int publications = banc.diffuseur.envois().size();
        var avant = banc.service.instantane(soiree, manche);
        doAnswer(invocation -> {
            banc.echouerCommit = true;
            return invocation.callRealMethod();
        }).when(banc.passages).enregistrer(eq(manche), any());
        assertThatThrownBy(() -> voter(manche, passage, votant, corps)).isInstanceOf(TransactionSystemException.class);
        doCallRealMethod().when(banc.passages).enregistrer(eq(manche), any());
        assertThat(banc.diffuseur.envois()).hasSize(publications);
        assertThat(banc.service.instantane(soiree, manche)).isEqualTo(avant);
        assertThat(voter(manche, passage, votant, corps)).isInstanceOf(Accepte.class);
    }

    UUID ouvrirVote(UUID manche) {
        UUID passage = inscrire(manche, 1).passageId();
        assertThat(confirmer(manche, appeler(manche, passage))).isInstanceOf(Accepte.class);
        banc.horloge.avancer(Duration.ofSeconds(180));
        return passage;
    }

    Acteur votant(int index) {
        return new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.joueursDe(soiree).get(index), soiree);
    }

    ResultatDeCommande voter(UUID manche, UUID passage, Acteur votant, CorpsKaraoke.VotePassage corps) {
        var envoi = envoi(votant, corps.envoiId(), "POST", "/passages/" + passage + "/vote", corps);
        return banc.service.voterPassage(manche, passage, envoi, corps);
    }

    AccuseCyclePassage appeler(UUID manche, UUID passage) {
        var corps = new CorpsKaraoke.AppelPassage(0L);
        return (AccuseCyclePassage) ((Accepte) banc.service.appelerPassage(manche, passage,
                envoi(animateur, UUID.randomUUID(), "POST", "/appel", corps), corps)).accuse();
    }

    UUID designer(UUID manche, Acteur ecran) {
        var corps = new CorpsKaraoke.DesignationLecteur(UUID.randomUUID());
        assertThat(banc.service.designerLecteurChanson(manche, envoi(ecran, UUID.randomUUID(), "PUT", "/lecteur", corps), corps))
                .isInstanceOf(Accepte.class);
        return corps.lecteurId();
    }

    ResultatDeCommande declarer(UUID manche, AccuseCyclePassage appel, Acteur ecran, UUID lecteur, Long dureeMs) {
        var corps = new CorpsKaraoke.DeclarationLectureChanson(lecteur, appel.etapeId(), 1L, "FICHIER_LOCAL",
                new CorpsKaraoke.Fichier("Titre.mp3", 120L, dureeMs), null);
        return banc.service.declarerLectureChanson(manche, appel.passageId(),
                envoi(ecran, UUID.randomUUID(), "POST", "/lecture", corps), corps);
    }

    ResultatDeCommande confirmer(UUID manche, AccuseCyclePassage appel) {
        var corps = new CorpsKaraoke.ConfirmationPresencePassage(UUID.randomUUID(), appel.etapeId(), 1L);
        return banc.service.confirmerPresencePassage(manche, appel.passageId(),
                envoi(joueur, corps.envoiId(), "POST", "/presence", corps), corps);
    }

    private LecturePassage lectureDe(UUID manche, AccuseCyclePassage appel) {
        return ((InstantaneKaraoke) banc.service.instantane(soiree, manche)).lectures().get(appel.passageId());
    }

    void piloter(UUID manche, InterventionDeManche.Action action) {
        var corps = new InterventionDeManche(action, null);
        assertThat(banc.service.piloter(manche, envoi(animateur, UUID.randomUUID(), "POST", "/commandes", corps), corps)).isInstanceOf(Accepte.class);
    }
    ResultatDeCommande inscrireResultat(UUID manche, long chanson, UUID id) {
        var corps = new CorpsKaraoke.InscriptionPassage(id, IdentifiantsChanson.encoder(chanson));
        return banc.service.inscrirePassage(manche, envoi(joueur, id, "POST", "/passages", corps), corps);
    }
    AccusePassage inscrire(UUID manche, long chanson) { return (AccusePassage) ((Accepte) inscrireResultat(manche, chanson, UUID.randomUUID())).accuse(); }
    UUID demarrer() {
        UUID manche = banc.service.creer(soiree, Map.of());
        assertThat(banc.service.demarrer(manche, envoi(animateur, UUID.randomUUID(), "POST", "/demarrer", Map.of()))).isInstanceOf(Accepte.class);
        return manche;
    }
    EnvoiDeCommande envoi(Acteur acteur, UUID id, String methode, String route, Object corps) {
        return new EnvoiDeCommande(acteur, route, id, new CorpsKaraoke.EmpreinteHttp(methode, corps));
    }
    static ContenuKaraoke contenu(long id) {
        return new ContenuKaraoke(new Chanson(IdentifiantsChanson.encoder(id), id, "Chanson " + id, "Artiste", null,
                180000L, EtatParoles.SYNCHRONISEES), List.of(new LigneParoles(0, "Une ligne"), new LigneParoles(0, "Au même instant")));
    }

    static class DepotDeManchesMemoire implements DepotDeManches {
        Map<UUID, ManchePersistante> manches = new HashMap<>();
        Map<UUID, EnvoiDeManche> recus = new HashMap<>();
        public ManchePersistante charger(UUID id) { return manches.get(id); }
        public void enregistrer(ManchePersistante manche) { manches.put(manche.getMancheId(), manche); }
        public void retirer(ManchePersistante manche) { manches.remove(manche.getMancheId()); }
        public List<ManchePersistante> deLaSoiree(UUID id) { return manches.values().stream().filter(m -> m.getSoireeId().equals(id)).toList(); }
        public EnvoiDeManche retrouverEnvoi(UUID id) { return recus.get(id); }
        public void enregistrerEnvoi(EnvoiDeManche recu) { recus.put(recu.getId(), recu); }
    }
    static class PersistancePassageMemoire extends PersistancePassageJpa {
        Map<UUID, EtatFileAttente> files = new HashMap<>();
        Map<UUID, Long> sequences = new HashMap<>();
        PersistancePassageMemoire() { super(null); }
        @Override public EtatFileAttente charger(UUID id) { return files.get(id); }
        @Override public void enregistrer(UUID id, EtatFileAttente etat) { files.put(id, etat); }
        @Override public long numeroPropose(UUID id) { return sequences.getOrDefault(id, 1L); }
        @Override public void incrementerNumero(UUID id, long numero) {
            assertThat(numeroPropose(id)).isEqualTo(numero); sequences.put(id, numero + 1);
        }
    }
    static class BancKaraoke extends AbstractPlatformTransactionManager {
        private static final long serialVersionUID = 1L;
        final JsonMapper json = JsonMapper.builder().build();
        final JsonMapper copieEntites = JsonMapper.builder().constructorDetector(tools.jackson.databind.cfg.ConstructorDetector.DEFAULT.withRequireAnnotation(true)).changeDefaultVisibility(v ->
                v.withFieldVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)).build();
        final DepotDeManchesMemoire depot = new DepotDeManchesMemoire();
        final PersistancePassageMemoire passages = spy(new PersistancePassageMemoire());
        Map<UUID, EtatKaraokePersiste> etats = new HashMap<>();
        final PortsDePassageFactices ports = new PortsDePassageFactices(ConfigurationTroisJoueurs::joueursDe);
        final ParticipantsDePassage participantsDesVues = mock(ParticipantsDePassage.class, delegatesTo(ports));
        final HorlogeFactice horloge = new HorlogeFactice();
        final PilotageDeSoireeDemonstration pilotage = new PilotageDeSoireeDemonstration();
        final DiffuseurFactice diffuseur = new DiffuseurFactice();
        final RegistreDesDeroulements registre = new RegistreDesDeroulements();
        final SourceDeParoles source = mock(SourceDeParoles.class);
        final MemoireDeCalageJpa calages = mock(MemoireDeCalageJpa.class);
        final VerrousSoiree verrous = new VerrousSoiree();
        final SocleDeManche socle;
        final ServiceKaraoke service;
        final FournisseurDeBlocKaraoke bloc;
        final AssembleurDeVues assembleur;
        boolean echouerCommit;
        final ThreadLocal<Sauvegarde> sauvegarde = new ThreadLocal<>();
        BancKaraoke() {
            this(new FabriqueKaraoke(Duration.ofSeconds(30)));
        }
        BancKaraoke(FabriqueKaraoke fabrique) {
            setRollbackOnCommitFailure(true);
            when(source.charger(anyLong())).thenAnswer(a -> Optional.of(contenu(a.getArgument(0))));
            when(source.rechercher(anyString())).thenReturn(List.of(contenu(1).chanson()));
            var commun = new ConfigurationTroisJoueurs.Commun(depot);
            var beans = new StaticListableBeanFactory();
            bloc = new FournisseurDeBlocKaraoke(depot, beans.getBeanProvider(ServiceKaraoke.class), participantsDesVues, PROPRIETES, SOURCE);
            assembleur = new AssembleurDeVues(commun, List.of(bloc), verrous, json);
            var vues = new PublicationDeVues(assembleur, commun, new PublicationApresCommit(), diffuseur, evenement -> { });
            socle = new SocleDeManche(verrous, this, depot, registre, new RegistreDesEnvois(depot, json), vues, horloge, json,
                    pilotage, java.util.List.of());
            var persistance = new PersistanceKaraokeJpa(null, json, passages) {
                @Override public EtatKaraokePersiste charger(UUID id) { return etats.get(id); }
                @Override public void enregistrer(UUID id, Object valeur) {
                    var etat = (InstantaneKaraoke) valeur;
                    passages.enregistrer(id, etat.file());
                    etats.put(id, new EtatKaraokePersiste(etat.file(), etat.contenus(), etat.cycle(), etat.lectures(), etat.lecteurDesigneId(), etat.voteDuPublic()));
                }
            };
            service = new ServiceKaraoke(fabrique, persistance, socle, ports, ports, passages, source, PROPRIETES, SOURCE,
                    new ChoixDeVersionParoles(source, SOURCE), calages);
            beans.addBean("service", service);
        }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            // Les records de domaine sont profondément immuables ; les entités mutables sont recréées.
            Map<UUID, ManchePersistante> manches = new HashMap<>();
            depot.manches.forEach((id, m) -> {
                var copie = new ManchePersistante(id, m.getSoireeId(), m.getMode(), m.getReglages(), horloge.maintenant());
                copie.changerEtat(m.getEtat(), m.getModifieLe()); manches.put(id, copie);
            });
            Map<UUID, EnvoiDeManche> recus = new HashMap<>();
            depot.recus.forEach((id, recu) -> recus.put(id, copieEntites.readValue(copieEntites.writeValueAsString(recu), EnvoiDeManche.class)));
            sauvegarde.set(new Sauvegarde(manches, recus, new HashMap<>(passages.files),
                    new HashMap<>(passages.sequences), new HashMap<>(etats)));
        }
        @Override protected void doCommit(DefaultTransactionStatus statut) {
            if (echouerCommit) { echouerCommit = false; throw new TransactionSystemException("Commit refusé"); }
            sauvegarde.remove();
        }
        @Override protected void doRollback(DefaultTransactionStatus statut) {
            var avant = sauvegarde.get();
            depot.manches = avant.manches(); depot.recus = avant.recus(); etats = avant.etats();
            passages.files = avant.files(); passages.sequences = avant.sequences(); sauvegarde.remove();
        }
        record Sauvegarde(Map<UUID, ManchePersistante> manches, Map<UUID, EnvoiDeManche> recus,
                Map<UUID, EtatFileAttente> files, Map<UUID, Long> sequences, Map<UUID, EtatKaraokePersiste> etats) { }
    }
}
