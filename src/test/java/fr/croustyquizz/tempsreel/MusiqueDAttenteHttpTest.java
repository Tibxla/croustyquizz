package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
	/** Sans autorisation Bearer, la requête part avec la session de l'Animateur qui a créé la Soirée. */
	private static final String SESSION_DU_PROPRIETAIRE = null;
	private MockMvc http;
	private MockHttpSession proprietaire;

	@BeforeEach
	void monterHttp() throws Exception {
		http = MockMvcBuilders.webAppContextSetup(application).build();
		proprietaire = connecterUnAnimateur();
	}

	@Test
	void couperPuisRelancerHorsMancheActualiseLaConsoleEtLaSalle() throws Exception {
		UUID soiree = creerSoiree();
		String animateur = SESSION_DU_PROPRIETAIRE;
		String ecran = creerAccesEcran(soiree, animateur);
		var premiereVue = capturer(soiree, "console", animateur);
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
			var coupure = regler(soiree, animateur, "COUPEE", 200);
			assertThat(coupure.path("etat").asString()).isEqualTo("COUPEE");
			assertThat(regler(soiree, animateur, "COUPEE", 200)).isEqualTo(coupure);
		} finally {
			courtier.unsubscribe(observer);
		}
		assertThat(destinations).containsExactlyInAnyOrder(Canaux.salle(soiree), Canaux.console(soiree));
		var console = capturer(soiree, "console", animateur);
		var salle = capturer(soiree, "salle", ecran);
		assertThat(console.path("revision").asLong()).isEqualTo(1);
		assertThat(salle.path("revision").asLong()).isEqualTo(1);
		assertThat(console.at("/vue/musiqueAttente")).isEqualTo(salle.at("/vue/musiqueAttente"));
		assertThat(salle.at("/vue/musiqueAttente/etat").asString()).isEqualTo("COUPEE");

		regler(soiree, animateur, "JOUE", 200);
		assertThat(capturer(soiree, "salle", ecran).at("/vue/musiqueAttente/etat").asString()).isEqualTo("JOUE");
	}

	@Test
	void seulsLesActeursAutorisesPeuventAgirSurUneSoireeConnue() throws Exception {
		UUID soiree = creerSoiree();
		String animateur = SESSION_DU_PROPRIETAIRE;
		String joueur = ouvrirEtRejoindreSoiree(soiree, animateur);
		String ecran = creerAccesEcran(soiree, animateur);
		long revisionAvantLesRefus = capturer(soiree, "console", animateur).path("revision").asLong();
		for (String autorisation : List.of(joueur, ecran)) {
			assertThat(regler(soiree, autorisation, "COUPEE", 403).path("code").asString()).isEqualTo("ACCES_REFUSE");
		}
		var autrePortee = http.perform(put(ROUTE, soiree).session(connecterUnAnimateur())
				.contentType(MediaType.APPLICATION_JSON).content("{\"etat\":\"COUPEE\"}"))
				.andExpect(status().isNotFound()).andReturn();
		ContratApi.verifier(CONTRAT, REGLEMENT, autrePortee);
		assertThat(json.readTree(autrePortee.getResponse().getContentAsString()).path("code").asString())
				.isEqualTo("RESSOURCE_INTROUVABLE");
		// Sur une Soirée enregistrée, l'Animateur de démonstration est refusé (#5).
		var demonstration = http.perform(put(ROUTE, soiree)
				.header("Authorization", autorisationDeDemonstration(soiree, "ANIMATEUR"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"etat\":\"COUPEE\"}"))
				.andExpect(status().isUnauthorized()).andReturn();
		ContratApi.verifier(CONTRAT, REGLEMENT, demonstration);
		assertThat(signaler(soiree, animateur, "false", 403).path("code").asString()).isEqualTo("ACCES_REFUSE");
		assertThat(signaler(soiree, ecran, "\"false\"", 400).path("code").asString()).isEqualTo("REQUETE_INVALIDE");
		var reponse = http.perform(put(ROUTE, soiree).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etat\":\"COUPEE\"}")).andExpect(status().isUnauthorized()).andReturn();
		ContratApi.verifier(CONTRAT, REGLEMENT, reponse);
		assertThat(capturer(soiree, "console", animateur).path("revision").asLong()).isEqualTo(revisionAvantLesRefus);
	}

	@Test
	void couperLaMusiqueAvantLaPremiereMancheDuneSoireeDeDemonstration() throws Exception {
		UUID soiree = UUID.randomUUID();
		String animateur = autorisationDeDemonstration(soiree, "ANIMATEUR");
		String ecran = autorisationDeDemonstration(soiree, "ECRAN");
		assertThat(regler(soiree, animateur, "COUPEE", 200).path("etat").asString()).isEqualTo("COUPEE");
		assertThat(capturer(soiree, "salle", ecran).at("/vue/musiqueAttente/etat").asString()).isEqualTo("COUPEE");
	}

	@Test
	void leDossierSignaleNeChangePasLeReglageEtLesDeuxSurviventAuRedemarrage() throws Exception {
		UUID soiree = creerSoiree();
		UUID autreSoiree = creerSoiree();
		String animateur = SESSION_DU_PROPRIETAIRE;
		String ecran = creerAccesEcran(soiree, animateur);
		String autreEcran = creerAccesEcran(autreSoiree, SESSION_DU_PROPRIETAIRE);
		regler(soiree, animateur, "COUPEE", 200);
		assertThat(signaler(soiree, ecran, "false", 200).path("morceauxDisponibles").asBoolean()).isFalse();
		signaler(soiree, ecran, "false", 200);
		var console = capturer(soiree, "console", animateur);
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
			var nouvelleSalle = capturer(soiree, "salle", ecran);
			assertThat(nouvelleSalle.path("revision").asLong()).isZero();
			assertThat(nouvelleSalle.at("/vue/musiqueAttente")).isEqualTo(console.at("/vue/musiqueAttente"));
			assertThat(capturer(autreSoiree, "salle", autreEcran).at("/vue/musiqueAttente/etat").asString()).isEqualTo("JOUE");
		}
	}

	private UUID creerSoiree() throws Exception {
		var reponse = http.perform(post("/api/soirees").session(proprietaire).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"titre":"Soirée Musique d’attente","format":"SOLO","pilotage":"MANUEL"}
						""")).andExpect(status().isCreated()).andReturn();
		ContratApi.verifier(CONTRAT, "creerSoiree", reponse);
		return UUID.fromString(json.readTree(reponse.getResponse().getContentAsString()).path("id").asString());
	}

	private String creerAccesEcran(UUID soiree, String animateur) throws Exception {
		var reponse = http.perform(identifier(post("/api/soirees/{soireeId}/acces-ecran", soiree), animateur))
				.andExpect(status().isOk()).andReturn();
		ContratApi.verifier(CONTRAT, "creerAccesEcran", reponse);
		return "Bearer " + json.readTree(reponse.getResponse().getContentAsString()).path("cleEcran").asString();
	}

	private String ouvrirEtRejoindreSoiree(UUID soiree, String animateur) throws Exception {
		var ouverture = http.perform(identifier(post("/api/soirees/{soireeId}/ouverture", soiree), animateur)
				.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isOk()).andReturn();
		ContratApi.verifier(CONTRAT, "ouvrirSoiree", ouverture);
		String code = json.readTree(ouverture.getResponse().getContentAsString()).path("codeAcces").asString();
		var arrivee = http.perform(post("/api/soirees/rejoindre").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("codeAcces", code, "pseudo", "Léa"))))
				.andExpect(status().isCreated()).andReturn();
		ContratApi.verifier(CONTRAT, "rejoindreSoiree", arrivee);
		return "Bearer " + json.readTree(arrivee.getResponse().getContentAsString()).path("jetonReconnexion").asString();
	}

	private JsonNode capturer(UUID soiree, String destination, String autorisation) throws Exception {
		var reponse = http.perform(identifier(get("/api/soirees/{soireeId}/etat/" + destination, soiree), autorisation))
				.andExpect(status().isOk()).andReturn();
		ContratApi.verifier(CONTRAT, "console".equals(destination) ? "retrouverEtatConsole" : "retrouverEtatSalle", reponse);
		return json.readTree(reponse.getResponse().getContentAsString());
	}

	private JsonNode regler(UUID soiree, String autorisation, String etat, int statut) throws Exception {
		var reponse = http.perform(identifier(put(ROUTE, soiree), autorisation)
				.contentType(MediaType.APPLICATION_JSON).content("{\"etat\":\"" + etat + "\"}"))
				.andExpect(status().is(statut)).andExpect(header().string("Cache-Control", "no-store")).andReturn();
		ContratApi.verifier(CONTRAT, REGLEMENT, reponse);
		return json.readTree(reponse.getResponse().getContentAsString());
	}

	private JsonNode signaler(UUID soiree, String autorisation, String disponibilite, int statut) throws Exception {
		var reponse = http.perform(identifier(put(ROUTE + "/disponibilite", soiree), autorisation)
				.contentType(MediaType.APPLICATION_JSON).content("{\"morceauxDisponibles\":" + disponibilite + "}"))
				.andExpect(status().is(statut)).andExpect(header().string("Cache-Control", "no-store")).andReturn();
		ContratApi.verifier(CONTRAT, "signalerDisponibiliteMusiqueDAttente", reponse);
		return json.readTree(reponse.getResponse().getContentAsString());
	}

	private MockHttpServletRequestBuilder identifier(MockHttpServletRequestBuilder requete, String autorisation) {
		return autorisation == null ? requete.session(proprietaire) : requete.header("Authorization", autorisation);
	}

	/** Inscrit puis connecte un Animateur ; MockMvc ne passe pas par le filtre CSRF, la session suffit. */
	private MockHttpSession connecterUnAnimateur() throws Exception {
		String email = UUID.randomUUID() + "@exemple.test";
		String motDePasse = "un mot de passe de test";
		http.perform(post("/api/animateurs").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", email, "motDePasse", motDePasse, "nom", "Camille",
						"lieu", Map.of("nom", "Le Balcon", "ville", "Nanterre")))))
				.andExpect(status().isCreated());
		var connexion = http.perform(post("/api/animateurs/session").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", email, "motDePasse", motDePasse))))
				.andExpect(status().isOk()).andReturn();
		return (MockHttpSession) connexion.getRequest().getSession(false);
	}

	private static String autorisationDeDemonstration(UUID soiree, String role) {
		return "Bearer demo:" + role + ":" + soiree + ":" + UUID.randomUUID();
	}
}
