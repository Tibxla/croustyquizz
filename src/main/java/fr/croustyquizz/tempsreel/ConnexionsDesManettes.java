package fr.croustyquizz.tempsreel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Connexions STOMP ouvertes par les Manettes, d'où vient le champ {@code connecte} des Joueurs (#8).
 * Un Joueur est connecté dès son arrivée, déconnecté à la fermeture de sa dernière connexion, puis
 * de nouveau connecté à la suivante. L'état vit en mémoire : un redémarrage remet tout le monde en ligne.
 * <p>
 * Les méthodes qui changent l'état rendent {@code true} pour un changement réel, que l'appelant publie
 * sous le verrou de la Soirée ({@link PresenceDesManettes}).
 */
@Component
public final class ConnexionsDesManettes {
	private final Map<UUID, Set<String>> ouvertes = new HashMap<>();
	private final Set<UUID> deconnectes = new HashSet<>();

	public synchronized boolean estConnecte(UUID joueurId) {
		return !deconnectes.contains(joueurId);
	}

	/** @return {@code true} si le Joueur était déconnecté */
	synchronized boolean ouvrir(UUID joueurId, String connexion) {
		ouvertes.computeIfAbsent(joueurId, id -> new HashSet<>()).add(connexion);
		return deconnectes.remove(joueurId);
	}

	/** @return {@code true} si c'était la dernière connexion ouverte du Joueur */
	synchronized boolean fermer(UUID joueurId, String connexion) {
		Set<String> connexions = ouvertes.get(joueurId);
		if (connexions == null || !connexions.remove(connexion)) {
			return false;
		}
		if (!connexions.isEmpty()) {
			return false;
		}
		ouvertes.remove(joueurId);
		return deconnectes.add(joueurId);
	}
}
