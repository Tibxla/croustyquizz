package fr.croustyquizz.soiree;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DepotDeJoueurs extends JpaRepository<Joueur, UUID> {

	/** Joueurs d'une Soirée, du premier arrivé au dernier. */
	List<Joueur> findBySoireeIdOrderByArriveLeAsc(UUID soireeId);

	/** Joueur dont la Manette présente ce jeton de reconnexion. */
	Optional<Joueur> findByEmpreinteJeton(byte[] empreinteJeton);

	/** Vrai si un Joueur de la Soirée, même parti ou exclu, porte déjà ce pseudo. */
	boolean existsBySoireeIdAndPseudoNormalise(UUID soireeId, String pseudoNormalise);

}
