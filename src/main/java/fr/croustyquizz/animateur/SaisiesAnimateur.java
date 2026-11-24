package fr.croustyquizz.animateur;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.JsonNode;

final class SaisiesAnimateur {

	private SaisiesAnimateur() {
	}

	record Inscription(
			@Valid LieuSaisi lieu,
			@NotBlank @CodePointLength(max = 120) @Pattern(regexp = "[^\\x00]*") String nom,
			@NotBlank @Email @CodePointLength(max = 254) String email,
			@CodePointLength(min = 12, max = 128) @Pattern(regexp = "[^\\p{Cs}]*") String motDePasse) {

		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		static Inscription lire(JsonNode corps) {
			verifierObjet(corps, 4);
			JsonNode lieu = corps.path("lieu");
			verifierObjet(lieu, 2);
			return new Inscription(new LieuSaisi(texte(lieu, "nom"), texte(lieu, "ville")),
					texte(corps, "nom"), texte(corps, "email"), texte(corps, "motDePasse"));
		}

		@Override
		public String toString() {
			return "Inscription[***]";
		}
	}

	record LieuSaisi(
			@NotBlank @CodePointLength(max = 120) @Pattern(regexp = "[^\\x00]*") String nom,
			@NotBlank @CodePointLength(max = 120) @Pattern(regexp = "[^\\x00]*") String ville) {
	}

	record Connexion(
			@NotBlank @Email @CodePointLength(max = 254) String email,
			@CodePointLength(min = 1, max = 128) @Pattern(regexp = "[^\\p{Cs}]*") String motDePasse) {

		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		static Connexion lire(JsonNode corps) {
			verifierObjet(corps, 2);
			return new Connexion(texte(corps, "email"), texte(corps, "motDePasse"));
		}

		@Override
		public String toString() {
			return "Connexion[***]";
		}
	}

	static void verifierObjet(JsonNode objet, int nombreDeChamps) {
		if (!objet.isObject() || objet.size() != nombreDeChamps) {
			throw new IllegalArgumentException("La structure du corps est invalide.");
		}
	}

	private static String texte(JsonNode objet, String champ) {
		JsonNode valeur = objet.path(champ);
		if (!valeur.isString()) {
			throw new IllegalArgumentException("Un champ texte obligatoire est absent ou invalide.");
		}
		return valeur.asString();
	}
}
