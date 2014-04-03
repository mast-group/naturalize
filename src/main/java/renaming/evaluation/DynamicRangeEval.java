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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.math.RandomUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import renaming.renamers.BaseIdentifierRenamings;
import renaming.segmentranking.SegmentRenamingSuggestion.Suggestion;
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
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.NGramLM;
import codemining.lm.ngram.smoothing.StupidBackoff;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class DynamicRangeEval {

	public static class BeforeAfterScore<T> {
		final T before;

		final T after;

		BeforeAfterScore(final T b, final T a) {
			before = b;
			after = a;
		}

		@Override
		public String toString() {
			return before + "," + after;
		}

	}

	public static final boolean DEBUG_OUTPUT = false;

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		final DynamicRangeEval dre = new DynamicRangeEval(new File(args[0]),
				new JavaTokenizer(), ScopesTUI.getScopeExtractorByName(args[1]));
		dre.calculateEverythin();
		dre.printROC();

	}

	private final List<BeforeAfterScore<Double>> crossEntropyCmp = Lists
			.newArrayList();

	private final List<BeforeAfterScore<Double>> scoreBest = Lists
			.newArrayList();

	private final List<BeforeAfterScore<Double>> score = Lists.newArrayList();

	private static final int nMethodsToCheck = 500;

	final Collection<File> allFiles;

	final IScopeExtractor scopeExtractor;

	/**
	 * 
	 */
	public DynamicRangeEval(final File directory, final ITokenizer tokenizer,
			final IScopeExtractor ex) {
		allFiles = FileUtils.listFiles(directory, tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);
		scopeExtractor = ex;
	}

	void calculateEverythin() {
		int methodsChecked = 0;
		for (final File f : allFiles) {
			try {
				final Set<File> trainFiles = Sets.newTreeSet();
				trainFiles.addAll(allFiles);
				trainFiles.remove(f);

				final BaseIdentifierRenamings renamer = new BaseIdentifierRenamings(
						new JavaTokenizer());
				renamer.buildRenamingModel(trainFiles);
				final NGramLM lm = new NGramLM(5, new JavaTokenizer());
				lm.trainModel(trainFiles);
				final List<String> allToks = Lists.newArrayList(renamer.getLM()
						.getTrie().getVocabulary());

				final AbstractNGramLM smoothedLM = new StupidBackoff(lm);

				final Map<String, MethodDeclaration> fileMethods = MethodRetriever
						.getMethodNodes(f);

				for (final Entry<String, MethodDeclaration> m : fileMethods
						.entrySet()) {
					pushStatsFor(renamer, m.getValue(), smoothedLM, allToks);
					methodsChecked++;
				}
				if (methodsChecked > nMethodsToCheck) {
					break;
				}
			} catch (final Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private void dumpStats() {
		System.out.println("========Before/After Cross-entropy============");
		printBeforeAfterList(crossEntropyCmp);
		System.out.println("========Before/After Score============");
		printBeforeAfterList(score);
		System.out.println("========Before/After Score Best============");
		printBeforeAfterList(scoreBest);
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

	private void printBeforeAfterList(final List<BeforeAfterScore<Double>> list) {
		for (final BeforeAfterScore<Double> entry : list) {
			System.out.println(entry);
		}
	}

	/**
	 * @param method
	 * @param lm
	 * @param renamedVars
	 * @param ssBefore
	 * @param targetName
	 * @param renamedMethodNode
	 * @param ssAfter
	 */
	public void printDebugOutput(final MethodDeclaration method,
			final AbstractNGramLM lm, final List<String> renamedVars,
			final SnippetSuggestions ssBefore, final String targetName,
			final ASTNode renamedMethodNode, final SnippetSuggestions ssAfter) {
		final double xEntAfter = lm.getExtrinsticEntropy(renamedMethodNode
				.toString());
		final double xEntBefore = lm.getExtrinsticEntropy(method.toString());
		crossEntropyCmp
				.add(new BeforeAfterScore<Double>(xEntBefore, xEntAfter));

		score.add(new BeforeAfterScore<Double>(ssBefore.score, ssAfter.score));

		System.out.println("==============================================");
		System.out.println(method.toString());
		System.out.println("---------------------------------------------");
		System.out.println("Renaming identifier '" + renamedVars.get(0)
				+ "' to '" + targetName + "'");
		System.out.println("score before: "
				+ String.format("%.2f", ssBefore.score) + " score after:"
				+ String.format("%.2f", ssAfter.score));
		System.out.println("logPnotRenaming before: "
				+ String.format("%.2f", ssBefore.getLogProbOfNotRenaming())
				+ " score after:"
				+ String.format("%.2f", ssAfter.getLogProbOfNotRenaming()));
		System.out.println("---------------------------------------------");
		System.out.print("Before " + "[");
		for (final Suggestion sg : ssBefore.suggestions) {
			System.out.print(sg.getIdentifierName() + "("
					+ String.format("%.2f", sg.getConfidence()) + "):"
					+ sg.getRenamings() + ",");
		}
		System.out.println("]");
		System.out.print("After [");
		for (final Suggestion sg : ssAfter.suggestions) {
			System.out.print(sg.getIdentifierName() + "("
					+ String.format("%.2f", sg.getConfidence()) + "):"
					+ sg.getRenamings() + ",");
		}
		System.out.println("]");
		System.out.println("Best Before "
				+ String.format("%.2f", ssBefore.suggestions.first()
						.getConfidence()));
		System.out.println("Best After "
				+ String.format("%.2f", ssAfter.suggestions.first()
						.getConfidence()));
	}

	public void printROC() {
		final double[] thresholds = { 0, .1, .5, 1, 1.5, 2, 2.5, 3, 3.5, 4,
				4.5, 5, 5.5, 6, 7, 8, 9, 10, 20, 50 };
		final double[] fpr = new double[thresholds.length];
		final double[] tpr = new double[thresholds.length];

		for (final BeforeAfterScore<Double> score : scoreBest) {
			for (int i = 0; i < thresholds.length; i++) {
				if (score.before > thresholds[i]) {
					fpr[i]++;
				}
				if (score.after > thresholds[i]) {
					tpr[i]++;
				}
			}
		}
		System.out.println("tpr=" + Arrays.toString(tpr));
		System.out.println("fpr=" + Arrays.toString(fpr));
		System.out.println("n=" + scoreBest.size());
	}

	private void pushStatsFor(final BaseIdentifierRenamings renamer,
			final MethodDeclaration method, final AbstractNGramLM lm,
			final List<String> allToks) throws IOException {
		final JavaASTExtractor ex = new JavaASTExtractor(false);
		final Multimap<Scope, String> scopes = scopeExtractor
				.getFromNode(method);
		final ScopedIdentifierRenaming identifierRenamer = new ScopedIdentifierRenaming(
				scopeExtractor, ParseType.METHOD);
		final List<String> renamedVars = Lists.newArrayList(scopes.values());
		Collections.shuffle(renamedVars);

		checkArgument(!renamedVars.isEmpty());

		final SnippetSuggestions ssBefore = SnippetScorer.scoreSnippet(method,
				renamer, scopeExtractor, false, false);

		final Map<String, String> renamingPlan = Maps.newTreeMap();
		final String targetName = getRandomName(allToks, renamer.getLM()
				.getTokenizer());
		renamingPlan.put(renamedVars.get(0), targetName);

		final String renamedMethod = identifierRenamer.getRenamedCode(
				method.toString(), method.toString(), renamingPlan);
		final ASTNode renamedMethodNode = ex.getASTNode(renamedMethod,
				ParseType.METHOD);

		final SnippetSuggestions ssAfter = SnippetScorer.scoreSnippet(
				renamedMethodNode, renamer, scopeExtractor, false, false);

		scoreBest.add(new BeforeAfterScore<Double>(-ssBefore.suggestions
				.first().getConfidence(), -ssAfter.suggestions.first()
				.getConfidence()));

		if (DEBUG_OUTPUT) {
			printDebugOutput(method, lm, renamedVars, ssBefore, targetName,
					renamedMethodNode, ssAfter);
		}
	}

}
