package fr.croustyquizz.karaoke;

import static java.net.HttpURLConnection.HTTP_OK;

import fr.croustyquizz.manche.Horloge;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

/** Adaptation du protocole LRCLIB aux records du Karaoké. */
public final class AdaptateurLrclib implements SourceDeParoles {
    private final ProprietesSourceKaraoke proprietes;
    private final HttpClient client;
    private final JsonMapper json;
    private final Horloge horloge;
    private Instant pasAvant = Instant.MIN;

    public AdaptateurLrclib(ProprietesSourceKaraoke proprietes, HttpClient client, JsonMapper json, Horloge horloge) {
        if (client.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("La source de paroles ne suit aucune redirection");
        }
        this.proprietes = proprietes;
        this.client = client;
        this.json = json;
        this.horloge = horloge;
    }

    @Override
    public List<Chanson> rechercher(String recherche) {
        JsonNode racine = consulter("/api/search?q=" + URLEncoder.encode(recherche, StandardCharsets.UTF_8));
        if (racine == null || !racine.isArray()) { throw new SourceParolesIndisponible("Tableau de Chansons attendu"); }
        List<Chanson> chansons = new ArrayList<>();
        for (JsonNode candidat : racine) {
            try { chansons.add(convertir(candidat).chanson()); }
            catch (IllegalArgumentException erreur) { continue; }
        }
        return List.copyOf(chansons);
    }

    @Override
    public Optional<ContenuKaraoke> charger(long idLrclib) {
        JsonNode racine = consulter("/api/get/" + idLrclib);
        if (racine == null) { return Optional.empty(); }
        try {
            ContenuKaraoke contenu = convertir(racine);
            if (contenu.chanson().idLrclib() != idLrclib) { throw new IllegalArgumentException("Identité de Chanson incohérente"); }
            return Optional.of(contenu);
        } catch (IllegalArgumentException erreur) {
            throw new SourceParolesIndisponible("Détail de Chanson mal formé", erreur);
        }
    }

    private JsonNode consulter(String chemin) {
        verifierAttente();
        URI uri = proprietes.urlLrclib().resolve(chemin);
        HttpRequest requete = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(proprietes.delaiSourceMs()))
                .header("User-Agent", proprietes.userAgentLrclib()).GET().build();
        long debutTransport = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> consultation = client.sendAsync(requete, enTetes -> {
            if (enTetes.statusCode() == 429 || enTetes.statusCode() == 503) {
                enTetes.headers().firstValue("Retry-After").ifPresent(this::memoriserAttente);
            }
            return new ReceptionBornee(proprietes.tailleReponseSourceMaxOctets(), enTetes.statusCode() != HTTP_OK);
        });
        try {
            // Le budget réel du transport inclut le corps, même après réception des en-têtes.
            long restant = TimeUnit.MILLISECONDS.toNanos(proprietes.delaiSourceMs()) - (System.nanoTime() - debutTransport);
            HttpResponse<byte[]> reponse = consultation.get(Math.max(0, restant), TimeUnit.NANOSECONDS);
            if (reponse.statusCode() == 404 && chemin.startsWith("/api/get/")) { return null; }
            if (reponse.statusCode() != HTTP_OK) { throw new SourceParolesIndisponible("Statut source : " + reponse.statusCode()); }
            String texte = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(reponse.body())).toString();
            return json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(texte);
        } catch (TimeoutException erreur) {
            consultation.cancel(true);
            throw new SourceParolesIndisponible("Le délai de la source de paroles est dépassé", erreur);
        } catch (ExecutionException erreur) {
            throw new SourceParolesIndisponible("Erreur de transport ou délai de la source de paroles", erreur);
        } catch (IOException | JacksonException erreur) {
            throw new SourceParolesIndisponible("Réponse de la source de paroles mal formée", erreur);
        } catch (InterruptedException erreur) {
            consultation.cancel(true);
            Thread.currentThread().interrupt();
            throw new SourceParolesIndisponible("Consultation interrompue", erreur);
        }
    }

    private synchronized void verifierAttente() {
        if (pasAvant.isAfter(horloge.maintenant())) {
            throw new SourceParolesIndisponible("La source de paroles impose une attente");
        }
    }

    private synchronized void memoriserAttente(String entete) {
        try {
            Instant maintenant = horloge.maintenant();
            Instant echeance = entete.matches("[0-9]+")
                    ? maintenant.plusSeconds(Long.parseLong(entete))
                    : ZonedDateTime.parse(entete, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            if (echeance.isAfter(maintenant) && echeance.isAfter(pasAvant)) { pasAvant = echeance; }
        } catch (DateTimeException | ArithmeticException | NumberFormatException erreur) {
            // Un Retry-After invalide ne crée ni ne raccourcit une attente.
            return;
        }
    }

    /** Réception bornée avant copie ; l'annulation ferme le flux sans le vider. */
    private static final class ReceptionBornee implements HttpResponse.BodySubscriber<byte[]> {
        private final long maximum;
        private final boolean ignorer;
        private final ByteArrayOutputStream octets = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> resultat = new CompletableFuture<>();
        private Flow.Subscription abonnement;
        private long recus;

        private ReceptionBornee(long maximum, boolean ignorer) { this.maximum = maximum; this.ignorer = ignorer; }
        @Override public CompletionStage<byte[]> getBody() { return resultat; }
        @Override public void onSubscribe(Flow.Subscription souscription) {
            abonnement = souscription;
            if (ignorer) {
                souscription.cancel();
                resultat.complete(new byte[0]);
            } else { souscription.request(1); }
        }
        @Override public void onNext(List<ByteBuffer> blocs) {
            for (ByteBuffer bloc : blocs) {
                int taille = bloc.remaining();
                if (taille > maximum - recus) {
                    abonnement.cancel();
                    resultat.completeExceptionally(new SourceParolesIndisponible("La taille maximale de réponse est dépassée"));
                    return;
                }
                recus += taille;
                byte[] copie = new byte[taille];
                bloc.get(copie);
                octets.writeBytes(copie);
            }
            abonnement.request(1);
        }
        @Override public void onError(Throwable erreur) { resultat.completeExceptionally(erreur); }
        @Override public void onComplete() { resultat.complete(octets.toByteArray()); }
    }

    private ContenuKaraoke convertir(JsonNode donnee) {
        if (!donnee.isObject() || !donnee.path("id").isIntegralNumber()
                || !donnee.path("id").canConvertToLong()) { throw new IllegalArgumentException("Identité de Chanson invalide"); }
        long id = donnee.path("id").asLong();
        verifierType(donnee.path("duration"), "nombre");
        verifierType(donnee.path("instrumental"), "booléen");
        Long duree = duree(donnee.path("duration"));
        String synchronisees = texte(donnee.path("syncedLyrics"));
        String simples = texte(donnee.path("plainLyrics"));
        List<LigneParoles> lignes = ParolesLrc.lire(synchronisees, duree);
        JsonNode instrumental = donnee.path("instrumental");
        EtatParoles etat = etatParoles(instrumental, synchronisees, simples, lignes);
        if (texte(donnee.path("trackName")) == null || texte(donnee.path("artistName")) == null) {
            throw new IllegalArgumentException("Titre et artiste requis");
        }
        Chanson chanson = new Chanson(IdentifiantsChanson.encoder(id), id,
                texte(donnee.path("trackName")), texte(donnee.path("artistName")),
                texte(donnee.path("albumName")), duree, etat);
        return new ContenuKaraoke(chanson, lignes);
    }

    private static String texte(JsonNode champ) {
        if (champ.isMissingNode() || champ.isNull()) { return null; }
        verifierType(champ, "texte");
        String valeur = champ.asString().trim();
        return valeur.isEmpty() ? null : valeur;
    }

    private static void verifierType(JsonNode champ, String type) {
        if (champ.isMissingNode() || champ.isNull()) { return; }
        boolean valide = switch (type) {
            case "nombre" -> champ.isNumber();
            case "booléen" -> champ.isBoolean();
            default -> champ.isString();
        };
        if (!valide) { throw new IllegalArgumentException("Champ de type " + type + " attendu"); }
    }

    private static Long duree(JsonNode champ) {
        if (!champ.isNumber()) { return null; }
        double millisecondes = champ.asDouble() * 1000;
        if (!Double.isFinite(millisecondes) || millisecondes <= 0 || millisecondes >= 0x1p63) { return null; }
        long arrondie = Math.round(millisecondes);
        return arrondie > 0 ? arrondie : null;
    }

    private static EtatParoles etatParoles(JsonNode instrumental, String synchronisees,
            String simples, List<LigneParoles> lignes) {
        if (instrumental.isMissingNode() || instrumental.isNull()) { return EtatParoles.INCONNU; }
        if (instrumental.asBoolean()) { return EtatParoles.SANS_VOIX; }
        if (!lignes.isEmpty()) { return EtatParoles.SYNCHRONISEES; }
        if (synchronisees != null) { return EtatParoles.INCONNU; }
        return simples == null ? EtatParoles.ABSENTES : EtatParoles.NON_SYNCHRONISEES;
    }
}
