package fr.croustyquizz.tempsreel;

import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatDeSoiree;
import fr.croustyquizz.manche.Refuse;

@Service
class ServiceMusiqueDAttente {
	private final DepotMusiqueDAttente depot;
	private final EtatDeSoiree etatDeSoiree;
	private final PublicationDeVues publication;
	private final VerrousSoiree verrous;
	private final TransactionTemplate transaction;

	ServiceMusiqueDAttente(DepotMusiqueDAttente depot, EtatDeSoiree etatDeSoiree, PublicationDeVues publication,
			VerrousSoiree verrous, PlatformTransactionManager transactions) {
		this.depot = depot;
		this.etatDeSoiree = etatDeSoiree;
		this.publication = publication;
		this.verrous = verrous;
		this.transaction = new TransactionTemplate(transactions);
	}

	void regler(UUID soireeId, MusiqueDAttente.Etat etat) {
		modifier(soireeId, courant -> {
			if (courant.etat() == etat) {
				throw new Refuse(409, "COMMANDE_SANS_EFFET", "Ce réglage de la Musique d’attente est déjà en vigueur.").exception();
			}
			return new MusiqueDAttente(etat, courant.morceauxDisponibles());
		});
	}

	void signaler(UUID soireeId, boolean morceauxDisponibles) {
		modifier(soireeId, courant -> new MusiqueDAttente(courant.etat(), morceauxDisponibles));
	}

	private void modifier(UUID soireeId, UnaryOperator<MusiqueDAttente> commande) {
		verrous.executer(soireeId, () -> {
			transaction.executeWithoutResult(statut -> {
				if (!etatDeSoiree.existe(soireeId)) {
					throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Soirée introuvable.").exception();
				}
				var courant = depot.lire(soireeId);
				var musique = commande.apply(courant);
				if (!courant.equals(musique)) {
					depot.enregistrer(soireeId, musique);
					publication.publier(soireeId, Set.of(Destination.SALLE, Destination.CONSOLE), "musique-attente-modifiee");
				}
			});
			return null;
		});
	}
}
