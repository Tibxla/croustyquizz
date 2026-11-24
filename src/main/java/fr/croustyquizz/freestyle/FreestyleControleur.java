package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.EnvoiDeCommande;
import fr.croustyquizz.manche.ReponsesDeCommande;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/soirees/{soireeId}/freestyle/instrumentales")
class FreestyleControleur {
    private final ServiceFreestyle service;
    private final JsonMapper json;

    FreestyleControleur(ServiceFreestyle service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @GetMapping
    ResponseEntity<?> consulter(@PathVariable UUID soireeId, Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("instrumentales", service.instrumentales(soireeId)));
    }

    @PutMapping
    ResponseEntity<?> declarer(@PathVariable UUID soireeId, @RequestHeader("Idempotency-Key") UUID envoiId,
            @RequestBody JsonNode corps, Acteur acteur) {
        var fiches = CorpsFreestyle.instrumentales(corps, json);
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ECRAN);
        String route = "/api/soirees/" + soireeId + "/freestyle/instrumentales";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, corps);
        return ReponsesDeCommande.http(service.declarer(envoi, fiches), route);
    }
}
