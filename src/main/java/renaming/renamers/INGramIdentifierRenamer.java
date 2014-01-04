package renaming.renamers;

import java.util.Set;
import java.util.SortedSet;

import codemining.languagetools.Scope;
import codemining.lm.ngram.NGram;

import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Multiset;

/**
 * An interface for all the classes that score a set of n-grams based on a set
 * of alternatives.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public interface INGramIdentifierRenamer {

	public static class Renaming implements Comparable<Renaming> {
		public final String name;

		public final double score;

		public final int nContexts;

		public final Scope scope;

		public Renaming(final String id, final double xEntropy,
				final int contexts, final Scope renamingScope) {
			name = id;
			score = xEntropy;
			nContexts = contexts;
			scope = renamingScope;
		}

		@Override
		public int compareTo(final Renaming other) {
			return ComparisonChain.start().compare(score, other.score)
					.compare(name, other.name).result();
		}

		@Override
		public boolean equals(final Object other) {
			if (!(other instanceof Renaming)) {
				return false;
			}
			final Renaming r = (Renaming) other;
			return Objects.equal(name, r.name) && Objects.equal(score, r.score);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(name, score);

		}

		@Override
		public String toString() {
			return name + ":" + String.format("%.2f", score);
		}

	}

	public static final String WILDCARD_TOKEN = "%WC%";

	/**
	 * Calculate the scores for each renaming and return in sorted map. Each
	 * n-gram should contain the WILDCARD_TOKEN at the position of the token to
	 * be renamed.
	 * 
	 * @param ngrams
	 * @param renamings
	 * @param type
	 * @return
	 */
	public abstract SortedSet<Renaming> calculateScores(
			Multiset<NGram<String>> ngrams, Set<String> alternatives,
			Scope scope);

}