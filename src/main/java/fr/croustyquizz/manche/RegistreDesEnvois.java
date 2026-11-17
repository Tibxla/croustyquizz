package fr.croustyquizz.manche;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Déduplication avant appel du domaine, conservée après redémarrage du processus. */
@Component
public final class RegistreDesEnvois {
	private static final int PREMIER_STATUT_ERREUR = 400;
	private final DepotDeManches depot;
	private final JsonMapper json;
	public RegistreDesEnvois(DepotDeManches depot, JsonMapper json) { this.depot = depot; this.json = json; }
	public ResultatDeCommande retrouver(EnvoiDeCommande envoi) {
		var precedent = depot.retrouverEnvoi(EnvoiDeManche.cle(envoi.acteur(), envoi.route(), envoi.envoiId()));
		if (precedent == null) { return null; }
		if (!precedent.getEmpreinte().equals(empreinte(envoi.corps()))) {
			return new Refuse(409, "ENVOI_REUTILISE", "Cet identifiant d'envoi a déjà été utilisé avec un autre contenu.");
		}
		JsonNode corps = json.readTree(precedent.getCorps());
		if (precedent.getStatut() >= PREMIER_STATUT_ERREUR) {
			return new Refuse(precedent.getStatut(), corps.path("code").asString(), corps.path("detail").asString());
		}
		return new Accepte(precedent.getStatut() == 204 ? null : corps, java.util.Set.of(), "manche-demarree");
	}
	public void enregistrer(EnvoiDeCommande envoi, ResultatDeCommande resultat, Instant maintenant) {
		depot.enregistrerEnvoi(new EnvoiDeManche(envoi.acteur(), envoi.route(), envoi.envoiId(), empreinte(envoi.corps()),
				ReponsesDeCommande.statut(resultat), json.writeValueAsString(ReponsesDeCommande.corps(resultat, envoi.route())), maintenant));
	}
	private String empreinte(Object corps) {
		try {
			byte[] octets = canonique(json.valueToTree(corps)).getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
		} catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
	}
	private String canonique(JsonNode noeud) {
		if (noeud.isObject()) {
			Map<String, String> trie = new TreeMap<>();
			noeud.properties().forEach(champ -> trie.put(champ.getKey(), canonique(champ.getValue())));
			return json.writeValueAsString(trie);
		}
		if (noeud.isArray()) {
			return json.writeValueAsString(noeud.valueStream().map(this::canonique).toList());
		}
		return noeud.toString();
	}
}
