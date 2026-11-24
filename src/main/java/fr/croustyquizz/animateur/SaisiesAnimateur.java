package fr.croustyquizz.animateur;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.JsonNode;

final class SaisiesAnimateur {

	private static final String SANS_CARACTERE_NUL = "[^\\x00]*";
	private static final String MOT_DE_PASSE_UNICODE = "[^\\p{Cs}]*";

	private SaisiesAnimateur() {
	}

	record Inscription(
			@Valid LieuSaisi lieu,
			@NotBlank @CodePointLength(max = 120) @Pattern(regexp = SANS_CARACTERE_NUL) String nom,
			@NotBlank @Email @CodePointLength(max = 254) String email,
			@CodePointLength(min = 12, max = 128) @Pattern(regexp = MOT_DE_PASSE_UNICODE) String motDePasse) {

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
			@NotBlank @CodePointLength(max = 120) @Pattern(regexp = SANS_CARACTERE_NUL) String nom,
			@NotBlank @CodePointLength(max = 120) @Pattern(regexp = SANS_CARACTERE_NUL) String ville) {
	}

	record Connexion(
			@NotBlank @Email @CodePointLength(max = 254) String email,
			@CodePointLength(min = 1, max = 128) @Pattern(regexp = MOT_DE_PASSE_UNICODE) String motDePasse) {

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

	record CreationInvitation(@NotBlank @Email @CodePointLength(max = 254) String email) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		static CreationInvitation lire(JsonNode corps) {
			verifierObjet(corps, 1);
			return new CreationInvitation(texte(corps, "email"));
		}
	}

	record ConsultationInvitation(String cle) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		static ConsultationInvitation lire(JsonNode corps) {
			verifierObjet(corps, 1);
			return new ConsultationInvitation(texte(corps, "cle"));
		}

		@Override
		public String toString() {
			return "ConsultationInvitation[***]";
		}
	}

	record AcceptationInvitation(String cle,
			@NotBlank @CodePointLength(max = 120) @Pattern(regexp = SANS_CARACTERE_NUL) String nom,
			@CodePointLength(min = 12, max = 128) @Pattern(regexp = MOT_DE_PASSE_UNICODE) String motDePasse) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		static AcceptationInvitation lire(JsonNode corps) {
			verifierObjet(corps, 3);
			return new AcceptationInvitation(texte(corps, "cle"), texte(corps, "nom"), texte(corps, "motDePasse"));
		}

		@Override
		public String toString() {
			return "AcceptationInvitation[***]";
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
