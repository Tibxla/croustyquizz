package fr.croustyquizz.campagne;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class EnvoiDeSmsTwilio implements EnvoiDeSms {
	private static final Logger JOURNAL = LoggerFactory.getLogger(EnvoiDeSmsTwilio.class);
	private static final String ROUTE = "/2010-04-01/Accounts/{compte}/Messages.json";
	private static final Pattern SID_MESSAGE = Pattern.compile("(SM|MM)[0-9a-fA-F]{32}");
	private static final Pattern NUMERO_FRANCAIS = Pattern.compile("\\+33[0-9]{9}");
	private static final Pattern NUMERO_INTERNATIONAL = Pattern.compile("\\+[1-9][0-9]{5,14}");
	private static final int TAILLE_MAX_REPONSE = 16 * 1024;

	private final RestClient client;
	private final JsonMapper json = new JsonMapper();
	private final String compte;
	private final String expediteur;
	private final String modeleEssai;

	EnvoiDeSmsTwilio(URI adresse, String compte, String jeton, String expediteur, String modeleEssai, Duration lecture) {
		var http = new SimpleClientHttpRequestFactory();
		http.setConnectTimeout(Duration.ofSeconds(2));
		http.setReadTimeout(lecture);
		this.client = RestClient.builder().baseUrl(adresse.toString()).requestFactory(http)
				.defaultHeaders(entetes -> entetes.setBasicAuth(compte, jeton))
				.build();
		this.compte = compte;
		this.expediteur = expediteur;
		this.modeleEssai = modeleEssai.strip();
	}

	@Override
	public Mode mode() {
		return modeleEssai.isEmpty() ? Mode.TWILIO : Mode.TWILIO_ESSAI;
	}

	@Override
	public Resultat envoyer(String texte, String numeroInternational) {
		String contenu = modeleEssai.isEmpty() ? texte : modeleEssai;
		byte[] formulaire = ("To=" + encoder(numeroInternational) + "&From=" + encoder(expediteur)
				+ "&Body=" + encoder(contenu)).getBytes(StandardCharsets.UTF_8);
		Resultat resultat;
		try {
			resultat = client.post().uri(ROUTE, compte)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED).contentLength(formulaire.length)
					// L’écriture directe évite la journalisation du formulaire par les convertisseurs HTTP.
					.body(sortie -> sortie.write(formulaire))
					.exchange((requete, reponse) -> lireResultat(reponse));
		} catch (ResourceAccessException erreur) {
			resultat = erreur.contains(SocketTimeoutException.class)
					? new Echec("Twilio n’a pas répondu à temps.") : new Echec("Twilio est injoignable.");
		}
		if (resultat instanceof Echec echec && JOURNAL.isWarnEnabled()) {
			JOURNAL.warn("SMS vers {} : {}", masquer(numeroInternational), echec.motif());
		}
		return resultat;
	}

	private Resultat lireResultat(ClientHttpResponse reponse) throws IOException {
		var statut = reponse.getStatusCode();
		JsonNode corps = lireJson(reponse);
		if (statut.is2xxSuccessful()) {
			String sid = corps.path("sid").asText("");
			return SID_MESSAGE.matcher(sid).matches() ? new Envoye(sid)
					: new Echec("Réponse Twilio sans identifiant de message valide.");
		}
		var code = corps.path("code");
		if (code.isIntegralNumber() && code.canConvertToInt() && code.intValue() > 0) {
			return new Echec("Twilio a refusé le SMS (code " + code.intValue() + ").");
		}
		return new Echec("Twilio a refusé le SMS (HTTP " + statut.value() + ").");
	}

	private JsonNode lireJson(ClientHttpResponse reponse) throws IOException {
		byte[] corps = reponse.getBody().readNBytes(TAILLE_MAX_REPONSE + 1);
		if (corps.length <= TAILLE_MAX_REPONSE && corps.length > 0) {
			try {
				return json.readTree(corps);
			} catch (JacksonException invalide) {
				return json.missingNode();
			}
		}
		return json.missingNode();
	}

	private static String encoder(String valeur) {
		return URLEncoder.encode(valeur, StandardCharsets.UTF_8);
	}

	private static String masquer(String numero) {
		if (!NUMERO_INTERNATIONAL.matcher(numero).matches()) {
			return "••";
		}
		String fin = numero.substring(numero.length() - 2);
		if (NUMERO_FRANCAIS.matcher(numero).matches()) {
			return numero.substring(0, 3) + " " + numero.charAt(3) + " •• •• •• " + fin;
		}
		return "•• " + fin;
	}
}
