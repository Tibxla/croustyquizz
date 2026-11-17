package fr.croustyquizz.karaoke;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/** Priorité de la liaison stricte sur les seuls corps Karaoké, sans dépendance métier. */
@Configuration(proxyBeanMethods = false)
public class ConfigurationKaraokeHttp implements WebMvcConfigurer {
    private final JsonMapper json;
    public ConfigurationKaraokeHttp(JsonMapper json) { this.json = json; }
    @Override public void extendMessageConverters(List<HttpMessageConverter<?>> convertisseurs) {
        convertisseurs.addFirst(new ConvertisseurCorpsKaraoke(json));
    }
}
