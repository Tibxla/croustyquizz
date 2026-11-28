package fr.croustyquizz.karaoke;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.util.DigestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bibliothèque de base (ADR 0011) : catalogue vérifié au démarrage, fichiers servis en statique. Les sons et
 * les LRC ne changent qu'avec une version de l'application, sous un nouveau nom : cache d'un an. Le catalogue
 * est revalidé à chaque chargement pour qu'un navigateur ne garde pas l'ancien après une mise à jour.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProprietesBibliotheque.class)
public class ConfigurationBibliotheque implements WebMvcConfigurer {
    private final ProprietesBibliotheque proprietes;

    public ConfigurationBibliotheque(ProprietesBibliotheque proprietes) { this.proprietes = proprietes; }

    @Bean
    CatalogueDeBase catalogueDeBase(ResourceLoader chargeur, JsonMapper json) {
        return CatalogueDeBase.charger(chargeur, proprietes.racine(), json);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registre) {
        registre.addResourceHandler("/bibliotheque/*.json").addResourceLocations(proprietes.racine())
                .setCacheControl(CacheControl.noCache()).setUseLastModified(false)
                .setEtagGenerator(ConfigurationBibliotheque::empreinte);
        registre.addResourceHandler("/bibliotheque/**").addResourceLocations(proprietes.racine())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());
    }

    private static String empreinte(Resource catalogue) {
        try (var contenu = catalogue.getInputStream()) {
            return "\"" + DigestUtils.md5DigestAsHex(contenu) + "\"";
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }
}
