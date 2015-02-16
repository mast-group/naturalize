/**
 *
 */
package renaming.history;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.revwalk.RevCommit;

import codemining.java.tokenizers.JavaTokenizer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import committools.data.AbstractCommitWalker;
import committools.data.EditListRetriever;
import committools.data.EditListRetriever.IEditListCallback;
import committools.data.RepositoryFileWalker;

/**
 * Print all the identifiers and how their line number changes through time.
 * This does not capture renamings.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class ChangingIdentifiersRepositoryWalker extends RepositoryFileWalker
		implements IEditListCallback {

	public class IdentifierInformationThroughTime {
		private final List<IdentifierInformation> identifierThroughTime = Lists
				.newArrayList();

		private boolean identifierDeleted = false;

		public IdentifierInformationThroughTime() {
			allIdentifierChains.add(this);
		}

		public final void addInformation(final IdentifierInformation idInfo) {
			checkArgument(!identifierDeleted);
			identifierThroughTime.add(idInfo);
		}

		public final IdentifierInformation getLast() {
			checkArgument(!identifierThroughTime.isEmpty());
			return identifierThroughTime.get(identifierThroughTime.size() - 1);
		}

		public void setIdentifierDeleted() {
			checkArgument(!identifierDeleted);
			identifierDeleted = true;
		}
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("Usage <repository>");
			System.exit(-1);
		}

		final ChangingIdentifiersRepositoryWalker cirw = new ChangingIdentifiersRepositoryWalker(
				args[0], AbstractCommitWalker.BASE_WALK);
		cirw.doWalk(20);
	}

	private final List<IdentifierInformationThroughTime> allIdentifierChains = Lists
			.newArrayList();

	private final Multimap<String, IdentifierInformationThroughTime> currentStateOfIdentifiers = ArrayListMultimap
			.create();

	private static final Logger LOGGER = Logger
			.getLogger(ChangingIdentifiersRepositoryWalker.class.getName());

	private final EditListRetriever editListRetriever;

	private final IdentifierInformationScanner infoScanner = new IdentifierInformationScanner();

	/**
	 * @param repositoryDirectory
	 * @param walkingStrategy
	 * @throws IOException
	 */
	public ChangingIdentifiersRepositoryWalker(
			final String repositoryDirectory,
			final ICommitWalkingStrategy walkingStrategy) throws IOException {
		super(repositoryDirectory, walkingStrategy);
		editListRetriever = new EditListRetriever(repository,
				JavaTokenizer.javaCodeFileFilter);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * committools.data.RepositoryFileWalker#visitCommitFiles(org.eclipse.jgit
	 * .revwalk.RevCommit)
	 */
	@Override
	public void visitCommitFiles(final RevCommit commit) {
		try {
			editListRetriever.retrieveEditListBetweenAndCallback(commit,
					commit.getParent(0), this);

		} catch (LargeObjectException | GitAPIException | IOException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		}

	}

	@Override
	public void visitDiffEntry(final DiffEntry entry, final EditList editList,
			final RevCommit commit) throws IOException {
		final String sha = commit.name();
		if (entry.getNewPath().equals("/dev/null")) {
			currentStateOfIdentifiers.removeAll(entry.getOldPath()).forEach(
					i -> i.setIdentifierDeleted());
			return;
		}
		final File targetFile = new File(repository.getRepository()
				.getWorkTree() + entry.getNewPath());
		final Set<IdentifierInformation> newIdentifierInfo = infoScanner
				.scanFile(targetFile, commit.name());
		if (currentStateOfIdentifiers.containsKey(entry.getOldPath())) {
			final Collection<IdentifierInformationThroughTime> state = currentStateOfIdentifiers
					.get(entry.getOldPath());
			// TODO: Match info and update state. This means that any already
			// deleted ids, will not be touched
			// new identifiers will be added. TODO

			if (!entry.getOldPath().equals(entry.getNewPath())) {
				currentStateOfIdentifiers.removeAll(entry.getOldPath());
			}
			currentStateOfIdentifiers.putAll(entry.getNewPath(), state);
		} else {
			// This is a new file, add happily...
			checkArgument(entry.getOldPath().equals("/dev/null"));
			final List<IdentifierInformationThroughTime> infosThroughTime = Lists
					.newArrayList();
			newIdentifierInfo
					.forEach(info -> {
						final IdentifierInformationThroughTime inf = new IdentifierInformationThroughTime();
						inf.addInformation(info);
						infosThroughTime.add(inf);
					});
			currentStateOfIdentifiers.putAll(entry.getNewPath(),
					infosThroughTime);
		}

	}
}
