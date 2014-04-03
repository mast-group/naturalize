/**
 * 
 */
package renaming.segmentranking;

import static com.google.common.base.Preconditions.checkArgument;

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

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.segmentranking.SegmentRenamingSuggestion.Suggestion;
import codemining.java.codeutils.scopes.ScopedIdentifierRenaming;
import codemining.java.codeutils.scopes.ScopesTUI;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;
import codemining.languagetools.Scope;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Randomly perturbate identifiers and evaluate ranking.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PerturbationEvaluator {

	private static class EvaluationResults {
		public static final int RANK_SIZE = 20;
		long[] tp = new long[RANK_SIZE];
		long total = 0;
		double reciprocalRankSum = 0;

		public void printStats() {
			final StringBuffer sb = new StringBuffer();
			for (int i = 0; i < RANK_SIZE; i++) {
				sb.append(((double) tp[i]) / total + ",");
			}
			sb.append(reciprocalRankSum / total);

			System.out.println(sb.toString());
		}
	}

	private class Evaluator implements Runnable {

		final File testFile;

		Evaluator(final File testedFile) {
			testFile = testedFile;
		}

		@Override
		public void run() {
			final Collection<File> trainFiles = Sets.newTreeSet(allFiles);
			checkArgument(trainFiles.remove(testFile));
			try {
				evaluateFile(testFile, trainFiles);
			} catch (IllegalArgumentException e) {
				LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
				throw new IllegalArgumentException(e);
			} catch (SecurityException e) {
				LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
				throw new IllegalArgumentException(e);
			} catch (InstantiationException e) {
				LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
				throw new IllegalArgumentException(e);
			} catch (IllegalAccessException e) {
				LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
				throw new IllegalArgumentException(e);
			} catch (InvocationTargetException e) {
				LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
				throw new IllegalArgumentException(e);
			} catch (NoSuchMethodException e) {
				LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
				throw new IllegalArgumentException(e);
			} catch (ClassNotFoundException e) {
				LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
				throw new IllegalArgumentException(e);
			} catch (IOException e) {
				LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
				throw new IllegalArgumentException(e);
			}
		}

	}

	/**
	 * Print the data.
	 * 
	 */
	private class Printer implements Runnable {
		@Override
		public void run() {
			er.printStats();
		}

	}

	private static final Logger LOGGER = Logger
			.getLogger(PerturbationEvaluator.class.getName());

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 3) {
			System.err
					.println("Usage <directory> <renamerClass> variable|method");
			return;
		}

		final IScopeExtractor scopeExtractor = ScopesTUI
				.getScopeExtractorByName(args[2]);

		final String renamerClass = args[1];

		final File directory = new File(args[0]);

		final PerturbationEvaluator pe = new PerturbationEvaluator(directory,
				new JavaTokenizer(), scopeExtractor, renamerClass);

		pe.performEvaluation();
		pe.er.printStats();

	}

	final ITokenizer tokenizer;

	final EvaluationResults er = new EvaluationResults();

	final IScopeExtractor scopeExtractor;

	final ScopedIdentifierRenaming varRenamer;

	final String renamerClass;

	final Collection<File> allFiles;

	public PerturbationEvaluator(final File directory,
			final ITokenizer tokenizer, final IScopeExtractor scopeExtractor,
			final String renamerClass) {
		allFiles = FileUtils.listFiles(directory, tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);
		this.tokenizer = tokenizer;
		this.scopeExtractor = scopeExtractor;
		this.renamerClass = renamerClass;
		varRenamer = new ScopedIdentifierRenaming(scopeExtractor,
				ParseType.COMPILATION_UNIT);
	}

	void evaluateFile(final File testFile, final Collection<File> trainFiles)
			throws IllegalArgumentException, SecurityException,
			InstantiationException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException,
			ClassNotFoundException, IOException {
		final AbstractIdentifierRenamings renamer = (AbstractIdentifierRenamings) Class
				.forName(renamerClass).getDeclaredConstructor(ITokenizer.class)
				.newInstance(tokenizer);
		renamer.buildRenamingModel(trainFiles);
		final Multimap<Scope, String> scopes = scopeExtractor
				.getFromFile(testFile);
		final String targetPertubedName = "mblamblambla";

		for (final Entry<Scope, String> entry : scopes.entries()) {
			// TODO, here instead of reading again, give the method
			final Multimap<Scope, String> perturbed = perturbedScopes(
					FileUtils.readFileToString(testFile), entry.getKey(),
					entry.getValue(), targetPertubedName);
			final SegmentRenamingSuggestion rn = new SegmentRenamingSuggestion(
					renamer, true);
			final SortedSet<Suggestion> sg = rn.rankSuggestions(perturbed);
			pushResults(sg, targetPertubedName);
		}
	}

	public void performEvaluation() {
		final ParallelThreadPool threadPool = new ParallelThreadPool();
		int fileNo = 0;
		for (final File f : allFiles) {
			threadPool.pushTask(new Evaluator(f));
			fileNo++;
			if (fileNo % 10 == 0) {
				threadPool.pushTask(new Printer());
			}
		}
		threadPool.waitForTermination();
	}

	public Multimap<Scope, String> perturbedScopes(final String fileContent,
			final Scope scope, final String from, final String to) {
		final Multimap<Scope, String> copy = varRenamer.getRenamedScopes(scope,
				from, to, fileContent);

		return copy;
	}

	private synchronized void pushResults(
			final SortedSet<Suggestion> suggestions, final String target) {
		if (suggestions.size() == 1) {
			return; // this is not something we should use...
		}

		er.total++;
		int pos = 0;
		boolean found = false;
		for (final Suggestion sg : suggestions) {
			pos++;
			if (sg.identifierName.equals(target)) {
				found = true;
				break;
			}
		}
		if (!found)
			return;

		er.reciprocalRankSum += 1. / ((double) pos);
		for (int i = pos; i <= EvaluationResults.RANK_SIZE; i++) {
			er.tp[i - 1]++;
		}
	}
}
