package fr.croustyquizz.tempsreel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IdentiteDeTestControleur.class)
@ActiveProfiles("dev")
class CacheDesRessourcesHttpTest {

	@Autowired
	private MockMvc http;

	@ParameterizedTest
	@ValueSource(strings = {
			"/js/contexte.mjs", "/css/croustyquizz.css", "/ecran.html",
			"/webjars/stomp__stompjs/bundles/stomp.umd.min.js" })
	void uneRessourceStatiqueSeRevalide(String chemin) throws Exception {
		var reponse = http.perform(get(chemin))
				.andExpect(status().isOk())
				.andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
				.andReturn().getResponse();

		http.perform(get(chemin).header(HttpHeaders.IF_MODIFIED_SINCE, reponse.getHeader(HttpHeaders.LAST_MODIFIED)))
				.andExpect(status().isNotModified())
				.andExpect(content().string(""));
	}

	@Test
	void laPageDIdentiteNeSeGardePas() throws Exception {
		http.perform(get("/test/identite.html"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
	}
}
