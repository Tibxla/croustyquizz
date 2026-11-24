package fr.croustyquizz.manche;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Base en mémoire et vraies synchronisations Spring, avec annulation des écritures. */
public final class StockageMancheFactice extends AbstractPlatformTransactionManager implements DepotDeManches {
	private static final long serialVersionUID = 1L;
	private final Map<UUID, ManchePersistante> manches = new HashMap<>();
	private final Map<UUID, EnvoiDeManche> envois = new HashMap<>();
	private final ModeFacticeDuSocle mode;
	private final ThreadLocal<Sauvegarde> sauvegarde = new ThreadLocal<>();
	public boolean echouerCommit;
	public RuntimeException panneLecture;
	public StockageMancheFactice(ModeFacticeDuSocle mode) { this.mode = mode; setRollbackOnCommitFailure(true); }
	@Override
	protected Object doGetTransaction() { return new Object(); }
	@Override
	protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
		Map<UUID, ManchePersistante> copie = new HashMap<>();
		manches.forEach((id, manche) -> copie.put(id, copier(manche)));
		sauvegarde.set(new Sauvegarde(copie, new HashMap<>(envois), new HashMap<>(mode.etats)));
	}
	@Override
	protected void doCommit(DefaultTransactionStatus statut) {
		if (echouerCommit) { echouerCommit = false; throw new TransactionSystemException("Commit refusé pour le test"); }
		sauvegarde.remove();
	}
	@Override
	protected void doRollback(DefaultTransactionStatus statut) {
		var avant = sauvegarde.get();
		manches.clear(); manches.putAll(avant.manches()); envois.clear(); envois.putAll(avant.envois());
		mode.etats.clear(); mode.etats.putAll(avant.etats()); sauvegarde.remove();
	}
	@Override
	public ManchePersistante charger(UUID id) {
		if (panneLecture != null) { var panne = panneLecture; panneLecture = null; throw panne; }
		return manches.get(id);
	}
	@Override
	public void enregistrer(ManchePersistante manche) { manches.put(manche.getMancheId(), manche); }

	@Override
	public void retirer(ManchePersistante manche) { manches.remove(manche.getMancheId()); }
	@Override
	public List<ManchePersistante> deLaSoiree(UUID soireeId) {
		return manches.values().stream().filter(m -> m.getSoireeId().equals(soireeId)).toList();
	}
	@Override
	public EnvoiDeManche retrouverEnvoi(UUID cle) { return envois.get(cle); }
	@Override
	public void enregistrerEnvoi(EnvoiDeManche envoi) {
		envois.put(envoi.getId(), envoi);
	}
	private ManchePersistante copier(ManchePersistante manche) {
		var copie = new ManchePersistante(manche.getMancheId(), manche.getSoireeId(), manche.getMode(), manche.getReglages(), java.time.Instant.EPOCH);
		copie.changerEtat(manche.getEtat(), java.time.Instant.EPOCH);
		return copie;
	}
	private record Sauvegarde(Map<UUID, ManchePersistante> manches, Map<UUID, EnvoiDeManche> envois,
			Map<UUID, DeroulementFactice.Etat> etats) { }
}
