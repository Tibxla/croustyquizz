package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.EtatDeSoiree;
import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Pouvoir;
import fr.croustyquizz.passage.ParticipantsDePassage;

/** Délégations gardées en mémoire pour les vues (#67) : une lecture par Soirée, oubliée à chaque changement. */
class ControleDesDelegationsTest {
	private static final UUID SOIREE = UUID.randomUUID();
	private static final UUID ALIZE = UUID.randomUUID();
	private final DepotDeDelegations depot = mock(DepotDeDelegations.class);
	private final EtatDeSoiree soirees = mock(EtatDeSoiree.class);
	private final VerrousSoiree verrous = new VerrousSoiree();
	private final ControleDesDelegations controle = new ControleDesDelegations(depot, soirees, verrous);

	@Test
	void lesVuesLisentLesDelegationsUneFoisJusquAuProchainOubli() {
		when(depot.deLaSoiree(SOIREE)).thenReturn(Map.of());
		assertThat(controle.deLaSoiree(SOIREE)).isEmpty();
		assertThat(controle.deLaSoiree(SOIREE)).isEmpty();
		assertThat(controle.enregistres(SOIREE, ALIZE)).isEmpty();
		verify(depot, times(1)).deLaSoiree(SOIREE);

		when(depot.deLaSoiree(SOIREE)).thenReturn(Map.of(ALIZE, EnumSet.of(Pouvoir.PHOTOS)));
		controle.oublier(SOIREE);
		assertThat(controle.enregistres(SOIREE, ALIZE)).containsExactly(Pouvoir.PHOTOS);
		assertThat(controle.deLaSoiree(SOIREE)).containsOnlyKeys(ALIZE);
		verify(depot, times(2)).deLaSoiree(SOIREE);
	}

	@Test
	void leControleDUneCommandeRelitToujoursLaBase() {
		when(depot.deLaSoiree(SOIREE)).thenReturn(Map.of());
		when(depot.pouvoirs(SOIREE, ALIZE)).thenReturn(EnumSet.of(Pouvoir.FILE_ATTENTE));
		controle.deLaSoiree(SOIREE);
		var alize = new Acteur(Acteur.Type.JOUEUR, ALIZE, SOIREE);
		assertThat(controle.detient(alize, Pouvoir.FILE_ATTENTE)).isTrue();
		assertThat(controle.detient(alize, Pouvoir.FILE_ATTENTE)).isTrue();
		verify(depot, times(2)).pouvoirs(SOIREE, ALIZE);
	}

	@Test
	void uneAttributionAnnuleeNeLaissePasSaLectureEnMemoire() {
		var participants = mock(ParticipantsDePassage.class);
		var publication = mock(PublicationDeVues.class);
		when(soirees.estEnregistree(SOIREE)).thenReturn(true);
		when(participants.pseudos(SOIREE)).thenReturn(Map.of(ALIZE, "Alizé"));
		when(depot.pouvoirs(SOIREE, ALIZE)).thenReturn(EnumSet.noneOf(Pouvoir.class));
		// La publication lit la ligne écrite dans la transaction, puis échoue : la transaction est annulée.
		when(depot.deLaSoiree(SOIREE)).thenReturn(Map.of(ALIZE, EnumSet.of(Pouvoir.PHOTOS)), Map.of());
		doAnswer(appel -> {
			assertThat(controle.deLaSoiree(SOIREE)).containsOnlyKeys(ALIZE);
			throw new IllegalStateException("diffusion impossible");
		}).when(publication).publier(any(), any(Set.class), anyString());
		var service = new ServiceDesDelegations(depot, controle, soirees, participants, publication, verrous,
				mock(Horloge.class), mock(PlatformTransactionManager.class));

		var animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), SOIREE);
		assertThatThrownBy(() -> service.attribuer(animateur, SOIREE, ALIZE, EnumSet.of(Pouvoir.PHOTOS)))
				.hasMessage("diffusion impossible");

		assertThat(controle.deLaSoiree(SOIREE)).isEmpty();
		verify(depot, times(2)).deLaSoiree(SOIREE);
	}
}
