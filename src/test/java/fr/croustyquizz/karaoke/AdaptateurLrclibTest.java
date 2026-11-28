package fr.croustyquizz.karaoke;

import static fr.croustyquizz.Attentes.PLAFOND_SECONDES;
import static fr.croustyquizz.Attentes.RETENUE_SECONDES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import fr.croustyquizz.manche.HorlogeFactice;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class AdaptateurLrclibTest {
    private HttpServer serveur;
    private ExecutorService executant;
    private HttpClient client;
    private URI base;
    private final HorlogeFactice horloge = new HorlogeFactice();
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicInteger appels = new AtomicInteger();
    private final AtomicReference<String> chemin = new AtomicReference<>();
    private final AtomicReference<String> requete = new AtomicReference<>();
    private final AtomicReference<String> agent = new AtomicReference<>();
    private volatile int statut = 200;
    private volatile String retryAfter;
    private volatile byte[] corps = "[]".getBytes(StandardCharsets.UTF_8);

    @Test
    void valideLesProprietesEtIgnoreLesFutursReglagesDinterfaceAuBinding() {
        new ApplicationContextRunner().withUserConfiguration(ConfigurationSourceKaraoke.class)
                .withBean(JsonMapper.class, () -> json)
                .withBean("horlogeDeManche", fr.croustyquizz.manche.Horloge.class, () -> horloge)
                .withPropertyValues("croustyquizz.karaoke.urlLrclib=" + base,
                        "croustyquizz.karaoke.userAgentLrclib=CroustyQuizz-test/1.0",
                        "croustyquizz.karaoke.delaiSourceMs=1234",
                        "croustyquizz.karaoke.tailleReponseSourceMaxOctets=4321",
                        "croustyquizz.karaoke.longueurRechercheMax=80",
                        "croustyquizz.karaoke.delaiConsultationNavigateurMs=8000")
                .run(contexte -> {
                    assertThat(contexte).hasNotFailed().hasSingleBean(SourceDeParoles.class);
                    assertThat(contexte.getBean(ProprietesSourceKaraoke.class))
                            .isEqualTo(new ProprietesSourceKaraoke(base, "CroustyQuizz-test/1.0", 1234, 4321, 80));
                    HttpClient configure = contexte.getBean(HttpClient.class);
                    assertThat(configure.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
                    assertThat(configure.connectTimeout()).contains(Duration.ofMillis(1234));
                    assertThat(appels).hasValue(0);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/relatif", "http:/sans-hote", "ftp://localhost", "https://localhost?q=titre",
        "https://localhost#fragment", "https://localhost?", "https://localhost#", "mailto:test@example.org"})
    void refuseLesUrlsDeSourceInvalides(String url) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProprietesSourceKaraoke(URI.create(url), "Agent", 1, 1, 1));
    }

    @Test
    void refuseLesReglagesAbsentsOuHorsBornes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProprietesSourceKaraoke(null, "Agent", 1, 1, 1));
        for (String invalide : new String[] {null, "", " \t\n"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ProprietesSourceKaraoke(base, invalide, 1, 1, 1));
        }
        for (long invalide : new long[] {0, -1}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ProprietesSourceKaraoke(base, "Agent", invalide, 1, 1));
            assertThatIllegalArgumentException().isThrownBy(() -> new ProprietesSourceKaraoke(base, "Agent", 1, invalide, 1));
        }
        for (int invalide : new int[] {0, 121}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ProprietesSourceKaraoke(base, "Agent", 1, 1, invalide));
        }
        assertThat(new ProprietesSourceKaraoke(URI.create("https://lrclib.net"), "Agent", 1, 1, 120).longueurRechercheMax())
                .isEqualTo(120);
    }

    @Test
    void refuseUnClientQuiAutoriseLesRedirectionsEtNeSuitPasLesTroisCents() {
        for (HttpClient.Redirect redirection : List.of(HttpClient.Redirect.NORMAL, HttpClient.Redirect.ALWAYS)) {
            try (HttpClient autre = HttpClient.newBuilder().followRedirects(redirection).build()) {
                assertThatIllegalArgumentException().isThrownBy(() ->
                        new AdaptateurLrclib(new ProprietesSourceKaraoke(base, "Agent", 1000, 1000, 120), autre, json, horloge));
            }
        }
        statut = 302;
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().rechercher("titre"));
        assertThat(appels).hasValue(1);
    }

    @BeforeEach
    void ouvrirLaSourceLocale() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executant = Executors.newVirtualThreadPerTaskExecutor();
        serveur.setExecutor(executant);
        serveur.createContext("/", this::repondre);
        serveur.start();
        base = URI.create("http://127.0.0.1:" + serveur.getAddress().getPort() + "/prefixe/");
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @AfterEach
    void fermerLaSourceLocale() {
        serveur.stop(0);
        executant.shutdownNow();
        client.close();
    }

    @Test
    void rechercheDepuisLaRacineAvecLaSaisieEtLeUserAgentConfigures() {
        reponse("""
                [{"id":17001,"trackName":"  Le Dernier Métro  ","artistName":" Les Néons ",
                  "albumName":null,"duration":208.25,"instrumental":false,
                  "syncedLyrics":"[00:12.500]Le quai s'éveille","inconnu":"ignoré"}]
                """);
        List<Chanson> resultat = adaptateur().rechercher("Métro & Néons + 🎤");
        assertThat(chemin.get()).isEqualTo("/api/search");
        assertThat(URLDecoder.decode(requete.get(), StandardCharsets.UTF_8)).isEqualTo("q=Métro & Néons + 🎤");
        assertThat(agent.get()).isEqualTo("CroustyQuizz-test/1.0");
        assertThat(resultat).containsExactly(new Chanson(IdentifiantsChanson.encoder(17001), 17001,
                "Le Dernier Métro", "Les Néons", null, 208250L, EtatParoles.SYNCHRONISEES));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> resultat.clear());
    }

    private AdaptateurLrclib adaptateur() {
        return adaptateur(2000, 2097152);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "null", "[]", "true", "{} {}", "{"})
    void uneReponseDeDetailVideOuInvalideNestJamaisUneAbsence(String valeur) {
        reponse(valeur);
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().charger(17001));
    }

    @Test
    void conserveLesTextesLongsEtLesIdentifiantsApresReconstructionSansDedoublonnerLaSource() {
        String longTexte = "Texte".repeat(2000);
        String premier = fiche("\"albumName\":null").replace(" Titre ", " " + longTexte + " ")
                .replace(" Artiste ", " " + longTexte + " ");
        reponse("[" + premier + "," + fiche("\"albumName\":\"Album\"").replace("17001", "17002")
                + "," + premier + "]");
        List<Chanson> avant = adaptateur().rechercher("titre");
        assertThat(avant).extracting(Chanson::idLrclib).containsExactly(17001L, 17002L, 17001L);
        assertThat(avant.getFirst().titre()).isEqualTo(longTexte);
        assertThat(avant.getFirst().artiste()).isEqualTo(longTexte);
        assertThat(adaptateur().rechercher("titre")).isEqualTo(avant);
        assertThat(appels).hasValue(2);
    }

    @Test
    void accepteAussiUneBaseSansBarreFinaleEtLesBornesDidentiteSource() {
        base = URI.create(base.toString().replace("/prefixe/", "/autre"));
        reponse("[" + fiche("\"instrumental\":null").replace("17001", "1") + ","
                + fiche("\"instrumental\":null").replace("17001", "9007199254740991") + "]");
        assertThat(adaptateur().rechercher("titre")).extracting(Chanson::idLrclib).containsExactly(1L, 9007199254740991L);
        assertThat(chemin.get()).isEqualTo("/api/search");
    }

    @Test
    void memoriseRetryAfterDesLesEnTetesMemeSiLeCorpsResteOuvert() {
        AdaptateurLrclib source = adaptateur(250, 1);
        CountDownLatch liberer = new CountDownLatch(1);
        serveur.removeContext("/");
        serveur.createContext("/", echange -> {
            try (echange) {
                appels.incrementAndGet();
                echange.getResponseHeaders().set("Retry-After", "3");
                echange.sendResponseHeaders(503, 0);
                echange.getResponseBody().write('!');
                echange.getResponseBody().flush();
                retenir(liberer);
            }
        });
        try {
            // Les 3 s sont l'exigence du test, pas un plafond d'attente : elles restent fixes, sous la retenue du serveur.
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.rechercher("titre"))
                            .withMessageContaining("503"));
            assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.charger(17001))
                    .withMessageContaining("attente");
            assertThat(appels).hasValue(1);
        } finally { liberer.countDown(); }
    }

    private AdaptateurLrclib adaptateur(long delai, long taille) {
        return new AdaptateurLrclib(new ProprietesSourceKaraoke(base, "CroustyQuizz-test/1.0",
                delai, taille, 1), client, json, horloge);
    }

    @Test
    void respecteLaLimiteExacteEnOctetsEtArreteLeFluxTropVolumineux() throws Exception {
        assertThat(adaptateur(2000, 2).rechercher("titre")).isEmpty();
        assertThatExceptionOfType(SourceParolesIndisponible.class)
                .isThrownBy(() -> adaptateur(2000, 1).rechercher("titre")).withStackTraceContaining("taille");
        CountDownLatch fluxCoupe = new CountDownLatch(1);
        serveur.removeContext("/");
        serveur.createContext("/", echange -> {
            try (echange) {
                echange.sendResponseHeaders(200, 0);
                byte[] bloc = new byte[32768];
                for (int indice = 0; indice < 1024; indice++) {
                    echange.getResponseBody().write(bloc);
                    echange.getResponseBody().flush();
                }
            } catch (IOException erreur) {
                fluxCoupe.countDown();
            }
        });
        assertThatExceptionOfType(SourceParolesIndisponible.class)
                .isThrownBy(() -> adaptateur(2000, 100).rechercher("titre")).withStackTraceContaining("taille");
        assertThat(fluxCoupe.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void borneLesEnTetesEtLeCorpsEntierSansAttendreLaFinDuFlux(boolean enTetesEnvoyes) throws Exception {
        CountDownLatch liberer = new CountDownLatch(1);
        CountDownLatch fluxCoupe = new CountDownLatch(1);
        serveur.removeContext("/");
        serveur.createContext("/", echange -> {
            try (echange) {
                if (enTetesEnvoyes) {
                    echange.sendResponseHeaders(200, 0);
                    echange.getResponseBody().write('[');
                    echange.getResponseBody().flush();
                }
                retenir(liberer);
                if (!enTetesEnvoyes) { echange.sendResponseHeaders(200, 0); }
                byte[] bloc = new byte[32768];
                for (int indice = 0; indice < 1024; indice++) {
                    echange.getResponseBody().write(bloc);
                    echange.getResponseBody().flush();
                }
            } catch (IOException erreur) {
                fluxCoupe.countDown();
            }
        });
        try {
            // Les 3 s sont l'exigence du test, pas un plafond d'attente : elles restent fixes, sous la retenue du serveur.
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThatExceptionOfType(SourceParolesIndisponible.class)
                            .isThrownBy(() -> adaptateur(250, 2097152).rechercher("titre"))
                            .withStackTraceContaining("délai"));
        } finally {
            liberer.countDown();
        }
        assertThat(fluxCoupe.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void borneLaConnexionTlsSurLeServeurLocalQuiNeRepondPasAuDialogueTls() throws Exception {
        // HttpsServer est un HttpServer ; son dialogue TLS reste bloqué jusqu'à la fin du test.
        CountDownLatch liberer = new CountDownLatch(1);
        serveur.stop(0);
        HttpsServer tls = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur = tls;
        tls.setExecutor(executant);
        tls.setHttpsConfigurator(new HttpsConfigurator(SSLContext.getDefault()) {
            @Override public void configure(HttpsParameters parametres) {
                try { retenir(liberer); }
                catch (IOException erreur) { throw new IllegalStateException(erreur); }
                super.configure(parametres);
            }
        });
        tls.createContext("/", this::repondre);
        tls.start();
        URI https = URI.create("https://127.0.0.1:" + tls.getAddress().getPort());
        try (HttpClient connexionBornee = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(250)).build()) {
            SourceDeParoles source = new AdaptateurLrclib(new ProprietesSourceKaraoke(https, "Agent", 1500, 1000, 120),
                    connexionBornee, json, horloge);
            // Les 3 s sont l'exigence du test, pas un plafond d'attente : elles restent fixes, sous la retenue du serveur.
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.rechercher("titre"))
                            .withStackTraceContaining(HttpConnectTimeoutException.class.getName()));
        } finally { liberer.countDown(); }
    }

    @Test
    void distingueAussiUneErreurReseauDunResultatVide() {
        serveur.stop(0);
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().rechercher("titre"))
                .withRootCauseInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void respecteRetryAfterEnSecondesSansJsonNiReessaiAutomatique(int code) {
        AdaptateurLrclib source = adaptateur();
        statut = code;
        retryAfter = "3";
        reponse("source occupée, ceci n'est pas du JSON");
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.rechercher("titre"));
        assertThat(appels).hasValue(1);
        statut = 200;
        reponse("[]");
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.charger(17001));
        horloge.avancer(Duration.ofSeconds(2));
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.rechercher("titre"));
        assertThat(appels).hasValue(1);
        assertThat(horloge.minuteursEnAttente()).isZero();
        horloge.avancer(Duration.ofSeconds(1));
        assertThat(source.rechercher("titre")).isEmpty();
        assertThat(appels).hasValue(2);
    }

    @Test
    void respecteAussiUneDateHttpPourUnDetail() {
        AdaptateurLrclib source = adaptateur();
        statut = 503;
        retryAfter = DateTimeFormatter.RFC_1123_DATE_TIME.format(horloge.maintenant().plusSeconds(10).atZone(ZoneOffset.UTC));
        reponse("occupé");
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.charger(17001));
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.rechercher("titre"));
        assertThat(appels).hasValue(1);
        horloge.avancer(Duration.ofSeconds(10));
        statut = 200;
        reponse(fiche("\"instrumental\":false"));
        assertThat(source.charger(17001)).isPresent();
        assertThat(appels).hasValue(2);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"", "0", "-1", "1.5", "+1", "n'importe quoi", "99999999999999999999999999999",
        "Thu, 01 Jan 1970 00:00:00 GMT"})
    void neCreePasDattenteSiRetryAfterEstAbsentInvalideOuEchu(String entete) {
        AdaptateurLrclib source = adaptateur();
        statut = 429;
        retryAfter = entete;
        reponse("occupé");
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.rechercher("titre"));
        statut = 200;
        reponse("[]");
        assertThat(source.rechercher("titre")).isEmpty();
        assertThat(appels).hasValue(2);
    }

    @Test
    void neRaccourcitPasUneAttenteConnueQuandUneReponseConcurrenteArrivePlusTard() throws Exception {
        CountDownLatch requeteEntree = new CountDownLatch(1);
        CountDownLatch libererReponse = new CountDownLatch(1);
        serveur.createContext("/api/get/2", echange -> {
            try (echange) {
                appels.incrementAndGet();
                requeteEntree.countDown();
                retenir(libererReponse);
                echange.getResponseHeaders().set("Retry-After", "1");
                echange.sendResponseHeaders(503, -1);
            }
        });
        AdaptateurLrclib source = adaptateur();
        try (ExecutorService taches = Executors.newVirtualThreadPerTaskExecutor()) {
            var court = taches.submit(() -> assertThatExceptionOfType(SourceParolesIndisponible.class)
                    .isThrownBy(() -> source.charger(2)));
            try {
                assertThat(requeteEntree.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
                statut = 429;
                retryAfter = "10";
                assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.rechercher("titre"));
            } finally {
                libererReponse.countDown();
            }
            court.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
        }
        statut = 200;
        horloge.avancer(Duration.ofSeconds(1));
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> source.rechercher("titre"));
        assertThat(appels).hasValue(2);
        horloge.avancer(Duration.ofSeconds(9));
        assertThat(source.rechercher("titre")).isEmpty();
        assertThat(appels).hasValue(3);
    }

    private static void retenir(CountDownLatch signal) throws IOException {
        try {
            if (!signal.await(RETENUE_SECONDES, TimeUnit.SECONDS)) { throw new IOException("Signal de test non reçu"); }
        } catch (InterruptedException erreur) {
            Thread.currentThread().interrupt();
            throw new IOException("Serveur de test interrompu", erreur);
        }
    }

    @ParameterizedTest
    @MethodSource("candidatsMalformes")
    void ecarteSeulementLeCandidatMalformeMaisRefuseLeDetail(String malforme) {
        String valide = fiche("\"instrumental\":false");
        String autre = valide.replace("17001", "17002");
        reponse("[" + autre + "," + malforme + "," + valide + "]");
        assertThat(adaptateur().rechercher("titre")).extracting(Chanson::idLrclib).containsExactly(17002L, 17001L);
        reponse("[" + malforme + "]");
        assertThat(adaptateur().rechercher("titre")).isEmpty();
        reponse(malforme);
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().charger(17001));
    }

    static Stream<String> candidatsMalformes() {
        Stream<String> identites = Stream.of("null", "true", "\"17001\"", "0", "-1", "1.5",
                "17001.0", "9007199254740992", "9223372036854775808")
                .map(id -> fiche("\"instrumental\":false").replace("17001", id));
        Stream<String> titres = Stream.of("null", "\"  \"", "12", "false", "[]", "{}")
                .flatMap(valeur -> Stream.of(
                        fiche("\"instrumental\":false").replace("\" Titre \"", valeur),
                        fiche("\"instrumental\":false").replace("\" Artiste \"", valeur)));
        Stream<String> types = Stream.of("\"duration\":\"208\"", "\"duration\":true", "\"duration\":{}",
                "\"instrumental\":0", "\"instrumental\":\"false\"", "\"plainLyrics\":[]",
                "\"syncedLyrics\":false", "\"albumName\":12").map(AdaptateurLrclibTest::fiche);
        return Stream.concat(Stream.concat(identites, titres), Stream.concat(types, Stream.of("null", "17", "{}", "[]")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null", "true", "{}", "[] []", "[{}"})
    void distingueLeJsonGlobalInvalideOuLaMauvaiseRacineDuneRechercheVide(String valeur) {
        reponse(valeur);
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().rechercher("titre"));
    }

    @Test
    void refuseUnUtf8InvalideEtUneIdentiteDeDetailIncoherente() {
        corps = new byte[] {'[', '"', (byte) 0xc3, 0x28, '"', ']'};
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().rechercher("titre"));
        reponse(fiche("\"instrumental\":false").replace("17001", "17002"));
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().charger(17001));
        reponse("[" + fiche("\"instrumental\":false") + "]");
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().charger(17001));
    }

    @Test
    void distingueLeDetailAbsentDeLaPanneEtDeLaRechercheVide() {
        assertThat(adaptateur().rechercher("titre")).isEmpty();
        statut = 404;
        reponse("non trouvé");
        assertThat(adaptateur().charger(17001)).isEmpty();
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().rechercher("titre"));
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 301, 302, 307, 429, 500, 503})
    void refuseLesStatutsEnEchecSansAnalyserLeCorps(int code) {
        statut = code;
        reponse("erreur sans JSON");
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().rechercher("titre"));
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> adaptateur().charger(17001));
    }

    @Test
    void chargeLeDetailAvecLesTextesRognesEtLesHorodatagesTriesStablement() {
        String paroles = """
                [ar:Métadonnée]
                [00:02][00:01.25] premier
                [00:01.250] égal
                [123:04.567] minutes longues
                [00:60][00:03] invalide
                [00:04][mauvais] invalide aussi
                [00:05.1] fraction invalide
                [00:07.1234] fraction trop longue
                [00:08]   
                [999999999999999999999999999999999:00] débordement
                [00:09] dernier
                [123:05] hors durée
                [123:06] hors durée aussi
                """;
        reponse(fiche("\"albumName\":\" Album \",\"duration\":7385,\"instrumental\":false,"
                + "\"plainLyrics\":\"simple\",\"syncedLyrics\":" + json.writeValueAsString(paroles)));
        ContenuKaraoke contenu = adaptateur().charger(17001).orElseThrow();
        assertThat(chemin.get()).isEqualTo("/api/get/17001");
        assertThat(contenu.chanson().album()).isEqualTo("Album");
        assertThat(contenu.lignes()).containsExactly(new LigneParoles(1250, "premier"),
                new LigneParoles(1250, "égal"), new LigneParoles(2000, "premier"),
                new LigneParoles(9000, "dernier"), new LigneParoles(7384567, "minutes longues"));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> contenu.lignes().clear());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "\"instrumental\":true,\"syncedLyrics\":\"[00:01] texte\" | SANS_VOIX | 1",
        "\"syncedLyrics\":\"[00:01] texte\" | INCONNU | 1",
        "\"instrumental\":null,\"syncedLyrics\":\"[00:01] texte\" | INCONNU | 1",
        "\"instrumental\":false,\"syncedLyrics\":\"[00:01] texte\" | SYNCHRONISEES | 1",
        "\"instrumental\":false,\"syncedLyrics\":\"invalide\",\"plainLyrics\":\"texte\" | INCONNU | 0",
        "\"instrumental\":false,\"syncedLyrics\":\"  \",\"plainLyrics\":\" texte \" | NON_SYNCHRONISEES | 0",
        "\"instrumental\":false,\"syncedLyrics\":null,\"plainLyrics\":null | ABSENTES | 0",
        "\"instrumental\":false | ABSENTES | 0"
    })
    void distingueTousLesEtatsSansPerdreLesLignes(String champs, EtatParoles etat, int nombreLignes) {
        reponse(fiche(champs));
        ContenuKaraoke contenu = adaptateur().charger(17001).orElseThrow();
        assertThat(contenu.chanson().etatParoles()).isEqualTo(etat);
        assertThat(contenu.chanson().dureeMs()).isNull();
        assertThat(contenu.lignes()).hasSize(nombreLignes);
        reponse("[" + fiche(champs) + "]");
        assertThat(adaptateur().rechercher("titre")).containsExactly(contenu.chanson());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "0.0001", "1e308", "1e999", "9223372036854776"})
    void gardeUneDureeNumeriqueInutilisableCommeInconnue(String duree) {
        reponse(fiche("\"duration\":" + duree + ",\"instrumental\":false,\"syncedLyrics\":\"[00:00] texte\""));
        Chanson chanson = adaptateur().charger(17001).orElseThrow().chanson();
        assertThat(chanson.dureeMs()).isNull();
        assertThat(chanson.etatParoles()).isEqualTo(EtatParoles.SYNCHRONISEES);
    }

    @Test
    void arronditLaDureeEtBorneLesLignesSansLimiterLesTextes() {
        String longTexte = "é".repeat(5000);
        reponse(fiche("\"duration\":0.0015,\"instrumental\":false,\"albumName\":\"  \","
                + "\"syncedLyrics\":" + json.writeValueAsString(" [00:00] " + longTexte + "\n[00:00.02] trop tard")));
        ContenuKaraoke contenu = adaptateur().charger(17001).orElseThrow();
        assertThat(contenu.chanson().dureeMs()).isEqualTo(2L);
        assertThat(contenu.chanson().album()).isNull();
        assertThat(contenu.lignes()).containsExactly(new LigneParoles(0, longTexte));
    }

    private static String fiche(String champs) {
        return "{\"id\":17001,\"trackName\":\" Titre \",\"artistName\":\" Artiste \"," + champs + "}";
    }

    private void reponse(String texte) { corps = texte.getBytes(StandardCharsets.UTF_8); }

    private void repondre(HttpExchange echange) throws IOException {
        try (echange) {
            appels.incrementAndGet();
            chemin.set(echange.getRequestURI().getPath());
            requete.set(echange.getRequestURI().getRawQuery());
            agent.set(echange.getRequestHeaders().getFirst("User-Agent"));
            echange.getResponseHeaders().set("Location", "/cible");
            if (retryAfter != null) { echange.getResponseHeaders().set("Retry-After", retryAfter); }
            echange.sendResponseHeaders(statut, statut == 204 ? -1 : corps.length);
            if (statut != 204) { echange.getResponseBody().write(corps); }
        }
    }
}
