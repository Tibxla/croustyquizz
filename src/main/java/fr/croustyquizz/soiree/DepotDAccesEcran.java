package fr.croustyquizz.soiree;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DepotDAccesEcran extends JpaRepository<AccesEcran, UUID> {

	Optional<AccesEcran> findByEmpreinteCle(byte[] empreinteCle);

	/** Supprime les accès expirés à cet instant, de toutes les Soirées ; rend leur nombre. */
	@Modifying
	@Query("delete from AccesEcran acces where acces.expireLe <= :instant")
	int purgerExpiresA(@Param("instant") Instant instant);

}
