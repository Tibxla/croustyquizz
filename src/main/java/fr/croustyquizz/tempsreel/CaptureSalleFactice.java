package fr.croustyquizz.tempsreel;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** Adapter du prototype, à remplacer lorsque le domaine fournira sa vue initiale. */
@Component
class CaptureSalleFactice implements CaptureVueRepli {
	@Override
	public Evenement capturerSalle(UUID soireeId) {
		return new VueSallePrototype(List.of());
	}
}
