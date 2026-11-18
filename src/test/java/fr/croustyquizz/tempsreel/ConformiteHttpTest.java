package fr.croustyquizz.tempsreel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import fr.croustyquizz.ContratApi;

/** Routes et sérialisation Spring réelles, sans PostgreSQL ni Docker. */
@WebMvcTest({ HeureControleur.class, CanauxDeTestControleur.class, BuzzControleur.class })
@ActiveProfiles("dev")
@Import({ ConformiteHttpTest.Configuration.class, VerrousSoiree.class })
class ConformiteHttpTest {

	private static final UUID SOIREE = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String PROTOTYPE = "docs/api/prototype/openapi.yaml";

	@Autowired
	private MockMvc http;

	@Test
	void consulterHeureRespecteLeContratMetier() throws Exception {
		var resultat = http.perform(get("/api/heure")).andExpect(status().isOk()).andReturn();

		ContratApi.verifier("docs/api/openapi.yaml", "consulterHeure", resultat);
	}

	@Test
	void consulterHeureRespecteAussiLeContratDuPrototype() throws Exception {
		var resultat = http.perform(get("/api/heure")).andExpect(status().isOk()).andReturn();

		ContratApi.verifier(PROTOTYPE, "consulterHeure", resultat);
	}

	@Test
	void consulterCanauxDeTestRespecteLeContratDuPrototype() throws Exception {
		var resultat = http.perform(get("/api/test/soirees/{soireeId}/canaux", SOIREE))
				.andExpect(status().isOk()).andReturn();

		ContratApi.verifier(PROTOTYPE, "consulterCanauxDeTest", resultat);
	}

	@Test
	void buzzerRespecteLeSchemaReferenceDansLesComposantsDuPrototype() throws Exception {
		var resultat = http.perform(post("/api/test/soirees/{soireeId}/buzz", SOIREE)
				.contentType(MediaType.APPLICATION_JSON).content("{\"pseudo\":\"  Léa  \"}"))
				.andExpect(status().isOk()).andReturn();

		ContratApi.verifier(PROTOTYPE, "buzzerDansLePrototype", resultat);
	}

	@Test
	void unBuzzInvalideRespecteLeStatutDErreurDuPrototype() throws Exception {
		var resultat = http.perform(post("/api/test/soirees/{soireeId}/buzz", SOIREE)
				.contentType(MediaType.APPLICATION_JSON).content("{\"pseudo\":\"   \"}"))
				.andExpect(status().isBadRequest()).andReturn();

		ContratApi.verifier(PROTOTYPE, "buzzerDansLePrototype", resultat);
	}

	@Test
	void unIdentifiantDeSoireeInvalideRespecteLeStatutDErreurDuPrototype() throws Exception {
		var resultat = http.perform(get("/api/test/soirees/{soireeId}/canaux", "invalide"))
				.andExpect(status().isBadRequest()).andReturn();

		ContratApi.verifier(PROTOTYPE, "consulterCanauxDeTest", resultat);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Configuration {

		@Bean
		@Primary
		Clock horlogeFixe() {
			return Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
		}

		@Bean
		DiffuseurTempsReel diffuseur() {
			return new DiffuseurFactice();
		}
	}
}
