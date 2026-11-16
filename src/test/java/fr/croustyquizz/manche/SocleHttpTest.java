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
import tools.jackson.databind.json.JsonMapper;

/** Serveur HTTP réel, service et diffuseur réels ; seule la base et l'identité sont factices. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SocleHttpTest.Configuration.class,
		properties = "spring.docker.compose.enabled=false")
@ActiveProfiles({"recette", "socle-http-test"})
class SocleHttpTest {
	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String PROTOTYPE = "docs/api/prototype/openapi.yaml";
	@LocalServerPort private int port;
	@Autowired private JsonMapper json;
	@Autowired private StockageMancheFactice stockage;
	@Autowired private RegistreDesDeroulements registre;
	@Autowired private ServiceDeManche<?, ?> service;

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

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, UUID soiree, Acteur.Type role, Object corps, UUID envoi) {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
		if (role != null) {
			requete.header("Authorization", "Bearer demo:" + role + ":" + soiree + ":" + FournisseurCommunDemonstration.JOUEUR);
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
			RegistreServicesDeManche.class, RegistreAbonnements.class, DiffuseurStomp.class, IdentitesFactices.class})
	static class Configuration {
		@Bean ModeFacticeDuSocle mode() { return new ModeFacticeDuSocle(); }
		@Bean StockageMancheFactice stockage(ModeFacticeDuSocle mode) { return new StockageMancheFactice(mode); }
		@Bean Horloge horloge() { return new HorlogeFactice(); }
		@Bean Clock clock(Horloge horloge) { return Clock.fixed(horloge.maintenant(), ZoneOffset.UTC); }
		@Bean ServiceDeManche<?, ?> service(ModeFacticeDuSocle mode, SocleDeManche socle) { return new ServiceDeManche<>(mode, mode, socle); }
		@Bean FournisseurPartieCommune commun(StockageMancheFactice stockage, JsonMapper json) {
			return new FournisseurCommunDemonstration(stockage, json);
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
