package renaming.whitespace;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import renaming.renamers.INGramIdentifierRenamer;
import codemining.java.codeutils.JavaFormattingTokenizer;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.NGram;
import codemining.lm.ngram.NGramLM;
import codemining.util.SettingsLoader;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.math.DoubleMath;

/**
 * Treat formatting issues as renaming-like problem.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class FormattingRenamings implements INGramIdentifierRenamer {

	/**
	 * Struct class containing results.
	 * 
	 */
	public static class WhitespacePrecisionRecall {
		static double[] THRESHOLD_VALUES = { -.1, -.2, -.5, -1, -1.5, -2, -2.5,
				-3, -3.5, -4, -5, -6, -7, -8, -10, -12, -15, -20, -50, -100,
				-Double.MAX_VALUE };
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
				if (tp.score > THRESHOLD_VALUES[i]) {
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

	private static final Logger LOGGER = Logger
			.getLogger(FormattingRenamings.class.getName());

	final ITokenizer wsTokenizer = new JavaFormattingTokenizer();

	AbstractNGramLM ngramLM;

	final Class<? extends AbstractNGramLM> smoothedNgramClass;

	public static final int NGRAM_SIZE = (int) SettingsLoader
			.getNumericSetting("ngramSize", 5);

	public FormattingRenamings() {
		super();
		try {
			smoothedNgramClass = (Class<? extends AbstractNGramLM>) Class
					.forName(SettingsLoader.getStringSetting(
							"ngramSmootherClass",
							"codemining.lm.ngram.smoothing.StupidBackoff"));
		} catch (final ClassNotFoundException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		}
	}

	public FormattingRenamings(final AbstractNGramLM languageModel) {
		super();
		ngramLM = languageModel;
		smoothedNgramClass = null;
	}

	public void buildModel(final Collection<File> trainingFiles) {
		try {
			final AbstractNGramLM dict = new NGramLM(NGRAM_SIZE, wsTokenizer);
			dict.trainModel(trainingFiles);

			final AbstractNGramLM ng = (AbstractNGramLM) checkNotNull(
					smoothedNgramClass,
					"no smoother class. Was the n-gram model pre-build?")
					.getDeclaredConstructor(AbstractNGramLM.class).newInstance(
							dict);
			ngramLM = ng;
		} catch (final IOException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		} catch (final IllegalArgumentException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		} catch (final SecurityException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		} catch (final InstantiationException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		} catch (final IllegalAccessException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		} catch (final InvocationTargetException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		} catch (final NoSuchMethodException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		}
	}

	/**
	 * Predict the max-likelihood tokens given the ngrams.
	 * 
	 * @param ngrams
	 * @param alternatives
	 * @return
	 */
	@Override
	public SortedSet<Renaming> calculateScores(
			final Multiset<NGram<String>> ngrams,
			final Set<String> alternatives, final Scope scope) {
		final SortedSet<Renaming> suggestions = Sets.newTreeSet();

		for (final String alternative : alternatives) {
			double score = 0;
			for (final NGram<String> ngram : ngrams) {
				score += DoubleMath
						.log2(ngramLM.getProbabilityFor(NGram
								.substituteTokenWith(ngram, WILDCARD_TOKEN,
										alternative)));
			}
			suggestions.add(new Renaming(alternative, -score, 1, null));
		}
		return suggestions;
	}

	public WhitespacePrecisionRecall evaluateFormattingAccuracy(
			final File testFile) throws IOException {
		final char[] fileContent = FileUtils.readFileToString(testFile)
				.toCharArray();
		final List<String> tokens = tokenizeCode(fileContent);

		final WhitespacePrecisionRecall result = new WhitespacePrecisionRecall();

		for (int i = 0; i < tokens.size(); i++) {
			if (tokens.get(i).startsWith("WS_")) {
				// create all n-grams around i
				final Multiset<NGram<String>> ngrams = getNGramsAround(i,
						tokens);

				// find all renamings
				final Set<String> alternatives = Sets.newTreeSet(ngramLM
						.getTrie().getVocabulary());
				alternatives.add(AbstractNGramLM.UNK_SYMBOL);

				// score accuracy of first suggestion
				final SortedSet<Renaming> suggestions = calculateScores(ngrams,
						alternatives, null);
				final String actual = tokens.get(i);
				result.addSuggestion(suggestions, actual);
			}
		}
		return result;
	}

	/**
	 * Return the n-grams around the index, renaming tokens[index] to the
	 * toBeReplaced token.
	 * 
	 * @param index
	 * @param tokens
	 * @param toBeReplaced
	 * @return
	 */
	public Multiset<NGram<String>> getNGramsAround(final int index,
			final List<String> tokens) {
		final Multiset<NGram<String>> ngrams = HashMultiset.create();
		final int start = index - ngramLM.getN() >= 0 ? index - ngramLM.getN()
				: 0;
		final int end = index + ngramLM.getN() <= tokens.size() ? index
				+ ngramLM.getN() : tokens.size();

		final List<String> context = Lists.newArrayList();
		for (int i = start; i < end; i++) {
			if (i == index) {
				context.add(WILDCARD_TOKEN);
			} else {
				context.add(tokens.get(i));
			}
		}

		final int startPos = index - ngramLM.getN() >= 0 ? index - start
				: index;

		for (int i = startPos; i < context.size(); i++) {
			ngrams.add(NGram.constructNgramAt(i, context, ngramLM.getN()));
		}

		return ngrams;
	}

	/**
	 * @param fileContent
	 * @return
	 */
	public List<String> tokenizeCode(final char[] fileContent) {
		final List<String> tokens = wsTokenizer.tokenListFromCode(fileContent);
		return tokens;
	}
}
