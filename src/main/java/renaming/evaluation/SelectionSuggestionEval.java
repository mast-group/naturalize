/**
 * 
 */
package renaming.evaluation;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;
import renaming.segmentranking.SnippetScorer;
import renaming.segmentranking.SnippetScorer.SnippetSuggestions;
import codemining.java.codedata.MethodRetriever;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.codeutils.scopes.ScopedIdentifierRenaming;
import codemining.java.codeutils.scopes.ScopesTUI;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;
import codemining.languagetools.Scope;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Compare snippets based on their score. Have we found the purturbed one?
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class SelectionSuggestionEval {

	public static class EvaluationResults {
		// The various thresholds to use for the Charles measure when rejecting
		public static final double[] THRESHOLDS = { .1, .5, 1, 1.5, 2, 2.5, 3,
				5, 7, 9, 11, 13, 15, 16, 17, 18, 19, 20, 21, 22, 23, 25, 27,
				28, 29, 30, 35, 40 };

		// The various percentage of perturbation to consider
		public static final double[] PERCENTAGE_PERTURBATION = { 0, .1, .2, .3,
				.4, .5, .6, .7, .8, .9, 1 };

		private final long[][] nReject = new long[PERCENTAGE_PERTURBATION.length][THRESHOLDS.length];

		private long nSnippetsConsidered = 0;

		public void printResults() {
			System.out.println("t=" + Arrays.toString(THRESHOLDS));
			System.out
					.println("pp=" + Arrays.toString(PERCENTAGE_PERTURBATION));
			System.out.print("rejectVals=[");
			for (int i = 0; i < nReject.length; i++) {
				System.out.print(Arrays.toString(nReject[i]) + ";");
			}
			System.out.println("]");
			System.out.println("nConsidered=" + nSnippetsConsidered);
		}

		public synchronized void pushResult(
				final double[] scoresAtPerturbationLevels) {
			checkArgument(scoresAtPerturbationLevels.length == PERCENTAGE_PERTURBATION.length);
			nSnippetsConsidered++;

			for (int i = 0; i < PERCENTAGE_PERTURBATION.length; i++) {
				for (int j = 0; j < THRESHOLDS.length; j++) {
					if (scoresAtPerturbationLevels[i] > THRESHOLDS[j]) {
						nReject[i][j]++;
					}
				}
			}

		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length != 3) {
			System.err
					.println("Usage <projectDir> variable|method|type|all <nExperiments>");
			return;
		}

		final SelectionSuggestionEval sse = new SelectionSuggestionEval(
				new File(args[0]), new JavaTokenizer(),
				ScopesTUI.getScopeExtractorByName(args[1]));

		final int nExperiments = Integer.parseInt(args[2]);
		final ParallelThreadPool pt = new ParallelThreadPool();
		for (int i = 0; i < nExperiments; i++) {
			pt.pushTask(new Runnable() {

				@Override
				public void run() {
					try {
						sse.runSingleExperiment();
					} catch (IOException e) {
						LOGGER.warning("Failed to run single experiment "
								+ ExceptionUtils.getFullStackTrace(e));
					}
				}

			});

			if (RandomUtils.nextDouble() < .1) {
				pt.pushTask(new Runnable() {
					@Override
					public void run() {
						sse.results.printResults();
					}
				});
			}
		}

		pt.waitForTermination();
		sse.results.printResults();
	}

	final Collection<File> allFiles;

	final ITokenizer tokenizer;

	final IScopeExtractor scopeExtractor;

	final EvaluationResults results = new EvaluationResults();

	private static final Logger LOGGER = Logger
			.getLogger(SelectionSuggestionEval.class.getName());

	// Number of snippets to evaluate per time
	public static final int N_SNIPPETS = 500;

	/**
	 * 
	 */
	public SelectionSuggestionEval(final File directory,
			final ITokenizer codeTokenizer, final IScopeExtractor extractor) {
		tokenizer = codeTokenizer;
		allFiles = FileUtils.listFiles(directory, tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);
		scopeExtractor = extractor;
	}

	/**
	 * @param renamer
	 * @return
	 */
	public List<File> buildRenamerAndGetTargetMethods(
			final BaseIdentifierRenamings renamer) {
		final List<File> testFiles = Lists.newArrayList();

		final List<File> allFilesList = Lists.newArrayList(allFiles);
		Collections.shuffle(allFilesList);

		int nGatheredMethods = 0;
		for (final File f : allFilesList) {
			try {
				final Map<String, MethodDeclaration> fileMethods = MethodRetriever
						.getMethodNodes(f);
				if (fileMethods.size() == 0) {
					continue;
				}
				nGatheredMethods += fileMethods.size();
				testFiles.add(f);
			} catch (Throwable e) {
				LOGGER.warning("Error in file " + f.getAbsolutePath() + "\n"
						+ ExceptionUtils.getFullStackTrace(e));
			}
			if (nGatheredMethods >= N_SNIPPETS) {
				break;
			}
		}
		checkArgument(nGatheredMethods >= N_SNIPPETS);

		final Set<File> trainingFiles = Sets.newTreeSet(allFiles);
		checkArgument(trainingFiles.removeAll(testFiles));

		renamer.buildRenamingModel(trainingFiles);
		return testFiles;
	}

	private void evaluatePerformanceOn(final MethodDeclaration currentMethod,
			final AbstractIdentifierRenamings renamer,
			final List<String> randomVars) throws IOException {
		final JavaASTExtractor ex = new JavaASTExtractor(false);
		final ScopedIdentifierRenaming identifierRenamer = new ScopedIdentifierRenaming(
				scopeExtractor, ParseType.METHOD);
		final double[] scoreAtPerturbationLevel = new double[EvaluationResults.PERCENTAGE_PERTURBATION.length];
		final Multimap<Scope, String> scopes = scopeExtractor
				.getFromNode(currentMethod);

		if (scopes.entries().size() < EvaluationResults.PERCENTAGE_PERTURBATION.length - 1) {
			return;
		}

		// Deduplicate keys. We will use the same!
		final List<String> identifiersToBeRenamed = Lists.newArrayList(Sets
				.newTreeSet(scopes.values()));
		Collections.shuffle(identifiersToBeRenamed);

		for (int i = 0; i < EvaluationResults.PERCENTAGE_PERTURBATION.length; i++) {
			// Perturb this extra percent of identifiers
			final Map<String, String> renamingPlan = generateRenamingPlan(
					identifiersToBeRenamed,
					EvaluationResults.PERCENTAGE_PERTURBATION[i], renamer,
					randomVars);
			// rename using the renamer naively. Note that we don't care about
			// scopes
			// and naming collisions here, some will happen. Life is hard.
			final String renamedMethod = identifierRenamer.getRenamedCode(
					currentMethod.toString(), currentMethod.toString(),
					renamingPlan);
			// and create an AST Node
			final ASTNode renamedMethodNode = ex.getASTNode(renamedMethod,
					ParseType.METHOD);

			// get score
			final SnippetSuggestions ss = SnippetScorer.scoreSnippet(
					renamedMethodNode, renamer, scopeExtractor, false, false);

			scoreAtPerturbationLevel[i] = ss.score / ss.suggestions.size();
		}
		results.pushResult(scoreAtPerturbationLevel);
	}

	/**
	 * Returns a plan of how each variable name will be renamed.
	 * 
	 * @param identifiersToBeRenamed
	 * @param percent
	 * @return
	 */
	private Map<String, String> generateRenamingPlan(
			final List<String> identifiersToBeRenamed, final double percent,
			final AbstractIdentifierRenamings renamer,
			final List<String> randomVars) {
		checkArgument(percent <= 1);
		checkArgument(percent >= 0);
		final int upTo = (int) (percent * identifiersToBeRenamed.size());
		final Map<String, String> renamingPlan = Maps.newTreeMap();

		for (int i = 0; i < upTo; i++) {
			final String rName = getRandomName(randomVars, renamer.getLM()
					.getTokenizer());
			renamingPlan.put(identifiersToBeRenamed.get(i), rName);
		}
		return renamingPlan;
	}

	private String getRandomName(final List<String> randomVars,
			final ITokenizer tokenizer) {
		String name = randomVars.get(RandomUtils.nextInt(randomVars.size()));
		while (!tokenizer.getTokenFromString(name).tokenType.equals(tokenizer
				.getIdentifierType())) {
			name = randomVars.get(RandomUtils.nextInt(randomVars.size()));
		}
		return name;
	}

	public void runSingleExperiment() throws IOException {
		final BaseIdentifierRenamings renamer = new BaseIdentifierRenamings(
				tokenizer);
		final List<File> selectedFiles = buildRenamerAndGetTargetMethods(renamer);
		final List<String> allToks = Lists.newArrayList(renamer.getLM()
				.getTrie().getVocabulary());
		Collections.shuffle(allToks);
		for (final File f : selectedFiles) {
			try {
				for (final Entry<String, MethodDeclaration> method : MethodRetriever
						.getMethodNodes(f).entrySet()) {
					evaluatePerformanceOn(method.getValue(), renamer, allToks);
				}
			} catch (Throwable e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}

		}

	}
}
