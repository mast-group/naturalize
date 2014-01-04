/**
 * 
 */
package renaming.ngram;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import codemining.lm.ILanguageModel;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.NGram;
import codemining.lm.ngram.smoothing.StupidBackoff;

/**
 * An n-gram model that interpolates the probability between two models.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class InterpolatedNGramLM extends StupidBackoff {

	private final AbstractNGramLM other;

	/**
	 * The interpolation weight.
	 */
	private final double lambda;

	private static final long serialVersionUID = -5318470048963631893L;

	public InterpolatedNGramLM(final AbstractNGramLM global,
			final AbstractNGramLM current, final double globalWeight) {
		super(current);
		other = global;
		lambda = globalWeight;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ngram.AbstractNGramLM#addFromSentence(java.util.List,
	 * boolean)
	 */
	@Override
	public void addFromSentence(List<String> sentence, boolean addNewVoc) {
		throw new UnsupportedOperationException(
				"InterpolatedNGramLM cannot be trained");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.ngram.AbstractNGramLM#addNgramToDict(codemining.lm.ngram
	 * .NGram, boolean)
	 */
	@Override
	protected void addNgramToDict(NGram<String> ngram, boolean addNewVoc) {
		throw new UnsupportedOperationException(
				"InterpolatedNGramLM cannot be trained");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ngram.AbstractNGramLM#addSentences(java.util.Set,
	 * boolean)
	 */
	@Override
	public void addSentences(Collection<List<String>> sentenceSet,
			boolean addNewVocabulary) {
		throw new UnsupportedOperationException(
				"InterpolatedNGramLM cannot be trained");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ngram.AbstractNGramLM#cutoffRare(int)
	 */
	@Override
	public void cutoffRare(int threshold) {
		throw new UnsupportedOperationException(
				"InterpolatedNGramLM cannot be altered");

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ILanguageModel#getImmutableVersion()
	 */
	@Override
	public ILanguageModel getImmutableVersion() {
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.ngram.AbstractNGramLM#getProbabilityFor(codemining.lm.ngram
	 * .NGram)
	 */
	@Override
	public double getProbabilityFor(final NGram<String> ngram) {
		final NGram<String> prunedNgram;
		if (ngram.size() > other.getN()) {
			prunedNgram = new NGram<String>(ngram, ngram.size() - other.getN(),
					ngram.size());
		} else {
			prunedNgram = ngram;
		}
		return lambda * other.getProbabilityFor(prunedNgram) + (1. - lambda)
				* super.getProbabilityFor(ngram);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.ILanguageModel#trainIncrementalModel(java.util.Collection)
	 */
	@Override
	public void trainIncrementalModel(Collection<File> files)
			throws IOException {
		throw new UnsupportedOperationException(
				"InterpolatedNGramLM cannot be trained");

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ILanguageModel#trainModel(java.util.Collection)
	 */
	@Override
	public void trainModel(Collection<File> files) throws IOException {
		throw new UnsupportedOperationException(
				"InterpolatedNGramLM cannot be trained");
	}

}
