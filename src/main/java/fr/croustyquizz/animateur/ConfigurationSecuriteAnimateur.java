package fr.croustyquizz.animateur;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import fr.croustyquizz.campagne.CampagnesSmsControleur;
import fr.croustyquizz.manche.Probleme;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.soiree.ServiceSoiree;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class ConfigurationSecuriteAnimateur {

	private static final RequestMatcher AVEC_BEARER = ResolveurAnimateurConnecte::porteUnBearer;
	private static final PathPatternRequestMatcher.Builder CHEMINS = PathPatternRequestMatcher.withDefaults();
	private static final Set<String> METHODES_SURES = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
	private static final String TEST = "/api/test/soirees/{soireeId}";

	@Bean
	PasswordEncoder encodeurMotDePasse() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CsrfTokenRepository jetonsCsrf() {
		return new HttpSessionCsrfTokenRepository();
	}

	@Bean
	HttpSessionSecurityContextRepository sessionsAnimateur() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	SessionAuthenticationStrategy renouvellementDeSession(CsrfTokenRepository jetons) {
		return new CompositeSessionAuthenticationStrategy(List.of(
				new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(jetons)));
	}

	@Bean
	DelegatingFilterProxyRegistrationBean filtreDeSessionAnimateur() {
		var filtre = new DelegatingFilterProxyRegistrationBean(
				AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME);
		// Dans ces préfixes, securityMatcher écarte les routes publiques et les accès Bearer de Soirée.
		filtre.setUrlPatterns(List.of(AnimateurControleur.CSRF, AnimateurControleur.INSCRIPTION, AnimateurControleur.SESSION,
				AnimateurControleur.INVITATIONS, AnimateurControleur.INVITATIONS + "/*",
				CampagnesSmsControleur.ROUTE, CampagnesSmsControleur.ROUTE + "/*",
				"/api/soirees", "/api/soirees/*", "/api/test/soirees/*"));
		return filtre;
	}

	@Bean
	SecurityFilterChain securiteAnimateur(HttpSecurity http, JsonMapper json,
			CsrfTokenRepository jetons, HttpSessionSecurityContextRepository sessions, ServiceSoiree soirees)
			throws Exception {
		var campagnes = CHEMINS.matcher(CampagnesSmsControleur.ROUTE + "/**");
		var compte = new OrRequestMatcher(campagnes, CHEMINS.matcher(AnimateurControleur.CSRF),
				CHEMINS.matcher(AnimateurControleur.INSCRIPTION), CHEMINS.matcher(AnimateurControleur.SESSION),
				CHEMINS.matcher(AnimateurControleur.INVITATIONS), CHEMINS.matcher(AnimateurControleur.CONSULTATION_INVITATION),
				CHEMINS.matcher(AnimateurControleur.ACCEPTATION_INVITATION));
		return http.securityMatcher(new OrRequestMatcher(compte, mutationsParSession(soirees)))
				.securityContext(contexte -> contexte.securityContextRepository(sessions))
				.csrf(csrf -> csrf
						.csrfTokenRepository(jetons)
						.ignoringRequestMatchers(AVEC_BEARER,
								CHEMINS.matcher(HttpMethod.POST, AnimateurControleur.CONSULTATION_INVITATION))
						.csrfTokenRequestHandler(new JetonCsrfDansEntete()))
				.authorizeHttpRequests(acces -> acces
						.requestMatchers(AnimateurControleur.CSRF).permitAll()
						.requestMatchers(new AndRequestMatcher(compte, AVEC_BEARER)).denyAll()
						.requestMatchers(campagnes).authenticated()
						.requestMatchers(AnimateurControleur.INVITATIONS).authenticated()
						.requestMatchers(HttpMethod.POST, AnimateurControleur.SESSION).permitAll()
						.requestMatchers(AnimateurControleur.SESSION).authenticated()
						.anyRequest().permitAll())
				.exceptionHandling(refus -> refus
						.authenticationEntryPoint((requete, reponse, erreur) ->
								ecrireProbleme(json, requete, reponse, refusAcces(requete)))
						.accessDeniedHandler((requete, reponse, erreur) ->
								ecrireProbleme(json, requete, reponse, erreur instanceof CsrfException
										? new Refuse(403, "CSRF_INVALIDE", "Le jeton CSRF est absent ou invalide.")
										: refusAcces(requete))))
				.requestCache(cache -> cache.disable())
				.logout(sortie -> sortie.disable())
				.build();
	}

	/**
	 * Toute méthode non sûre sous /api/soirees, portée par la session et sans Bearer, exige le CSRF :
	 * une route ajoutée plus tard est protégée d'office. Seule l'arrivée d'un Joueur, publique, y échappe.
	 */
	private static RequestMatcher mutationsParSession(ServiceSoiree soirees) {
		var sousLesSoirees = new OrRequestMatcher(CHEMINS.matcher("/api/soirees"), CHEMINS.matcher("/api/soirees/**"));
		var publique = CHEMINS.matcher(HttpMethod.POST, "/api/soirees/rejoindre");
		RequestMatcher metier = requete -> !METHODES_SURES.contains(requete.getMethod())
				&& sousLesSoirees.matches(requete) && !publique.matches(requete);
		var test = new OrRequestMatcher(CHEMINS.matcher(HttpMethod.POST, TEST + "/manches"),
				CHEMINS.matcher(HttpMethod.POST, TEST + "/manches/{mancheId}/demarrer"),
				CHEMINS.matcher(HttpMethod.PUT, TEST + "/pilotage"));
		return requete -> !AVEC_BEARER.matches(requete) && SessionAnimateur.de(requete.getSession(false)) != null
				&& (metier.matches(requete) || mutationDeTestEnregistree(test.matcher(requete), soirees));
	}

	private static boolean mutationDeTestEnregistree(RequestMatcher.MatchResult correspondance, ServiceSoiree soirees) {
		if (!correspondance.isMatch()) {
			return false;
		}
		try {
			return soirees.estEnregistree(UUID.fromString(correspondance.getVariables().get("soireeId")));
		} catch (IllegalArgumentException invalide) {
			return false; // Spring MVC répondra 400 pour l'identifiant mal formé.
		}
	}

	private static Refuse refusAcces(HttpServletRequest requete) {
		if (HttpMethod.POST.matches(requete.getMethod()) && AVEC_BEARER.matches(requete)) {
			return new Refuse(403, "ACCES_REFUSE", "Cette opération utilise la session du navigateur.");
		}
		return new Refuse(401, "AUTHENTIFICATION_REQUISE", "Connectez-vous pour accéder à votre compte.");
	}

	private static void ecrireProbleme(JsonMapper json, HttpServletRequest requete, HttpServletResponse reponse,
			Refuse refus) throws IOException {
		reponse.setStatus(refus.statutHttp());
		reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		reponse.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
		json.writeValue(reponse.getOutputStream(), Probleme.de(refus, requete.getRequestURI()));
	}

	private static final class JetonCsrfDansEntete implements CsrfTokenRequestHandler {
		private final XorCsrfTokenRequestAttributeHandler gestionnaire = new XorCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(HttpServletRequest requete, HttpServletResponse reponse, Supplier<CsrfToken> jeton) {
			gestionnaire.handle(requete, reponse, jeton);
		}

		@Override
		public String resolveCsrfTokenValue(HttpServletRequest requete, CsrfToken jeton) {
			return requete.getHeader(jeton.getHeaderName()) == null ? null
					: gestionnaire.resolveCsrfTokenValue(requete, jeton);
		}
	}
}
