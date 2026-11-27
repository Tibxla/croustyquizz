// Frontière DOM des modules ; aucun navigateur ni réseau dans node --test.
export function creerDocument() {
	class Element {
		constructor(tag) {
			this.tagName = tag.toUpperCase();
			// Liens vers le haut non énumérables : un message d'échec d'assert ne parcourt que les enfants (18 Go le 24/11, #120).
			Object.defineProperties(this, { ownerDocument: { value: document, writable: true }, parentNode: { value: null, writable: true } });
			this.children = []; this.attributes = new Map();
			this.ecouteurs = new Map(); this.hidden = false; this.disabled = false; this.value = ''; this.open = false; this.texte = '';
			const proprietes = new Map(); this.style = { setProperty: (k, v) => proprietes.set(k, v), getPropertyValue: k => proprietes.get(k) ?? '' };
			this.classList = { contains: c => this.className.split(/\s+/).includes(c), add: c => { if (!this.classList.contains(c)) this.className += ` ${c}`; }, remove: c => { this.className = this.className.split(/\s+/).filter(x => x !== c).join(' '); }, toggle: (c, force) => { const ajouter = force ?? !this.classList.contains(c); this.classList[ajouter ? 'add' : 'remove'](c); return ajouter; } };
			this.className = '';
		}
		get isConnected() { return this === document.head || this === document.body || Boolean(this.parentNode?.isConnected); }
		get textContent() { return this.texte + this.children.map(e => e.textContent).join(''); }
		set textContent(t) { this.replaceChildren(); this.texte = String(t); }
		get childElementCount() { return this.children.length; }
		setAttribute(k, v) { this.attributes.set(k, String(v)); if (k === 'id') this.id = String(v); if (k === 'class') this.className = String(v); }
		getAttribute(k) { return k === 'class' ? this.className : this.attributes.get(k) ?? null; }
		removeAttribute(k) { this.attributes.delete(k); }
		append(...enfants) { for (const e of enfants) { e.remove(); this.children.push(e); e.parentNode = this; } }
		appendChild(e) { this.append(e); return e; }
		replaceChildren(...enfants) { for (const e of this.children) e.parentNode = null; this.children = []; this.texte = ''; this.append(...enfants); }
		remove() { if (this.parentNode) this.parentNode.children = this.parentNode.children.filter(e => e !== this); this.parentNode = null; }
		contains(cible) { return this === cible || this.children.some(e => e.contains(cible)); }
		matches(selecteur) {
			if (selecteur.includes(',')) return selecteur.split(',').some(s => this.matches(s.trim()));
			const attr = /\[([^=\]]+)(?:=["']?([^"'\]]+)["']?)?\]/g;
			for (const m of selecteur.matchAll(attr)) if (this.getAttribute(m[1]) === null || (m[2] !== undefined && this.getAttribute(m[1]) !== m[2])) return false;
			const classe = selecteur.match(/\.([\w-]+)/)?.[1]; if (classe && !this.classList.contains(classe)) return false;
			const identifiant = selecteur.match(/#([\w-]+)/)?.[1]; if (identifiant && this.id !== identifiant) return false;
			const tag = selecteur.match(/^[\w-]+/)?.[0]; return !tag || this.tagName === tag.toUpperCase();
		}
		querySelectorAll(s) { return this.children.flatMap(e => [...(e.matches(s) ? [e] : []), ...e.querySelectorAll(s)]); }
		querySelector(s) { return this.querySelectorAll(s)[0] ?? null; }
		addEventListener(nom, rappel, options = {}) { const liste = this.ecouteurs.get(nom) ?? []; liste.push({ rappel, once: options.once }); this.ecouteurs.set(nom, liste); }
		removeEventListener(nom, rappel) { this.ecouteurs.set(nom, (this.ecouteurs.get(nom) ?? []).filter(e => e.rappel !== rappel)); }
		dispatchEvent(evenement) { evenement.target ??= this; evenement.preventDefault ??= () => {}; for (const e of [...(this.ecouteurs.get(evenement.type) ?? [])]) { if (e.once) this.removeEventListener(evenement.type, e.rappel); e.rappel(evenement); } return true; }
		click() { if (!this.disabled) this.dispatchEvent({ type: 'click' }); }
		focus() { document.activeElement = this; }
		scrollIntoView(options) { document.defilements.push({ element: this, options }); }
		showModal() { this.open = true; }
		close() { this.open = false; this.dispatchEvent({ type: 'close' }); }
	}
	const document = { isConnected: true, createElement: tag => new Element(tag), createDocumentFragment: () => new Element('fragment') };
	document.head = document.createElement('head'); document.body = document.createElement('body'); document.activeElement = document.body;
	document.defilements = [];
	const evenementsDocument = document.createElement('document');
	for (const methode of ['addEventListener', 'removeEventListener', 'dispatchEvent']) {
		document[methode] = evenementsDocument[methode].bind(evenementsDocument);
	}
	document.querySelectorAll = s => [...document.head.querySelectorAll(s), ...document.body.querySelectorAll(s)];
	document.querySelector = s => document.querySelectorAll(s)[0] ?? null;
	return document;
}

export const terminerMicrotaches = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };

export function creerBanc(montrer, type = 'JOUEUR', document = creerDocument()) {
	const principale = document.createElement('div'), laterale = document.createElement('div');
	document.body.append(principale, laterale);
	const appels = []; let rappel, captures = 0;
	const contexte = { soireeId: 's', acteur: { type, identifiant: 'j' },
		maintenantServeur: Date.now,
		envoyer: (...args) => new Promise((resolve, reject) => appels.push({ args, resolve, reject })),
		resynchroniser: () => captures++, surEtatConnexion: f => { rappel = f; f('synchronisation'); return () => { rappel = null; }; } };
	const module = montrer({ principale, laterale }, contexte);
	return { document, principale, laterale, appels, module, contexte, captures: () => captures, connecter: e => rappel?.(e), abonnee: () => Boolean(rappel) };
}

export const chanson = { chansonId: 'c', titre: 'Titre de démonstration', artiste: 'Artiste fictif', album: null, dureeMs: 123456, etatParoles: 'SYNCHRONISEES' };
export const passage = (id = 'p', numero = 14, tour = 1) => ({ passageId: id, numero, tour, position: numero, numeroModification: 2,
	chanson: { ...chanson, chansonId: id }, interpretes: [{ joueurId: 'j', pseudo: 'Jo' }], prioritaire: false, attente: { etat: 'ESTIMEE', dureeMs: 60000 } });
export function vue(bloc = {}) { return { modeEnCours: 'KARAOKE', karaoke: { mancheId: 'm', courant: null, etat: 'EN_COURS',
	configurationInterface: { longueurRechercheMax: 120, delaiConsultationNavigateurMs: 8000, delaiConfirmationNavigateurMs: 10000 },
	limitePassagesEnAttente: 2, peutRechercher: true, peutInscrire: true, peutGererFile: true, peutReglerLimite: true,
	motifInscriptionRefusee: null, mesPassagesEnAttente: [], prochainsPassages: [], passagesEnAttente: [], nombreAutresPassages: 0, ...bloc } }; }
