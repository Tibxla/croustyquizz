package fr.croustyquizz.manche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Routage d'une Manche vers le service de son mode (#240), sans Spring. Les faux notent si la lecture tourne dans une
 * transaction en lecture seule, ce que {@code StockageMancheFactice} ne permet pas d'observer.
 */
class RegistreServicesDeMancheTest {
	private static final UUID SOIREE = new UUID(0, 1);
	private static final Instant CREEE_LE = Instant.parse("2026-11-25T20:00:00Z");

	private final TransactionsNotees transactions = new TransactionsNotees();

	/** Le dépôt se lit dans une transaction en lecture seule, validée avant de rendre le service. */
	@Test
	void laMancheSeLitDansUneTransactionEnLectureSeuleFermeeAvantDeRendreLeService() {
		ManchePersistante manche = manche(SOIREE, ModeDeJeu.KARAOKE);
		ServiceDeManche<?, ?> karaoke = service(ModeDeJeu.KARAOKE);
		var depot = new Depot(List.of(manche));
		var registre = new RegistreServicesDeManche(List.of(karaoke), depot, transactions);

		assertThat(registre.pourManche(SOIREE, manche.getMancheId())).isSameAs(karaoke);

		assertThat(depot.lectureEnTransaction).isTrue();
		assertThat(transactions.lectureSeule).isTrue();
		assertThat(transactions.ouverte).isFalse();
		assertThat(transactions.validees).isEqualTo(1);
	}

	/** Une Manche absente ou d'une autre Soirée reste introuvable, et la lecture est tout de même validée. */
	@Test
	void uneMancheAbsenteOuDUneAutreSoireeEstIntrouvable() {
		ManchePersistante ailleurs = manche(new UUID(0, 9), ModeDeJeu.KARAOKE);
		var registre = new RegistreServicesDeManche(List.of(service(ModeDeJeu.KARAOKE)), new Depot(List.of(ailleurs)),
				transactions);

		for (UUID mancheId : List.of(ailleurs.getMancheId(), new UUID(0, 3))) {
			assertThatThrownBy(() -> registre.pourManche(SOIREE, mancheId))
					.isInstanceOfSatisfying(RefusDeCommande.class,
							refus -> assertThat(refus.refus().statutHttp()).isEqualTo(404));
		}
		assertThat(transactions.validees).isEqualTo(2);
		assertThat(transactions.annulees).isZero();
	}

	private static ServiceDeManche<?, ?> service(ModeDeJeu mode) {
		ServiceDeManche<?, ?> service = mock(ServiceDeManche.class);
		when(service.mode()).thenReturn(mode);
		return service;
	}

	private static ManchePersistante manche(UUID soireeId, ModeDeJeu mode) {
		return new ManchePersistante(UUID.randomUUID(), soireeId, mode, "{}", CREEE_LE);
	}

	/** Gestionnaire factice : note si une transaction est ouverte, et en quel mode. */
	private static final class TransactionsNotees implements PlatformTransactionManager {
		private boolean ouverte;
		private boolean lectureSeule;
		private int validees;
		private int annulees;
		@Override public TransactionStatus getTransaction(TransactionDefinition definition) {
			ouverte = true;
			lectureSeule = definition != null && definition.isReadOnly();
			return new SimpleTransactionStatus();
		}
		@Override public void commit(TransactionStatus statut) { ouverte = false; validees++; }
		@Override public void rollback(TransactionStatus statut) { ouverte = false; annulees++; }
	}

	/** Dépôt factice : seul le chargement d'une Manche est attendu. */
	private final class Depot implements DepotDeManches {
		private final List<ManchePersistante> manches;
		private boolean lectureEnTransaction;
		Depot(List<ManchePersistante> manches) { this.manches = manches; }
		@Override public ManchePersistante charger(UUID mancheId) {
			lectureEnTransaction = transactions.ouverte;
			return manches.stream().filter(manche -> manche.getMancheId().equals(mancheId)).findFirst().orElse(null);
		}
		@Override public List<ManchePersistante> deLaSoiree(UUID soireeId) { throw new UnsupportedOperationException(); }
		@Override public void enregistrer(ManchePersistante manche) { throw new UnsupportedOperationException(); }
		@Override public void retirer(ManchePersistante manche) { throw new UnsupportedOperationException(); }
		@Override public EnvoiDeManche retrouverEnvoi(UUID cle) { throw new UnsupportedOperationException(); }
		@Override public void enregistrerEnvoi(EnvoiDeManche envoi) { throw new UnsupportedOperationException(); }
	}
}
