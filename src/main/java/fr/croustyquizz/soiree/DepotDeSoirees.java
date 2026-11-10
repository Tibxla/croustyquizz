package fr.croustyquizz.soiree;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface DepotDeSoirees extends JpaRepository<Soiree, UUID> {

	Optional<Soiree> findByCodeAccesAndEtatIn(String codeAcces, Collection<EtatSoiree> etats);

	boolean existsByCodeAccesAndEtatIn(String codeAcces, Collection<EtatSoiree> etats);

	/** Charge la Soirée en bloquant les autres modifications jusqu'à la fin de la transaction. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Soiree s where s.id = :id")
	Optional<Soiree> verrouiller(UUID id);

}
