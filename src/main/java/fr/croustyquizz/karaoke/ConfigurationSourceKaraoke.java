package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.Horloge;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Branchement de la source ; aucune consultation au démarrage. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProprietesSourceKaraoke.class)
public class ConfigurationSourceKaraoke {
    @Bean
    HttpClient clientSourceKaraoke(ProprietesSourceKaraoke proprietes) {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(proprietes.delaiSourceMs())).build();
    }

    @Bean
    SourceDeParoles sourceDeParoles(ProprietesSourceKaraoke proprietes, HttpClient clientSourceKaraoke,
            JsonMapper json, Horloge horlogeDeManche) {
        return new AdaptateurLrclib(proprietes, clientSourceKaraoke, json, horlogeDeManche);
    }
}
