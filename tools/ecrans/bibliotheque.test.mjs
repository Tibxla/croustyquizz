import test from 'node:test';
import assert from 'node:assert/strict';
import {
	chargerBibliotheque, decrireCredits, fusionnerMorceauxDAttente, morceauxDeLaBibliotheque, partiesDuCredit,
} from '../../src/main/resources/static/js/bibliotheque.mjs';
import { choisirFichierDeChanson } from '../../src/main/resources/static/js/modes/karaoke/fichiers.mjs';

const catalogue = {
	version: 1,
	licences: {
		'CC-BY-4.0': { nom: 'CC BY 4.0', url: 'https://creativecommons.org/licenses/by/4.0/deed.fr' },
		'CC-BY-SA-4.0': { nom: 'CC BY-SA 4.0', url: 'https://creativecommons.org/licenses/by-sa/4.0/deed.fr' },
	},
	entrees: [
		{ type: 'attente', titre: 'Ambiance', artiste: 'Kevin MacLeod', dureeMs: 3000, licence: 'CC-BY-4.0',
			source: 'https://example.org/ambiance', fichier: 'attente/kevin-macleod-ambiance.mp3', tailleOctets: 1200 },
		{ type: 'attente', titre: 'Crépuscule', artiste: 'Kevin MacLeod', dureeMs: 3000, licence: 'CC-BY-4.0',
			source: 'https://example.org/crepuscule', fichier: 'attente/kevin-macleod-crepuscule.mp3', tailleOctets: 1300 },
		{ type: 'chanson', numero: 1, titre: 'La lanterne', artiste: 'Les Lucioles', dureeMs: 4000, licence: 'CC-BY-SA-4.0',
			source: 'https://example.org/la-lanterne', fichier: 'chansons/les-lucioles-la-lanterne.mp3', tailleOctets: 2400,
			lrc: 'chansons/les-lucioles-la-lanterne.lrc' },
	],
};
const dossier = (...chemins) => chemins.map(chemin => ({ name: chemin.split('/').at(-1), webkitRelativePath: chemin, size: 10 }));

test('la Bibliothèque de base donne ses morceaux d’attente comme des fichiers lisibles par leur URL, avec leur crédit', () => {
	const attente = morceauxDeLaBibliotheque(catalogue, 'attente');
	assert.deepEqual(attente[0], {
		name: 'kevin-macleod-ambiance.mp3', size: 1200, url: '/bibliotheque/attente/kevin-macleod-ambiance.mp3',
		credit: { titre: 'Ambiance', artiste: 'Kevin MacLeod', licence: 'CC BY 4.0',
			urlLicence: 'https://creativecommons.org/licenses/by/4.0/deed.fr' },
	});
	assert.equal(attente.length, 2);
	assert.deepEqual(morceauxDeLaBibliotheque({ version: 1, licences: {}, entrees: [] }, 'attente'), []);
});

test('les morceaux d’Attente du dossier s’ajoutent à ceux de la bibliothèque et remplacent leurs homonymes', () => {
	const bibliotheque = morceauxDeLaBibliotheque(catalogue, 'attente');
	assert.deepEqual(fusionnerMorceauxDAttente(bibliotheque, []), bibliotheque);
	const lieu = dossier('Musique/Attente/Soirée.ogg', 'Musique/Attente/Kevin MacLeod - Ambiance.mp3',
		'Musique/Artiste - Chanson.mp3', 'Musique/Attente/notes.txt');
	const fusion = fusionnerMorceauxDAttente(bibliotheque, lieu);
	assert.deepEqual(fusion.map(morceau => morceau.name),
		['kevin-macleod-crepuscule.mp3', 'Soirée.ogg', 'Kevin MacLeod - Ambiance.mp3']);
	// Le titre seul suffit aussi à reconnaître le même morceau.
	assert.deepEqual(fusionnerMorceauxDAttente(bibliotheque, dossier('Musique/Attente/crepuscule.MP3')).map(m => m.name),
		['kevin-macleod-ambiance.mp3', 'crepuscule.MP3']);
	assert.deepEqual(fusionnerMorceauxDAttente([], lieu).map(m => m.name), ['Soirée.ogg', 'Kevin MacLeod - Ambiance.mp3']);
});

test('le fichier du Lieu l’emporte sur celui de la bibliothèque, qui passe avant les paroles seules', () => {
	const chanson = { titre: 'La lanterne', artiste: 'Les Lucioles', origine: 'BIBLIOTHEQUE', bibliotheque: {
		fichier: '/bibliotheque/chansons/les-lucioles-la-lanterne.mp3', tailleOctets: 2400, licence: 'CC BY-SA 4.0',
		urlLicence: 'https://creativecommons.org/licenses/by-sa/4.0/deed.fr', urlSource: 'https://example.org/la-lanterne' } };
	const libre = { etat: 'unique', bibliotheque: true, fichier: {
		name: 'les-lucioles-la-lanterne.mp3', size: 2400, url: '/bibliotheque/chansons/les-lucioles-la-lanterne.mp3' } };
	assert.deepEqual(choisirFichierDeChanson([], chanson), libre);
	assert.deepEqual(choisirFichierDeChanson(null, chanson), libre);
	const [local] = dossier('Musique/Les Lucioles - La lanterne.mp3');
	assert.deepEqual(choisirFichierDeChanson([local], chanson), { etat: 'unique', fichier: local });
	// Deux fichiers du Lieu se disputent la Chanson : la bibliothèque tranche plutôt que les paroles seules.
	assert.deepEqual(choisirFichierDeChanson(dossier('Musique/A/La lanterne - Les Lucioles.mp3',
		'Musique/B/La lanterne - Les Lucioles.mp3'), chanson), libre);
	const deLrclib = { titre: 'La lanterne', artiste: 'Les Lucioles', origine: 'LRCLIB', bibliotheque: null };
	assert.deepEqual(choisirFichierDeChanson([], deLrclib), { etat: 'absent' });
	assert.deepEqual(choisirFichierDeChanson([local], deLrclib), { etat: 'unique', fichier: local });
});

test('le crédit nomme le titre, l’auteur et la licence, et seul le titre peut s’abréger', () => {
	assert.deepEqual(partiesDuCredit({ nature: 'Musique', titre: 'Ambiance', artiste: 'Kevin MacLeod', licence: 'CC BY 4.0' }),
		['Musique : « Ambiance', '\u00a0», Kevin MacLeod · CC BY 4.0']);
});

test('la page des crédits reprend chaque entrée du catalogue avec sa licence, sa source et les paroles en BY-SA', () => {
	const credits = decrireCredits(catalogue);
	assert.deepEqual(credits.map(section => [section.titre, section.entrees.length]),
		[['Musique d’attente', 2], ['Chansons du Karaoké', 1]]);
	assert.deepEqual(credits[1].entrees[0], {
		titre: 'La lanterne', artiste: 'Les Lucioles', licence: 'CC BY-SA 4.0',
		urlLicence: 'https://creativecommons.org/licenses/by-sa/4.0/deed.fr', source: 'https://example.org/la-lanterne',
		paroles: '/bibliotheque/chansons/les-lucioles-la-lanterne.lrc', parolesPartagees: true,
	});
	assert.equal(credits[0].entrees[0].paroles, null);
	assert.deepEqual(decrireCredits({ version: 1, licences: {}, entrees: [] }), []);
});

test('le catalogue se charge depuis le serveur, et son absence laisse une bibliothèque vide', async () => {
	const demandes = [];
	const chercher = async (url, options) => {
		demandes.push([url, options]);
		return { ok: true, json: async () => catalogue };
	};
	assert.deepEqual(await chargerBibliotheque(chercher), catalogue);
	assert.deepEqual(demandes, [['/bibliotheque/catalogue.json', { cache: 'no-cache' }]]);
	const vide = { version: 1, licences: {}, entrees: [] };
	assert.deepEqual(await chargerBibliotheque(async () => ({ ok: false, status: 404 })), vide);
	assert.deepEqual(await chargerBibliotheque(async () => { throw new TypeError('réseau'); }), vide);
	assert.deepEqual(await chargerBibliotheque(async () => ({ ok: true, json: async () => ({ entrees: 'faux' }) })), vide);
});
