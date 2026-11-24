package fr.croustyquizz.manche;

import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** Adaptateur JPA pour le cycle commun et les reçus idempotents. */
@Repository
public class DepotDeManchesJpa implements DepotDeManches {
	private final EntityManager entites;
	public DepotDeManchesJpa(EntityManager entites) { this.entites = entites; }
	@Override
	public ManchePersistante charger(UUID id) { return entites.find(ManchePersistante.class, id); }
	@Override
	public void enregistrer(ManchePersistante manche) {
		if (manche.getPositionProgramme() == null) {
			int dernierePosition = entites.createQuery("""
					select coalesce(max(m.positionProgramme), -1)
					from ManchePersistante m where m.soireeId = :soiree
					""", Integer.class).setParameter("soiree", manche.getSoireeId()).getSingleResult();
			manche.placerDansLeProgramme(dernierePosition + 1);
		}
		entites.merge(manche);
	}
	@Override
	public void retirer(ManchePersistante manche) { entites.remove(manche); }
	@Override
	public List<ManchePersistante> deLaSoiree(UUID soireeId) {
		return entites.createQuery("select m from ManchePersistante m where m.soireeId = :soiree order by m.positionProgramme",
				ManchePersistante.class).setParameter("soiree", soireeId).getResultList();
	}
	@Override
	public EnvoiDeManche retrouverEnvoi(UUID cle) { return entites.find(EnvoiDeManche.class, cle); }
	@Override
	public void enregistrerEnvoi(EnvoiDeManche envoi) { entites.persist(envoi); }
}
