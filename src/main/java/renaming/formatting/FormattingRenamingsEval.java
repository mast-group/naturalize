/**
 * 
 */
package renaming.formatting;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.apache.commons.io.FileUtils;

import renaming.renamers.INGramIdentifierRenamer.Renaming;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.NGram;

import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

/**
 * Evaluate Formatting Renamings.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class FormattingRenamingsEval {

	/**
	 * Struct class containing results.
	 * 
	 */
	public static class WhitespacePrecisionRecall {
		static double[] THRESHOLD_VALUES = { .1, .2, .5, 1, 1.5, 2, 2.5, 3,
				3.5, 4, 5, 6, 7, 8, 10, 12, 15, 20, 50, 100, Double.MAX_VALUE };
		final long[] nGaveSuggestions = new long[THRESHOLD_VALUES.length];
		final long[] nCorrect = new long[THRESHOLD_VALUES.length];

		long total = 0;

		/**
		 * Accumulate results to this object
		 * 
		 * @param other
		 */
		public synchronized void accumulate(
				final WhitespacePrecisionRecall other) {
			for (int i = 0; i < THRESHOLD_VALUES.length; i++) {
				nGaveSuggestions[i] += other.nGaveSuggestions[i];
				nCorrect[i] += other.nCorrect[i];
			}
			total += other.total;
		}

		public void addSuggestion(final SortedSet<Renaming> suggestions,
				final String actual) {
			total++;
			if (suggestions.isEmpty()) {
				return;
			}
			final Renaming tp = suggestions.first();
			if (suggestions.first().name.equals(AbstractNGramLM.UNK_SYMBOL)) {
				return;
			}
			final boolean isCorrect = tp.name.equals(actual);

			// Find the index after which we suggest things
			int idx = THRESHOLD_VALUES.length - 1;
			for (int i = 0; i < THRESHOLD_VALUES.length; i++) {
				if (tp.score < THRESHOLD_VALUES[i]) {
					idx = i;
					break;
				}
			}

			for (int i = idx; i < THRESHOLD_VALUES.length; i++) {
				nGaveSuggestions[i]++;
				if (isCorrect) {
					nCorrect[i]++;
				}
			}
		}

		@Override
		public String toString() {
			final double[] precisionAtThreshold = new double[THRESHOLD_VALUES.length];
			for (int i = 0; i < THRESHOLD_VALUES.length; i++) {
				precisionAtThreshold[i] = ((double) nCorrect[i])
						/ nGaveSuggestions[i];

			}
			return Arrays.toString(THRESHOLD_VALUES) + "\n"
					+ Arrays.toString(nCorrect) + "\n"
					+ Arrays.toString(nGaveSuggestions) + "\n"
					+ Arrays.toString(precisionAtThreshold) + "\n" + total;
		}

	}

	private final FormattingRenamings renamer;

	/**
	 * 
	 */
	public FormattingRenamingsEval(final FormattingRenamings renamer) {
		this.renamer = renamer;
	}

	public FormattingRenamingsEval.WhitespacePrecisionRecall evaluateFormattingAccuracy(
			final File testFile) throws IOException {
		final char[] fileContent = FileUtils.readFileToString(testFile)
				.toCharArray();
		final List<String> tokens = renamer.tokenizeCode(fileContent);

		final FormattingRenamingsEval.WhitespacePrecisionRecall result = new FormattingRenamingsEval.WhitespacePrecisionRecall();

		for (int i = 0; i < tokens.size(); i++) {
			if (tokens.get(i).startsWith("WS_")) {
				// create all n-grams around i
				final Multiset<NGram<String>> ngrams = renamer.getNGramsAround(
						i, tokens);

				// find all renamings
				final Set<String> alternatives = Sets.newTreeSet(renamer
						.getNgramLM().getTrie().getVocabulary());
				alternatives.add(AbstractNGramLM.UNK_SYMBOL);

				// score accuracy of first suggestion
				final SortedSet<Renaming> suggestions = renamer
						.calculateScores(ngrams, alternatives, null);
				final String actual = tokens.get(i);
				result.addSuggestion(suggestions, actual);
			}
		}
		return result;
	}

}
