/**
 * 
 */
package renaming.evaluation;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import renaming.evaluation.NamingEvaluator.ResultObject;
import renaming.renamers.AbstractIdentifierRenamings;
import codemining.java.codeutils.scopes.ScopesTUI;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope.ScopeType;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.util.parallel.ParallelThreadPool;
import codemining.util.serialization.ISerializationStrategy.SerializationException;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Evaluate renaming by leaving one file out.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class LeaveOneOutEvaluator {

	/**
	 * Test a specific file.
	 * 
	 */
	public class ModelEvaluator implements Runnable {

		final IScopeExtractor scopeExtractor;

		final File testedFile;

		final AbstractIdentifierRenamings renamer;

		public ModelEvaluator(final File fileToRetain,
				final IScopeExtractor extractor, final String renamerClass,
				final String renamerConstructorParams) {

			testedFile = fileToRetain;
			scopeExtractor = extractor;

			try {

				if (renamerConstructorParams == null) {
					renamer = (AbstractIdentifierRenamings) Class
							.forName(renamerClass)
							.getDeclaredConstructor(ITokenizer.class)
							.newInstance(tokenizer);
				} else {
					renamer = (AbstractIdentifierRenamings) Class
							.forName(renamerClass)
							.getDeclaredConstructor(ITokenizer.class,
									String.class)
							.newInstance(tokenizer, renamerConstructorParams);
				}
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
			}
		}

		@Override
		public void run() {
			try {
				final Collection<File> testFiles = Lists.newArrayList();
				testFiles.add(testedFile);

				final Collection<File> trainFiles = Sets.newTreeSet(allFiles);
				checkArgument(trainFiles.removeAll(testFiles));

				final NamingEvaluator ve = new NamingEvaluator(trainFiles,
						data, renamer);
				ve.performEvaluation(testFiles, scopeExtractor);
			} catch (Exception e) {
				LOGGER.warning("Error in file " + testedFile.getAbsolutePath()
						+ " " + ExceptionUtils.getFullStackTrace(e));
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
			for (int i = 0; i < data.length; i++) {
				System.out.println("==============" + ScopeType.values()[i]
						+ "===========");
				data[i].printStats();
			}
		}

	}

	private static final Logger LOGGER = Logger
			.getLogger(LeaveOneOutEvaluator.class.getName());

	/**
	 * @param args
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws ClassNotFoundException
	 */
	public static void main(String[] args) throws InstantiationException,
			IllegalAccessException, ClassNotFoundException,
			SerializationException {
		if (args.length < 5) {
			System.err
					.println("Usage <folder> <tokenizerClass> <wrapperClass> variable|method <renamingClass> [<renamerConstrParams> ..]");
			return;
		}

		final File directory = new File(args[0]);

		final Class<? extends ITokenizer> tokenizerName = (Class<? extends ITokenizer>) Class
				.forName(args[1]);
		final ITokenizer tokenizer = tokenizerName.newInstance();

		final Class<? extends AbstractNGramLM> smoothedNgramClass = (Class<? extends AbstractNGramLM>) Class
				.forName(args[2]);

		final LeaveOneOutEvaluator eval = new LeaveOneOutEvaluator(directory,
				tokenizer, smoothedNgramClass);

		final IScopeExtractor scopeExtractor = ScopesTUI
				.getScopeExtractorByName(args[3]);

		eval.performEvaluation(scopeExtractor, args[4],
				args.length == 6 ? args[5] : null);
	}

	final Collection<File> allFiles;

	final ResultObject[] data = new ResultObject[ScopeType.values().length];

	final ITokenizer tokenizer;

	public LeaveOneOutEvaluator(final File directory,
			final ITokenizer tokenizer,
			final Class<? extends AbstractNGramLM> smoother) {
		allFiles = FileUtils.listFiles(directory, tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);
		this.tokenizer = tokenizer;
		for (int i = 0; i < data.length; i++) {
			data[i] = new ResultObject();
		}
	}

	public void performEvaluation(final IScopeExtractor scopeExtractor,
			final String renamerClass, final String additionalParams) {
		final ParallelThreadPool threadPool = new ParallelThreadPool();
		int fileNo = 0;
		for (final File fi : allFiles) {
			threadPool.pushTask(new ModelEvaluator(fi, scopeExtractor,
					renamerClass, additionalParams));
			fileNo++;
			if (fileNo % 40 == 0) {
				threadPool.pushTask(new Printer());
			}
		}
		threadPool.waitForTermination();
	}

}
