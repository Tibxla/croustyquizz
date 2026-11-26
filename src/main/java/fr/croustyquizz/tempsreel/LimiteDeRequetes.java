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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
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
 * Limite de requêtes du Buzz, des Réponses, de l'accès à une Soirée et du compte de l'Animateur (#4, #238) : un
 * seau à jetons par seuil et par clé, en mémoire, car l'application tourne sur une seule instance. Au-delà, la
 * requête reçoit {@code 429 TROP_DE_REQUETES} au format Probleme, avec
 * {@code Retry-After} (docs/api/README.md, « Limite de requêtes »).
 *
 * <p>Clés. Le Buzz, les Réponses et le QR code comptent par jeton : une empreinte de l'en-tête Authorization,
 * jamais le secret lui-même, et sans consulter la base avant la limite. Sans jeton, pour l'arrivée par code qui
 * n'a pas encore d'identité, pour l'accès de l'Écran et pour les mutations du compte, la clé est l'adresse IP de
 * {@link HttpServletRequest#getRemoteAddr()} : derrière
 * Render ou Tailscale Funnel, Tomcat y place déjà l'adresse du client tirée de X-Forwarded-For, mais seulement
 * quand la connexion vient d'un relais de confiance ({@code server.forward-headers-strategy: native}, relais
 * d'adresse privée ou locale). Ce filtre ne lit donc jamais X-Forwarded-For lui-même : un client ne peut pas
 * choisir sa clé. Une adresse IPv6 compte par bloc de 64 bits, qu'un seul abonné reçoit en entier.
 *
 * <p>À la connexion, le contrôleur applique aussi le seuil par e-mail normalisé après validation du corps,
 * avant la recherche du compte et le calcul BCrypt. L'e-mail est conservé et journalisé sous forme d'empreinte.
 *
 * <p>L'arrivée par code a deux seaux par adresse : toutes les arrivées, prises avant la requête, et les seuls
 * codes inconnus, pris une fois la réponse connue. Un code inconnu au-delà de la limite voit son {@code 404}
 * remplacé par un {@code 429} ; une arrivée qui aboutit ne touche jamais ce seau, si bien qu'une salle derrière un
 * seul Wi-Fi arrive avec le bon code même quand un voisin a épuisé les essais du réseau. Le bon code n'étant
 * jamais refusé, c'est le seau de toutes les arrivées qui borne le rythme des essais d'un script.
 *
 * <p>Mémoire bornée sans tâche de fond : à chaque requête limitée, au plus une fois par minute, les seaux
 * redevenus pleins sont oubliés, ce qui ne change rien puisqu'un seau plein vaut un seau neuf. Chaque seuil a sa
 * table et son plafond : des jetons inventés ne remplissent que la table de leur route, jamais celle des arrivées.
 * Au plafond, une nouvelle clé passe sans être comptée plutôt que d'être refusée, pour ne pas bloquer la salle,
 * sauf sur les codes inconnus : un code inconnu d'une adresse nouvelle y reçoit {@code 429}, le bon code passe.
 * Le temps vient de l'Horloge, remplacée par une horloge factice dans les tests.
 */
public final class LimiteDeRequetes extends OncePerRequestFilter {

	static final String CODE = "TROP_DE_REQUETES";
	private static final String POST = "POST";
	private static final String ANIMATEURS = "/api/animateurs";
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
	private final TableDeSeaux connexionEmail;
	private final List<TableDeSeaux> tables;
	private final AtomicLong dernierePurge;

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
				new Regle("codes-inconnus", POST, "/api/soirees/rejoindre", Cle.ADRESSE, true, reglages.codesInconnus()),
				new Regle("arrivees", POST, "/api/soirees/rejoindre", Cle.ADRESSE, false, reglages.arrivees()),
				new Regle("connexion-adresse", POST, ANIMATEURS + "/session", Cle.ADRESSE, false, reglages.connexionAdresse()),
				new Regle("inscription-adresse", POST, ANIMATEURS, Cle.ADRESSE, false, reglages.inscriptionAdresse()),
				new Regle("acces-ecran", POST, soiree + "/acces-ecran", Cle.ADRESSE, false, reglages.accesEcran()),
				new Regle("qr-code", "GET", soiree + "/qrcode", Cle.JETON, false, reglages.qrCode()));
		this.connexionEmail = new TableDeSeaux("connexion-email", reglages.connexionEmail(), false);
		this.tables = Stream.concat(regles.stream().map(Regle::table), Stream.of(connexionEmail)).toList();
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
		CleDeSeau codesInconnus = null;
		for (Regle regle : regles) {
			if (!regle.concerne(requete.getMethod(), chemin)) {
				continue;
			}
			CleDeSeau cle = new CleDeSeau(regle.table, cle(regle.cle, requete));
			if (regle.seulementCodesInconnus) {
				codesInconnus = cle;
				continue;
			}
			long attente = prendre(cle, maintenant);
			if (attente > 0) {
				prises.forEach(this::rendre);
				refuser(requete, reponse, attente);
				return;
			}
			prises.add(cle);
		}
		if (codesInconnus == null) {
			chaine.doFilter(requete, reponse);
		} else {
			filtrerLesCodesInconnus(requete, reponse, chaine, codesInconnus);
		}
	}

	/** Compte un essai pour l'e-mail normalisé de la connexion et rend le refus HTTP si le seuil est atteint. */
	public Optional<ResponseEntity<Probleme>> compterEssaiDeConnexion(String emailNormalise, HttpServletRequest requete) {
		if (!actif) {
			return Optional.empty();
		}
		long attente = prendre(new CleDeSeau(connexionEmail, empreinte(emailNormalise)), nanos(horloge.maintenant()));
		return attente > 0 ? Optional.of(refus(requete, attente)) : Optional.empty();
	}

	/**
	 * Le seau des codes inconnus n'est pris qu'après un {@code 404} : la réponse est gardée en mémoire le temps de
	 * savoir s'il faut la remplacer par un {@code 429}. Un code inconnu ne change rien côté serveur, si bien que
	 * le remplacement ne cache aucune mutation.
	 */
	private void filtrerLesCodesInconnus(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine,
			CleDeSeau cle) throws ServletException, IOException {
		var tampon = new ContentCachingResponseWrapper(reponse);
		chaine.doFilter(requete, tampon);
		if (tampon.getStatus() == HttpServletResponse.SC_NOT_FOUND && !reponse.isCommitted()) {
			long attente = prendre(cle, nanos(horloge.maintenant()));
			if (attente > 0) {
				refuser(requete, reponse, attente);
				return;
			}
		}
		tampon.copyBodyToResponse();
	}

	/** Nombre de seaux en mémoire, toutes routes confondues, pour les tests de purge et de plafond. */
	int nombreDeSeaux() {
		return tables.stream().mapToInt(table -> table.seaux.size()).sum();
	}

	private long prendre(CleDeSeau cle, long maintenant) {
		purgerSiBesoin(maintenant, false);
		Map<String, SeauDeJetons> seaux = cle.table().seaux;
		if (!seaux.containsKey(cle.valeur()) && seaux.size() >= plafondCles) {
			purgerSiBesoin(maintenant, true);
			if (seaux.size() >= plafondCles) {
				return auPlafond(cle.table(), maintenant);
			}
		}
		long[] attente = new long[1];
		boolean[] signaler = new boolean[1];
		seaux.compute(cle.valeur(), (inutile, seau) -> {
			SeauDeJetons courant = seau == null ? nouveauSeau(cle, maintenant) : seau;
			attente[0] = courant.prendre(maintenant);
			signaler[0] = attente[0] > 0 && courant.premierRefus();
			return courant;
		});
		if (signaler[0] && JOURNAL.isInfoEnabled()) {
			JOURNAL.info("Limite de requêtes atteinte : {} pour {}.", cle.table().nom, cle.valeur());
		}
		return attente[0];
	}

	private static SeauDeJetons nouveauSeau(CleDeSeau cle, long maintenant) {
		return new SeauDeJetons(cle.table().seuil.rafale(), cle.table().seuil.intervalle(), maintenant);
	}

	private void rendre(CleDeSeau cle) {
		cle.table().seaux.computeIfPresent(cle.valeur(), (inutile, seau) -> {
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
		for (TableDeSeaux table : tables) {
			for (String cle : table.seaux.keySet()) {
				table.seaux.computeIfPresent(cle, (inutile, seau) -> seau.estPlein(maintenant) ? null : seau);
			}
		}
	}

	/**
	 * Table pleine : la nouvelle clé passe sans être comptée, sauf sur les codes inconnus, où elle est refusée pour
	 * un intervalle. Le journal l'avertit au plus une fois par minute et par route.
	 */
	private long auPlafond(TableDeSeaux table, long maintenant) {
		long precedent = table.dernierAvertissement.get();
		if ((precedent == Long.MIN_VALUE || maintenant - precedent >= PURGE_REGULIERE)
				&& table.dernierAvertissement.compareAndSet(precedent, maintenant) && JOURNAL.isWarnEnabled()) {
			JOURNAL.warn("Limite de requêtes : plafond de {} clés atteint pour {}, {}.", plafondCles, table.nom,
					table.refuserAuPlafond ? "les codes inconnus des nouvelles adresses sont refusés"
							: "les nouvelles clés passent sans être comptées");
		}
		return table.refuserAuPlafond ? table.seuil.intervalle().toNanos() : 0;
	}

	private void refuser(HttpServletRequest requete, HttpServletResponse reponse, long attente)
			throws IOException {
		var refus = refus(requete, attente);
		reponse.setStatus(refus.getStatusCode().value());
		refus.getHeaders().forEach((nom, valeurs) -> reponse.setHeader(nom, valeurs.getFirst()));
		reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
		json.writeValue(reponse.getOutputStream(), refus.getBody());
	}

	private static ResponseEntity<Probleme> refus(HttpServletRequest requete, long attente) {
		long secondes = Math.max(1, (attente + SECONDE - 1) / SECONDE);
		String detail = "Trop de requêtes en peu de temps. Réessaie dans " + secondes
				+ (secondes == 1 ? " seconde." : " secondes.");
		return ResponseEntity.status(429).header(HttpHeaders.RETRY_AFTER, Long.toString(secondes))
				.cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(Probleme.de(new Refuse(429, CODE, detail), requete.getRequestURI()));
	}

	private static String cle(Cle type, HttpServletRequest requete) {
		String autorisation = requete.getHeader(HttpHeaders.AUTHORIZATION);
		return switch (type) {
			case JETON -> autorisation == null || autorisation.isBlank() ? adresse(requete) : "jeton:" + empreinte(autorisation);
			// Aucun segment du chemin dans la clé : une seule adresse ne remplit pas la table de sa route.
			case ADRESSE -> adresse(requete);
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

	private static String empreinte(String valeur) {
		try {
			byte[] condense = MessageDigest.getInstance("SHA-256").digest(valeur.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(condense, OCTETS_EMPREINTE));
		} catch (NoSuchAlgorithmException absent) {
			throw new IllegalStateException("SHA-256 est fourni par toute JVM.", absent);
		}
	}

	private static long nanos(Instant instant) {
		return instant.getEpochSecond() * SECONDE + instant.getNano();
	}

	private enum Cle { JETON, ADRESSE }

	/** Associe une table de seaux aux requêtes HTTP traitées par le filtre. */
	private record Regle(String methode, PathPattern motif, Cle cle, boolean seulementCodesInconnus, TableDeSeaux table) {

		Regle(String nom, String methode, String motif, Cle cle, boolean seulementCodesInconnus, ReglagesDesLimites.Seuil seuil) {
			this(methode, PathPatternParser.defaultInstance.parse(motif), cle, seulementCodesInconnus,
					new TableDeSeaux(nom, seuil, seulementCodesInconnus));
		}

		boolean concerne(String methodeRecue, PathContainer chemin) {
			return methode.equals(methodeRecue) && motif.matches(chemin);
		}

	}

	private static final class TableDeSeaux {
		private final String nom;
		private final ReglagesDesLimites.Seuil seuil;
		private final boolean refuserAuPlafond;
		private final Map<String, SeauDeJetons> seaux = new ConcurrentHashMap<>();
		private final AtomicLong dernierAvertissement = new AtomicLong(Long.MIN_VALUE);

		TableDeSeaux(String nom, ReglagesDesLimites.Seuil seuil, boolean refuserAuPlafond) {
			this.nom = nom;
			this.seuil = seuil;
			this.refuserAuPlafond = refuserAuPlafond;
		}
	}

	private record CleDeSeau(TableDeSeaux table, String valeur) {
	}

}
