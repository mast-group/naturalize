/**
 * 
 */
package renaming.segmentranking;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.eclipse.jdt.core.dom.ASTNode;

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.INGramIdentifierRenamer.Renaming;
import renaming.segmentranking.SegmentRenamingSuggestion.Suggestion;
import codemining.java.codeutils.scopes.MethodScopeExtractor;
import codemining.java.codeutils.scopes.TypenameScopeExtractor;
import codemining.languagetools.IScopeExtractor;
import codemining.util.SettingsLoader;

import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.math.DoubleMath;

/**
 * Score snippets based on the log-likelihood ratio.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class SnippetScorer {

	public static class SnippetSuggestions implements
			Comparable<SnippetSuggestions> {

		public final double score;

		public final SortedSet<Suggestion> suggestions;

		public SnippetSuggestions(final SortedSet<Suggestion> suggestionSet,
				final double confidenceScore) {
			score = confidenceScore;
			suggestions = suggestionSet;
		}

		@Override
		public int compareTo(final SnippetSuggestions other) {
			return ComparisonChain
					.start()
					.compare(score, other.score)
					.compare(suggestions.size(), other.suggestions.size())
					.compare(suggestions, other.suggestions,
							Ordering.usingToString()).result();

		}

		@Override
		public boolean equals(final Object obj) {
			if (!(obj instanceof SnippetSuggestions)) {
				return false;
			}
			final SnippetSuggestions other = (SnippetSuggestions) obj;
			return Double.compare(other.score, score) == 0
					&& suggestions.equals(other.suggestions);
		}

		/**
		 * Get the probability of a specific renaming happening (taking at least
		 * one of the suggestions).
		 * 
		 * @param suggestions
		 * @return
		 */
		public double getLogProbOfNotRenaming() {
			double logProbNotRename = 0;
			for (final Suggestion sg : suggestions) {
				final double pNotRename = sg.getProbNotRename();
				checkArgument(pNotRename <= 1, pNotRename
						+ " should be in the probability range");
				checkArgument(pNotRename >= 0, pNotRename
						+ " should be in the probability range");
				logProbNotRename += DoubleMath.log2(pNotRename);
			}
			return logProbNotRename;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(score, suggestions);
		}

		@Override
		public String toString() {
			return suggestions.toString() + " score:" + score;
		}

	}

	private static final Logger LOGGER = Logger.getLogger(SnippetScorer.class
			.getName());

	private static final double SUGGESTION_THRESHOLD_TYPE = SettingsLoader
			.getNumericSetting("SuggestionThresholdType", 1);
	private static final double SUGGESTION_THRESHOLD_METHOD = SettingsLoader
			.getNumericSetting("SuggestionThresholdMethod", 1);
	private static final double SUGGESTION_THRESHOLD_VAR = SettingsLoader
			.getNumericSetting("SuggestionThresholdVar", 6);
	private static final int SUGGESTION_K = (int) SettingsLoader
			.getNumericSetting("k", 5);

	public static SortedSet<Renaming> applyThresholdToRenamings(
			final SortedSet<Renaming> suggestedRenamings, final double threshold) {
		final SortedSet<Renaming> filteredRenamings = Sets.newTreeSet();
		boolean includedUNK = false;
		int r = 0;
		for (final Renaming renaming : suggestedRenamings) {
			if (renaming.score > threshold || r > SUGGESTION_K) {
				// once we exceed the threshold stop
				break;
			}
			if (renaming.name.equals("UNK_SYMBOL")) {
				includedUNK = true;
			}
			filteredRenamings.add(renaming);
			r++;
		}

		if (!includedUNK) {
			filteredRenamings.add(new Renaming("UNK_SYMBOL", threshold, 0,
					suggestedRenamings.first().scope));
		}

		return filteredRenamings;
	}

	/**
	 * Compute the score of the suggested renamings for the current identifier
	 * name. This functions computes the gap (g function) for the given renaming
	 * and the current identifier naming.
	 * 
	 * @param suggestedRenamings
	 * @param currentIdentifierName
	 * @param useUNKs
	 * @return
	 */
	public static double getScore(final SortedSet<Renaming> suggestedRenamings,
			final String currentIdentifierName, final boolean useUNKs) {
		final double firstScore = suggestedRenamings.first().score;
		double currentNameScore = 0;
		for (final Renaming renaming : suggestedRenamings) {
			if (renaming.name.equals(currentIdentifierName)
					|| (renaming.name.equals("UNK_SYMBOL") && useUNKs)) {
				currentNameScore = renaming.score;
				break;
			}
		}
		return currentNameScore - firstScore;
	}

	public static double getThresholdFor(final String identifierType) {
		if (identifierType.equals(MethodScopeExtractor.METHOD_CALL)) {
			return SUGGESTION_THRESHOLD_METHOD;
		} else if (identifierType.equals(TypenameScopeExtractor.TYPENAME)) {
			return SUGGESTION_THRESHOLD_TYPE;
		} else {
			return SUGGESTION_THRESHOLD_VAR;
		}
	}

	/**
	 * Score a snippet given by the scopes.
	 * 
	 * @param snippetScopes
	 * @return
	 * @throws IOException
	 */
	public static SnippetSuggestions scoreSnippet(final ASTNode node,
			final AbstractIdentifierRenamings renamer,
			final IScopeExtractor scopeExtractor) throws IOException {
		return scoreSnippet(node, renamer, scopeExtractor, true, true);
	}

	public static SnippetSuggestions scoreSnippet(final ASTNode node,
			final AbstractIdentifierRenamings renamer,
			final IScopeExtractor scopeExtractor,
			final boolean filterSuggestions, final boolean useUNK)
			throws IOException {
		final SegmentRenamingSuggestion srs = new SegmentRenamingSuggestion(
				renamer, scopeExtractor, useUNK);
		final SortedSet<Suggestion> suggestions = srs.rankSuggestions(node);
		final SortedSet<Suggestion> filteredSuggestions = Sets.newTreeSet();

		double score = 0;
		for (final Suggestion suggestion : suggestions) {
			final SortedSet<Renaming> filteredRenamings;
			if (filterSuggestions) {
				filteredRenamings = applyThresholdToRenamings(
						suggestion.getRenamings(),
						getThresholdFor(suggestion.scope.type));
			} else {
				filteredRenamings = suggestion.getRenamings();
			}
			final double currentScore = getScore(filteredRenamings,
					suggestion.identifierName, useUNK);
			if (Double.compare(currentScore, 0) == 0) {
				continue;
			}
			filteredSuggestions.add(new Suggestion(suggestion
					.getIdentifierName(), suggestion.scope, -currentScore,
					filteredRenamings));
			score += currentScore;
		}
		return new SnippetSuggestions(Sets.newTreeSet(filteredSuggestions),
				score);
	}

	private final AbstractIdentifierRenamings renamer;

	private final IScopeExtractor scopeExtractor;

	/**
	 * 
	 */
	public SnippetScorer(final AbstractIdentifierRenamings renamer,
			final IScopeExtractor extractor) {
		this.renamer = renamer;
		scopeExtractor = extractor;
	}

	public SnippetSuggestions scoreSnippet(final ASTNode node,
			final boolean useUNK) throws IOException {
		return scoreSnippet(node, renamer, scopeExtractor, true, useUNK);
	}
}
