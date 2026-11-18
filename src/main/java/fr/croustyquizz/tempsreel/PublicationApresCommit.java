package fr.croustyquizz.tempsreel;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Une annulation ne publie rien ; hors transaction, l'envoi est immédiat. */
@Component
public final class PublicationApresCommit {
	public void publier(Runnable publication) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			publication.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { publication.run(); }
		});
	}
}
