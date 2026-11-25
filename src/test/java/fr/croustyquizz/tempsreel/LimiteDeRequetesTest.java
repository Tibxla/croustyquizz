package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import fr.croustyquizz.manche.HorlogeFactice;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LimiteDeRequetesTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String SOIREE = "/api/soirees/" + UUID.randomUUID();
	private static final String BUZZ = SOIREE + "/buzz";
	private static final String REJOINDRE = "/api/soirees/rejoindre";
	private static final ReglagesDesLimites.Seuil DEUX_PAR_SECONDE = new ReglagesDesLimites.Seuil(2, Duration.ofSeconds(1));

	private final HorlogeFactice horloge = new HorlogeFactice();
	private final AtomicInteger traitees = new AtomicInteger();
	private LimiteDeRequetes limite = new LimiteDeRequetes(reglages(true, 1_000), horloge, JSON);

	@Test
	void unBuzzDeTropRendUn429AuFormatProblemeAvecRetryAfter() throws Exception {
		assertThat(envoyer("POST", BUZZ, "jeton-lea", null).getStatus()).isEqualTo(200);
		assertThat(envoyer("POST", BUZZ, "jeton-lea", null).getStatus()).isEqualTo(200);

		MockHttpServletResponse refus = envoyer("POST", BUZZ, "jeton-lea", null);

		assertThat(refus.getStatus()).isEqualTo(429);
		assertThat(refus.getHeader("Retry-After")).isEqualTo("1");
		assertThat(refus.getHeader("Cache-Control")).isEqualTo("no-store");
		assertThat(refus.getContentType()).startsWith("application/problem+json");
		JsonNode probleme = JSON.readTree(refus.getContentAsString());
		assertThat(probleme.path("status").asInt()).isEqualTo(429);
		assertThat(probleme.path("code").asString()).isEqualTo("TROP_DE_REQUETES");
		assertThat(probleme.path("title").asString()).isEqualTo("TROP_DE_REQUETES");
		assertThat(probleme.path("type").asString()).isEqualTo("about:blank");
		assertThat(probleme.path("instance").asString()).isEqualTo(BUZZ);
		assertThat(probleme.path("detail").asString()).isEqualTo("Trop de requêtes en peu de temps. Réessaie dans 1 seconde.");
		assertThat(traitees).hasValue(2);
	}

	@Test
	void unJetonNeGenePasUnAutreJeton() throws Exception {
		envoyer("POST", BUZZ, "jeton-lea", null);
		envoyer("POST", BUZZ, "jeton-lea", null);
		assertThat(envoyer("POST", BUZZ, "jeton-lea", null).getStatus()).isEqualTo(429);

		assertThat(envoyer("POST", BUZZ, "jeton-sam", null).getStatus()).isEqualTo(200);
	}

	@Test
	void laLimiteSeRouvreApresLeDelaiAnnonce() throws Exception {
		envoyer("POST", BUZZ, "jeton-lea", null);
		envoyer("POST", BUZZ, "jeton-lea", null);
		assertThat(envoyer("POST", BUZZ, "jeton-lea", null).getStatus()).isEqualTo(429);

		horloge.avancer(Duration.ofMillis(999));
		assertThat(envoyer("POST", BUZZ, "jeton-lea", null).getStatus()).isEqualTo(429);
		horloge.avancer(Duration.ofMillis(1));
		assertThat(envoyer("POST", BUZZ, "jeton-lea", null).getStatus()).isEqualTo(200);
	}

	@Test
	void sansJetonLaCleEstLAdresse() throws Exception {
		envoyer("POST", BUZZ, null, null, "203.0.113.1");
		envoyer("POST", BUZZ, null, null, "203.0.113.1");

		assertThat(envoyer("POST", BUZZ, null, null, "203.0.113.1").getStatus()).isEqualTo(429);
		assertThat(envoyer("POST", BUZZ, null, null, "203.0.113.2").getStatus()).isEqualTo(200);
		assertThat(envoyer("POST", BUZZ, "jeton-lea", null, "203.0.113.1").getStatus()).isEqualTo(200);
	}

	@Test
	void chaqueRouteDeReponseASaPropreLimite() throws Exception {
		String morceau = SOIREE + "/manches/" + UUID.randomUUID() + "/morceaux/" + UUID.randomUUID();
		String vote = SOIREE + "/manches/" + UUID.randomUUID() + "/passages/" + UUID.randomUUID() + "/vote";
		String reponse = SOIREE + "/manches/" + UUID.randomUUID() + "/questions/" + UUID.randomUUID() + "/reponses";
		for (String[] route : new String[][] { { "PUT", morceau + "/brouillon" }, { "POST", morceau + "/proposition" },
				{ "POST", vote }, { "POST", reponse } }) {
			envoyer(route[0], route[1], "jeton-lea", null);
			envoyer(route[0], route[1], "jeton-lea", null);
			assertThat(envoyer(route[0], route[1], "jeton-lea", null).getStatus()).as(route[1]).isEqualTo(429);
		}
		assertThat(envoyer("POST", BUZZ, "jeton-lea", null).getStatus()).isEqualTo(200);
	}

	@Test
	void lesAutresRoutesNeSontJamaisLimitees() throws Exception {
		for (int i = 0; i < 20; i++) {
			assertThat(envoyer("GET", SOIREE + "/etat/manette", "jeton-lea", null).getStatus()).isEqualTo(200);
			assertThat(envoyer("GET", BUZZ, "jeton-lea", null).getStatus()).isEqualTo(200);
			assertThat(envoyer("POST", SOIREE + "/commandes", "jeton-lea", null).getStatus()).isEqualTo(200);
		}
	}

	@Test
	void leCheminSeLitSousLeCheminDeContexte() throws Exception {
		var requete = requete("POST", "/quiz" + BUZZ, "jeton-lea", "198.51.100.7");
		requete.setContextPath("/quiz");
		for (int i = 0; i < 2; i++) {
			limite.doFilter(requete, new MockHttpServletResponse(), chaine(200));
		}
		var refus = new MockHttpServletResponse();
		limite.doFilter(requete, refus, chaine(200));

		assertThat(refus.getStatus()).isEqualTo(429);
		assertThat(JSON.readTree(refus.getContentAsString()).path("instance").asString()).isEqualTo("/quiz" + BUZZ);
	}

	@Test
	void seulsLesCodesInconnusConsommentLesEssaisDeCode() throws Exception {
		limite = new LimiteDeRequetes(reglages(true, 1_000, new ReglagesDesLimites.Seuil(100, Duration.ofSeconds(1))), horloge, JSON);
		for (int statut : new int[] { 201, 201, 409, 422, 400, 201 }) {
			assertThat(envoyer("POST", REJOINDRE, null, statut).getStatus()).isEqualTo(statut);
		}
		assertThat(envoyer("POST", REJOINDRE, null, 404).getStatus()).isEqualTo(404);
		assertThat(envoyer("POST", REJOINDRE, null, 404).getStatus()).isEqualTo(404);

		MockHttpServletResponse refus = envoyer("POST", REJOINDRE, null, 201);
		assertThat(refus.getStatus()).isEqualTo(429);
		assertThat(refus.getHeader("Retry-After")).isEqualTo("3");
		assertThat(JSON.readTree(refus.getContentAsString()).path("detail").asString())
				.isEqualTo("Trop de requêtes en peu de temps. Réessaie dans 3 secondes.");
		assertThat(envoyer("POST", REJOINDRE, null, 404, "203.0.113.50").getStatus()).isEqualTo(404);

		horloge.avancer(Duration.ofSeconds(3));
		assertThat(envoyer("POST", REJOINDRE, null, 201).getStatus()).isEqualTo(201);
	}

	@Test
	void lesArriveesDUneMemeAdresseRestentPlafonnees() throws Exception {
		for (int i = 0; i < 3; i++) {
			assertThat(envoyer("POST", REJOINDRE, null, 201).getStatus()).isEqualTo(201);
		}
		MockHttpServletResponse refus = envoyer("POST", REJOINDRE, null, 201);

		assertThat(refus.getStatus()).isEqualTo(429);
		assertThat(refus.getHeader("Retry-After")).isEqualTo("1");
	}

	@Test
	void unRefusDArriveeNeConsommeAucunEssaiDeCode() throws Exception {
		for (int i = 0; i < 3; i++) {
			envoyer("POST", REJOINDRE, null, 201);
		}
		for (int i = 0; i < 5; i++) {
			assertThat(envoyer("POST", REJOINDRE, null, 404).getStatus()).isEqualTo(429);
		}
		horloge.avancer(Duration.ofSeconds(1));

		assertThat(envoyer("POST", REJOINDRE, null, 404).getStatus()).isEqualTo(404);
		horloge.avancer(Duration.ofSeconds(1));
		assertThat(envoyer("POST", REJOINDRE, null, 404).getStatus()).isEqualTo(404);
	}

	@Test
	void uneAdresseIpv6CompteParBlocDe64Bits() throws Exception {
		limite = new LimiteDeRequetes(reglages(true, 1_000, new ReglagesDesLimites.Seuil(100, Duration.ofSeconds(1))), horloge, JSON);
		envoyer("POST", REJOINDRE, null, 404, "2001:db8:1:2::1");
		envoyer("POST", REJOINDRE, null, 404, "2001:db8:1:2:ffff:ffff:ffff:ffff");

		assertThat(envoyer("POST", REJOINDRE, null, 404, "2001:db8:1:2::abcd").getStatus()).isEqualTo(429);
		assertThat(envoyer("POST", REJOINDRE, null, 404, "2001:db8:1:3::1").getStatus()).isEqualTo(404);
		assertThat(envoyer("POST", REJOINDRE, null, 404, "::ffff:203.0.113.9").getStatus()).isEqualTo(404);
	}

	@Test
	void lAccesDEcranCompteParAdresseEtParSoiree() throws Exception {
		String autreSoiree = "/api/soirees/" + UUID.randomUUID();
		envoyer("POST", SOIREE + "/acces-ecran", "demo:ANIMATEUR", null);
		envoyer("POST", SOIREE + "/acces-ecran", "demo:ANIMATEUR", null);

		MockHttpServletResponse refus = envoyer("POST", SOIREE + "/acces-ecran", "autre-animateur", null);
		assertThat(refus.getStatus()).isEqualTo(429);
		assertThat(refus.getHeader("Retry-After")).isEqualTo("6");
		assertThat(envoyer("POST", autreSoiree + "/acces-ecran", null, null).getStatus()).isEqualTo(200);
		assertThat(envoyer("POST", SOIREE + "/acces-ecran", null, null, "203.0.113.77").getStatus()).isEqualTo(200);
	}

	@Test
	void leQrCodeCompteParCleDEcran() throws Exception {
		envoyer("GET", SOIREE + "/qrcode", "cle-ecran", null);
		envoyer("GET", SOIREE + "/qrcode", "cle-ecran", null);

		assertThat(envoyer("GET", SOIREE + "/qrcode", "cle-ecran", null).getStatus()).isEqualTo(429);
		assertThat(envoyer("GET", SOIREE + "/qrcode", "autre-cle", null).getStatus()).isEqualTo(200);
	}

	@Test
	void uneLimiteDesactiveeLaisseToutPasser() throws Exception {
		limite = new LimiteDeRequetes(reglages(false, 1_000), horloge, JSON);
		for (int i = 0; i < 20; i++) {
			assertThat(envoyer("POST", BUZZ, "jeton-lea", null).getStatus()).isEqualTo(200);
		}
	}

	@Test
	void lesSeauxRedevenusPleinsSontPurges() throws Exception {
		envoyer("POST", BUZZ, "jeton-lea", null);
		envoyer("POST", BUZZ, "jeton-sam", null);
		assertThat(limite.nombreDeSeaux()).isEqualTo(2);

		horloge.avancer(Duration.ofMinutes(1));
		envoyer("POST", BUZZ, "jeton-tom", null);

		assertThat(limite.nombreDeSeaux()).isEqualTo(1);
	}

	@Test
	void auPlafondUneNouvelleCleNEstNiRefuseeNiRetenue() throws Exception {
		limite = new LimiteDeRequetes(reglages(true, 2), horloge, JSON);
		envoyer("POST", BUZZ, "jeton-lea", null);
		envoyer("POST", BUZZ, "jeton-lea", null);
		envoyer("POST", BUZZ, "jeton-sam", null);

		for (int i = 0; i < 5; i++) {
			assertThat(envoyer("POST", BUZZ, "jeton-tom", null).getStatus()).isEqualTo(200);
		}
		assertThat(limite.nombreDeSeaux()).isEqualTo(2);
		assertThat(envoyer("POST", BUZZ, "jeton-lea", null).getStatus()).isEqualTo(429);
	}

	private MockHttpServletResponse envoyer(String methode, String chemin, String jeton, Integer statut) throws Exception {
		return envoyer(methode, chemin, jeton, statut, "192.0.2.10");
	}

	private MockHttpServletResponse envoyer(String methode, String chemin, String jeton, Integer statut, String adresse)
			throws Exception {
		var reponse = new MockHttpServletResponse();
		limite.doFilter(requete(methode, chemin, jeton, adresse), reponse, chaine(statut == null ? 200 : statut));
		return reponse;
	}

	private static MockHttpServletRequest requete(String methode, String chemin, String jeton, String adresse) {
		var requete = new MockHttpServletRequest(methode, chemin);
		requete.setRemoteAddr(adresse);
		if (jeton != null) {
			requete.addHeader("Authorization", "Bearer " + jeton);
		}
		return requete;
	}

	private FilterChain chaine(int statut) {
		return (requete, reponse) -> {
			traitees.incrementAndGet();
			((HttpServletResponse) reponse).setStatus(statut);
		};
	}

	private static ReglagesDesLimites reglages(boolean actif, int plafond) {
		return reglages(actif, plafond, new ReglagesDesLimites.Seuil(3, Duration.ofSeconds(1)));
	}

	private static ReglagesDesLimites reglages(boolean actif, int plafond, ReglagesDesLimites.Seuil arrivees) {
		return new ReglagesDesLimites(actif, plafond, DEUX_PAR_SECONDE, DEUX_PAR_SECONDE,
				new ReglagesDesLimites.Seuil(2, Duration.ofSeconds(3)), arrivees,
				new ReglagesDesLimites.Seuil(2, Duration.ofSeconds(6)), DEUX_PAR_SECONDE);
	}

}
