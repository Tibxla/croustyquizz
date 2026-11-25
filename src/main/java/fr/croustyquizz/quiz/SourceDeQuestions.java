package fr.croustyquizz.quiz;

import java.util.List;

@FunctionalInterface
public interface SourceDeQuestions {
	List<Question> tirer(List<String> categories, Difficulte difficulte, int nombre);
}
