package fr.croustyquizz.animateur;

import java.io.IOException;
import java.util.List;
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
import org.springframework.security.web.util.matcher.RequestMatcher;

import fr.croustyquizz.manche.Probleme;
import fr.croustyquizz.manche.Refuse;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class ConfigurationSecuriteAnimateur {

	private static final RequestMatcher AVEC_BEARER = requete -> {
		String autorisation = requete.getHeader(HttpHeaders.AUTHORIZATION);
		return autorisation != null && autorisation.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length());
	};

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
		// Le filtre et son pare-feu ne traitent que les routes de compte.
		filtre.setUrlPatterns(List.of(AnimateurControleur.CSRF, AnimateurControleur.INSCRIPTION, AnimateurControleur.SESSION));
		return filtre;
	}

	@Bean
	SecurityFilterChain securiteAnimateur(HttpSecurity http, JsonMapper json,
			CsrfTokenRepository jetons, HttpSessionSecurityContextRepository sessions) throws Exception {
		return http.securityContext(contexte -> contexte.securityContextRepository(sessions))
				.csrf(csrf -> csrf
						.csrfTokenRepository(jetons)
						.ignoringRequestMatchers(AVEC_BEARER)
						.csrfTokenRequestHandler(new JetonCsrfDansEntete()))
				.authorizeHttpRequests(acces -> acces
						.requestMatchers(AnimateurControleur.CSRF).permitAll()
						.requestMatchers(AVEC_BEARER).denyAll()
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
