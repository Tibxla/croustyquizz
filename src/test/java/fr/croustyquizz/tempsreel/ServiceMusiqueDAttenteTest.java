package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatDeSoiree;
import fr.croustyquizz.manche.PouvoirsDelegues;
import fr.croustyquizz.manche.RefusDeCommande;

/** Musique d'attente dans un contexte partiel, sans les beans de la Délégation (#67). */
class ServiceMusiqueDAttenteTest {
	private static final UUID SOIREE = UUID.randomUUID();
	private final DepotMusiqueDAttente depot = mock(DepotMusiqueDAttente.class);
	private final EtatDeSoiree soirees = mock(EtatDeSoiree.class);
	private final PublicationDeVues publication = mock(PublicationDeVues.class);

	@Test
	void sansControleDesDelegationsSeulLAnimateurRegleLaMusique() {
		when(soirees.existe(SOIREE)).thenReturn(true);
		when(depot.lire(SOIREE)).thenReturn(MusiqueDAttente.PAR_DEFAUT);
		var sansDelegation = new StaticListableBeanFactory().getBeanProvider(PouvoirsDelegues.class);
		var service = new ServiceMusiqueDAttente(depot, soirees, publication, new VerrousSoiree(),
				mock(PlatformTransactionManager.class), sansDelegation);

		var refus = catchThrowableOfType(RefusDeCommande.class, () ->
				service.regler(new Acteur(Acteur.Type.JOUEUR, UUID.randomUUID(), SOIREE), SOIREE, MusiqueDAttente.Etat.COUPEE));
		assertThat(refus.refus().code()).isEqualTo("ACCES_REFUSE");
		verify(publication, never()).publier(any(), any(Set.class), anyString());

		service.regler(new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), SOIREE), SOIREE, MusiqueDAttente.Etat.COUPEE);
		verify(depot).enregistrer(SOIREE, new MusiqueDAttente(MusiqueDAttente.Etat.COUPEE, null));
		verify(publication).publier(SOIREE, Set.of(Destination.SALLE, Destination.CONSOLE), "musique-attente-modifiee");
	}
}
