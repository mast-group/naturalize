/**
 * 
 */
package renaming.tools;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
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
import renaming.renamers.INGramIdentifierRenamer.Renaming;
import renaming.segmentranking.SegmentRenamingSuggestion.Suggestion;
import renaming.segmentranking.SnippetScorer;
import renaming.segmentranking.SnippetScorer.SnippetSuggestions;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.codeutils.scopes.AllScopeExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.Lists;

/**
 * A code review assistant. Given a file and either a repository or a
 * pre-trained LM returns a sorted list of suggested renamings for that file.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class CodeReviewAssistant {

	/**
	 * @param args
	 * @throws SerializationException
	 * @throws IOException
	 */
	public static void main(final String[] args) throws SerializationException,
			IOException {

		final CommandLineParser parser = new PosixParser();

		final Options options = new Options();

		options.addOption(OptionBuilder.isRequired(false)
				.withDescription("Rename all identifiers. Default").create("a"));

		options.addOption(OptionBuilder.isRequired(false)
				.withLongOpt("variables").withDescription("Rename variables.")
				.create("v"));
		options.addOption(OptionBuilder.isRequired(false)
				.withLongOpt("methods").withDescription("Rename method calls.")
				.create("m"));
		options.addOption(OptionBuilder.isRequired(false).withLongOpt("types")
				.withDescription("Rename types.").create("t"));

		final OptionGroup lmGroup = new OptionGroup();
		lmGroup.setRequired(true);
		lmGroup.addOption(OptionBuilder.hasArg().withLongOpt("languagemodel")
				.withDescription("Use this pretrained language model")
				.withArgName("FILE").create("l"));
		lmGroup.addOption(OptionBuilder
				.withArgName("directory")
				.hasArg()
				.isRequired(false)
				.withArgName("DIRECTORY")
				.withDescription(
						"Codebase to use to train renamer. This option is mutually exclusive with -l")
				.withLongOpt("codebasedir").create("c"));
		options.addOptionGroup(lmGroup);

		final CommandLine parse;
		try {
			parse = parser.parse(options, args);
		} catch (final ParseException ex) {
			System.err.println(ex.getMessage());
			final HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("styleprofile FILE1, FILE2, ...", options);
			return;
		}

		final List<File> testFiles = Lists.newArrayList();
		if (parse.getArgs().length == 0) {
			System.err.println("No files specified");
			final HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("styleprofile FILE1, FILE2, ...", options);
			return;
		}
		for (final String filename : parse.getArgs()) {
			testFiles.add(new File(filename));
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
			final SnippetSuggestions suggestions = scorer.scoreSnippet(
					ex.getAST(f), true);
			if (!suggestions.suggestions.isEmpty()) {
				noSuggestions = false;
				System.out
						.println("=========================================================");
				System.out.println("Suggestions for" + f.getAbsolutePath());
				System.out
						.println("=========================================================");
				printRenaming(suggestions, snippetCode, -1);
			}
		}

		if (noSuggestions) {
			System.out.println("No suggestions");
		}

	}

	/**
	 * Print a single set of renamings
	 * 
	 * @param renaming
	 * @param id
	 */
	public static void printRenaming(final SnippetSuggestions suggestions,
			final String code, final int id) {
		final JavaTokenizer tokenizer = new JavaTokenizer();
		final DecimalFormat df = new DecimalFormat("#.00");
		if (id != -1) {
			System.out
					.println("==========SNIPPET " + id + "==================");
		}
		System.out.println(code);
		System.out.println("-------------------------------------------");
		int i = 0;
		for (final Suggestion suggestion : suggestions.suggestions) {
			if (suggestion.getConfidence() > -.5) {
				continue;
			}
			i++;

			double total = 0;
			double scoreOfCurrent = Double.POSITIVE_INFINITY;
			for (final Renaming alternative : suggestion.getRenamings()) {
				total += Math.pow(2, -alternative.score);
				if ((alternative.name.equals(suggestion.getIdentifierName()) || alternative.name
						.equals("UNK_SYMBOL"))
						&& Double.isInfinite(scoreOfCurrent)) {
					scoreOfCurrent = alternative.score;
				}
			}

			System.out.print(i + ".'" + suggestion.getIdentifierName() + "' ("
					+ df.format(Math.pow(2, -scoreOfCurrent) / total * 100.)
					+ "%) -> {");

			int j = 1;
			for (final Renaming alternative : suggestion.getRenamings()) {
				if (!tokenizer
						.getIdentifierType()
						.equals(tokenizer.getTokenFromString(alternative.name).tokenType)) {
					continue; // avoid non-identifier tokens.
				}
				if (alternative.score >= scoreOfCurrent) {
					break;
				}
				System.out.print(alternative.name
						+ "("
						+ df.format(Math.pow(2, -alternative.score) * 100
								/ total) + "%), ");
				j++;
				if (j > 10) {
					break;
				}
			}
			System.out.println("}");
		}
	}

	private CodeReviewAssistant() {
	}
}
