/**
 *
 */
package renaming.history;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.NoWorkTreeException;

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
		public String fromVersion;
		public String toVersion;
		String filename;
		String nameBefore;
		String nameAfter;
		public Range<Integer> linesBefore;
		public Range<Integer> linesAfter;

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			final StringBuilder builder = new StringBuilder();
			builder.append("Renaming [fromVersion=");
			builder.append(fromVersion);
			builder.append(", toVersion=");
			builder.append(toVersion);
			builder.append(", filename=");
			builder.append(filename);
			builder.append(", nameBefore=");
			builder.append(nameBefore);
			builder.append(", nameAfter=");
			builder.append(nameAfter);
			builder.append(", linesBefore=");
			builder.append(linesBefore);
			builder.append(", linesAfter=");
			builder.append(linesAfter);
			builder.append("]");
			return builder.toString();
		}

	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws GitAPIException
	 * @throws NoHeadException
	 * @throws NoWorkTreeException
	 */
	public static void main(final String[] args) throws IOException,
			NoWorkTreeException, NoHeadException, GitAPIException {
		if (args.length != 3) {
			System.err.println("Usage <datafile> <prefix> <repositoryDir>");
			System.exit(-1);
		}
		final SvnToGitMapper mapper = new SvnToGitMapper(args[2]);
		final RepentDataParser rdp = new RepentDataParser(new File(args[0]),
				mapper.mapSvnToGit(), args[1], new Predicate<Integer>() {

					@Override
					public boolean test(final Integer t) {
						return t < 250;
					}
				});
		final List<Renaming> renamings = rdp.parse();
		for (final Renaming renaming : renamings) {
			System.out.println(renaming);
		}
		System.out.println("Num Renamings: " + renamings.size());
	}

	private final File datafile;
	private final BiMap<Integer, String> gitShaMap;
	private final String filePrefix;

	Pattern lineMatcher = Pattern
			.compile("^(\\S+\\.java)_DiffLine_(\\d+)-(\\d+):(((\\d+)-?(\\d+)?),((\\d+)-?(\\d+)?))+:([a-zA-Z0-9_]+)->([a-zA-Z0-9_]+)");

	private static final Logger LOGGER = Logger
			.getLogger(RepentDataParser.class.getName());

	private final Predicate<Integer> revisionFilter;

	/**
	 *
	 */
	public RepentDataParser(final File datafile,
			final BiMap<Integer, String> gitShaMap, final String filePrefix,
			final Predicate<Integer> revisionFilter) {
		this.datafile = datafile;
		this.gitShaMap = gitShaMap;
		this.filePrefix = filePrefix;
		this.revisionFilter = revisionFilter;
	}

	public List<Renaming> parse() throws IOException {
		final List<Renaming> allRenamings = Lists.newArrayList();
		for (final String line : FileUtils.readLines(datafile)) {
			final Matcher matcher = lineMatcher.matcher(line);
			if (matcher.find()) {
				final Renaming renamingPoint = new Renaming();
				renamingPoint.filename = matcher.group(1);
				checkArgument(renamingPoint.filename.startsWith(filePrefix));
				renamingPoint.filename = renamingPoint.filename
						.substring(filePrefix.length());
				renamingPoint.fromVersion = gitShaMap.get(Integer
						.parseInt(matcher.group(2)));
				final int toRevision = Integer.parseInt(matcher.group(3));
				if (!revisionFilter.test(toRevision)) {
					continue;
				}
				renamingPoint.toVersion = gitShaMap.get(toRevision);

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
