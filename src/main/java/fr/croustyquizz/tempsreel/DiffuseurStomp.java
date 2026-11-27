package fr.croustyquizz.tempsreel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Numérote et conserve les vues, sous le même verrou que leur capture. */
@Component
public class DiffuseurStomp implements DiffuseurTempsReel {

	private final SimpMessagingTemplate messagerie;
	private final Clock horloge;
	private final VerrousSoiree verrous;
	private final CaptureVueRepli repli;
	private final CaptureVuePrototype prototype;
	private final Duration delaiEviction;
	private final Map<UUID, VuesDeSoiree> vues = new ConcurrentHashMap<>();

	public DiffuseurStomp(SimpMessagingTemplate messagerie, Clock horloge, VerrousSoiree verrous,
			CaptureVueRepli repli, CaptureVuePrototype prototype,
			@Value("${croustyquizz.tempsreel.delai-eviction-vues}") Duration delaiEviction) {
		this.messagerie = messagerie;
		this.horloge = horloge;
		this.verrous = verrous;
		this.repli = repli;
		this.prototype = prototype;
		this.delaiEviction = delaiEviction;
	}

	@Override
	public void diffuserSalle(UUID soireeId, Evenement evenement) {
		publier(soireeId, Canaux.salle(soireeId), evenement);
	}

	@Override
	public void envoyerJoueur(UUID soireeId, UUID joueurId, Evenement evenement) {
		publier(soireeId, Canaux.joueur(joueurId), evenement);
	}

	@Override
	public void envoyerConsole(UUID soireeId, Evenement evenement) {
		publier(soireeId, Canaux.console(soireeId), evenement);
	}

	private void publier(UUID soireeId, String destination, Evenement evenement) {
		verrous.executer(soireeId, () -> {
			Instant maintenant = horloge.instant();
			Instant echeance = evenement instanceof VueComposee vue && vue.soireeTermineeLe() != null
					? vue.soireeTermineeLe().plus(delaiEviction) : null;
			if (echeanceAtteinte(echeance, maintenant)) {
				vues.remove(soireeId);
				return null;
			}
			var soiree = vues.computeIfAbsent(soireeId, id -> new VuesDeSoiree());
			if (echeance != null) {
				soiree.echeance = echeance;
			}
			if (evincerSiEchue(soireeId, soiree, maintenant)) {
				return null;
			}
			long revision = soiree.revisions.merge(destination, 1L, Long::sum);
			Enveloppe enveloppe = new Enveloppe(evenement.type(), soireeId, revision, maintenant, evenement);
			// Même si le transport échoue, une nouvelle capture retrouve la mutation.
			(evenement instanceof VueSallePrototype ? soiree.vuesPrototype : soiree.dernieresVues).put(destination, enveloppe);
			messagerie.convertAndSend(destination, enveloppe);
			return null;
		});
	}

	@Scheduled(fixedDelay = 60_000)
	void evincerLesSoireesEchues() {
		Instant maintenant = horloge.instant();
		vues.forEach((id, soiree) -> {
			if (echeanceAtteinte(soiree.echeance, maintenant)) {
				verrous.executer(id, () -> evincerSiEchue(id, vues.get(id), horloge.instant()));
			}
		});
	}

	private static boolean echeanceAtteinte(Instant echeance, Instant maintenant) {
		return echeance != null && !maintenant.isBefore(echeance);
	}

	private boolean evincerSiEchue(UUID soireeId, VuesDeSoiree soiree, Instant maintenant) {
		return soiree != null && echeanceAtteinte(soiree.echeance, maintenant) && vues.remove(soireeId, soiree);
	}

	private VuesDeSoiree vuesConservees(UUID soireeId) {
		var soiree = vues.get(soireeId);
		return evincerSiEchue(soireeId, soiree, horloge.instant()) ? null : soiree;
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
			var soiree = vuesConservees(soireeId);
			var vue = soiree == null ? null : soiree.vuesPrototype.get(Canaux.salle(soireeId));
			return new VueSynchronisee(vue == null ? 0 : vue.revision(), horloge.instant(), abonnementId,
					vue == null ? prototype.capturerSalle(soireeId) : vue.donnees());
		});
	}

	private VueSynchronisee capturer(UUID soireeId, String destination, UUID abonnementId,
			Supplier<Evenement> capture) {
		return verrous.executer(soireeId, () -> {
			var soiree = vuesConservees(soireeId);
			long revision = soiree == null ? 0 : soiree.revisions.getOrDefault(destination, 0L);
			Enveloppe derniere = soiree == null ? null : soiree.dernieresVues.get(destination);
			Evenement vue = derniere == null ? capture.get() : derniere.donnees();
			return new VueSynchronisee(revision, horloge.instant(), abonnementId, vue);
		});
	}

	private static final class VuesDeSoiree {
		// Seule l'échéance est lue par la purge en dehors du verrou de Soirée.
		private volatile Instant echeance;
		private final Map<String, Enveloppe> dernieresVues = new HashMap<>();
		private final Map<String, Enveloppe> vuesPrototype = new HashMap<>();
		private final Map<String, Long> revisions = new HashMap<>();
	}
}
