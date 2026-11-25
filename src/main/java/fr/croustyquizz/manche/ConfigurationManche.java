package fr.croustyquizz.manche;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Horloge de production ; les minuteurs sont sérialisés par les services, par Soirée. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProprietesManche.class)
class ConfigurationManche {
	@Bean(destroyMethod = "shutdown")
	ScheduledExecutorService minuteursDeManche() { return Executors.newScheduledThreadPool(2); }
	@Bean
	Horloge horlogeDeManche(Clock horloge, ScheduledExecutorService minuteursDeManche) {
		return new HorlogeSysteme(horloge, minuteursDeManche);
	}
}
