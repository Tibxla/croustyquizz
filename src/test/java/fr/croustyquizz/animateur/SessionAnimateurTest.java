package fr.croustyquizz.animateur;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SessionAnimateurTest {

	@Test
	void invaliderLaSessionFermeToutesLesConsolesMalgreLesErreursDeFermeture() throws IOException {
		var sessionHttp = new MockHttpSession();
		var session = new SessionAnimateur(new AnimateurConnecte(UUID.randomUUID(), UUID.randomUUID()));
		session.attacher(sessionHttp);
		var consoleEnEcriture = mock(WebSocketSession.class);
		var consoleAvecErreurIo = mock(WebSocketSession.class);
		var consoleDisponible = mock(WebSocketSession.class);
		doThrow(new IllegalStateException("Écriture en cours")).when(consoleEnEcriture).close(CloseStatus.POLICY_VIOLATION);
		doThrow(new IOException("Connexion interrompue")).when(consoleAvecErreurIo).close(CloseStatus.POLICY_VIOLATION);
		assertThat(session.ajouter(consoleEnEcriture)).isTrue();
		assertThat(session.ajouter(consoleAvecErreurIo)).isTrue();
		assertThat(session.ajouter(consoleDisponible)).isTrue();

		assertThatCode(sessionHttp::invalidate).doesNotThrowAnyException();

		verify(consoleEnEcriture).close(CloseStatus.POLICY_VIOLATION);
		verify(consoleAvecErreurIo).close(CloseStatus.POLICY_VIOLATION);
		verify(consoleDisponible).close(CloseStatus.POLICY_VIOLATION);
		assertThat(session.ajouter(mock(WebSocketSession.class))).isFalse();
	}

	@Test
	void laSessionRestaureeConserveLIdentiteEtPeutAccueillirDeNouvellesConsoles() throws Exception {
		var sessionHttp = new MockHttpSession();
		var identite = new AnimateurConnecte(UUID.randomUUID(), UUID.randomUUID());
		var session = new SessionAnimateur(identite);
		session.attacher(sessionHttp);
		var ancienneConsole = mock(WebSocketSession.class);
		assertThat(session.ajouter(ancienneConsole)).isTrue();

		var memoire = new ByteArrayOutputStream();
		try (var sortie = new ObjectOutputStream(memoire)) {
			sortie.writeObject(sessionHttp.serializeState());
		}
		var sessionHttpRestauree = new MockHttpSession();
		try (var entree = new ObjectInputStream(new ByteArrayInputStream(memoire.toByteArray()))) {
			sessionHttpRestauree.deserializeState((Serializable) entree.readObject());
		}

		var restauree = SessionAnimateur.de(sessionHttpRestauree);
		assertThat(restauree).isNotNull();
		assertThat(restauree.identite()).isEqualTo(identite);
		var nouvelleConsole = mock(WebSocketSession.class);
		assertThat(restauree.ajouter(nouvelleConsole)).isTrue();
		sessionHttpRestauree.invalidate();
		verify(nouvelleConsole).close(CloseStatus.POLICY_VIOLATION);
		verifyNoInteractions(ancienneConsole);
	}
}
