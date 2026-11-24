// Serveur statique local pour la recette Playwright, sans Spring ni PostgreSQL.
// node tools/ecrans/servir-recette.mjs ; les simulations réseau sont dans recette-navigateur.mjs.
import { createServer } from 'node:http';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { homedir } from 'node:os';
import { resolve, extname } from 'node:path';

const racine = resolve('src/main/resources/static');
const port = Number(process.env.CROUSTYQUIZZ_RECETTE_PORT ?? 18080);
const jar = resolve(homedir(), '.m2/repository/org/webjars/npm/stomp__stompjs/7.1.1/stomp__stompjs-7.1.1.jar');
const stomp = execFileSync('unzip', ['-p', jar, 'META-INF/resources/webjars/stomp__stompjs/7.1.1/bundles/stomp.umd.min.js']);
const types = { '.html': 'text/html', '.mjs': 'text/javascript', '.js': 'text/javascript', '.css': 'text/css',
	'.woff2': 'font/woff2', '.svg': 'image/svg+xml' };
// Fichiers synthétiques locaux pour la recette du dossier et du lecteur réel.
const dossierRecette = resolve('target/issue-112/recette');
async function synthetiserWav(chemin, dureeMs) {
	const frequence = 8000, nombre = Math.round(dureeMs * frequence / 1000);
	const wav = Buffer.alloc(44 + nombre * 2);
	wav.write('RIFF'); wav.writeUInt32LE(wav.length - 8, 4); wav.write('WAVEfmt ', 8);
	wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22);
	wav.writeUInt32LE(frequence, 24); wav.writeUInt32LE(frequence * 2, 28);
	wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34); wav.write('data', 36); wav.writeUInt32LE(nombre * 2, 40);
	for (let i = 0; i < nombre; i++) wav.writeInt16LE(Math.round(1500 * Math.sin(2 * Math.PI * 330 * i / frequence)), 44 + i * 2);
	await writeFile(chemin, wav);
}
const dossiers = Object.fromEntries(['unique', 'absent', 'ambigu'].map(nom => [nom, resolve(dossierRecette, nom)]));
for (const dossier of Object.values(dossiers)) await mkdir(dossier, { recursive: true });
const nomFichier = 'Les Néons - Le Dernier Métro.wav';
await mkdir(resolve(dossiers.ambigu, 'Autre version'), { recursive: true });
await Promise.all([
	synthetiserWav(resolve(dossiers.unique, nomFichier), 6000),
	synthetiserWav(resolve(dossiers.absent, 'Une autre Chanson.wav'), 1000),
	synthetiserWav(resolve(dossiers.ambigu, nomFichier), 1000),
	synthetiserWav(resolve(dossiers.ambigu, 'Autre version', 'Le Dernier Métro - Les Néons.WAV'), 1000),
	writeFile(resolve(dossiers.unique, 'lisez-moi.txt'), 'Dossier synthétique de recette\n'),
]);

createServer(async (requete, reponse) => {
	const chemin = new URL(requete.url, 'http://localhost').pathname;
	try {
		let contenu;
		if (chemin === '/webjars/stomp__stompjs/bundles/stomp.umd.min.js') contenu = stomp;
		else if (chemin === '/test/identite.html') contenu = await readFile('src/main/resources/demonstration/identite.html');
		else {
			const fichier = resolve(racine, '.' + chemin);
			if (!fichier.startsWith(racine + '/')) throw new Error('Chemin invalide');
			contenu = await readFile(fichier);
		}
		reponse.writeHead(200, { 'Content-Type': types[extname(chemin)] ?? 'application/octet-stream', 'Cache-Control': 'no-store' });
		reponse.end(contenu);
	} catch {
		reponse.writeHead(404); reponse.end();
	}
}).listen(port, '127.0.0.1', () => console.log(`Recette : http://127.0.0.1:${port}/test/identite.html`));
