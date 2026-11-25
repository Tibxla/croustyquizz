package fr.croustyquizz.photos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.RefusDeCommande;
import fr.croustyquizz.soiree.EtatSoiree;
import fr.croustyquizz.soiree.Joueur;
import fr.croustyquizz.soiree.Soiree;
import fr.croustyquizz.soiree.ServiceSoiree;
import fr.croustyquizz.tempsreel.CycleDeScene;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.VerrousSoiree;

/** Service réel sur une Horloge factice ; Soirée, Manches et publication simulées. */
class ServicePhotosTest {

	private static final int MO_ET_DEMI = 1_500_000;
	// Espionnée pour compter les contrôles d’inactivité programmés.
	private final HorlogeFactice horloge = spy(new HorlogeFactice());
	private final PhotosEnMemoire photos = new PhotosEnMemoire();
	private final ServiceSoiree soirees = mock(ServiceSoiree.class);
	private final DepotDeManches manches = mock(DepotDeManches.class);
	private final PublicationDeVues vues = mock(PublicationDeVues.class);
	private final ServicePhotos service = new ServicePhotos(photos, soirees, manches, new VerrousSoiree(), vues, horloge,
			mock(PlatformTransactionManager.class));
	private final UUID soiree = UUID.randomUUID();
	private final Soiree ouverte = mock(Soiree.class);
	private final List<Joueur> inscrits = new ArrayList<>();
	private final AtomicInteger lectures = new AtomicInteger();
	private int numero;

	@BeforeEach
	void ouvrirSoiree() {
		when(ouverte.accepteLesArrivees()).thenReturn(true);
		when(ouverte.getEtat()).thenReturn(EtatSoiree.OUVERTE);
		when(soirees.consulter(soiree)).thenReturn(ouverte);
		when(soirees.joueurs(soiree)).thenAnswer(appel -> List.copyOf(inscrits));
		when(manches.deLaSoiree(soiree)).thenReturn(List.of());
	}

	@Test
	void lesGardesBonMarchePassentSousLeVerrouAvantLaLectureDuCorps() throws IOException {
		var lea = joueur("Léa", false);
		var exclu = joueur("Exclu", true);
		assertThat(refusSansLecture(exclu, 10).code()).isEqualTo("ACCES_REFUSE");
		assertThat(refusSansLecture(lea, PhotosEnMemoire.OCTETS_MAX_SERVEUR + 1).code()).isEqualTo("SERVICE_INDISPONIBLE");
		for (int i = 0; i < 3; i++) {
			envoyer(lea, UUID.randomUUID(), 10);
		}
		assertThat(refusSansLecture(lea, 10).code()).isEqualTo("LIMITE_ATTEINTE");
		reglerEnvois(false);
		assertThat(refusSansLecture(joueur("Sam", false), 10).code()).isEqualTo("PHOTOS_COUPEES");
		when(ouverte.accepteLesArrivees()).thenReturn(false);
		assertThat(refusSansLecture(lea, 10).code()).isEqualTo("ETAT_INCOMPATIBLE");
		assertThat(lectures).hasValue(3);
	}

	@Test
	void uneCleDejaRecueRendSonRecuMemeLimiteAtteinte() throws IOException {
		var lea = joueur("Léa", false);
		UUID cle = UUID.randomUUID();
		envoyer(lea, UUID.randomUUID(), 10);
		envoyer(lea, UUID.randomUUID(), 10);
		var empreinte = "photo-de-lea";
		var recu = service.envoyer(lea, cle, 10, () -> new JpegDeSalle(new byte[10], empreinte));
		var rejeu = service.envoyer(lea, cle, 10, () -> new JpegDeSalle(new byte[10], empreinte));
		assertThat(rejeu.accuse()).isEqualTo(recu.accuse());
		assertThat(rejeu.statutHttp()).isEqualTo(201);
	}

	@Test
	void quatreEnvoisEnCoursRefusentLeCinquiemeSansLireSonCorpsEtToutEnvoiRendSaPlace() throws IOException {
		var lea = joueur("Léa", false);
		for (int i = 0; i < PhotosEnMemoire.ENVOIS_SIMULTANES_MAX; i++) {
			assertThat(photos.entamerEnvoi()).isTrue();
		}
		var refus = refusSansLecture(lea, 10);
		assertThat(refus.statutHttp()).isEqualTo(503);
		assertThat(refus.code()).isEqualTo("SERVICE_INDISPONIBLE");
		for (int i = 0; i < PhotosEnMemoire.ENVOIS_SIMULTANES_MAX; i++) {
			photos.terminerEnvoi();
		}
		for (int i = 0; i < PhotosEnMemoire.ENVOIS_SIMULTANES_MAX + 1; i++) {
			var illisible = catchThrowableOfType(RefusDeCommande.class, () -> service.envoyer(lea, UUID.randomUUID(), 10,
					() -> { throw JpegDeSalle.invalide("La photo doit être un JPEG lisible.").exception(); }));
			assertThat(illisible.refus().code()).isEqualTo("DONNEES_INVALIDES");
		}
		assertThat(envoyer(lea, UUID.randomUUID(), 10).statutHttp()).isEqualTo(201);
	}

	@Test
	void leBudgetDeSoireeCompteLesPhotosEnAttenteAccepteesEtALAffiche() throws IOException {
		var joueurs = List.of(joueur("A", false), joueur("B", false), joueur("C", false), joueur("D", false));
		var premiere = photoId(envoyer(joueurs.get(0), UUID.randomUUID(), MO_ET_DEMI));
		moderer(premiere, true);
		assertThat(photos.trouver(soiree).photoAffichee().photoId()).isEqualTo(premiere);
		var seconde = photoId(envoyer(joueurs.get(0), UUID.randomUUID(), MO_ET_DEMI));
		enMancheActive(true);
		moderer(seconde, true);
		for (int i = 0; i < 8; i++) {
			envoyer(joueurs.get(1 + i / 3), UUID.randomUUID(), MO_ET_DEMI);
		}
		var refus = refusSansLecture(joueurs.get(3), MO_ET_DEMI);
		assertThat(refus.statutHttp()).isEqualTo(409);
		assertThat(refus.code()).isEqualTo("LIMITE_ATTEINTE");
		assertThat(refus.detail()).contains("16 Mo");
		var derniere = photoId(envoyer(joueurs.get(3), UUID.randomUUID(), 1_000_000));
		assertThat(photos.trouver(soiree).octetsConserves()).isEqualTo(FileDePhotos.OCTETS_MAX_PAR_SOIREE);
		moderer(derniere, false);
		assertThat(envoyer(joueurs.get(3), UUID.randomUUID(), 1_000_000).statutHttp()).isEqualTo(201);
	}

	@Test
	void trentePhotosAccepteesAuPlusAttendentLAfficheEtLaTrenteEtUniemeResteEnAttente() throws IOException {
		var lea = joueur("Léa", false);
		enMancheActive(true);
		for (int i = 0; i < FileDePhotos.ACCEPTEES_MAX; i++) {
			moderer(photoId(envoyer(lea, UUID.randomUUID(), 10)), true);
		}
		var enTrop = photoId(envoyer(lea, UUID.randomUUID(), 10));
		var refus = catchThrowableOfType(RefusDeCommande.class, () -> moderer(enTrop, true));
		assertThat(refus.refus().statutHttp()).isEqualTo(409);
		assertThat(refus.refus().code()).isEqualTo("LIMITE_ATTEINTE");
		assertThat(photos.trouver(soiree).enAttente()).extracting(FileDePhotos.PhotoEnAttente::photoId).containsExactly(enTrop);
		moderer(enTrop, false);
		assertThat(photos.trouver(soiree).enAttente()).isEmpty();
	}

	@Test
	void uneErreurDesPhotosNeRessortPasDuCycleDeSceneDUnAutreModule() throws IOException {
		var photo = photoAcceptee(joueur("Léa", false));
		enMancheActive(false);
		doThrow(new IllegalStateException("Assemblage impossible")).when(vues).publier(eq(soiree), any(Accepte.class));
		assertThatCode(() -> service.actualiserScene(new CycleDeScene(soiree, false, false))).doesNotThrowAnyException();
		assertThat(photos.trouver(soiree).photoAffichee().photoId()).isEqualTo(photo);
	}

	@Test
	void uneFinDAfficheEnEchecEstJournaliseeEtLAfficheSuivanteReprogrammee() throws IOException {
		var lea = joueur("Léa", false);
		var premiere = photoAcceptee(lea);
		var seconde = photoAcceptee(lea);
		enMancheActive(false);
		service.actualiserScene(new CycleDeScene(soiree, false, false));
		assertThat(photos.trouver(soiree).photoAffichee().photoId()).isEqualTo(premiere);
		when(soirees.consulter(soiree)).thenThrow(new DataRetrievalFailureException("Base indisponible")).thenReturn(ouverte);
		clearInvocations(vues);

		assertThatCode(() -> horloge.avancer(FileDePhotos.DUREE_AFFICHE)).doesNotThrowAnyException();
		assertThat(photos.trouver(soiree).photoAffichee()).isNull();
		assertThat(photos.octetsConserves()).isEqualTo(10);

		horloge.avancer(FileDePhotos.DUREE_AFFICHE);
		assertThat(photos.trouver(soiree).photoAffichee().photoId()).isEqualTo(seconde);
		verify(vues).publier(eq(soiree), argThat((Accepte publie) -> publie.aRepublier().contains(Destination.SALLE)));
		horloge.avancer(FileDePhotos.DUREE_AFFICHE);
		assertThat(photos.octetsConserves()).isZero();
	}

	@Test
	void uneSoireeSansCommandeNiAfficheDepuisDouzeHeuresPerdSesPhotosEtSesRecus() throws IOException {
		var lea = joueur("Léa", false);
		UUID cle = UUID.randomUUID();
		var premier = service.envoyer(lea, cle, 10, () -> new JpegDeSalle(new byte[10], "meme-photo"));
		horloge.avancer(FileDePhotos.INACTIVITE_MAX.minusSeconds(1));
		for (int i = 0; i < 5; i++) {
			reglerEnvois(true);
		}
		// Un seul contrôle vivant : l’exécuteur réel garderait chaque tâche annulée douze heures.
		verify(horloge, times(1)).programmer(eq(FileDePhotos.INACTIVITE_MAX), any());
		horloge.avancer(FileDePhotos.INACTIVITE_MAX.minusSeconds(1));
		assertThat(photos.trouver(soiree).enAttente()).hasSize(1);
		doNothing().when(vues).publier(eq(soiree), any(Accepte.class));
		clearInvocations(vues);

		horloge.avancer(Duration.ofSeconds(1));
		assertThat(photos.trouver(soiree)).isNull();
		assertThat(photos.octetsConserves()).isZero();
		verify(vues, atLeastOnce()).publier(eq(soiree), argThat((Accepte publie) -> publie.aRepublier().contains(Destination.CONSOLE)));
		var apresOubli = service.envoyer(lea, cle, 10, () -> new JpegDeSalle(new byte[10], "meme-photo"));
		assertThat(photoId(apresOubli)).isNotEqualTo(photoId(premier));
	}

	@Test
	void lOubliDUneSoireeInactiveGardeLesEnvoisCoupes() throws IOException {
		envoyer(joueur("Léa", false), UUID.randomUUID(), 10);
		reglerEnvois(false);
		horloge.avancer(FileDePhotos.INACTIVITE_MAX);
		var file = photos.trouver(soiree);
		assertThat(file.ouvertes()).isFalse();
		assertThat(file.enAttente()).isEmpty();
		assertThat(photos.octetsConserves()).isZero();
	}

	private Acteur joueur(String pseudo, boolean exclu) {
		var inscrit = mock(Joueur.class);
		UUID id = UUID.randomUUID();
		when(inscrit.getId()).thenReturn(id);
		when(inscrit.getPseudo()).thenReturn(pseudo);
		when(inscrit.isExclu()).thenReturn(exclu);
		inscrits.add(inscrit);
		return new Acteur(Acteur.Type.JOUEUR, id, soiree);
	}

	private Acteur animateur() {
		return new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
	}

	private Accepte envoyer(Acteur joueur, UUID cle, int taille) throws IOException {
		String empreinte = "photo-" + numero++;
		return service.envoyer(joueur, cle, taille, () -> {
			lectures.incrementAndGet();
			return new JpegDeSalle(new byte[taille], empreinte);
		});
	}

	private fr.croustyquizz.manche.Refuse refusSansLecture(Acteur joueur, long tailleAnnoncee) {
		var refus = catchThrowableOfType(RefusDeCommande.class, () -> service.envoyer(joueur, UUID.randomUUID(), tailleAnnoncee,
				() -> { throw new AssertionError("Le corps ne doit pas être lu."); }));
		assertThat(refus).isNotNull();
		return refus.refus();
	}

	private UUID photoAcceptee(Acteur joueur) throws IOException {
		enMancheActive(true);
		var photo = photoId(envoyer(joueur, UUID.randomUUID(), 10));
		moderer(photo, true);
		return photo;
	}

	private void moderer(UUID photoId, boolean acceptee) {
		service.moderer(animateur(), UUID.randomUUID(), photoId, acceptee);
	}

	private void reglerEnvois(boolean ouvertes) {
		service.regler(animateur(), UUID.randomUUID(), ouvertes);
	}

	private void enMancheActive(boolean active) {
		if (active) {
			var manche = mock(ManchePersistante.class);
			when(manche.getEtat()).thenReturn(EtatManche.EN_COURS);
			when(manches.deLaSoiree(soiree)).thenReturn(List.of(manche));
		} else {
			when(manches.deLaSoiree(soiree)).thenReturn(List.of());
		}
	}

	private static UUID photoId(Accepte recu) {
		return ((ServicePhotos.AccusePhoto) recu.accuse()).photoId();
	}
}
