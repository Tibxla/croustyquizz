package fr.croustyquizz.manche;

/** Résultat HTTP décidé par le déroulement, avec ses seules vues à republier. */
public sealed interface ResultatDeCommande permits Accepte, Refuse { }
