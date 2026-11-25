import test from 'node:test';
import assert from 'node:assert/strict';
import { fichiersDeMusiqueDAttente, normaliserNomAudio, trouverFichierDeChanson } from '../../src/main/resources/static/js/modes/karaoke/fichiers.mjs';

test('normalise les extensions audio, les accents, la casse et la ponctuation Unicode', () => {
	assert.equal(normaliserNomAudio('  Beyoncé—ＨＡＬＯ (東京 ２).MP3'), 'beyonce halo 東京 2');
	assert.equal(normaliserNomAudio('Halo.flac'), 'halo flac');
});

test('trouve une correspondance exacte dans les deux ordres et refuse les doublons de sous-dossiers', () => {
	const chanson = { artiste: 'Beyoncé', titre: 'Halo' };
	const fichiers = ['Beyoncé - Halo.MP3', 'halo beyonce.m4A', 'Halo (live).mp3', 'Beyoncé - Halo.flac']
		.map(name => ({ name, webkitRelativePath: 'dossier/' + name }));
	for (const fichier of fichiers.slice(0, 2)) {
		assert.deepEqual(trouverFichierDeChanson([fichier], chanson), { etat: 'unique', fichier });
	}
	assert.deepEqual(trouverFichierDeChanson(fichiers, chanson), { etat: 'ambigu' });
	assert.deepEqual(trouverFichierDeChanson(fichiers.slice(2), chanson), { etat: 'absent' });
	for (const ext of ['ogg', 'wav']) {
		const fichier = { name: 'Halo · Beyonce.' + ext };
		assert.deepEqual(trouverFichierDeChanson([fichier], chanson), { etat: 'unique', fichier });
	}
});

test('les fichiers du sous-dossier Attente sont réservés à la Musique d’attente', () => {
	const chanson = { artiste: 'Beyoncé', titre: 'Halo' };
	const fichier = dossier => ({ name: 'Beyoncé - Halo.mp3', webkitRelativePath: `${dossier}/Beyoncé - Halo.mp3` });
	const attente = fichier('Dossier/ÀTTÉNTE/Sous-dossier');
	const habituel = fichier('Attente/Chansons/Attente');
	const ponctuation = fichier('Dossier/Attente!');
	const image = { name: 'pochette.png', webkitRelativePath: 'Dossier/Attente/pochette.png' };
	assert.deepEqual(fichiersDeMusiqueDAttente([habituel, ponctuation, attente, image]), [attente]);
	assert.deepEqual(fichiersDeMusiqueDAttente([habituel, ponctuation]), []);
	assert.deepEqual(trouverFichierDeChanson([attente, habituel], chanson), { etat: 'unique', fichier: habituel });
	assert.deepEqual(trouverFichierDeChanson([attente], chanson), { etat: 'absent' });
});
