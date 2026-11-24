package fr.croustyquizz.manche;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import fr.croustyquizz.ContratApi;
import fr.croustyquizz.tempsreel.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Serveur HTTP réel, service et diffuseur réels ; seule la base et l'identité sont factices. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SocleHttpTest.Configuration.class,
		properties = "spring.docker.compose.enabled=false")
@ActiveProfiles({"dev", "socle-http-test"})
class SocleHttpTest {
	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String PROTOTYPE = "docs/api/prototype/openapi.yaml";
	private static final String CHOISIR_PILOTAGE = "choisirPilotageDeTest";
	@LocalServerPort private int port;
	@Autowired private JsonMapper json;
	@Autowired private StockageMancheFactice stockage;
	@Autowired private RegistreDesDeroulements registre;
	@Autowired private ServiceDeManche<?, ?> service;
	@Autowired private PilotageDeSoiree pilotage;
	@Autowired private ModeFacticeDuSocle mode;

	@Test
	void choisirLePilotageAvantLeDemarrageModifieLaCaptureSansPublier() throws Exception {
		UUID soiree = UUID.randomUUID();
		UUID autreSoiree = UUID.randomUUID();
		UUID manche = service.creer(soiree, new ModeFacticeDuSocle.Reglages(3, 20));
		String route = "/api/test/soirees/" + soiree + "/pilotage";
		assertThat(captureConsole(soiree).at("/vue/soiree/pilotage").asString()).isEqualTo("MANUEL");
		for (String pilotage : List.of("AUTOMATIQUE", "MANUEL", "AUTOMATIQUE")) {
			var reponse = envoyer(HttpMethod.PUT, route, soiree, Acteur.Type.ANIMATEUR, Map.of("pilotage", pilotage), null);
			assertThat(reponse.getStatusCode().value()).isEqualTo(200);
			ContratApi.verifier(PROTOTYPE, CHOISIR_PILOTAGE, reponse);
			assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
			assertThat(json.readTree(reponse.getBody()).path("soireeId").asString()).isEqualTo(soiree.toString());
			assertThat(json.readTree(reponse.getBody()).path("pilotage").asString()).isEqualTo(pilotage);
			var capture = captureConsole(soiree);
			assertThat(capture.path("revision").asLong()).isZero();
			assertThat(capture.at("/vue/soiree/pilotage").asString()).isEqualTo(pilotage);
		}
		assertThat(captureConsole(autreSoiree).at("/vue/soiree/pilotage").asString()).isEqualTo("MANUEL");

		var demarrage = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches/" + manche + "/demarrer",
				soiree, Acteur.Type.ANIMATEUR, null, UUID.randomUUID());
		assertThat(demarrage.getStatusCode().value()).isEqualTo(200);
		ContratApi.verifier(PROTOTYPE, "demarrerMancheDeTest", demarrage);
		var refus = envoyer(HttpMethod.PUT, route, soiree, Acteur.Type.ANIMATEUR, Map.of("pilotage", "MANUEL"), null);
		verifierRefusPilotage(refus, 409, "ETAT_INCOMPATIBLE");
		assertThat(json.readTree(refus.getBody()).path("detail").asString())
				.isEqualTo("La Soirée a commencé : son Pilotage ne change plus.");
		assertThat(pilotage.estAutomatique(soiree)).isTrue();
		var capture = captureConsole(soiree);
		assertThat(capture.path("revision").asLong()).isOne();
		assertThat(capture.at("/vue/soiree/pilotage").asString()).isEqualTo("AUTOMATIQUE");
	}

	@Test
	void lesManchesLuesAvantLeChoixRecoiventLeNouveauPilotage() throws Exception {
		UUID soiree = UUID.randomUUID();
		var reglages = new ModeFacticeDuSocle.Reglages(3, 20);
		UUID premiere = service.creer(soiree, reglages);
		UUID suivante = service.creer(soiree, reglages);
		service.instantane(soiree, premiere);
		assertThat(mode.dernierContexte.pilotageAutomatique()).isFalse();
		service.instantane(soiree, suivante);
		assertThat(mode.dernierContexte.pilotageAutomatique()).isFalse();

		String route = "/api/test/soirees/" + soiree + "/pilotage";
		var choix = envoyer(HttpMethod.PUT, route, soiree, Acteur.Type.ANIMATEUR, Map.of("pilotage", "AUTOMATIQUE"), null);
		assertThat(choix.getStatusCode().value()).isEqualTo(200);
		ContratApi.verifier(PROTOTYPE, CHOISIR_PILOTAGE, choix);

		service.instantane(soiree, suivante);
		assertThat(mode.dernierContexte.pilotageAutomatique()).isTrue();
		var demarrage = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches/" + premiere + "/demarrer",
				soiree, Acteur.Type.ANIMATEUR, null, UUID.randomUUID());
		assertThat(demarrage.getStatusCode().value()).isEqualTo(200);
		ContratApi.verifier(PROTOTYPE, "demarrerMancheDeTest", demarrage);
		assertThat(mode.dernierContexte.mancheId()).isEqualTo(premiere);
		assertThat(mode.dernierContexte.pilotageAutomatique()).isTrue();
	}

	@Test
	void choisirLePilotageExigeUnCorpsValideEtLAnimateurDeLaSoiree() throws Exception {
		UUID soiree = UUID.randomUUID();
		String route = "/api/test/soirees/" + soiree + "/pilotage";
		for (var corps : List.of(Map.of(), Map.of("pilotage", "INCONNU"), Map.of("pilotage", "MANUEL", "autre", true))) {
			verifierRefusPilotage(envoyer(HttpMethod.PUT, route, soiree, Acteur.Type.ANIMATEUR, corps, null),
					400, "REQUETE_INVALIDE");
		}
		var corps = Map.of("pilotage", "AUTOMATIQUE");
		verifierRefusPilotage(envoyer(HttpMethod.PUT, route, soiree, null, corps, null), 401, "AUTHENTIFICATION_REQUISE");
		verifierRefusPilotage(envoyer(HttpMethod.PUT, route, soiree, Acteur.Type.JOUEUR, corps, null), 403, "ACCES_REFUSE");
		verifierRefusPilotage(envoyer(HttpMethod.PUT, route, UUID.randomUUID(), Acteur.Type.ANIMATEUR, corps, null),
				404, "RESSOURCE_INTROUVABLE");
		assertThat(captureConsole(soiree).at("/vue/soiree/pilotage").asString()).isEqualTo("MANUEL");
	}

	@Test
	void creationDemarragePilotageEtCapturesRespectentLesContrats() throws Exception {
		UUID soiree = UUID.randomUUID();
		var creation = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches", soiree, Acteur.Type.ANIMATEUR,
				Map.of("mode", "QUIZ", "reglages", Map.of("nombreEtapes", 3, "tempsParEtapeSecondes", 20)), null);
		ContratApi.verifier(PROTOTYPE, "creerMancheDeTest", creation);
		assertThat(creation.getStatusCode().value()).isEqualTo(201);
		UUID manche = UUID.fromString(json.readTree(creation.getBody()).path("mancheId").asString());
		var demarrage = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches/" + manche + "/demarrer", soiree,
				Acteur.Type.ANIMATEUR, null, UUID.randomUUID());
		ContratApi.verifier(PROTOTYPE, "demarrerMancheDeTest", demarrage);
		String route = "/api/soirees/" + soiree + "/manches/" + manche + "/commandes";
		UUID envoi = UUID.randomUUID();
		var pause = envoyer(HttpMethod.POST, route, soiree, Acteur.Type.ANIMATEUR, Map.of("action", "METTRE_EN_PAUSE"), envoi);
		ContratApi.verifier(CONTRAT, "piloterManche", pause);
		assertThat(pause.getStatusCode().value()).isEqualTo(200);
		registre.retirer(manche);
		assertThat(service.instantane(soiree, manche)).extracting("pause").isEqualTo(true);
		var repetition = envoyer(HttpMethod.POST, route, soiree, Acteur.Type.ANIMATEUR, Map.of("action", "METTRE_EN_PAUSE"), envoi);
		ContratApi.verifier(CONTRAT, "piloterManche", repetition);
		assertThat(repetition.getBody()).isEqualTo(pause.getBody());
		for (String destination : List.of("salle", "console", "manette")) {
			Acteur.Type role = destination.equals("manette") ? Acteur.Type.JOUEUR : Acteur.Type.ANIMATEUR;
			var capture = envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/" + destination, soiree, role, null, null);
			ContratApi.verifier(CONTRAT, "retrouverEtat" + Character.toUpperCase(destination.charAt(0)) + destination.substring(1), capture);
			assertThat(json.readTree(capture.getBody()).path("revision").asLong()).isEqualTo(2);
			assertThat(json.readTree(capture.getBody()).at("/vue/mancheActive/etat").asString()).isEqualTo("EN_PAUSE");
			assertThat(json.readTree(capture.getBody()).at("/vue/quiz/phase").asString()).isEqualTo("EN_PAUSE");
		}
	}

	@Test
	void erreursMetierValidationEtTechniquesRestentDistinctes() throws Exception {
		UUID soiree = UUID.randomUUID();
		UUID manche = service.creer(soiree, new ModeFacticeDuSocle.Reglages(3, 20));
		String route = "/api/soirees/" + soiree + "/manches/" + manche + "/commandes";
		for (Object corps : List.of(Map.of(), Map.of("action", "INCONNUE"), Map.of("action", "TERMINER", "acteurId", UUID.randomUUID()))) {
			var reponse = envoyer(HttpMethod.POST, route, soiree, Acteur.Type.ANIMATEUR, corps, UUID.randomUUID());
			assertThat(reponse.getStatusCode().value()).isEqualTo(400);
			ContratApi.verifier(CONTRAT, "piloterManche", reponse);
		}
		var refus = envoyer(HttpMethod.POST, route, soiree, Acteur.Type.ANIMATEUR, Map.of("action", "REPRENDRE"), UUID.randomUUID());
		assertThat(refus.getStatusCode().value()).isEqualTo(409);
		ContratApi.verifier(CONTRAT, "piloterManche", refus);
		stockage.echouerCommit = true;
		var indisponible = envoyer(HttpMethod.POST, route, soiree, Acteur.Type.ANIMATEUR, Map.of("action", "REPRENDRE"), UUID.randomUUID());
		assertThat(indisponible.getStatusCode().value()).isEqualTo(503);
		ContratApi.verifier(CONTRAT, "piloterManche", indisponible);
	}

	@Test
	void uneErreurDeBaseDonne503EtUneErreurInattendueDonne500SansCorps() throws Exception {
		UUID soiree = UUID.randomUUID();
		UUID manche = service.creer(soiree, new ModeFacticeDuSocle.Reglages(3, 20));
		String route = "/api/soirees/" + soiree + "/manches/" + manche + "/commandes";
		stockage.panneLecture = new org.springframework.dao.DataAccessResourceFailureException("Détail privé de la base");
		var indisponible = envoyer(HttpMethod.POST, route, soiree, Acteur.Type.ANIMATEUR, Map.of("action", "TERMINER"), UUID.randomUUID());
		assertThat(indisponible.getStatusCode().value()).isEqualTo(503);
		assertThat(indisponible.getBody()).doesNotContain("Détail privé");
		ContratApi.verifier(CONTRAT, "piloterManche", indisponible);
		stockage.panneLecture = new IllegalStateException("Erreur interne privée");
		var inattendue = envoyer(HttpMethod.POST, route, soiree, Acteur.Type.ANIMATEUR, Map.of("action", "TERMINER"), UUID.randomUUID());
		assertThat(inattendue.getStatusCode().value()).isEqualTo(500);
		assertThat(inattendue.getBody()).isNullOrEmpty();
	}

	@Test
	void capturesExigentIdentitePorteeRoleEtAbonnementDuMemeActeur() throws Exception {
		UUID soiree = UUID.randomUUID();
		String route = "/api/soirees/" + soiree + "/etat/console";
		var anonyme = envoyer(HttpMethod.GET, route, soiree, null, null, null);
		assertThat(anonyme.getStatusCode().value()).isEqualTo(401);
		ContratApi.verifier(CONTRAT, "retrouverEtatConsole", anonyme);
		var joueur = envoyer(HttpMethod.GET, route, soiree, Acteur.Type.JOUEUR, null, null);
		assertThat(joueur.getStatusCode().value()).isEqualTo(403);
		ContratApi.verifier(CONTRAT, "retrouverEtatConsole", joueur);
		var autreSoiree = envoyer(HttpMethod.GET, route, UUID.randomUUID(), Acteur.Type.ANIMATEUR, null, null);
		assertThat(autreSoiree.getStatusCode().value()).isEqualTo(404);
		ContratApi.verifier(CONTRAT, "retrouverEtatConsole", autreSoiree);
		var abonnement = envoyer(HttpMethod.GET, route + "?abonnementId=" + UUID.randomUUID(), soiree, Acteur.Type.ANIMATEUR, null, null);
		assertThat(abonnement.getStatusCode().value()).isEqualTo(409);
		ContratApi.verifier(CONTRAT, "retrouverEtatConsole", abonnement);
	}

	private JsonNode captureConsole(UUID soiree) throws Exception {
		var reponse = envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/console", soiree, Acteur.Type.ANIMATEUR, null, null);
		assertThat(reponse.getStatusCode().value()).isEqualTo(200);
		ContratApi.verifier(CONTRAT, "retrouverEtatConsole", reponse);
		return json.readTree(reponse.getBody());
	}

	private void verifierRefusPilotage(ResponseEntity<String> reponse, int statut, String code) throws Exception {
		assertThat(reponse.getStatusCode().value()).isEqualTo(statut);
		ContratApi.verifier(PROTOTYPE, CHOISIR_PILOTAGE, reponse);
		assertThat(json.readTree(reponse.getBody()).path("code").asString()).isEqualTo(code);
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, UUID soiree, Acteur.Type role, Object corps, UUID envoi) {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
		if (role != null) {
			requete.header("Authorization", "Bearer demo:" + role + ":" + soiree + ":" + FournisseurCommunDemonstration.joueurId(soiree));
		}
		if (envoi != null) { requete.header("Idempotency-Key", envoi.toString()); }
		if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
		return requete.retrieve().onStatus(statut -> statut.isError(), (demande, reponse) -> { }).toEntity(String.class);
	}

	@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
	@org.springframework.context.annotation.Profile("socle-http-test")
	@EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
			"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
			"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
			"org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
	@Import({PilotageMancheControleur.class, ManchesDeTestControleur.class, VuesControleur.class, ErreursHttp.class,
			ConfigurationActeurHttp.class, AssembleurDeVues.class, PublicationDeVues.class, PublicationApresCommit.class,
			VerrousSoiree.class, RegistreDesDeroulements.class, RegistreDesEnvois.class, SocleDeManche.class,
			RegistreServicesDeManche.class, RegistreAbonnements.class, DiffuseurStomp.class, IdentitesFactices.class,
			PilotageDeSoireeDemonstration.class, EtatDeSoireeDemonstration.class})
	static class Configuration {
		@Bean ModeFacticeDuSocle mode() { return new ModeFacticeDuSocle(); }
		@Bean StockageMancheFactice stockage(ModeFacticeDuSocle mode) { return new StockageMancheFactice(mode); }
		@Bean Horloge horloge() { return new HorlogeFactice(); }
		@Bean Clock clock(Horloge horloge) { return Clock.fixed(horloge.maintenant(), ZoneOffset.UTC); }
		@Bean ServiceDeManche<?, ?> service(ModeFacticeDuSocle mode, SocleDeManche socle) { return new ServiceDeManche<>(mode, mode, socle); }
		@Bean FournisseurPartieCommune commun(StockageMancheFactice stockage, JsonMapper json, PilotageDeSoiree pilotage) {
			return new FournisseurCommunDemonstration(stockage, json, pilotage);
		}
		@Bean FournisseurDeBloc bloc(org.springframework.beans.factory.ObjectProvider<ServiceDeManche<?, ?>> service,
				StockageMancheFactice stockage) { return new BlocFacticeDuSocle(service::getObject, stockage); }

		@Bean CaptureVuePrototype prototype() { return id -> new VueSallePrototype(List.of()); }
		@Bean SimpMessagingTemplate messagerie() {
			var canal = new ExecutorSubscribableChannel(); canal.subscribe(message -> { });
			return new SimpMessagingTemplate(canal);
		}
	}
	@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
	static class IdentitesFactices {
		@Bean ResolveurDActeur identites() { return new ResolveurDActeurDemonstration(); }
	}

}
