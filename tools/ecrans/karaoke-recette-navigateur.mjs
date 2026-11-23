// node tools/ecrans/servir-recette.mjs
// playwright-cli open
// playwright-cli run-code --filename=tools/ecrans/karaoke-recette-navigateur.mjs
// Vrais HTML/CSS/modules/WebJar, frontières HTTP et STOMP simulées, aucun accès externe.
async page => {
	const url = page.url();
	const base = url.startsWith('http://127.0.0.1:') ? new URL(url).origin : 'http://127.0.0.1:18080';
	page = await page.context().newPage();
	const dossierRecette = 'target/issue-112/recette';
	const soireeId = '00000001-0000-4000-8000-000000000000';
	let mancheId = '00000002-0000-4000-8000-000000000000';
	const ids = [3, 4, 5].map(n => `0000000${n}-0000-4000-8000-000000000000`);
	const chanson = { chansonId: 'chanson-demonstration', titre: 'Le Dernier Métro', artiste: 'Les Néons', album: 'Démonstration', dureeMs: 208000, etatParoles: 'SYNCHRONISEES' };
	const joueurs = ids.map((id, i) => ({ id, pseudo: ['Jo', 'Léa', 'Sam'][i], connecte: true }));
	let passages = [12, 13, 14, 15].map((numero, i) => ({ passageId: `000000${numero}-0000-4000-8000-000000000000`,
		numero, numeroModification: 0, position: i + 1, tour: i === 3 ? 2 : 1, prioritaire: i === 2,
		chanson: { ...chanson, chansonId: `demo-${numero}` }, interpretes: [{ joueurId: ids[i % 3], pseudo: joueurs[i % 3].pseudo }],
		attente: { etat: 'ESTIMEE', dureeMs: i * 208000, calculeeLe: '2026-11-17T20:00:00Z' } }));
	let limite = 3, terminee = false, confirmationRetardee, perdreConfirmation = true;
	let courant = null, lecteurDesigneId = null, lecture = null, fichierLocal = null, etatManche = 'EN_COURS';
	const signaux = [], commandes = [];
	const maintenant = () => new Date().toISOString();
	const actif = () => courant && ['APPELE', 'EN_COURS', 'EN_PAUSE'].includes(courant.etat);
	const position = () => courant.positionChronologiqueMs + (etatManche === 'EN_PAUSE' ? 0 : Date.now() - Date.parse(courant.calculeLe));
	const paroles = { parolesId: '00000020-0000-4000-8000-000000000000', dureeMs: 800, lignes: [
		{ positionMs: 50, texte: 'Le train quitte le quai' },
		{ positionMs: 250, texte: 'Une lumière file' },
		{ positionMs: 500, texte: 'La ville écoute encore' },
	] };
	const clients = [], erreurs = [], captures = [];
	const verifier = (condition, texte) => { if (!condition) throw new Error(texte); };
	function accuse(cle, action, passage) {
		const commun = { envoiId: cle, soireeId, mancheId, action, recueLe: '2026-11-17T20:00:00Z' };
		if (action === 'REGLER_LIMITE') return { ...commun, passage: null };
		if (action === 'DEFINIR_PRIORITE') return { ...commun, passage: { passageId: passage.passageId, numeroModification: passage.numeroModification } };
		return { ...commun, passageId: passage.passageId, numeroModification: passage.numeroModification,
			etatApres: ['RETIRER', 'DESINSCRIRE'].includes(action) ? 'RETIRE' : 'EN_ATTENTE' };
	}
	function projection(client) {
		const commun = { soiree: { id: soireeId, titre: 'Soirée de démonstration', format: 'SOLO', pilotage: 'MANUEL', etat: 'EN_COURS', codeAcces: 'DEMO01' },
			mancheActive: terminee ? null : { id: mancheId, ordre: 0, mode: 'KARAOKE', etat: etatManche }, modeEnCours: terminee ? null : 'KARAOKE',
			joueurs, classement: [], dernierBuzz: null };
		const personnels = passages.filter(p => p.interpretes.some(i => i.joueurId === client.identifiant));
		const bloc = { mancheId, etat: etatManche, courant, limitePassagesEnAttente: limite, nombreEnAttente: passages.length, tourOuvert: 1,
			passagePrioritaireId: passages.find(p => p.prioritaire)?.passageId ?? null,
			configurationInterface: { longueurRechercheMax: 120, delaiConsultationNavigateurMs: 8000, delaiConfirmationNavigateurMs: 100 } };
		if (client.type === 'ANIMATEUR') return { ...commun, programme: [commun.mancheActive].filter(Boolean), karaoke: terminee ? null :
			{ ...bloc, passagesEnAttente: passages, peutGererFile: true, peutReglerLimite: true, peutRechercher: true,
				peutAppeler: !actif() && etatManche === 'EN_COURS' && passages.length > 0, peutPasser: Boolean(actif()),
				peutReprendre: etatManche === 'EN_PAUSE' && lecture?.etat !== 'EN_ERREUR', peutReprendreEnParolesSeules: lecture?.etat === 'EN_ERREUR',
				peutCaler: courant?.etat === 'EN_COURS' && lecture?.etat === 'DEMARREE', lecteurDesigne: lecteurDesigneId !== null, lecture, fichierLocal } };
		const suite = { prochainsPassages: passages.slice(0, 3), nombreAutresPassages: Math.max(0, passages.length - 3) };
		if (client.type === 'ECRAN') return { ...commun, karaoke: terminee ? null : { ...bloc, ...suite, lecteurDesigneId, lecture,
			paroles: actif() && courant.etat !== 'APPELE' ? paroles : null } };
		return { ...commun, joueurId: client.identifiant, eligiblePourMancheActive: true, derniereReponse: null, resultatPersonnel: null,
			karaoke: terminee ? null : { ...bloc, ...suite, mesPassagesEnAttente: personnels, nombrePersonnelEnAttente: personnels.length,
				peutConfirmerPresence: etatManche === 'EN_COURS' && courant?.etat === 'APPELE' && courant.interpretes.some(i => i.joueurId === client.identifiant),
				peutRechercher: true, peutInscrire: personnels.length < limite, motifInscriptionRefusee: personnels.length < limite ? null : 'LIMITE_ATTEINTE' } };
	}
	function publier(type = 'file-attente-modifiee') {
		for (const c of clients) if (c.socket && c.abonnement) {
			c.revision++;
			c.socket.send(`MESSAGE\nsubscription:${c.abonnement}\nmessage-id:${c.revision}\ndestination:/test\n\n${JSON.stringify({ revision: c.revision, type, soireeId, donnees: projection(c) })}\0`);
		}
	}
	function fermerPassage(etat) {
		courant = { ...courant, etat, finPrevueLe: null, positionChronologiqueMs: null, clotureLe: maintenant() };
		lecture = fichierLocal = null;
	}
	function traiterPassage(chemin, c, corps, cible) {
		const operation = chemin.split('/').at(-1);
		if (c.type === 'ECRAN') {
			verifier(['lecteur', 'lecture', 'fin-lecture', 'erreur-lecture', 'position'].includes(operation), 'Commandes du lecteur uniquement');
		}
		switch (operation) {
			case 'lecteur':
				if (courant?.etat === 'APPELE' && lecteurDesigneId !== corps.lecteurId) lecture = fichierLocal = null;
				lecteurDesigneId = corps.lecteurId;
				return 'lecture-chanson-modifiee';
			case 'appel': {
				verifier(c.type === 'ANIMATEUR' && cible.position === 1 && cible.numeroModification === corps.numeroModification, 'Appel du premier Passage');
				passages.shift();
				passages = passages.map((p, i) => ({ ...p, position: i + 1 }));
				const { tour, position, prioritaire, attente, ...infos } = cible;
				courant = { ...infos, etapeId: crypto.randomUUID(), etat: 'APPELE', numeroModification: cible.numeroModification + 1,
					appeleLe: maintenant(), calculeLe: maintenant(), echeanceAppelLe: new Date(Date.now() + 15000).toISOString(),
					delaiAppelRestantMs: 15000, demarreLe: null, finPrevueLe: null, positionChronologiqueMs: null };
				return 'passage-appele';
			}
			case 'lecture':
				verifier(c.type === 'ECRAN' && corps.lecteurId === lecteurDesigneId && courant.etat === 'APPELE', 'Déclaration réservée au lecteur pendant l’appel');
				verifier(corps.etapeId === courant.etapeId && corps.numeroModification === courant.numeroModification, 'Compteur et étape de déclaration');
				fichierLocal = corps.fichier;
				if (fichierLocal) verifier(Object.keys(fichierLocal).sort().join() === 'dureeMs,nom,tailleOctets' && !fichierLocal.nom.includes('/'), 'Seul le nom exact du fichier part');
				lecture = { lectureId: crypto.randomUUID(), lecteurId: corps.lecteurId, source: corps.source,
					motifParolesSeules: corps.motifParolesSeules, etat: 'PREPAREE', origineParoles: 'INSCRIPTION',
					decalageMs: 0, numeroModificationCalage: 0, demandePosition: null };
				return 'lecture-chanson-modifiee';
			case 'presence':
				verifier(c.type === 'JOUEUR' && courant.interpretes.some(i => i.joueurId === c.identifiant), 'Confirmation par l’Interprète');
				verifier(corps.etapeId === courant.etapeId && corps.numeroModification === courant.numeroModification, 'Demande de présence pour l’étape affichée');
				courant = { ...courant, etat: 'EN_COURS', demarreLe: maintenant(), calculeLe: maintenant(),
					delaiAppelRestantMs: null, echeanceAppelLe: null, finPrevueLe: new Date(Date.now() + 45000).toISOString(), positionChronologiqueMs: 0 };
				lecture = lecture ? { ...lecture, etat: 'DEMARREE' } : { lectureId: crypto.randomUUID(), lecteurId: null,
					etat: 'DEMARREE', source: 'PAROLES_SEULES', motifParolesSeules: 'ECRAN_NON_PRET', decalageMs: 0, numeroModificationCalage: 0 };
				return 'passage-demarre';
			case 'commandes':
				verifier(c.type === 'ANIMATEUR' && corps.etapeId === (actif() ? courant.etapeId : undefined), 'Étape de pilotage selon le courant actif');
				if (corps.action === 'TERMINER') { terminee = true; return 'manche-terminee'; }
				if (corps.action === 'PASSER_ETAPE') { fermerPassage('SAUTE'); return 'passage-saute'; }
				if (corps.action === 'METTRE_EN_PAUSE') {
					const tempsRestant = courant?.etat === 'APPELE' ? Math.max(0, Date.parse(courant.echeanceAppelLe) - Date.now()) : null;
					if (actif()) courant = { ...courant, etat: tempsRestant === null ? 'EN_PAUSE' : 'APPELE', calculeLe: maintenant(),
						delaiAppelRestantMs: tempsRestant, echeanceAppelLe: null, finPrevueLe: null,
						positionChronologiqueMs: tempsRestant === null ? position() : null };
					etatManche = 'EN_PAUSE';
					return 'manche-mise-en-pause';
				}
				etatManche = 'EN_COURS';
				if (courant?.etat === 'APPELE') courant = { ...courant, calculeLe: maintenant(), echeanceAppelLe: new Date(Date.now() + courant.delaiAppelRestantMs).toISOString() };
				else if (actif()) courant = { ...courant, etat: 'EN_COURS', calculeLe: maintenant(), finPrevueLe: new Date(Date.now() + 45000).toISOString() };
				return 'manche-reprise';
			case 'debut':
				lecture = { ...lecture, demandePosition: { demandeId: crypto.randomUUID(), demandeeLe: maintenant() } };
				return 'calage-paroles-modifie';
			case 'position':
				verifier(corps.demandeId === lecture.demandePosition?.demandeId, 'Demande de position unique');
				signaux.push({ operation, c, corps });
				lecture = { ...lecture, decalageMs: corps.positionMs - 50, numeroModificationCalage: lecture.numeroModificationCalage + 1, demandePosition: null };
				return 'calage-paroles-modifie';
			case 'correction':
				lecture = { ...lecture, decalageMs: lecture.decalageMs + (corps.sens === 'RETARDER' ? 100 : -100),
					numeroModificationCalage: lecture.numeroModificationCalage + 1 };
				return 'calage-paroles-modifie';
			case 'fin-lecture':
				signaux.push({ operation, c, corps, finPrevueLe: courant.finPrevueLe, signaleLe: maintenant() });
				fermerPassage('TERMINE');
				return 'passage-termine';
			case 'erreur-lecture':
				signaux.push({ operation, c, corps });
				courant = { ...courant, etat: 'EN_PAUSE', calculeLe: maintenant(), positionChronologiqueMs: position(), finPrevueLe: null };
				etatManche = 'EN_PAUSE';
				lecture = { ...lecture, etat: 'EN_ERREUR' };
				return 'lecture-chanson-modifiee';
			case 'paroles-seules':
				lecture = { ...lecture, source: 'PAROLES_SEULES', motifParolesSeules: 'ERREUR_LECTURE', etat: 'DEMARREE' };
				fichierLocal = null;
				etatManche = 'EN_COURS';
				courant = { ...courant, etat: 'EN_COURS', calculeLe: maintenant() };
				return 'lecture-chanson-modifiee';
			default: return null;
		}
	}
	async function installer(onglet, type, identifiant, ecran) {
		const c = { onglet, type, identifiant, revision: 0, captures: 0 }; clients.push(c);
		onglet.on('pageerror', e => erreurs.push(e.message));
		await onglet.route('**/*', async route => {
			if (!route.request().url().startsWith(base + '/')) { await route.abort(); throw new Error('Accès externe interdit dans la recette.'); }
			await route.continue();
		});
		await onglet.routeWebSocket('**/ws', socket => {
			c.socket = socket;
			socket.onMessage(message => {
				const trame = String(message);
				if (trame.startsWith('CONNECT')) socket.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
				if (trame.startsWith('SUBSCRIBE')) c.abonnement = /^id:(.*)$/m.exec(trame)[1];
			});
		});
		await onglet.route('**/api/**', async route => {
			const requete = route.request(), adresse = requete.url(), chemin = adresse.split('?')[0].slice(base.length);
			const parametre = nom => { const valeur = new RegExp(`[?&]${nom}=([^&]*)`).exec(adresse)?.[1]; return valeur ? decodeURIComponent(valeur.replace(/\+/g, ' ')) : null; };
			verifier(requete.headers().authorization === `Bearer demo:${type}:${soireeId}:${identifiant}`, 'Identité HTTP sur chaque requête');
			if (chemin.includes('/etat/')) {
				c.captures++;
				await route.fulfill({ json: { abonnementId: parametre('abonnementId'), revision: c.revision, heureServeur: maintenant(), vue: projection(c) } }); return;
			}
			if (requete.method() === 'GET') {
				if (parametre('recherche') === 'panne') {
					await route.fulfill({ status: 503, json: { type: 'about:blank', title: 'Source indisponible', status: 503, code: 'SOURCE_PAROLES_INDISPONIBLE', detail: 'La recherche est indisponible.' } }); return;
				}
				await route.fulfill({ json: chemin.endsWith('/chansons') ? { rechercheId: parametre('rechercheId'), recherche: parametre('recherche'), chansons: [chanson] } :
					{ chanson, inscriptionPossible: type === 'JOUEUR', motifIndisponibilite: type === 'JOUEUR' ? null : 'ANIMATEUR' } }); return;
			}
			const corps = requete.postDataJSON(), cle = corps.envoiId ?? requete.headers()['idempotency-key'];
			verifier(Boolean(cle), 'Clé d’envoi obligatoire');
			commandes.push({ c, chemin, corps, cle });
			let cible = passages.find(p => p.passageId === (corps.passage?.passageId ?? chemin.split('/').at(-2)));
			const evenementPassage = traiterPassage(chemin, c, corps, cible);
			if (evenementPassage) {
				publier(evenementPassage);
				await route.fulfill({ json: { envoiId: cle, lectureId: lecture?.lectureId ?? null } });
				return;
			}
			let action = 'DEPLACER';
			if (chemin.endsWith('/limite')) { limite = corps.limitePassagesEnAttente; action = 'REGLER_LIMITE'; }
			else if (chemin.endsWith('/priorite')) {
				action = 'DEFINIR_PRIORITE'; passages = passages.map(p => ({ ...p, prioritaire: corps.prioritaire && p.passageId === corps.passage.passageId, numeroModification: p.numeroModification + 1 }));
				cible = passages.find(p => p.passageId === corps.passage.passageId);
			} else if (/\/(retrait|desinscription)$/.test(chemin)) { action = chemin.endsWith('/retrait') ? 'RETIRER' : 'DESINSCRIRE'; passages = passages.filter(p => p.passageId !== cible.passageId); cible = { ...cible, numeroModification: cible.numeroModification + 1 }; }
			else if (chemin.endsWith('/passages')) {
				const p = { ...passages[0], passageId: '00000016-0000-4000-8000-000000000000', numero: 16, numeroModification: 0,
					position: passages.length + 1, tour: 3, prioritaire: false, chanson, interpretes: [{ joueurId: identifiant, pseudo: 'Jo' }],
					attente: { etat: 'ESTIMEE', dureeMs: passages.length * 208000, calculeeLe: '2026-11-17T20:00:00Z' } };
				passages.push(p); cible = p; action = 'INSCRIRE'; publier();
				if (perdreConfirmation) {
					perdreConfirmation = false;
					await new Promise(resolve => { confirmationRetardee = async () => { await route.fulfill({ status: 201, json: accuse(cle, 'INSCRIRE', p) }); resolve(); }; }); return;
				}
			}
			publier(chemin.endsWith('/limite') ? 'limite-passages-modifiee' : 'file-attente-modifiee');
			await route.fulfill({ status: action === 'INSCRIRE' ? 201 : 200, json: accuse(cle, action, cible) });
		});
		await onglet.addInitScript(({ soireeId, type, identifiant }) => sessionStorage.setItem('croustyquizz.jeton', `demo:${type}:${soireeId}:${identifiant}`), { soireeId, type, identifiant });
		if (type === 'ECRAN') await onglet.addInitScript(() => {
			const play = HTMLMediaElement.prototype.play;
			window.recetteAudio = { fichiers: [], refuserTemoin: false, refuserFichier: false };
			HTMLMediaElement.prototype.play = function () {
				if (this.src.startsWith('data:') && window.recetteAudio.refuserTemoin) {
					window.recetteAudio.refuserTemoin = false;
					return Promise.reject(new DOMException('Blocage simulé du son', 'NotAllowedError'));
				}
				if (this.src.startsWith('blob:')) {
					window.recetteAudio.fichiers.push(this);
					if (window.recetteAudio.refuserFichier) {
						window.recetteAudio.refuserFichier = false;
						return Promise.reject(new DOMException('Refus simulé du départ', 'NotAllowedError'));
					}
				}
				return play.call(this);
			};
		});
		await onglet.goto(`${base}/${ecran}.html?soiree=${soireeId}`);
		await onglet.waitForFunction(() => document.querySelector('[data-connexion]').textContent === 'Vue synchronisée');
		await onglet.locator('.karaoke').first().waitFor(); return c;
	}
	const consoleClient = await installer(page, 'ANIMATEUR', ids[0], 'console');
	const manettes = [];
	for (const id of ids) manettes.push(await installer(await page.context().newPage(), 'JOUEUR', id, 'manette'));
	const salle = await installer(await page.context().newPage(), 'ECRAN', ids[0], 'ecran');
	const manette = manettes[0].onglet;
	verifier(!(await manette.locator('[data-personnel]').innerText()).includes('Tu participeras'), 'Retardataire admis au Karaoké');
	verifier(await manette.locator('.mon-ticket').count() === 2, 'Deux Tours et tickets personnels multiples');
	await page.locator('button.ticket').first().focus(); await page.keyboard.press('Enter');
	verifier(await page.locator('button[aria-pressed=true]').count() === 1, 'Sélection clavier unique');
	await page.getByRole('button', { name: 'Priorité', exact: true }).click();
	await page.keyboard.press('Escape');
	verifier(await page.evaluate(() => document.activeElement?.textContent === 'Priorité'), 'Échap rend le focus');
	await page.getByRole('button', { name: 'Priorité', exact: true }).click(); await page.getByRole('button', { name: 'Confirmer', exact: true }).click();
	await page.waitForFunction(() => document.querySelector('button[aria-pressed=true]')?.textContent.includes('Prioritaire'));
	verifier(await page.evaluate(() => document.activeElement?.isConnected && document.activeElement !== document.body), 'Retour de focus après confirmation');
	await page.getByRole('button', { name: 'Retirer', exact: true }).focus(); limite = 4; publier('limite-passages-modifiee');
	await page.waitForFunction(() => document.querySelector('#karaoke-limite').value === '4');
	verifier(await page.evaluate(() => document.activeElement?.textContent === 'Retirer'), 'Événement reçu pendant le focus d’une commande');
	await page.locator('#karaoke-limite').fill('5'); publier();
	verifier(await page.locator('#karaoke-limite').inputValue() === '5', 'Vue reçue pendant la saisie');
	await page.getByRole('button', { name: 'Appliquer', exact: true }).click();
	await page.locator('[data-laterale] input').fill('panne'); await page.getByRole('button', { name: 'Chercher', exact: true }).click();
	await page.getByText('La recherche est indisponible.', { exact: true }).waitFor();
	await page.waitForFunction(() => document.querySelector('[data-connexion]').textContent === 'Vue synchronisée');
	await page.locator('[data-laterale] input').fill('métro'); await page.getByRole('button', { name: 'Chercher', exact: true }).click();
	await page.getByRole('button', { name: chanson.titre, exact: true }).click();
	verifier((await page.locator('dialog').innerText()).includes('Consultation uniquement'), 'Fiche Animateur sans inscription');
	await page.keyboard.press('Escape');
	await manette.locator('input').fill('métro'); await manette.getByRole('button', { name: 'Chercher', exact: true }).click();
	await manette.getByRole('button', { name: chanson.titre, exact: true }).click();
	await manette.getByRole('button', { name: "M'inscrire", exact: true }).click();
	await manette.waitForFunction(() => document.querySelectorAll('.mon-ticket').length === 3);
	await manette.getByRole('button', { name: 'Fermer', exact: true }).click();
	await confirmationRetardee(); // Le ticket est confirmé par vue avant le 201 tardif.
	verifier(await manette.locator('.mon-ticket').count() === 3, '201 tardif sans ticket dupliqué');
	const avant = consoleClient.captures;
	await page.getByRole('button', { name: 'Resynchroniser', exact: true }).click();
	await page.waitForFunction(() => document.querySelector('[data-connexion]').textContent === 'Vue synchronisée');
	verifier(consoleClient.captures > avant, 'Recapture effective');
	for (const [client, nom, tailles] of [[manettes[0], 'manette', [[390, 844]]], [consoleClient, 'console', [[1440, 900], [1200, 900], [1024, 768], [860, 900]]], [salle, 'ecran', [[1920, 1080]]]]) {
		for (const [width, height] of tailles) {
			await client.onglet.setViewportSize({ width, height }); await client.onglet.evaluate(() => document.fonts.ready);
			verifier(await client.onglet.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${nom} sans débordement à ${width}`);
			if (nom === 'ecran') verifier(await client.onglet.evaluate(() => document.querySelector('[data-talon]').getBoundingClientRect().bottom <= innerHeight), 'Talon visible sans défilement');
			const fichier = `${dossierRecette}/file-${nom}-${width}.png`; await client.onglet.screenshot({ path: fichier, fullPage: true }); captures.push(fichier);
		}
	}
	const dossiers = Object.fromEntries(['unique', 'absent', 'ambigu'].map(nom => [nom, `${dossierRecette}/${nom}`]));
	const nomFichier = `${chanson.artiste} - ${chanson.titre}.wav`;
	const ongletSalle = salle.onglet;
	const animateur2 = await installer(await page.context().newPage(), 'ANIMATEUR', ids[0], 'console');
	const salle2 = await installer(await page.context().newPage(), 'ECRAN', ids[0], 'ecran');
	const action = (onglet, name) => onglet.getByRole('button', { name, exact: true });
	const attendreTexte = (onglet, texte) => onglet.getByText(texte, { exact: true }).waitFor({ timeout: 5000 });
	const contraste = element => element.evaluate(e => {
		function luminance(couleur) {
			const [r, v, b] = couleur.match(/\d+/g).slice(0, 3).map(n => {
				const v = Number(n) / 255;
				return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
			});
			return 0.2126 * r + 0.7152 * v + 0.0722 * b;
		}
		let surface = e;
		while (getComputedStyle(surface).backgroundColor === 'rgba(0, 0, 0, 0)') surface = surface.parentElement;
		const niveaux = [luminance(getComputedStyle(e).color), luminance(getComputedStyle(surface).backgroundColor)].sort((a, b) => b - a);
		return (niveaux[0] + 0.05) / (niveaux[1] + 0.05);
	});
	async function choisirDossier(onglet, dossier) {
		const selecteur = onglet.waitForEvent('filechooser');
		await action(onglet, 'Choisir le dossier de musique').click();
		await (await selecteur).setFiles(dossier);
	}
	async function appeler() {
		const p = passages[0], joueur = manettes.find(c => c.identifiant === p.interpretes[0].joueurId).onglet;
		await action(page, 'Appeler le suivant').click();
		await joueur.locator('.mon-ticket--appele').waitFor({ timeout: 5000 });
		return joueur;
	}
	const confirmerPresence = joueur => action(joueur, 'Je suis prêt').click();

	await ongletSalle.evaluate(() => { window.recetteAudio.refuserTemoin = true; });
	await choisirDossier(ongletSalle, dossiers.unique);
	await attendreTexte(ongletSalle, '1 fichier audio');
	await attendreTexte(ongletSalle, 'Son bloqué');
	await attendreTexte(salle2.onglet, 'Cet écran affiche seulement.');
	const lecteurInitial = lecteurDesigneId;
	await ongletSalle.emulateMedia({ reducedMotion: 'reduce' });
	const premier = await appeler();
	verifier(await ongletSalle.locator('.karaoke__courant-salle .ticket__corps').evaluate(e => getComputedStyle(e).animationName === 'none'), 'Détachement supprimé en mouvement réduit');
	verifier(await ongletSalle.locator('.karaoke .ticket').count() === 4, 'Passage appelé et trois prochains tickets');
	const fileCourte = passages;
	passages = passages.map(p => ({ ...p, chanson: { ...p.chanson, titre: 'Chanson de démonstration '.repeat(8) } }));
	publier();
	await ongletSalle.waitForFunction(() => [...document.querySelectorAll('[data-laterale] .ticket__quoi')].some(e => e.scrollWidth > e.clientWidth));
	const placementSalle = await ongletSalle.evaluate(() => {
		const boites = [...document.querySelectorAll('[data-laterale] .ticket, .salle__cote .commun, [data-talon]')]
			.map(e => ({ classe: e.className, haut: Math.round(e.getBoundingClientRect().top), bas: Math.round(e.getBoundingClientRect().bottom) }));
		return { visible: document.querySelector('[data-talon]').getBoundingClientRect().bottom <= innerHeight, boites };
	});
	const fichierSalle = `${dossierRecette}/passage-ecran-1920.png`;
	await ongletSalle.screenshot({ path: fichierSalle });
	captures.push(fichierSalle);
	verifier(placementSalle.visible, 'Talon visible avec quatre tickets à 1920 × 1080 : ' + JSON.stringify(placementSalle.boites));
	passages = fileCourte; publier();
	const boutonPresence = action(premier, 'Je suis prêt');
	verifier(await contraste(boutonPresence) >= 4.5, 'Contraste de l’action citron sur le ticket appelé');
	await boutonPresence.scrollIntoViewIfNeeded();
	const { x, y, width, height } = await boutonPresence.boundingBox();
	const tactile = await premier.context().newCDPSession(premier);
	await tactile.send('Emulation.setTouchEmulationEnabled', { enabled: true });
	await tactile.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: x + width / 2, y: y + height / 2 }] });
	await tactile.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
	await tactile.send('Emulation.setTouchEmulationEnabled', { enabled: false });
	await tactile.detach();
	await attendreTexte(page, 'Paroles seules : Écran lecteur pas prêt');
	for (const width of [1440, 1200, 1024, 860]) {
		await page.setViewportSize({ width, height: 900 });
		verifier(await page.locator('.karaoke__barre-passage').evaluate(barre => barre.scrollWidth <= barre.clientWidth && document.documentElement.scrollWidth <= innerWidth),
			`Barre de Passage et de calage sans débordement à ${width} px`);
	}
	const fichierConsole = `${dossierRecette}/passage-console-860.png`;
	await page.screenshot({ path: fichierConsole, fullPage: true }); captures.push(fichierConsole);
	await action(page, 'Passer').click();
	await action(ongletSalle, 'Activer le son').focus();
	await ongletSalle.keyboard.press('Enter');
	await action(ongletSalle, 'Activer le son').waitFor({ state: 'hidden', timeout: 5000 });
	verifier(await action(ongletSalle, 'Choisir le dossier de musique').evaluate(e => e === document.activeElement), 'Le clavier revient au dossier après déblocage');
	await ongletSalle.emulateMedia({ reducedMotion: 'no-preference' });

	const suivant = await appeler();
	await attendreTexte(page, 'Fichier local : ' + nomFichier);
	verifier(fichierLocal.dureeMs === 6000, 'Métadonnées du WAV local');
	verifier(await ongletSalle.evaluate(() => window.recetteAudio.fichiers.length === 0), 'Aucun fichier joué pendant l’appel');
	verifier(!(await ongletSalle.locator('.karaoke').first().innerText()).includes(nomFichier), 'Pas de nom de fichier sur la salle');
	await action(page, 'Pause').click();
	await attendreTexte(suivant, 'Pause : attends la reprise.');
	const tempsFige = await ongletSalle.locator('.karaoke__delai').innerText();
	await action(page, 'Reprendre').click();
	await action(suivant, 'Je suis prêt').waitFor();
	verifier(tempsFige.length > 0, 'Temps d’appel visible en pause');
	courant = { ...courant, calculeLe: new Date(Date.now() - 2000).toISOString(), delaiAppelRestantMs: 1000,
		echeanceAppelLe: new Date(Date.now() - 1000).toISOString() };
	publier('lecture-chanson-modifiee');
	await suivant.setViewportSize({ width: 390, height: 844 });
	await attendreTexte(suivant, 'Le temps est écoulé, tu peux encore confirmer.');
	const fichierManette = `${dossierRecette}/passage-manette-390.png`;
	await suivant.screenshot({ path: fichierManette, fullPage: true }); captures.push(fichierManette);
	await confirmerPresence(suivant);
	await ongletSalle.waitForFunction(() => window.recetteAudio.fichiers.at(-1)?.currentTime > 0.1);
	const positionAudio = () => ongletSalle.evaluate(() => window.recetteAudio.fichiers.at(-1).currentTime);
	await action(page, 'Pause').click();
	await attendreTexte(ongletSalle, 'Pause');
	await ongletSalle.waitForFunction(() => window.recetteAudio.fichiers.at(-1)?.paused);
	const positionFigee = await positionAudio();
	verifier(await positionAudio() === positionFigee, 'Pause locale sans remise à zéro');
	await action(page, 'Reprendre').click();
	await ongletSalle.waitForFunction(position => window.recetteAudio.fichiers.at(-1)?.currentTime > position, positionFigee);
	await action(animateur2.onglet, 'ça commence').click();
	await page.waitForFunction(() => !document.querySelector('.karaoke__passage-courant').textContent.includes('Mesure en attente') &&
		!document.querySelector('.karaoke__passage-courant').textContent.includes('Décalage : 0,0 s'));
	const decalageMesure = lecture.decalageMs;
	verifier(signaux.filter(s => s.operation === 'position').length === 1, 'Relevé local unique depuis une autre Console');
	await action(animateur2.onglet, 'Retarder les paroles de 0,1 s').click();
	await page.waitForFunction(() => !document.querySelector('.karaoke__barre-passage').textContent.includes('Envoi en cours'));
	verifier(lecture.decalageMs === decalageMesure + 100, 'Correction appliquée par la vue');
	await attendreTexte(ongletSalle, 'La ville écoute encore');
	await ongletSalle.waitForFunction(() => document.querySelector('.paroles__chante')?.textContent === 'La ville écoute encore');
	await ongletSalle.waitForFunction(() => window.recetteAudio.fichiers.at(-1)?.currentTime > 0.8);
	verifier(await positionAudio() > 0.8, 'Le fichier continue à jouer après la durée des paroles');
	verifier(await contraste(ongletSalle.locator('.paroles__chante')) >= 3, 'Contraste de la ligne chantée');
	const fichierParoles = `${dossierRecette}/paroles-ecran-1920.png`;
	await ongletSalle.screenshot({ path: fichierParoles }); captures.push(fichierParoles);

	const appelsSalle2 = commandes.filter(e => e.c === salle2).length;
	await salle2.onglet.reload();
	await attendreTexte(salle2.onglet, 'Cet écran affiche seulement.');
	verifier(await salle2.onglet.evaluate(() => window.recetteAudio.fichiers.length === 0), 'Capture d’une fenêtre rouverte sans son');
	verifier(await salle2.onglet.locator('.ticket--immobile').count() === 1, 'Capture immobile');
	verifier(commandes.filter(e => e.c === salle2).length === appelsSalle2, 'Aucun signal de la fenêtre rouverte');
	await choisirDossier(salle2.onglet, dossiers.absent);
	await attendreTexte(ongletSalle, 'Cet écran affiche seulement.');
	verifier(lecteurDesigneId !== lecteurInitial && await salle2.onglet.evaluate(() => window.recetteAudio.fichiers.length === 0),
		'La nouvelle désignation ne joue pas le Passage déjà engagé');
	try {
		await page.getByText('Terminé', { exact: true }).waitFor({ timeout: 12000 });
	} catch (erreur) {
		const audio = await ongletSalle.evaluate(() => {
			const fichier = window.recetteAudio.fichiers.at(-1);
			return { position: fichier?.currentTime, pause: fichier?.paused, fin: fichier?.ended };
		});
		throw new Error('Fin locale non reçue. Lecteur : ' + JSON.stringify(audio) + '. Erreurs des pages : ' + erreurs.join(', '), { cause: erreur });
	}
	const finLocale = signaux.find(s => s.operation === 'fin-lecture');
	verifier(finLocale.c === salle && finLocale.corps.positionMs === 6000 && finLocale.signaleLe < finLocale.finPrevueLe,
		'Le lecteur d’origine joue jusqu’au bout et clôt avant la fin prévue');

	for (const [dossier, nombre, texte] of [[dossiers.absent, '1 fichier audio', 'Paroles seules : aucun fichier trouvé'],
		[dossiers.ambigu, '2 fichiers audio', 'Paroles seules : plusieurs fichiers correspondent']]) {
		await choisirDossier(salle2.onglet, dossier);
		await attendreTexte(salle2.onglet, nombre);
		const joueur = await appeler();
		await attendreTexte(page, texte);
		await confirmerPresence(joueur);
		await salle2.onglet.locator('.paroles__ligne').first().waitFor();
		verifier(await salle2.onglet.evaluate(() => window.recetteAudio.fichiers.length === 0), 'Paroles seules sans son');
		await action(page, 'Passer').click();
	}
	await choisirDossier(salle2.onglet, dossiers.unique);
	const dernier = await appeler();
	await attendreTexte(page, 'Fichier local : ' + nomFichier);
	await salle2.onglet.evaluate(() => { window.recetteAudio.refuserFichier = true; });
	await confirmerPresence(dernier);
	await attendreTexte(salle2.onglet, "La musique ne se lance pas. L'Animateur choisit la suite.");
	await attendreTexte(page, 'Erreur de lecture : reprends en paroles seules ou passe le Passage.');
	verifier(await action(page, 'Reprendre').isDisabled(), 'Reprise musicale désactivée après une erreur');
	await action(page, 'Paroles seules').click();
	await attendreTexte(page, 'Paroles seules après une erreur du fichier');
	await salle2.onglet.locator('.paroles__ligne').first().waitFor();
	verifier(signaux.filter(s => s.operation === 'erreur-lecture').length === 1, 'Signal d’erreur unique');
	const lecteurSuivant = lecteurDesigneId;
	const mancheSuivante = '00000009-0000-4000-8000-000000000000';
	mancheId = mancheSuivante; courant = lecture = fichierLocal = lecteurDesigneId = null;
	publier('manche-demarree');
	await attendreTexte(page, 'Écran lecteur prêt');
	verifier(lecteurDesigneId === lecteurSuivant && commandes.filter(e => e.chemin.endsWith(`/manches/${mancheSuivante}/lecteur`)).length === 1,
		'Seule la fenêtre lectrice se redésigne sans choisir de dossier');
	const clesSalle = commandes.filter(e => e.c.type === 'ECRAN').map(e => e.cle);
	verifier(new Set(clesSalle).size === clesSalle.length, 'Clé neuve pour chaque commande de lecteur');
	verifier(await ongletSalle.locator('[data-envoi]').isHidden() && await salle2.onglet.locator('[data-envoi]').isHidden(), 'Aucun bandeau d’envoi sur la salle');
	verifier(await salle.onglet.locator('[data-talon]').innerText().then(t => t.includes('DEMO01')), 'Talon intact');
	await action(page, 'Terminer la Manche').click();
	await action(page, 'Confirmer').click();
	for (const c of clients) {
		await c.onglet.waitForFunction(() => document.querySelectorAll('link[data-mode=karaoke]').length === 0);
		verifier(await c.onglet.locator('[data-principale]').innerText() === '', 'Démontage des zones');
	}
	verifier(erreurs.length === 0, erreurs.join('\n'));
	for (const c of clients) if (c.onglet !== page) await c.onglet.close();
	return { resultat: 'File d’attente et Passage, WAV local, calage, pauses, rattrapages, erreur et Manche suivante vérifiés.', captures };
}
