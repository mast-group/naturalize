/**
 * 
 */
package renaming.formatting;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.java.tokenizers.JavaWhitespaceTokenizer;
import codemining.util.parallel.ParallelThreadPool;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class FormattingEvaluation {

	private class Printer implements Runnable {

		@Override
		public void run() {
			System.out.println(results);
		}

	}

	/**
	 * Evaluate the whitespace accuracy.
	 * 
	 */
	private class WhitespaceEvaluator implements Runnable {
		final File testedFile;

		WhitespaceEvaluator(final File test) {
			testedFile = test;
		}

		@Override
		public void run() {
			try {
				final Collection<File> trainFiles = new TreeSet<File>();
				trainFiles.addAll(allFiles);
				checkArgument(trainFiles.remove(testedFile));

				FormattingRenamings fr = new FormattingRenamings();
				fr.buildModel(trainFiles);
				FormattingRenamingsEval eval = new FormattingRenamingsEval(fr);
				results.accumulate(eval.evaluateFormattingAccuracy(testedFile));
			} catch (IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}

	}

	private static final Logger LOGGER = Logger
			.getLogger(FormattingEvaluation.class.getName());

	public static void main(final String[] args) {
		if (args.length < 1) {
			System.err.println("Usage <folderToEvaluate>");
			return;
		}

		final JavaWhitespaceTokenizer tokenizer = new JavaWhitespaceTokenizer();

		final Collection<File> allFiles = FileUtils.listFiles(
				new File(args[0]), tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);

		final FormattingEvaluation fe = new FormattingEvaluation(allFiles);
		fe.performEvaluation();
	}

	FormattingRenamingsEval.WhitespacePrecisionRecall results = new FormattingRenamingsEval.WhitespacePrecisionRecall();

	final Collection<File> allFiles;

	/**
	 * 
	 */
	public FormattingEvaluation(final Collection<File> files) {
		allFiles = files;
	}

	public void performEvaluation() {
		final ParallelThreadPool threadPool = new ParallelThreadPool();
		int fileNo = 0;
		for (final File fi : allFiles) {
			threadPool.pushTask(new WhitespaceEvaluator(fi));
			fileNo++;
			if (fileNo % 10 == 0) {
				threadPool.pushTask(new Printer());
			}
		}
		threadPool.waitForTermination();
		System.out.println(results);
	}
}
