/**
 *
 */
package renaming.history;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.BiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

/**
 * Parse the REPENT data
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class RepentDataParser {

	public static class Renaming {
		String fromVersion;
		String toVersion;
		String filename;
		String nameBefore;
		String nameAfter;
		public Range<Integer> linesBefore;
		public Range<Integer> linesAfter;
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("Usage <datafile> <prefix>");
			System.exit(-1);
		}
		final RepentDataParser rdp = new RepentDataParser(new File(args[0]),
				null, args[1]);
		rdp.parse();
	}

	private final File datafile;
	private final BiMap<Integer, String> gitShaMap;
	private final String filePrefix;

	Pattern lineMatcher = Pattern
			.compile("^(\\S+\\.java)_DiffLine_(\\d+)-(\\d+):(((\\d+)-?(\\d+)?),((\\d+)-?(\\d+)?))+:([a-zA-Z0-9_]+)->([a-zA-Z0-9_]+)");

	private static final Logger LOGGER = Logger
			.getLogger(RepentDataParser.class.getName());

	/**
	 *
	 */
	public RepentDataParser(final File datafile,
			final BiMap<Integer, String> gitShaMap, final String filePrefix) {
		this.datafile = datafile;
		this.gitShaMap = gitShaMap;
		this.filePrefix = filePrefix;
	}

	public List<Renaming> parse() throws IOException {
		final List<Renaming> allRenamings = Lists.newArrayList();
		for (final String line : FileUtils.readLines(datafile)) {
			final Matcher matcher = lineMatcher.matcher(line);
			if (matcher.find()) {
				final Renaming renamingPoint = new Renaming();
				renamingPoint.filename = matcher.group(1); // TODO: Remove
															// prefix
				checkArgument(renamingPoint.filename.startsWith(filePrefix));
				renamingPoint.filename = renamingPoint.filename
						.substring(filePrefix.length());
				renamingPoint.fromVersion = gitShaMap.get(Integer
						.parseInt(matcher.group(2)));
				renamingPoint.toVersion = gitShaMap.get(Integer
						.parseInt(matcher.group(3)));

				if (matcher.group(7) != null) {
					renamingPoint.linesBefore = Range.closed(
							Integer.parseInt(matcher.group(6)),
							Integer.parseInt(matcher.group(7)));
				} else {
					renamingPoint.linesBefore = Range.closed(
							Integer.parseInt(matcher.group(6)),
							Integer.parseInt(matcher.group(6)));
				}

				if (matcher.group(10) != null) {
					renamingPoint.linesAfter = Range.closed(
							Integer.parseInt(matcher.group(9)),
							Integer.parseInt(matcher.group(10)));
				} else {
					renamingPoint.linesAfter = Range.closed(
							Integer.parseInt(matcher.group(9)),
							Integer.parseInt(matcher.group(9)));
				}
				renamingPoint.nameBefore = matcher.group(11);
				renamingPoint.nameAfter = matcher.group(12);
				allRenamings.add(renamingPoint);
			} else {
				LOGGER.warning("Failed to parse " + line);
			}
		}
		return allRenamings;
	}

}
