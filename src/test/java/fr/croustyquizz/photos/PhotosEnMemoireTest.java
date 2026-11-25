package fr.croustyquizz.photos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.croustyquizz.manche.RefusDeCommande;

class PhotosEnMemoireTest {

	@Test
	void lePlafondGlobalEstExactEtLaPurgeRendLaPlaceAuxAutresSoirees() {
		var memoire = new PhotosEnMemoire();
		var soirees = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		byte[] jpeg = new byte[1_500_000];
		// Le même tableau représente des uploads de cette taille, sans allouer 63 Mo au test.
		// Dix photos par Soirée restent sous les 16 Mo de chacune.
		for (int i = 0; i < 42; i++) {
			memoire.reserver(jpeg.length);
			memoire.file(soirees.get(i / 10)).ajouter(UUID.randomUUID(), "Léa", jpeg);
		}
		memoire.reserver(1_000_000);
		var refus = catchThrowableOfType(RefusDeCommande.class, () -> memoire.reserver(1));
		assertThat(refus.refus().statutHttp()).isEqualTo(503);
		assertThat(refus.refus().code()).isEqualTo("SERVICE_INDISPONIBLE");

		memoire.purger(soirees.getFirst());
		assertThat(memoire.trouver(soirees.getFirst())).isNull();
		memoire.reserver(15_000_000);
		var encorePleine = catchThrowableOfType(RefusDeCommande.class, () -> memoire.reserver(1));
		assertThat(encorePleine.refus().statutHttp()).isEqualTo(503);
	}

	@Test
	void auPlusQuatreEnvoisSontLusEnMemeTemps() {
		var memoire = new PhotosEnMemoire();
		for (int i = 0; i < 4; i++) {
			assertThat(memoire.entamerEnvoi()).isTrue();
		}
		assertThat(memoire.entamerEnvoi()).isFalse();
		memoire.terminerEnvoi();
		assertThat(memoire.entamerEnvoi()).isTrue();
	}

}
