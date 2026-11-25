package fr.croustyquizz.tempsreel;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.EtatDeSoiree;
import fr.croustyquizz.manche.Pouvoir;
import fr.croustyquizz.manche.PouvoirsDelegues;
import fr.croustyquizz.manche.Refuse;

/**
 * Lecture des Délégations (#67) pour le contrôle des commandes et l'assemblage des vues, sous le verrou de la
 * Soirée. Une Soirée terminée n'a plus de Délégation, même avant l'effacement de ses lignes.
 */
@Component
public class ControleDesDelegations implements PouvoirsDelegues {
	private final DepotDeDelegations depot;
	private final EtatDeSoiree soirees;
	private final VerrousSoiree verrous;

	ControleDesDelegations(DepotDeDelegations depot, EtatDeSoiree soirees, VerrousSoiree verrous) {
		this.depot = depot;
		this.soirees = soirees;
		this.verrous = verrous;
	}

	@Override
	public void autoriser(Acteur acteur, Pouvoir pouvoir) {
		switch (acteur.type()) {
			case ANIMATEUR -> { }
			case JOUEUR -> {
				Set<Pouvoir> recus = pouvoirs(acteur);
				if (!recus.contains(pouvoir)) {
					throw new Refuse(403, "ACCES_REFUSE", recus.isEmpty()
							? "Cette commande demande une Délégation de l’Animateur."
							: "Ta Délégation ne comprend pas ce pouvoir.").exception();
				}
			}
			default -> throw new Refuse(403, "ACCES_REFUSE", "Cet accès ne permet pas cette opération.").exception();
		}
	}

	@Override
	public boolean detient(Acteur acteur, Pouvoir pouvoir) {
		return acteur.type() == Acteur.Type.JOUEUR && pouvoirs(acteur).contains(pouvoir);
	}

	/** Pouvoirs du Joueur, vides sans Délégation ou une fois la Soirée terminée. */
	Set<Pouvoir> pouvoirs(Acteur joueur) {
		UUID soireeId = joueur.soireeId();
		return verrous.executer(soireeId, () -> soirees.estTerminee(soireeId)
				? EnumSet.noneOf(Pouvoir.class) : depot.pouvoirs(soireeId, joueur.identifiant()));
	}

	/** Pouvoirs enregistrés, pour un assemblage qui a déjà écarté une Soirée terminée. */
	Set<Pouvoir> enregistres(UUID soireeId, UUID joueurId) {
		return verrous.executer(soireeId, () -> depot.pouvoirs(soireeId, joueurId));
	}

	/** Délégations de la Soirée, par Joueur, de la moins récemment modifiée à la plus récente. */
	Map<UUID, Set<Pouvoir>> deLaSoiree(UUID soireeId) {
		return verrous.executer(soireeId, () -> depot.deLaSoiree(soireeId));
	}
}
