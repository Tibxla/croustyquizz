package fr.croustyquizz.manche;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Adaptation HTTP et corps enregistrés : aucune décision de jeu n'est recalculée ici. */
public final class ReponsesDeCommande {
	private ReponsesDeCommande() { }
	public static int statut(ResultatDeCommande resultat) {
		return resultat instanceof Refuse refus ? refus.statutHttp() : ((Accepte) resultat).statutHttp();
	}
	public static Object corps(ResultatDeCommande resultat, String route) {
		return resultat instanceof Refuse refus ? Probleme.de(refus, route) : ((Accepte) resultat).accuse();
	}
	public static ResponseEntity<?> http(ResultatDeCommande resultat, String route) {
		var reponse = ResponseEntity.status(statut(resultat)).cacheControl(CacheControl.noStore());
		if (resultat instanceof Refuse) { reponse.contentType(MediaType.APPLICATION_PROBLEM_JSON); }
		else if (((Accepte) resultat).accuse() != null) { reponse.contentType(MediaType.APPLICATION_JSON); }
		return reponse.body(corps(resultat, route));
	}
}
