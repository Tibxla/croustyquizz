package fr.croustyquizz.soiree;

/**
 * Source des codes d'accès proposés à l'ouverture d'une Soirée. Un test la
 * remplace pour forcer deux ouvertures à tirer le même code.
 */
@FunctionalInterface
interface TirageDeCode {

	/** Un code au format de {@link CodeAcces}, pas forcément libre. */
	String tirer();

}
