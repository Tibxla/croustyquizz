package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.ModeFacticeDuSocle;
import fr.croustyquizz.manche.PilotageDeSoireeDemonstration;
import fr.croustyquizz.manche.ServiceDeManche;
import fr.croustyquizz.manche.SocleDeManche;

/** Le socle reçoit le Pilotage choisi à la création d'une Soirée sur PostgreSQL. */
class PilotageDeSoireeEnregistreeTest {

	private static final String PROTOTYPE = "docs/api/prototype/openapi.yaml";

	@Nested
	@ActiveProfiles("dev")
	class Developpement extends AvecPostgreSQL {
		@Autowired private PilotageDeSoireeDemonstration demonstration;

		@Test
		void lesChoixDeDemonstrationNeChangentPasLePilotageEnregistre() throws Exception {
			UUID automatique = creerSoiree(Pilotage.AUTOMATIQUE);
			UUID manuelle = creerSoiree(Pilotage.MANUEL);

			choisir(automatique, "MANUEL");
			choisir(manuelle, "AUTOMATIQUE");
			assertThat(demonstration.estAutomatique(manuelle)).isTrue();
			verifierPilotageDuDeroulement(automatique, true);
			verifierPilotageDuDeroulement(manuelle, false);
		}

		@Test
		void uneSoireeAbsenteDeLaBaseSuitLeChoixDeDemonstration() throws Exception {
			UUID soiree = UUID.randomUUID();
			verifierPilotageDuDeroulement(soiree, false);
			choisir(soiree, "AUTOMATIQUE");
			verifierPilotageDuDeroulement(soiree, true);
		}

		private void choisir(UUID soiree, String pilotage) throws Exception {
			var reponse = demanderPilotage(soiree, pilotage);
			assertThat(reponse.getStatusCode().value()).isEqualTo(200);
			ContratApi.verifier(PROTOTYPE, "choisirPilotageDeTest", reponse);
		}
	}

	@Nested
	@ActiveProfiles("prod")
	class Production extends AvecPostgreSQL {
		@Test
		void lePilotageEnregistreEstLuEtLesSoireesAbsentesRestentManuelles() {
			verifierPilotageDuDeroulement(creerSoiree(Pilotage.AUTOMATIQUE), true);
			verifierPilotageDuDeroulement(creerSoiree(Pilotage.MANUEL), false);
			verifierPilotageDuDeroulement(UUID.randomUUID(), false);
		}

		@Test
		void laRouteDeChoixEstAbsente() throws Exception {
			var reponse = demanderPilotage(UUID.randomUUID(), "AUTOMATIQUE");
			assertThat(reponse.getStatusCode().value()).isEqualTo(404);
			ContratApi.verifier(PROTOTYPE, "choisirPilotageDeTest", reponse);
		}
	}

	@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.docker.compose.enabled=false")
	@Import(TestcontainersConfiguration.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
	abstract static class AvecPostgreSQL {
		@Autowired private ServiceSoiree soirees;
		@Autowired private SocleDeManche socle;
		@LocalServerPort private int port;

		UUID creerSoiree(Pilotage pilotage) {
			return soirees.creer("Soirée de test", Format.SOLO, pilotage).getId();
		}

		void verifierPilotageDuDeroulement(UUID soiree, boolean attendu) {
			var mode = new ModeFacticeDuSocle();
			var service = new ServiceDeManche<>(mode, mode, socle);
			UUID manche = service.creer(soiree, new ModeFacticeDuSocle.Reglages(3, 20));
			service.instantane(soiree, manche);
			assertThat(mode.dernierContexte.pilotageAutomatique()).isEqualTo(attendu);
		}

		ResponseEntity<String> demanderPilotage(UUID soiree, String pilotage) {
			String jeton = "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID();
			return RestClient.create("http://localhost:" + port).put().uri("/api/test/soirees/{soireeId}/pilotage", soiree)
					.header("Authorization", "Bearer " + jeton).contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("pilotage", pilotage))
					.retrieve().onStatus(statut -> statut.isError(), (demande, reponse) -> { }).toEntity(String.class);
		}
	}
}
