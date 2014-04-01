/**
 * 
 */
package renaming.evaluation;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.eclipse.jdt.core.dom.ASTNode;

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.INGramIdentifierRenamer.Renaming;
import codemining.java.codeutils.scopes.VariableScopeExtractor;
import codemining.java.codeutils.scopes.VariableScopeExtractor.Variable;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope;
import codemining.util.SettingsLoader;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Evaluate the "problem" with junk variable names.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class JunkIssueEvaluator {

	private static class JunkPercentage {
		public long totalVariables = 0;
		public long nJunkInTotal = 0;

		public synchronized void putResults(final long nVars, final long nJunk) {
			totalVariables += nVars;
			nJunkInTotal += nJunk;
		}
	}

	class JunkRenamingRunnable implements Runnable {

		final Collection<File> allFiles;

		final File testFile;
		final JunkPercentage resultObject;

		public JunkRenamingRunnable(final Collection<File> allFiles,
				final File testFile, final JunkPercentage resultObject) {
			this.allFiles = allFiles;
			this.testFile = testFile;
			this.resultObject = resultObject;
		}

		@Override
		public void run() {

			try {
				final Multimap<Scope, String> scopes = (new VariableScopeExtractor.VariableScopeSnippetExtractor())
						.getFromFile(testFile);

				final List<Entry<Scope, String>> selectedScopes = sampleScopes(scopes);

				if (selectedScopes.isEmpty()) {
					return;
				}

				final Set<File> trainFiles = Sets.newTreeSet(allFiles);
				checkArgument(trainFiles.remove(testFile));
				final AbstractIdentifierRenamings renamer = createRenamer(
						renamerClass, renamerConstructorParams);
				renamer.buildRenamingModel(trainFiles);

				final long nVars = selectedScopes.size();
				long nJunk = 0;
				for (final Entry<Scope, String> variable : selectedScopes) {
					final SortedSet<Renaming> renamings = renamer.getRenamings(
							variable.getKey(), variable.getValue());
					if (renamings.first().name.matches("^junk[0-9]+$")) {
						nJunk++;
					}
				}

				resultObject.putResults(nVars, nJunk);
			} catch (final IOException ioe) {
				LOGGER.warning("Failed to get scopes from "
						+ testFile.getAbsolutePath() + " "
						+ ExceptionUtils.getFullStackTrace(ioe));
			}
		}

		/**
		 * @param scopes
		 * @return
		 */
		private List<Entry<Scope, String>> sampleScopes(
				final Multimap<Scope, String> scopes) {
			final List<Entry<Scope, String>> selectedScopes = Lists
					.newArrayList();

			// Sample
			for (final Entry<Scope, String> variable : scopes.entries()) {
				if (RandomUtils.nextDouble() > SAMPLING_RATIO) {
					continue;
				}
				selectedScopes.add(variable);
			}
			return selectedScopes;
		}

	}

	public static final double SAMPLING_RATIO = SettingsLoader
			.getNumericSetting("samplingPercent", 1.);

	private static final Logger LOGGER = Logger
			.getLogger(JunkIssueEvaluator.class.getName());

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		if (args.length < 3) {
			System.err
					.println("Usage <projectDir> <tempDir> <renamerClass> [renamerConstructorParams]");
			return;
		}

		final JunkIssueEvaluator jie = new JunkIssueEvaluator(
				new File(args[0]), new File(args[1]), args[2],
				args.length == 4 ? args[3] : null);
		final double[] results = jie.runExperiment();
		System.out.println(Arrays.toString(results));

	}

	JavaTokenizer tokenizer = new JavaTokenizer();
	final double[] pJunkification = { .005, .01, .02, .03, .05, .07, .1, .15 };
	final File tmpDir;
	final File prjDir;

	final String renamerClass;
	final String renamerConstructorParams;

	final List<File> junkVariables = Lists.newArrayList();

	long nTotalVars;

	/**
	 * 
	 */
	public JunkIssueEvaluator(final File projectDir, final File tmpDir,
			final String renamerClass, final String renamerConstructorParams) {
		this.tmpDir = tmpDir;
		this.prjDir = projectDir;
		this.renamerClass = renamerClass;
		this.renamerConstructorParams = renamerConstructorParams;
	}

	private void addJunkVarsUpToPct(final double percent,
			final JunkVariableRenamer jvr) {
		// Find total num of variables that need to be renamed
		final long targetSize = (long) ((1. - percent) * nTotalVars);
		final long nVarsToRename = junkVariables.size() - targetSize;

		// Rename
		for (int i = 0; i < nVarsToRename; i++) {
			try {
				final File currentFile = junkVariables.remove(junkVariables
						.size() - 1);
				FileUtils.writeStringToFile(currentFile, jvr
						.renameSingleVariablesToJunk(FileUtils
								.readFileToString(currentFile)));
			} catch (final Exception e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}

	}

	/**
	 * @param renamerClass
	 * @param renamerConstructorParams
	 * @throws IllegalArgumentException
	 */
	private AbstractIdentifierRenamings createRenamer(
			final String renamerClass, final String renamerConstructorParams)
			throws IllegalArgumentException {
		final AbstractIdentifierRenamings renamer;
		try {
			if (renamerConstructorParams == null) {
				renamer = (AbstractIdentifierRenamings) Class
						.forName(renamerClass)
						.getDeclaredConstructor(ITokenizer.class)
						.newInstance(tokenizer);
			} else {
				renamer = (AbstractIdentifierRenamings) Class
						.forName(renamerClass)
						.getDeclaredConstructor(ITokenizer.class, String.class)
						.newInstance(tokenizer, renamerConstructorParams);
			}
		} catch (final IllegalArgumentException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (final SecurityException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (final InstantiationException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (final IllegalAccessException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (final InvocationTargetException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (final NoSuchMethodException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (final ClassNotFoundException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		}
		return renamer;
	}

	private double evaluateNumJunkVars() {
		final Collection<File> allFiles = FileUtils.listFiles(tmpDir,
				tokenizer.getFileFilter(), DirectoryFileFilter.DIRECTORY);

		final ParallelThreadPool ptp = new ParallelThreadPool();
		final JunkPercentage jp = new JunkPercentage();

		for (final File testFile : allFiles) {
			ptp.pushTask(new JunkRenamingRunnable(allFiles, testFile, jp));
		}

		ptp.waitForTermination();
		LOGGER.info("accJunk = " + ((double) jp.nJunkInTotal)
				/ jp.totalVariables);
		return ((double) jp.nJunkInTotal) / jp.totalVariables;
	}

	private void mirrorToTemporaryDir() {
		final Collection<File> allFiles = FileUtils.listFiles(prjDir,
				tokenizer.getFileFilter(), DirectoryFileFilter.DIRECTORY);
		try {
			FileUtils.cleanDirectory(tmpDir);
		} catch (final IOException e) {
			LOGGER.warning("Failed to clean temporary directory");
		}
		for (final File file : allFiles) {
			try {
				final Multimap<ASTNode, Variable> vars = VariableScopeExtractor
						.getVariableScopes(file);
				final long nVars = vars.entries().size();
				nTotalVars += nVars;
				final File outFile = new File(tmpDir.getAbsolutePath() + "/"
						+ file.getName());
				for (int i = 0; i < nVars; i++) {
					junkVariables.add(outFile);
				}
				FileUtils.copyFile(file, outFile);
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}

		Collections.shuffle(junkVariables); // Generate random plan
	}

	public double[] runExperiment() {
		final double[] pJunk = new double[pJunkification.length];
		mirrorToTemporaryDir();
		final JunkVariableRenamer jvr = new JunkVariableRenamer();
		for (int i = 0; i < pJunkification.length; i++) {
			addJunkVarsUpToPct(pJunkification[i], jvr);
			pJunk[i] = evaluateNumJunkVars();
			System.out.println(pJunkification[i] + ":" + pJunk[i]);
		}
		return pJunk;
	}
}
