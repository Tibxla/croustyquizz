package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.Acteur;

/** Clés d'Écran de salle et reconnaissance des secrets, sur un vrai PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ TestcontainersConfiguration.class, ServiceSoiree.class, ServiceDesAcces.class, TirageAuHasard.class,
		ServiceDesAccesTest.Reglages.class })
class ServiceDesAccesTest {

	static final Instant DEBUT = Instant.parse("2026-11-12T19:30:00Z");

	/** Horloge que le test fait avancer. */
	static final class HorlogeReglable extends Clock {

		private volatile Instant maintenant = DEBUT;

		void avancerA(Instant instant) {
			maintenant = instant;
		}

		@Override
		public Instant instant() {
			return maintenant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Reglages {

		@Bean
		@Primary
		HorlogeReglable horlogeReglable() {
			return new HorlogeReglable();
		}

	}

	@Autowired
	private ServiceDesAcces acces;

	@Autowired
	private ServiceSoiree soirees;

	@Autowired
	private HorlogeReglable horloge;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void effacerLaBase() {
		horloge.avancerA(DEBUT);
		jdbc.update("delete from acces_ecran");
		jdbc.update("delete from joueur");
		jdbc.update("delete from soiree");
	}

	@Test
	void uneCleDEcranReconnaitLEcranDeSaSoireeJusquASonExpiration() {
		UUID soiree = soireeOuverte().getId();

		OuvertureEcran ouverture = acces.ouvrirEcran(soiree);

		assertThat(ouverture.acces().getExpireLe()).isEqualTo(DEBUT.plus(AccesEcran.DUREE));
		assertThat(acces.reconnaitre(ouverture.cle())).contains(
				new Acteur(Acteur.Type.ECRAN, ouverture.acces().getId(), soiree));
		horloge.avancerA(ouverture.acces().getExpireLe());
		assertThat(acces.reconnaitre(ouverture.cle())).isEmpty();
	}

	@Test
	void neStockeQueLEmpreinteDeLaCle() {
		OuvertureEcran ouverture = acces.ouvrirEcran(soireeOuverte().getId());

		byte[] stockee = jdbc.queryForObject("select empreinte_cle from acces_ecran where id = ?", byte[].class,
				ouverture.acces().getId());
		assertThat(stockee).isEqualTo(Secrets.empreinte(ouverture.cle()));
		assertThat(ouverture.toString()).doesNotContain(ouverture.cle());
	}

	@Test
	void unJetonDeReconnexionReconnaitSonJoueurSaufSIlEstExclu() {
		Soiree soiree = soireeOuverte();
		ArriveeDeJoueur arrivee = soirees.rejoindre(soiree.getCodeAcces(), "Léa");
		String jeton = arrivee.jeton().valeur();

		assertThat(acces.reconnaitre(jeton)).contains(
				new Acteur(Acteur.Type.JOUEUR, arrivee.joueur().getId(), soiree.getId()));
		jdbc.update("update joueur set exclu = true where id = ?", arrivee.joueur().getId());
		assertThat(acces.reconnaitre(jeton)).isEmpty();
	}

	@Test
	void unSecretInconnuOuMalFormeNeReconnaitPersonne() {
		assertThat(acces.reconnaitre(null)).isEmpty();
		assertThat(acces.reconnaitre("demo:ECRAN:" + UUID.randomUUID() + ":" + UUID.randomUUID())).isEmpty();
		assertThat(acces.reconnaitre("a".repeat(43))).isEmpty();
	}

	@Test
	void uneSoireeTermineeOuInconnueNOuvrePasDEcran() {
		UUID terminee = soireeOuverte().getId();
		jdbc.update("update soiree set etat = 'TERMINEE' where id = ?", terminee);

		assertThatThrownBy(() -> acces.ouvrirEcran(terminee)).isInstanceOf(SoireeDejaLancee.class);
		assertThatThrownBy(() -> acces.ouvrirEcran(UUID.randomUUID())).isInstanceOf(SoireeIntrouvable.class);
	}

	private Soiree soireeOuverte() {
		return soirees.ouvrir(soirees.creer("Quiz du jeudi", Format.SOLO, Pilotage.MANUEL).getId());
	}

}
