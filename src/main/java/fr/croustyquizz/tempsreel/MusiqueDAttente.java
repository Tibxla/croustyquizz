package fr.croustyquizz.tempsreel;

record MusiqueDAttente(Etat etat, Boolean morceauxDisponibles) {
	static final MusiqueDAttente PAR_DEFAUT = new MusiqueDAttente(Etat.JOUE, null);

	enum Etat { JOUE, COUPEE }
}
