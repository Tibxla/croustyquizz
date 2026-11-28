package fr.croustyquizz.tempsreel;

import static fr.croustyquizz.Attentes.PLAFOND_SECONDES;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.tempsreel.ClientDeSoiree.JoueurArrive;
import fr.croustyquizz.tempsreel.ClientDeSoiree.SoireeOuverte;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, BuzzDeSoireeHttpTest.TempsDuTest.class})
class BuzzDeSoireeHttpTest {

	private static final String ENVOYER_BUZZ = "envoyerBuzz";

	@LocalServerPort private int port;
	@Autowired private JsonMapper json;
	@Autowired private HorlogeFactice horloge;
	@Autowired private JdbcTemplate jdbc;
	private ClientDeSoiree client;

	@TestConfiguration(proxyBeanMethods = false)
	static class TempsDuTest {
		@Bean @Primary HorlogeFactice horlogeDesBuzz() { return new HorlogeFactice(); }
	}

	@BeforeEach
	void ouvrirLeClient() {
		client = new ClientDeSoiree(port, json);
	}

	@AfterEach
	void fermerLesConnexions() {
		client.close();
	}

	@Test
	void laManetteBuzzeEtLesTroisEcransRecoiventLeMemeBuzz() throws Exception {
		SoireeOuverte soiree = ouvrirSoiree();
		JoueurArrive lea = rejoindre(soiree, "Léa");
		var salle = abonner(soiree, "salle", soiree.cleEcran(), Canaux.salle(soiree.id()));
		var console = abonner(soiree, "console", soiree.animateur(), Canaux.console(soiree.id()));
		var manette = abonner(soiree, "manette", lea.jeton(), Canaux.joueur(lea.id()));
		var prototype = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree.id() + "/buzz", null,
				Map.of("pseudo", "Sam"), null);
		assertThat(prototype.getStatusCode().value()).isEqualTo(404);
		ContratApi.verifier("docs/api/prototype/openapi.yaml", "buzzerDansLePrototype", prototype);

		UUID envoi = UUID.randomUUID();
		JsonNode recu = verifier(ENVOYER_BUZZ, buzzer(soiree, lea.jeton(), Map.of("envoiId", envoi), null), 200);
		assertThat(recu.path("envoiId").asString()).isEqualTo(envoi.toString());
		assertThat(recu.path("joueurId").asString()).isEqualTo(lea.id().toString());
		assertThat(recu.path("pseudo").asString()).isEqualTo("Léa");
		assertThat(recu.path("recuLe").asString()).isEqualTo(horloge.maintenant().toString());
		assertThat(recu.path("rang").asLong()).isEqualTo(1);
		for (var ecran : List.of(salle, console, manette)) {
			JsonNode evenement = ecran.poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
			assertThat(evenement).isNotNull();
			assertThat(evenement.path("type").asString()).isEqualTo("buzz-recu");
			assertThat(evenement.path("soireeId").asString()).isEqualTo(soiree.id().toString());
			assertThat(evenement.path("donnees").path("dernierBuzz")).isEqualTo(recu);
		}
		JsonNode capture = verifier("retrouverEtatSalle", envoyer(HttpMethod.GET, soiree.route() + "/etat/salle",
				soiree.cleEcran(), null, null), 200);
		assertThat(capture.path("vue").path("dernierBuzz")).isEqualTo(recu);
		JoueurArrive sam = rejoindre(soiree, "Sam");
		JsonNode captureManette = verifier("retrouverEtatManette", envoyer(HttpMethod.GET, soiree.route() + "/etat/manette",
				sam.jeton(), null, null), 200);
		assertThat(captureManette.path("vue").path("dernierBuzz")).isEqualTo(recu);
	}

	@Test
	void seulsLesJoueursDeLaSoireePeuventBuzzer() throws Exception {
		SoireeOuverte soiree = ouvrirSoiree();
		JoueurArrive lea = rejoindre(soiree, "Léa");
		JoueurArrive sam = rejoindre(ouvrirSoiree(), "Sam");
		var demande = Map.of("envoiId", UUID.randomUUID());

		verifierRefus(buzzer(soiree, sam.jeton(), demande, null), 404, "RESSOURCE_INTROUVABLE");
		verifierRefus(buzzer(soiree, "jeton-invalide", demande, null), 401, "AUTHENTIFICATION_REQUISE");
		verifierRefus(buzzer(soiree, soiree.cleEcran(), demande, null), 403, "ACCES_REFUSE");
		verifierRefus(buzzer(soiree, lea.jeton(), demande, UUID.randomUUID()), 400, "REQUETE_INVALIDE");
		verifierRefus(buzzer(soiree, lea.jeton(), null, null), 400, "REQUETE_INVALIDE");
		verifierRefus(buzzer(soiree, lea.jeton(), Map.of("envoiId", "1-1-1-1-1"), null), 400, "REQUETE_INVALIDE");
		verifierRefus(buzzer(soiree, lea.jeton(), Map.of("envoiId", UUID.randomUUID(), "pseudo", "Sam"), null),
				400, "REQUETE_INVALIDE");
		JsonNode recu = verifier(ENVOYER_BUZZ, buzzer(soiree, lea.jeton(), demande, null), 200);
		assertThat(recu.path("rang").asLong()).isEqualTo(1);
	}

	@Test
	void unQuizActifRefuseUnNouveauBuzzMaisLeRecuInitialResteDisponible() throws Exception {
		SoireeOuverte soiree = ouvrirSoiree();
		JoueurArrive lea = rejoindre(soiree, "Léa");
		var salle = abonner(soiree, "salle", soiree.cleEcran(), Canaux.salle(soiree.id()));
		UUID envoi = UUID.randomUUID();
		JsonNode recu = verifier(ENVOYER_BUZZ, buzzer(soiree, lea.jeton(), null, envoi), 200);
		assertThat(salle.poll(PLAFOND_SECONDES, TimeUnit.SECONDS)).isNotNull();
		JsonNode quiz = verifier("ajouterManche", envoyer(HttpMethod.POST, soiree.route() + "/manches", soiree.animateur(),
				Map.of("mode", "QUIZ", "reglages", Map.of("nombreEtapes", 1, "tempsParEtapeSecondes", 10)), null), 201);
		UUID mancheId = UUID.fromString(quiz.path("id").asString());
		// Le déroulement du Quiz (#14) n'est pas encore disponible sur cette base.
		changerEtatDuQuiz(mancheId, "EN_COURS");

		var repetition = buzzer(soiree, lea.jeton(), Map.of("envoiId", envoi), null);
		assertThat(verifier(ENVOYER_BUZZ, repetition, 200)).isEqualTo(recu);
		UUID autreEnvoi = UUID.randomUUID();
		verifierRefus(buzzer(soiree, lea.jeton(), null, autreEnvoi), 409, "BUZZ_NON_AUTORISE");
		changerEtatDuQuiz(mancheId, "EN_PAUSE");
		verifierRefus(buzzer(soiree, lea.jeton(), null, UUID.randomUUID()), 409, "BUZZ_NON_AUTORISE");
		changerEtatDuQuiz(mancheId, "TERMINEE");
		verifierRefus(buzzer(soiree, lea.jeton(), null, autreEnvoi), 409, "BUZZ_NON_AUTORISE");

		JsonNode suivant = verifier(ENVOYER_BUZZ, buzzer(soiree, lea.jeton(), null, UUID.randomUUID()), 200);
		assertThat(suivant.path("rang").asLong()).isEqualTo(2);
		JsonNode evenement = salle.poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
		assertThat(evenement).isNotNull();
		assertThat(evenement.path("donnees").path("dernierBuzz")).isEqualTo(suivant);
	}

	// Pas de budget global : chaque attente du test est déjà bornée, et un budget fixe pourrait la trancher avant terme.
	@Test
	void soixanteManettesBuzzantEnsembleRecoiventToutesLesRevisionsDansLeMemeOrdre() throws Exception {
		SoireeOuverte soiree = ouvrirSoiree();
		List<EnvoiDeBuzz> envois = new ArrayList<>();
		for (int i = 1; i <= 60; i++) {
			envois.add(new EnvoiDeBuzz(rejoindre(soiree, "Joueur " + i), UUID.randomUUID()));
		}
		List<BlockingQueue<BuzzDiffuse>> destinations = new ArrayList<>();
		destinations.add(abonner(soiree, "salle", soiree.cleEcran(), Canaux.salle(soiree.id()), BuzzDiffuse::de));
		destinations.add(abonner(soiree, "console", soiree.animateur(), Canaux.console(soiree.id()), BuzzDiffuse::de));
		for (var envoi : envois) {
			JoueurArrive joueur = envoi.joueur();
			destinations.add(abonner(soiree, "manette", joueur.jeton(), Canaux.joueur(joueur.id()), BuzzDiffuse::de));
		}

		Map<Long, JsonNode> recus = buzzerEnsemble(soiree, envois);
		assertThat(recus).hasSize(60);
		assertThat(recus.values().stream().map(recu -> Instant.parse(recu.path("recuLe").asString())).toList()).isSorted();
		for (var destination : destinations) {
			verifierDiffusions(destination, recus);
		}
	}

	private Map<Long, JsonNode> buzzerEnsemble(SoireeOuverte soiree, List<EnvoiDeBuzz> envois) throws Exception {
		var rafale = ClientDeSoiree.enRafale(envois, envoi -> buzzer(soiree, envoi.joueur().jeton(), null, envoi.id()));
		Map<Long, JsonNode> recus = new TreeMap<>();
		for (int i = 0; i < envois.size(); i++) {
			JsonNode recu = verifier(ENVOYER_BUZZ, rafale.reponses().get(i), 200);
			assertThat(recu.path("envoiId").asString()).isEqualTo(envois.get(i).id().toString());
			assertThat(recu.path("joueurId").asString()).isEqualTo(envois.get(i).joueur().id().toString());
			assertThat(recus.put(recu.path("rang").asLong(), recu)).isNull();
		}
		return recus;
	}

	private static void verifierDiffusions(BlockingQueue<BuzzDiffuse> destination, Map<Long, JsonNode> recus)
			throws InterruptedException {
		long revisionPrecedente = -1;
		for (long rang = 1; rang <= recus.size(); rang++) {
			BuzzDiffuse evenement = destination.poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
			assertThat(evenement).as("Buzz de rang %s", rang).isNotNull();
			assertThat(evenement.type()).isEqualTo("buzz-recu");
			assertThat(evenement.buzz()).isEqualTo(recus.get(rang));
			if (revisionPrecedente >= 0) {
				assertThat(evenement.revision()).isEqualTo(revisionPrecedente + 1);
			}
			revisionPrecedente = evenement.revision();
		}
		assertThat(destination).isEmpty();
	}

	private void changerEtatDuQuiz(UUID mancheId, String etat) {
		jdbc.update("update manche_deroulement set etat = ? where manche_id = ?", etat, mancheId);
	}

	private SoireeOuverte ouvrirSoiree() throws Exception {
		return client.ouvrirSoiree();
	}

	private JoueurArrive rejoindre(SoireeOuverte soiree, String pseudo) throws Exception {
		return client.rejoindre(soiree, pseudo);
	}

	private BlockingQueue<JsonNode> abonner(SoireeOuverte soiree, String ecran, String secret, String destination) throws Exception {
		return client.abonner(soiree, ecran, secret, destination);
	}

	private <T> BlockingQueue<T> abonner(SoireeOuverte soiree, String ecran, String secret, String destination,
			Function<JsonNode, T> projection) throws Exception {
		return client.abonner(soiree, ecran, secret, destination, projection);
	}

	private ResponseEntity<String> buzzer(SoireeOuverte soiree, String jeton, Object corps, UUID cle) {
		return envoyer(HttpMethod.POST, soiree.route() + "/buzz", jeton, corps, cle);
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, String secret, Object corps, UUID cle) {
		return client.envoyer(methode, route, secret, corps, cle);
	}

	private JsonNode verifier(String operation, ResponseEntity<String> reponse, int statut) throws Exception {
		return client.verifier(operation, reponse, statut);
	}

	private void verifierRefus(ResponseEntity<String> reponse, int statut, String code) throws Exception {
		assertThat(verifier(ENVOYER_BUZZ, reponse, statut).path("code").asString()).isEqualTo(code);
	}

	private record EnvoiDeBuzz(JoueurArrive joueur, UUID id) { }
	private record BuzzDiffuse(String type, long revision, JsonNode buzz) {
		static BuzzDiffuse de(JsonNode evenement) {
			return new BuzzDiffuse(evenement.path("type").asString(), evenement.path("revision").asLong(),
					evenement.path("donnees").path("dernierBuzz"));
		}
	}
}
