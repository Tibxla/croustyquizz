package fr.croustyquizz.freestyle;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ConfigurationFreestyle {
    @Bean
    @ConditionalOnMissingBean(DictionnaireDeRimes.class)
    DictionnaireDeRimes dictionnaireFreestyle() { return new DictionnaireFreestyleLocal(); }

    @Bean
    FabriqueFreestyle fabriqueFreestyle(DictionnaireDeRimes dictionnaire,
            @Value("${croustyquizz.passage.limite-passages-en-attente}") long limite,
            @Value("${croustyquizz.passage.delai-appel-secondes}") long appel,
            @Value("${croustyquizz.passage.duree-vote-secondes}") long vote) {
        return new FabriqueFreestyle(dictionnaire, limite, Duration.ofSeconds(appel), Duration.ofSeconds(vote));
    }
}
