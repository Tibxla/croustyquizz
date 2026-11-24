package fr.croustyquizz.soiree;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EnvoiDeCommande;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.RegistreDesEnvois;
import fr.croustyquizz.manche.ResultatDeCommande;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.VerrousSoiree;

/** Sérialise les Buzz avec le pilotage de la Soirée et publie après le commit. */
@Service
class ServiceBuzz {

	private final DepotDeSoirees soirees;
	private final DepotDeJoueurs joueurs;
	private final DepotDeManches manches;
	private final RegistreDesEnvois envois;
	private final VerrousSoiree verrous;
	private final Horloge horloge;
	private final PublicationDeVues vues;
	private final TransactionTemplate transaction;

	ServiceBuzz(DepotDeSoirees soirees, DepotDeJoueurs joueurs, DepotDeManches manches, RegistreDesEnvois envois,
			VerrousSoiree verrous, Horloge horloge, PublicationDeVues vues, PlatformTransactionManager transactions) {
		this.soirees = soirees;
		this.joueurs = joueurs;
		this.manches = manches;
		this.envois = envois;
		this.verrous = verrous;
		this.horloge = horloge;
		this.vues = vues;
		this.transaction = new TransactionTemplate(transactions);
	}

	ResultatDeCommande buzzer(EnvoiDeCommande envoi) {
		UUID soireeId = envoi.acteur().soireeId();
		return verrous.executer(soireeId, () -> transaction.execute(statut -> traiter(envoi, soireeId)));
	}

	private ResultatDeCommande traiter(EnvoiDeCommande envoi, UUID soireeId) {
		Soiree soiree = soirees.verrouiller(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId));
		Joueur joueur = joueurs.findById(envoi.acteur().identifiant())
				.filter(trouve -> soireeId.equals(trouve.getSoireeId()) && !trouve.isExclu())
				.orElseThrow(() -> new Refuse(401, "AUTHENTIFICATION_REQUISE", "Un jeton de reconnexion valide est requis.")
						.exception());
		ResultatDeCommande precedent = envois.retrouver(envoi);
		if (precedent != null) {
			return precedent;
		}
		Instant reception = horloge.maintenant();
		ResultatDeCommande resultat = recevoir(soiree, joueur, envoi.envoiId(), reception);
		envois.enregistrer(envoi, resultat, reception);
		if (resultat instanceof Accepte accepte) {
			vues.publier(soireeId, accepte);
		}
		return resultat;
	}

	private ResultatDeCommande recevoir(Soiree soiree, Joueur joueur, UUID envoiId, Instant reception) {
		if (!EtatSoiree.AVEC_CODE_ACTIF.contains(soiree.getEtat())) {
			return new Refuse(409, "BUZZ_NON_AUTORISE", "La Soirée n’est pas ouverte aux Buzz.");
		}
		var active = manches.deLaSoiree(soiree.getId()).stream()
				.filter(manche -> manche.getEtat() == EtatManche.EN_COURS || manche.getEtat() == EtatManche.EN_PAUSE)
				.findFirst();
		// #17 : aiguiller ici le Blind test vers le service de la Manche active, sous le même verrou.
		if (active.isPresent()) {
			return new Refuse(409, "BUZZ_NON_AUTORISE", "Le Buzz du socle est indisponible pendant une Manche.");
		}
		Buzz buzz = soiree.enregistrerBuzz(joueur, envoiId, reception);
		return new Accepte(buzz, Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES), "buzz-recu");
	}
}
