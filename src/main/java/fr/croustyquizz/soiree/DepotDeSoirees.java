package fr.croustyquizz.soiree;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DepotDeSoirees extends JpaRepository<Soiree, UUID> {

	Optional<Soiree> findByCodeAccesAndEtatIn(String codeAcces, Collection<EtatSoiree> etats);

	boolean existsByCodeAccesAndEtatIn(String codeAcces, Collection<EtatSoiree> etats);

}
