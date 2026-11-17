package fr.croustyquizz.manche;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Refus et validation au format Probleme ; une erreur inattendue reste hors contrat et journalisée. */
@RestControllerAdvice
public class ErreursHttp {
	private static final Logger JOURNAL = LoggerFactory.getLogger(ErreursHttp.class);
	@ExceptionHandler(RefusDeCommande.class)
	public ResponseEntity<?> refus(RefusDeCommande erreur, HttpServletRequest requete) {
		return ReponsesDeCommande.http(erreur.refus(), requete.getRequestURI());
	}
	@ExceptionHandler({DataAccessException.class, TransactionException.class})
	public ResponseEntity<?> indisponible(RuntimeException erreur, HttpServletRequest requete) {
		JOURNAL.error("Persistance de Manche indisponible", erreur);
		return ReponsesDeCommande.http(new Refuse(503, "SERVICE_INDISPONIBLE", "Le service est temporairement indisponible."),
				requete.getRequestURI());
	}
	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> autre(Exception erreur, HttpServletRequest requete) {
		if (erreur instanceof ErrorResponse reponse && reponse.getStatusCode().is4xxClientError()) {
			int statut = reponse.getStatusCode().value();
			String code = statut == 404 ? "RESSOURCE_INTROUVABLE" : "REQUETE_INVALIDE";
			return ReponsesDeCommande.http(new Refuse(statut, code, "La requête est invalide."), requete.getRequestURI());
		}
		if (erreur instanceof ConstraintViolationException
				|| erreur instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
				|| erreur instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
			return ReponsesDeCommande.http(new Refuse(400, "REQUETE_INVALIDE", "La requête est invalide."), requete.getRequestURI());
		}
		JOURNAL.error("Erreur inattendue dans le traitement HTTP", erreur);
		return ResponseEntity.internalServerError().cacheControl(CacheControl.noStore()).build();
	}
}
