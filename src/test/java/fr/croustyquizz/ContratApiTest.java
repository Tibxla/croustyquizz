package fr.croustyquizz;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

class ContratApiTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String HEURE = "{\"maintenant\":\"2026-11-10T12:00:00Z\"}";
	private static final String COMPTE = """
			{"animateur":{"id":"00000000-0000-0000-0000-000000000001","nom":"Léa",
			"lieuId":"00000000-0000-0000-0000-000000000002"},
			"lieu":{"id":"00000000-0000-0000-0000-000000000002","nom":"Lieu fictif","ville":"Paris"}}
			""";
	private static final String PROBLEME = """
			{"type":"about:blank","title":"Authentification requise","status":401,
			"code":"AUTHENTIFICATION_REQUISE","detail":"Veuillez vous connecter.","instance":"/api/animateurs/session"}
			""";

	@Test
	void unChampRenommeEstRefuseAvecSonCheminJson() throws Exception {
		MvcResult resultat = reponse(200, "application/json", "{\"heure\":\"2026-11-10T12:00:00Z\"}");

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "consulterHeure", resultat))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("consulterHeure")
				.hasMessageContaining("$")
				.hasMessageContaining("maintenant");
	}

	@Test
	void unChampAjouteEstRefuse() throws Exception {
		MvcResult resultat = reponse(200, "application/json", HEURE.replace("}", ",\"secret\":\"interdit\"}"));

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "consulterHeure", resultat))
				.isInstanceOf(AssertionError.class).hasMessageContaining("secret").hasMessageContaining("$");
	}

	@Test
	void uneHeureConformeEstAccepteeAvecLeCharsetHttp() throws Exception {
		ContratApi.verifier(CONTRAT, "consulterHeure", reponse(200, "application/json;charset=UTF-8", HEURE));
	}

	@ParameterizedTest
	@ValueSource(strings = { "42", "\"pas une date\"" })
	void leTypeEtLeFormatDeLHeureSontVerifies(String maintenant) throws Exception {
		MvcResult resultat = reponse(200, "application/json", "{\"maintenant\":" + maintenant + "}");

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "consulterHeure", resultat))
				.isInstanceOf(AssertionError.class).hasMessageContaining("$.maintenant");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "{", "null", "[]", "{} {}" })
	void unCorpsAbsentOuInvalideEstRefuse(String corps) throws Exception {
		MvcResult resultat = reponse(200, "application/json", corps);

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "consulterHeure", resultat))
				.isInstanceOf(AssertionError.class).hasMessageContaining("consulterHeure");
	}

	@Test
	void unStatutNonDeclareEstRefuseMemeSiLeCorpsEstConforme() throws Exception {
		MvcResult resultat = reponse(201, "application/json", HEURE);

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "consulterHeure", resultat))
				.isInstanceOf(AssertionError.class).hasMessageContaining("HTTP 201").hasMessageContaining("statut non déclaré");
	}

	@Test
	void uneOperationInconnueEstSignalee() throws Exception {
		MvcResult resultat = reponse(200, "application/json", HEURE);

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "operationInconnue", resultat))
				.isInstanceOf(AssertionError.class).hasMessageContaining("Opération inconnue").hasMessageContaining("operationInconnue");
	}

	@Test
	void unTypeDeContenuNonDeclareEstRefuse() throws Exception {
		MvcResult resultat = reponse(200, "text/plain", HEURE);

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "consulterHeure", resultat))
				.isInstanceOf(AssertionError.class).hasMessageContaining("type de contenu non déclaré");
	}

	@Test
	void unTypeDeContenuAbsentEstSignale() throws Exception {
		MvcResult resultat = reponse(200, null, HEURE);

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "consulterHeure", resultat))
				.isInstanceOf(AssertionError.class).hasMessageContaining("Content-Type manquant");
	}

	@Test
	void lesReferencesInternesAuDocumentExterneSontResolues() throws Exception {
		ContratApi.verifier(CONTRAT, "inscrireAnimateur", reponse(201, "application/json", COMPTE));
	}

	@Test
	void uneErreurImbriqueeDansLeDocumentExterneEstLocalisee() throws Exception {
		MvcResult resultat = reponse(201, "application/json", COMPTE.replace("\"Paris\"", "42"));

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "inscrireAnimateur", resultat))
				.isInstanceOf(AssertionError.class).hasMessageContaining("$.lieu.ville");
	}

	@Test
	void uneReponseDErreurReferenceeDansLesComposantsEstAcceptee() throws Exception {
		ContratApi.verifier(CONTRAT, "consulterAnimateur", reponse(401, "application/problem+json", PROBLEME));
	}

	@Test
	void leSchemaDUneReponseDErreurReferenceeEstVerifie() throws Exception {
		MvcResult resultat = reponse(401, "application/problem+json", PROBLEME.replace("AUTHENTIFICATION_REQUISE", "INCONNU"));

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "consulterAnimateur", resultat))
				.isInstanceOf(AssertionError.class).hasMessageContaining("$.code");
	}

	@Test
	void uneReponseDeclareeSansCorpsEstAcceptee() throws Exception {
		ContratApi.verifier(CONTRAT, "deconnecterAnimateur", reponse(204, null, ""));
	}

	@Test
	void unCorpsNonDeclareEstRefuse() throws Exception {
		MvcResult resultat = reponse(204, "application/json", "{}");

		assertThatThrownBy(() -> ContratApi.verifier(CONTRAT, "deconnecterAnimateur", resultat))
				.isInstanceOf(AssertionError.class).hasMessageContaining("réponse sans corps attendue");
	}

	private static MvcResult reponse(int statut, String type, String corps) throws Exception {
		MockHttpServletResponse reponse = new MockHttpServletResponse();
		reponse.setStatus(statut);
		reponse.setContentType(type);
		reponse.getWriter().write(corps);
		MvcResult resultat = mock(MvcResult.class);
		when(resultat.getResponse()).thenReturn(reponse);
		return resultat;
	}
}
