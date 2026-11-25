package fr.croustyquizz.tempsreel;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Probleme;
import fr.croustyquizz.manche.Refuse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limite de requêtes du Buzz, des Réponses et de l'accès à une Soirée (#4), avant toute exposition publique : un
 * seau à jetons par route et par clé, en mémoire, car l'application tourne sur une seule instance. Au-delà, la
 * requête n'atteint pas le contrôleur et reçoit {@code 429 TROP_DE_REQUETES} au format Probleme, avec
 * {@code Retry-After} (docs/api/README.md, « Limite de requêtes »).
 *
 * <p>Clés. Le Buzz, les Réponses et le QR code comptent par jeton : une empreinte de l'en-tête Authorization,
 * jamais le secret lui-même, et sans consulter la base avant la limite. Sans jeton, et pour l'arrivée par code
 * qui n'a pas encore d'identité, la clé est l'adresse IP de {@link HttpServletRequest#getRemoteAddr()} : derrière
 * Render ou Tailscale Funnel, Tomcat y place déjà l'adresse du client tirée de X-Forwarded-For, mais seulement
 * quand la connexion vient d'un relais de confiance ({@code server.forward-headers-strategy: native}, relais
 * d'adresse privée ou locale). Ce filtre ne lit donc jamais X-Forwarded-For lui-même : un client ne peut pas
 * choisir sa clé. Une adresse IPv6 compte par bloc de 64 bits, qu'un seul abonné reçoit en entier.
 *
 * <p>L'arrivée par code a deux seaux par adresse : toutes les arrivées, et les seuls codes inconnus. Un essai de
 * code est pris avant la requête, pour que des envois simultanés ne passent pas tous, puis rendu si la réponse
 * n'est pas {@code 404} : une salle derrière un seul Wi-Fi arrive d'un coup, un script qui essaie des codes est
 * arrêté.
 *
 * <p>Mémoire bornée sans tâche de fond : à chaque requête limitée, au plus une fois par minute, les seaux
 * redevenus pleins sont oubliés, ce qui ne change rien puisqu'un seau plein vaut un seau neuf. Au plafond de clés,
 * une nouvelle clé passe sans être comptée plutôt que d'être refusée : remplir la table ne bloque pas la salle.
 * Le temps vient de l'Horloge, remplacée par une horloge factice dans les tests.
 */
public final class LimiteDeRequetes extends OncePerRequestFilter {

	static final String CODE = "TROP_DE_REQUETES";
	private static final String POST = "POST";
	private static final Logger JOURNAL = LoggerFactory.getLogger(LimiteDeRequetes.class);
	private static final long SECONDE = Duration.ofSeconds(1).toNanos();
	private static final long PURGE_REGULIERE = Duration.ofMinutes(1).toNanos();
	private static final long PURGE_AU_PLAFOND = SECONDE;
	private static final int OCTETS_EMPREINTE = 16;
	private static final int OCTETS_BLOC_IPV6 = 8;

	private final boolean actif;
	private final int plafondCles;
	private final Horloge horloge;
	private final JsonMapper json;
	private final List<Regle> regles;
	private final Map<CleDeSeau, SeauDeJetons> seaux = new ConcurrentHashMap<>();
	private final AtomicLong dernierePurge;
	private final AtomicLong dernierAvertissement = new AtomicLong(Long.MIN_VALUE);

	public LimiteDeRequetes(ReglagesDesLimites reglages, Horloge horloge, JsonMapper json) {
		this.actif = reglages.actif();
		this.plafondCles = reglages.plafondCles();
		this.horloge = horloge;
		this.json = json;
		this.dernierePurge = new AtomicLong(nanos(horloge.maintenant()));
		String soiree = "/api/soirees/{soireeId}";
		String manche = soiree + "/manches/{mancheId}";
		this.regles = List.of(
				new Regle("buzz", POST, soiree + "/buzz", Cle.JETON, false, reglages.buzz()),
				new Regle("reponse", POST, manche + "/questions/{etapeId}/reponses", Cle.JETON, false, reglages.reponses()),
				new Regle("brouillon", "PUT", manche + "/morceaux/{etapeId}/brouillon", Cle.JETON, false, reglages.reponses()),
				new Regle("proposition", POST, manche + "/morceaux/{etapeId}/proposition", Cle.JETON, false,
						reglages.reponses()),
				new Regle("vote", POST, manche + "/passages/{passageId}/vote", Cle.JETON, false, reglages.reponses()),
				new Regle("arrivees", POST, "/api/soirees/rejoindre", Cle.ADRESSE, false, reglages.arrivees()),
				new Regle("codes-inconnus", POST, "/api/soirees/rejoindre", Cle.ADRESSE, true, reglages.codesInconnus()),
				new Regle("acces-ecran", POST, soiree + "/acces-ecran", Cle.ADRESSE_ET_SOIREE, false, reglages.accesEcran()),
				new Regle("qr-code", "GET", soiree + "/qrcode", Cle.JETON, false, reglages.qrCode()));
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest requete) {
		return !actif;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
			throws ServletException, IOException {
		PathContainer chemin = PathContainer.parsePath(requete.getRequestURI().substring(requete.getContextPath().length()));
		long maintenant = nanos(horloge.maintenant());
		List<CleDeSeau> prises = new ArrayList<>();
		for (Regle regle : regles) {
			PathPattern.PathMatchInfo correspondance = regle.correspondance(requete.getMethod(), chemin);
			if (correspondance == null) {
				continue;
			}
			CleDeSeau cle = new CleDeSeau(regle, cle(regle.cle, requete, correspondance));
			long attente = prendre(cle, maintenant);
			if (attente > 0) {
				prises.forEach(this::rendre);
				refuser(requete, reponse, attente);
				return;
			}
			prises.add(cle);
		}
		chaine.doFilter(requete, reponse);
		if (reponse.getStatus() != HttpServletResponse.SC_NOT_FOUND) {
			prises.stream().filter(cle -> cle.regle().seulementCodesInconnus).forEach(this::rendre);
		}
	}

	/** Nombre de seaux en mémoire, pour les tests de purge et de plafond. */
	int nombreDeSeaux() {
		return seaux.size();
	}

	private long prendre(CleDeSeau cle, long maintenant) {
		purgerSiBesoin(maintenant, false);
		if (!seaux.containsKey(cle) && seaux.size() >= plafondCles) {
			purgerSiBesoin(maintenant, true);
			if (seaux.size() >= plafondCles) {
				avertirDuPlafond(maintenant);
				return 0;
			}
		}
		long[] attente = new long[1];
		boolean[] signaler = new boolean[1];
		seaux.compute(cle, (inutile, seau) -> {
			SeauDeJetons courant = seau == null ? new SeauDeJetons(cle.regle().seuil.rafale(),
					cle.regle().seuil.intervalle(), maintenant) : seau;
			attente[0] = courant.prendre(maintenant);
			signaler[0] = attente[0] > 0 && courant.premierRefus();
			return courant;
		});
		if (signaler[0] && JOURNAL.isInfoEnabled()) {
			JOURNAL.info("Limite de requêtes atteinte : {} pour {}.", cle.regle().nom, cle.valeur());
		}
		return attente[0];
	}

	private void rendre(CleDeSeau cle) {
		seaux.computeIfPresent(cle, (inutile, seau) -> {
			seau.rendre();
			return seau;
		});
	}

	private void purgerSiBesoin(long maintenant, boolean auPlafond) {
		long precedente = dernierePurge.get();
		long ecart = auPlafond ? PURGE_AU_PLAFOND : PURGE_REGULIERE;
		if (maintenant - precedente < ecart || !dernierePurge.compareAndSet(precedente, maintenant)) {
			return;
		}
		for (CleDeSeau cle : seaux.keySet()) {
			seaux.computeIfPresent(cle, (inutile, seau) -> seau.estPlein(maintenant) ? null : seau);
		}
	}

	private void avertirDuPlafond(long maintenant) {
		long precedent = dernierAvertissement.get();
		if ((precedent == Long.MIN_VALUE || maintenant - precedent >= PURGE_REGULIERE)
				&& dernierAvertissement.compareAndSet(precedent, maintenant)) {
			JOURNAL.warn("Limite de requêtes : plafond de {} clés atteint, les nouvelles clés passent sans être comptées.",
					plafondCles);
		}
	}

	private void refuser(HttpServletRequest requete, HttpServletResponse reponse, long attente)
			throws IOException {
		long secondes = Math.max(1, (attente + SECONDE - 1) / SECONDE);
		String detail = "Trop de requêtes en peu de temps. Réessaie dans " + secondes
				+ (secondes == 1 ? " seconde." : " secondes.");
		reponse.setStatus(429);
		reponse.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(secondes));
		reponse.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
		reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
		json.writeValue(reponse.getOutputStream(), Probleme.de(new Refuse(429, CODE, detail), requete.getRequestURI()));
	}

	private static String cle(Cle type, HttpServletRequest requete, PathPattern.PathMatchInfo correspondance) {
		String autorisation = requete.getHeader(HttpHeaders.AUTHORIZATION);
		return switch (type) {
			case JETON -> autorisation == null || autorisation.isBlank() ? adresse(requete) : "jeton:" + empreinte(autorisation);
			case ADRESSE -> adresse(requete);
			case ADRESSE_ET_SOIREE -> adresse(requete) + " soiree:" + correspondance.getUriVariables().get("soireeId");
		};
	}

	/** Adresse du client, déjà corrigée par Tomcat derrière un relais de confiance ; IPv6 par bloc de 64 bits. */
	static String adresse(HttpServletRequest requete) {
		String brute = requete.getRemoteAddr();
		if (brute == null || brute.indexOf(':') < 0) {
			return "ip:" + brute;
		}
		try {
			// Une adresse littérale ne déclenche aucune résolution DNS.
			InetAddress lue = InetAddress.getByName(brute);
			if (lue instanceof Inet6Address) {
				return "ip6:" + HexFormat.of().formatHex(Arrays.copyOf(lue.getAddress(), OCTETS_BLOC_IPV6)) + "/64";
			}
			return "ip:" + lue.getHostAddress();
		} catch (UnknownHostException illisible) {
			return "ip:" + brute;
		}
	}

	private static String empreinte(String autorisation) {
		try {
			byte[] condense = MessageDigest.getInstance("SHA-256").digest(autorisation.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(condense, OCTETS_EMPREINTE));
		} catch (NoSuchAlgorithmException absent) {
			throw new IllegalStateException("SHA-256 est fourni par toute JVM.", absent);
		}
	}

	private static long nanos(Instant instant) {
		return instant.getEpochSecond() * SECONDE + instant.getNano();
	}

	private enum Cle { JETON, ADRESSE, ADRESSE_ET_SOIREE }

	/** Route limitée ; une instance par route, comparée par identité dans les clés de seau. */
	private static final class Regle {

		private final String nom;
		private final String methode;
		private final PathPattern motif;
		private final Cle cle;
		private final boolean seulementCodesInconnus;
		private final ReglagesDesLimites.Seuil seuil;

		Regle(String nom, String methode, String motif, Cle cle, boolean seulementCodesInconnus, ReglagesDesLimites.Seuil seuil) {
			this.nom = nom;
			this.methode = methode;
			this.motif = PathPatternParser.defaultInstance.parse(motif);
			this.cle = cle;
			this.seulementCodesInconnus = seulementCodesInconnus;
			this.seuil = seuil;
		}

		PathPattern.PathMatchInfo correspondance(String methodeRecue, PathContainer chemin) {
			return methode.equals(methodeRecue) ? motif.matchAndExtract(chemin) : null;
		}

	}

	private record CleDeSeau(Regle regle, String valeur) {
	}

}
