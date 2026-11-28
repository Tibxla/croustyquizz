package fr.croustyquizz.tempsreel;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import fr.croustyquizz.animateur.AnimateurConnecte;
import fr.croustyquizz.animateur.ResolveurAnimateurConnecte;
import fr.croustyquizz.manche.Acteur;

/** Injecte l'identité résolue sans exposer la requête brute aux routes métier. */
@Configuration(proxyBeanMethods = false)
public class ConfigurationActeurHttp implements WebMvcConfigurer {
	private final ObjectProvider<ResolveurDActeur> resolveur;
	public ConfigurationActeurHttp(ObjectProvider<ResolveurDActeur> resolveur) { this.resolveur = resolveur; }
	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolveurs) {
		resolveurs.add(new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parametre) {
				return parametre.getParameterType() == Acteur.class || parametre.getParameterType() == AnimateurConnecte.class;
			}
			@Override
			public Object resolveArgument(MethodParameter parametre, ModelAndViewContainer modele,
					NativeWebRequest requete, WebDataBinderFactory liaison) {
				var servlet = requete.getNativeRequest(HttpServletRequest.class);
				if (parametre.getParameterType() == AnimateurConnecte.class) {
					return ResolveurAnimateurConnecte.resoudre(servlet);
				}
				return resolveur.getObject().resoudre(servlet);
			}
		});
	}
}
