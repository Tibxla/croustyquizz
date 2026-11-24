package fr.croustyquizz.blindtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/** Contre un serveur local qui sert des réponses Deezer enregistrées : aucun appel réel à Deezer. */
class AdaptateurDeezerTest {

	private static final long PLAYLIST = 908_622_995L;
	private static final URI POCHETTE_HALO = URI.create(
			"https://e-cdns-images.dzcdn.net/images/cover/2e0/250x250-000000-80-0-0.jpg");

	private final JsonMapper json = JsonMapper.builder().build();
	/** Réponses servies, par chemin et requête : « /prefixe/playlist/1/tracks?index=0&limit=100 ». */
	private final Map<String, Reponse> reponses = new ConcurrentHashMap<>();
	private final List<String> demandes = new CopyOnWriteArrayList<>();
	private HttpServer serveur;
	private ExecutorService executant;
	private HttpClient client;
	private URI base;

	private record Reponse(int statut, byte[] corps) {
	}

	@BeforeEach
	void ouvrirDeezerLocal() throws IOException {
		serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		executant = Executors.newVirtualThreadPerTaskExecutor();
		serveur.setExecutor(executant);
		serveur.createContext("/", this::repondre);
		serveur.start();
		base = URI.create("http://127.0.0.1:" + serveur.getAddress().getPort() + "/prefixe/");
		client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
	}

	@AfterEach
	void fermerDeezerLocal() {
		serveur.stop(0);
		executant.shutdownNow();
		client.close();
	}

	// --- Listes de Morceaux ---

	@Test
	void litUnePlaylistSurDeuxPagesEtEcarteLesMorceauxSansExtrait() {
		servir("playlist/908622995/tracks?index=0&limit=100", "playlist-page-1.json");
		servir("playlist/908622995/tracks?index=3&limit=100", "playlist-page-2.json");

		List<Morceau> morceaux = adaptateur().morceauxDePlaylist(PLAYLIST);

		assertThat(morceaux).containsExactly(
				new Morceau(3135556, "Halo", "Beyoncé", POCHETTE_HALO),
				new Morceau(67238735, "Get Lucky", "Daft Punk", null),
				new Morceau(2114295, "Respect", "Aretha Franklin",
						URI.create("https://e-cdns-images.dzcdn.net/images/cover/7a/250x250-000000-80-0-0.jpg")));
		assertThat(demandes).containsExactly("/prefixe/playlist/908622995/tracks?index=0&limit=100",
				"/prefixe/playlist/908622995/tracks?index=3&limit=100");
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(morceaux::clear);
	}

	@Test
	void prefereLeTitreCourtAuTitreComplet() {
		servir("playlist/908622995/tracks?index=0&limit=100", "playlist-page-1.json");
		servir("playlist/908622995/tracks?index=3&limit=100", "playlist-page-2.json");

		assertThat(adaptateur().morceauxDePlaylist(PLAYLIST)).extracting(Morceau::titre)
				.contains("Get Lucky").doesNotContain("Get Lucky (Radio Edit - feat. Pharrell Williams and Nile Rodgers)");
	}

	@Test
	void borneLeNombreDePagesDUnePlaylist() {
		String morceau = "{\"id\":1,\"title\":\"T\",\"preview\":\"https://cdnt-preview.dzcdn.net/1.mp3\","
				+ "\"artist\":{\"name\":\"A\"}}";
		String pagePleine = "{\"data\":[" + String.join(",", Collections.nCopies(AdaptateurDeezer.TAILLE_PAGE,
				morceau)) + "],\"total\":1000000}";
		serveur.removeContext("/");
		serveur.createContext("/", echange -> envoyer(echange, new Reponse(200,
				pagePleine.getBytes(StandardCharsets.UTF_8))));

		assertThat(adaptateur().morceauxDePlaylist(PLAYLIST))
				.hasSize(AdaptateurDeezer.PAGES_MAX * AdaptateurDeezer.TAILLE_PAGE);
		assertThat(demandes).hasSize(AdaptateurDeezer.PAGES_MAX);
	}

	@Test
	void litLeClassementDUnGenreEtEcarteLesCandidatsMalFormes() {
		servir("chart/132/tracks?limit=100", "chart-132.json");

		List<Morceau> morceaux = adaptateur().morceauxDuGenre(132);

		// Sans title_short, le titre complet sert ; une pochette illisible devient absente.
		assertThat(morceaux).containsExactly(
				new Morceau(3135556, "Halo", "Beyoncé", POCHETTE_HALO),
				new Morceau(3127466, "Dancing Queen", "ABBA", null));
		assertThat(demandes).containsExactly("/prefixe/chart/132/tracks?limit=100");
	}

	@Test
	void uneListeVideNEstPasUnePanne() {
		servir("chart/0/tracks?limit=100", "{\"data\":[],\"total\":0}".getBytes(StandardCharsets.UTF_8));

		assertThat(adaptateur().morceauxDuGenre(0)).isEmpty();
	}

	// --- Lien d'extrait ---

	@Test
	void redemandeLeLienDExtraitAChaqueAppelSansCache() {
		servir("track/3135556", "track-3135556.json");
		AdaptateurDeezer deezer = adaptateur();

		Optional<URI> premier = deezer.lienExtrait(3135556);
		servir("track/3135556", lu("track-3135556.json").replace("hmac=99", "hmac=100"));
		Optional<URI> second = deezer.lienExtrait(3135556);

		assertThat(premier).contains(URI.create(
				"https://cdnt-preview.dzcdn.net/api/1/1/a/b/c/0/abc.mp3?hdnea=exp=1764101999~acl=*~hmac=99"));
		assertThat(second).hasValueSatisfying(lien -> assertThat(lien.toString()).endsWith("hmac=100"));
		assertThat(demandes).containsExactly("/prefixe/track/3135556", "/prefixe/track/3135556");
	}

	@Test
	void unMorceauSansExtraitOuIllisibleNAPasDeLien() {
		servir("track/916424", "track-sans-extrait.json");
		servir("track/3135556", lu("track-3135556.json").replace("\"readable\":true", "\"readable\":false"));

		assertThat(adaptateur().lienExtrait(916424)).isEmpty();
		assertThat(adaptateur().lienExtrait(3135556)).isEmpty();
	}

	@Test
	void refuseUnDetailDontLIdentiteNeCorrespondPas() {
		servir("track/42", "track-3135556.json");

		assertThatExceptionOfType(SourceMusicaleIndisponible.class).isThrownBy(() -> adaptateur().lienExtrait(42));
	}

	// --- Pannes : toujours l'exception dédiée ---

	@Test
	void uneErreurJsonDeDeezerAvecUnStatut200EstUnePanne() {
		servir("chart/132/tracks?limit=100", "erreur-quota.json");
		servir("track/3135556", "erreur-quota.json");

		assertThatExceptionOfType(SourceMusicaleIndisponible.class).isThrownBy(() -> adaptateur().morceauxDuGenre(132))
				.withMessageContaining("4");
		assertThatExceptionOfType(SourceMusicaleIndisponible.class)
				.isThrownBy(() -> adaptateur().lienExtrait(3135556));
	}

	@ParameterizedTest
	@ValueSource(ints = {302, 400, 403, 404, 429, 500, 503})
	void unStatutInattenduEstUnePanneSansSuivreDeRedirection(int statut) {
		reponses.put("/prefixe/track/3135556", new Reponse(statut, lu("track-3135556.json").getBytes(StandardCharsets.UTF_8)));

		assertThatExceptionOfType(SourceMusicaleIndisponible.class)
				.isThrownBy(() -> adaptateur().lienExtrait(3135556)).withMessageContaining(String.valueOf(statut));
		assertThat(demandes).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "null", "[]", "{", "{} {}", "{\"data\":{}}", "{\"total\":3}"})
	void uneReponseMalFormeeEstUnePanne(String corps) {
		servir("chart/132/tracks?limit=100", corps.getBytes(StandardCharsets.UTF_8));

		assertThatExceptionOfType(SourceMusicaleIndisponible.class).isThrownBy(() -> adaptateur().morceauxDuGenre(132));
	}

	@Test
	void unUtf8InvalideEstUnePanne() {
		servir("chart/132/tracks?limit=100", new byte[] {'{', '"', (byte) 0xC3, '"', ':', '1', '}'});

		assertThatExceptionOfType(SourceMusicaleIndisponible.class).isThrownBy(() -> adaptateur().morceauxDuGenre(132));
	}

	@Test
	void uneReponseTropVolumineuseEstUnePanne() {
		servir("track/3135556", "track-3135556.json");

		assertThatExceptionOfType(SourceMusicaleIndisponible.class)
				.isThrownBy(() -> adaptateur(2000, 64).lienExtrait(3135556));
	}

	@Test
	void unDeezerInjoignableEstUnePanne() {
		serveur.stop(0);

		assertThatExceptionOfType(SourceMusicaleIndisponible.class).isThrownBy(() -> adaptateur().lienExtrait(3135556))
				.withRootCauseInstanceOf(IOException.class);
	}

	@Test
	void unDelaiDepasseEstUnePanneMemeSiLeCorpsResteOuvert() throws Exception {
		CountDownLatch liberer = new CountDownLatch(1);
		serveur.removeContext("/");
		serveur.createContext("/", echange -> {
			try (echange) {
				demandes.add(echange.getRequestURI().toString());
				echange.sendResponseHeaders(200, 0);
				echange.getResponseBody().write('{');
				echange.getResponseBody().flush();
				liberer.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException interruption) {
				Thread.currentThread().interrupt();
			}
		});
		try {
			assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
					assertThatExceptionOfType(SourceMusicaleIndisponible.class)
							.isThrownBy(() -> adaptateur(250, 2_097_152).lienExtrait(3135556))
							.withMessageContaining("délai"));
		} finally {
			liberer.countDown();
		}
	}

	// --- Réglages et branchement ---

	@Test
	void accepteUneBaseSansBarreFinale() {
		base = URI.create(base.toString().replace("/prefixe/", "/prefixe"));
		servir("track/3135556", "track-3135556.json");

		assertThat(adaptateur().lienExtrait(3135556)).isPresent();
		assertThat(demandes).containsExactly("/prefixe/track/3135556");
	}

	@Test
	void refuseUnClientQuiSuitLesRedirections() {
		for (HttpClient.Redirect redirection : List.of(HttpClient.Redirect.NORMAL, HttpClient.Redirect.ALWAYS)) {
			try (HttpClient autre = HttpClient.newBuilder().followRedirects(redirection).build()) {
				assertThatIllegalArgumentException().isThrownBy(() ->
						new AdaptateurDeezer(new ProprietesCatalogueMusical(base, 1000, 1000), autre, json));
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"/relatif", "http:/sans-hote", "ftp://localhost", "https://localhost?q=1",
		"https://localhost#fragment", "mailto:test@example.org"})
	void refuseLesUrlsDeezerInvalides(String url) {
		assertThatIllegalArgumentException().isThrownBy(() -> new ProprietesCatalogueMusical(URI.create(url), 1, 1));
	}

	@Test
	void refuseLesReglagesAbsentsOuNonPositifs() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ProprietesCatalogueMusical(null, 1, 1));
		assertThatIllegalArgumentException().isThrownBy(() -> new ProprietesCatalogueMusical(base, 0, 1));
		assertThatIllegalArgumentException().isThrownBy(() -> new ProprietesCatalogueMusical(base, 1, 0));
	}

	@Test
	void brancheLeCatalogueSansConsulterDeezerAuDemarrage() {
		new ApplicationContextRunner().withUserConfiguration(ConfigurationCatalogueMusical.class)
				.withBean(JsonMapper.class, () -> json)
				.withPropertyValues("croustyquizz.blindtest.urlDeezer=" + base,
						"croustyquizz.blindtest.delaiDeezerMs=1234",
						"croustyquizz.blindtest.tailleReponseMaxOctets=4321")
				.run(contexte -> {
					assertThat(contexte).hasNotFailed().hasSingleBean(CatalogueMusical.class);
					assertThat(contexte.getBean(CatalogueMusical.class)).isInstanceOf(AdaptateurDeezer.class);
					assertThat(contexte.getBean(ProprietesCatalogueMusical.class))
							.isEqualTo(new ProprietesCatalogueMusical(base, 1234, 4321));
					HttpClient configure = contexte.getBean(HttpClient.class);
					assertThat(configure.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
					assertThat(configure.connectTimeout()).contains(Duration.ofMillis(1234));
					assertThat(demandes).isEmpty();
				});
	}

	// --- Outils ---

	private AdaptateurDeezer adaptateur() {
		return adaptateur(2000, 2_097_152);
	}

	private AdaptateurDeezer adaptateur(long delaiMs, long tailleMax) {
		return new AdaptateurDeezer(new ProprietesCatalogueMusical(base, delaiMs, tailleMax), client, json);
	}

	private void servir(String cheminEtRequete, String fichierOuCorps) {
		String corps = fichierOuCorps.endsWith(".json") ? lu(fichierOuCorps) : fichierOuCorps;
		servir(cheminEtRequete, corps.getBytes(StandardCharsets.UTF_8));
	}

	private void servir(String cheminEtRequete, byte[] corps) {
		reponses.put("/prefixe/" + cheminEtRequete, new Reponse(200, corps));
	}

	private static String lu(String fichier) {
		try (InputStream flux = AdaptateurDeezerTest.class.getResourceAsStream("/blindtest/deezer/" + fichier)) {
			return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException erreur) {
			throw new UncheckedIOException(erreur);
		}
	}

	private void repondre(HttpExchange echange) throws IOException {
		String demande = echange.getRequestURI().toString();
		envoyer(echange, reponses.getOrDefault(demande, new Reponse(404, new byte[0])));
	}

	private void envoyer(HttpExchange echange, Reponse reponse) throws IOException {
		try (echange) {
			demandes.add(echange.getRequestURI().toString());
			echange.getResponseHeaders().set("Location", "/ailleurs");
			echange.sendResponseHeaders(reponse.statut(), reponse.corps().length == 0 ? -1 : reponse.corps().length);
			if (reponse.corps().length > 0) {
				echange.getResponseBody().write(reponse.corps());
			}
		}
	}

}
