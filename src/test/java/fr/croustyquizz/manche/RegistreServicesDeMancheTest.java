package fr.croustyquizz.manche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import fr.croustyquizz.tempsreel.VerrousSoiree;

/** Départ d'un Joueur (#205) : répartition vers les Manches actives, sans Spring. */
class RegistreServicesDeMancheTest {
	private static final UUID SOIREE = new UUID(0, 1);
	private static final UUID JOUEUR = new UUID(0, 2);
	private static final Instant CREEE_LE = Instant.parse("2026-11-25T20:00:00Z");

	private final TransactionsNotees transactions = new TransactionsNotees();

	/**
	 * L'Exclusion est déjà validée quand elle appelle le Départ : l'échec d'une Manche ne doit ni remonter jusqu'à
	 * elle ni priver les autres Manches du retrait.
	 */
	@Test
	void lEchecDUneMancheNeRemontePasEtNePriveLesAutresDuDepart() {
		ManchePersistante enPanne = active(ModeDeJeu.KARAOKE, EtatManche.EN_COURS);
		ManchePersistante suivante = active(ModeDeJeu.FREESTYLE, EtatManche.EN_PAUSE);
		ServiceDeManche<?, ?> karaoke = service(ModeDeJeu.KARAOKE);
		ServiceDeManche<?, ?> freestyle = service(ModeDeJeu.FREESTYLE);
		doAnswer(appel -> { throw new IllegalStateException("Panne factice"); })
				.when(karaoke).retirerJoueurParti(SOIREE, enPanne.getMancheId(), JOUEUR);
		var registre = new RegistreServicesDeManche(List.of(karaoke, freestyle), new Depot(List.of(enPanne, suivante)),
				new VerrousSoiree(), transactions);

		assertThatCode(() -> registre.retirerJoueurParti(SOIREE, JOUEUR)).doesNotThrowAnyException();

		verify(freestyle).retirerJoueurParti(SOIREE, suivante.getMancheId(), JOUEUR);
	}

	/** Le dépôt se lit dans une transaction, refermée avant que chaque service ouvre la sienne. */
	@Test
	void lesManchesActivesSeLisentDansUneTransactionFermeeAvantLeRetrait() {
		ManchePersistante manche = active(ModeDeJeu.KARAOKE, EtatManche.EN_COURS);
		ServiceDeManche<?, ?> karaoke = service(ModeDeJeu.KARAOKE);
		var transactionsPendantLeRetrait = new java.util.ArrayList<Boolean>();
		doAnswer(appel -> transactionsPendantLeRetrait.add(transactions.ouverte))
				.when(karaoke).retirerJoueurParti(SOIREE, manche.getMancheId(), JOUEUR);
		var depot = new Depot(List.of(manche));
		var registre = new RegistreServicesDeManche(List.of(karaoke), depot, new VerrousSoiree(), transactions);

		registre.retirerJoueurParti(SOIREE, JOUEUR);

		assertThat(depot.lectureEnTransaction).isTrue();
		assertThat(transactionsPendantLeRetrait).containsExactly(false);
		assertThat(transactions.validees).isEqualTo(1);
	}

	private static ServiceDeManche<?, ?> service(ModeDeJeu mode) {
		ServiceDeManche<?, ?> service = mock(ServiceDeManche.class);
		when(service.mode()).thenReturn(mode);
		return service;
	}

	private static ManchePersistante active(ModeDeJeu mode, EtatManche etat) {
		var manche = new ManchePersistante(UUID.randomUUID(), SOIREE, mode, "{}", CREEE_LE);
		manche.changerEtat(etat, CREEE_LE);
		return manche;
	}

	/** Gestionnaire factice : note si une transaction est ouverte. */
	private static final class TransactionsNotees implements PlatformTransactionManager {
		private boolean ouverte;
		private int validees;
		@Override public TransactionStatus getTransaction(TransactionDefinition definition) {
			ouverte = true;
			return new SimpleTransactionStatus();
		}
		@Override public void commit(TransactionStatus statut) { ouverte = false; validees++; }
		@Override public void rollback(TransactionStatus statut) { ouverte = false; }
	}

	/** Dépôt factice : seule la lecture des Manches de la Soirée est attendue. */
	private final class Depot implements DepotDeManches {
		private final List<ManchePersistante> manches;
		private boolean lectureEnTransaction;
		Depot(List<ManchePersistante> manches) { this.manches = manches; }
		@Override public List<ManchePersistante> deLaSoiree(UUID soireeId) {
			lectureEnTransaction = transactions.ouverte;
			return manches;
		}
		@Override public ManchePersistante charger(UUID mancheId) { throw new UnsupportedOperationException(); }
		@Override public void enregistrer(ManchePersistante manche) { throw new UnsupportedOperationException(); }
		@Override public void retirer(ManchePersistante manche) { throw new UnsupportedOperationException(); }
		@Override public EnvoiDeManche retrouverEnvoi(UUID cle) { throw new UnsupportedOperationException(); }
		@Override public void enregistrerEnvoi(EnvoiDeManche envoi) { throw new UnsupportedOperationException(); }
	}
}
