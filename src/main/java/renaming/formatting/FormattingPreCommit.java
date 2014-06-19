/**
 * 
 */
package renaming.formatting;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.RandomUtils;

import renaming.renamers.INGramIdentifierRenamer.Renaming;
import renaming.segmentranking.SnippetScorer;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * A precommit to evaluate formatting
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class FormattingPreCommit {

	public class RejectEvaluator implements Runnable {
		private final File testFile;

		final FormattingRenamings fr = new FormattingRenamings();

		final List<String> allWhitespaceChars = Lists.newArrayList();

		public RejectEvaluator(final File testFile) {
			this.testFile = testFile;
		}

		public void evaluate() throws IOException {
			final Collection<File> trainSet = Sets.newTreeSet(allFiles);
			trainSet.remove(testFile);
			fr.buildModel(trainSet);

			for (final String token : fr.getNgramLM().getTrie().getVocabulary()) {
				if (token.startsWith("WS_")) {
					allWhitespaceChars.add(token);
				}
			}

			final List<String> tokens = fr.tokenizeCode(FileUtils
					.readFileToString(testFile).toCharArray());

			final List<Integer> wsIndex = getWSIndex(tokens);
			Collections.shuffle(wsIndex);

			final List<String> perturbed = perturbTokens(tokens, wsIndex.get(0));

			double topPerturbedScore = 0;
			double topNormalScore = 0;
			for (final int pos : wsIndex) {
				final SortedSet<Renaming> normalRenaming = fr.calculateScores(
						fr.getNGramsAround(pos, tokens),
						Sets.newTreeSet(allWhitespaceChars), null);
				final double normalScore = SnippetScorer.getScore(
						normalRenaming, tokens.get(pos), false);
				if (normalScore > topNormalScore) {
					topNormalScore = normalScore;
				}

				final SortedSet<Renaming> perturbedRenaming = fr
						.calculateScores(fr.getNGramsAround(pos, perturbed),
								Sets.newTreeSet(allWhitespaceChars), null);
				final double perturbedScore = SnippetScorer.getScore(
						perturbedRenaming, perturbed.get(pos), false);
				if (perturbedScore > topPerturbedScore) {
					topPerturbedScore = perturbedScore;
				}

			}
			result.pushResult(topNormalScore, topPerturbedScore);
		}

		/**
		 * Return a random name.
		 * 
		 * @param original
		 * @return
		 */
		private String getRandomName(final String original) {
			String name = allWhitespaceChars.get(RandomUtils
					.nextInt(allWhitespaceChars.size()));
			while (name.equals(original)) {
				name = allWhitespaceChars.get(RandomUtils
						.nextInt(allWhitespaceChars.size()));
			}
			return name;
		}

		/**
		 * Return the index list of all whitespaces.
		 * 
		 * @param tokens
		 * @return
		 */
		private List<Integer> getWSIndex(final List<String> tokens) {
			final List<Integer> wsIndexes = Lists.newArrayList();
			for (int i = 0; i < tokens.size(); i++) {
				if (tokens.get(i).startsWith("WS_")) {
					wsIndexes.add(i);
				}
			}
			return wsIndexes;
		}

		/**
		 * Perturb the tokens at a given percentage.
		 * 
		 * @param tokens
		 * @param perturbationP
		 * @param wsIndex
		 * @return
		 */
		private List<String> perturbTokens(final List<String> tokens,
				int randPos) {
			final List<String> pTokens = Lists.newArrayList(tokens);
			pTokens.set(randPos, getRandomName(tokens.get(randPos)));
			return pTokens;
		}

		@Override
		public void run() {
			try {
				evaluate();
			} catch (IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

	public static class ResultObject {
		public static final double[] THRESHOLDS = { 0, .0001, .001, .01, .05,
				.1, .5, 1, 1.5, 2, 2.5, 3, 5, 7, 9, 10, 11, 12, 13, 14, 15, 16,
				17, 20, 30, 100 };

		private final long[] tpr = new long[THRESHOLDS.length];
		private final long[] fpr = new long[THRESHOLDS.length];

		private long nSnippetsConsidered = 0;

		public void printResults() {
			System.out.println("t=" + Arrays.toString(THRESHOLDS));
			System.out.println("tpr=" + Arrays.toString(tpr) + ";");
			System.out.println("fpr=" + Arrays.toString(fpr) + ";");

			System.out.println("nConsidered=" + nSnippetsConsidered);
		}

		public synchronized void pushResult(final double beforeScore,
				final double afterScore) {
			nSnippetsConsidered++;

			for (int i = 0; i < THRESHOLDS.length; i++) {
				if (beforeScore > THRESHOLDS[i]) {
					fpr[i]++;
				}
				if (afterScore > THRESHOLDS[i]) {
					tpr[i]++;
				}
			}

		}
	}

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		if (args.length < 1) {
			System.err.println("Usage <directory>");
			return;
		}

		FormattingPreCommit fpc = new FormattingPreCommit(new File(args[0]));
		fpc.runEval();
		fpc.result.printResults();

	}

	final ResultObject result = new ResultObject();

	final Collection<File> allFiles;

	public static final Logger LOGGER = Logger
			.getLogger(FormattingPreCommit.class.getName());

	/**
	 * 
	 */
	public FormattingPreCommit(final File projDirectory) {
		allFiles = FileUtils.listFiles(projDirectory,
				(new JavaTokenizer()).getFileFilter(),
				DirectoryFileFilter.DIRECTORY);
	}

	public void runEval() {
		final int nFileSamples = (int) Math.ceil(allFiles.size() * .5);
		final List<File> files = Lists.newArrayList(allFiles);
		Collections.shuffle(files);

		final ParallelThreadPool th = new ParallelThreadPool();
		for (int i = 0; i < nFileSamples; i++) {
			th.pushTask(new RejectEvaluator(files.get(i)));
			if (i % 10 == 0) {
				th.pushTask(new Runnable() {

					@Override
					public void run() {
						result.printResults();
					}

				});
			}
		}

		th.waitForTermination();
	}
}
