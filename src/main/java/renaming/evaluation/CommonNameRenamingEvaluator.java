/**
 * 
 */
package renaming.evaluation;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;
import renaming.renamers.INGramIdentifierRenamer.Renaming;
import codemining.java.codeutils.scopes.VariableScopeExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.smoothing.StupidBackoff;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Evaluate if and how we use common (and maybe junk?) names.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class CommonNameRenamingEvaluator {

	/**
	 * A runnable to evaluate the junk-ness of a renaming.
	 * 
	 */
	private class EvaluationRunnable implements Runnable {

		final File evaluatedFile;

		public EvaluationRunnable(File fi) {
			evaluatedFile = fi;
		}

		/**
		 * @param renamer
		 * @param scopes
		 */
		private void evaluateJunkRenamings(
				final AbstractIdentifierRenamings renamer,
				final Multimap<Scope, String> scopes) {
			for (final Entry<Scope, String> variable : scopes.entries()) {
				try {
					final SortedSet<Renaming> renamings = renamer.getRenamings(
							variable.getKey(), variable.getValue());
					final boolean weServeJunk = junkVariables
							.contains(renamings.first().name);
					final boolean variableWasJunk = junkVariables
							.contains(variable.getValue());
					updateResults(variableWasJunk, weServeJunk);
				} catch (Throwable e) {
					LOGGER.warning("Failed to evaluate renaming " + variable
							+ " " + ExceptionUtils.getFullStackTrace(e));
				}
			}
		}

		@Override
		public void run() {
			try {
				final Collection<File> trainFiles = new TreeSet<File>();
				trainFiles.addAll(allFiles);
				checkArgument(trainFiles.remove(evaluatedFile));

				final Collection<File> testFiles = Lists.newArrayList();
				testFiles.add(evaluatedFile);

				final AbstractIdentifierRenamings renamer = new BaseIdentifierRenamings(
						tokenizer);

				renamer.buildRenamingModel(trainFiles);

				final Multimap<Scope, String> m;
				m = scopeExtractor.getFromFile(evaluatedFile);

				evaluateJunkRenamings(renamer, m);

			} catch (Exception e) {
				LOGGER.warning("Error in file "
						+ evaluatedFile.getAbsolutePath() + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

	private class Printer implements Runnable {

		@Override
		public void run() {
			System.out.println(totalNotJunk + "," + totalJunk + ","
					+ totalJunkToNotJunk + "," + totalNotJunkToJunk);
		}

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 5) {
			System.err.println("Usage <folder> <junkNames> ...");
			return;
		}

		final File directory = new File(args[0]);

		final ITokenizer tokenizer = new JavaTokenizer();

		final Class<? extends AbstractNGramLM> smoothedNgramClass = StupidBackoff.class;

		final CommonNameRenamingEvaluator evaluator = new CommonNameRenamingEvaluator(
				directory, tokenizer, smoothedNgramClass,
				Sets.newTreeSet(Arrays.asList(args).subList(1, args.length)));
		evaluator.evaluate();
		evaluator.printResults();

	}

	final Collection<File> allFiles;

	final ITokenizer tokenizer;

	final Class<? extends AbstractNGramLM> smoothedNgramClass;
	private static final Logger LOGGER = Logger
			.getLogger(CommonNameRenamingEvaluator.class.getName());
	final Set<String> junkVariables;
	int totalJunk;

	int totalNotJunk;

	int totalJunkToNotJunk;

	int totalNotJunkToJunk;

	final IScopeExtractor scopeExtractor = new VariableScopeExtractor.VariableScopeSnippetExtractor();

	/**
	 * @param smoothedNgramClass
	 * @param tokenizer
	 * @param directory
	 * 
	 */
	public CommonNameRenamingEvaluator(File directory, ITokenizer tokenizer,
			Class<? extends AbstractNGramLM> smoothedNgramClass,
			Set<String> junkVariables) {
		allFiles = FileUtils.listFiles(directory, tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);
		this.tokenizer = tokenizer;
		this.smoothedNgramClass = smoothedNgramClass;
		this.junkVariables = junkVariables;
	}

	public void evaluate() {
		final ParallelThreadPool threadPool = new ParallelThreadPool();
		int fileNo = 0;
		for (final File fi : allFiles) {
			threadPool.pushTask(new EvaluationRunnable(fi));
			fileNo++;
			if (fileNo % 10 == 0) {
				threadPool.pushTask(new Printer());
			}
		}
		threadPool.waitForTermination();
	}

	public void printResults() {
		(new Printer()).run();
	}

	/**
	 * Update the results.
	 * 
	 * @param isJunk
	 * @param servedJunk
	 */
	private synchronized void updateResults(final boolean isJunk,
			final boolean servedJunk) {
		if (isJunk) {
			totalJunk++;
			if (!servedJunk) {
				totalJunkToNotJunk++;
			}
		} else {
			totalNotJunk++;
			if (servedJunk) {
				totalNotJunkToJunk++;
			}
		}

	}

}
