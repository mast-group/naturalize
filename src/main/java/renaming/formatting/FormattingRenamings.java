package renaming.formatting;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;

import renaming.renamers.INGramIdentifierRenamer;
import codemining.java.tokenizers.JavaWidthAnnotatedWhitespaceTokenizer;
import codemining.languagetools.FormattingTokenizer;
import codemining.languagetools.IFormattingTokenizer;
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
 * Treat formatting issues as renaming-like problem. For compatibility reasons
 * this class creates a java renamer by default.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class FormattingRenamings implements INGramIdentifierRenamer {

	private static final Logger LOGGER = Logger
			.getLogger(FormattingRenamings.class.getName());

	/**
	 * The formatting (whitespace) tokenizer.
	 */
	final IFormattingTokenizer tokenizer;

	private AbstractNGramLM ngramLM;

	private final Class<? extends AbstractNGramLM> smoothedNgramClass;

	public static final int NGRAM_SIZE = (int) SettingsLoader
			.getNumericSetting("ngramSize", 5);

	public FormattingRenamings() {
		this(new FormattingTokenizer(
				new JavaWidthAnnotatedWhitespaceTokenizer()));
	}

	public FormattingRenamings(final AbstractNGramLM languageModel,
			final IFormattingTokenizer tokenizer) {
		ngramLM = languageModel;
		smoothedNgramClass = null;
		this.tokenizer = tokenizer;
	}

	public FormattingRenamings(final IFormattingTokenizer tokenizer) {
		this.tokenizer = tokenizer;
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

	public void buildModel(final Collection<File> trainingFiles) {
		try {
			final AbstractNGramLM dict = new NGramLM(NGRAM_SIZE, tokenizer);
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
				score += DoubleMath.log2(getNgramLM().getProbabilityFor(
						NGram.substituteTokenWith(ngram, WILDCARD_TOKEN,
								alternative)));
			}
			suggestions.add(new Renaming(alternative, -score, 1, null));
		}
		return suggestions;
	}

	public AbstractNGramLM getNgramLM() {
		return ngramLM;
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
		final int start = index - getNgramLM().getN() >= 0 ? index
				- getNgramLM().getN() : 0;
		final int end = index + getNgramLM().getN() <= tokens.size() ? index
				+ getNgramLM().getN() : tokens.size();

		final List<String> context = Lists.newArrayList();
		for (int i = start; i < end; i++) {
			if (i == index) {
				context.add(WILDCARD_TOKEN);
			} else {
				context.add(tokens.get(i));
			}
		}

		final int startPos = index - getNgramLM().getN() >= 0 ? index - start
				: index;

		for (int i = startPos; i < context.size(); i++) {
			ngrams.add(NGram.constructNgramAt(i, context, getNgramLM().getN()));
		}

		return ngrams;
	}

	/**
	 * @param code
	 * @return
	 */
	public List<String> tokenizeCode(final char[] code) {
		final List<String> tokens = tokenizer.tokenListFromCode(code);
		return tokens;
	}
}
