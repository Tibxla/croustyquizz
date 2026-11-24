package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.CroustyQuizzApplication;
import fr.croustyquizz.TestcontainersConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("recette")
@Import(TestcontainersConfiguration.class)
class MusiqueDAttenteHttpTest {
	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String ROUTE = "/api/soirees/{soireeId}/musique-attente";
	private static final String REGLEMENT = "reglerMusiqueDAttente";

	@Autowired WebApplicationContext application;
	@Autowired JsonMapper json;
	@Autowired PostgreSQLContainer postgres;
	@Autowired @Qualifier("brokerChannel") SubscribableChannel courtier;
	private MockMvc http;

	@BeforeEach
	void monterHttp() {
		http = MockMvcBuilders.webAppContextSetup(application).build();
	}

	@Test
	void couperPuisRelancerHorsMancheActualiseLaConsoleEtLaSalle() throws Exception {
		UUID soiree = creerSoiree();
		var premiereVue = capturer(soiree, "console", "ANIMATEUR");
		assertThat(premiereVue.at("/vue/musiqueAttente/etat").asString()).isEqualTo("JOUE");
		assertThat(premiereVue.at("/vue/musiqueAttente/morceauxDisponibles").isNull()).isTrue();

		var destinations = new ArrayList<String>();
		MessageHandler observer = message -> {
			String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
			if (destination != null && destination.contains(soiree.toString())) {
				assertThat(json.readTree((byte[]) message.getPayload()).path("type").asString())
						.isEqualTo("musique-attente-modifiee");
				destinations.add(destination);
			}
		};
		courtier.subscribe(observer);
		try {
			var coupure = regler(soiree, "ANIMATEUR", "COUPEE", 200);
			assertThat(coupure.path("etat").asString()).isEqualTo("COUPEE");
			assertThat(regler(soiree, "ANIMATEUR", "COUPEE", 200)).isEqualTo(coupure);
		} finally {
			courtier.unsubscribe(observer);
		}
		assertThat(destinations).containsExactlyInAnyOrder(Canaux.salle(soiree), Canaux.console(soiree));
		var console = capturer(soiree, "console", "ANIMATEUR");
		var salle = capturer(soiree, "salle", "ECRAN");
		assertThat(console.path("revision").asLong()).isEqualTo(1);
		assertThat(salle.path("revision").asLong()).isEqualTo(1);
		assertThat(console.at("/vue/musiqueAttente")).isEqualTo(salle.at("/vue/musiqueAttente"));
		assertThat(salle.at("/vue/musiqueAttente/etat").asString()).isEqualTo("COUPEE");

		regler(soiree, "ANIMATEUR", "JOUE", 200);
		assertThat(capturer(soiree, "salle", "ECRAN").at("/vue/musiqueAttente/etat").asString()).isEqualTo("JOUE");
	}

	@Test
	void seulsLesActeursAutorisesPeuventAgirSurUneSoireeConnue() throws Exception {
		UUID soiree = creerSoiree();
		for (String role : new String[] { "JOUEUR", "ECRAN" }) {
			assertThat(regler(soiree, role, "COUPEE", 403).path("code").asString()).isEqualTo("ACCES_REFUSE");
		}
		var autrePortee = http.perform(put(ROUTE, soiree).header("Authorization", jeton(UUID.randomUUID(), "ANIMATEUR"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"etat\":\"COUPEE\"}"))
				.andExpect(status().isNotFound()).andReturn();
		ContratApi.verifier(CONTRAT, REGLEMENT, autrePortee);
		assertThat(json.readTree(autrePortee.getResponse().getContentAsString()).path("code").asString())
				.isEqualTo("RESSOURCE_INTROUVABLE");
		assertThat(signaler(soiree, "ANIMATEUR", "false", 403).path("code").asString()).isEqualTo("ACCES_REFUSE");
		assertThat(signaler(soiree, "ECRAN", "\"false\"", 400).path("code").asString()).isEqualTo("REQUETE_INVALIDE");
		var reponse = http.perform(put(ROUTE, soiree).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etat\":\"COUPEE\"}")).andExpect(status().isUnauthorized()).andReturn();
		ContratApi.verifier(CONTRAT, REGLEMENT, reponse);
		assertThat(capturer(soiree, "console", "ANIMATEUR").path("revision").asLong()).isZero();
	}

	@Test
	void couperLaMusiqueAvantLaPremiereMancheDuneSoireeDeDemonstration() throws Exception {
		UUID soiree = UUID.randomUUID();
		assertThat(regler(soiree, "ANIMATEUR", "COUPEE", 200).path("etat").asString()).isEqualTo("COUPEE");
		assertThat(capturer(soiree, "salle", "ECRAN").at("/vue/musiqueAttente/etat").asString()).isEqualTo("COUPEE");
	}

	@Test
	void leDossierSignaleNeChangePasLeReglageEtLesDeuxSurviventAuRedemarrage() throws Exception {
		UUID soiree = creerSoiree();
		UUID autreSoiree = creerSoiree();
		regler(soiree, "ANIMATEUR", "COUPEE", 200);
		assertThat(signaler(soiree, "ECRAN", "false", 200).path("morceauxDisponibles").asBoolean()).isFalse();
		signaler(soiree, "ECRAN", "false", 200);
		var console = capturer(soiree, "console", "ANIMATEUR");
		assertThat(console.path("revision").asLong()).isEqualTo(2);
		assertThat(console.at("/vue/musiqueAttente")).isEqualTo(json.readTree("""
				{"etat":"COUPEE","morceauxDisponibles":false}
				"""));

		try (var redemarrage = new SpringApplicationBuilder(CroustyQuizzApplication.class).profiles("recette")
				.run("--server.port=0", "--spring.docker.compose.enabled=false",
						"--spring.datasource.url=" + postgres.getJdbcUrl(),
						"--spring.datasource.username=" + postgres.getUsername(),
						"--spring.datasource.password=" + postgres.getPassword())) {
			http = MockMvcBuilders.webAppContextSetup((WebApplicationContext) redemarrage).build();
			var nouvelleSalle = capturer(soiree, "salle", "ECRAN");
			assertThat(nouvelleSalle.path("revision").asLong()).isZero();
			assertThat(nouvelleSalle.at("/vue/musiqueAttente")).isEqualTo(console.at("/vue/musiqueAttente"));
			assertThat(capturer(autreSoiree, "salle", "ECRAN").at("/vue/musiqueAttente/etat").asString()).isEqualTo("JOUE");
		}
	}

	private UUID creerSoiree() throws Exception {
		var reponse = http.perform(post("/api/soirees").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"titre":"Soirée Musique d’attente","format":"SOLO","pilotage":"MANUEL"}
						""")).andExpect(status().isCreated()).andReturn();
		ContratApi.verifier(CONTRAT, "creerSoiree", reponse);
		return UUID.fromString(json.readTree(reponse.getResponse().getContentAsString()).path("id").asString());
	}

	private JsonNode capturer(UUID soiree, String destination, String role) throws Exception {
		var reponse = http.perform(get("/api/soirees/{soireeId}/etat/" + destination, soiree)
				.header("Authorization", jeton(soiree, role))).andExpect(status().isOk()).andReturn();
		ContratApi.verifier(CONTRAT, "console".equals(destination) ? "retrouverEtatConsole" : "retrouverEtatSalle", reponse);
		return json.readTree(reponse.getResponse().getContentAsString());
	}

	private JsonNode regler(UUID soiree, String role, String etat, int statut) throws Exception {
		var reponse = http.perform(put(ROUTE, soiree).header("Authorization", jeton(soiree, role))
				.contentType(MediaType.APPLICATION_JSON).content("{\"etat\":\"" + etat + "\"}"))
				.andExpect(status().is(statut)).andExpect(header().string("Cache-Control", "no-store")).andReturn();
		ContratApi.verifier(CONTRAT, REGLEMENT, reponse);
		return json.readTree(reponse.getResponse().getContentAsString());
	}

	private JsonNode signaler(UUID soiree, String role, String disponibilite, int statut) throws Exception {
		var reponse = http.perform(put(ROUTE + "/disponibilite", soiree).header("Authorization", jeton(soiree, role))
				.contentType(MediaType.APPLICATION_JSON).content("{\"morceauxDisponibles\":" + disponibilite + "}"))
				.andExpect(status().is(statut)).andExpect(header().string("Cache-Control", "no-store")).andReturn();
		ContratApi.verifier(CONTRAT, "signalerDisponibiliteMusiqueDAttente", reponse);
		return json.readTree(reponse.getResponse().getContentAsString());
	}

	private static String jeton(UUID soiree, String role) {
		return "Bearer demo:" + role + ":" + soiree + ":" + UUID.randomUUID();
	}
}
