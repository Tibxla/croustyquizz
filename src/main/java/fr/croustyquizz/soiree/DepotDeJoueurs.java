package fr.croustyquizz.soiree;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DepotDeJoueurs extends JpaRepository<Joueur, UUID> {

	/** Joueurs d'une Soirée, du premier arrivé au dernier. */
	List<Joueur> findBySoireeIdOrderByArriveLeAsc(UUID soireeId);

}
