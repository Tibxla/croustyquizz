package fr.croustyquizz.soiree;

import static java.time.temporal.ChronoUnit.MICROS;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import fr.croustyquizz.manche.AccuseCommande;
import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EnvoiDeCommande;
import fr.croustyquizz.manche.EnvoiDeManche;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.RefusDeCommande;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.RegistreServicesDeManche;
import fr.croustyquizz.manche.ResultatDeCommande;
import fr.croustyquizz.manche.SocleDeManche;

/** Sérialise les transitions de la Soirée et les commandes de Manche avec le même verrou. */
@Service
class OrchestrateurDeSoiree {
	private final DepotDeSoirees soirees;
	private final RegistreServicesDeManche modes;
	private final SocleDeManche socle;
	private final TransactionTemplate transaction;

	OrchestrateurDeSoiree(DepotDeSoirees soirees, RegistreServicesDeManche modes, SocleDeManche socle) {
		this.soirees = soirees;
		this.modes = modes;
		this.socle = socle;
		transaction = new TransactionTemplate(socle.transactions());
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	ResultatDeCommande piloter(Acteur acteur, String route, UUID envoiId, CommandeSoiree commande) {
		var envoi = new EnvoiDeCommande(acteur, route, envoiId, commande);
		UUID soireeId = acteur.soireeId();
		return socle.verrous().executer(soireeId, () -> {
			ResultatDeCommande precedent = socle.envois().retrouver(envoi);
			if (precedent != null) {
				return precedent;
			}
			try {
				return switch (commande.action()) {
					case LANCER -> lancer(soireeId, envoi);
					case MANCHE_SUIVANTE -> avancer(soireeId, envoi);
					case TERMINER -> terminer(soireeId, envoi);
				};
			} catch (RefusDeCommande refus) {
				return transaction.execute(statut -> conserver(envoi, refus.refus()));
			}
		});
	}

	/** Un rappel pour la même Manche terminée et le même Animateur retrouve son reçu. */
	ResultatDeCommande mancheSuivante(Acteur acteur, UUID mancheTermineeId) {
		var commande = new CommandeSoiree(CommandeSoiree.Action.MANCHE_SUIVANTE);
		UUID envoiId = UUID.nameUUIDFromBytes(("manche-suivante:" + mancheTermineeId).getBytes(StandardCharsets.UTF_8));
		String route = "/interne/soirees/" + acteur.soireeId() + "/manche-suivante";
		return piloter(acteur, route, envoiId, commande);
	}

	private ResultatDeCommande lancer(UUID soireeId, EnvoiDeCommande envoi) {
		ManchePersistante premiere = transaction.execute(statut -> {
			Soiree soiree = verrouiller(soireeId);
			UUID cle = EnvoiDeManche.cle(envoi.acteur(), envoi.route(), envoi.envoiId());
			if (soiree.getEtat() != EtatSoiree.OUVERTE && !soiree.reprendLancement(cle)) {
				throw incompatible("La Soirée doit être ouverte pour être lancée.");
			}
			var programme = socle.depot().deLaSoiree(soireeId);
			if (programme.isEmpty()) {
				throw new Refuse(409, "PROGRAMME_INCOMPLET", "Ajoute une Manche au programme avant de lancer la Soirée.").exception();
			}
			if (programme.stream().anyMatch(manche -> manche.getEtat() != EtatManche.PRETE)) {
				throw incompatible("Une Manche du programme a déjà démarré.");
			}
			programme.forEach(manche -> modes.pourMode(manche.getMode()));
			if (soiree.getEtat() == EtatSoiree.OUVERTE) {
				soiree.lancer(socle.horloge().maintenant().truncatedTo(MICROS), cle);
			}
			return programme.getFirst();
		});
		// Le service valide le démarrage et le reçu de Soirée dans la même transaction.
		return demarrer(premiere, envoi);
	}

	private ResultatDeCommande avancer(UUID soireeId, EnvoiDeCommande envoi) {
		verifierEnCours(soirees.findById(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId)));
		var programme = socle.depot().deLaSoiree(soireeId);
		if (programme.stream().anyMatch(OrchestrateurDeSoiree::estActive)) {
			throw incompatible("Termine la Manche active avant de passer à la suivante.");
		}
		return programme.stream().filter(manche -> manche.getEtat() == EtatManche.PRETE).findFirst()
				.map(manche -> demarrer(manche, envoi))
				.orElseGet(() -> validerFin(soireeId, envoi));
	}

	private ResultatDeCommande demarrer(ManchePersistante manche, EnvoiDeCommande envoi) {
		return modes.pourMode(manche.getMode()).demarrer(manche.getMancheId(), envoi);
	}

	private ResultatDeCommande terminer(UUID soireeId, EnvoiDeCommande envoi) {
		verifierEnCours(soirees.findById(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId)));
		var active = socle.depot().deLaSoiree(soireeId).stream().filter(OrchestrateurDeSoiree::estActive)
				.findFirst().orElse(null);
		if (active != null) {
			String routeInterne = "/interne/soirees/" + soireeId + "/manches/" + active.getMancheId() + "/cloture";
			var cloture = new EnvoiDeCommande(envoi.acteur(), routeInterne, envoi.envoiId(), envoi.corps());
			var resultat = modes.pourMode(active.getMode()).terminerPourSoiree(active.getMancheId(), cloture);
			if (resultat instanceof Refuse refus) {
				throw refus.exception();
			}
		}
		return validerFin(soireeId, envoi);
	}

	private ResultatDeCommande validerFin(UUID soireeId, EnvoiDeCommande envoi) {
		return transaction.execute(statut -> {
			Soiree soiree = verrouiller(soireeId);
			verifierEnCours(soiree);
			var maintenant = socle.horloge().maintenant().truncatedTo(MICROS);
			soiree.terminer(maintenant);
			var resultat = new Accepte(new AccuseCommande(envoi.envoiId(), maintenant, soireeId),
					Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES), "soiree-terminee");
			socle.vues().publier(soireeId, resultat);
			return conserver(envoi, resultat);
		});
	}

	private ResultatDeCommande conserver(EnvoiDeCommande envoi, ResultatDeCommande resultat) {
		socle.envois().enregistrer(envoi, resultat, socle.horloge().maintenant());
		return resultat;
	}

	private Soiree verrouiller(UUID soireeId) {
		return soirees.verrouiller(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId));
	}

	private static boolean estActive(ManchePersistante manche) {
		return manche.getEtat() == EtatManche.EN_COURS || manche.getEtat() == EtatManche.EN_PAUSE;
	}

	private static void verifierEnCours(Soiree soiree) {
		if (soiree.getEtat() != EtatSoiree.EN_COURS) {
			throw incompatible("La Soirée doit être en cours.");
		}
	}

	private static RefusDeCommande incompatible(String detail) {
		return new Refuse(409, "ETAT_INCOMPATIBLE", detail).exception();
	}
}
