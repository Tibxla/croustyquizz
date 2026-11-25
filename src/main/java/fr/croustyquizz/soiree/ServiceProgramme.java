package fr.croustyquizz.soiree;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.AjoutDeManche;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.RegistreServicesDeManche;
import fr.croustyquizz.manche.SocleDeManche;
import fr.croustyquizz.tempsreel.DiffuseurStomp;
import fr.croustyquizz.tempsreel.VueSynchronisee;
import tools.jackson.databind.JsonNode;

/** Composition du programme d'une Soirée avant son lancement. */
@Service
class ServiceProgramme {
	private static final Accepte PROGRAMME_MODIFIE = new Accepte(null, Set.of(Destination.CONSOLE), "programme-modifie");
	private final DepotDeSoirees soirees;
	private final SocleDeManche socle;
	private final RegistreServicesDeManche modes;
	private final DiffuseurStomp vues;
	private final TransactionTemplate transaction;
	private final TransactionTemplate lecture;

	ServiceProgramme(DepotDeSoirees soirees, SocleDeManche socle, RegistreServicesDeManche modes, DiffuseurStomp vues) {
		this.soirees = soirees;
		this.socle = socle;
		this.modes = modes;
		this.vues = vues;
		this.transaction = new TransactionTemplate(socle.transactions());
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.lecture = new TransactionTemplate(socle.transactions());
		this.lecture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.lecture.setReadOnly(true);
	}

	ProjectionManche ajouter(UUID soireeId, ModeDeJeu mode, JsonNode reglages) {
		// Un programme figé refuse avant tout appel externe : Deezer en panne ne masque pas le 409. Revérifié sous le verrou.
		lecture.executeWithoutResult(statut -> verifierModifiable(
				soirees.findById(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId)),
				socle.depot().deLaSoiree(soireeId)));
		// Hors du verrou : un mode peut consulter un service externe (le tirage Deezer du Blind test, ADR 0009).
		AjoutDeManche ajout = mode == ModeDeJeu.QUIZ
				// Le Quiz peut être préparé avant la livraison de son service de déroulement.
				? new AjoutDeManche(new ManchePersistante(UUID.randomUUID(), soireeId, mode, reglages.toString(),
						socle.horloge().maintenant()), manche -> { })
				: modes.pourMode(mode).preparerAjout(soireeId, reglages);
		return socle.verrous().executer(soireeId, () -> modifier(soireeId, programme -> {
			ajout.enregistrer(socle.depot());
			return ProjectionManche.de(ajout.manche(), socle.json());
		}));
	}

	void retirer(UUID soireeId, UUID mancheId) {
		socle.verrous().executer(soireeId, () -> modifier(soireeId, programme -> {
			ManchePersistante cible = programme.stream().filter(manche -> manche.getMancheId().equals(mancheId))
					.findFirst().orElseThrow(() -> new Refuse(404, "RESSOURCE_INTROUVABLE", "Manche introuvable.").exception());
			socle.depot().retirer(cible);
			reindexer(programme.stream().filter(manche -> !manche.getMancheId().equals(mancheId)).toList());
			return null;
		}));
	}

	VueSynchronisee ordonner(UUID soireeId, List<UUID> mancheIds) {
		return socle.verrous().executer(soireeId, () -> {
			modifier(soireeId, programme -> {
				Map<UUID, ManchePersistante> manches = programme.stream()
						.collect(Collectors.toMap(ManchePersistante::getMancheId, Function.identity()));
				if (mancheIds.size() != manches.size() || !manches.keySet().equals(Set.copyOf(mancheIds))) {
					throw new Refuse(422, "DONNEES_INVALIDES",
							"L’ordre doit contenir une seule fois chaque Manche de la Soirée.").exception();
				}
				reindexer(mancheIds.stream().map(manches::get).toList());
				return null;
			});
			// Le commit a publié la nouvelle révision. Le verrou garde la capture à cette révision.
			return vues.capturerConsole(soireeId, null);
		});
	}

	/** À appeler sous le verrou de la Soirée. */
	private <T> T modifier(UUID soireeId, Function<List<ManchePersistante>, T> mutation) {
		return transaction.execute(statut -> {
			T resultat = mutation.apply(programmeModifiable(soireeId));
			socle.vues().publier(soireeId, PROGRAMME_MODIFIE);
			return resultat;
		});
	}

	private static void reindexer(List<ManchePersistante> programme) {
		for (int position = 0; position < programme.size(); position++) {
			programme.get(position).placerDansLeProgramme(position);
		}
	}

	private List<ManchePersistante> programmeModifiable(UUID soireeId) {
		Soiree soiree = soirees.verrouiller(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId));
		List<ManchePersistante> programme = socle.depot().deLaSoiree(soireeId);
		verifierModifiable(soiree, programme);
		return programme;
	}

	private static void verifierModifiable(Soiree soiree, List<ManchePersistante> programme) {
		if ((soiree.getEtat() != EtatSoiree.PREPARATION && soiree.getEtat() != EtatSoiree.OUVERTE)
				// Le démarrage d'une Manche par la route de test ne fait pas avancer l'état de Soirée.
				|| programme.stream().anyMatch(manche -> manche.getEtat() != EtatManche.PRETE)) {
			throw new Refuse(409, "ETAT_INCOMPATIBLE", "La Soirée a commencé. Son programme est figé.").exception();
		}
	}
}
