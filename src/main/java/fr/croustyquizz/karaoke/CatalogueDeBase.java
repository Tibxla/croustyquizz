package fr.croustyquizz.karaoke;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Catalogue de la Bibliothèque de base (ADR 0011), lu et vérifié une fois au démarrage. Toute anomalie
 * lève {@link CatalogueDeBaseInvalide} : mieux vaut une application qui ne démarre pas qu'un crédit faux.
 */
public final class CatalogueDeBase {
    /** Licences qui permettent la diffusion dans un bar et l'adaptation en paroles synchronisées, avec leur lien officiel. */
    public static final Map<String, URI> LICENCES_ACCEPTEES = licencesAcceptees();
    /** Plafond de la bibliothèque livrée, décidé avec Tibxla. */
    static final long TAILLE_TOTALE_MAX_OCTETS = 40_000_000L;
    private static final long ENTIER_MAX = 9_007_199_254_740_991L;
    private static final Pattern NOM = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern FICHIER = Pattern.compile("(attente|instrumentales|chansons)/" + NOM + "\\.(mp3|m4a|ogg|wav)");
    private static final Pattern LRC = Pattern.compile("chansons/" + NOM + "\\.lrc");
    private static final String ALBUM = "album";
    private static final String URL = "url";
    private static final Set<String> CHAMPS_RACINE = Set.of("version", "licences", "entrees");
    private static final Set<String> CHAMPS_LICENCE = Set.of("nom", URL);
    private static final Set<String> CHAMPS_COMMUNS = Set.of("type", "titre", "artiste", ALBUM, "dureeMs", "licence",
            "source", "fichier", "tailleOctets");

    private final List<EntreeDeBibliotheque> entreesLues;
    private final Map<Long, ContenuKaraoke> contenus;
    private final Map<String, ChansonDeBibliotheque> parChansonId;

    private CatalogueDeBase(List<EntreeDeBibliotheque> morceaux, Map<String, String> nomsDeLicence,
            Map<Long, ContenuKaraoke> contenus) {
        this.entreesLues = List.copyOf(morceaux);
        this.contenus = new LinkedHashMap<>(contenus);
        Map<String, ChansonDeBibliotheque> index = new LinkedHashMap<>();
        for (EntreeDeBibliotheque morceau : morceaux) {
            if (morceau.type() != TypeDEntree.CHANSON) { continue; }
            index.put(IdentifiantsChanson.encoderBibliotheque(morceau.numero()), new ChansonDeBibliotheque(morceau.url(),
                    morceau.tailleOctets(), nomsDeLicence.get(morceau.licence()), LICENCES_ACCEPTEES.get(morceau.licence()),
                    morceau.source()));
        }
        this.parChansonId = Map.copyOf(index);
    }

    public List<EntreeDeBibliotheque> entrees() { return entreesLues; }

    /** Chansons du catalogue avec leurs paroles, dans l'ordre du catalogue. */
    public List<ContenuKaraoke> chansons() { return List.copyOf(contenus.values()); }

    public Optional<ContenuKaraoke> contenu(long numero) { return Optional.ofNullable(contenus.get(numero)); }

    /** Fichier et crédit d'une Chanson du catalogue ; vide pour LRCLIB ou un numéro disparu. */
    public Optional<ChansonDeBibliotheque> bibliotheque(String chansonId) { return Optional.ofNullable(parChansonId.get(chansonId)); }

    public static CatalogueDeBase charger(ResourceLoader chargeur, String racine, JsonMapper json) {
        String base = racine.endsWith("/") ? racine : racine + "/";
        JsonNode catalogue;
        try {
            catalogue = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(lireTexte(chargeur.getResource(base + "catalogue.json"), "catalogue.json"));
        } catch (JacksonException erreur) {
            throw new CatalogueDeBaseInvalide("catalogue illisible : " + base + "catalogue.json", erreur);
        }
        return new Lecture(chargeur, base).lire(catalogue);
    }

    /** Lecture d'un catalogue, entrée par entrée, avec des messages qui nomment l'entrée fautive. */
    private static final class Lecture {
        private final ResourceLoader chargeur;
        private final String base;
        private final Set<String> fichiers = new HashSet<>();
        private final Map<Long, ContenuKaraoke> contenus = new LinkedHashMap<>();
        private long tailleTotale;

        Lecture(ResourceLoader chargeur, String base) { this.chargeur = chargeur; this.base = base; }

        CatalogueDeBase lire(JsonNode catalogue) {
            if (!catalogue.isObject()) { throw new CatalogueDeBaseInvalide("catalogue : objet attendu"); }
            verifierChamps("catalogue", catalogue, CHAMPS_RACINE, CHAMPS_RACINE);
            if (!catalogue.path("version").isIntegralNumber() || catalogue.path("version").asLong() != 1) {
                throw new CatalogueDeBaseInvalide("catalogue : version 1 attendue");
            }
            Map<String, String> noms = licences(catalogue.path("licences"));
            JsonNode lues = catalogue.path("entrees");
            if (!lues.isArray()) { throw new CatalogueDeBaseInvalide("catalogue : tableau entrees attendu"); }
            List<EntreeDeBibliotheque> morceaux = new ArrayList<>();
            int rang = 0;
            for (JsonNode entree : lues) {
                rang++;
                String contexte = "entrée " + rang;
                try { morceaux.add(entree(entree, noms, contexte)); }
                catch (IllegalArgumentException erreur) {
                    throw new CatalogueDeBaseInvalide(contexte + " : " + erreur.getMessage(), erreur);
                }
            }
            if (tailleTotale > TAILLE_TOTALE_MAX_OCTETS) {
                throw new CatalogueDeBaseInvalide("catalogue : taille totale de " + tailleTotale + " octets au-delà de 40 Mo");
            }
            return new CatalogueDeBase(morceaux, noms, contenus);
        }

        private static Map<String, String> licences(JsonNode licences) {
            if (!licences.isObject()) { throw new CatalogueDeBaseInvalide("catalogue : objet licences attendu"); }
            Map<String, String> noms = new LinkedHashMap<>();
            for (var licence : licences.properties()) {
                String code = licence.getKey();
                URI officielle = LICENCES_ACCEPTEES.get(code);
                String nommee = "licence « " + code + " »";
                if (officielle == null) { throw new CatalogueDeBaseInvalide(nommee + " refusée"); }
                JsonNode fiche = licence.getValue();
                if (!fiche.isObject()) { throw new CatalogueDeBaseInvalide(nommee + " : objet attendu"); }
                verifierChamps(nommee, fiche, CHAMPS_LICENCE, CHAMPS_LICENCE);
                if (!fiche.path(URL).isString() || !officielle.toString().equals(fiche.path(URL).asString())) {
                    throw new CatalogueDeBaseInvalide(nommee + " : lien officiel attendu, " + officielle);
                }
                noms.put(code, texteRequis(fiche, "nom"));
            }
            return noms;
        }

        private EntreeDeBibliotheque entree(JsonNode entree, Map<String, String> licences, String contexte) {
            if (!entree.isObject()) { throw new IllegalArgumentException("objet attendu"); }
            TypeDEntree type = type(entree.path("type"));
            Set<String> permis = new HashSet<>(CHAMPS_COMMUNS);
            Set<String> requis = new HashSet<>(permis);
            requis.remove(ALBUM);
            if (type == TypeDEntree.CHANSON) { permis.addAll(Set.of("numero", "lrc")); requis.addAll(Set.of("numero", "lrc")); }
            if (type == TypeDEntree.INSTRUMENTALE) {
                permis.addAll(Set.of("tempoBpm", "departBeatMs"));
                requis.addAll(Set.of("tempoBpm", "departBeatMs"));
            }
            verifierChamps(contexte, entree, permis, requis);
            String titre = texteRequis(entree, "titre");
            String artiste = texteRequis(entree, "artiste");
            JsonNode champAlbum = entree.path(ALBUM);
            String album = champAlbum.isNull() || champAlbum.isMissingNode() ? null : texteRequis(entree, ALBUM);
            long dureeMs = entier(entree, "dureeMs", 1, ENTIER_MAX);
            String licence = texteRequis(entree, "licence");
            if (!licences.containsKey(licence)) { throw new IllegalArgumentException("licence « " + licence + " » non déclarée"); }
            URI source = source(texteRequis(entree, "source"));
            String fichier = texteRequis(entree, "fichier");
            if (!FICHIER.matcher(fichier).matches() || !fichier.startsWith(type.dossier())) {
                throw new IllegalArgumentException("fichier « " + fichier + " » invalide ou hors du dossier " + type.dossier());
            }
            if (!fichiers.add(fichier)) { throw new IllegalArgumentException("fichier « " + fichier + " » en double"); }
            long taille = entier(entree, "tailleOctets", 1, ENTIER_MAX);
            verifierTaille(fichier, taille);
            tailleTotale += taille;
            Long numero = null;
            String lrc = null;
            Integer tempo = null;
            Long depart = null;
            if (type == TypeDEntree.CHANSON) {
                numero = entier(entree, "numero", 1, ENTIER_MAX);
                lrc = texteRequis(entree, "lrc");
                if (!LRC.matcher(lrc).matches()) { throw new IllegalArgumentException("lrc « " + lrc + " » invalide"); }
                var lignes = ParolesLrc.lireStrictement(lireTexte(ressource(lrc, "LRC"), lrc), dureeMs);
                var chanson = new Chanson(IdentifiantsChanson.encoderBibliotheque(numero), numero, titre, artiste, album,
                        dureeMs, EtatParoles.SYNCHRONISEES);
                if (contenus.putIfAbsent(numero, new ContenuKaraoke(chanson, lignes)) != null) {
                    throw new IllegalArgumentException("numero " + numero + " en double");
                }
            }
            if (type == TypeDEntree.INSTRUMENTALE) {
                tempo = (int) entier(entree, "tempoBpm", 40, 300);
                depart = entier(entree, "departBeatMs", 0, dureeMs - 1);
            }
            return new EntreeDeBibliotheque(type, titre, artiste, album, dureeMs, licence, source, fichier, taille,
                    numero, lrc, tempo, depart);
        }

        private Resource ressource(String chemin, String nature) {
            Resource ressource = chargeur.getResource(base + chemin);
            if (!ressource.exists()) { throw new IllegalArgumentException(nature + " absent : " + chemin); }
            return ressource;
        }

        private void verifierTaille(String fichier, long taille) {
            try {
                long reelle = ressource(fichier, "fichier").contentLength();
                if (reelle != taille) {
                    throw new IllegalArgumentException("taille de « " + fichier + " » : " + reelle + " octets, " + taille + " déclarés");
                }
            } catch (IOException erreur) {
                throw new IllegalArgumentException("fichier illisible : " + fichier, erreur);
            }
        }

        private static TypeDEntree type(JsonNode champ) {
            for (TypeDEntree type : TypeDEntree.values()) {
                if (champ.isString() && type.nom().equals(champ.asString())) { return type; }
            }
            throw new IllegalArgumentException("type « " + (champ.isString() ? champ.asString() : champ.toString()) + " » inconnu");
        }

        private static URI source(String texte) {
            try {
                URI uri = new URI(texte);
                if (!uri.isAbsolute() || uri.getHost() == null
                        || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                    throw new IllegalArgumentException("source : URL HTTP absolue attendue");
                }
                return uri;
            } catch (URISyntaxException erreur) {
                throw new IllegalArgumentException("source : URL HTTP absolue attendue", erreur);
            }
        }
    }

    private static void verifierChamps(String contexte, JsonNode objet, Set<String> permis, Set<String> requis) {
        for (String nom : objet.propertyNames()) {
            if (!permis.contains(nom)) { throw new CatalogueDeBaseInvalide(contexte + " : champ inconnu « " + nom + " »"); }
        }
        var manquants = requis.stream().filter(nom -> objet.path(nom).isMissingNode()).sorted().toList();
        if (!manquants.isEmpty()) {
            throw new CatalogueDeBaseInvalide(contexte + " : champs requis manquants : " + String.join(", ", manquants));
        }
    }

    private static String texteRequis(JsonNode objet, String champ) {
        JsonNode valeur = objet.path(champ);
        if (!valeur.isString() || valeur.asString().isBlank()) { throw new IllegalArgumentException(champ + " : texte requis"); }
        return valeur.asString().trim();
    }

    private static long entier(JsonNode objet, String champ, long minimum, long maximum) {
        JsonNode valeur = objet.path(champ);
        if (!valeur.isIntegralNumber() || !valeur.canConvertToLong() || valeur.asLong() < minimum || valeur.asLong() > maximum) {
            throw new IllegalArgumentException(champ + " : entier de " + minimum + " à " + maximum + " requis");
        }
        return valeur.asLong();
    }

    private static String lireTexte(Resource ressource, String chemin) {
        if (!ressource.exists()) { throw new CatalogueDeBaseInvalide("catalogue absent : " + chemin); }
        try (InputStream flux = ressource.getInputStream()) {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(flux.readAllBytes())).toString();
        } catch (CharacterCodingException erreur) {
            throw new CatalogueDeBaseInvalide("UTF-8 attendu : " + chemin, erreur);
        } catch (IOException erreur) {
            throw new CatalogueDeBaseInvalide("lecture impossible : " + chemin, erreur);
        }
    }

    private static Map<String, URI> licencesAcceptees() {
        Map<String, URI> licences = new LinkedHashMap<>();
        licences.put("CC0-1.0", URI.create("https://creativecommons.org/publicdomain/zero/1.0/deed.fr"));
        licences.put("domaine-public", URI.create("https://creativecommons.org/publicdomain/mark/1.0/deed.fr"));
        for (String version : List.of("2.0", "2.5", "3.0", "4.0")) {
            licences.put("CC-BY-" + version, URI.create("https://creativecommons.org/licenses/by/" + version + "/deed.fr"));
            licences.put("CC-BY-SA-" + version, URI.create("https://creativecommons.org/licenses/by-sa/" + version + "/deed.fr"));
        }
        licences.put("LAL-1.3", URI.create("https://artlibre.org/licence/lal/"));
        return Map.copyOf(licences);
    }
}
