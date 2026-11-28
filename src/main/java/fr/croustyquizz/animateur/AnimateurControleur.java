package fr.croustyquizz.animateur;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.ReponsesDeCommande;
import fr.croustyquizz.manche.RefusDeCommande;
import fr.croustyquizz.tempsreel.LimiteDeRequetes;

@RestController
class AnimateurControleur {

	static final String CSRF = "/api/csrf";
	static final String INSCRIPTION = "/api/animateurs";
	static final String SESSION = "/api/animateurs/session";

	private final ComptesAnimateur comptes;
	private final LimiteDeRequetes limites;
	private final HttpSessionSecurityContextRepository sessions;
	private final SessionAuthenticationStrategy renouvellement;
	private final LogoutHandler fermeture;

	AnimateurControleur(ComptesAnimateur comptes, LimiteDeRequetes limites, HttpSessionSecurityContextRepository sessions,
			SessionAuthenticationStrategy renouvellement) {
		this.comptes = comptes;
		this.limites = limites;
		this.sessions = sessions;
		this.renouvellement = renouvellement;
		// Invalider la session suffit : le cookie restant ne désigne plus aucune session.
		var sortie = new SecurityContextLogoutHandler();
		sortie.setSecurityContextRepository(sessions);
		this.fermeture = sortie;
	}

	@GetMapping(CSRF)
	ResponseEntity<JetonCsrf> obtenirCsrf(CsrfToken jeton) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(new JetonCsrf(jeton.getHeaderName(), jeton.getToken()));
	}

	@PostMapping(INSCRIPTION)
	ResponseEntity<ComptesAnimateur.CompteCree> inscrireAnimateur(@Valid @RequestBody SaisiesAnimateur.Inscription saisie) {
		return ResponseEntity.created(URI.create(SESSION)).cacheControl(CacheControl.noStore())
				.body(comptes.inscrire(saisie));
	}

	@PostMapping(SESSION)
	ResponseEntity<?> connecterAnimateur(@Valid @RequestBody SaisiesAnimateur.Connexion saisie,
			HttpServletRequest requete, HttpServletResponse reponse) {
		String emailNormalise = ComptesAnimateur.normaliser(saisie.email());
		var refus = limites.verifierEssaisDeConnexion(emailNormalise, requete);
		if (refus.isPresent()) {
			return refus.orElseThrow();
		}
		ComptesAnimateur.IdentiteAnimateur identite;
		try {
			identite = comptes.reconnaitre(emailNormalise, saisie.motDePasse());
		} catch (RefusDeCommande echec) {
			if (!"IDENTIFIANTS_INVALIDES".equals(echec.refus().code())) {
				throw echec;
			}
			return limites.compterEchecDeConnexion(emailNormalise, requete).orElseThrow(() -> echec);
		}
		var authentification = UsernamePasswordAuthenticationToken.authenticated(identite, null, List.of());
		renouvellement.onAuthentication(authentification, requete, reponse);
		var contexte = SecurityContextHolder.createEmptyContext();
		contexte.setAuthentication(authentification);
		SecurityContextHolder.setContext(contexte);
		sessions.saveContext(contexte, requete, reponse);
		new SessionAnimateur(new AnimateurConnecte(identite.id(), identite.lieuId()))
				.attacher(requete.getSession());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(identite);
	}

	@GetMapping(SESSION)
	ResponseEntity<ComptesAnimateur.IdentiteAnimateur> consulterAnimateur(
			@AuthenticationPrincipal ComptesAnimateur.IdentiteAnimateur identite) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(identite);
	}

	@DeleteMapping(SESSION)
	ResponseEntity<Void> deconnecterAnimateur(HttpServletRequest requete, HttpServletResponse reponse,
			Authentication authentification) {
		fermeture.logout(requete, reponse, authentification);
		return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<?> saisieInvalide(HttpServletRequest requete) {
		return ReponsesDeCommande.http(new Refuse(422, "DONNEES_INVALIDES", "Les données saisies sont invalides."),
				requete.getRequestURI());
	}

	record JetonCsrf(String headerName, String token) {
		@Override
		public String toString() {
			return "JetonCsrf[" + headerName + "=***]";
		}
	}
}
