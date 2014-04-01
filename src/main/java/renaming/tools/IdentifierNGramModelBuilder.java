package renaming.tools;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import renaming.ngram.IdentifierNeighborsNGramLM;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.ITokenizer;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.smoothing.StupidBackoff;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * Tool for building an LM.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class IdentifierNGramModelBuilder {

	private static final Logger LOGGER = Logger
			.getLogger(IdentifierNGramModelBuilder.class.getName());

	/**
	 * @param args
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws ClassNotFoundException
	 * @throws IOException
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws InstantiationException,
			IllegalAccessException, ClassNotFoundException, IOException,
			IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, SerializationException {
		final CommandLineParser parser = new PosixParser();

		final Options options = new Options();

		options.addOption(OptionBuilder.isRequired(true)
				.withDescription("n-gram n parameter. The size of n.").hasArg()
				.create("n"));
		options.addOption(OptionBuilder
				.isRequired(true)
				.withLongOpt("trainDir")
				.hasArg()
				.withDescription("The directory containing the training files.")
				.create("t"));
		options.addOption(OptionBuilder.isRequired(true).withLongOpt("output")
				.hasArg()
				.withDescription("File to output the serialized n-gram model.")
				.create("o"));

		final CommandLine parse;
		try {
			parse = parser.parse(options, args);
		} catch (ParseException ex) {
			System.err.println(ex.getMessage());
			final HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("buildlm", options);
			return;
		}

		final ITokenizer tokenizer = new JavaTokenizer();
		final String nStr = parse.getOptionValue("n");
		final int n = Integer.parseInt(nStr);
		final File trainDirectory = new File(parse.getOptionValue("t"));
		checkArgument(trainDirectory.isDirectory());
		final String targetSerFile = parse.getOptionValue("o");

		final IdentifierNeighborsNGramLM dict = new IdentifierNeighborsNGramLM(
				n, tokenizer);

		LOGGER.info("NGramLM Model builder started with " + n
				+ "-gram for files in " + trainDirectory.getAbsolutePath());

		final Collection<File> files = FileUtils.listFiles(trainDirectory,
				dict.modelledFilesFilter(), DirectoryFileFilter.DIRECTORY);
		dict.trainModel(files);

		LOGGER.info("Ngram model build. Adding Smoother...");

		final AbstractNGramLM ng = new StupidBackoff(dict);

		LOGGER.info("Ngram model build. Serializing...");
		Serializer.getSerializer().serialize(ng, targetSerFile);
	}
}
