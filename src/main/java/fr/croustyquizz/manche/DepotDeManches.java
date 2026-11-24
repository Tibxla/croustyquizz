package fr.croustyquizz.manche;

import java.util.List;
import java.util.UUID;

/** Stockage commun appelé exclusivement dans la transaction du service. */
public interface DepotDeManches {
	ManchePersistante charger(UUID mancheId);
	void enregistrer(ManchePersistante manche);
	void retirer(ManchePersistante manche);
	List<ManchePersistante> deLaSoiree(UUID soireeId);
	EnvoiDeManche retrouverEnvoi(UUID cle);
	void enregistrerEnvoi(EnvoiDeManche envoi);
}
