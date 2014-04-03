/**
 * 
 */
package renaming.evaluation;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.RandomUtils;

import renaming.renamers.BaseIdentifierRenamings;
import renaming.renamers.INGramIdentifierRenamer.Renaming;
import codemining.java.codeutils.scopes.ScopesTUI;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.Scope;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;

/**
 * Rename the percent of renamings that we keep UNK after a renaming.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class UnkRenamingsEval {

	private class Evaluator implements Runnable {

		final File testFile;

		public Evaluator(final File testFile) {
			this.testFile = testFile;
		}

		@Override
		public void run() {
			try {
				final Set<File> trainFiles = Sets.newTreeSet(allFiles);
				checkArgument(trainFiles.remove(testFile));
				final BaseIdentifierRenamings renamer = new BaseIdentifierRenamings(
						new JavaTokenizer());
				renamer.buildRenamingModel(trainFiles);

				final Multimap<Scope, String> scopes = scopeExtractor
						.getFromFile(testFile);
				final Multimap<Scope, String> unkEntries = TreeMultimap
						.create();
				for (final Entry<Scope, String> entry : scopes.entries()) {
					if (renamer.getLM().getTrie().isUNK(entry.getValue())) {
						unkEntries.put(entry.getKey(), entry.getValue());
					}
				}

				int[] nReturnedUNK = new int[THRESHOLD_VALUES.length];
				for (final Entry<Scope, String> entry : unkEntries.entries()) {
					final SortedSet<Renaming> renamings = renamer.getRenamings(
							entry.getKey(), entry.getValue());
					if (renamings.isEmpty()) {
						continue;
					}
					if (renamings.first().name.equals("UNK_SYMBOL")) {
						for (int i = 0; i < nReturnedUNK.length; i++) {
							nReturnedUNK[i]++;
						}
					} else {
						double unkScore = 0;
						for (final Renaming renaming : renamings) {
							if (renaming.name.equals("UNK_SYMBOL")) {
								unkScore = renaming.score;
							}
						}
						checkArgument(unkScore != 0);

						for (int i = 0; i < nReturnedUNK.length; i++) {
							if (unkScore - renamings.first().score < THRESHOLD_VALUES[i]) {
								nReturnedUNK[i]++;
							}
						}
					}
				}
				result.updateResults(nReturnedUNK, unkEntries.size());
			} catch (IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}

		}

	}

	public class ResultObject {

		private long[] nUNK = new long[THRESHOLD_VALUES.length];
		private long nTotal = 0;

		public void print() {
			System.out.println(Arrays.toString(nUNK));
			final double[] pCorrect = new double[THRESHOLD_VALUES.length];
			for (int i = 0; i < THRESHOLD_VALUES.length; i++) {
				pCorrect[i] = ((double) nUNK[i]) / nTotal;
			}
			System.out.println("pUNK=" + Arrays.toString(pCorrect));
			System.out.println("nUNK=" + Arrays.toString(nUNK));
			System.out.println("nTotal=" + nTotal);
		}

		public synchronized void updateResults(final int[] correct,
				final int total) {
			checkArgument(correct.length == THRESHOLD_VALUES.length);
			for (int i = 0; i < THRESHOLD_VALUES.length; i++) {
				nUNK[i] += correct[i];
			}
			nTotal += total;
		}

	}

	private static final Logger LOGGER = Logger
			.getLogger(UnkRenamingsEval.class.getName());

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 2) {
			System.err.println("Usge <projectDir> all|variable|method|type");
			return;
		}

		final File prj = new File(args[0]);
		final IScopeExtractor extractor = ScopesTUI
				.getScopeExtractorByName(args[1]);

		final UnkRenamingsEval eval = new UnkRenamingsEval(prj, extractor);
		eval.runExperiment();
	}

	final Collection<File> allFiles;

	final IScopeExtractor scopeExtractor;

	final ResultObject result = new ResultObject();

	public static final double[] THRESHOLD_VALUES = { .1, .5, 1, 1.5, 2, 2.5,
			3, 3.5, 4, 5, 6, 7, 8, 10 };

	/**
	 * 
	 */
	public UnkRenamingsEval(final File directory,
			final IScopeExtractor extractor) {
		allFiles = FileUtils.listFiles(directory,
				(new JavaTokenizer()).getFileFilter(),
				DirectoryFileFilter.DIRECTORY);
		scopeExtractor = extractor;
	}

	public void runExperiment() {
		final ParallelThreadPool tp = new ParallelThreadPool();

		for (final File f : allFiles) {
			tp.pushTask(new Evaluator(f));
			if (RandomUtils.nextDouble() < .02) {
				tp.pushTask(new Runnable() {
					@Override
					public void run() {
						result.print();
					}
				});
			}
		}
		tp.waitForTermination();
		result.print();
	}

}
