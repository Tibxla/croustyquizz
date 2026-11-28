// Avec l'application de développement sur le port 8080 et playwright-cli open :
// playwright-cli run-code --filename=tools/ecrans/compte-recette-navigateur.mjs
async page => {
	const contexte = await page.context().browser().newContext({ viewport: { width: 390, height: 844 } });
	try {
		const navigateur = await contexte.newPage();
		const base = 'http://localhost:8080';
		const preparation = '/preparer-soiree.html';
		const soirees = '/soirees.html';
		const motDePasse = 'un mot de passe de recette';
		const email = `recette-${Date.now()}@exemple.test`;
		const chercher = selecteur => navigateur.locator(selecteur);
		const bouton = nom => navigateur.getByRole('button', { name: nom, exact: true });
		const remplirInscription = async motDePasseSaisi => {
			for (const [id, valeur] of Object.entries({
				lieu: 'Le Balcon de recette', ville: 'Nanterre', nom: 'Camille', email, 'mot-de-passe': motDePasseSaisi,
			})) {
				await chercher(`#${id}`).fill(valeur);
			}
		};
		const verifierErreur = async id => navigateur.waitForFunction(champId => {
			const champ = document.getElementById(champId);
			const erreur = document.getElementById(`${champId}-erreur`);
			return document.activeElement === champ && champ.getAttribute('aria-invalid') === 'true'
				&& champ.getAttribute('aria-describedby').split(' ').includes(erreur.id)
				&& erreur.classList.contains('champ--erreur') && !erreur.hidden && erreur.textContent.length > 0;
		}, id);

		await navigateur.goto(base + preparation);
		await navigateur.waitForURL(adresse => adresse.pathname === '/connexion.html'
			&& adresse.searchParams.get('retour') === preparation);
		await navigateur.getByRole('link', { name: 'Créer un compte' }).click();
		await bouton('Créer le compte').click();
		await verifierErreur('lieu');
		await remplirInscription('trop court');
		await bouton('Créer le compte').click();
		await verifierErreur('mot-de-passe');
		await chercher('#mot-de-passe').fill(motDePasse);
		await bouton('Créer le compte').click();
		await navigateur.waitForURL(base + soirees);
		const accueil = navigateur.getByText('Ton Lieu est créé.', { exact: true });
		const listeVide = navigateur.getByText('Aucune Soirée pour ce Lieu. Prépare la première.', { exact: true });
		await accueil.waitFor({ state: 'visible' });
		await listeVide.waitFor({ state: 'visible' });
		await navigateur.reload();
		await listeVide.waitFor({ state: 'visible' });
		await accueil.waitFor({ state: 'hidden' });
		await bouton('Compte de Camille').click();
		await bouton('Se déconnecter').click();
		await navigateur.waitForURL(adresse => adresse.pathname === '/connexion.html'
			&& adresse.searchParams.get('retour') === soirees);
		await navigateur.getByRole('link', { name: 'Créer un compte' }).click();
		await remplirInscription(motDePasse);
		await bouton('Créer le compte').click();
		await verifierErreur('email');

		await navigateur.goto(base + preparation);
		await navigateur.waitForURL(adresse => adresse.pathname === '/connexion.html'
			&& adresse.searchParams.get('retour') === preparation);
		await chercher('#email').fill(email);
		await chercher('#mot-de-passe').fill('mauvais mot de passe');
		await bouton('Se connecter').click();
		await verifierErreur('mot-de-passe');
		await chercher('#mot-de-passe').fill(motDePasse);
		await bouton('Se connecter').click();
		await navigateur.waitForURL(base + preparation);

		await chercher('#titre').fill('Soirée de recette du compte');
		await bouton('Créer la Soirée').click();
		await bouton('Ouvrir aux Joueurs').click();
		await navigateur.getByRole('link', { name: 'Ouvrir la Console d’animation' }).click();
		await navigateur.waitForFunction(() => document.querySelector('[data-connexion]')?.textContent === 'Vue synchronisée');
		const consoleDemandee = new URL(navigateur.url());
		await bouton('Se déconnecter').click();
		await navigateur.waitForURL(adresse => adresse.pathname === '/connexion.html'
			&& adresse.searchParams.get('retour') === consoleDemandee.pathname + consoleDemandee.search);
		return 'Session ouverte à l’inscription, accueil, rechargement, erreurs par champ, connexion, Console synchronisée et déconnexion vérifiés.';
	} finally {
		await contexte.close();
	}
}
