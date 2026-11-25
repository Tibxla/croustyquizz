package fr.croustyquizz.soiree;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import fr.croustyquizz.blindtest.AccuseBuzz;
import fr.croustyquizz.blindtest.ServiceBlindTest;
import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EnvoiDeCommande;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.RegistreDesEnvois;
import fr.croustyquizz.manche.ResultatDeCommande;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.VerrousSoiree;

/**
 * Sérialise les Buzz avec le pilotage de la Soirée et publie après le commit. Pendant une Manche de Blind test, le
 * Buzz nomme sa cible et passe au service de la Manche, comme une action de Joueur (accord de #8, ADR 0009 point 5).
 */
@Service
class ServiceBuzz {

	private final DepotDeSoirees soirees;
	private final DepotDeJoueurs joueurs;
	private final DepotDeManches manches;
	private final RegistreDesEnvois envois;
	private final VerrousSoiree verrous;
	private final Horloge horloge;
	private final PublicationDeVues vues;
	private final ObjectProvider<ServiceBlindTest> blindTest;
	private final TransactionTemplate transaction;

	ServiceBuzz(DepotDeSoirees soirees, DepotDeJoueurs joueurs, DepotDeManches manches, RegistreDesEnvois envois,
			VerrousSoiree verrous, Horloge horloge, PublicationDeVues vues, PlatformTransactionManager transactions,
			ObjectProvider<ServiceBlindTest> blindTest) {
		this.soirees = soirees;
		this.joueurs = joueurs;
		this.manches = manches;
		this.envois = envois;
		this.verrous = verrous;
		this.horloge = horloge;
		this.vues = vues;
		this.blindTest = blindTest;
		this.transaction = new TransactionTemplate(transactions);
	}

	/** Morceau visé par un Buzz de Blind test ; absent hors Blind test. */
	record CibleDuBuzz(UUID mancheId, UUID etapeId) { }

	/** Résultat décidé ici, ou Buzz à transmettre à la Manche de Blind test active. */
	private record Aiguillage(ResultatDeCommande resultat, UUID mancheId, String pseudo) {
		static Aiguillage decide(ResultatDeCommande resultat) {
			return new Aiguillage(resultat, null, null);
		}
	}

	ResultatDeCommande buzzer(EnvoiDeCommande envoi, CibleDuBuzz cible) {
		UUID soireeId = envoi.acteur().soireeId();
		return verrous.executer(soireeId, () -> {
			// La transaction de la Soirée se termine avant celle de la Manche : elles ne s'imbriquent pas.
			Aiguillage aiguillage = transaction.execute(statut -> traiter(envoi, soireeId, cible));
			if (aiguillage.resultat() != null) {
				return aiguillage.resultat();
			}
			return blindTest.getObject().buzzer(aiguillage.mancheId(), envoi, cible.etapeId(), aiguillage.pseudo(),
					buzz -> retenir(soireeId, buzz));
		});
	}

	private Aiguillage traiter(EnvoiDeCommande envoi, UUID soireeId, CibleDuBuzz cible) {
		Soiree soiree = soirees.verrouiller(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId));
		Joueur joueur = joueurs.findById(envoi.acteur().identifiant())
				.filter(trouve -> soireeId.equals(trouve.getSoireeId()) && !trouve.isExclu())
				.orElseThrow(() -> new Refuse(401, "AUTHENTIFICATION_REQUISE", "Un jeton de reconnexion valide est requis.")
						.exception());
		// Un même envoi rend son reçu initial, même après la fin du Morceau ou de la Manche.
		ResultatDeCommande precedent = envois.retrouver(envoi);
		if (precedent != null) {
			return Aiguillage.decide(precedent);
		}
		ManchePersistante active = manches.deLaSoiree(soiree.getId()).stream()
				.filter(manche -> manche.getEtat() == EtatManche.EN_COURS || manche.getEtat() == EtatManche.EN_PAUSE)
				.findFirst().orElse(null);
		if (active != null && active.getMode() == ModeDeJeu.BLIND_TEST && EtatSoiree.AVEC_CODE_ACTIF.contains(soiree.getEtat())) {
			if (cible == null) {
				// Erreur de requête : rien n'est retenu, le même envoi corrigé reste possible.
				return Aiguillage.decide(new Refuse(400, "REQUETE_INVALIDE",
						"Pendant un Blind test, le Buzz nomme sa Manche et son Morceau."));
			}
			if (cible.mancheId().equals(active.getMancheId())) {
				return new Aiguillage(null, active.getMancheId(), joueur.getPseudo());
			}
		}
		Instant reception = horloge.maintenant();
		ResultatDeCommande resultat = cible == null ? recevoir(soiree, joueur, envoi.envoiId(), reception, active)
				: new Refuse(409, "COMMANDE_PERIMEE", "Cette Manche n’est plus en cours.");
		envois.enregistrer(envoi, resultat, reception);
		if (resultat instanceof Accepte accepte) {
			vues.publier(soireeId, accepte);
		}
		return Aiguillage.decide(resultat);
	}

	private ResultatDeCommande recevoir(Soiree soiree, Joueur joueur, UUID envoiId, Instant reception,
			ManchePersistante active) {
		if (!EtatSoiree.AVEC_CODE_ACTIF.contains(soiree.getEtat())) {
			return new Refuse(409, "BUZZ_NON_AUTORISE", "La Soirée n’est pas ouverte aux Buzz.");
		}
		if (active != null) {
			return new Refuse(409, "BUZZ_NON_AUTORISE", "Le Buzz du socle est indisponible pendant une Manche.");
		}
		Buzz buzz = soiree.enregistrerBuzz(joueur, envoiId, reception);
		return new Accepte(buzz, Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES), "buzz-recu");
	}

	/** Dans la transaction de la Manche, avant sa publication : dernierBuzz montre le Buzz qui prend la main. */
	private void retenir(UUID soireeId, AccuseBuzz buzz) {
		soirees.findById(soireeId).ifPresent(soiree -> soiree.retenirBuzz(
				new Buzz(buzz.envoiId(), buzz.joueurId(), buzz.pseudo(), buzz.recuLe(), buzz.rang())));
	}
}
