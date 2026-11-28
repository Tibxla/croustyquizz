package fr.croustyquizz.tempsreel;

import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatDeSoiree;
import fr.croustyquizz.manche.Refuse;

@Service
class ServiceSonEcran {
	private final SonDesEcrans son;
	private final EtatDeSoiree soirees;
	private final RegistreAbonnements abonnements;
	private final VerrousSoiree verrous;
	private final PublicationDeVues publication;
	private final TransactionTemplate transaction;

	ServiceSonEcran(SonDesEcrans son, EtatDeSoiree soirees, RegistreAbonnements abonnements,
			VerrousSoiree verrous, PublicationDeVues publication, PlatformTransactionManager transactions) {
		this.son = son;
		this.soirees = soirees;
		this.abonnements = abonnements;
		this.verrous = verrous;
		this.publication = publication;
		transaction = new TransactionTemplate(transactions);
	}

	void signaler(Acteur acteur, UUID abonnement, boolean actif) {
		UUID soireeId = acteur.soireeId();
		verrous.executer(soireeId, () -> {
			if (!soirees.existe(soireeId)) {
				throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Soirée introuvable.").exception();
			}
			String connexion = abonnements.connexionInstallee(abonnement, Canaux.salle(soireeId), acteur);
			if (connexion == null) {
				throw new Refuse(409, "ABONNEMENT_INACTIF", "L’abonnement de l’Écran de salle n’est plus actif.").exception();
			}
			modifier(soireeId, connexion, actif);
			return null;
		});
	}

	@EventListener
	@Order(1) // Le registre invalide d’abord l’abonnement, avant de prendre le verrou.
	void deconnecter(SessionDisconnectEvent evenement) {
		if (evenement.getUser() instanceof IdentiteStomp identite && identite.acteur().type() == Acteur.Type.ECRAN) {
			UUID soireeId = identite.acteur().soireeId();
			verrous.executer(soireeId, () -> {
				modifier(soireeId, evenement.getSessionId(), null);
				return null;
			});
		}
	}

	private void modifier(UUID soireeId, String connexion, Boolean actif) {
		var avant = son.etat(soireeId);
		Boolean precedent = son.remplacer(soireeId, connexion, actif);
		if (son.etat(soireeId) == avant) { return; }
		try {
			transaction.executeWithoutResult(statut ->
					publication.publier(soireeId, Set.of(Destination.CONSOLE), "son-ecran-modifie"));
		} catch (RuntimeException erreur) {
			son.remplacer(soireeId, connexion, precedent);
			throw erreur;
		}
	}
}
