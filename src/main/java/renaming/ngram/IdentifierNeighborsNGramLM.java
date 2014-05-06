package renaming.ngram;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.languagetools.ITokenizer;
import codemining.languagetools.ITokenizer.FullToken;
import codemining.lm.ILanguageModel;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.ImmutableNGramLM;
import codemining.lm.ngram.NGram;
import codemining.lm.util.TokenVocabularyBuilder;
import codemining.util.SettingsLoader;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.Lists;

/**
 * An n-gram LM that is specific to identifiers.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class IdentifierNeighborsNGramLM extends AbstractNGramLM {

	/**
	 * Extract ngrams from a specific file.
	 * 
	 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
	 * 
	 */
	private class NGramExtractorRunnable implements Runnable {

		final File codeFile;

		final ITokenizer tokenizer;

		public NGramExtractorRunnable(final File file,
				final ITokenizer tokenizerModule) {
			codeFile = file;
			tokenizer = tokenizerModule;
		}

		public void addRelevantNGrams(final List<FullToken> lst) {

			final SortedSet<Integer> identifierPositions = new TreeSet<Integer>();
			final List<String> sentence = Lists.newArrayList();

			for (int i = 0; i < lst.size(); i++) {
				final FullToken fullToken = lst.get(i);
				sentence.add(fullToken.token);
				if (fullToken.tokenType.equals(tokenizer.getIdentifierType())) {
					identifierPositions.add(i);
				}
			}

			// Construct the rest
			for (int i = 0; i < sentence.size(); i++) {
				// Filter n-grams with no identifiers
				if (identifierPositions.subSet(i - getN() + 1, i + 1).isEmpty()) {
					continue;
				}
				final NGram<String> ngram = NGram.constructNgramAt(i, sentence,
						getN());
				if (ngram.size() > 1) {
					addNgram(ngram, false);
				}
			}

		}

		@Override
		public void run() {
			LOGGER.finer("Reading file " + codeFile.getAbsolutePath());
			try {
				final List<FullToken> tokens = tokenizer
						.getTokenListFromCode(codeFile);

				addRelevantNGrams(tokens);
			} catch (final IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

	private static final Logger LOGGER = Logger
			.getLogger(IdentifierNeighborsNGramLM.class.getName());

	private static final long serialVersionUID = 2765488075402402353L;

	public static final int CLEAN_NGRAM_THRESHOLD = (int) SettingsLoader
			.getNumericSetting("CleanNgramCountThreshold", 1);

	public static final int CLEAN_VOCABULARY_THRESHOLD = (int) SettingsLoader
			.getNumericSetting("CleanVocabularyThreshold", 1);

	/**
	 * Constructor.
	 * 
	 * @param size
	 *            the max-size of the n-grams. The n.
	 */
	public IdentifierNeighborsNGramLM(final int size,
			final ITokenizer tokenizerModule) {
		super(size, tokenizerModule);
	}

	/**
	 * Given a sentence (i.e. a list of strings) add all appropriate ngrams.
	 * 
	 * @param sentence
	 *            an (ordered) list of tokens belonging to a sentence.
	 */
	@Override
	public void addFromSentence(final List<String> sentence,
			final boolean addNewToks) {
		for (int i = getN() - 1; i < sentence.size(); ++i) {
			final NGram<String> ngram = NGram.constructNgramAt(i, sentence,
					getN());
			if (ngram.size() > 1) {
				addNgram(ngram, addNewToks);
			}
		}

		// Construct for the last parts
		for (int i = getN() - 1; i > 0; i--) {
			final NGram<String> ngram = NGram.constructNgramAt(
					sentence.size() - 1, sentence, i);
			addNgram(ngram, addNewToks);
		}
	}

	/**
	 * Given an ngram (a list of strings with size <= n) add it to the trie and
	 * update the counts of counts.
	 * 
	 * @param ngram
	 */
	@Override
	protected void addNgram(final NGram<String> ngram, final boolean addNewVoc) {

		trie.add(ngram, addNewVoc);
	}

	/**
	 * Add a set of sentences to the dictionary.
	 * 
	 * @param sentenceSet
	 */
	@Override
	public void addSentences(final Collection<List<String>> sentenceSet,
			final boolean addNewVocabulary) {
		for (final List<String> sent : sentenceSet) {
			addFromSentence(sent, addNewVocabulary);
		}
	}

	/**
	 * Cut-off rare ngrams by removing rare tokens.
	 * 
	 * @param threshold
	 */
	@Override
	public void cutoffRare(final int threshold) {
		trie.cutoffRare(threshold);
	}

	@Override
	public ILanguageModel getImmutableVersion() {
		return new ImmutableNGramLM(this);
	}

	@Override
	public double getProbabilityFor(final NGram<String> ngram) {
		return getMLProbabilityFor(ngram, false);
	}

	@Override
	public void removeNgram(final NGram<String> ngram) {
		trie.remove(ngram);
	}

	@Override
	public void trainIncrementalModel(final Collection<File> files)
			throws IOException {
		trainModel(files);

	}

	@Override
	public void trainModel(final Collection<File> files) throws IOException {
		LOGGER.info("Building vocabulary...");
		trie.buildVocabularySymbols(TokenVocabularyBuilder.buildVocabulary(
				files, getTokenizer(), CLEAN_VOCABULARY_THRESHOLD));

		LOGGER.info("Vocabulary Built. Counting n-grams");
		trainModel(files, false, false);
	}

	/**
	 * @param files
	 * @param performCleanups
	 */
	private void trainModel(final Collection<File> files,
			final boolean performCleanups, final boolean addNewToksToVocabulary) {
		final ParallelThreadPool threadPool = new ParallelThreadPool();

		for (final File fi : files) {
			threadPool.pushTask(new NGramExtractorRunnable(fi, getTokenizer()));

		}

		threadPool.waitForTermination();
	}

}
