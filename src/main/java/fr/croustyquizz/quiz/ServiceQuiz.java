package fr.croustyquizz.quiz;

import fr.croustyquizz.manche.EnvoiDeCommande;
import fr.croustyquizz.manche.FabriqueDeDeroulement;
import fr.croustyquizz.manche.PersistanceDuMode;
import fr.croustyquizz.manche.PreparationDeCommande;
import fr.croustyquizz.manche.ResultatDeCommande;
import fr.croustyquizz.manche.ServiceDeManche;
import fr.croustyquizz.manche.SocleDeManche;
import java.util.UUID;

public class ServiceQuiz extends ServiceDeManche<ReglagesQuiz, EtatQuizPersiste> {

	private final SocleDeManche socle;

	public ServiceQuiz(
			FabriqueDeDeroulement<ReglagesQuiz, EtatQuizPersiste> fabrique,
			PersistanceDuMode<EtatQuizPersiste> persistance,
			SocleDeManche socle
	) {
		super(fabrique, persistance, socle);
		this.socle = socle;
	}

	public ResultatDeCommande repondre(UUID mancheId, EnvoiDeCommande envoi, UUID etapeId, UUID propositionId) {
		return executer(mancheId, envoi, () -> PreparationDeCommande.prete(deroulement -> {
			EnvoyerReponse action = new EnvoyerReponse(
					envoi.acteur(),
					socle.horloge().maintenant(),
					envoi.envoiId(),
					etapeId,
					propositionId
			);
			return deroulement.recevoir(action);
		}));
	}
}
