/**
 * 
 */
package renaming.tools;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;
import renaming.segmentranking.SnippetScorer;
import renaming.segmentranking.SnippetScorer.SnippetSuggestions;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.codeutils.scopes.AllScopeExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.util.SettingsLoader;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.Lists;

/**
 * A pre-commit hook for reject
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PreCommitVerifier {

	public static double THRESHOLD_VALUE = SettingsLoader.getNumericSetting(
			"threshold", 7);

	/**
	 * @param args
	 * @throws IOException
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws IOException,
			SerializationException {
		final CommandLineParser parser = new PosixParser();

		final Options options = new Options();

		options.addOption(OptionBuilder.isRequired(false)
				.withDescription("Check all identifiers. Default").create("a"));

		options.addOption(OptionBuilder.isRequired(false)
				.withLongOpt("variables").withDescription("Check variables.")
				.create("v"));
		options.addOption(OptionBuilder.isRequired(false)
				.withLongOpt("methods").withDescription("Check method calls.")
				.create("m"));
		options.addOption(OptionBuilder.isRequired(false).withLongOpt("types")
				.withDescription("Check types.").create("t"));

		final OptionGroup lmGroup = new OptionGroup();
		lmGroup.setRequired(true);
		lmGroup.addOption(OptionBuilder
				.hasArg()
				.withLongOpt("languagemodel")
				.withDescription(
						"Use this pretrained language model given by the file")
				.withArgName("FILE").create("l"));
		lmGroup.addOption(OptionBuilder
				.withArgName("directory")
				.hasArg()
				.isRequired(false)
				.withArgName("DIRECTORY")
				.withDescription(
						"Use this codebase to use to train language model. This option is mutually exclusive with -l")
				.withLongOpt("codebasedir").create("c"));
		options.addOptionGroup(lmGroup);

		final CommandLine parse;
		try {
			parse = parser.parse(options, args);
		} catch (final ParseException ex) {
			System.err.println(ex.getMessage());
			final HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("naturalizecheck FILE1, FILE2, ...", options);
			return;
		}

		final List<File> testFiles = Lists.newArrayList();
		for (final String filename : parse.getArgs()) {
			final File currentFile = new File(filename);
			if (!currentFile.exists()) { // Probably someone is deleting a file
				continue;
			}
			if (!filename.endsWith(".java")) {
				continue;
			} else if (currentFile.isDirectory()) {
				testFiles.addAll(FileUtils.listFiles(currentFile,
						(new JavaTokenizer()).getFileFilter(),
						DirectoryFileFilter.DIRECTORY));
			} else {
				testFiles.add(currentFile);
			}
		}

		if (testFiles.isEmpty()) {
			// Nothing to do here...
			System.exit(0);
		}

		final AbstractIdentifierRenamings renamer;
		if (parse.hasOption("l")) {
			final AbstractNGramLM ngramLM = (AbstractNGramLM) Serializer
					.getSerializer().deserializeFrom(parse.getOptionValue("l"));
			renamer = new BaseIdentifierRenamings(ngramLM);
		} else if (parse.hasOption("c")) {
			final ITokenizer tokenizer = new JavaTokenizer();
			renamer = new BaseIdentifierRenamings(tokenizer);
			final Collection<File> trainFiles = FileUtils.listFiles(new File(
					parse.getOptionValue("codebasedir")), tokenizer
					.getFileFilter(), DirectoryFileFilter.DIRECTORY);

			trainFiles.removeAll(testFiles);

			renamer.buildRenamingModel(trainFiles);
		} else {
			final HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("codeprofile", options);
			return;
		}

		final IScopeExtractor scopeExtractor;
		if (!parse.hasOption("v") && !parse.hasOption("m")
				&& !parse.hasOption("t")) {
			scopeExtractor = new AllScopeExtractor.AllScopeSnippetExtractor();
		} else {
			scopeExtractor = new AllScopeExtractor.AllScopeSnippetExtractor(
					parse.hasOption("v"), parse.hasOption("m"),
					parse.hasOption("t"));
		}

		final JavaASTExtractor ex = new JavaASTExtractor(false);
		final SnippetScorer scorer = new SnippetScorer(renamer, scopeExtractor);
		boolean noSuggestions = true;
		for (final File f : testFiles) {
			final String snippetCode = FileUtils.readFileToString(f);
			SnippetSuggestions suggestions;
			try {
				suggestions = scorer.scoreSnippet(
						ex.getBestEffortAstNode(snippetCode), false);
				if (!suggestions.suggestions.isEmpty()) {
					noSuggestions = false;
					System.out
							.println("=========================================================");
					System.out.println("Suggestions for" + f.getAbsolutePath());
					System.out
							.println("=========================================================");
					CodeReviewAssistant.printRenaming(suggestions, snippetCode,
							-1);
				}
			} catch (final Exception e) {
				e.printStackTrace();
			}

		}

		if (noSuggestions) {
			System.exit(0);
		} else {
			System.exit(-1);
		}

	}

	/**
	 * 
	 */
	private PreCommitVerifier() {
	}
}
