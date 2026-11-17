package fr.croustyquizz.tempsreel;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import org.springframework.stereotype.Component;
import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Destination;

/** Capture avant commit sous le verrou, puis publication des seules destinations modifiées. */
@Component
public final class PublicationDeVues {
	private static final Logger JOURNAL = LoggerFactory.getLogger(PublicationDeVues.class);
	private final AssembleurDeVues assembleur;
	private final FournisseurPartieCommune commun;
	private final PublicationApresCommit apresCommit;
	private final DiffuseurTempsReel diffuseur;
	public PublicationDeVues(AssembleurDeVues assembleur, FournisseurPartieCommune commun,
			PublicationApresCommit apresCommit, DiffuseurTempsReel diffuseur) {
		this.assembleur = assembleur; this.commun = commun; this.apresCommit = apresCommit; this.diffuseur = diffuseur;
	}
	public void publier(UUID soireeId, Accepte resultat) {
		Set<Destination> destinations = new LinkedHashSet<>(resultat.aRepublier());
		if (destinations.remove(Destination.TOUTES_LES_MANETTES)) {
			commun.joueurs(soireeId).forEach(id -> destinations.add(Destination.joueur(id)));
		}
		Map<Destination, Evenement> captures = new LinkedHashMap<>();
		for (var destination : destinations) {
			captures.put(destination, assembleur.assembler(soireeId, destination, resultat.typeEvenement()));
		}
		apresCommit.publier(() -> captures.forEach((destination, vue) -> {
			try { envoyer(soireeId, destination, vue); }
			catch (RuntimeException erreur) {
				// Le diffuseur conserve déjà cette vue ; la reconnexion peut la récupérer.
				JOURNAL.error("Échec de diffusion après commit vers {} de la Soirée {}", destination, soireeId, erreur);
			}
		}));
	}
	private void envoyer(UUID soireeId, Destination destination, Evenement vue) {
		switch (destination.type()) {
			case SALLE -> diffuseur.diffuserSalle(soireeId, vue);
			case CONSOLE -> diffuseur.envoyerConsole(soireeId, vue);
			case JOUEUR -> diffuseur.envoyerJoueur(soireeId, destination.joueurId(), vue);
			default -> throw new IllegalArgumentException("Destination non développée.");
		}
	}
}
