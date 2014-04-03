/**
 * 
 */
package renaming.evaluation;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.segmentranking.SnippetScorer;
import renaming.segmentranking.SnippetScorer.SnippetSuggestions;
import renaming.tools.CodeReviewAssistant;
import codemining.java.codedata.MethodRetriever;
import codemining.java.codeutils.scopes.ScopesTUI;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.util.SettingsLoader;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Get the output for the human evaluation of unnecessary diversity of names.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class UnecessaryNamesHumanEvalOutput {

	public static final int NUM_EXAMPLES_TO_PRODUCE = 50;

	private static final Logger LOGGER = Logger
			.getLogger(UnecessaryNamesHumanEvalOutput.class.getName());

	private static final double SAMPLING_RATE = SettingsLoader
			.getNumericSetting("samplingPercent", 1);

	/**
	 * @param extractor
	 * @param directory
	 * @param renamerClass
	 * @param renamerConstructorParams
	 * @return
	 * @throws IllegalArgumentException
	 */
	public static SortedMap<SnippetScorer.SnippetSuggestions, String> getSnippetRanking(
			final IScopeExtractor extractor, final File directory,
			final String renamerClass, final String renamerConstructorParams)
			throws IllegalArgumentException {
		final JavaTokenizer tokenizer = new JavaTokenizer();
		final Collection<File> allFiles = FileUtils.listFiles(directory,
				tokenizer.getFileFilter(), DirectoryFileFilter.DIRECTORY);

		final SortedMap<SnippetScorer.SnippetSuggestions, String> fileScores = Maps
				.newTreeMap();

		for (final File file : allFiles) {
			try {
				final AbstractIdentifierRenamings renamer;
				if (RandomUtils.nextDouble() > SAMPLING_RATE) {
					continue;
				}
				try {

					if (renamerConstructorParams == null) {
						renamer = (AbstractIdentifierRenamings) Class
								.forName(renamerClass)
								.getDeclaredConstructor(ITokenizer.class)
								.newInstance(tokenizer);
					} else {
						renamer = (AbstractIdentifierRenamings) Class
								.forName(renamerClass)
								.getDeclaredConstructor(ITokenizer.class,
										String.class)
								.newInstance(tokenizer,
										renamerConstructorParams);
					}
				} catch (final Exception e) {
					LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
					throw new IllegalArgumentException(e);
				}

				final Collection<File> trainingFiles = Sets
						.newHashSet(allFiles);
				checkArgument(trainingFiles.remove(file));

				renamer.buildRenamingModel(trainingFiles);

				final SnippetScorer scorer = new SnippetScorer(renamer,
						extractor);

				for (final Entry<String, MethodDeclaration> entry : MethodRetriever
						.getMethodNodes(file).entrySet()) {
					final SnippetSuggestions snippetSuggestion = scorer
							.scoreSnippet(entry.getValue(), true);
					fileScores.put(snippetSuggestion, file.getAbsolutePath()
							+ "." + entry.getKey() + ": \n"
							+ entry.getValue().toString());
				}
			} catch (final Throwable e) {
				LOGGER.warning("Could not process file "
						+ file.getAbsolutePath() + "\n"
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}
		return fileScores;
	}

	private static List<Entry<SnippetSuggestions, String>> getTop10pct(
			final SortedMap<SnippetSuggestions, String> snippetRank) {
		final List<Entry<SnippetSuggestions, String>> top = Lists
				.newArrayList();

		int currentSize = 0;
		final int targetSize = (int) (snippetRank.size() / 10) + 1;
		for (final Entry<SnippetSuggestions, String> entry : snippetRank
				.entrySet()) {
			if (currentSize > targetSize) {
				break;
			}
			if (entry.getKey().suggestions.isEmpty()) {
				continue;
			} else if (entry.getKey().score < 1) {
				continue;
			}
			currentSize++;
			top.add(entry);
		}
		Collections.shuffle(top);
		return top;
	}

	public static void main(final String args[]) {
		if (args.length < 3) {
			System.err
					.println("Usage <directory> class|method|variable|all <renamerClass> [renamerArgs]");
			return;
		}
		final IScopeExtractor extractor = ScopesTUI
				.getScopeExtractorByName(args[1]);
		final File directory = new File(args[0]);
		final String renamerClass = args[2];
		final String renamerConstructorParams;
		if (args.length < 4) {
			renamerConstructorParams = null;
		} else {
			renamerConstructorParams = args[3];
		}

		final SortedMap<SnippetSuggestions, String> methodScores = getSnippetRanking(
				extractor, directory, renamerClass, renamerConstructorParams);

		final List<Entry<SnippetSuggestions, String>> topRenamings = getTop10pct(methodScores);

		int i = 1;
		for (final Entry<SnippetSuggestions, String> entry : topRenamings
				.subList(
						0,
						topRenamings.size() < NUM_EXAMPLES_TO_PRODUCE ? topRenamings
								.size() : NUM_EXAMPLES_TO_PRODUCE)) {
			CodeReviewAssistant.printRenaming(entry.getKey(), entry.getValue(),
					i);
			i++;
		}
	}
}
