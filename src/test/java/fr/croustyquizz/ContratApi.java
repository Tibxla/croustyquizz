package fr.croustyquizz;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Vérifie le statut et le corps JSON, sans vérifier les en-têtes métier ni l'authentification.
 * Utilisation dans un test MockMvc :
 * {@code ContratApi.verifier("docs/api/openapi.yaml", "consulterHeure", resultat);}
 * Les chemins de fichiers sont relatifs à la racine du projet, comme dans Maven.
 */
public final class ContratApi {

	private static final YAMLMapper YAML = YAMLMapper.builder().build();
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ContratApi() {
	}

	public static void verifier(String fichierContrat, String operationId, MvcResult resultat) throws IOException {
		Path fichier = Path.of(fichierContrat).toAbsolutePath().normalize();
		JsonNode contrat = YAML.readTree(fichier.toFile());
		String operation = trouverOperation(contrat, operationId);
		int statut = resultat.getResponse().getStatus();
		String contexte = fichierContrat + " : " + operationId + " (HTTP " + statut + ")";
		String reponse = operation + "/responses/" + statut;
		if (contrat.at(reponse).isMissingNode()) {
			throw new AssertionError(contexte + " : statut non déclaré dans le contrat");
		}
		reponse = resoudreReference(contrat, reponse);
		JsonNode contenu = contrat.at(reponse).path("content");
		String corps = resultat.getResponse().getContentAsString();
		if (contenu.isMissingNode()) {
			if (!corps.isEmpty()) {
				throw new AssertionError(contexte + " : réponse sans corps attendue");
			}
			return;
		}
		String type = resultat.getResponse().getContentType();
		if (type == null) {
			throw new AssertionError(contexte + " : Content-Type manquant");
		}
		for (var representation : contenu.properties()) {
			if (MediaType.parseMediaType(representation.getKey()).isCompatibleWith(MediaType.parseMediaType(type))) {
				String schema = reponse + "/content/" + echapper(representation.getKey()) + "/schema";
				validerSchema(fichier, schema, corps, contexte);
				return;
			}
		}
		throw new AssertionError(contexte + " : type de contenu non déclaré : " + type);
	}

	private static String trouverOperation(JsonNode contrat, String operationId) {
		for (var chemin : contrat.path("paths").properties()) {
			for (var methode : chemin.getValue().properties()) {
				if (operationId.equals(methode.getValue().path("operationId").asString())) {
					return "/paths/" + echapper(chemin.getKey()) + "/" + methode.getKey();
				}
			}
		}
		throw new AssertionError("Opération inconnue dans le contrat : " + operationId);
	}

	private static String resoudreReference(JsonNode contrat, String pointeur) {
		String courant = pointeur;
		var visitees = new HashSet<String>();
		while (contrat.at(courant).has("$ref")) {
			String reference = contrat.at(courant).path("$ref").asString();
			if (!reference.startsWith("#/") || !visitees.add(reference)) {
				throw new AssertionError("Référence de réponse non locale ou cyclique : " + reference);
			}
			courant = reference.substring(1);
		}
		if (contrat.at(courant).isMissingNode()) {
			throw new AssertionError("Référence de réponse introuvable : " + courant);
		}
		return courant;
	}

	private static void validerSchema(Path fichier, String pointeur, String corps, String contexte) {
		var configuration = SchemaRegistryConfig.builder().formatAssertionsEnabled(true)
				.locale(Locale.FRENCH).pathType(PathType.JSON_PATH).build();
		var registre = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
				constructeur -> constructeur.schemaRegistryConfig(configuration).schemas(ContratApi::lireSchema));
		var schema = registre.getSchema(SchemaLocation.of(fichier.toUri() + "#" + pointeur));
		try {
			JsonNode valeur = JSON.readTree(corps);
			if (valeur == null || valeur.isMissingNode()) {
				throw new AssertionError(contexte + " : corps JSON attendu");
			}
			var erreurs = schema.validate(valeur);
			if (!erreurs.isEmpty()) {
				throw new AssertionError(contexte + " : corps JSON non conforme\n" + erreurs.stream()
						.map(erreur -> erreur.getInstanceLocation() + " : " + erreur.getMessage())
						.sorted().collect(Collectors.joining("\n")));
			}
		} catch (tools.jackson.core.JacksonException exception) {
			throw new AssertionError(contexte + " : corps JSON illisible", exception);
		}
	}

	/** Conserve la base de chaque document pour les $ref, sans téléchargement réseau. */
	private static String lireSchema(String adresse) {
		URI uri = URI.create(adresse);
		if (!"file".equals(uri.getScheme())) {
			throw new AssertionError("Référence hors des contrats locaux : " + adresse);
		}
		return JSON.writeValueAsString(YAML.readTree(Path.of(uri).toFile()));
	}

	private static String echapper(String segment) {
		return segment.replace("~", "~0").replace("/", "~1");
	}
}
