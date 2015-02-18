/**
 *
 */
package renaming.history;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import committools.data.GitCommitUtils;

/**
 * Map SVN revisions to Git SHAs.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class SvnToGitMapper {

	/**
	 * @param args
	 * @throws GitAPIException
	 * @throws IOException
	 * @throws NoHeadException
	 * @throws NoWorkTreeException
	 */
	public static void main(final String[] args) throws NoWorkTreeException,
			NoHeadException, IOException, GitAPIException {
		if (args.length != 1) {
			System.err.println("Usage <repository>");
			System.exit(-1);
		}
		final SvnToGitMapper mapper = new SvnToGitMapper(args[0]);
		System.out.println(mapper.mapSvnToGit());
	}

	private final String repositoryDirectory;

	private static final Pattern svnIdMatcher = Pattern
			.compile("git-svn-id:\\s\\S+@(\\d+)");

	/**
	 *
	 */
	public SvnToGitMapper(final String repositoryDirectory) {
		this.repositoryDirectory = repositoryDirectory;
	}

	public BiMap<Integer, String> mapSvnToGit() throws IOException,
			NoWorkTreeException, NoHeadException, GitAPIException {
		final BiMap<Integer, String> mappings = HashBiMap.create();
		final Git repository = GitCommitUtils
				.getGitRepository(repositoryDirectory);

		for (final RevCommit commit : GitCommitUtils
				.getAllCommitsTopological(repository)) {
			final String message = commit.getFullMessage();
			if (!message.contains("git-svn-id")) {
				continue;
			}
			final Matcher matcher = svnIdMatcher.matcher(message);
			matcher.find();
			mappings.put(Integer.parseInt(matcher.group(1)), commit.name());
		}

		return mappings;
	}
}
