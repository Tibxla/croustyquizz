package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.PortsDePassageFactices;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** HTTP, contrats et PostgreSQL réels. Nécessite Docker ; source exclusivement factice. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, ConfigurationTroisJoueurs.class})
class KaraokeHttpTest {
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired ServiceKaraoke service;
    @Autowired PortsDePassageFactices ports;
    @Autowired DepotDeManches depot;
    @Autowired PilotageDeSoireeDemonstration pilotage;
    @MockitoSpyBean PersistanceKaraokeJpa persistance;
    final UUID soiree = UUID.randomUUID();
    final Acteur animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
    final Acteur joueur = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.getFirst(), soiree);

    @Test void lesHuitOperationsEtLeRejeuApresRetraitEtFinRespectentLeContrat() throws Exception {
        UUID manche = demarrer(); String racine = racine(manche);
        verifier("rechercherChansons", envoyer(HttpMethod.GET, racine + "/chansons?recherche=Chanson&rechercheId=" + UUID.randomUUID(), joueur, null, null), 200);
        verifier("consulterChanson", envoyer(HttpMethod.GET, racine + "/chansons/" + IdentifiantsChanson.encoder(1), joueur, null, null), 200);
        UUID id = UUID.randomUUID();
        var corps = Map.of("envoiId", id, "chansonId", IdentifiantsChanson.encoder(1));
        var inscription = envoyer(HttpMethod.POST, racine + "/passages", joueur, corps, null);
        verifier("inscrirePassage", inscription, 201);
        UUID passage = UUID.fromString(json.readTree(inscription.getBody()).path("passageId").asString());
        verifier("deplacerPassage", envoyer(HttpMethod.PUT, racine + "/passages/" + passage + "/placement", animateur,
                "{\"numeroModification\":0,\"tour\":1,\"avant\":null}", UUID.randomUUID()), 200);
        verifier("definirPrioritePassage", envoyer(HttpMethod.PUT, racine + "/file-attente/priorite", animateur,
                Map.of("passage", Map.of("passageId", passage, "numeroModification", 0), "prioritaire", true), UUID.randomUUID()), 200);
        verifier("reglerLimitePassages", envoyer(HttpMethod.PUT, racine + "/file-attente/limite", animateur,
                Map.of("limitePassagesEnAttente", 3), UUID.randomUUID()), 200);
        verifier("retirerPassage", envoyer(HttpMethod.POST, racine + "/passages/" + passage + "/retrait", animateur,
                Map.of("numeroModification", 1), UUID.randomUUID()), 200);
        var repete = envoyer(HttpMethod.POST, racine + "/passages", joueur, corps, null);
        verifier("inscrirePassage", repete, 201); assertThat(json.readTree(repete.getBody())).isEqualTo(json.readTree(inscription.getBody()));
        var seconde = envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(2)), null);
        verifier("inscrirePassage", seconde, 201);
        UUID autre = UUID.fromString(json.readTree(seconde.getBody()).path("passageId").asString());
        verifier("desinscrirePassage", envoyer(HttpMethod.POST, racine + "/passages/" + autre + "/desinscription", joueur,
                Map.of("envoiId", UUID.randomUUID(), "numeroModification", 0), null), 200);
        verifier("piloterManche", envoyer(HttpMethod.POST, racine + "/commandes", animateur, Map.of("action", "TERMINER"), UUID.randomUUID()), 200);
        var apresFin = envoyer(HttpMethod.POST, racine + "/passages", joueur, corps, null);
        verifier("inscrirePassage", apresFin, 201); assertThat(json.readTree(apresFin.getBody())).isEqualTo(json.readTree(inscription.getBody()));
        verifier("consulterChanson", envoyer(HttpMethod.GET, racine + "/chansons/" + IdentifiantsChanson.encoder(1), joueur, null, null), 409);
    }

    @Test void lesSchemasFermesEtEntiersStrictsPrecedentLIdentite() throws Exception {
        UUID manche = demarrer(); String racine = racine(manche);
        for (String corps : List.of("{}", "{\"envoiId\":null,\"chansonId\":\"x\"}",
                "{\"envoiId\":\"" + UUID.randomUUID() + "\",\"chansonId\":\"x\",\"acteur\":\"faux\"}")) {
            verifier("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", null, corps, null), 400);
        }
        for (String valeur : List.of("null", "-1", "1.0", "\"2\"", "9007199254740992")) {
            verifier("retirerPassage", envoyer(HttpMethod.POST, racine + "/passages/" + UUID.randomUUID() + "/retrait", null,
                    "{\"numeroModification\":" + valeur + "}", UUID.randomUUID()), 400);
        }
        verifier("deplacerPassage", envoyer(HttpMethod.PUT, racine + "/passages/" + UUID.randomUUID() + "/placement", null,
                "{\"numeroModification\":0,\"tour\":1}", UUID.randomUUID()), 400);
        verifier("definirPrioritePassage", envoyer(HttpMethod.PUT, racine + "/file-attente/priorite", null,
                Map.of("passage", Map.of("passageId", UUID.randomUUID(), "numeroModification", 0), "prioritaire", "true"), UUID.randomUUID()), 400);
        // Bornes de #96 : limite dans 1..20, chansonId de 1 à 128 caractères.
        for (long limite : List.of(0L, 21L)) {
            verifier("reglerLimitePassages", envoyer(HttpMethod.PUT, racine + "/file-attente/limite", null,
                    Map.of("limitePassagesEnAttente", limite), UUID.randomUUID()), 400);
        }
        verifier("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", null,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", "x".repeat(129)), null), 400);
        verifier("desinscrirePassage", envoyer(HttpMethod.POST, racine + "/passages/" + UUID.randomUUID() + "/desinscription", null,
                Map.of("envoiId", UUID.randomUUID(), "numeroModification", "0"), null), 400);
    }

    @Test void lesBornesDeLaLimiteEtDeChansonIdSuiventLeContrat() throws Exception {
        UUID manche = demarrer(); String racine = racine(manche);
        verifier("reglerLimitePassages", envoyer(HttpMethod.PUT, racine + "/file-attente/limite", animateur,
                Map.of("limitePassagesEnAttente", 20), UUID.randomUUID()), 200);
        verifier("reglerLimitePassages", envoyer(HttpMethod.PUT, racine + "/file-attente/limite", animateur,
                Map.of("limitePassagesEnAttente", 21), UUID.randomUUID()), 400);
        refuser("consulterChanson", envoyer(HttpMethod.GET, racine + "/chansons/" + "x".repeat(129), joueur, null, null), 400, "REQUETE_INVALIDE");
        refuser("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", "x".repeat(129)), null), 400, "REQUETE_INVALIDE");
        // 128 caractères passent la validation et vont jusqu'au métier : identifiant inconnu.
        refuser("consulterChanson", envoyer(HttpMethod.GET, racine + "/chansons/" + "x".repeat(128), joueur, null, null), 404, "RESSOURCE_INTROUVABLE");
        refuser("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", "x".repeat(128)), null), 404, "RESSOURCE_INTROUVABLE");
    }

    @Test void creationValideLeDefautEtRefuseLesReglagesNonObjetsOuNonEntiers() throws Exception {
        var creation = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches", animateur,
                Map.of("mode", "KARAOKE", "reglages", Map.of()), null);
        verifierPrototype("creerMancheDeTest", creation, 201);
        UUID manche = UUID.fromString(json.readTree(creation.getBody()).path("mancheId").asString());
        assertThat(json.readTree(depot.charger(manche).getReglages()).path("limitePassagesEnAttente").asLong()).isEqualTo(1);
        var maximum = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches", animateur,
                Map.of("mode", "KARAOKE", "reglages", Map.of("limitePassagesEnAttente", 20)), null);
        verifierPrototype("creerMancheDeTest", maximum, 201);
        for (String reglages : List.of("[]", "\"texte\"", "{\"inconnu\":1}", "{\"limitePassagesEnAttente\":null}",
                "{\"limitePassagesEnAttente\":1.0}", "{\"limitePassagesEnAttente\":\"2\"}", "{\"limitePassagesEnAttente\":0}",
                "{\"limitePassagesEnAttente\":21}", "{\"limitePassagesEnAttente\":9007199254740992}")) {
            verifierPrototype("creerMancheDeTest", envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches", animateur,
                    "{\"mode\":\"KARAOKE\",\"reglages\":" + reglages + "}", null), 400);
        }
    }

    @Test void rolesPorteePresenceEtAttenteDAutruiSontRefusesSansRecu() throws Exception {
        UUID manche = demarrer(); String racine = racine(manche);
        var inscription = Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1));
        verifier("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", null, inscription, null), 401);
        verifier("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", animateur, inscription, null), 403);
        verifier("reglerLimitePassages", envoyer(HttpMethod.PUT, racine + "/file-attente/limite", joueur, Map.of("limitePassagesEnAttente", 2), UUID.randomUUID()), 403);
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        verifier("consulterChanson", envoyer(HttpMethod.GET, racine + "/chansons/x", ecran, null, null), 403);
        verifier("rechercherChansons", envoyer(HttpMethod.GET, racine + "/chansons?recherche=x&rechercheId=" + UUID.randomUUID(), ecran, null, null), 403);
        var autreSoiree = new Acteur(Acteur.Type.JOUEUR, joueur.identifiant(), UUID.randomUUID());
        verifier("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", autreSoiree, inscription, null), 404);
        var acceptee = envoyer(HttpMethod.POST, racine + "/passages", joueur, inscription, null);
        verifier("inscrirePassage", acceptee, 201);
        var autreJoueur = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.get(1), soiree);
        String passage = json.readTree(acceptee.getBody()).path("passageId").asString();
        verifier("desinscrirePassage", envoyer(HttpMethod.POST, racine + "/passages/" + passage + "/desinscription", autreJoueur,
                Map.of("envoiId", UUID.randomUUID(), "numeroModification", 0), null), 404);
        ports.absents.add(joueur.identifiant());
        try {
            verifier("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur, inscription, null), 404);
        } finally { ports.absents.remove(joueur.identifiant()); }
        ports.interdites.add(manche);
        try { verifier("consulterChanson", envoyer(HttpMethod.GET, racine + "/chansons/x", joueur, null, null), 404); }
        finally { ports.interdites.remove(manche); }
        UUID autreMode = UUID.randomUUID();
        new org.springframework.transaction.support.TransactionTemplate(serviceSocle().transactions()).executeWithoutResult(s ->
                depot.enregistrer(new ManchePersistante(autreMode, soiree, ModeDeJeu.QUIZ, "{}", serviceSocle().horloge().maintenant())));
        verifier("inscrirePassage", envoyer(HttpMethod.POST, racine(autreMode) + "/passages", joueur, inscription, null), 404);
    }

    @Test void lesRefusMetierEtDeSourceGardentLeurStatutEtLeurCode() throws Exception {
        UUID manche = service.creer(soiree, Map.of()); String racine = racine(manche);
        var premiere = Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1));
        refuser("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur, premiere, null), 409, "ETAPE_INACTIVE");
        verifierPrototype("demarrerMancheDeTest", envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches/" + manche + "/demarrer",
                animateur, null, UUID.randomUUID()), 200);
        for (long id : List.of(404L, 422L, 503L)) {
            int statut = (int) id;
            String code = id == 404 ? "RESSOURCE_INTROUVABLE" : id == 422 ? "CHANSON_INDISPONIBLE" : "SOURCE_PAROLES_INDISPONIBLE";
            refuser("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur,
                    Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(id)), null), statut, code);
        }
        UUID inconnuId = UUID.randomUUID();
        var inconnue = Map.of("envoiId", inconnuId, "chansonId", "indécodable");
        refuser("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur, inconnue, null), 404, "RESSOURCE_INTROUVABLE");
        refuser("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", inconnuId, "chansonId", IdentifiantsChanson.encoder(1)), null), 409, "ENVOI_REUTILISE");
        refuser("consulterChanson", envoyer(HttpMethod.GET, racine + "/chansons/" + IdentifiantsChanson.encoder(503), joueur, null, null), 503, "SOURCE_PAROLES_INDISPONIBLE");
        refuser("consulterChanson", envoyer(HttpMethod.GET, racine + "/chansons/x", joueur, null, null), 404, "RESSOURCE_INTROUVABLE");
        refuser("rechercherChansons", envoyer(HttpMethod.GET, racine + "/chansons?recherche=---&rechercheId=" + UUID.randomUUID(), joueur, null, null), 400, "REQUETE_INVALIDE");
        var accepte = envoyer(HttpMethod.POST, racine + "/passages", joueur, premiere, null); verifier("inscrirePassage", accepte, 201);
        UUID passage = UUID.fromString(json.readTree(accepte.getBody()).path("passageId").asString());
        refuser("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null), 409, "PASSAGE_DEJA_INSCRIT");
        refuser("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(2)), null), 409, "LIMITE_ATTEINTE");
        refuser("deplacerPassage", envoyer(HttpMethod.PUT, racine + "/passages/" + passage + "/placement", animateur,
                Map.of("numeroModification", 0, "tour", 1, "avant", Map.of("passageId", passage, "numeroModification", 0)), UUID.randomUUID()), 422, "DONNEES_INVALIDES");
        refuser("deplacerPassage", envoyer(HttpMethod.PUT, racine + "/passages/" + passage + "/placement", animateur,
                "{\"numeroModification\":0,\"tour\":2,\"avant\":null}", UUID.randomUUID()), 409, "ROTATION_PROTEGEE");
        refuser("definirPrioritePassage", envoyer(HttpMethod.PUT, racine + "/file-attente/priorite", animateur,
                Map.of("passage", Map.of("passageId", passage, "numeroModification", 1), "prioritaire", true), UUID.randomUUID()), 409, "COMMANDE_PERIMEE");
        refuser("retirerPassage", envoyer(HttpMethod.POST, racine + "/passages/" + UUID.randomUUID() + "/retrait", animateur,
                Map.of("numeroModification", 0), UUID.randomUUID()), 404, "RESSOURCE_INTROUVABLE");
        refuser("desinscrirePassage", envoyer(HttpMethod.POST, racine + "/passages/" + passage + "/desinscription", joueur,
                Map.of("envoiId", UUID.randomUUID(), "numeroModification", 1), null), 409, "COMMANDE_PERIMEE");
        ports.inactives.add(manche);
        try {
            refuser("reglerLimitePassages", envoyer(HttpMethod.PUT, racine + "/file-attente/limite", animateur,
                    Map.of("limitePassagesEnAttente", 2), UUID.randomUUID()), 409, "ETAPE_INACTIVE");
        } finally { ports.inactives.remove(manche); }
        verifier("piloterManche", envoyer(HttpMethod.POST, racine + "/commandes", animateur, Map.of("action", "TERMINER"), UUID.randomUUID()), 200);
        refuser("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(503)), null), 409, "ETAT_INCOMPATIBLE");
    }
    @Test void lePassageDemarreSurConfirmationEtLInterpretationEstVerifieeAvantLeRecu() throws Exception {
        UUID manche = demarrer();
        String racine = racine(manche);
        var inscription = envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        verifier("inscrirePassage", inscription, 201);
        String route = racine + "/passages/" + json.readTree(inscription.getBody()).path("passageId").asString();
        var appel = envoyer(HttpMethod.POST, route + "/appel", animateur, Map.of("numeroModification", 0), UUID.randomUUID());
        verifier("appelerPassage", appel, 200);
        verifierRestauration(manche, EtatManche.EN_COURS);
        String etape = json.readTree(appel.getBody()).path("etapeId").asString();
        UUID envoiId = UUID.randomUUID();
        var corps = Map.of("envoiId", envoiId, "etapeId", etape, "numeroModification", 1);
        var autre = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.get(1), soiree);
        refuser("confirmerPresencePassage", envoyer(HttpMethod.POST, route + "/presence", autre, corps, null),
                404, "RESSOURCE_INTROUVABLE");
        assertThat(depot.retrouverEnvoi(EnvoiDeManche.cle(autre, route + "/presence", envoiId))).isNull();
        var confirme = envoyer(HttpMethod.POST, route + "/presence", joueur, corps, null);
        verifier("confirmerPresencePassage", confirme, 200);
        assertThat(json.readTree(confirme.getBody()).path("etatApres").asString()).isEqualTo("EN_COURS");
        verifierRestauration(manche, EtatManche.EN_COURS);
        var rejeu = envoyer(HttpMethod.POST, route + "/presence", joueur, corps, null);
        verifier("confirmerPresencePassage", rejeu, 200);
        assertThat(json.readTree(rejeu.getBody())).isEqualTo(json.readTree(confirme.getBody()));
        verifier("piloterManche", envoyer(HttpMethod.POST, racine + "/commandes", animateur,
                Map.of("action", "TERMINER", "etapeId", etape), UUID.randomUUID()), 200);
        refuser("confirmerPresencePassage", envoyer(HttpMethod.POST, route + "/presence", autre, corps, null),
                404, "RESSOURCE_INTROUVABLE");
        assertThat(depot.retrouverEnvoi(EnvoiDeManche.cle(autre, route + "/presence", envoiId))).isNull();
        var apresFin = envoyer(HttpMethod.POST, route + "/presence", joueur, corps, null);
        verifier("confirmerPresencePassage", apresFin, 200);
        assertThat(json.readTree(apresFin.getBody())).isEqualTo(json.readTree(confirme.getBody()));
    }

    @Test void leLecteurCaleSignaleUneErreurEtLaConsoleReprendEnParolesSeules() throws Exception {
        UUID manche = demarrer();
        String racine = racine(manche);
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        UUID lecteur = UUID.randomUUID();
        var designation = Map.of("lecteurId", lecteur);
        refuser("designerLecteurChanson", envoyer(HttpMethod.PUT, racine + "/lecteur", null, designation, UUID.randomUUID()),
                401, "AUTHENTIFICATION_REQUISE");
        refuser("designerLecteurChanson", envoyer(HttpMethod.PUT, racine + "/lecteur", animateur, designation, UUID.randomUUID()),
                403, "ACCES_REFUSE");
        verifier("designerLecteurChanson", envoyer(HttpMethod.PUT, racine + "/lecteur", ecran, designation, UUID.randomUUID()), 200);
        var inscription = envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        verifier("inscrirePassage", inscription, 201);
        String route = racine + "/passages/" + json.readTree(inscription.getBody()).path("passageId").asString();
        var appel = envoyer(HttpMethod.POST, route + "/appel", animateur, Map.of("numeroModification", 0), UUID.randomUUID());
        verifier("appelerPassage", appel, 200);
        String etape = json.readTree(appel.getBody()).path("etapeId").asString();
        String fichier = """
                {"lecteurId":"%s","etapeId":"%s","numeroModification":1,"source":"FICHIER_LOCAL",
                 "fichier":{"nom":"Chanson.mp3","tailleOctets":120,"dureeMs":0},"motifParolesSeules":null}
                """.formatted(lecteur, etape);
        for (String invalide : List.of(fichier.replace("Chanson.mp3", "dossier/Chanson.mp3"),
                fichier.replace(",\"dureeMs\":0", ""), fichier.replace("120", "-1"),
                fichier.replace("FICHIER_LOCAL", "PAROLES_SEULES"), fichier.replace("null", "\"ECRAN_NON_PRET\""))) {
            refuser("declarerLectureChanson", envoyer(HttpMethod.POST, route + "/lecture", null, invalide, UUID.randomUUID()),
                    400, "REQUETE_INVALIDE");
        }
        UUID cleRefus = UUID.randomUUID();
        UUID autre = UUID.randomUUID();
        String nonDesigne = fichier.replace(lecteur.toString(), autre.toString());
        refuser("declarerLectureChanson", envoyer(HttpMethod.POST, route + "/lecture", ecran, nonDesigne, cleRefus),
                409, "LECTEUR_NON_DESIGNE");
        var declare = envoyer(HttpMethod.POST, route + "/lecture", ecran, fichier, UUID.randomUUID());
        verifier("declarerLectureChanson", declare, 200);
        String lecture = json.readTree(declare.getBody()).path("lectureId").asString();
        var presence = Map.of("envoiId", UUID.randomUUID(), "etapeId", etape, "numeroModification", 1);
        verifier("confirmerPresencePassage", envoyer(HttpMethod.POST, route + "/presence", joueur, presence, null), 200);
        refuser("declarerLectureChanson", envoyer(HttpMethod.POST, route + "/lecture", ecran, nonDesigne, cleRefus),
                409, "LECTEUR_NON_DESIGNE");
        refuser("declarerLectureChanson", envoyer(HttpMethod.POST, route + "/lecture", ecran, fichier, UUID.randomUUID()),
                409, "COMMANDE_PERIMEE");
        var cible = Map.of("etapeId", etape, "lectureId", lecture);
        var debut = envoyer(HttpMethod.POST, route + "/calage/debut", animateur, cible, UUID.randomUUID());
        verifier("marquerDebutParoles", debut, 200);
        String demande = json.readTree(debut.getBody()).path("demandeId").asString();
        var releve = Map.of("lecteurId", lecteur, "etapeId", etape, "lectureId", lecture, "demandeId", demande, "positionMs", 0);
        verifier("releverPositionLecture", envoyer(HttpMethod.POST, route + "/calage/position", ecran, releve, UUID.randomUUID()), 200);
        var correction = Map.of("etapeId", etape, "lectureId", lecture, "numeroModificationCalage", 0, "sens", "RETARDER");
        UUID cleCorrection = UUID.randomUUID();
        var cale = envoyer(HttpMethod.POST, route + "/calage/correction", animateur, correction, cleCorrection);
        verifier("corrigerCalage", cale, 200);
        assertThat(json.readTree(cale.getBody()).path("numeroModificationCalage").asInt()).isEqualTo(1);
        var rejeu = envoyer(HttpMethod.POST, route + "/calage/correction", animateur, correction, cleCorrection);
        verifier("corrigerCalage", rejeu, 200);
        assertThat(json.readTree(rejeu.getBody())).isEqualTo(json.readTree(cale.getBody()));
        refuser("corrigerCalage", envoyer(HttpMethod.POST, route + "/calage/correction", animateur, correction, UUID.randomUUID()),
                409, "COMMANDE_PERIMEE");
        var signal = Map.of("lecteurId", lecteur, "etapeId", etape, "lectureId", lecture);
        verifier("signalerErreurLectureChanson", envoyer(HttpMethod.POST, route + "/erreur-lecture", ecran, signal, UUID.randomUUID()), 200);
        verifierRestauration(manche, EtatManche.EN_PAUSE);
        refuser("marquerDebutParoles", envoyer(HttpMethod.POST, route + "/calage/debut", animateur, cible, UUID.randomUUID()),
                409, "ETAT_INCOMPATIBLE");
        verifier("reprendreEnParolesSeules", envoyer(HttpMethod.POST, route + "/paroles-seules", animateur, cible, UUID.randomUUID()), 200);
        verifierRestauration(manche, EtatManche.EN_COURS);
        refuser("signalerFinLectureChanson", envoyer(HttpMethod.POST, route + "/fin-lecture", ecran,
                Map.of("lecteurId", lecteur, "etapeId", etape, "lectureId", lecture, "positionMs", 0), UUID.randomUUID()),
                409, "ETAT_INCOMPATIBLE");
    }

    @Test void laFinLocaleEnPauseClotLePassageEtLaisseLaMancheEnPause() throws Exception {
        UUID manche = demarrer();
        String racine = racine(manche);
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        UUID lecteur = UUID.randomUUID();
        verifier("designerLecteurChanson", envoyer(HttpMethod.PUT, racine + "/lecteur", ecran,
                Map.of("lecteurId", lecteur), UUID.randomUUID()), 200);
        var inscription = envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        verifier("inscrirePassage", inscription, 201);
        String route = racine + "/passages/" + json.readTree(inscription.getBody()).path("passageId").asString();
        var appel = envoyer(HttpMethod.POST, route + "/appel", animateur, Map.of("numeroModification", 0), UUID.randomUUID());
        verifier("appelerPassage", appel, 200);
        String etape = json.readTree(appel.getBody()).path("etapeId").asString();
        String corps = """
                {"lecteurId":"%s","etapeId":"%s","numeroModification":1,"source":"FICHIER_LOCAL",
                 "fichier":{"nom":"Chanson.mp3","tailleOctets":120,"dureeMs":null},"motifParolesSeules":null}
                """.formatted(lecteur, etape);
        var declare = envoyer(HttpMethod.POST, route + "/lecture", ecran, corps, UUID.randomUUID());
        verifier("declarerLectureChanson", declare, 200);
        String lecture = json.readTree(declare.getBody()).path("lectureId").asString();
        verifier("confirmerPresencePassage", envoyer(HttpMethod.POST, route + "/presence", joueur,
                Map.of("envoiId", UUID.randomUUID(), "etapeId", etape, "numeroModification", 1), null), 200);
        verifier("piloterManche", envoyer(HttpMethod.POST, racine + "/commandes", animateur,
                Map.of("action", "METTRE_EN_PAUSE", "etapeId", etape), UUID.randomUUID()), 200);
        verifierRestauration(manche, EtatManche.EN_PAUSE);
        var signal = Map.of("lecteurId", lecteur, "etapeId", etape, "lectureId", lecture, "positionMs", 120);
        verifier("signalerFinLectureChanson", envoyer(HttpMethod.POST, route + "/fin-lecture", ecran, signal, UUID.randomUUID()), 200);
        verifierRestauration(manche, EtatManche.EN_PAUSE);
        var vue = envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/salle", ecran, null, null);
        verifier("retrouverEtatSalle", vue, 200);
        assertThat(json.readTree(vue.getBody()).at("/vue/karaoke/courant/etat").asString()).isEqualTo("TERMINE");
        refuser("signalerFinLectureChanson", envoyer(HttpMethod.POST, route + "/fin-lecture", ecran, signal, UUID.randomUUID()),
                409, "COMMANDE_PERIMEE");
    }

    @Test void leVoteAppliqueLesRefusCommunsEtMetierEtRejoueLeRecuInitial() throws Exception {
        UUID manche = demarrer();
        var votant = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.get(1), soiree);
        String chemin = racine(manche) + "/passages/" + inscrire(manche, joueur, 1);
        String route = chemin + "/vote";
        UUID cle = UUID.randomUUID();
        var demande = Map.of("envoiId", cle, "numeroModification", 3, "note", 4);

        for (Object invalide : List.of(0, 6, 4.5, "4", true)) {
            refuserVote(route, null, Map.of("envoiId", cle, "numeroModification", 3, "note", invalide), 400, "REQUETE_INVALIDE");
        }
        for (String champ : List.of("joueurId", "inconnu")) {
            var invalide = new HashMap<String, Object>(demande);
            invalide.put(champ, votant.identifiant());
            refuserVote(route, votant, invalide, 400, "REQUETE_INVALIDE");
        }
        refuserVote(route, null, demande, 401, "AUTHENTIFICATION_REQUISE");
        refuserVote(route, animateur, demande, 403, "ACCES_REFUSE");
        refuserVote(route.replace(manche.toString(), UUID.randomUUID().toString()), votant, demande, 404, "RESSOURCE_INTROUVABLE");
        ports.absents.add(votant.identifiant());
        try {
            refuserVote(route, votant, demande, 404, "RESSOURCE_INTROUVABLE");
            assertThat(depot.retrouverEnvoi(EnvoiDeManche.cle(votant, route, cle))).isNull();
        } finally { ports.absents.remove(votant.identifiant()); }
        ports.inactives.add(manche);
        try {
            refuserVote(route, votant, demande, 409, "ETAPE_INACTIVE");
            assertThat(depot.retrouverEnvoi(EnvoiDeManche.cle(votant, route, cle))).isNull();
        } finally { ports.inactives.remove(manche); }

        socle.registre().retirer(manche);
        doThrow(new DataAccessResourceFailureException("Stockage indisponible pour ce test")).when(persistance).charger(manche);
        try {
            refuserVote(route, votant, demande, 503, "SERVICE_INDISPONIBLE");
            assertThat(depot.retrouverEnvoi(EnvoiDeManche.cle(votant, route, cle))).isNull();
        } finally { doCallRealMethod().when(persistance).charger(manche); }

        String inconnu = racine(manche) + "/passages/" + UUID.randomUUID() + "/vote";
        piloter(racine(manche), "METTRE_EN_PAUSE");
        refuserVote(inconnu, votant, demande, 404, "RESSOURCE_INTROUVABLE");
        assertThat(depot.retrouverEnvoi(EnvoiDeManche.cle(votant, inconnu, cle))).isNotNull();
        var pause = Map.of("envoiId", UUID.randomUUID(), "numeroModification", 0, "note", 4);
        refuserVote(route, votant, pause, 409, "ETAT_INCOMPATIBLE");
        piloter(racine(manche), "REPRENDRE");
        refuserVote(route, votant, pause, 409, "ETAT_INCOMPATIBLE");
        refuserVote(route, joueur, Map.of("envoiId", UUID.randomUUID(), "numeroModification", 0, "note", 4), 409, "VOTE_INTERDIT");
        refuserVote(route, votant, Map.of("envoiId", UUID.randomUUID(), "numeroModification", 1, "note", 4), 409, "COMMANDE_PERIMEE");
        refuserVote(route, votant, Map.of("envoiId", UUID.randomUUID(), "numeroModification", 0, "note", 4), 409, "VOTE_CLOS");

        var appel = envoyer(HttpMethod.POST, chemin + "/appel", animateur, Map.of("numeroModification", 0), UUID.randomUUID());
        verifier("appelerPassage", appel, 200);
        var etape = json.readTree(appel.getBody()).path("etapeId").asString();
        verifier("confirmerPresencePassage", envoyer(HttpMethod.POST, chemin + "/presence", joueur,
                Map.of("envoiId", UUID.randomUUID(), "etapeId", etape, "numeroModification", 1), null), 200);
        ((HorlogeFactice) socle.horloge()).avancer(java.time.Duration.ofSeconds(180));
        var recu = envoyer(HttpMethod.POST, route, votant, demande, null);
        verifier("voterPassage", recu, 200);
        assertThat(recu.getHeaders().getLocation()).isNull();
        assertThat(json.readTree(recu.getBody()).path("note").asInt()).isEqualTo(4);
        refuserVote(route, votant, Map.of("envoiId", cle, "numeroModification", 3, "note", 5), 409, "ENVOI_REUTILISE");
        refuserVote(route, votant, Map.of("envoiId", UUID.randomUUID(), "numeroModification", 3, "note", 5), 409, "VOTE_DEJA_ENREGISTRE");
        ((HorlogeFactice) socle.horloge()).avancer(java.time.Duration.ofSeconds(30));
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        for (var destination : Map.of("Salle", ecran, "Console", animateur, "Manette", votant).entrySet()) {
            var capture = envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/" + destination.getKey().toLowerCase(Locale.ROOT),
                    destination.getValue(), null, null);
            verifier("retrouverEtat" + destination.getKey(), capture, 200);
            var vote = json.readTree(capture.getBody()).at("/vue/karaoke/votes/0");
            assertThat(vote.path("etat").asString()).isEqualTo("CLOS");
            assertThat(vote.path("moyenneDixiemes").asInt()).isEqualTo(40);
            assertThat(vote.path("nombreVotes").asInt()).isEqualTo(1);
        }
        piloter(racine(manche), "TERMINER");
        var rejeu = envoyer(HttpMethod.POST, route, votant, demande, null);
        verifier("voterPassage", rejeu, 200);
        assertThat(json.readTree(rejeu.getBody())).isEqualTo(json.readTree(recu.getBody()));
        refuserVote(route, votant, Map.of("envoiId", UUID.randomUUID(), "numeroModification", 3, "note", 4), 409, "ETAT_INCOMPATIBLE");
    }

    private void refuserVote(String route, Acteur acteur, Object corps, int statut, String code) throws Exception {
        refuser("voterPassage", envoyer(HttpMethod.POST, route, acteur, corps, null), statut, code);
    }

    @Test void lesDixOperationsAppliquentLesRefusCommunsEtConserventSeulementLesRecusDurables() throws Exception {
        UUID manche = demarrer();
        UUID passage = inscrire(manche, joueur, 1);
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        for (var cas : operationsDuPassage(passage, ecran)) {
            String route = racine(manche) + cas.chemin();
            UUID envoi = UUID.randomUUID();
            var corps = cas.avecEnvoi(envoi);
            UUID cle = cas.acteur().type() == Acteur.Type.JOUEUR ? null : envoi;
            var autreRole = cas.acteur().type() == Acteur.Type.ANIMATEUR ? ecran : animateur;
            refuser(cas.operation(), envoyer(cas.methode(), route, null, corps, cle), 401, "AUTHENTIFICATION_REQUISE");
            refuser(cas.operation(), envoyer(cas.methode(), route, autreRole, corps, cle), 403, "ACCES_REFUSE");
            refuser(cas.operation(), envoyer(cas.methode(), racine(UUID.randomUUID()) + cas.chemin(), cas.acteur(), corps, cle),
                    404, "RESSOURCE_INTROUVABLE");
            ports.inactives.add(manche);
            try {
                refuser(cas.operation(), envoyer(cas.methode(), route, cas.acteur(), corps, cle), 409, "ETAPE_INACTIVE");
                assertThat(depot.retrouverEnvoi(EnvoiDeManche.cle(cas.acteur(), route, envoi))).isNull();
            } finally { ports.inactives.remove(manche); }

            socle.registre().retirer(manche);
            doThrow(new DataAccessResourceFailureException("Stockage indisponible pour ce test")).when(persistance).charger(manche);
            try {
                refuser(cas.operation(), envoyer(cas.methode(), route, cas.acteur(), corps, cle), 503, "SERVICE_INDISPONIBLE");
                assertThat(depot.retrouverEnvoi(EnvoiDeManche.cle(cas.acteur(), route, envoi))).isNull();
            } finally { doCallRealMethod().when(persistance).charger(manche); }

            var premier = envoyer(cas.methode(), route, cas.acteur(), corps, cle);
            if (cas.methode() == HttpMethod.PUT) { verifier(cas.operation(), premier, 200); }
            else { refuser(cas.operation(), premier, 409, "COMMANDE_PERIMEE"); }
            var autreCorps = new HashMap<>(corps);
            autreCorps.put(cas.champModifie(), cas.autreValeur());
            refuser(cas.operation(), envoyer(cas.methode(), route, cas.acteur(), autreCorps, cle), 409, "ENVOI_REUTILISE");
        }
    }

    @Test void appelerRefuseLaPausePuisLeCourantActif() throws Exception {
        UUID manche = demarrer();
        String racine = racine(manche);
        var autre = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.get(1), soiree);
        UUID premier = inscrire(manche, joueur, 1);
        UUID second = inscrire(manche, autre, 2);
        var corps = Map.of("numeroModification", 0);
        String premierAppel = racine + "/passages/" + premier + "/appel";
        String secondAppel = racine + "/passages/" + second + "/appel";
        piloter(racine, "METTRE_EN_PAUSE");
        UUID cleEnPause = UUID.randomUUID();
        refuser("appelerPassage", envoyer(HttpMethod.POST, premierAppel, animateur, corps, cleEnPause), 409, "ETAT_INCOMPATIBLE");
        piloter(racine, "REPRENDRE");
        refuser("appelerPassage", envoyer(HttpMethod.POST, premierAppel, animateur, corps, cleEnPause), 409, "ETAT_INCOMPATIBLE");
        refuser("appelerPassage", envoyer(HttpMethod.POST, secondAppel, animateur, corps, UUID.randomUUID()), 409, "COMMANDE_PERIMEE");
        verifier("appelerPassage", envoyer(HttpMethod.POST, premierAppel, animateur, corps, UUID.randomUUID()), 200);
        refuser("appelerPassage", envoyer(HttpMethod.POST, secondAppel, animateur, corps, UUID.randomUUID()), 409, "ETAT_INCOMPATIBLE");
    }

    @Test void leContratConserveLe201EtRefuseLAppelManuelEnAutomatique() throws Exception {
        pilotage.choisir(soiree, true);
        UUID manche = demarrer();
        String racine = racine(manche);
        var inscription = envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        verifier("inscrirePassage", inscription, 201);
        var recu = json.readTree(inscription.getBody());
        assertThat(recu.path("etatApres").asString()).isEqualTo("EN_ATTENTE");
        assertThat(recu.path("numeroModification").asInt()).isZero();
        var avant = (InstantaneKaraoke) service.instantane(soiree, manche);
        UUID courant = UUID.fromString(recu.path("passageId").asString());
        assertThat(avant.cycle().passageId()).isEqualTo(courant);

        String route = racine + "/passages/" + courant + "/appel";
        var corps = Map.of("numeroModification", 0);
        var refus = envoyer(HttpMethod.POST, route, animateur, corps, UUID.randomUUID());
        refuser("appelerPassage", refus, 409, "ETAT_INCOMPATIBLE");
        assertThat(json.readTree(refus.getBody()).path("detail").asString())
                .isEqualTo("En Pilotage Automatique, le Passage suivant est appelé seul.");
        refuser("appelerPassage",
                envoyer(HttpMethod.POST, racine + "/passages/" + UUID.randomUUID() + "/appel", animateur, corps, UUID.randomUUID()),
                404, "RESSOURCE_INTROUVABLE");
        assertThat(service.instantane(soiree, manche)).isEqualTo(avant);
    }

    private UUID inscrire(UUID manche, Acteur interprete, long chanson) throws Exception {
        var reponse = envoyer(HttpMethod.POST, racine(manche) + "/passages", interprete,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(chanson)), null);
        verifier("inscrirePassage", reponse, 201);
        return UUID.fromString(json.readTree(reponse.getBody()).path("passageId").asString());
    }

    private void piloter(String racine, String action) throws Exception {
        verifier("piloterManche", envoyer(HttpMethod.POST, racine + "/commandes", animateur,
                Map.of("action", action), UUID.randomUUID()), 200);
    }

    private List<OperationDuPassage> operationsDuPassage(UUID passage, Acteur ecran) {
        String chemin = "/passages/" + passage;
        UUID etape = UUID.randomUUID(), lecteur = UUID.randomUUID(), lecture = UUID.randomUUID(), demande = UUID.randomUUID();
        Map<String, Object> cible = Map.of("etapeId", etape, "lectureId", lecture);
        var signal = Map.<String, Object>of("etapeId", etape, "lectureId", lecture, "lecteurId", lecteur);
        var declaration = new HashMap<String, Object>(Map.of("etapeId", etape, "lecteurId", lecteur, "numeroModification", 1,
                "source", "PAROLES_SEULES", "motifParolesSeules", "FICHIER_ABSENT"));
        declaration.put("fichier", null);
        return List.of(
                new OperationDuPassage("appelerPassage", HttpMethod.POST, chemin + "/appel", animateur,
                        Map.of("numeroModification", 1), "numeroModification", 0),
                new OperationDuPassage("confirmerPresencePassage", HttpMethod.POST, chemin + "/presence", joueur,
                        Map.of("etapeId", etape, "numeroModification", 1), "numeroModification", 2),
                new OperationDuPassage("designerLecteurChanson", HttpMethod.PUT, "/lecteur", ecran,
                        Map.of("lecteurId", lecteur), "lecteurId", UUID.randomUUID()),
                new OperationDuPassage("declarerLectureChanson", HttpMethod.POST, chemin + "/lecture", ecran,
                        declaration, "numeroModification", 2),
                new OperationDuPassage("signalerFinLectureChanson", HttpMethod.POST, chemin + "/fin-lecture", ecran,
                        Map.of("etapeId", etape, "lectureId", lecture, "lecteurId", lecteur, "positionMs", 0), "positionMs", 1),
                new OperationDuPassage("signalerErreurLectureChanson", HttpMethod.POST, chemin + "/erreur-lecture", ecran,
                        signal, "lectureId", UUID.randomUUID()),
                new OperationDuPassage("reprendreEnParolesSeules", HttpMethod.POST, chemin + "/paroles-seules", animateur,
                        cible, "etapeId", UUID.randomUUID()),
                new OperationDuPassage("marquerDebutParoles", HttpMethod.POST, chemin + "/calage/debut", animateur,
                        cible, "lectureId", UUID.randomUUID()),
                new OperationDuPassage("corrigerCalage", HttpMethod.POST, chemin + "/calage/correction", animateur,
                        Map.of("etapeId", etape, "lectureId", lecture, "sens", "RETARDER", "numeroModificationCalage", 0), "sens", "AVANCER"),
                new OperationDuPassage("releverPositionLecture", HttpMethod.POST, chemin + "/calage/position", ecran,
                        Map.of("etapeId", etape, "lectureId", lecture, "lecteurId", lecteur, "demandeId", demande, "positionMs", 0),
                        "demandeId", UUID.randomUUID()));
    }

    private record OperationDuPassage(String operation, HttpMethod methode, String chemin, Acteur acteur, Map<String, Object> corps,
            String champModifie, Object autreValeur) {
        Map<String, Object> avecEnvoi(UUID id) {
            if (acteur.type() != Acteur.Type.JOUEUR) { return corps; }
            var avecEnvoi = new HashMap<>(corps);
            avecEnvoi.put("envoiId", id);
            return avecEnvoi;
        }
    }

    private void verifierRestauration(UUID manche, EtatManche etat) {
        var avant = (InstantaneKaraoke) service.instantane(soiree, manche);
        assertThat(depot.charger(manche).getEtat()).isEqualTo(etat);
        socle.registre().retirer(manche);
        assertThat(service.instantane(soiree, manche)).isEqualTo(avant);
    }

    void refuser(String operation, ResponseEntity<String> reponse, int statut, String code) throws Exception {
        verifier(operation, reponse, statut);
        assertThat(json.readTree(reponse.getBody()).path("code").asString()).isEqualTo(code);
    }

    @Autowired SocleDeManche socle;
    SocleDeManche serviceSocle() { return socle; }
    UUID demarrer() throws Exception {
        UUID manche = service.creer(soiree, Map.of());
        verifierPrototype("demarrerMancheDeTest", envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches/" + manche + "/demarrer",
                animateur, null, UUID.randomUUID()), 200);
        return manche;
    }
    String racine(UUID manche) { return "/api/soirees/" + soiree + "/manches/" + manche; }
    ResponseEntity<String> envoyer(HttpMethod methode, String route, Acteur acteur, Object corps, UUID id) {
        var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
        if (acteur != null) { requete.header("Authorization", "Bearer demo:" + acteur.type() + ":" + acteur.soireeId() + ":" + acteur.identifiant()); }
        if (id != null) { requete.header("Idempotency-Key", id.toString()).header("X-CSRF-TOKEN", "demonstration"); }
        if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
        return requete.retrieve().onStatus(s -> s.isError(), (q, r) -> { }).toEntity(String.class);
    }
    static void verifier(String operation, ResponseEntity<String> reponse, int statut) throws Exception {
        ContratApi.verifier("docs/api/openapi.yaml", operation, reponse);
        assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(reponse.getStatusCode().value()).isEqualTo(statut);
    }
    static void verifierPrototype(String operation, ResponseEntity<String> reponse, int statut) throws Exception {
        ContratApi.verifier("docs/api/prototype/openapi.yaml", operation, reponse);
        assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(reponse.getStatusCode().value()).isEqualTo(statut);
    }
    /** Liaison HTTP isolée, exécutable hors Docker avec -Dtest=KaraokeHttpTest$LiaisonSansBaseTest. */
    public static class LiaisonSansBaseTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"application/json", "application/problem+json", "application/vnd.croustyquizz+json"})
        void lesSixCorpsInvalidesSontRefusesAvantDeResoudreLIdentite(String typeContenu) throws Exception {
            var banc = new ServiceKaraokeTest.BancKaraoke();
            var resolution = new java.util.concurrent.atomic.AtomicInteger();
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new KaraokeControleur(banc.service))
                    .setControllerAdvice(new ErreursHttp())
                    .setMessageConverters(new ConvertisseurCorpsKaraoke(banc.json), new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(banc.json))
                    .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                        public boolean supportsParameter(org.springframework.core.MethodParameter p) { return p.getParameterType() == Acteur.class; }
                        public Object resolveArgument(org.springframework.core.MethodParameter p,
                                org.springframework.web.method.support.ModelAndViewContainer m,
                                org.springframework.web.context.request.NativeWebRequest r,
                                org.springframework.web.bind.support.WebDataBinderFactory l) {
                            resolution.incrementAndGet();
                            throw new Refuse(401, "AUTHENTIFICATION_REQUISE", "Identité absente.").exception();
                        }
                    }).build();
            String racine = "/api/soirees/" + UUID.randomUUID() + "/manches/" + UUID.randomUUID();
            record Cas(HttpMethod methode, String route, String operation, String corps) { }
            var cas = List.of(new Cas(HttpMethod.POST, "/passages", "inscrirePassage", "{}"),
                    new Cas(HttpMethod.POST, "/passages", "inscrirePassage", "{illisible"),
                    new Cas(HttpMethod.POST, "/passages/" + UUID.randomUUID() + "/desinscription", "desinscrirePassage", "{}"),
                    new Cas(HttpMethod.POST, "/passages/" + UUID.randomUUID() + "/retrait", "retirerPassage", "{\"numeroModification\":\"0\"}"),
                    new Cas(HttpMethod.PUT, "/passages/" + UUID.randomUUID() + "/placement", "deplacerPassage", "{\"numeroModification\":0,\"tour\":1}"),
                    new Cas(HttpMethod.PUT, "/file-attente/priorite", "definirPrioritePassage", "{\"passage\":null,\"prioritaire\":false}"),
                    new Cas(HttpMethod.PUT, "/file-attente/limite", "reglerLimitePassages", "{\"limitePassagesEnAttente\":1.0}"));
            for (var c : cas) {
                var resultat = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(c.methode(), racine + c.route())
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(typeContenu).content(c.corps())).andReturn();
                verifier(c.operation(), ResponseEntity.status(resultat.getResponse().getStatus()).headers(entetes(resultat))
                        .body(resultat.getResponse().getContentAsString()), 400);
            }
            assertThat(resolution).hasValue(0);
        }
        @Test void lesHuitRoutesLientLesCorpsEtRendentLesReponsesDuService() throws Exception {
            var parcours = new ServiceKaraokeTest();
            UUID manche = parcours.demarrer();
            var banc = parcours.banc;
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new KaraokeControleur(banc.service))
                    .setControllerAdvice(new ErreursHttp())
                    .setMessageConverters(new ConvertisseurCorpsKaraoke(banc.json), new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(banc.json))
                    .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                        public boolean supportsParameter(org.springframework.core.MethodParameter p) { return p.getParameterType() == Acteur.class; }
                        public Object resolveArgument(org.springframework.core.MethodParameter p,
                                org.springframework.web.method.support.ModelAndViewContainer m,
                                org.springframework.web.context.request.NativeWebRequest r,
                                org.springframework.web.bind.support.WebDataBinderFactory l) {
                            return new fr.croustyquizz.tempsreel.ResolveurDActeurDemonstration().resoudre(r.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class));
                        }
                    }).build();
            String racine = "/api/soirees/" + parcours.soiree + "/manches/" + manche;
            appeler(mvc, "rechercherChansons", HttpMethod.GET, racine + "/chansons?recherche=Chanson&rechercheId=" + UUID.randomUUID(), parcours.joueur, null, 200);
            appeler(mvc, "consulterChanson", HttpMethod.GET, racine + "/chansons/" + IdentifiantsChanson.encoder(1), parcours.joueur, null, 200);
            String corps = banc.json.writeValueAsString(Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)));
            var inscription = appeler(mvc, "inscrirePassage", HttpMethod.POST, racine + "/passages", parcours.joueur, corps, 201);
            UUID passage = UUID.fromString(banc.json.readTree(inscription.getBody()).path("passageId").asString());
            appeler(mvc, "deplacerPassage", HttpMethod.PUT, racine + "/passages/" + passage + "/placement", parcours.animateur,
                    "{\"numeroModification\":0,\"tour\":1,\"avant\":null}", 200);
            appeler(mvc, "definirPrioritePassage", HttpMethod.PUT, racine + "/file-attente/priorite", parcours.animateur,
                    "{\"passage\":{\"passageId\":\"" + passage + "\",\"numeroModification\":0},\"prioritaire\":true}", 200);
            appeler(mvc, "reglerLimitePassages", HttpMethod.PUT, racine + "/file-attente/limite", parcours.animateur,
                    "{\"limitePassagesEnAttente\":2}", 200);
            appeler(mvc, "desinscrirePassage", HttpMethod.POST, racine + "/passages/" + passage + "/desinscription", parcours.joueur,
                    "{\"envoiId\":\"" + UUID.randomUUID() + "\",\"numeroModification\":1}", 200);
            var seconde = appeler(mvc, "inscrirePassage", HttpMethod.POST, racine + "/passages", parcours.joueur,
                    banc.json.writeValueAsString(Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(2))), 201);
            UUID second = UUID.fromString(banc.json.readTree(seconde.getBody()).path("passageId").asString());
            appeler(mvc, "retirerPassage", HttpMethod.POST, racine + "/passages/" + second + "/retrait", parcours.animateur,
                    "{\"numeroModification\":0}", 200);
        }
        static ResponseEntity<String> appeler(org.springframework.test.web.servlet.MockMvc mvc, String operation, HttpMethod methode,
                String route, Acteur acteur, String corps, int statut) throws Exception {
            verifierChemin(operation, methode, route);
            var requete = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(methode, route)
                    .header("Authorization", "Bearer demo:" + acteur.type() + ":" + acteur.soireeId() + ":" + acteur.identifiant())
                    .header("Idempotency-Key", UUID.randomUUID());
            if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).content(corps); }
            var resultat = mvc.perform(requete).andReturn();
            var reponse = ResponseEntity.status(resultat.getResponse().getStatus()).headers(entetes(resultat))
                    .body(resultat.getResponse().getContentAsString());
            verifier(operation, reponse, statut);
            return reponse;
        }

        static void verifierChemin(String operation, HttpMethod methode, String route) {
            var contrat = tools.jackson.dataformat.yaml.YAMLMapper.builder().build().readTree(java.nio.file.Path.of("docs/api/openapi.yaml").toFile());
            for (var chemin : contrat.path("paths").properties()) {
                for (var verbe : chemin.getValue().properties()) {
                    if (operation.equals(verbe.getValue().path("operationId").asString())) {
                        assertThat(methode.name().toLowerCase(Locale.ROOT)).isEqualTo(verbe.getKey());
                        String motif = chemin.getKey().replaceAll("\\{[^}]+}", "[^/]+");
                        assertThat(route.split("\\?", 2)[0]).matches(motif);
                        return;
                    }
                }
            }
            throw new AssertionError("Opération absente du contrat : " + operation);
        }

        static HttpHeaders entetes(org.springframework.test.web.servlet.MvcResult resultat) {
            var entetes = new HttpHeaders();
            resultat.getResponse().getHeaderNames().forEach(n -> entetes.put(n, new ArrayList<>(resultat.getResponse().getHeaders(n))));
            return entetes;
        }
    }

}
