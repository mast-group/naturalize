/**
 * 
 */
package renaming.tools;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import renaming.formatting.FormattingRenamings;
import renaming.renamers.INGramIdentifierRenamer.Renaming;
import codemining.cpp.codeutils.CASTAnnotatedTokenizer;
import codemining.cpp.codeutils.CppWhitespaceTokenizer;
import codemining.java.tokenizers.JavaWhitespaceTokenizer;
import codemining.languagetools.FormattingTokenizer;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.NGram;
import codemining.util.SettingsLoader;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

/**
 * A code review assistant that checks formatting.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class FormattingReviewAssistant {

	public static final double CONFIDENCE_THRESHOLD = SettingsLoader
			.getNumericSetting("confidenceThreshold", 10);

	private static double getScoreOf(final SortedSet<Renaming> suggestions,
			final String actual) {
		for (final Renaming r : suggestions) {
			if (r.name.equals(actual)) {
				return r.score;
			}
		}
		return Double.MAX_VALUE;
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		if (args.length < 3) {
			System.err.println("Usage <trainDirectory> <suggestFile> cpp|java");
			System.exit(-1);
		}

		final File trainDir = new File(args[0]);
		final File testFile = new File(args[1]);
		final String language = args[2];
		final FormattingTokenizer tokenizer;
		if (language.equals("cpp")) {
			tokenizer = new FormattingTokenizer(new CASTAnnotatedTokenizer(
					new CppWhitespaceTokenizer()));
		} else if (language.equals("java")) {
			tokenizer = new FormattingTokenizer(new JavaWhitespaceTokenizer());
		} else {
			throw new IllegalArgumentException("Unrecognized option "
					+ language);
		}

		final Collection<File> trainFiles = FileUtils.listFiles(trainDir,
				tokenizer.getFileFilter(), DirectoryFileFilter.DIRECTORY);
		trainFiles.remove(testFile);
		final FormattingReviewAssistant reviewer = new FormattingReviewAssistant(
				tokenizer, trainFiles);

		reviewer.evaluateFile(testFile);
	}

	private final FormattingRenamings renamings;

	private final FormattingTokenizer tokenizer;

	private final Set<String> alternatives;

	/**
	 * 
	 */
	public FormattingReviewAssistant(final FormattingTokenizer tokenizer,
			final Collection<File> trainFiles) {
		this.tokenizer = tokenizer;
		renamings = new FormattingRenamings(tokenizer);
		renamings.buildModel(trainFiles);
		alternatives = Sets.newHashSet(getAlternativeNamings());
	}

	private void evaluateFile(final File testFile) throws IOException {
		final String testSourceFile = FileUtils.readFileToString(testFile);
		final List<String> tokens = renamings.tokenizeCode(testSourceFile
				.toCharArray());
		final SortedMap<Integer, SortedSet<Renaming>> suggestedRenamings = Maps
				.newTreeMap();

		for (int i = 0; i < tokens.size(); i++) {
			if (tokens.get(i).startsWith("WS_")) {
				// create all n-grams around i
				final Multiset<NGram<String>> ngrams = renamings
						.getNGramsAround(i, tokens);

				// score accuracy of first suggestion
				final SortedSet<Renaming> suggestions = renamings
						.calculateScores(ngrams, alternatives, null);
				final String actual = tokens.get(i);
				if (suggestions.first().name.equals(AbstractNGramLM.UNK_SYMBOL)
						|| suggestions.first().name.equals(actual)) {
					continue;
				}

				final double actualScore = getScoreOf(suggestions, actual);
				if (actualScore - suggestions.first().score > CONFIDENCE_THRESHOLD) {
					suggestedRenamings.put(i, suggestions);
				}
			}
		}

		// Now print the code if we have anything here. This is tricky:
		final SortedMap<Integer, SortedSet<Renaming>> positionedRenamings = postionRenamings(
				testSourceFile, suggestedRenamings);

		int lineStart = 0;
		int renamingCount = 0;
		while (lineStart < testSourceFile.length()) {
			final int lineEnd = testSourceFile.indexOf("\n", lineStart);
			System.out.print(testSourceFile.substring(lineStart, lineEnd + 1));
			final SortedMap<Integer, SortedSet<Renaming>> lineRenamings = positionedRenamings
					.subMap(lineStart, lineEnd + 1);
			if (!lineRenamings.isEmpty()) {
				final Set<Integer> inLinePositions = Sets.newTreeSet();
				for (final Entry<Integer, SortedSet<Renaming>> renaming : lineRenamings
						.entrySet()) {
					inLinePositions.add(renaming.getKey() - lineStart);
				}
				printRenamingPointers(inLinePositions, lineEnd - lineStart,
						renamingCount);
				renamingCount += inLinePositions.size();
			}
			lineStart = lineEnd + 1;
		}
		// Get the char positions where things are wrong
		// Print each line, unless the previous line had a suggestion
		// If so, compute position and add an arrow pointing there, with the
		// given suggestion
		System.out
				.println("-----------------------------------------------------");
		int i = 1;
		for (final Entry<Integer, SortedSet<Renaming>> renaming : positionedRenamings
				.entrySet()) {
			System.out.println(i + ":" + renaming.getValue().first());
			i++;
		}
	}

	/**
	 * @param fr
	 * @return
	 */
	public Set<String> getAlternativeNamings() {
		final Set<String> alternatives = Sets.newTreeSet(Sets.filter(renamings
				.getNgramLM().getTrie().getVocabulary(),
				new Predicate<String>() {

					@Override
					public boolean apply(final String input) {
						return input.startsWith("WS_");
					}
				}));
		alternatives.add(AbstractNGramLM.UNK_SYMBOL);
		return alternatives;
	}

	/**
	 * Position the renaming in the text.
	 * 
	 * @param testSourceFile
	 * @param suggestedRenamings
	 * @return
	 */
	private SortedMap<Integer, SortedSet<Renaming>> postionRenamings(
			final String testSourceFile,
			final SortedMap<Integer, SortedSet<Renaming>> suggestedRenamings) {
		final List<String> tokensPos = tokenizer
				.tokenListFromCode(testSourceFile.toCharArray());

		// Hack: reverse engineer list to do something useful
		final SortedMap<Integer, SortedSet<Renaming>> positionedRenamings = Maps
				.newTreeMap();
		int i = 0;
		for (final Entry<Integer, String> token : tokenizer.getBaseTokenizer()
				.tokenListWithPos(testSourceFile.toCharArray()).entrySet()) {
			if (suggestedRenamings.containsKey(i)) {
				positionedRenamings.put(token.getKey(),
						suggestedRenamings.get(i));
			}
			if (tokensPos.get(i).equals(FormattingTokenizer.WS_NO_SPACE)) {
				i++;
			}
			i++;
		}

		return positionedRenamings;
	}

	private void printRenamingPointers(final Set<Integer> inLinePositions,
			final int length, int renamingCount) {
		int currentPos = 0;
		while (currentPos <= length) {
			if (inLinePositions.contains(currentPos)) {
				renamingCount++;
				System.out.print("^->" + renamingCount);
			} else {
				System.out.print(" ");
			}
			currentPos++;
		}
		System.out.println();
	}
}
