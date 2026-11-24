package fr.croustyquizz.photos;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.UUID;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Minuteur;
import fr.croustyquizz.manche.Refuse;

/** Les opérations sur cette file sont sérialisées par le verrou de sa Soirée. */
final class FileDePhotos {

	static final int ATTENTE_MAX_PAR_JOUEUR = 3;
	static final int ATTENTE_MAX_PAR_SOIREE = 30;
	static final Duration DUREE_AFFICHE = Duration.ofSeconds(8);

	private final Map<UUID, Photo> photosEnAttente = new LinkedHashMap<>();
	private final SequencedMap<UUID, Photo> acceptees = new LinkedHashMap<>();
	private final Map<Cle, Reception> recus = new HashMap<>();
	private final Set<Destination> aPublier = new LinkedHashSet<>();
	private boolean envoisOuverts = true;
	private Affiche affiche;
	private Minuteur minuteur;

	boolean ouvertes() {
		return envoisOuverts;
	}

	void regler(boolean valeur) {
		envoisOuverts = valeur;
	}

	void verifierEnvoi(UUID joueurId) {
		if (!envoisOuverts) {
			throw new Refuse(409, "PHOTOS_COUPEES", "L’Animateur a coupé les envois de photos.").exception();
		}
		if (photosEnAttente.values().stream().filter(photo -> photo.joueurId().equals(joueurId)).count() >= ATTENTE_MAX_PAR_JOUEUR) {
			throw new Refuse(409, "LIMITE_ATTEINTE", "Tu as déjà trois photos en attente de validation.").exception();
		}
		if (photosEnAttente.size() >= ATTENTE_MAX_PAR_SOIREE) {
			throw new Refuse(409, "LIMITE_ATTEINTE", "La Soirée a déjà trente photos en attente de validation.").exception();
		}
	}

	UUID ajouter(UUID joueurId, String pseudo, byte[] jpeg) {
		UUID id = UUID.randomUUID();
		photosEnAttente.put(id, new Photo(joueurId, pseudo, jpeg));
		return id;
	}

	Photo moderer(UUID photoId, boolean acceptee) {
		Photo photo = photosEnAttente.remove(photoId);
		if (photo == null) {
			if (acceptees.containsKey(photoId) || affiche != null && affiche.projection().photoId().equals(photoId)) {
				throw new Refuse(409, "COMMANDE_PERIMEE", "Cette photo a déjà été acceptée.").exception();
			}
			throw PhotosEnMemoire.photoIntrouvable().exception();
		}
		if (acceptee) {
			acceptees.put(photoId, photo);
		}
		return photo;
	}

	PhotoAffichee ouvrirAffiche(Instant maintenant) {
		if (affiche != null || acceptees.isEmpty()) {
			return null;
		}
		var suivante = acceptees.pollFirstEntry();
		affiche = new Affiche(suivante.getValue(), new PhotoAffichee(suivante.getKey(), maintenant.plus(DUREE_AFFICHE)));
		return affiche.projection();
	}

	void minuter(Minuteur nouveau) {
		minuteur = nouveau;
	}

	void interrompre() {
		if (affiche != null) {
			acceptees.putFirst(affiche.projection().photoId(), affiche.photo());
			fermerAffiche();
		}
	}

	int expirer() {
		int taille = affiche.photo().taille();
		fermerAffiche();
		return taille;
	}

	private void fermerAffiche() {
		affiche = null;
		minuteur.annuler();
		minuteur = null;
	}

	PhotoAffichee photoAffichee() {
		return affiche == null ? null : affiche.projection();
	}

	byte[] lire(UUID id, boolean lecturePrivee, Instant maintenant) {
		if (affiche != null && affiche.projection().photoId().equals(id)
				&& (lecturePrivee || maintenant.isBefore(affiche.projection().echeance()))) {
			return affiche.photo().jpeg();
		}
		if (lecturePrivee) {
			Photo photo = photosEnAttente.getOrDefault(id, acceptees.get(id));
			if (photo != null) {
				return photo.jpeg();
			}
		}
		throw PhotosEnMemoire.photoIntrouvable().exception();
	}

	List<PhotoEnAttente> enAttente() {
		return photosEnAttente.entrySet().stream().map(e -> new PhotoEnAttente(e.getKey(), e.getValue().pseudo())).toList();
	}

	long octetsConserves() {
		long attente = photosEnAttente.values().stream().mapToLong(Photo::taille).sum();
		long file = acceptees.values().stream().mapToLong(Photo::taille).sum();
		return attente + file + (affiche == null ? 0 : affiche.photo().taille());
	}

	Set<Destination> publications(Set<Destination> destinations) {
		aPublier.addAll(destinations);
		return Set.copyOf(aPublier);
	}

	void publicationsTerminees() {
		aPublier.clear();
	}

	Accepte rejouer(Cle cle, Object empreinte) {
		Reception reception = recus.get(cle);
		if (reception == null) {
			return null;
		}
		if (!reception.empreinte().equals(empreinte)) {
			throw new Refuse(409, "ENVOI_REUTILISE", "Cette clé a déjà servi pour un autre envoi.").exception();
		}
		Accepte precedent = reception.recu();
		return new Accepte(precedent.accuse(), Set.of(), precedent.typeEvenement(), precedent.statutHttp());
	}

	void conserver(Cle cle, Object empreinte, Accepte recu) {
		recus.put(cle, new Reception(empreinte, recu));
	}

	record Cle(Acteur acteur, UUID envoiId) { }
	record Photo(UUID joueurId, String pseudo, byte[] jpeg) {
		int taille() { return jpeg.length; }
	}
	record Affiche(Photo photo, PhotoAffichee projection) { }
	record PhotoAffichee(UUID photoId, Instant echeance) { }
	record PhotoEnAttente(UUID photoId, String pseudo) { }
	private record Reception(Object empreinte, Accepte recu) { }
}
