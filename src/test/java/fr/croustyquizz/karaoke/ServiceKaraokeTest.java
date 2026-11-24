package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import fr.croustyquizz.tempsreel.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;

/** Service réel, transactions annulables, source et ports factices. */
class ServiceKaraokeTest {
    static final ProprietesKaraoke PROPRIETES = new ProprietesKaraoke(new ProprietesKaraoke.Passage(1, 30), new ProprietesKaraoke.Karaoke(8000, 10000));
    static final ProprietesSourceKaraoke SOURCE = new ProprietesSourceKaraoke(URI.create("https://source.invalid"), "Test", 5000, 2000000, 120);
    final BancKaraoke banc = new BancKaraoke();
    final UUID soiree = UUID.randomUUID();
    final Acteur joueur = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.getFirst(), soiree);
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
        var autre = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.get(1), soiree);
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
            entrees.countDown(); attendre(sortir); return Optional.of(contenu(a.getArgument(0)));
        }).when(banc.source).charger(anyLong());
        try (var fils = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            try {
                var premier = fils.submit(() -> inscrireResultat(manche, 1, UUID.randomUUID()));
                var second = fils.submit(() -> inscrireResultat(manche, 2, UUID.randomUUID()));
                assertThat(entrees.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                // La source lente n'empêche pas un autre pilotage de prendre le verrou.
                piloter(manche, InterventionDeManche.Action.METTRE_EN_PAUSE);
                sortir.countDown();
                var resultats = List.of(premier.get(3, java.util.concurrent.TimeUnit.SECONDS), second.get(3, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(resultats.stream().filter(Accepte.class::isInstance)).hasSize(1);
                assertThat(resultats.stream().filter(Refuse.class::isInstance).map(r -> ((Refuse) r).code())).containsExactly("LIMITE_ATTEINTE");
                verify(banc.passages, times(1)).incrementerNumero(soiree, 1);
            } finally { sortir.countDown(); }
        }
    }

    @Test void deuxPreparationsDeMemeCleNeConsommentQuUnNumeroEtUneAutreEmpreinteEstRefusee() throws Exception {
        UUID manche = demarrer(); UUID id = UUID.randomUUID();
        var entrees = new java.util.concurrent.CountDownLatch(2); var sortir = new java.util.concurrent.CountDownLatch(1);
        doAnswer(a -> { entrees.countDown(); attendre(sortir); return Optional.of(contenu(a.getArgument(0))); }).when(banc.source).charger(anyLong());
        try (var fils = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            try {
                var premier = fils.submit(() -> inscrireResultat(manche, 1, id));
                var second = fils.submit(() -> inscrireResultat(manche, 2, id));
                assertThat(entrees.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); sortir.countDown();
                var resultats = List.of(premier.get(3, java.util.concurrent.TimeUnit.SECONDS), second.get(3, java.util.concurrent.TimeUnit.SECONDS));
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
        doAnswer(a -> { entrees.countDown(); attendre(sortir); return Optional.of(contenu(1)); }).when(banc.source).charger(1);
        try (var fils = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            try {
                var premier = fils.submit(() -> inscrireResultat(manche, 1, id));
                var second = fils.submit(() -> inscrireResultat(manche, 1, id));
                assertThat(entrees.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); sortir.countDown();
                var initial = premier.get(3, java.util.concurrent.TimeUnit.SECONDS);
                var repete = second.get(3, java.util.concurrent.TimeUnit.SECONDS);
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

    void piloter(UUID manche, InterventionDeManche.Action action) {
        var corps = new InterventionDeManche(action, null);
        assertThat(banc.service.piloter(manche, envoi(animateur, UUID.randomUUID(), "POST", "/commandes", corps), corps)).isInstanceOf(Accepte.class);
    }
    ResultatDeCommande inscrireResultat(UUID manche, long chanson, UUID id) {
        var corps = new CorpsKaraoke.InscriptionPassage(id, IdentifiantsChanson.encoder(chanson));
        return banc.service.inscrirePassage(manche, envoi(joueur, id, "POST", "/passages", corps), corps);
    }
    AccusePassage inscrire(UUID manche, long chanson) { return (AccusePassage) ((Accepte) inscrireResultat(manche, chanson, UUID.randomUUID())).accuse(); }
    static void attendre(java.util.concurrent.CountDownLatch signal) {
        try { assertThat(signal.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException erreur) { Thread.currentThread().interrupt(); throw new IllegalStateException(erreur); }
    }

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
        Map<UUID, Map<UUID, ContenuKaraoke>> contenus = new HashMap<>();
        final PortsDePassageFactices ports = new PortsDePassageFactices();
        final HorlogeFactice horloge = new HorlogeFactice();
        final DiffuseurFactice diffuseur = new DiffuseurFactice();
        final RegistreDesDeroulements registre = new RegistreDesDeroulements();
        final SourceDeParoles source = mock(SourceDeParoles.class);
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
            bloc = new FournisseurDeBlocKaraoke(depot, beans.getBeanProvider(ServiceKaraoke.class), ports, horloge, PROPRIETES, SOURCE);
            assembleur = new AssembleurDeVues(commun, List.of(bloc), verrous, json);
            var vues = new PublicationDeVues(assembleur, commun, new PublicationApresCommit(), diffuseur);
            socle = new SocleDeManche(verrous, this, depot, registre, new RegistreDesEnvois(depot, json), vues, horloge, json,
                    soireeId -> false);
            PersistanceDuMode<EtatKaraokePersiste> persistance = new PersistanceDuMode<>() {
                public EtatKaraokePersiste charger(UUID id) { return new EtatKaraokePersiste(passages.charger(id), contenus.get(id)); }
                public void enregistrer(UUID id, Object valeur) {
                    var etat = (InstantaneKaraoke) valeur; passages.enregistrer(id, etat.file()); contenus.put(id, etat.contenus());
                }
            };
            service = new ServiceKaraoke(fabrique, persistance, socle, ports, ports, passages, source, PROPRIETES, SOURCE);
            beans.addBean("service", service);
        }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            // Les records de domaine sont profondément immuables ; les entités mutables sont recréées.
            Map<UUID, ManchePersistante> manches = new HashMap<>();
            depot.manches.forEach((id, m) -> {
                var copie = new ManchePersistante(id, m.getSoireeId(), m.getMode(), m.getReglages(), horloge.maintenant());
                copie.changerEtat(m.getEtat(), horloge.maintenant()); manches.put(id, copie);
            });
            Map<UUID, Map<UUID, ContenuKaraoke>> copieContenus = new HashMap<>();
            contenus.forEach((id, valeurs) -> copieContenus.put(id, Map.copyOf(valeurs)));
            Map<UUID, EnvoiDeManche> recus = new HashMap<>();
            depot.recus.forEach((id, recu) -> recus.put(id, copieEntites.readValue(copieEntites.writeValueAsString(recu), EnvoiDeManche.class)));
            sauvegarde.set(new Sauvegarde(manches, recus, new HashMap<>(passages.files),
                    new HashMap<>(passages.sequences), copieContenus));
        }
        @Override protected void doCommit(DefaultTransactionStatus statut) {
            if (echouerCommit) { echouerCommit = false; throw new TransactionSystemException("Commit refusé"); }
            sauvegarde.remove();
        }
        @Override protected void doRollback(DefaultTransactionStatus statut) {
            var avant = sauvegarde.get();
            depot.manches = avant.manches(); depot.recus = avant.recus(); contenus = avant.contenus();
            passages.files = avant.files(); passages.sequences = avant.sequences(); sauvegarde.remove();
        }
        record Sauvegarde(Map<UUID, ManchePersistante> manches, Map<UUID, EnvoiDeManche> recus,
                Map<UUID, EtatFileAttente> files, Map<UUID, Long> sequences, Map<UUID, Map<UUID, ContenuKaraoke>> contenus) { }
    }
}
