package fr.croustyquizz.blindtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.manche.RegistreDesDeroulements;
import fr.croustyquizz.tempsreel.Canaux;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Blind test de bout en bout (#177) sur une Soirée enregistrée : PostgreSQL, HTTP, STOMP, Horloge factice et
 * catalogue local. Chaque réponse est validée par le contrat, chaque vue de salle aussi.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, BlindTestHttpTest.Environnement.class})
class BlindTestHttpTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final Morceau HALO = new Morceau(3135556, "Halo", "Beyoncé",
			URI.create("https://e-cdns-images.dzcdn.net/images/cover/halo.jpg"));
	private static final Morceau GET_LUCKY = new Morceau(67238735, "Get Lucky", "Daft Punk", null);

	@LocalServerPort private int port;
	@Autowired private JsonMapper json;
	@Autowired private HorlogeFactice horloge;
	@Autowired private CatalogueDeTest catalogue;
	@Autowired private RegistreDesDeroulements registre;
	@Autowired private ServiceBlindTest service;
	private final WsWebSocketContainer conteneur = new WsWebSocketContainer();
	private final WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient(conteneur));
	private final List<StompSession> sessions = new ArrayList<>();
	private final HttpClient clientHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private final RestClient http = clientHttp(clientHttp);

	@TestConfiguration(proxyBeanMethods = false)
	static class Environnement {
		@Bean @Primary HorlogeFactice horlogeDuBlindTest() { return new HorlogeFactice(); }
		@Bean @Primary CatalogueDeTest catalogueDeTest() { return new CatalogueDeTest(); }
	}

	/** Catalogue local : deux Morceaux, des pannes et des lenteurs à la demande ; jamais d'appel à Deezer. */
	static final class CatalogueDeTest implements CatalogueMusical {
		volatile boolean enPanne;
		volatile CountDownLatch retenue;
		final Set<Long> sansExtrait = ConcurrentHashMap.newKeySet();
		final AtomicInteger liensDemandes = new AtomicInteger();
		final AtomicInteger liensRendus = new AtomicInteger();
		/** Nombre de prochaines demandes de lien qui échouent hors du contrat du catalogue. */
		final AtomicInteger pannesImprevues = new AtomicInteger();
		/**
		 * Playlists propres à un test : une préparation encore en cours pour la Soirée d'un test précédent ne peut ni
		 * consommer leurs pannes ni fausser leur compte de demandes (#216).
		 */
		final Map<Long, MorceauxIsoles> playlistsIsolees = new ConcurrentHashMap<>();
		private final AtomicLong prochainId = new AtomicLong(900_000_000L);

		/** Deux Morceaux neufs, dont les {@code pannes} premières demandes de lien échouent hors du contrat. */
		MorceauxIsoles isoler(int pannes) {
			long base = prochainId.getAndAdd(10);
			var isoles = new MorceauxIsoles(base, List.of(new Morceau(base + 1, "Halo", "Beyoncé", null),
					new Morceau(base + 2, "Get Lucky", "Daft Punk", null)), new AtomicInteger(pannes), new AtomicInteger());
			playlistsIsolees.put(base, isoles);
			return isoles;
		}

		@Override public List<Morceau> morceauxDePlaylist(long playlistId) {
			if (enPanne) { throw new SourceMusicaleIndisponible("Deezer ne répond pas."); }
			MorceauxIsoles isoles = playlistsIsolees.get(playlistId);
			return isoles != null ? isoles.morceaux() : List.of(HALO, GET_LUCKY);
		}
		@Override public List<Morceau> morceauxDuGenre(long genreId) { return morceauxDePlaylist(genreId); }
		@Override public Optional<URI> lienExtrait(long idDeezer) {
			for (MorceauxIsoles isoles : playlistsIsolees.values()) {
				if (isoles.contient(idDeezer)) {
					isoles.demandes().incrementAndGet();
					if (isoles.pannes().getAndUpdate(reste -> Math.max(0, reste - 1)) > 0) {
						throw new IllegalStateException("Panne imprévue du catalogue de test.");
					}
					return Optional.of(URI.create("https://cdnt-preview.dzcdn.net/extrait.mp3?hdnea=exp=1~hmac=" + Long.toHexString(idDeezer * 31)));
				}
			}
			liensDemandes.incrementAndGet();
			if (pannesImprevues.getAndUpdate(reste -> Math.max(0, reste - 1)) > 0) {
				throw new IllegalStateException("Panne imprévue du catalogue de test.");
			}
			try {
				CountDownLatch attente = retenue;
				if (attente != null && !attente.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Retenue oubliée"); }
			} catch (InterruptedException interruption) {
				Thread.currentThread().interrupt();
			} finally {
				liensRendus.incrementAndGet();
			}
			return sansExtrait.contains(idDeezer) ? Optional.empty()
					: Optional.of(URI.create("https://cdnt-preview.dzcdn.net/extrait.mp3?hdnea=exp=1~hmac=" + Long.toHexString(idDeezer * 31)));
		}
	}

	record MorceauxIsoles(long playlistId, List<Morceau> morceaux, AtomicInteger pannes, AtomicInteger demandes) {
		boolean contient(long idDeezer) {
			return morceaux.stream().anyMatch(morceau -> morceau.idDeezer() == idDeezer);
		}
	}

	@BeforeEach
	void reinitialiserLeCatalogue() {
		catalogue.enPanne = false;
		catalogue.retenue = null;
		catalogue.sansExtrait.clear();
		catalogue.pannesImprevues.set(0);
	}

	@AfterEach
	void fermerLesConnexions() {
		CountDownLatch attente = catalogue.retenue;
		if (attente != null) { attente.countDown(); }
		for (var session : sessions) {
			if (session.isConnected()) { session.disconnect(); }
		}
		stomp.stop();
		conteneur.destroy();
		clientHttp.close();
	}

	// --- Parcours complet ---

	@Test
	void uneMancheDeDeuxMorceauxSeJoueDeBoutEnBout() throws Exception {
		Soiree soiree = ouvrirSoiree("MANUEL");
		Joueur lea = rejoindre(soiree, "Léa");
		Joueur sam = rejoindre(soiree, "Sam");
		JsonNode manche = verifier("ajouterManche", envoyer(HttpMethod.POST, soiree.route() + "/manches", soiree.animateur(),
				Map.of("mode", "BLIND_TEST", "reglages", Map.of("source", Map.of("type", "PLAYLIST", "idDeezer", 908622995),
						"nombreMorceaux", 2)), null), 201);
		assertThat(manche.path("reglages").toString()).doesNotContain("Halo", "3135556", "67238735");
		String mancheId = manche.path("id").asString();
		var salle = abonner(soiree, "salle", soiree.cleEcran(), Canaux.salle(soiree.id()));
		lancer(soiree);

		// Morceau 1 : le lien est demandé hors du verrou, puis l'extrait part.
		JsonNode bloc = attendrePhase(soiree, "EXTRAIT", 1);
		String etape1 = bloc.path("etapeId").asString();
		assertThat(bloc.path("lienExtrait").asString()).startsWith("https://cdnt-preview.dzcdn.net/");
		Morceau premier = morceauRevele(soiree);

		JsonNode buzzLea = verifier("envoyerBuzz", buzzer(soiree, lea, mancheId, etape1, UUID.randomUUID()), 200);
		assertThat(buzzLea.path("pseudo").asString()).isEqualTo("Léa");
		assertThat(buzzLea.path("rang").asInt()).isEqualTo(1);
		verifierRefus("envoyerBuzz", buzzer(soiree, sam, mancheId, etape1, UUID.randomUUID()), 409, "BUZZ_NON_AUTORISE");
		JsonNode captureSalle = capture(soiree, "salle", soiree.cleEcran());
		assertThat(captureSalle.path("vue").path("dernierBuzz")).isEqualTo(buzzLea);
		assertThat(captureSalle.path("vue").path("blindtest").path("phase").asString()).isEqualTo("PROPOSITION");
		JsonNode reglagesSalle = captureSalle.path("vue").path("mancheActive").path("reglages");
		assertThat(reglagesSalle.propertyNames()).containsExactlyInAnyOrder("source", "nombreMorceaux");
		assertThat(reglagesSalle.toString()).doesNotContain(premier.titre(), premier.artiste(),
				String.valueOf(premier.idDeezer()));

		// Brouillon : sa Manette le retrouve après un rechargement ; un rejeu plus ancien est ignoré.
		String proposition = soiree.route() + "/manches/" + mancheId + "/morceaux/" + etape1;
		verifier("deposerBrouillonProposition", envoyer(HttpMethod.PUT, proposition + "/brouillon", lea.jeton(),
				Map.of("numero", 2, "titre", "", "artiste", premier.artiste().toLowerCase()), null), 204);
		verifier("deposerBrouillonProposition", envoyer(HttpMethod.PUT, proposition + "/brouillon", lea.jeton(),
				Map.of("numero", 1, "titre", "", "artiste", "rien"), null), 204);
		JsonNode manetteLea = capture(soiree, "manette", lea.jeton()).path("vue").path("blindtest");
		assertThat(manetteLea.path("aLaMain").asBoolean()).isTrue();
		assertThat(manetteLea.path("monBrouillon").path("numero").asInt()).isEqualTo(2);
		assertThat(manetteLea.path("monBrouillon").path("artiste").asString()).isEqualTo(premier.artiste().toLowerCase());

		// Léa trouve l'artiste mais laisse le titre vide : 30 points, et elle est bloquée.
		UUID envoiLea = UUID.randomUUID();
		JsonNode accuse = verifier("validerProposition", envoyer(HttpMethod.POST, proposition + "/proposition", lea.jeton(),
				Map.of("envoiId", envoiLea, "titre", "", "artiste", premier.artiste()), null), 200);
		assertThat(accuse.path("titre").asString()).isEqualTo("FAUX");
		assertThat(accuse.path("artiste").asString()).isEqualTo("JUSTE");
		assertThat(accuse.path("pointsGagnes").asInt()).isEqualTo(30);
		assertThat(verifier("validerProposition", envoyer(HttpMethod.POST, proposition + "/proposition", lea.jeton(),
				Map.of("envoiId", envoiLea, "titre", "", "artiste", premier.artiste()), null), 200)).isEqualTo(accuse);
		verifierRefus("envoyerBuzz", buzzer(soiree, lea, mancheId, etape1, UUID.randomUUID()), 409, "BUZZ_NON_AUTORISE");

		// Sam reprend la main ; son brouillon est jugé à l'échéance et termine le Morceau.
		JsonNode buzzSam = verifier("envoyerBuzz", buzzer(soiree, sam, mancheId, etape1, UUID.randomUUID()), 200);
		assertThat(buzzSam.path("rang").asInt()).isEqualTo(2);
		verifier("deposerBrouillonProposition", envoyer(HttpMethod.PUT, proposition + "/brouillon", sam.jeton(),
				Map.of("numero", 1, "titre", premier.titre().toUpperCase(), "artiste", ""), null), 204);
		horloge.avancer(Duration.ofSeconds(20));
		verifierRefus("validerProposition", envoyer(HttpMethod.POST, proposition + "/proposition", sam.jeton(),
				Map.of("envoiId", UUID.randomUUID(), "titre", premier.titre(), "artiste", ""), null), 409, "HORS_DELAI");
		JsonNode console = capture(soiree, "console", soiree.animateur()).path("vue");
		assertThat(console.path("blindtest").path("phase").asString()).isEqualTo("REVELATION");
		assertThat(console.path("blindtest").path("joueursBloques").get(0).asString()).isEqualTo(lea.id().toString());
		assertThat(console.path("blindtest").path("partiesTrouvees")).hasSize(2);
		assertThat(points(console, "Sam")).isEqualTo(70);
		assertThat(points(console, "Léa")).isEqualTo(30);

		// Morceau 2 : PASSER_ETAPE pendant la Révélation ; un Buzz parti pendant le Morceau 1 est périmé.
		verifier("piloterManche", piloter(soiree, mancheId, "PASSER_ETAPE", etape1), 200);
		JsonNode second = attendrePhase(soiree, "EXTRAIT", 2);
		assertThat(second.path("etapeId").asString()).isNotEqualTo(etape1);
		verifierRefus("envoyerBuzz", buzzer(soiree, lea, mancheId, etape1, UUID.randomUUID()), 409, "COMMANDE_PERIMEE");
		assertThat(second.path("partiesTrouvees")).isEmpty();

		// Chaque vue de salle publiée respecte le contrat et ne dit rien du Morceau avant sa Révélation.
		List<String> types = new ArrayList<>();
		for (JsonNode evenement = salle.poll(2, TimeUnit.SECONDS); evenement != null; evenement = salle.poll(200, TimeUnit.MILLISECONDS)) {
			types.add(evenement.path("type").asString());
			verifierVueSalle(evenement.path("donnees"));
		}
		assertThat(types).containsSubsequence("manche-demarree", "morceau-ouvert", "buzz-recu", "proposition-jugee",
				"buzz-recu", "morceau-revele", "morceau-en-preparation", "morceau-ouvert");
		assertThat(types).doesNotContain("brouillon-depose");
	}

	@Test
	void apresLaFinDeLaMancheBrouillonEtPropositionSontPerimes() throws Exception {
		Soiree soiree = ouvrirSoiree("MANUEL");
		Joueur lea = rejoindre(soiree, "Léa");
		String mancheId = ajouterBlindTest(soiree);
		lancer(soiree);
		String etape = attendrePhase(soiree, "EXTRAIT", 1).path("etapeId").asString();
		verifier("envoyerBuzz", buzzer(soiree, lea, mancheId, etape, UUID.randomUUID()), 200);

		verifier("piloterManche", piloter(soiree, mancheId, "TERMINER", etape), 200);

		// Comme le Buzz : un Morceau qui n'est plus affiché passe avant l'état de la Manche (contrat, ordre des refus).
		String proposition = soiree.route() + "/manches/" + mancheId + "/morceaux/" + etape;
		verifierRefus("deposerBrouillonProposition", envoyer(HttpMethod.PUT, proposition + "/brouillon", lea.jeton(),
				Map.of("numero", 1, "titre", "Halo", "artiste", ""), null), 409, "COMMANDE_PERIMEE");
		verifierRefus("validerProposition", envoyer(HttpMethod.POST, proposition + "/proposition", lea.jeton(),
				Map.of("envoiId", UUID.randomUUID(), "titre", "Halo", "artiste", ""), null), 409, "COMMANDE_PERIMEE");
		verifierRefus("envoyerBuzz", buzzer(soiree, lea, mancheId, etape, UUID.randomUUID()), 409, "COMMANDE_PERIMEE");
		// L'Animateur, lui, pilote une Manche terminée : l'état reste le motif du refus.
		verifierRefus("piloterManche", piloter(soiree, mancheId, "PASSER_ETAPE", etape), 409, "ETAT_INCOMPATIBLE");
	}

	// --- Deezer ---

	@Test
	void deezerEnPanneALaCreationRendUn503SansAjouterDeManche() throws Exception {
		Soiree soiree = ouvrirSoiree("MANUEL");
		catalogue.enPanne = true;

		verifierRefus("ajouterManche", envoyer(HttpMethod.POST, soiree.route() + "/manches", soiree.animateur(),
				Map.of("mode", "BLIND_TEST", "reglages", Map.of("source", Map.of("type", "GENRE", "idDeezer", 132),
						"nombreMorceaux", 2)), null), 503, "SOURCE_MUSICALE_INDISPONIBLE");

		assertThat(capture(soiree, "console", soiree.animateur()).path("vue").path("programme")).isEmpty();
	}

	@Test
	void unProgrammeFigeRefuseLAjoutAvantDeConsulterDeezer() throws Exception {
		Soiree soiree = ouvrirSoiree("MANUEL");
		rejoindre(soiree, "Léa");
		ajouterBlindTest(soiree);
		lancer(soiree);
		catalogue.enPanne = true;

		verifierRefus("ajouterManche", envoyer(HttpMethod.POST, soiree.route() + "/manches", soiree.animateur(),
				Map.of("mode", "BLIND_TEST", "reglages", Map.of("source", Map.of("type", "GENRE", "idDeezer", 132),
						"nombreMorceaux", 2)), null), 409, "ETAT_INCOMPATIBLE");
	}

	@Test
	void unMorceauSansExtraitEstSauteEtLaMancheSeTermineApresLeDernier() throws Exception {
		Soiree soiree = ouvrirSoiree("MANUEL");
		rejoindre(soiree, "Léa");
		ajouterBlindTest(soiree);
		catalogue.sansExtrait.addAll(Set.of(HALO.idDeezer(), GET_LUCKY.idDeezer()));
		var salle = abonner(soiree, "salle", soiree.cleEcran(), Canaux.salle(soiree.id()));

		lancer(soiree);

		await().atMost(Duration.ofSeconds(5)).until(() -> capture(soiree, "salle", soiree.cleEcran())
				.path("vue").path("mancheActive").isNull());
		List<String> types = new ArrayList<>();
		for (JsonNode evenement = salle.poll(2, TimeUnit.SECONDS); evenement != null; evenement = salle.poll(200, TimeUnit.MILLISECONDS)) {
			types.add(evenement.path("type").asString());
		}
		assertThat(types).containsSubsequence("manche-demarree", "morceau-saute", "manche-terminee");
	}

	@Test
	void unDeezerLentAuPassageAutomatiqueNeBloqueRienEtUnMorceauPasseAbandonneSonLien() throws Exception {
		Soiree soiree = ouvrirSoiree("AUTOMATIQUE");
		rejoindre(soiree, "Léa");
		String mancheId = ajouterBlindTest(soiree);
		lancer(soiree);
		attendrePhase(soiree, "EXTRAIT", 1);
		horloge.avancer(Duration.ofSeconds(30));
		assertThat(bloc(soiree).path("phase").asString()).isEqualTo("REVELATION");

		// Le minuteur de 8 s marque le Morceau 2 comme attendu ; Deezer tarde à rendre son lien.
		catalogue.retenue = new CountDownLatch(1);
		int demandes = catalogue.liensDemandes.get();
		horloge.avancer(Duration.ofSeconds(8));
		await().atMost(Duration.ofSeconds(3)).until(() -> catalogue.liensDemandes.get() > demandes);
		JsonNode enPreparation = bloc(soiree);
		assertThat(enPreparation.path("phase").asString()).isEqualTo("PREPARATION");
		assertThat(enPreparation.path("numero").asInt()).isEqualTo(2);

		// Pendant l'attente, captures et commandes passent : l'Animateur révèle le Morceau 2 à la main.
		verifier("piloterManche", piloter(soiree, mancheId, "PASSER_ETAPE", enPreparation.path("etapeId").asString()), 200);
		assertThat(bloc(soiree).path("phase").asString()).isEqualTo("REVELATION");

		int rendus = catalogue.liensRendus.get();
		catalogue.retenue.countDown();
		await().atMost(Duration.ofSeconds(3)).until(() -> catalogue.liensRendus.get() > rendus);
		await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
			JsonNode apres = bloc(soiree);
			assertThat(apres.path("phase").asString()).isEqualTo("REVELATION");
			assertThat(apres.path("lecture").isNull()).isTrue();
		});
	}

	@Test
	void unePanneImpreviseeDeLaPreparationEstReessayeeParLHorloge() throws Exception {
		Soiree soiree = ouvrirSoiree("MANUEL");
		rejoindre(soiree, "Léa");
		var isoles = catalogue.isoler(1);
		ajouterBlindTest(soiree, isoles.playlistId());

		lancer(soiree);

		// La demande échoue sans conclure ; tant que le temps ne passe pas, la Manche attend son essai suivant.
		await().atMost(Duration.ofSeconds(3)).until(() -> isoles.demandes().get() > 0);
		// Aucun nouvel essai sans l'Horloge : le compteur, lu sans requête, reste à un pendant une demi-seconde.
		await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3)).until(() -> isoles.demandes().get() == 1);
		await().atMost(Duration.ofSeconds(5)).until(() -> "PREPARATION".equals(bloc(soiree).path("phase").asString()));
		// Seule l'Horloge relance : aucune capture ne lit le déroulement (ADR 0004).
		laisserPasserLesEssais(soiree, 1);
		assertThat(isoles.demandes().get()).isEqualTo(2);
	}

	@Test
	void unMorceauDontLaPreparationEchoueTroisFoisEstSauteSansFigerLaManche() throws Exception {
		Soiree soiree = ouvrirSoiree("MANUEL");
		rejoindre(soiree, "Léa");
		var isoles = catalogue.isoler(ServiceBlindTest.ECHECS_AVANT_SAUT);
		ajouterBlindTest(soiree, isoles.playlistId());

		lancer(soiree);

		// Le Morceau 1 est sauté sans nouvelle demande de lien ; le Morceau 2 se prépare normalement.
		laisserPasserLesEssais(soiree, 2);
		assertThat(isoles.demandes().get()).isEqualTo(ServiceBlindTest.ECHECS_AVANT_SAUT + 1);
	}

	/** Fait passer le temps par pas d'un essai, comme une vraie horloge, jusqu'à l'extrait du Morceau attendu. */
	private void laisserPasserLesEssais(Soiree soiree, int numero) {
		await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(10)).until(() -> {
			JsonNode bloc = bloc(soiree);
			if ("EXTRAIT".equals(bloc.path("phase").asString()) && bloc.path("numero").asInt() == numero) {
				return true;
			}
			horloge.avancer(ServiceBlindTest.DELAI_NOUVEL_ESSAI);
			return false;
		});
	}

	// --- Restauration ---

	@Test
	void uneEvictionEnPleineSaisieRestaureLaMemeVueEtReprogrammeLEcheance() throws Exception {
		Soiree soiree = ouvrirSoiree("MANUEL");
		Joueur lea = rejoindre(soiree, "Léa");
		rejoindre(soiree, "Sam");
		String mancheId = ajouterBlindTest(soiree);
		lancer(soiree);
		String etape = attendrePhase(soiree, "EXTRAIT", 1).path("etapeId").asString();
		Morceau premier = morceauRevele(soiree);
		horloge.avancer(Duration.ofSeconds(4));
		verifier("envoyerBuzz", buzzer(soiree, lea, mancheId, etape, UUID.randomUUID()), 200);
		verifier("deposerBrouillonProposition", envoyer(HttpMethod.PUT, soiree.route() + "/manches/" + mancheId
				+ "/morceaux/" + etape + "/brouillon", lea.jeton(),
				Map.of("numero", 1, "titre", premier.titre(), "artiste", premier.artiste()), null), 204);
		horloge.avancer(Duration.ofSeconds(5));
		UUID manche = UUID.fromString(mancheId);
		var avant = (InstantaneBlindTest) service.instantane(soiree.id(), manche);
		Destination manetteLea = Destination.joueur(lea.id());

		// Comme après un redémarrage : la capture reconstruit la vue par le fournisseur, qui restaure le déroulement.
		registre.retirer(manche);
		var apres = (InstantaneBlindTest) service.instantane(soiree.id(), manche);

		assertThat(apres).isEqualTo(avant);
		assertThat(VuesBlindTest.projeter(manche, apres, manetteLea))
				.isEqualTo(VuesBlindTest.projeter(manche, avant, manetteLea));
		horloge.avancer(Duration.ofSeconds(15));
		JsonNode console = capture(soiree, "console", soiree.animateur()).path("vue");
		assertThat(console.path("blindtest").path("phase").asString()).isEqualTo("REVELATION");
		assertThat(points(console, "Léa")).isEqualTo(100);
	}

	// --- Outils ---

	private JsonNode attendrePhase(Soiree soiree, String phase, int numero) throws Exception {
		await().atMost(Duration.ofSeconds(5)).until(() -> {
			JsonNode bloc = bloc(soiree);
			return phase.equals(bloc.path("phase").asString()) && bloc.path("numero").asInt() == numero;
		});
		return bloc(soiree);
	}

	private JsonNode bloc(Soiree soiree) throws Exception {
		return capture(soiree, "salle", soiree.cleEcran()).path("vue").path("blindtest");
	}

	/** La Console voit la réponse attendue : le test en tire le Morceau courant, tiré au hasard. */
	private Morceau morceauRevele(Soiree soiree) throws Exception {
		String titre = capture(soiree, "console", soiree.animateur()).path("vue").path("blindtest").path("reponse")
				.path("titre").asString();
		return HALO.titre().equals(titre) ? HALO : GET_LUCKY;
	}

	private static long points(JsonNode vue, String nom) {
		for (JsonNode ligne : vue.path("classement")) {
			if (nom.equals(ligne.path("nom").asString())) { return ligne.path("points").asLong(); }
		}
		return 0;
	}

	private JsonNode capture(Soiree soiree, String ecran, String secret) throws Exception {
		String operation = switch (ecran) {
			case "salle" -> "retrouverEtatSalle";
			case "console" -> "retrouverEtatConsole";
			default -> "retrouverEtatManette";
		};
		JsonNode capture = verifier(operation, envoyer(HttpMethod.GET, soiree.route() + "/etat/" + ecran, secret, null, null), 200);
		if ("salle".equals(ecran)) { verifierVueSalle(capture.path("vue")); }
		return capture;
	}

	/** La vue de salle est lisible par un Joueur : rien du Morceau courant avant sa Révélation. */
	private void verifierVueSalle(JsonNode vue) throws Exception {
		ObjectNode capture = json.createObjectNode();
		capture.put("revision", 0);
		capture.put("heureServeur", horloge.maintenant().toString());
		capture.put("abonnementId", UUID.randomUUID().toString());
		capture.set("vue", vue);
		ContratApi.verifier(CONTRAT, "retrouverEtatSalle", 200, MediaType.APPLICATION_JSON_VALUE, capture.toString());
		JsonNode bloc = vue.path("blindtest");
		if (bloc.isObject() && !"REVELATION".equals(bloc.path("phase").asString())) {
			String texte = bloc.toString();
			for (Morceau morceau : List.of(HALO, GET_LUCKY)) {
				assertThat(texte).doesNotContain(String.valueOf(morceau.idDeezer()) + "\"", "\"" + morceau.idDeezer());
				if (bloc.path("partiesTrouvees").isEmpty()) {
					assertThat(texte).doesNotContain(morceau.titre(), morceau.artiste());
				}
			}
		}
	}

	private String ajouterBlindTest(Soiree soiree) throws Exception {
		return ajouterBlindTest(soiree, 1);
	}

	private String ajouterBlindTest(Soiree soiree, long playlistId) throws Exception {
		return verifier("ajouterManche", envoyer(HttpMethod.POST, soiree.route() + "/manches", soiree.animateur(),
				Map.of("mode", "BLIND_TEST", "reglages", Map.of("source", Map.of("type", "PLAYLIST", "idDeezer", playlistId),
						"nombreMorceaux", 2)), null), 201).path("id").asString();
	}

	private void lancer(Soiree soiree) throws Exception {
		verifier("piloterSoiree", envoyer(HttpMethod.POST, soiree.route() + "/commandes", soiree.animateur(),
				Map.of("action", "LANCER"), UUID.randomUUID()), 200);
	}

	private ResponseEntity<String> piloter(Soiree soiree, String mancheId, String action, String etapeId) {
		return envoyer(HttpMethod.POST, soiree.route() + "/manches/" + mancheId + "/commandes", soiree.animateur(),
				Map.of("action", action, "etapeId", etapeId), UUID.randomUUID());
	}

	private ResponseEntity<String> buzzer(Soiree soiree, Joueur joueur, String mancheId, String etapeId, UUID envoi) {
		return envoyer(HttpMethod.POST, soiree.route() + "/buzz", joueur.jeton(),
				Map.of("envoiId", envoi, "mancheId", mancheId, "etapeId", etapeId), null);
	}

	private Soiree ouvrirSoiree(String pilotage) throws Exception {
		JsonNode creation = verifier("creerSoiree", envoyer(HttpMethod.POST, "/api/soirees", null,
				Map.of("titre", "Blind test du mardi", "format", "SOLO", "pilotage", pilotage), null), 201);
		UUID id = UUID.fromString(creation.path("id").asString());
		String animateur = "demo:ANIMATEUR:" + id + ":" + UUID.randomUUID();
		String route = "/api/soirees/" + id;
		JsonNode ouverte = verifier("ouvrirSoiree", envoyer(HttpMethod.POST, route + "/ouverture", animateur,
				null, UUID.randomUUID()), 200);
		JsonNode ecran = verifier("creerAccesEcran", envoyer(HttpMethod.POST, route + "/acces-ecran", animateur,
				null, UUID.randomUUID()), 200);
		return new Soiree(id, route, ouverte.path("codeAcces").asString(), animateur, ecran.path("cleEcran").asString());
	}

	private Joueur rejoindre(Soiree soiree, String pseudo) throws Exception {
		JsonNode arrivee = verifier("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre", null,
				Map.of("codeAcces", soiree.code(), "pseudo", pseudo), null), 201);
		return new Joueur(UUID.fromString(arrivee.path("joueur").path("id").asString()),
				arrivee.path("jetonReconnexion").asString());
	}

	private BlockingQueue<JsonNode> abonner(Soiree soiree, String ecran, String secret, String destination) throws Exception {
		stomp.setMessageConverter(new JacksonJsonMessageConverter());
		StompHeaders connexion = new StompHeaders();
		connexion.set("Authorization", "Bearer " + secret);
		StompSession session = stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
				connexion, new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
		sessions.add(session);
		StompHeaders abonnement = new StompHeaders();
		abonnement.setDestination(destination);
		abonnement.setId(UUID.randomUUID().toString());
		BlockingQueue<JsonNode> recus = new ArrayBlockingQueue<>(256);
		session.subscribe(abonnement, new StompFrameHandler() {
			@Override public Type getPayloadType(StompHeaders entetes) { return JsonNode.class; }
			@Override public void handleFrame(StompHeaders entetes, Object contenu) { recus.add((JsonNode) contenu); }
		});
		String capture = soiree.route() + "/etat/" + ecran + "?abonnementId=" + abonnement.getId();
		await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(3))
				.until(() -> envoyer(HttpMethod.GET, capture, secret, null, null).getStatusCode().value() == 200);
		return recus;
	}

	private static RestClient clientHttp(HttpClient client) {
		var fabrique = new JdkClientHttpRequestFactory(client);
		fabrique.setReadTimeout(Duration.ofSeconds(15));
		return RestClient.builder().requestFactory(fabrique).build();
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, String secret, Object corps, UUID cle) {
		var requete = http.method(methode).uri("http://localhost:" + port + route);
		if (secret != null) { requete.headers(entetes -> entetes.setBearerAuth(secret)); }
		if (cle != null) { requete.header("Idempotency-Key", cle.toString()).header("X-CSRF-TOKEN", "demonstration"); }
		if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
		return requete.retrieve().onStatus(statut -> statut.isError(), (q, r) -> { }).toEntity(String.class);
	}

	private JsonNode verifier(String operation, ResponseEntity<String> reponse, int statut) throws Exception {
		assertThat(reponse.getStatusCode().value()).as("%s : %s", operation, reponse.getBody()).isEqualTo(statut);
		ContratApi.verifier(CONTRAT, operation, reponse);
		return reponse.getBody() == null ? json.nullNode() : json.readTree(reponse.getBody());
	}

	private void verifierRefus(String operation, ResponseEntity<String> reponse, int statut, String code) throws Exception {
		assertThat(verifier(operation, reponse, statut).path("code").asString()).isEqualTo(code);
	}

	private record Soiree(UUID id, String route, String code, String animateur, String cleEcran) { }
	private record Joueur(UUID id, String jeton) { }

}
