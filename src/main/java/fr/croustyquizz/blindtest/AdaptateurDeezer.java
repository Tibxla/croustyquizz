package fr.croustyquizz.blindtest;

import static java.net.HttpURLConnection.HTTP_OK;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link CatalogueMusical} sur l'API publique Deezer, sans clé. Toute panne (réseau, délai, statut inattendu, erreur
 * JSON de Deezer, réponse mal formée) devient {@link SourceMusicaleIndisponible}. Les liens d'extrait signés expirent :
 * ils sont redemandés à chaque appel, sans cache.
 */
public final class AdaptateurDeezer implements CatalogueMusical {

	/** Taille d'une page de playlist demandée à Deezer. */
	static final int TAILLE_PAGE = 100;
	/** Pages de playlist lues au plus, soit 2 000 Morceaux, la taille maximale d'une playlist Deezer. */
	static final int PAGES_MAX = 20;

	private final ProprietesCatalogueMusical proprietes;
	private final HttpClient client;
	private final JsonMapper json;

	public AdaptateurDeezer(ProprietesCatalogueMusical proprietes, HttpClient client, JsonMapper json) {
		if (client.followRedirects() != HttpClient.Redirect.NEVER) {
			throw new IllegalArgumentException("Le catalogue musical ne suit aucune redirection.");
		}
		this.proprietes = proprietes;
		this.client = client;
		this.json = json;
	}

	@Override
	public List<Morceau> morceauxDePlaylist(long playlistId) {
		List<Morceau> morceaux = new ArrayList<>();
		int index = 0;
		for (int page = 0; page < PAGES_MAX; page++) {
			JsonNode racine = consulter("playlist/" + playlistId + "/tracks?index=" + index + "&limit=" + TAILLE_PAGE);
			JsonNode donnees = donnees(racine);
			retenirJouables(donnees, morceaux);
			index += donnees.size();
			if (donnees.isEmpty() || index >= racine.path("total").asLong(0)) {
				break;
			}
		}
		return List.copyOf(morceaux);
	}

	@Override
	public List<Morceau> morceauxDuGenre(long genreId) {
		List<Morceau> morceaux = new ArrayList<>();
		retenirJouables(donnees(consulter("chart/" + genreId + "/tracks?limit=" + TAILLE_PAGE)), morceaux);
		return List.copyOf(morceaux);
	}

	@Override
	public Optional<URI> lienExtrait(long idDeezer) {
		JsonNode morceau = consulter("track/" + idDeezer);
		if (!morceau.isObject() || morceau.path("id").asLong() != idDeezer) {
			throw new SourceMusicaleIndisponible("Détail de Morceau Deezer mal formé.");
		}
		return extrait(morceau);
	}

	private static JsonNode donnees(JsonNode racine) {
		JsonNode donnees = racine.path("data");
		if (!donnees.isArray()) {
			throw new SourceMusicaleIndisponible("Liste de Morceaux Deezer attendue.");
		}
		return donnees;
	}

	/** Garde les Morceaux lisibles qui ont un extrait ; un candidat mal formé est écarté sans faire échouer la liste. */
	private static void retenirJouables(JsonNode donnees, List<Morceau> morceaux) {
		for (JsonNode candidat : donnees) {
			if (extrait(candidat).isEmpty()) {
				continue;
			}
			try {
				morceaux.add(morceau(candidat));
			} catch (IllegalArgumentException mal) {
				continue;
			}
		}
	}

	private static Morceau morceau(JsonNode candidat) {
		JsonNode id = candidat.path("id");
		if (!id.isIntegralNumber() || !id.canConvertToLong()) {
			throw new IllegalArgumentException("Identifiant Deezer invalide.");
		}
		String court = texte(candidat.path("title_short"));
		String titre = court == null ? texte(candidat.path("title")) : court;
		String artiste = texte(candidat.path("artist").path("name"));
		if (titre == null || artiste == null) {
			throw new IllegalArgumentException("Titre et artiste requis.");
		}
		return new Morceau(id.asLong(), titre, artiste, uri(candidat.path("album").path("cover_medium")));
	}

	/** Lien d'extrait d'un Morceau lisible ; vide s'il n'est pas lisible ou si {@code preview} est vide. */
	private static Optional<URI> extrait(JsonNode morceau) {
		if (morceau.path("readable").isBoolean() && !morceau.path("readable").asBoolean()) {
			return Optional.empty();
		}
		return Optional.ofNullable(uri(morceau.path("preview")));
	}

	private static String texte(JsonNode champ) {
		if (!champ.isString()) {
			return null;
		}
		String valeur = champ.asString().strip();
		return valeur.isEmpty() ? null : valeur;
	}

	/** Lien HTTP absolu, ou {@code null} s'il manque ou ne se lit pas comme tel. */
	private static URI uri(JsonNode champ) {
		String valeur = texte(champ);
		if (valeur == null) {
			return null;
		}
		try {
			URI lien = new URI(valeur);
			boolean web = "https".equalsIgnoreCase(lien.getScheme()) || "http".equalsIgnoreCase(lien.getScheme());
			return web && lien.getHost() != null ? lien : null;
		} catch (URISyntaxException mal) {
			return null;
		}
	}

	private JsonNode consulter(String chemin) {
		URI uri = racine().resolve(chemin);
		HttpRequest requete = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(proprietes.delaiDeezerMs()))
				.header("Accept", "application/json").GET().build();
		long debut = System.nanoTime();
		CompletableFuture<HttpResponse<byte[]>> consultation = client.sendAsync(requete,
				enTetes -> new ReceptionBornee(proprietes.tailleReponseMaxOctets(), enTetes.statusCode() != HTTP_OK));
		try {
			// Le délai couvre aussi le corps, même après réception des en-têtes.
			long restant = TimeUnit.MILLISECONDS.toNanos(proprietes.delaiDeezerMs()) - (System.nanoTime() - debut);
			HttpResponse<byte[]> reponse = consultation.get(Math.max(0, restant), TimeUnit.NANOSECONDS);
			if (reponse.statusCode() != HTTP_OK) {
				throw new SourceMusicaleIndisponible("Statut Deezer inattendu : " + reponse.statusCode());
			}
			String texte = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(reponse.body())).toString();
			JsonNode racine = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(texte);
			if (racine == null || !racine.isObject()) {
				throw new SourceMusicaleIndisponible("Réponse Deezer mal formée.");
			}
			if (racine.has("error")) {
				// Deezer signale ses erreurs (quota, donnée absente) dans un corps JSON, avec un statut 200.
				throw new SourceMusicaleIndisponible("Erreur Deezer : " + racine.path("error").path("code").asString(""));
			}
			return racine;
		} catch (TimeoutException erreur) {
			consultation.cancel(true);
			throw new SourceMusicaleIndisponible("Le délai de Deezer est dépassé.", erreur);
		} catch (ExecutionException erreur) {
			throw new SourceMusicaleIndisponible("Deezer est injoignable.", erreur);
		} catch (IOException | JacksonException erreur) {
			throw new SourceMusicaleIndisponible("Réponse Deezer mal formée.", erreur);
		} catch (InterruptedException erreur) {
			consultation.cancel(true);
			Thread.currentThread().interrupt();
			throw new SourceMusicaleIndisponible("Consultation de Deezer interrompue.", erreur);
		}
	}

	/** Racine terminée par « / », pour que les chemins relatifs s'y ajoutent au lieu de remplacer son dernier segment. */
	private URI racine() {
		String base = proprietes.urlDeezer().toString();
		return URI.create(base.endsWith("/") ? base : base + "/");
	}

	/** Réception bornée ; l'annulation ferme le flux sans le vider. */
	private static final class ReceptionBornee implements HttpResponse.BodySubscriber<byte[]> {

		private final long maximum;
		private final boolean ignorer;
		private final ByteArrayOutputStream octets = new ByteArrayOutputStream();
		private final CompletableFuture<byte[]> resultat = new CompletableFuture<>();
		private Flow.Subscription abonnement;
		private long recus;

		private ReceptionBornee(long maximum, boolean ignorer) {
			this.maximum = maximum;
			this.ignorer = ignorer;
		}

		@Override
		public CompletionStage<byte[]> getBody() {
			return resultat;
		}

		@Override
		public void onSubscribe(Flow.Subscription souscription) {
			abonnement = souscription;
			if (ignorer) {
				souscription.cancel();
				resultat.complete(new byte[0]);
			} else {
				souscription.request(1);
			}
		}

		@Override
		public void onNext(List<ByteBuffer> blocs) {
			for (ByteBuffer bloc : blocs) {
				int taille = bloc.remaining();
				if (taille > maximum - recus) {
					abonnement.cancel();
					resultat.completeExceptionally(
							new SourceMusicaleIndisponible("La taille maximale de réponse Deezer est dépassée."));
					return;
				}
				recus += taille;
				byte[] copie = new byte[taille];
				bloc.get(copie);
				octets.writeBytes(copie);
			}
			abonnement.request(1);
		}

		@Override
		public void onError(Throwable erreur) {
			resultat.completeExceptionally(erreur);
		}

		@Override
		public void onComplete() {
			resultat.complete(octets.toByteArray());
		}

	}

}
