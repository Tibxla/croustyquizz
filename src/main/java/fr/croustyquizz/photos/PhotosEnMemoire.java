package fr.croustyquizz.photos;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Refuse;

/** Budget commun à toutes les Soirées, envois simultanés et projections sans octets. */
@Component
public final class PhotosEnMemoire {

	static final long OCTETS_MAX_SERVEUR = 64_000_000;
	/** Corps lus en même temps : 4 × 1,5 Mo au plus, hors files. */
	static final int ENVOIS_SIMULTANES_MAX = 4;
	private final ConcurrentMap<UUID, FileDePhotos> files = new ConcurrentHashMap<>();
	private final Semaphore envoisEnCours = new Semaphore(ENVOIS_SIMULTANES_MAX);
	private long octetsReserves;

	/** Sans attente : un envoi de trop est refusé avant la lecture de son corps. */
	boolean entamerEnvoi() {
		return envoisEnCours.tryAcquire();
	}

	void terminerEnvoi() {
		envoisEnCours.release();
	}

	FileDePhotos file(UUID soireeId) {
		return files.computeIfAbsent(soireeId, id -> new FileDePhotos());
	}

	FileDePhotos trouver(UUID soireeId) {
		return files.get(soireeId);
	}

	synchronized void verifierBudget(long taille) {
		if (octetsReserves + taille > OCTETS_MAX_SERVEUR) {
			throw new Refuse(503, "SERVICE_INDISPONIBLE", "La mémoire des photos est pleine. Réessaie plus tard.").exception();
		}
	}

	synchronized void reserver(int taille) {
		verifierBudget(taille);
		octetsReserves += taille;
	}

	synchronized long octetsConserves() {
		return octetsReserves;
	}

	synchronized void liberer(long taille) {
		octetsReserves -= taille;
	}

	void purger(UUID soireeId) {
		FileDePhotos file = files.remove(soireeId);
		if (file != null) {
			liberer(file.vider());
		}
	}

	/** Soirée inactive : ses photos et ses reçus disparaissent, un réglage coupé reste. */
	void oublier(UUID soireeId) {
		FileDePhotos file = files.get(soireeId);
		if (file != null) {
			liberer(file.vider());
			if (file.ouvertes()) {
				files.remove(soireeId);
			}
		}
	}

	/** Appelée lors de l’assemblage, sous le verrou de Soirée. */
	public Object projection(UUID soireeId, Destination destination, boolean soireeOuverte, boolean mancheActive) {
		FileDePhotos file = soireeOuverte ? trouver(soireeId) : null;
		boolean ouvertes = soireeOuverte && (file == null || file.ouvertes());
		return switch (destination.type()) {
			case SALLE -> file == null || mancheActive ? null : file.photoAffichee();
			case CONSOLE -> new BlocConsole(ouvertes, file == null ? List.of() : file.enAttente());
			case JOUEUR, TOUTES_LES_MANETTES -> new Reglages(ouvertes);
		};
	}

	static Refuse photoIntrouvable() {
		return new Refuse(404, "RESSOURCE_INTROUVABLE", "Photo introuvable.");
	}

	record Reglages(boolean ouvertes) { }
	record BlocConsole(boolean ouvertes, List<FileDePhotos.PhotoEnAttente> enAttente) { }
}
