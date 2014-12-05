/**
 * 
 */
package renaming.renamers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.languagetools.Scope;
import codemining.languagetools.bindings.TokenNameBinding;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.NGram;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultiset;
import com.google.common.math.DoubleMath;

/**
 * Retrieve and score potential renamings.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public abstract class AbstractIdentifierRenamings implements
		INGramIdentifierRenamer {

	private static final Logger LOGGER = Logger
			.getLogger(AbstractIdentifierRenamings.class.getName());

	protected AbstractNGramLM ngramLM;

	protected double addScopePriors(final String identifierName,
			final Scope scope) {
		return 0;
	}

	public abstract void buildRenamingModel(final Collection<File> training);

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * renaming.renamers.INGramIdentifierRenamer#calculateScores(com.google.
	 * common.collect.Multiset, java.util.Set, codemining.languagetools.Scope)
	 */
	@Override
	public SortedSet<Renaming> calculateScores(
			final Multiset<NGram<String>> ngrams,
			final Set<String> alternatives, final Scope scope) {
		final SortedSet<Renaming> scoreMap = Sets.newTreeSet();

		for (final String identifierName : alternatives) {
			double score = 0;
			for (final Entry<NGram<String>> ngram : ngrams.entrySet()) {
				try {
					final NGram<String> identNGram = NGram.substituteTokenWith(
							ngram.getElement(), WILDCARD_TOKEN, identifierName);
					final double ngramScore = scoreNgram(identNGram);
					score += DoubleMath.log2(ngramScore) * ngram.getCount();
				} catch (final Throwable e) {
					LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
				}
			}
			scoreMap.add(new Renaming(identifierName, (addScopePriors(
					identifierName, scope) - score) / ngrams.size(), ngrams
					.size() / ngramLM.getN(), scope));
		}

		return scoreMap;
	}

	/**
	 * @param relevantNgrams
	 * @param currentName
	 * @return
	 */
	public Multiset<String> getAlternativeNames(
			final Multiset<NGram<String>> relevantNgrams,
			final String currentName) {
		// Get all alternative namings
		final Multiset<String> nameAlternatives = ngramLM
				.getAlternativeNamings(relevantNgrams, WILDCARD_TOKEN);
		nameAlternatives.add(currentName); // Give the current identifier a
											// chance...

		// Prune naming alternatives
		final Multiset<String> toKeep = TreeMultiset.create();

		int seen = 0;
		for (final Entry<String> ent : Multisets.copyHighestCountFirst(
				nameAlternatives).entrySet()) {
			if (seen > 1000) {
				break;
			}
			toKeep.add(ent.getElement(), ent.getCount());
			seen++;
		}
		toKeep.add(AbstractNGramLM.UNK_SYMBOL);
		return toKeep;
	}

	public AbstractNGramLM getLM() {
		return ngramLM;
	}

	/**
	 * @param targetPositions
	 * @param tokens
	 * @return
	 */
	public Multiset<NGram<String>> getNgramsAtPosition(
			final SortedSet<Integer> targetPositions, final List<String> tokens) {
		final Multiset<NGram<String>> ngrams = HashMultiset.create();
		for (final int ngramPos : targetPositions) {
			for (int i = 0; i < ngramLM.getN(); i++) {
				final int nGramPosition = ngramPos + i;
				if (nGramPosition >= tokens.size()) {
					break;
				}
				final NGram<String> ngram = NGram.constructNgramAt(
						nGramPosition, tokens, ngramLM.getN());
				ngrams.add(ngram);

				if (ngram.size() <= 1) {
					continue;
				}
			}
		}

		return ngrams;
	}

	/**
	 * Returns a list of potential renamings
	 * 
	 * @param scope
	 * @param targetIdentifier
	 * @return
	 */
	public SortedSet<Renaming> getRenamings(final Scope scope,
			final String targetIdentifier) {
		// Get the snippet n-grams
		final Multiset<NGram<String>> relevantNgrams = getSnippetNGrams(
				scope.code, targetIdentifier);

		final Multiset<String> toKeep = getAlternativeNames(relevantNgrams,
				targetIdentifier);

		// calculate scores for each naming
		return calculateScores(relevantNgrams, toKeep.elementSet(), scope);
	}

	/**
	 * Return the renamings for a single token name binding
	 * 
	 * @param binding
	 * @return
	 */
	public SortedSet<Renaming> getRenamings(final TokenNameBinding binding) {
		// Get the snippet n-grams
		final Multiset<NGram<String>> relevantNgrams = getSnippetNGrams(binding);

		final String currentName = binding.getName();

		final Multiset<String> toKeep = getAlternativeNames(relevantNgrams,
				currentName);

		// calculate scores for each naming
		return calculateScores(relevantNgrams, toKeep.elementSet(), null);
	}

	/**
	 * Return all n-grams that contain the given identifier in the snippet.
	 * 
	 * @param snippet
	 * @param targetIdentifier
	 * @return
	 */
	public Multiset<NGram<String>> getSnippetNGrams(final String snippet,
			final String targetIdentifier) {
		final List<String> lst = checkNotNull(ngramLM).getTokenizer()
				.tokenListFromCode(snippet.toCharArray());

		final SortedSet<Integer> identifierPositions = Sets.newTreeSet();
		final List<String> sentence = Lists.newArrayList();

		for (int i = 0; i < lst.size(); i++) {
			final String token = lst.get(i);
			sentence.add(token);
			if (token.equals(targetIdentifier)
					|| token.contains("%" + targetIdentifier + "%")) {
				identifierPositions.add(i);
				sentence.set(i, token.replace(targetIdentifier, WILDCARD_TOKEN));
			}
		}
		return getNgramsAtPosition(identifierPositions, sentence);
	}

	/**
	 * Return the n-gram for the current snippet.
	 * 
	 * @param binding
	 * @return
	 */
	private Multiset<NGram<String>> getSnippetNGrams(
			final TokenNameBinding binding) {
		// TODO: Need much more efficient method.
		final TokenNameBinding renamed = binding.renameTo(WILDCARD_TOKEN);
		return getNgramsAtPosition(Sets.newTreeSet(renamed.nameIndexes),
				renamed.sourceCodeTokens);
	}

	/**
	 * Return if the current token is an UNK under the n-gram LM.
	 * 
	 * @param token
	 * @return
	 */
	public boolean isTrueUNK(final String token) {
		return checkNotNull(ngramLM).getTrie().isUNK(token);
	}

	/**
	 * @param ngram
	 * @return
	 */
	public abstract double scoreNgram(final NGram<String> ngram);
}
