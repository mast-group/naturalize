/**
 * 
 */
package renaming.segmentranking;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jdt.core.dom.ASTNode;

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;
import renaming.renamers.INGramIdentifierRenamer.Renaming;
import codemining.java.codeutils.scopes.ScopesTUI;
import codemining.java.codeutils.scopes.VariableScopeExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope;

import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Rank renaming suggestions based on the confidence we have.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class SegmentRenamingSuggestion {

	/**
	 * Struct class for suggestions.
	 * 
	 */
	public static class Suggestion implements Comparable<Suggestion> {

		final double confidenceGap;

		public final Scope scope;

		final String identifierName;

		final SortedSet<Renaming> renamings;

		Suggestion(final String idName, final Scope scope,
				final double confidence, final SortedSet<Renaming> alternatives) {
			identifierName = idName;
			this.scope = scope;
			renamings = alternatives;
			confidenceGap = confidence;
		}

		@Override
		public int compareTo(final Suggestion other) {
			return ComparisonChain.start()
					.compare(confidenceGap, other.confidenceGap)
					.compare(identifierName, other.identifierName)
					.compare(scope, other.scope).result();
		}

		@Override
		public boolean equals(final Object obj) {
			if (!(obj instanceof Suggestion)) {
				return false;
			}

			final Suggestion other = (Suggestion) obj;
			return Double.compare(confidenceGap, other.confidenceGap) == 0
					&& identifierName.compareTo(other.identifierName) == 0
					&& scope.compareTo(other.scope) == 0;

		}

		public double getConfidence() {
			return confidenceGap;
		}

		public String getIdentifierName() {
			return identifierName;
		}

		/**
		 * Return the probability that this renaming will happen.
		 * 
		 * @return
		 */
		public double getProbNotRename() {
			double probNotRename = 0;
			final String targetIdentifier = getIdentifierName();
			double sum = 0;
			for (final Renaming renaming : getRenamings()) {
				sum += Math.pow(2, -renaming.score);
			}
			for (final Renaming renaming : getRenamings()) {
				if (renaming.name.equals(targetIdentifier)
						|| renaming.name.equals("UNK_SYMBOL")) {
					probNotRename += Math.pow(2, -renaming.score) / sum;
				}
			}
			return probNotRename;
		}

		public SortedSet<Renaming> getRenamings() {
			return renamings;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(identifierName, scope, confidenceGap);
		}

		@Override
		public String toString() {
			return identifierName + "(" + confidenceGap + ")";
		}

	}

	private static final Logger LOGGER = Logger
			.getLogger(SegmentRenamingSuggestion.class.getName());

	public static SortedSet<Suggestion> getVariableSuggestions(
			final File currentFile, final File directory, final boolean useUNK)
			throws IOException {
		final ITokenizer tokenizer = new JavaTokenizer();

		final AbstractIdentifierRenamings renamer = new BaseIdentifierRenamings(
				tokenizer);

		final Collection<java.io.File> trainingFiles = FileUtils.listFiles(
				directory, tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);

		trainingFiles.remove(currentFile);

		renamer.buildRenamingModel(trainingFiles);

		final IScopeExtractor scopeExtractor = new VariableScopeExtractor.VariableScopeSnippetExtractor();

		final SegmentRenamingSuggestion suggestion = new SegmentRenamingSuggestion(
				renamer, scopeExtractor, useUNK);

		return suggestion.rankSuggestions(currentFile);
	}

	public static void main(final String[] args)
			throws IllegalArgumentException, SecurityException,
			InstantiationException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException,
			ClassNotFoundException, IOException {
		if (args.length < 4) {
			System.err
					.println("Usage <TestFile> <TrainDirectory> <renamerClass> variable|method");
			return;
		}

		final ITokenizer tokenizer = new JavaTokenizer();

		final AbstractIdentifierRenamings renamer = (AbstractIdentifierRenamings) Class
				.forName(args[2]).getDeclaredConstructor(ITokenizer.class)
				.newInstance(tokenizer);

		renamer.buildRenamingModel(FileUtils.listFiles(new File(args[1]),
				tokenizer.getFileFilter(), DirectoryFileFilter.DIRECTORY));

		final IScopeExtractor scopeExtractor = ScopesTUI
				.getScopeExtractorByName(args[3]);
		final SegmentRenamingSuggestion suggestion = new SegmentRenamingSuggestion(
				renamer, scopeExtractor, true);

		System.out.println(suggestion.rankSuggestions(new File(args[0])));

	}

	final AbstractIdentifierRenamings renamer;

	final IScopeExtractor scopeExtractor;

	final boolean useUNK;

	public SegmentRenamingSuggestion(final AbstractIdentifierRenamings renamer,
			final boolean useUNK) {
		this.renamer = renamer;
		scopeExtractor = null;
		this.useUNK = useUNK;
	}

	/**
	 * 
	 */
	public SegmentRenamingSuggestion(final AbstractIdentifierRenamings renamer,
			final IScopeExtractor extractor, final boolean useUNK) {
		this.renamer = renamer;
		scopeExtractor = extractor;
		this.useUNK = useUNK;
	}

	private Suggestion addRenamingSuggestion(
			final SortedSet<Renaming> renamings, final String idName,
			final Scope scope) {
		final double topSuggestionXEnt = renamings.first().score;
		double currentNameXent = Double.NaN;

		for (final Renaming renaming : renamings) {
			if (renaming.name.equals(idName)
					|| (renaming.name.equals("UNK_SYMBOL") && useUNK)) {
				currentNameXent = renaming.score;
				break;
			}
		}

		checkArgument(!Double.isNaN(currentNameXent));

		final double confidence = topSuggestionXEnt - currentNameXent;
		return new Suggestion(idName, scope, confidence, renamings);
	}

	public void buildModel(final Collection<File> trainingFiles) {
		renamer.buildRenamingModel(trainingFiles);
	}

	public SortedSet<Suggestion> rankSuggestions(final ASTNode node)
			throws IOException {
		final Multimap<Scope, String> identifiers = checkNotNull(
				scopeExtractor, "No scope extractor available").getFromNode(
				node);
		return rankSuggestions(identifiers);
	}

	public SortedSet<Suggestion> rankSuggestions(final File f)
			throws IOException {
		final Multimap<Scope, String> identifiers = checkNotNull(
				scopeExtractor, "No scope extractor available").getFromFile(f);
		return rankSuggestions(identifiers);
	}

	/**
	 * @param identifiers
	 * @return
	 */
	public SortedSet<Suggestion> rankSuggestions(
			final Multimap<Scope, String> identifiers) {
		final SortedSet<Suggestion> suggestions = Sets.newTreeSet();
		for (final Entry<Scope, String> s : identifiers.entries()) {
			try {
				final SortedSet<Renaming> renamings = renamer.getRenamings(
						s.getKey(), s.getValue());
				suggestions.add(addRenamingSuggestion(renamings, s.getValue(),
						s.getKey()));
			} catch (final Throwable e) {
				LOGGER.warning("Failed to get suggestions for " + s
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}

		return suggestions;
	}
}
