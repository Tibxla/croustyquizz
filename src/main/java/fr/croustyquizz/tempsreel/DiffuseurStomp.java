package fr.croustyquizz.tempsreel;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Numérote et conserve les vues, sous le même verrou que leur capture. */
@Component
public class DiffuseurStomp implements DiffuseurTempsReel {

	private final SimpMessagingTemplate messagerie;
	private final Clock horloge;
	private final VerrousSoiree verrous;
	private final CaptureVueRepli repli;
	private final CaptureVuePrototype prototype;
	private final Map<String, Enveloppe> dernieresVues = new ConcurrentHashMap<>();
	private final Map<String, Enveloppe> vuesPrototype = new ConcurrentHashMap<>();
	private final Map<String, Long> revisions = new ConcurrentHashMap<>();

	public DiffuseurStomp(SimpMessagingTemplate messagerie, Clock horloge, VerrousSoiree verrous, CaptureVueRepli repli, CaptureVuePrototype prototype) {
		this.messagerie = messagerie;
		this.horloge = horloge;
		this.verrous = verrous;
		this.repli = repli;
		this.prototype = prototype;
	}

	@Override
	public void diffuserSalle(UUID soireeId, Evenement evenement) {
		publier(soireeId, soireeId, Canaux.salle(soireeId), evenement);
	}

	@Override
	public void envoyerJoueur(UUID soireeId, UUID joueurId, Evenement evenement) {
		publier(soireeId, soireeId, Canaux.joueur(joueurId), evenement);
	}

	@Override
	public void envoyerConsole(UUID soireeId, Evenement evenement) {
		publier(soireeId, soireeId, Canaux.console(soireeId), evenement);
	}

	private void publier(UUID verrouId, UUID soireeId, String destination, Evenement evenement) {
		verrous.executer(verrouId, () -> {
			long revision = revisions.merge(destination, 1L, Long::sum);
			Enveloppe enveloppe = new Enveloppe(evenement.type(), soireeId, revision, horloge.instant(), evenement);
			// Même si le transport échoue, une nouvelle capture retrouve la mutation.
			(evenement instanceof VueSallePrototype ? vuesPrototype : dernieresVues).put(destination, enveloppe);
			messagerie.convertAndSend(destination, enveloppe);
			return null;
		});
	}

	public VueSynchronisee capturerSalle(UUID soireeId, UUID abonnementId) {
		return capturer(soireeId, Canaux.salle(soireeId), abonnementId, () -> repli.capturerSalle(soireeId));
	}

	public VueSynchronisee capturerConsole(UUID soireeId, UUID abonnementId) {
		return capturer(soireeId, Canaux.console(soireeId), abonnementId, () -> repli.capturerConsole(soireeId));
	}

	public VueSynchronisee capturerJoueur(UUID soireeId, UUID joueurId, UUID abonnementId) {
		return capturer(soireeId, Canaux.joueur(joueurId), abonnementId, () -> repli.capturerJoueur(soireeId, joueurId));
	}

	/** Le prototype conserve sa propre forme de repli, même avec l'assembleur métier. */
	public VueSynchronisee capturerPrototype(UUID soireeId, UUID abonnementId) {
		return verrous.executer(soireeId, () -> {
			var vue = vuesPrototype.get(Canaux.salle(soireeId));
			return new VueSynchronisee(vue == null ? 0 : vue.revision(), horloge.instant(), abonnementId,
					vue == null ? prototype.capturerSalle(soireeId) : vue.donnees());
		});
	}

	private VueSynchronisee capturer(UUID soireeId, String destination, UUID abonnementId,
			java.util.function.Supplier<Evenement> capture) {
		return verrous.executer(soireeId, () -> {
			Enveloppe derniere = dernieresVues.get(destination);
			Evenement vue = derniere == null ? capture.get() : derniere.donnees();
			return new VueSynchronisee(revisions.getOrDefault(destination, 0L), horloge.instant(), abonnementId, vue);
		});
	}
}
