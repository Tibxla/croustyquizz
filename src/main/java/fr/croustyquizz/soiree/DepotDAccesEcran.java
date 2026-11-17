package fr.croustyquizz.soiree;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DepotDAccesEcran extends JpaRepository<AccesEcran, UUID> {

	Optional<AccesEcran> findByEmpreinteCle(byte[] empreinteCle);

}
