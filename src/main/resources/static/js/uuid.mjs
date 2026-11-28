// Identifiant d'envoi (UUID v4) : Idempotency-Key, envoiId, identifiants de suivi.
// Hors contexte sécurisé (téléphone en http:// sur le réseau local), seul getRandomValues existe : d'où le repli.
export function creerUuid(cryptoDisponible = globalThis.crypto) {
	if (cryptoDisponible.randomUUID) return cryptoDisponible.randomUUID();
	const octets = cryptoDisponible.getRandomValues(new Uint8Array(16));
	octets[6] = (octets[6] & 15) | 64;
	octets[8] = (octets[8] & 63) | 128;
	const hex = Array.from(octets, octet => octet.toString(16).padStart(2, '0')).join('');
	return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
