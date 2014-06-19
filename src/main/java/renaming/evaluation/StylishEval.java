/**
 * 
 */
package renaming.evaluation;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import renaming.formatting.FormattingRenamings;
import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;
import renaming.renamers.INGramIdentifierRenamer.Renaming;
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
import codemining.languagetools.TokenizerUtils;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Evaluate stylish? using custom perturbation. Bad and hacky to get the result
 * asap. Sorry.
 * 
 * @author Miltos Allamanis <m.allamanis@edd.ac.uk>
 * 
 */
public class StylishEval {

	/**
	 * Simple struct containing the stylish thresholds for a single snippet
	 * 
	 */
	public static class EvaluationResult {
		public double whitespaceRejectionScore;
		public double identifierRejectionScore;
		public boolean isPerturbed;
		public boolean isWhitespacePerturbation;

		@Override
		public String toString() {
			return identifierRejectionScore + "," + whitespaceRejectionScore
					+ "," + (isPerturbed ? 1 : 0) + ","
					+ (isWhitespacePerturbation ? 1 : 0);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Usage <projectDir>");
			return;
		}

		final StylishEval sse = new StylishEval(new File(args[0]),
				new JavaTokenizer(),
				ScopesTUI.getScopeExtractorByName("variable"));

		final int nExperiments = N_SNIPPETS;
		final ParallelThreadPool threadPool = new ParallelThreadPool();
		for (int i = 0; i < nExperiments; i++) {
			threadPool.pushTask(new Runnable() {

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
		}

		threadPool.waitForTermination();
		for (final EvaluationResult result : sse.results) {
			System.out.println(result);
		}
	}

	private final List<EvaluationResult> results = Collections
			.synchronizedList(new ArrayList<EvaluationResult>());

	final Collection<File> allFiles;

	final ITokenizer tokenizer;

	final IScopeExtractor scopeExtractor;

	private static final Logger LOGGER = Logger
			.getLogger(SelectionSuggestionEval.class.getName());

	// Number of snippets to evaluate per time
	public static final int N_SNIPPETS = 12;

	/**
	 * 
	 */
	public StylishEval(final File directory, final ITokenizer codeTokenizer,
			final IScopeExtractor extractor) {
		tokenizer = codeTokenizer;
		allFiles = FileUtils.listFiles(directory, tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);
		scopeExtractor = extractor;
	}

	/**
	 * Pick the files where we will evaluate on and build the identifier
	 * renamer.
	 * 
	 * @param renamer
	 * @return
	 */
	public List<File> buildRenamersAndGetTargetMethods(
			final BaseIdentifierRenamings renamer,
			final FormattingRenamings formattingRenamer) {
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
		formattingRenamer.buildModel(trainingFiles);
		return testFiles;
	}

	private void evaluatePerformanceOn(MethodDeclaration currentMethod,
			final String fileText, final AbstractIdentifierRenamings renamer,
			final FormattingRenamings formattingRenamer,
			final String varToRename) throws IOException {

		final JavaASTExtractor ex = new JavaASTExtractor(false);
		final ScopedIdentifierRenaming identifierRenamer = new ScopedIdentifierRenaming(
				scopeExtractor, ParseType.METHOD);

		final Multimap<Scope, String> scopes = scopeExtractor
				.getFromNode(currentMethod);

		if (scopes.entries().isEmpty()) {
			return;
		}

		final boolean doPerturb = RandomUtils.nextDouble() > .333;
		final boolean doWsPerturb = RandomUtils.nextBoolean();

		if (doPerturb && !doWsPerturb) {
			final Map<String, String> renamingPlan = Maps.newTreeMap();
			renamingPlan.put(varToRename, "DUMMY_VAR");

			// rename using the renamer naively. Note that we don't care about
			// scopes
			// and naming collisions here, some will happen. Life is hard.
			final String renamedMethod = identifierRenamer.getRenamedCode(
					currentMethod.toString(), currentMethod.toString(),
					renamingPlan);
			// and create an AST Node
			currentMethod = (MethodDeclaration) ex.getASTNode(renamedMethod,
					ParseType.METHOD);
		}

		final SnippetSuggestions ss = SnippetScorer.scoreSnippet(currentMethod,
				renamer, scopeExtractor, false, false);

		List<String> wsTokens = formattingRenamer.tokenizeCode(fileText
				.substring(
						currentMethod.getStartPosition(),
						currentMethod.getStartPosition()
								+ currentMethod.getLength()).toCharArray());
		TokenizerUtils.removeSentenceStartEndTokens(wsTokens);
		wsTokens = wsTokens.subList(1, wsTokens.size() - 1);
		final List<Integer> wsIndexes = Lists.newArrayList();
		for (int i = 0; i < wsTokens.size(); i++) {
			if (wsTokens.get(i).startsWith("WS_")) {
				wsIndexes.add(i);
			}
		}

		final Set<String> allWhitespaceChars = formattingRenamer.getNgramLM()
				.getTrie().getVocabulary();

		if (doPerturb && doWsPerturb) {
			final int randomPos = wsIndexes.get(RandomUtils.nextInt(wsIndexes
					.size()));
			final String targetWs = (String) allWhitespaceChars.toArray()[RandomUtils
					.nextInt(allWhitespaceChars.size())];
			wsTokens.set(randomPos, targetWs);
		}

		double wsRejectionScore = 0;
		for (final int pos : wsIndexes) {
			final SortedSet<Renaming> normalRenaming = formattingRenamer
					.calculateScores(
							formattingRenamer.getNGramsAround(pos, wsTokens),
							Sets.newTreeSet(allWhitespaceChars), null);
			final double normalScore = SnippetScorer.getScore(normalRenaming,
					wsTokens.get(pos), false);
			if (normalScore > wsRejectionScore) {
				wsRejectionScore = normalScore;
			}
		}

		final EvaluationResult result = new EvaluationResult();
		result.isPerturbed = doPerturb;
		if (doPerturb) {
			result.isWhitespacePerturbation = doWsPerturb;
		}

		if (ss.suggestions.isEmpty()) {
			result.identifierRejectionScore = 0;
		} else {
			result.identifierRejectionScore = -ss.suggestions.first()
					.getConfidence();
		}

		result.whitespaceRejectionScore = wsRejectionScore;
		results.add(result);

	}

	public void runSingleExperiment() throws IOException {
		final BaseIdentifierRenamings idRenamer = new BaseIdentifierRenamings(
				tokenizer);
		final FormattingRenamings formattingRenaming = new FormattingRenamings();
		final List<File> selectedFiles = buildRenamersAndGetTargetMethods(
				idRenamer, formattingRenaming);

		final List<String> allToks = Lists.newArrayList(idRenamer.getLM()
				.getTrie().getVocabulary());
		Collections.shuffle(allToks);
		for (final File f : selectedFiles) {
			try {
				for (final Entry<String, MethodDeclaration> method : MethodRetriever
						.getMethodNodes(f).entrySet()) {
					final Collection<String> snippetIdentifiers = scopeExtractor
							.getFromNode(method.getValue()).values();
					final String toRename;
					if (!snippetIdentifiers.isEmpty()) {
						toRename = (String) snippetIdentifiers.toArray()[RandomUtils
								.nextInt(snippetIdentifiers.size())];
					} else {
						continue;
					}
					evaluatePerformanceOn(method.getValue(),
							FileUtils.readFileToString(f), idRenamer,
							formattingRenaming, toRename);
				}
			} catch (Throwable e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}

		}

	}
}
