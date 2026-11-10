package fr.croustyquizz.tempsreel;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Numérote et conserve les vues, sous le même verrou que leur capture. */
@Component
class DiffuseurStomp implements DiffuseurTempsReel {

	private final SimpMessagingTemplate messagerie;
	private final Clock horloge;
	private final VerrousSoiree verrous;
	private final CaptureVueRepli repli;
	private final Map<String, Enveloppe> dernieresVues = new ConcurrentHashMap<>();

	DiffuseurStomp(SimpMessagingTemplate messagerie, Clock horloge, VerrousSoiree verrous, CaptureVueRepli repli) {
		this.messagerie = messagerie;
		this.horloge = horloge;
		this.verrous = verrous;
		this.repli = repli;
	}

	@Override
	public void diffuserSalle(UUID soireeId, Evenement evenement) {
		publier(soireeId, soireeId, Canaux.salle(soireeId), evenement);
	}

	@Override
	public void envoyerJoueur(UUID joueurId, Evenement evenement) {
		// Le rattachement Joueur → Soirée arrivera avec les accès métier (#10).
		publier(joueurId, null, Canaux.joueur(joueurId), evenement);
	}

	@Override
	public void envoyerConsole(UUID soireeId, Evenement evenement) {
		publier(soireeId, soireeId, Canaux.console(soireeId), evenement);
	}

	private void publier(UUID verrouId, UUID soireeId, String destination, Evenement evenement) {
		synchronized (verrous.pour(verrouId)) {
			Enveloppe precedente = dernieresVues.get(destination);
			long revision = precedente == null ? 1 : precedente.revision() + 1;
			Enveloppe enveloppe = new Enveloppe(evenement.type(), soireeId, revision, horloge.instant(), evenement);
			// Même si le transport échoue, une nouvelle capture retrouve la mutation.
			dernieresVues.put(destination, enveloppe);
			messagerie.convertAndSend(destination, enveloppe);
		}
	}

	public VueSynchronisee capturerSalle(UUID soireeId, UUID abonnementId) {
		synchronized (verrous.pour(soireeId)) {
			Enveloppe derniere = dernieresVues.get(Canaux.salle(soireeId));
			Evenement vue = derniere == null ? repli.capturerSalle(soireeId) : derniere.donnees();
			return new VueSynchronisee(derniere == null ? 0 : derniere.revision(), horloge.instant(), abonnementId, vue);
		}
	}
}
