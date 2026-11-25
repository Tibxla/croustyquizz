package fr.croustyquizz.manche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.soiree.Format;
import fr.croustyquizz.soiree.Pilotage;
import fr.croustyquizz.soiree.ServiceDesAcces;
import fr.croustyquizz.soiree.ServiceSoiree;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Banc de bout en bout du Pilotage Automatique (#123) : PostgreSQL, orchestrateur de Soirée de #183, Manches
 * factices d'une étape et horloge factice. Les vues sont lues par HTTP et validées contre le contrat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, SocleMancheJpaTest.Configuration.class})
class PilotageAutomatiqueTest {
	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final Duration TRANSITION = Duration.ofSeconds(15);

	@LocalServerPort private int port;
	@Autowired @Qualifier("serviceFacticeJpa") private ServiceDeManche<?, ?> service;
	@Autowired private SocleMancheJpaTest.PannesDuServiceFactice pannes;
	@Autowired private RegistreDesDeroulements registre;
	@Autowired private HorlogeFactice horloge;
	@Autowired private PilotageAutomatique pilotageAutomatique;
	@Autowired private PilotageDeSoireeDemonstration pilotageDemonstration;
	@Autowired private ServiceSoiree soirees;
	@Autowired private ServiceDesAcces acces;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private JsonMapper json;

	/** L'horloge système a des nanosecondes, PostgreSQL des microsecondes : l'échéance doit survivre à la relecture. */
	@BeforeEach
	void decalerSousLaSeconde() {
		horloge.avancer(Duration.ofNanos(123_456_789));
	}

	/** Le banc est partagé : les autres classes de test attendent une horloge à la seconde ronde. */
	@AfterEach
	void retablir() {
		pannes.demarrage.set(false);
		pannes.enregistrement.set(false);
		horloge.avancer(Duration.ofNanos(1_000_000_000L - horloge.maintenant().getNano()));
	}

	@Test
	void deuxManchesSEnchainentApresLaTransitionPuisLaSoireeSeTermineSeule() throws Exception {
		var banc = soiree(Pilotage.AUTOMATIQUE, 10, 10);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(10));
		assertThat(etatManche(banc.manche(0))).isEqualTo("TERMINEE");
		Instant fin = echeance();
		for (var vue : vues(banc)) {
			assertThat(vue.path("mancheActive").isNull()).isTrue();
			assertThat(Instant.parse(vue.at("/transitionManche/finPrevueLe").asString())).isEqualTo(fin);
			assertThat(vue.at("/transitionManche/modeSuivant").asString()).isEqualTo("QUIZ");
		}

		horloge.avancer(TRANSITION.minusSeconds(1));
		assertThat(etatManche(banc.manche(1))).isEqualTo("PRETE");
		horloge.avancer(Duration.ofSeconds(1));
		assertThat(etatManche(banc.manche(1))).isEqualTo("EN_COURS");
		for (var vue : vues(banc)) {
			assertThat(vue.path("transitionManche").isNull()).isTrue();
			assertThat(vue.at("/mancheActive/id").asString()).isEqualTo(banc.manche(1).toString());
		}

		horloge.avancer(Duration.ofSeconds(10));
		Instant finDeSoiree = echeance();
		for (var vue : vues(banc)) {
			assertThat(Instant.parse(vue.at("/transitionManche/finPrevueLe").asString())).isEqualTo(finDeSoiree);
			assertThat(vue.at("/transitionManche/modeSuivant").isNull()).isTrue();
		}
		horloge.avancer(TRANSITION);
		assertThat(etatSoiree(banc)).isEqualTo("TERMINEE");
		for (var vue : vues(banc)) {
			assertThat(vue.at("/soiree/etat").asString()).isEqualTo("TERMINEE");
			assertThat(vue.path("transitionManche").isNull()).isTrue();
		}
		assertThat(transitions(banc)).isZero();
	}

	@Test
	void terminerLaMancheEnAutomatiqueOuvreLaTransitionCommeUneFinNaturelle() throws Exception {
		var banc = soiree(Pilotage.AUTOMATIQUE, 10, 10);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(3));
		piloterManche(banc, banc.manche(0), "TERMINER", 200);
		var console = capturer(banc, "console").path("vue");
		assertThat(Instant.parse(console.at("/transitionManche/finPrevueLe").asString()))
				.isEqualTo(echeance());
		horloge.avancer(TRANSITION);
		assertThat(etatManche(banc.manche(1))).isEqualTo("EN_COURS");
	}

	@Test
	void terminerLaSoireePendantUneMancheNOuvreAucuneTransition() throws Exception {
		var banc = soiree(Pilotage.AUTOMATIQUE, 10, 10);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(3));
		piloterSoiree(banc, "TERMINER", 200);
		assertThat(etatSoiree(banc)).isEqualTo("TERMINEE");
		assertThat(transitions(banc)).isZero();
		for (var vue : vues(banc)) {
			assertThat(vue.path("transitionManche").isNull()).isTrue();
		}
		horloge.avancer(Duration.ofSeconds(30));
		assertThat(etatManche(banc.manche(1))).isEqualTo("PRETE");
	}

	@Test
	void mancheSuivanteAbregeLaTransitionEtLeRappelNeFaitRien() throws Exception {
		var banc = soiree(Pilotage.AUTOMATIQUE, 10, 60);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(10));
		horloge.avancer(Duration.ofSeconds(5));
		piloterSoiree(banc, "MANCHE_SUIVANTE", 200);
		assertThat(etatManche(banc.manche(1))).isEqualTo("EN_COURS");
		for (var vue : vues(banc)) {
			assertThat(vue.path("transitionManche").isNull()).isTrue();
		}
		long revision = capturer(banc, "console").path("revision").asLong();
		horloge.avancer(Duration.ofSeconds(10));
		assertThat(capturer(banc, "console").path("revision").asLong()).as("le rappel ne republie rien").isEqualTo(revision);
		assertThat(etatManche(banc.manche(1))).isEqualTo("EN_COURS");
		assertThat(etatSoiree(banc)).isEqualTo("EN_COURS");
		assertThat(transitions(banc)).isZero();
	}

	@Test
	void terminerLaSoireePendantLaTransitionLaRetireEtLeRappelNeFaitRien() throws Exception {
		var banc = soiree(Pilotage.AUTOMATIQUE, 10, 10);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(10));
		horloge.avancer(Duration.ofSeconds(5));
		piloterSoiree(banc, "TERMINER", 200);
		assertThat(etatSoiree(banc)).isEqualTo("TERMINEE");
		for (var vue : vues(banc)) {
			assertThat(vue.path("transitionManche").isNull()).isTrue();
		}
		long revision = capturer(banc, "console").path("revision").asLong();
		horloge.avancer(TRANSITION);
		assertThat(capturer(banc, "console").path("revision").asLong()).isEqualTo(revision);
		assertThat(etatManche(banc.manche(1))).isEqualTo("PRETE");
		assertThat(transitions(banc)).isZero();
	}

	@Test
	void apresLaDerniereMancheLaTransitionAnnonceLaFinPuisTermineLaSoiree() throws Exception {
		var banc = soiree(Pilotage.AUTOMATIQUE, 10);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(10));
		assertThat(capturer(banc, "salle").at("/vue/transitionManche/modeSuivant").isNull()).isTrue();
		assertThat(etatSoiree(banc)).isEqualTo("EN_COURS");
		horloge.avancer(TRANSITION);
		assertThat(etatSoiree(banc)).isEqualTo("TERMINEE");
		assertThat(capturer(banc, "salle").at("/vue/transitionManche").isNull()).isTrue();
	}

	@Test
	void unRetourArriereDeLaFinDeMancheNEnregistreAucuneTransition() throws Exception {
		var banc = soiree(Pilotage.AUTOMATIQUE, 10, 10);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(3));
		var terminer = new InterventionDeManche(InterventionDeManche.Action.TERMINER, null);
		var envoi = new EnvoiDeCommande(new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), banc.soiree()),
				"/commandes", UUID.randomUUID(), terminer);
		pannes.enregistrement.set(true);
		assertThatThrownBy(() -> service.piloter(banc.manche(0), envoi, terminer)).isInstanceOf(IllegalStateException.class);
		pannes.enregistrement.set(false);
		assertThat(etatManche(banc.manche(0))).isEqualTo("EN_COURS");
		assertThat(transitions(banc)).isZero();
		horloge.avancer(TRANSITION);
		assertThat(etatManche(banc.manche(1))).isEqualTo("PRETE");
		assertThat(capturer(banc, "console").at("/vue/transitionManche").isNull()).isTrue();
	}

	@Test
	void unRedemarrageReprendLaTransitionPourLeTempsQuiRestait() throws Exception {
		var banc = soiree(Pilotage.AUTOMATIQUE, 10, 10);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(10));
		horloge.avancer(Duration.ofSeconds(5));
		redemarrer(banc);

		horloge.avancer(Duration.ofSeconds(9));
		assertThat(etatManche(banc.manche(1))).isEqualTo("PRETE");
		assertThat(capturer(banc, "console").at("/vue/transitionManche/modeSuivant").asString()).isEqualTo("QUIZ");
		horloge.avancer(Duration.ofSeconds(1));
		assertThat(etatManche(banc.manche(1))).isEqualTo("EN_COURS");
	}

	@Test
	void uneEcheancePasseePendantLArretSExecuteAuPremierRappel() throws Exception {
		var banc = soiree(Pilotage.AUTOMATIQUE, 10, 10);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(10));
		horloge.oublierLesMinuteurs();
		horloge.avancer(Duration.ofSeconds(40));
		redemarrer(banc);
		assertThat(etatManche(banc.manche(1))).isEqualTo("PRETE");
		horloge.avancer(Duration.ZERO);
		assertThat(etatManche(banc.manche(1))).isEqualTo("EN_COURS");
	}

	@Test
	void enManuelAucuneTransitionNeCommence() throws Exception {
		var banc = soiree(Pilotage.MANUEL, 10, 10);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(10));
		assertThat(etatManche(banc.manche(0))).isEqualTo("TERMINEE");
		assertThat(transitions(banc)).isZero();
		for (var vue : vues(banc)) {
			assertThat(vue.path("transitionManche").isNull()).isTrue();
		}
		horloge.avancer(TRANSITION);
		assertThat(etatManche(banc.manche(1))).isEqualTo("PRETE");
	}

	@Test
	void unEchecDeLEnchainementRepublieLesVuesSansTransitionEtLaisseMancheSuivante() throws Exception {
		var banc = soiree(Pilotage.AUTOMATIQUE, 10, 10);
		piloterSoiree(banc, "LANCER", 200);
		horloge.avancer(Duration.ofSeconds(10));
		long revision = capturer(banc, "console").path("revision").asLong();
		pannes.demarrage.set(true);
		horloge.avancer(TRANSITION);
		pannes.demarrage.set(false);
		assertThat(etatManche(banc.manche(1))).isEqualTo("PRETE");
		assertThat(transitions(banc)).isZero();
		var console = capturer(banc, "console");
		assertThat(console.path("revision").asLong()).isGreaterThan(revision);
		for (var vue : vues(banc)) {
			assertThat(vue.path("transitionManche").isNull()).isTrue();
			assertThat(vue.path("mancheActive").isNull()).isTrue();
		}
		piloterSoiree(banc, "MANCHE_SUIVANTE", 200);
		assertThat(etatManche(banc.manche(1))).isEqualTo("EN_COURS");
	}

	@Test
	void uneTransitionDUneSoireeRedevenueManuelleNEstPasReprogrammee() {
		UUID soiree = UUID.randomUUID();
		pilotageDemonstration.choisir(soiree, true);
		UUID premiere = service.creer(soiree, new ModeFacticeDuSocle.Reglages(1, 10));
		service.creer(soiree, new ModeFacticeDuSocle.Reglages(1, 10));
		service.demarrer(premiere, new EnvoiDeCommande(EnchainementDeSoiree.acteur(soiree), "/demarrer", UUID.randomUUID(),
				Map.of()));
		horloge.avancer(Duration.ofSeconds(10));
		assertThat(transitions(soiree)).isOne();

		// Au redémarrage, une Soirée de démonstration redevient Manuelle (#119).
		pilotageDemonstration.choisir(soiree, false);
		jdbc.update("delete from manche_transition where soiree_id <> ?", soiree);
		horloge.oublierLesMinuteurs();
		registre.retirer(premiere);
		pilotageAutomatique.reprendre();
		assertThat(transitions(soiree)).isZero();
		assertThat(horloge.minuteursEnAttente()).isZero();
	}

	@Test
	void uneSoireeDeDemonstrationEnAutomatiqueDemarreSaMancheSuivante() throws Exception {
		UUID soiree = UUID.randomUUID();
		pilotageDemonstration.choisir(soiree, true);
		UUID premiere = service.creer(soiree, new ModeFacticeDuSocle.Reglages(1, 10));
		UUID seconde = service.creer(soiree, new ModeFacticeDuSocle.Reglages(1, 10));
		service.demarrer(premiere, new EnvoiDeCommande(EnchainementDeSoiree.acteur(soiree), "/demarrer", UUID.randomUUID(),
				Map.of()));
		horloge.avancer(Duration.ofSeconds(10));
		String jeton = "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID();
		var vue = lire(soiree, "/etat/console", jeton, "retrouverEtatConsole").path("vue");
		assertThat(vue.at("/transitionManche/modeSuivant").asString()).isEqualTo("QUIZ");
		horloge.avancer(TRANSITION);
		assertThat(etatManche(seconde)).isEqualTo("EN_COURS");
		assertThat(transitions(soiree)).isZero();
	}

	@Test
	void apresLaDerniereMancheDUneSoireeDeDemonstrationLaTransitionDisparait() throws Exception {
		UUID soiree = UUID.randomUUID();
		pilotageDemonstration.choisir(soiree, true);
		UUID seule = service.creer(soiree, new ModeFacticeDuSocle.Reglages(1, 10));
		service.demarrer(seule, new EnvoiDeCommande(EnchainementDeSoiree.acteur(soiree), "/demarrer", UUID.randomUUID(),
				Map.of()));
		horloge.avancer(Duration.ofSeconds(10));
		String jeton = "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID();
		assertThat(lire(soiree, "/etat/console", jeton, "retrouverEtatConsole").at("/vue/transitionManche/modeSuivant")
				.isNull()).isTrue();
		horloge.avancer(TRANSITION);
		assertThat(lire(soiree, "/etat/console", jeton, "retrouverEtatConsole").at("/vue/transitionManche").isNull()).isTrue();
		assertThat(transitions(soiree)).isZero();
	}

	private Instant echeance() {
		return horloge.maintenant().plus(TRANSITION).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
	}

	/** Simule un arrêt : minuteurs et déroulements perdus, puis l'événement de démarrage de l'application. */
	private void redemarrer(Banc banc) {
		horloge.oublierLesMinuteurs();
		banc.manches().forEach(registre::retirer);
		pilotageAutomatique.reprendre();
	}

	private Banc soiree(Pilotage pilotage, int... dureesSecondes) {
		UUID soiree = soirees.creer("Soirée enchaînée", Format.SOLO, pilotage).getId();
		String code = soirees.ouvrir(soiree).getCodeAcces();
		String jetonJoueur = soirees.rejoindre(code, "Léa").jeton().valeur();
		String cleEcran = acces.ouvrirEcran(soiree).cle();
		List<UUID> manches = new ArrayList<>();
		for (int duree : dureesSecondes) {
			manches.add(service.creer(soiree, new ModeFacticeDuSocle.Reglages(1, duree)));
		}
		return new Banc(soiree, manches, "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID(), cleEcran, jetonJoueur);
	}

	private void piloterSoiree(Banc banc, String action, int statut) throws Exception {
		commander("piloterSoiree", "/api/soirees/" + banc.soiree() + "/commandes", banc, action, statut);
	}

	private void piloterManche(Banc banc, UUID manche, String action, int statut) throws Exception {
		commander("piloterManche", "/api/soirees/" + banc.soiree() + "/manches/" + manche + "/commandes", banc, action, statut);
	}

	private void commander(String operation, String route, Banc banc, String action, int statut) throws Exception {
		var reponse = RestClient.create("http://localhost:" + port).post().uri(route)
				.header("Authorization", "Bearer " + banc.animateur())
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.header("X-CSRF-TOKEN", "csrf_demo_000000000")
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("action", action))
				.retrieve().onStatus(code -> code.isError(), (demande, resultat) -> { }).toEntity(String.class);
		ContratApi.verifier(CONTRAT, operation, reponse);
		assertThat(reponse.getStatusCode().value()).as(operation + " " + action).isEqualTo(statut);
	}

	private List<JsonNode> vues(Banc banc) throws Exception {
		return List.of(capturer(banc, "salle").path("vue"), capturer(banc, "console").path("vue"),
				capturer(banc, "manette").path("vue"));
	}

	private JsonNode capturer(Banc banc, String destination) throws Exception {
		String jeton = switch (destination) {
			case "salle" -> banc.cleEcran();
			case "console" -> banc.animateur();
			default -> banc.jetonJoueur();
		};
		String operation = "retrouverEtat" + Character.toUpperCase(destination.charAt(0)) + destination.substring(1);
		return lire(banc.soiree(), "/etat/" + destination, jeton, operation);
	}

	private JsonNode lire(UUID soiree, String chemin, String jeton, String operation) throws Exception {
		var reponse = RestClient.create("http://localhost:" + port).method(HttpMethod.GET)
				.uri("/api/soirees/" + soiree + chemin)
				.header("Authorization", "Bearer " + jeton)
				.retrieve().onStatus(statut -> statut.isError(), (requete, refus) -> { }).toEntity(String.class);
		ContratApi.verifier(CONTRAT, operation, reponse);
		assertThat(reponse.getStatusCode().value()).as(operation).isEqualTo(200);
		return json.readTree(reponse.getBody());
	}

	private String etatManche(UUID manche) {
		return jdbc.queryForObject("select etat from manche_deroulement where manche_id = ?", String.class, manche);
	}

	private String etatSoiree(Banc banc) {
		return jdbc.queryForObject("select etat from soiree where id = ?", String.class, banc.soiree());
	}

	private int transitions(Banc banc) {
		return transitions(banc.soiree());
	}

	private int transitions(UUID soiree) {
		return jdbc.queryForObject("select count(*) from manche_transition where soiree_id = ?", Integer.class, soiree);
	}

	private record Banc(UUID soiree, List<UUID> manches, String animateur, String cleEcran, String jetonJoueur) {
		UUID manche(int ordre) { return manches.get(ordre); }
	}
}
