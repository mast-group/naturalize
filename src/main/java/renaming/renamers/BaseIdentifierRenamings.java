/**
 * 
 */
package renaming.renamers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;

import renaming.ngram.IdentifierNeighborsNGramLM;
import codemining.languagetools.ITokenizer;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.NGram;
import codemining.util.SettingsLoader;

/**
 * A generic prior-based identifier renaming class.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class BaseIdentifierRenamings extends AbstractIdentifierRenamings {

	public static final int NGRAM_SIZE = (int) SettingsLoader
			.getNumericSetting("ngramSize", 5);

	final Class<? extends AbstractNGramLM> smoothedNgramClass;

	final ITokenizer tokenizer;

	private static final Logger LOGGER = Logger
			.getLogger(BaseIdentifierRenamings.class.getName());

	public BaseIdentifierRenamings() {
		tokenizer = null;
		smoothedNgramClass = null;
	}

	public BaseIdentifierRenamings(final AbstractNGramLM model) {
		this.ngramLM = model;
		tokenizer = model.getTokenizer();
		smoothedNgramClass = null;
	}

	/**
	 * 
	 */
	public BaseIdentifierRenamings(final ITokenizer tokenizer) {
		super();
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * renaming.renamers.AbstractIdentifierRenamings#buildRenamingModel(java
	 * .util.Collection)
	 */
	@Override
	public void buildRenamingModel(final Collection<File> trainingFiles) {
		checkArgument(trainingFiles.size() > 0);
		try {
			final AbstractNGramLM dict = new IdentifierNeighborsNGramLM(
					NGRAM_SIZE, tokenizer);
			dict.trainModel(trainingFiles);

			final AbstractNGramLM ng = (AbstractNGramLM) checkNotNull(
					smoothedNgramClass,
					"no smoother class. n-gram model was probably pre-build and should not be trainable.")
					.getDeclaredConstructor(AbstractNGramLM.class).newInstance(
							dict);
			this.ngramLM = ng;

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

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * renaming.renamers.AbstractIdentifierRenamings#scoreNgram(codemining.lm
	 * .ngram.NGram)
	 */
	@Override
	public double scoreNgram(final NGram<String> ngram) {
		final double ngramScore = ngramLM.getProbabilityFor(ngram);
		return ngramScore;
	}

}
