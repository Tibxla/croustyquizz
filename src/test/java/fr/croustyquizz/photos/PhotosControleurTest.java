package fr.croustyquizz.photos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.RefusDeCommande;
import jakarta.servlet.ServletInputStream;

class PhotosControleurTest {

	@Test
	void unContentLengthTropGrandEstRefuseSansPlaceDEnvoiNiLectureDuCorps() {
		var service = mock(ServicePhotos.class);
		var soiree = UUID.randomUUID();
		var requete = new MockHttpServletRequest("POST", "/api/soirees/" + soiree + "/photos") {
			@Override
			public ServletInputStream getInputStream() {
				throw new AssertionError("Le corps ne doit pas être lu.");
			}
			@Override
			public long getContentLengthLong() {
				return JpegDeSalle.OCTETS_MAX + 1L;
			}
		};
		requete.setContentType("image/jpeg");
		var joueur = new Acteur(Acteur.Type.JOUEUR, UUID.randomUUID(), soiree);

		var refus = catchThrowableOfType(RefusDeCommande.class,
				() -> new PhotosControleur(service).envoyer(soiree, UUID.randomUUID(), joueur, requete));
		assertThat(refus.refus().statutHttp()).isEqualTo(422);
		assertThat(refus.refus().code()).isEqualTo("DONNEES_INVALIDES");
		assertThat(refus.refus().detail()).contains("1,5 Mo");
		verifyNoInteractions(service);
	}
}
