package fr.croustyquizz.photos;

import java.io.IOException;
import java.time.Duration;
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
import fr.croustyquizz.manche.Minuteur;
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

	/** Le corps ne se lit qu’avec une place d’envoi et après les gardes sous verrou, verrou relâché. */
	Accepte envoyer(Acteur joueur, UUID cle, long tailleAnnoncee, LectureDuCorps lecture) throws IOException {
		if (!photos.entamerEnvoi()) {
			throw new Refuse(503, "SERVICE_INDISPONIBLE",
					"Trop de photos arrivent en même temps. Réessaie dans un instant.").exception();
		}
		try {
			verifierAvantLecture(joueur, cle, tailleAnnoncee);
			return envoyer(joueur, cle, lecture.lire());
		} finally {
			photos.terminerEnvoi();
		}
	}

	private void verifierAvantLecture(Acteur joueur, UUID cle, long taille) {
		UUID soireeId = joueur.soireeId();
		verrous.executer(soireeId, () -> {
			verifierSoireeOuverte(soireeId);
			var file = photos.trouver(soireeId);
			if (file != null && file.connait(new FileDePhotos.Cle(joueur, cle))) {
				// Le rejeu compare l’empreinte après lecture et rend le reçu, même limite atteinte.
				return null;
			}
			if (file != null) {
				file.verifierEnvoi(joueur.identifiant(), taille);
			}
			pseudo(joueur);
			photos.verifierBudget(taille);
			return null;
		});
	}

	private Accepte envoyer(Acteur joueur, UUID cle, JpegDeSalle jpeg) {
		UUID soireeId = joueur.soireeId();
		return executer(joueur, cle, jpeg.empreinte(), file -> {
			file.verifierEnvoi(joueur.identifiant(), jpeg.octets().length);
			String pseudo = pseudo(joueur);
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

	private String pseudo(Acteur joueur) {
		return soirees.joueurs(joueur.soireeId()).stream()
				.filter(inscrit -> inscrit.getId().equals(joueur.identifiant()) && !inscrit.isExclu())
				.map(Joueur::getPseudo).findFirst()
				.orElseThrow(() -> new Refuse(403, "ACCES_REFUSE", "Ce Joueur ne peut pas envoyer de photo.").exception());
	}

	private Accepte executer(Acteur acteur, UUID cle, Object empreinte, Function<FileDePhotos, Accepte> action) {
		UUID soireeId = acteur.soireeId();
		return verrous.executer(soireeId, () -> {
			verifierSoireeOuverte(soireeId);
			var file = photos.file(soireeId);
			// Toute commande, même refusée, repousse l’oubli : la file vient peut-être d’être créée.
			entretenir(soireeId, file);
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

	/**
	 * Le cycle nous parvient sous le verrou et après le commit de la vue de Soirée, dans
	 * l’afterCommit d’un autre module : aucune erreur des photos ne doit en ressortir.
	 */
	@EventListener
	public void actualiserScene(CycleDeScene cycle) {
		UUID soireeId = cycle.soireeId();
		try {
			actualiser(cycle);
		} catch (RuntimeException erreur) {
			JOURNAL.error("Actualisation des photos impossible pour la Soirée {}", soireeId, erreur);
		}
	}

	private void actualiser(CycleDeScene cycle) {
		UUID soireeId = cycle.soireeId();
		var file = photos.trouver(soireeId);
		if (file == null) {
			return;
		}
		if (cycle.soireeTerminee()) {
			photos.purger(soireeId);
		} else if (cycle.mancheActive()) {
			if (file.interrompre()) {
				entretenir(soireeId, file);
			}
		} else if (afficherSuivante(soireeId, file)) {
			// La transaction d'origine est déjà validée mais Spring n'a pas encore libéré ses ressources.
			try {
				nouvelleLecture.executeWithoutResult(statut -> publier(soireeId, file, Set.of(Destination.SALLE)));
			} catch (DataAccessException | TransactionException erreur) {
				JOURNAL.error("Publication de la photo indisponible pour la Soirée {}", soireeId, erreur);
			}
		}
	}

	private void verifierSoireeOuverte(UUID soireeId) {
		if (!soireeOuverte(soireeId)) {
			throw new Refuse(409, "ETAT_INCOMPATIBLE", "Les photos sont réservées à une Soirée ouverte ou en cours.").exception();
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
		boolean ouverte = file.ouvrirAffiche(horloge.maintenant(), diffusion ->
				horloge.programmer(FileDePhotos.DUREE_AFFICHE, () -> expirer(soireeId, diffusion)));
		if (ouverte) {
			entretenir(soireeId, file);
		}
		return ouverte;
	}

	/** Rappel de l’Horloge : une erreur est journalisée et l’affiche suivante reprogrammée. */
	private void expirer(UUID soireeId, UUID diffusion) {
		try {
			verrous.executer(soireeId, () -> {
				var file = photos.trouver(soireeId);
				int tailleLiberee = file == null ? 0 : file.expirer(diffusion);
				if (tailleLiberee > 0) {
					photos.liberer(tailleLiberee);
					entretenir(soireeId, file);
					if (soireeOuverte(soireeId)) {
						// Une Manche active aurait interrompu l’affiche après son commit, sous ce même verrou.
						afficherSuivante(soireeId, file);
						publier(soireeId, file, Set.of(Destination.SALLE));
					}
				}
				return null;
			});
		} catch (RuntimeException erreur) {
			JOURNAL.error("Fin d’affiche en échec pour la Soirée {} : nouvelle tentative", soireeId, erreur);
			programmerReprise(soireeId);
		}
	}

	private void programmerReprise(UUID soireeId) {
		horloge.programmer(FileDePhotos.DUREE_AFFICHE, () -> reprendre(soireeId));
	}

	/** Relance l’affiche après un échec ; s’arrête quand la file disparaît (fin ou oubli de la Soirée). */
	private void reprendre(UUID soireeId) {
		try {
			verrous.executer(soireeId, () -> {
				var file = photos.trouver(soireeId);
				if (file != null && soireeOuverte(soireeId) && !mancheActive(soireeId)) {
					boolean ouverte = afficherSuivante(soireeId, file);
					// Sans nouvelle affiche, achève la publication restée en suspens.
					publier(soireeId, file, ouverte ? Set.of(Destination.SALLE) : Set.of());
				}
				return null;
			});
		} catch (RuntimeException erreur) {
			JOURNAL.error("Reprise de l’affiche en échec pour la Soirée {} : nouvelle tentative", soireeId, erreur);
			programmerReprise(soireeId);
		}
	}

	private void entretenir(UUID soireeId, FileDePhotos file) {
		file.entretenir(horloge.maintenant(), delai -> programmerControle(soireeId, file, delai));
	}

	private Minuteur programmerControle(UUID soireeId, FileDePhotos file, Duration delai) {
		return horloge.programmer(delai, () -> oublierSiInactive(soireeId, file));
	}

	private void oublierSiInactive(UUID soireeId, FileDePhotos file) {
		try {
			verrous.executer(soireeId, () -> {
				// Une file recréée depuis porte son propre contrôle.
				if (file.equals(photos.trouver(soireeId))
						&& file.controlerInactivite(horloge.maintenant(), delai -> programmerControle(soireeId, file, delai))) {
					// La mémoire est rendue avant toute lecture de la base.
					photos.oublier(soireeId);
					publier(soireeId, file, Set.of(Destination.CONSOLE));
				}
				return null;
			});
		} catch (RuntimeException erreur) {
			JOURNAL.error("Publication après l’oubli des photos impossible pour la Soirée {}", soireeId, erreur);
		}
	}

	private void publier(UUID soireeId, FileDePhotos file, Set<Destination> destinations) {
		// Si l’assemblage échoue, le reçu permet au rejeu de finir la première publication.
		var aPublier = file.publications(destinations);
		if (!aPublier.isEmpty()) {
			vues.publier(soireeId, new Accepte(null, aPublier, EVENEMENT));
			file.publicationsTerminees();
		}
	}

	@FunctionalInterface
	interface LectureDuCorps {
		JpegDeSalle lire() throws IOException;
	}

	record AccusePhoto(UUID envoiId, UUID soireeId, UUID photoId) { }
	private record Moderation(UUID photoId, boolean acceptee) { }
}
