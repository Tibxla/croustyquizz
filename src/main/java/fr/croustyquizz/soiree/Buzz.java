package fr.croustyquizz.soiree;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Reçu du Buzz du socle, repris dans la partie commune des vues. */
@Embeddable
record Buzz(@Column(name = "buzz_envoi_id") UUID envoiId,
		@Column(name = "buzz_joueur_id") UUID joueurId,
		@Column(name = "buzz_pseudo") String pseudo,
		@Column(name = "buzz_recu_le") Instant recuLe,
		@Column(name = "buzz_rang") long rang) { }
