package fr.croustyquizz.photos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.croustyquizz.manche.RefusDeCommande;

class PhotosEnMemoireTest {

	@Test
	void lePlafondGlobalEstExactEtLaPurgeRendLaPlaceAuxAutresSoirees() {
		var memoire = new PhotosEnMemoire();
		var soiree = UUID.randomUUID();
		var autreSoiree = UUID.randomUUID();
		byte[] jpeg = new byte[1_500_000];
		// Le même tableau représente des uploads de cette taille, sans allouer 63 Mo au test.
		for (int i = 0; i < 42; i++) {
			var destination = i < 21 ? soiree : autreSoiree;
			memoire.reserver(jpeg.length);
			memoire.file(destination).ajouter(UUID.randomUUID(), "Léa", jpeg);
		}
		memoire.reserver(1_000_000);
		var refus = catchThrowableOfType(RefusDeCommande.class, () -> memoire.reserver(1));
		assertThat(refus.refus().statutHttp()).isEqualTo(503);
		assertThat(refus.refus().code()).isEqualTo("SERVICE_INDISPONIBLE");

		memoire.purger(soiree);
		assertThat(memoire.trouver(soiree)).isNull();
		memoire.reserver(31_500_000);
		var encorePleine = catchThrowableOfType(RefusDeCommande.class, () -> memoire.reserver(1));
		assertThat(encorePleine.refus().statutHttp()).isEqualTo(503);
	}

}
