package fr.croustyquizz.photos;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import fr.croustyquizz.manche.AccuseCommande;
import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.soiree.EtatSoiree;
import fr.croustyquizz.soiree.Joueur;
import fr.croustyquizz.soiree.ServiceSoiree;
import fr.croustyquizz.tempsreel.CycleDeScene;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.VerrousSoiree;

@Service
class ServicePhotos {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServicePhotos.class);
	private static final String EVENEMENT = "photos-modifiees";
	private static final Set<EtatManche> ETATS_ACTIFS = EnumSet.of(EtatManche.EN_COURS, EtatManche.EN_PAUSE);
	private final PhotosEnMemoire photos;
	private final ServiceSoiree soirees;
	private final DepotDeManches manches;
	private final VerrousSoiree verrous;
	private final PublicationDeVues vues;
	private final Horloge horloge;
	private final TransactionTemplate nouvelleLecture;

	ServicePhotos(PhotosEnMemoire photos, ServiceSoiree soirees, DepotDeManches manches, VerrousSoiree verrous,
			PublicationDeVues vues, Horloge horloge, PlatformTransactionManager transactions) {
		this.photos = photos;
		this.soirees = soirees;
		this.manches = manches;
		this.verrous = verrous;
		this.vues = vues;
		this.horloge = horloge;
		nouvelleLecture = new TransactionTemplate(transactions);
		nouvelleLecture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		nouvelleLecture.setReadOnly(true);
	}

	Accepte envoyer(Acteur joueur, UUID cle, JpegDeSalle jpeg) {
		UUID soireeId = joueur.soireeId();
		return executer(joueur, cle, jpeg.empreinte(), file -> {
			file.verifierEnvoi(joueur.identifiant());
			String pseudo = soirees.joueurs(soireeId).stream()
					.filter(inscrit -> inscrit.getId().equals(joueur.identifiant()) && !inscrit.isExclu())
					.map(Joueur::getPseudo).findFirst()
					.orElseThrow(() -> new Refuse(403, "ACCES_REFUSE", "Ce Joueur ne peut pas envoyer de photo.").exception());
			photos.reserver(jpeg.octets().length);
			UUID photoId = file.ajouter(joueur.identifiant(), pseudo, jpeg.octets());
			return new Accepte(new AccusePhoto(cle, soireeId, photoId), Set.of(Destination.CONSOLE), EVENEMENT, 201);
		});
	}

	Accepte moderer(Acteur animateur, UUID cle, UUID photoId, boolean acceptee) {
		UUID soireeId = animateur.soireeId();
		return executer(animateur, cle, new Moderation(photoId, acceptee), file -> {
			boolean sceneLibre = acceptee && !mancheActive(soireeId);
			var photo = file.moderer(photoId, acceptee);
			if (!acceptee) {
				photos.liberer(photo.taille());
			}
			boolean afficheModifiee = sceneLibre && afficherSuivante(soireeId, file);
			Set<Destination> destinations = afficheModifiee
					? Set.of(Destination.CONSOLE, Destination.SALLE) : Set.of(Destination.CONSOLE);
			return new Accepte(new AccusePhoto(cle, soireeId, photoId), destinations, EVENEMENT);
		});
	}

	Accepte regler(Acteur animateur, UUID cle, boolean ouvertes) {
		UUID soireeId = animateur.soireeId();
		return executer(animateur, cle, ouvertes, file -> {
			boolean changement = file.ouvertes() != ouvertes;
			file.regler(ouvertes);
			return new Accepte(new AccuseCommande(cle, horloge.maintenant(), soireeId),
					changement ? Set.of(Destination.CONSOLE, Destination.TOUTES_LES_MANETTES) : Set.of(), EVENEMENT);
		});
	}

	private Accepte executer(Acteur acteur, UUID cle, Object empreinte, Function<FileDePhotos, Accepte> action) {
		UUID soireeId = acteur.soireeId();
		return verrous.executer(soireeId, () -> {
			if (!soireeOuverte(soireeId)) {
				throw new Refuse(409, "ETAT_INCOMPATIBLE", "Les photos sont réservées à une Soirée ouverte ou en cours.").exception();
			}
			var file = photos.file(soireeId);
			var identite = new FileDePhotos.Cle(acteur, cle);
			Accepte precedent = file.rejouer(identite, empreinte);
			if (precedent != null) {
				publier(soireeId, file, Set.of());
				return precedent;
			}
			Accepte recu = action.apply(file);
			file.conserver(identite, empreinte, recu);
			publier(soireeId, file, recu.aRepublier());
			return recu;
		});
	}

	byte[] lire(Acteur acteur, UUID photoId) {
		if (acteur.type() == Acteur.Type.JOUEUR) {
			throw new Refuse(403, "ACCES_REFUSE", "Les Joueurs ne peuvent pas télécharger les photos.").exception();
		}
		UUID soireeId = acteur.soireeId();
		boolean lecturePrivee = acteur.type() == Acteur.Type.ANIMATEUR;
		return verrous.executer(soireeId, () -> {
			var file = photos.trouver(soireeId);
			if (file == null || !soireeOuverte(soireeId)
					|| !lecturePrivee && mancheActive(soireeId)) {
				throw PhotosEnMemoire.photoIntrouvable().exception();
			}
			return file.lire(photoId, lecturePrivee, horloge.maintenant());
		});
	}

	/** Le cycle nous parvient sous le verrou et après le commit de la vue de Soirée. */
	@EventListener
	public void actualiserScene(CycleDeScene cycle) {
		UUID soireeId = cycle.soireeId();
		var file = photos.trouver(soireeId);
		if (file == null) {
			return;
		}
		if (cycle.soireeTerminee()) {
			photos.purger(soireeId);
		} else if (cycle.mancheActive()) {
			file.interrompre();
		} else if (afficherSuivante(soireeId, file)) {
			// La transaction d'origine est déjà validée mais Spring n'a pas encore libéré ses ressources.
			try {
				nouvelleLecture.executeWithoutResult(statut -> publier(soireeId, file, Set.of(Destination.SALLE)));
			} catch (DataAccessException | TransactionException erreur) {
				JOURNAL.error("Publication de la photo indisponible pour la Soirée {}", soireeId, erreur);
			}
		}
	}

	private boolean soireeOuverte(UUID soireeId) {
		var soiree = soirees.consulter(soireeId);
		if (soiree.getEtat() == EtatSoiree.TERMINEE) {
			photos.purger(soireeId);
		}
		return soiree.accepteLesArrivees();
	}

	private boolean mancheActive(UUID soireeId) {
		return manches.deLaSoiree(soireeId).stream().anyMatch(manche -> ETATS_ACTIFS.contains(manche.getEtat()));
	}

	private boolean afficherSuivante(UUID soireeId, FileDePhotos file) {
		var affiche = file.ouvrirAffiche(horloge.maintenant());
		if (affiche == null) {
			return false;
		}
		file.minuter(horloge.programmer(FileDePhotos.DUREE_AFFICHE, () -> expirer(soireeId, affiche)));
		return true;
	}

	private void expirer(UUID soireeId, FileDePhotos.PhotoAffichee affiche) {
		verrous.executer(soireeId, () -> {
			var file = photos.trouver(soireeId);
			if (file != null && file.photoAffichee() == affiche) {
				photos.liberer(file.expirer());
				if (soireeOuverte(soireeId)) {
					// Une Manche active aurait interrompu l’affiche après son commit, sous ce même verrou.
					afficherSuivante(soireeId, file);
					publier(soireeId, file, Set.of(Destination.SALLE));
				}
			}
			return null;
		});
	}

	private void publier(UUID soireeId, FileDePhotos file, Set<Destination> destinations) {
		// Si l’assemblage échoue, le reçu permet au rejeu de finir la première publication.
		var aPublier = file.publications(destinations);
		if (!aPublier.isEmpty()) {
			vues.publier(soireeId, new Accepte(null, aPublier, EVENEMENT));
			file.publicationsTerminees();
		}
	}

	record AccusePhoto(UUID envoiId, UUID soireeId, UUID photoId) { }
	private record Moderation(UUID photoId, boolean acceptee) { }
}
