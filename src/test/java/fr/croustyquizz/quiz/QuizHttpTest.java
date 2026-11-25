package fr.croustyquizz.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.ErreursHttp;
import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.ModeFacticeDuSocle;
import fr.croustyquizz.manche.PilotageMancheControleur;
import fr.croustyquizz.manche.RegistreDesDeroulements;
import fr.croustyquizz.manche.RegistreDesEnvois;
import fr.croustyquizz.manche.RegistreServicesDeManche;
import fr.croustyquizz.manche.SocleDeManche;
import fr.croustyquizz.manche.StockageMancheFactice;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import fr.croustyquizz.tempsreel.AssembleurDeVues;
import fr.croustyquizz.tempsreel.CaptureVuePrototype;
import fr.croustyquizz.tempsreel.ConfigurationActeurHttp;
import fr.croustyquizz.tempsreel.DiffuseurFactice;
import fr.croustyquizz.tempsreel.DiffuseurStomp;
import fr.croustyquizz.tempsreel.FournisseurCommunDemonstration;
import fr.croustyquizz.tempsreel.FournisseurDeBloc;
import fr.croustyquizz.tempsreel.FournisseurPartieCommune;
import fr.croustyquizz.tempsreel.PublicationApresCommit;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.RegistreAbonnements;
import fr.croustyquizz.tempsreel.ResolveurDActeur;
import fr.croustyquizz.tempsreel.ResolveurDActeurDemonstration;
import fr.croustyquizz.tempsreel.VerrousSoiree;
import fr.croustyquizz.tempsreel.VueSallePrototype;
import fr.croustyquizz.tempsreel.VuesControleur;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HashMap;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = QuizHttpTest.Configuration.class,
		properties = "spring.docker.compose.enabled=false"
)
@ActiveProfiles({"recette", "quiz-http-test"})
class QuizHttpTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private ServiceQuiz serviceQuiz;

	@Autowired
	private HorlogeFactice horloge;

	@Test
	void parcoursCompletQuizReponsesIdempotenceEtDelais() throws Exception {
		UUID soiree = UUID.randomUUID();
		UUID mancheId = serviceQuiz.creer(soiree, Map.of("nombreEtapes", 3, "tempsParEtapeSecondes", 20));

		// 1. Démarrer la Manche de Quiz
		serviceQuiz.demarrer(mancheId, new fr.croustyquizz.manche.EnvoiDeCommande(
				new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree),
				"/api/test/demarrer",
				UUID.randomUUID(),
				null
		));

		InstantaneQuiz instantane = (InstantaneQuiz) serviceQuiz.instantane(soiree, mancheId);
		assertThat(instantane.phase()).isEqualTo(PhaseQuiz.QUESTION_OUVERTE);
		UUID etapeId = instantane.question().etapeId();
		UUID pJuste = instantane.question().propositions().getFirst().id();
		UUID pFausse = instantane.question().propositions().get(1).id();

		// 2. Un joueur envoie une réponse juste via HTTP
		UUID envoiId1 = UUID.randomUUID();
		String routeReponse = "/api/soirees/" + soiree + "/manches/" + mancheId + "/questions/" + etapeId + "/reponses";
		var reponse = envoyer(HttpMethod.POST, routeReponse, soiree, Acteur.Type.JOUEUR,
				Map.of("envoiId", envoiId1.toString(), "propositionId", pJuste.toString()), envoiId1);

		ContratApi.verifier(CONTRAT, "envoyerReponse", reponse);
		assertThat(reponse.getStatusCode().value()).isEqualTo(200);

		// 3. Répéter le même envoi (même envoiId et même contenu) -> idempotent
		var repetition = envoyer(HttpMethod.POST, routeReponse, soiree, Acteur.Type.JOUEUR,
				Map.of("envoiId", envoiId1.toString(), "propositionId", pJuste.toString()), envoiId1);
		ContratApi.verifier(CONTRAT, "envoyerReponse", repetition);
		assertThat(repetition.getStatusCode().value()).isEqualTo(200);
		assertThat(repetition.getBody()).isEqualTo(reponse.getBody());

		// 4. Tenter de modifier la réponse acceptée avec un nouvel envoiId -> 409 REPONSE_DEJA_ENREGISTREE
		UUID envoiId2 = UUID.randomUUID();
		var modif = envoyer(HttpMethod.POST, routeReponse, soiree, Acteur.Type.JOUEUR,
				Map.of("envoiId", envoiId2.toString(), "propositionId", pFausse.toString()), envoiId2);
		ContratApi.verifier(CONTRAT, "envoyerReponse", modif);
		assertThat(modif.getStatusCode().value()).isEqualTo(409);

		// 5. Clôturer la Question (avancer le temps jusqu'à l'échéance)
		horloge.avancer(Duration.ofSeconds(20));
		InstantaneQuiz instantaneCorrigee = (InstantaneQuiz) serviceQuiz.instantane(soiree, mancheId);
		assertThat(instantaneCorrigee.phase()).isEqualTo(PhaseQuiz.CORRECTION);

		// 6. Répéter le premier envoi APRÈS clôture -> retrouve le résultat initial
		var repetitionApresCloture = envoyer(HttpMethod.POST, routeReponse, soiree, Acteur.Type.JOUEUR,
				Map.of("envoiId", envoiId1.toString(), "propositionId", pJuste.toString()), envoiId1);
		ContratApi.verifier(CONTRAT, "envoyerReponse", repetitionApresCloture);
		assertThat(repetitionApresCloture.getStatusCode().value()).isEqualTo(200);

		// 7. Première réception après clôture -> 409 HORS_DELAI
		UUID envoiTardif = UUID.randomUUID();
		var horsDelai = envoyer(HttpMethod.POST, routeReponse, soiree, Acteur.Type.JOUEUR,
				Map.of("envoiId", envoiTardif.toString(), "propositionId", pJuste.toString()), envoiTardif);
		ContratApi.verifier(CONTRAT, "envoyerReponse", horsDelai);
		assertThat(horsDelai.getStatusCode().value()).isEqualTo(409);
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, UUID soiree, Acteur.Type role, Object corps, UUID envoi) {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
		if (role != null) {
			requete.header("Authorization", "Bearer demo:" + role + ":" + soiree + ":" + FournisseurCommunDemonstration.joueurId(soiree));
		}
		if (envoi != null) {
			requete.header("Idempotency-Key", envoi.toString());
		}
		if (corps != null) {
			requete.contentType(MediaType.APPLICATION_JSON).body(corps);
		}
		return requete.retrieve().onStatus(statut -> statut.isError(), (demande, rep) -> {}).toEntity(String.class);
	}

	@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
	@org.springframework.context.annotation.Profile("quiz-http-test")
	@EnableAutoConfiguration(excludeName = {
			"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
			"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
			"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
			"org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
	})
	@Import({
			PilotageMancheControleur.class, ReponseQuizControleur.class, VuesControleur.class, ErreursHttp.class,
			ConfigurationActeurHttp.class, AssembleurDeVues.class, PublicationDeVues.class, PublicationApresCommit.class,
			VerrousSoiree.class, RegistreDesDeroulements.class, RegistreDesEnvois.class, SocleDeManche.class,
			RegistreServicesDeManche.class, RegistreAbonnements.class, DiffuseurStomp.class, QuizHttpTest.IdentitesFactices.class
	})
	static class Configuration {

		private final Map<UUID, EtatQuizPersiste> etatsPersistes = new HashMap<>();

		@Bean
		HorlogeFactice horloge() {
			return new HorlogeFactice();
		}

		@Bean
		Clock clock(HorlogeFactice horloge) {
			return Clock.fixed(horloge.maintenant(), ZoneOffset.UTC);
		}

		@Bean
		SourceDeQuestions sourceDeQuestions() {
			return (cats, diff, n) -> List.of(
					creerQuestion("Question 1 ?"),
					creerQuestion("Question 2 ?"),
					creerQuestion("Question 3 ?")
			);
		}

		private Question creerQuestion(String enonce) {
			UUID pJuste = UUID.randomUUID();
			return new Question(
					UUID.randomUUID(),
					enonce,
					"Culture générale",
					Difficulte.FACILE,
					List.of(
							new Proposition(pJuste, "Bonne réponse"),
							new Proposition(UUID.randomUUID(), "Faux 1"),
							new Proposition(UUID.randomUUID(), "Faux 2"),
							new Proposition(UUID.randomUUID(), "Faux 3")
					),
					pJuste
			);
		}

		@Bean
		FabriqueQuiz fabriqueQuiz(SourceDeQuestions source) {
			return new FabriqueQuiz(source);
		}

		@Bean
		fr.croustyquizz.manche.PersistanceDuMode<EtatQuizPersiste> persistanceQuiz() {
			return new fr.croustyquizz.manche.PersistanceDuMode<>() {
				@Override
				public EtatQuizPersiste charger(UUID mancheId) {
					return etatsPersistes.get(mancheId);
				}

				@Override
				public void enregistrer(UUID mancheId, Object instantane) {
					if (instantane instanceof DeroulementQuiz deroulement) {
						etatsPersistes.put(mancheId, deroulement.etatPersiste());
					}
				}
			};
		}

		@Bean
		StockageMancheFactice stockage(ModeFacticeDuSocle mode) {
			return new StockageMancheFactice(mode);
		}

		@Bean
		ModeFacticeDuSocle modeFactice() {
			return new ModeFacticeDuSocle();
		}

		@Bean
		ServiceQuiz serviceQuiz(
				FabriqueQuiz fabrique,
				fr.croustyquizz.manche.PersistanceDuMode<EtatQuizPersiste> persistance,
				SocleDeManche socle
		) {
			return new ServiceQuiz(fabrique, persistance, socle);
		}

		@Bean
		FournisseurPartieCommune commun(StockageMancheFactice stockage, JsonMapper json) {
			return new FournisseurCommunDemonstration(stockage, json);
		}

		@Bean
		FournisseurDeBloc blocQuiz(org.springframework.beans.factory.ObjectProvider<ServiceQuiz> service,
				StockageMancheFactice stockage) {
			return new FournisseurDeBlocQuiz(service::getObject, stockage);
		}

		@Bean
		CaptureVuePrototype prototype() {
			return id -> new VueSallePrototype(List.of());
		}

		@Bean
		SimpMessagingTemplate messagerie() {
			var canal = new ExecutorSubscribableChannel();
			canal.subscribe(message -> {});
			return new SimpMessagingTemplate(canal);
		}
	}

	@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
	static class IdentitesFactices {
		@Bean
		ResolveurDActeur identites() {
			return new ResolveurDActeurDemonstration();
		}
	}
}
