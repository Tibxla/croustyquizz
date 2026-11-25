package fr.croustyquizz.manche;

/**
 * Contrôle des pouvoirs délégués (#67), implémenté par {@code tempsreel}. L'appelant tient le verrou de la
 * Soirée pendant tout le parcours de sa commande, contrôle compris : un retrait de Délégation, sérialisé par ce
 * même verrou, vaut donc dès la commande suivante. La portée de la Soirée est vérifiée avant, par l'appelant.
 */
public interface PouvoirsDelegues {

	/** Laisse passer l'Animateur et le Joueur délégué de ce pouvoir ; lève 403 ACCES_REFUSE sinon, sans reçu. */
	void autoriser(Acteur acteur, Pouvoir pouvoir);

	/** Vrai pour un Joueur dont la Délégation comprend ce pouvoir. */
	boolean detient(Acteur acteur, Pouvoir pouvoir);

	/** Sans Délégation : l'Animateur seul, pour les assemblages de test qui n'ont pas de base. */
	PouvoirsDelegues ANIMATEUR_SEUL = new PouvoirsDelegues() {
		@Override
		public void autoriser(Acteur acteur, Pouvoir pouvoir) {
			if (acteur.type() != Acteur.Type.ANIMATEUR) {
				throw new Refuse(403, "ACCES_REFUSE", "Cet accès ne permet pas cette opération.").exception();
			}
		}

		@Override
		public boolean detient(Acteur acteur, Pouvoir pouvoir) {
			return false;
		}
	};
}
