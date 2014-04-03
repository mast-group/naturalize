/**
 * 
 */
package renaming.tools;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;
import renaming.segmentranking.SnippetScorer;
import renaming.segmentranking.SnippetScorer.SnippetSuggestions;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.codeutils.scopes.VariableScopeExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.Sets;

/**
 * Rank-suggest renamings for a whole repository.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class JavaRepositoryRenamingSuggestor {

	private static final class NamedSnippetSuggestions extends
			SnippetSuggestions {

		public final File owningFile;

		public NamedSnippetSuggestions(final SnippetSuggestions suggestion,
				final File file) {
			super(suggestion.suggestions, suggestion.score);
			owningFile = file;
		}

		@Override
		public int compareTo(final SnippetSuggestions other) {
			return -super.compareTo(other);
		}

		public void print() {
			System.out
					.println("=========================================================\n");
			System.out.println("Suggestions for" + owningFile.getAbsolutePath()
					+ "\n");
			System.out
					.println("=========================================================");
			try {
				CodeReviewAssistant.printRenaming(this,
						FileUtils.readFileToString(owningFile), -1);
			} catch (final IOException e) {
				System.out.println("ERROR: Failed to read file");
			}
		}

	}

	private static final Logger LOGGER = Logger
			.getLogger(JavaRepositoryRenamingSuggestor.class.getName());

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		if (args.length != 2) {
			System.out
					.println("Usage <repositoryDir> <directoryToProduceSuggestionsFrom>");
			System.exit(-1);
		}

		final File repositoryDirectory = new File(args[0]);
		final File suggestionDirectory = new File(args[1]);
		final ITokenizer tokenizer = new JavaTokenizer();

		final Collection<File> allFiles = FileUtils.listFiles(
				repositoryDirectory, tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);
		final Collection<File> suggestFiles = FileUtils.listFiles(
				suggestionDirectory, tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);

		final Set<NamedSnippetSuggestions> suggestionSet = Sets
				.newConcurrentHashSet();
		final ParallelThreadPool ptp = new ParallelThreadPool();
		for (final File f : suggestFiles) {
			ptp.pushTask(new Runnable() {

				@Override
				public void run() {
					try {
						final AbstractIdentifierRenamings renamer = new BaseIdentifierRenamings(
								tokenizer);

						final Collection<File> trainFiles = Sets
								.newHashSet(allFiles);
						trainFiles.remove(f);
						renamer.buildRenamingModel(trainFiles);

						final IScopeExtractor scopeExtractor = new VariableScopeExtractor.VariableScopeSnippetExtractor();
						final JavaASTExtractor ex = new JavaASTExtractor(false);
						final SnippetScorer scorer = new SnippetScorer(renamer,
								scopeExtractor);

						final SnippetSuggestions suggestions = scorer
								.scoreSnippet(ex.getAST(f), true);
						if (!suggestions.suggestions.isEmpty()) {
							suggestionSet.add(new NamedSnippetSuggestions(
									suggestions, f));
						}
					} catch (final IOException e) {
						// Ah.. Nothing we can do..
						LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
					}
				}
			});

		}

		ptp.waitForTermination();

		final SortedSet<NamedSnippetSuggestions> allSuggestions = Sets
				.newTreeSet(suggestionSet);

		// Print suggestions
		for (final NamedSnippetSuggestions suggestion : allSuggestions) {
			if (!suggestion.suggestions.isEmpty()) {
				suggestion.print();
			}
		}

	}
}
