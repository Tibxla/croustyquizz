package fr.croustyquizz.soiree;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.ErreursHttp;
import fr.croustyquizz.tempsreel.ConfigurationActeurHttp;
import fr.croustyquizz.tempsreel.ResolveurDActeur;
import fr.croustyquizz.tempsreel.ResolveurDActeurDemonstration;

class ResolveurDActeurSoireeTest {

	@Test
	void unIdentifiantMalFormeDonne404QuandLActeurEstResoluAvantLeParametreDeChemin() throws Exception {
		var soirees = mock(DepotDeSoirees.class);
		var joueurs = mock(DepotDeJoueurs.class);
		var acces = new ServiceDesAcces(soirees, joueurs, mock(DepotDAccesEcran.class), Clock.systemUTC());
		var resolveur = new ResolveurDActeurSoiree(acces, soirees, joueurs, new ResolveurDActeurDemonstration());
		var beans = new StaticListableBeanFactory(Map.of("resolveur", resolveur));
		var configuration = new ConfigurationActeurHttp(beans.getBeanProvider(ResolveurDActeur.class));
		var resolveurs = new ArrayList<HandlerMethodArgumentResolver>();
		configuration.addArgumentResolvers(resolveurs);
		var mvc = MockMvcBuilders.standaloneSetup(new RouteAvecActeurEnPremier())
				.setCustomArgumentResolvers(resolveurs.getFirst()).setControllerAdvice(new ErreursHttp()).build();

		var reponse = mvc.perform(put("/api/test/soirees/identifiant-invalide/pilotage")
				.header("Authorization", "Bearer demo:ANIMATEUR:" + UUID.randomUUID() + ":" + UUID.randomUUID()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESSOURCE_INTROUVABLE"))
				.andReturn();
		ContratApi.verifier("docs/api/prototype/openapi.yaml", "choisirPilotageDeTest", reponse);
	}

	@RestController
	static class RouteAvecActeurEnPremier {
		@PutMapping("/api/test/soirees/{soireeId}/pilotage")
		void choisirPilotage(Acteur acteur, @PathVariable UUID soireeId) {
			throw new AssertionError("La route doit être refusée lors de la résolution de l’Acteur.");
		}
	}
}
