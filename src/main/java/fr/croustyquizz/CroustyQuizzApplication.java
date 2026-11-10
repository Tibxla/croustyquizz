package fr.croustyquizz;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CroustyQuizzApplication {

	public static void main(String[] args) {
		SpringApplication.run(CroustyQuizzApplication.class, args);
	}

	/** Heure du serveur, injectée pour qu'un test puisse la figer. */
	@Bean
	Clock horloge() {
		return Clock.systemUTC();
	}

}
