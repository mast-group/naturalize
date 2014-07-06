/**
 * 
 */
package renaming.renamers;

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
import codemining.lm.ngram.smoothing.InterpolatedNGramLM;
import codemining.util.SettingsLoader;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * Evaluate LMs where we have a global n-gram model for interpolation.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class InterpolatedIdentifierRenamings extends
		AbstractIdentifierRenamings {

	private static final Logger LOGGER = Logger
			.getLogger(InterpolatedIdentifierRenamings.class.getName());

	final Class<? extends AbstractNGramLM> smoothedNgramClass;

	final ITokenizer tokenizer;

	public static final int NGRAM_SIZE = (int) SettingsLoader
			.getNumericSetting("ngramSize", 5);

	public static final double LAMBDA = SettingsLoader.getNumericSetting(
			"lambda", .2);

	static AbstractNGramLM globalNgram;
	static String ngramModelPath;

	public InterpolatedIdentifierRenamings(final ITokenizer tokenizer,
			final String globalNgramDirectory) {
		super();
		this.tokenizer = tokenizer;
		try {
			smoothedNgramClass = (Class<? extends AbstractNGramLM>) Class
					.forName(SettingsLoader.getStringSetting(
							"ngramSmootherClass",
							"codemining.lm.ngram.smoothing.StupidBackoff"));
			loadNGramModel(globalNgramDirectory);
		} catch (final ClassNotFoundException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (final SerializationException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public void buildRenamingModel(final Collection<File> trainingFiles) {
		try {
			final AbstractNGramLM dict = new IdentifierNeighborsNGramLM(
					NGRAM_SIZE, tokenizer);
			dict.trainModel(trainingFiles);

			final AbstractNGramLM ng = (AbstractNGramLM) checkNotNull(
					smoothedNgramClass,
					"no smoother class. n-gram model was probably pre-build and should not be trainable.")
					.getDeclaredConstructor(AbstractNGramLM.class).newInstance(
							dict);

			this.ngramLM = new InterpolatedNGramLM(globalNgram, ng, LAMBDA);
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
	 * Load as a static variable the global model.
	 * 
	 * @param globalNgramDirectory
	 * @throws SerializationException
	 */
	private synchronized void loadNGramModel(final String globalNgramDirectory)
			throws SerializationException {
		if (ngramModelPath != null
				&& !ngramModelPath.equals(globalNgramDirectory)) {
			throw new IllegalArgumentException("Two global models...");
		}
		if (globalNgram != null) {
			return;
		}

		globalNgram = (AbstractNGramLM) Serializer.getSerializer()
				.deserializeFrom(globalNgramDirectory);
		ngramModelPath = globalNgramDirectory;
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
