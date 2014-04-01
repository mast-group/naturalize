/**
 * 
 */
package renaming.tui;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope;
import codemining.util.serialization.ISerializationStrategy.SerializationException;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class IdentifierRenamer {

	private static final Logger LOGGER = Logger
			.getLogger(IdentifierRenamer.class.getName());

	/**
	 * @param args
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws SerializationException
	 */
	public static void main(String[] args) throws IOException,
			ClassNotFoundException, SerializationException {
		if (args.length < 3) {
			System.err
					.println("Usage <trainDirectory> <snippetPath> <identifierToSuggest> ...");
			return;
		}

		final ITokenizer tokenizer = new JavaTokenizer();

		final Collection<File> trainingFiles = FileUtils.listFiles(new File(
				args[0]), tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);

		final String snippet = FileUtils.readFileToString(new File(args[1]));

		final AbstractIdentifierRenamings renamer = new BaseIdentifierRenamings(
				new JavaTokenizer());
		renamer.buildRenamingModel(trainingFiles);

		for (int i = 2; i < args.length; i++) {
			try {
				final SortedSet<AbstractIdentifierRenamings.Renaming> renamings = renamer
						.getRenamings(new Scope(snippet,
								Scope.ScopeType.SCOPE_LOCAL, null, -1, -1),
								args[i]);

				System.out.println("Renames for  " + args[i] + " ("
						+ renamings.size() + " alternatives)");

				double sum = 0;
				for (final AbstractIdentifierRenamings.Renaming listing : renamings) {
					sum += Math.exp(-listing.score);
				}

				int j = 0;
				for (final AbstractIdentifierRenamings.Renaming listing : renamings) {
					System.out.println(listing.name + " " + listing.score + " "
							+ (Math.exp(-listing.score) / sum * 100.) + "%");
					j++;
					if (j > 25) {
						break;
					}
				}
			} catch (Throwable e) {
				LOGGER.warning("Failed to get renaming for " + args[i] + " :"
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}

	}
}
