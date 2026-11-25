package fr.croustyquizz.tempsreel;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Destination;

/** Capture avant commit sous le verrou, puis publication des seules destinations modifiées. */
@Component
public final class PublicationDeVues {
	private static final String SOIREE_TERMINEE = "soiree-terminee";
	private static final Logger JOURNAL = LoggerFactory.getLogger(PublicationDeVues.class);
	private final AssembleurDeVues assembleur;
	private final FournisseurPartieCommune commun;
	private final PublicationApresCommit apresCommit;
	private final DiffuseurTempsReel diffuseur;
	private final ApplicationEventPublisher evenements;
	public PublicationDeVues(AssembleurDeVues assembleur, FournisseurPartieCommune commun,
			PublicationApresCommit apresCommit, DiffuseurTempsReel diffuseur, ApplicationEventPublisher evenements) {
		this.assembleur = assembleur; this.commun = commun; this.apresCommit = apresCommit; this.diffuseur = diffuseur;
		this.evenements = evenements;
	}
	public void publier(UUID soireeId, Accepte resultat) {
		publier(soireeId, resultat, false);
	}
	public void publier(UUID soireeId, Accepte resultat, boolean classementModifie) {
		publier(soireeId, resultat.aRepublier(), resultat.typeEvenement(), classementModifie);
	}
	void publier(UUID soireeId, Set<Destination> aRepublier, String typeEvenement) {
		publier(soireeId, aRepublier, typeEvenement, false);
	}
	private void publier(UUID soireeId, Set<Destination> aRepublier, String typeEvenement, boolean classementModifie) {
		Set<Destination> destinations = new LinkedHashSet<>(aRepublier);
		if (destinations.remove(Destination.TOUTES_LES_MANETTES)) {
			commun.joueurs(soireeId).forEach(id -> destinations.add(Destination.joueur(id)));
		}
		var captures = new LinkedHashMap<>(assembleur.assembler(soireeId, destinations, typeEvenement));
		if (classementModifie) {
			Set<Destination> autres = new LinkedHashSet<>(Set.of(Destination.SALLE, Destination.CONSOLE));
			if (!aRepublier.contains(Destination.TOUTES_LES_MANETTES)) {
				commun.joueurs(soireeId).forEach(id -> autres.add(Destination.joueur(id)));
			}
			autres.removeAll(destinations);
			captures.putAll(assembleur.assembler(soireeId, autres, "classement-modifie"));
		}
		var salle = captures.get(Destination.SALLE);
		apresCommit.publier(() -> {
			captures.forEach((destination, vue) -> {
				try { envoyer(soireeId, destination, vue); }
				catch (RuntimeException erreur) {
					// Le diffuseur conserve déjà cette vue ; la reconnexion peut la récupérer.
					JOURNAL.error("Échec de diffusion après commit vers {} de la Soirée {}", destination, soireeId, erreur);
				}
			});
			// Les photos passent hors Manche active et disparaissent à la fin de la Soirée.
			if (SOIREE_TERMINEE.equals(typeEvenement)) {
				evenements.publishEvent(new CycleDeScene(soireeId, false, true));
			} else if (salle != null && salle.champs().containsKey("mancheActive")) {
				evenements.publishEvent(new CycleDeScene(soireeId, salle.champs().get("mancheActive") != null, false));
			}
		});
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
