package fr.croustyquizz.campagne;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EnvoiDeSmsTwilioTest {
	private static final String COMPTE = "AC00000000000000000000000000000000";
	private static final String MESSAGE = "SM00000000000000000000000000000001";
	private static final String JETON = "jeton-fictif-pour-le-test";
	private static final String EXPEDITEUR = "+12025550123";
	private static final String DESTINATAIRE = "+33639980027";
	private static final String ROUTE = "/2010-04-01/Accounts/" + COMPTE + "/Messages.json";

	private HttpServer serveur;
	private URI adresse;

	@BeforeEach
	void demarrerServeur() throws IOException {
		serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.start();
		adresse = URI.create("http://127.0.0.1:" + serveur.getAddress().getPort());
	}

	@AfterEach
	void arreterServeur() {
		serveur.stop(0);
	}

	@ParameterizedTest
	@CsvSource({
		"'', TWILIO, %C3%89t%C3%A9+%26+quiz%0ASTOP+%3A+https%3A%2F%2Fexample.invalid%2Fa",
		"sms_appointment_reminders, TWILIO_ESSAI, sms_appointment_reminders"
	})
	void transmetLeFormulaireAuthentifieEtRendLeSid(String modele, EnvoiDeSms.Mode mode, String corpsEncode) {
		var requete = new AtomicReference<Requete>();
		serveur.createContext("/", echange -> {
			try (echange) {
				requete.set(new Requete(echange.getRequestMethod(), echange.getRequestURI().getPath(),
						echange.getRequestHeaders().getFirst("Authorization"),
						echange.getRequestHeaders().getFirst("Content-Type"),
						new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
				repondre(echange, 201, "{\"sid\":\"" + MESSAGE + "\",\"status\":\"queued\"}");
			}
		});

		var sms = new EnvoiDeSmsTwilio(adresse, COMPTE, JETON, EXPEDITEUR, modele, Duration.ofSeconds(2));
		assertThat(sms.mode()).isEqualTo(mode);
		assertThat(sms.envoyer("Été & quiz\nSTOP : https://example.invalid/a", DESTINATAIRE))
				.isEqualTo(new EnvoiDeSms.Envoye(MESSAGE));
		assertThat(requete.get()).isEqualTo(new Requete("POST", ROUTE, authentification(),
				"application/x-www-form-urlencoded",
				"To=%2B33639980027&From=%2B12025550123&Body=" + corpsEncode));
	}

	@Test
	void isoleLeCodeDuRefusEtNeJournaliseQueLeNumeroMasque(CapturedOutput sortie) {
		serveur.createContext(ROUTE, echange -> {
			try (echange) {
				repondre(echange, 400, """
						{"code":21608,"message":"Refus pour %s depuis %s avec %s"}
						""".formatted(DESTINATAIRE, EXPEDITEUR, JETON));
			}
		});
		Logger journalHttp = (Logger) LoggerFactory.getLogger("org.springframework.web.client");
		Level initial = journalHttp.getLevel();
		journalHttp.setLevel(Level.TRACE);
		try {
			assertThat(adaptateur(Duration.ofSeconds(2)).envoyer("Texte confidentiel", DESTINATAIRE))
					.isEqualTo(new EnvoiDeSms.Echec("Twilio a refusé le SMS (code 21608)."));
		} finally {
			journalHttp.setLevel(initial);
		}
		assertThat(sortie.getAll()).contains("+33 6 •• •• •• 27")
				.doesNotContain(DESTINATAIRE.substring(1), EXPEDITEUR.substring(1), JETON,
						authentification(), "Texte confidentiel");
	}

	@Test
	void rendUnEchecQuandLaConnexionEstCoupee() {
		serveur.createContext(ROUTE, HttpExchange::close);

		assertThat(adaptateur(Duration.ofSeconds(2)).envoyer("Quiz jeudi !", DESTINATAIRE))
				.isEqualTo(new EnvoiDeSms.Echec("Twilio est injoignable."));
	}

	@Test
	void rendUnEchecQuandLaLectureDepasseLeDelai() {
		var liberation = new CountDownLatch(1);
		serveur.createContext(ROUTE, echange -> {
			try (echange) {
				echange.sendResponseHeaders(201, 100);
				try {
					liberation.await();
				} catch (InterruptedException interruption) {
					Thread.currentThread().interrupt();
				}
			}
		});

		try {
			assertTimeoutPreemptively(Duration.ofSeconds(4), () ->
					assertThat(adaptateur(Duration.ofMillis(250)).envoyer("Quiz jeudi !", DESTINATAIRE))
							.isEqualTo(new EnvoiDeSms.Echec("Twilio n’a pas répondu à temps.")));
		} finally {
			liberation.countDown();
		}
	}

	@Test
	void rendUnEchecSansReprendreUneReponseInattendue() {
		serveur.createContext(ROUTE, echange -> {
			try (echange) {
				repondre(echange, 201, "{\"sid\":\"" + DESTINATAIRE + "\"}");
			}
		});

		assertThat(adaptateur(Duration.ofSeconds(2)).envoyer("Quiz jeudi !", DESTINATAIRE))
				.isEqualTo(new EnvoiDeSms.Echec("Réponse Twilio sans identifiant de message valide."));
	}

	private EnvoiDeSms adaptateur(Duration lecture) {
		return new EnvoiDeSmsTwilio(adresse, COMPTE, JETON, EXPEDITEUR, "", lecture);
	}

	private static String authentification() {
		return "Basic " + Base64.getEncoder().encodeToString((COMPTE + ":" + JETON).getBytes(StandardCharsets.UTF_8));
	}

	private static void repondre(HttpExchange echange, int statut, String corps) throws IOException {
		byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
		echange.getResponseHeaders().set("Content-Type", "application/json");
		echange.sendResponseHeaders(statut, octets.length);
		echange.getResponseBody().write(octets);
	}

	private record Requete(String methode, String chemin, String autorisation, String type, String formulaire) { }
}
