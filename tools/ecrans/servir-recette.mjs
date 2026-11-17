// Serveur statique local pour la recette Playwright, sans Spring ni PostgreSQL.
// node tools/ecrans/servir-recette.mjs ; les simulations réseau sont dans recette-navigateur.mjs.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { homedir } from 'node:os';
import { resolve, extname } from 'node:path';

const racine = resolve('src/main/resources/static');
const jar = resolve(homedir(), '.m2/repository/org/webjars/npm/stomp__stompjs/7.1.1/stomp__stompjs-7.1.1.jar');
const stomp = execFileSync('unzip', ['-p', jar, 'META-INF/resources/webjars/stomp__stompjs/7.1.1/bundles/stomp.umd.min.js']);
const types = { '.html': 'text/html', '.mjs': 'text/javascript', '.js': 'text/javascript', '.css': 'text/css',
	'.woff2': 'font/woff2', '.svg': 'image/svg+xml' };
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
}).listen(18080, '127.0.0.1', () => console.log('Recette : http://127.0.0.1:18080/test/identite.html'));
