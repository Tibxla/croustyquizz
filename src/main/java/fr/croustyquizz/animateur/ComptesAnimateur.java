package fr.croustyquizz.animateur;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.manche.Refuse;

@Service
class ComptesAnimateur {

	private final JdbcClient jdbc;
	private final PasswordEncoder encodeur;
	private final String hachageSansCompte;

	ComptesAnimateur(JdbcClient jdbc, PasswordEncoder encodeur) {
		this.jdbc = jdbc;
		this.encodeur = encodeur;
		this.hachageSansCompte = encodeur.encode(empreinte("Compte inconnu"));
	}

	@Transactional
	CompteCree inscrire(SaisiesAnimateur.Inscription saisie) {
		var lieu = new Lieu(UUID.randomUUID(), saisie.lieu().nom(), saisie.lieu().ville());
		jdbc.sql("insert into lieu (id, nom, ville) values (:id, :nom, :ville)")
				.param("id", lieu.id()).param("nom", lieu.nom()).param("ville", lieu.ville()).update();
		return new CompteCree(creerCompte(lieu.id(), saisie.nom(), saisie.email(), saisie.motDePasse()), lieu);
	}

	void verifierEmailDisponible(String email) {
		boolean existe = jdbc.sql("select exists (select 1 from animateur where lower(email) = :email)")
				.param("email", normaliser(email)).query(Boolean.class).single();
		if (existe) {
			throw emailDejaUtilise().exception();
		}
	}

	IdentiteAnimateur creerCompte(UUID lieuId, String nom, String email, String motDePasse) {
		String hachage = encodeur.encode(empreinte(motDePasse));
		var animateur = new IdentiteAnimateur(UUID.randomUUID(), nom, lieuId);
		int insertions = jdbc.sql("""
				insert into animateur (id, lieu_id, nom, email, mot_de_passe_hache)
				values (:id, :lieuId, :nom, :email, :hachage)
				on conflict (lower(email)) do nothing
				""")
				.param("id", animateur.id()).param("lieuId", lieuId).param("nom", animateur.nom())
				.param("email", normaliser(email)).param("hachage", hachage).update();
		if (insertions == 0) {
			throw emailDejaUtilise().exception();
		}
		return animateur;
	}

	private static Refuse emailDejaUtilise() {
		return new Refuse(409, "EMAIL_DEJA_UTILISE", "Cet e-mail est déjà utilisé.");
	}

	IdentiteAnimateur reconnaitre(SaisiesAnimateur.Connexion saisie) {
		var compte = jdbc.sql("""
				select id, nom, lieu_id, mot_de_passe_hache from animateur where lower(email) = :email
				""")
				.param("email", normaliser(saisie.email()))
				.query((ligne, numero) -> new Compte(
						new IdentiteAnimateur(ligne.getObject("id", UUID.class), ligne.getString("nom"),
								ligne.getObject("lieu_id", UUID.class)),
						ligne.getString("mot_de_passe_hache")))
				.optional();
		// Un e-mail inconnu passe lui aussi par une vérification BCrypt.
		String hachage = compte.map(Compte::hachage).orElse(hachageSansCompte);
		boolean correspond = encodeur.matches(empreinte(saisie.motDePasse()), hachage);
		if (!correspond || compte.isEmpty()) {
			throw new Refuse(401, "IDENTIFIANTS_INVALIDES", "L’e-mail ou le mot de passe est invalide.").exception();
		}
		return compte.orElseThrow().identite();
	}

	/** La casse est repliée en Java : le lower() de PostgreSQL dépend de la locale de la base. */
	static String normaliser(String email) {
		return email.toLowerCase(Locale.ROOT);
	}

	/** BCrypt accepte au plus 72 octets. L'empreinte encodée couvre tout le mot de passe. */
	private static String empreinte(String motDePasse) {
		try {
			var sha256 = MessageDigest.getInstance("SHA-256");
			sha256.update("CroustyQuizz/animateur:".getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(sha256.digest(motDePasse.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException erreur) {
			throw new IllegalStateException("SHA-256 est toujours disponible dans le JDK.", erreur);
		}
	}

	record CompteCree(IdentiteAnimateur animateur, Lieu lieu) {
	}

	record IdentiteAnimateur(UUID id, String nom, UUID lieuId) implements Serializable {
		private static final long serialVersionUID = 1L;
	}

	record Lieu(UUID id, String nom, String ville) {
	}

	private record Compte(IdentiteAnimateur identite, String hachage) {
		@Override
		public String toString() {
			return "Compte[" + identite + "]";
		}
	}
}
