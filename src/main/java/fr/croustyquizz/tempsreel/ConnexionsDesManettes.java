package fr.croustyquizz.tempsreel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Connexions STOMP ouvertes par les Manettes, d'où vient le champ {@code connecte} des Joueurs (#8).
 * Un Joueur est connecté dès son arrivée, et le reste tant qu'il n'a ouvert aucune connexion ; il est
 * déconnecté à la fermeture de sa dernière connexion, puis de nouveau connecté à la suivante. L'état vit
 * en mémoire, rangé par Soirée : un redémarrage remet tout le monde en ligne, et la fin d'une Soirée
 * l'oublie d'un coup (#266).
 * <p>
 * Les méthodes qui changent l'état rendent {@code true} pour un changement réel, que l'appelant publie
 * sous le verrou de la Soirée ({@link PresenceDesManettes}).
 */
@Component
public final class ConnexionsDesManettes {
	/** Par Soirée, les connexions ouvertes de chaque Joueur suivi ; un ensemble vide marque un Joueur déconnecté. */
	private final Map<UUID, Map<UUID, Set<String>>> parSoiree = new HashMap<>();
	/** Joueurs déconnectés, tous rangés dans {@link #parSoiree}. */
	private final Set<UUID> deconnectes = new HashSet<>();

	public synchronized boolean estConnecte(UUID joueurId) {
		return !deconnectes.contains(joueurId);
	}

	/** @return {@code true} si le Joueur était déconnecté */
	synchronized boolean ouvrir(UUID soireeId, UUID joueurId, String connexion) {
		parSoiree.computeIfAbsent(soireeId, id -> new HashMap<>())
				.computeIfAbsent(joueurId, id -> new HashSet<>()).add(connexion);
		return deconnectes.remove(joueurId);
	}

	/** @return {@code true} si c'était la dernière connexion ouverte du Joueur */
	synchronized boolean fermer(UUID soireeId, UUID joueurId, String connexion) {
		Set<String> connexions = parSoiree.getOrDefault(soireeId, Map.of()).get(joueurId);
		if (connexions == null || !connexions.remove(connexion) || !connexions.isEmpty()) {
			return false;
		}
		return deconnectes.add(joueurId);
	}

	/** Efface la présence des Joueurs de cette Soirée, connexions encore ouvertes comprises. */
	synchronized void oublier(UUID soireeId) {
		Map<UUID, Set<String>> joueurs = parSoiree.remove(soireeId);
		if (joueurs != null) {
			deconnectes.removeAll(joueurs.keySet());
		}
	}

	/** Pour les tests : la Soirée occupe encore de la mémoire. */
	synchronized boolean suitLaSoiree(UUID soireeId) {
		return parSoiree.containsKey(soireeId);
	}
}
