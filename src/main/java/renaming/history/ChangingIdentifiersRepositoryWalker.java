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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.revwalk.RevCommit;

import codemining.java.tokenizers.JavaTokenizer;
import codemining.util.data.Pair;

import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
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
			if (!identifierThroughTime.isEmpty()
					&& !getLast().name.equals(idInfo.name)) {
				System.out.println(getLast().name + "->" + idInfo.name);
			}
			identifierThroughTime.add(idInfo);
		}

		public final IdentifierInformation getLast() {
			checkArgument(!identifierThroughTime.isEmpty());
			return identifierThroughTime.get(identifierThroughTime.size() - 1);
		}

		public boolean isDeleted() {
			return identifierDeleted;
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
		cirw.doWalk();
		// printVersions(cirw);
	}

	/**
	 * @param cirw
	 */
	private static void printVersions(
			final ChangingIdentifiersRepositoryWalker cirw) {
		for (final IdentifierInformationThroughTime idChain : cirw.allIdentifierChains) {
			if (idChain.identifierThroughTime.size() > 0) {
				System.out.println("Identifier for "
						+ idChain.identifierThroughTime.get(0).name);

				for (final IdentifierInformation identifier : idChain.identifierThroughTime) {
					System.out.println(identifier);
				}
				System.out
						.println("***************************************************");
			}
		}
	}

	private final List<IdentifierInformationThroughTime> allIdentifierChains = Lists
			.newArrayList();

	private final Multimap<String, IdentifierInformationThroughTime> currentStateOfIdentifiers = ArrayListMultimap
			.create();

	private static final Logger LOGGER = Logger
			.getLogger(ChangingIdentifiersRepositoryWalker.class.getName());

	private static final int MAX_LINE_RANGE_SIZE = 5;

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

	private void doFirstScan(final File repositoryDir, final String sha) {
		for (final File f : FileUtils
				.listFiles(repositoryDir, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY)) {
			final String fileInRepo = f.getAbsolutePath().substring(
					(int) (repositoryDir.getAbsolutePath().length() + 1));
			Set<IdentifierInformation> identiferInfos;
			try {
				identiferInfos = infoScanner.scanFile(f, sha);
				identiferInfos
						.forEach(info -> {
							final IdentifierInformationThroughTime iitt = new IdentifierInformationThroughTime();
							iitt.addInformation(info);
							currentStateOfIdentifiers.put(fileInRepo, iitt);
						});
			} catch (final IOException e) {
				LOGGER.severe("Could not find file " + f + "\n"
						+ ExceptionUtils.getFullStackTrace(e));
			}

		}
	}

	/**
	 * Return a range of the possible line positions of the old line number in
	 * the new file.
	 *
	 * @param declarationLineNumber
	 * @param editList
	 * @return
	 */
	private Range<Integer> getNewLineGivenOld(final int oldLineNumber,
			final EditList editList) {
		int offsetAbove = 0;
		for (final Edit edit : editList) {
			if (edit.getBeginA() < oldLineNumber
					&& edit.getEndA() < oldLineNumber) {
				offsetAbove += -(edit.getEndA() - edit.getBeginA())
						+ (edit.getEndB() - edit.getBeginB());
			} else if (edit.getBeginA() <= oldLineNumber
					&& edit.getEndA() >= oldLineNumber) {
				// if it was in the old range, it is now in the new range
				checkArgument(
						edit.getBeginA() + offsetAbove == edit.getBeginB(),
						"Beggining was %s but expected %s", edit.getBeginB(),
						edit.getBeginA() + offsetAbove);
				return Range.closed(edit.getBeginB(), edit.getEndB());
			} else {
				return Range.closed(oldLineNumber + offsetAbove, oldLineNumber
						+ offsetAbove);
			}
		}
		return Range.closed(oldLineNumber + offsetAbove, oldLineNumber
				+ offsetAbove);
	}

	/**
	 * Return the equivalence classes of the variables. This is essentially, a
	 * list of variables that could be identical.
	 *
	 * @param state
	 * @param newIdentifierInfo
	 * @return
	 */
	Collection<Pair<IdentifierInformationThroughTime, Set<IdentifierInformation>>> matchByType(
			final Collection<IdentifierInformationThroughTime> state,
			final Set<IdentifierInformation> newIdentifierInfo) {
		final List<Pair<IdentifierInformationThroughTime, Set<IdentifierInformation>>> equivalenceClasses = Lists
				.newArrayList();
		for (final IdentifierInformationThroughTime iitt : state) {
			if (!iitt.isDeleted()) {
				equivalenceClasses.add(Pair.create(iitt, Sets.newHashSet()));
			}
		}

		for (final IdentifierInformation newIdentifierInformation : newIdentifierInfo) {
			// First try to match to an existing set
			boolean atLeastOneMatchFound = false;
			for (final Pair<IdentifierInformationThroughTime, Set<IdentifierInformation>> idPair : equivalenceClasses) {
				if (idPair.first == null) {
					continue;
				} else if (idPair.first.getLast().areTypeEqual(
						newIdentifierInformation)) {
					atLeastOneMatchFound = true;
					idPair.second.add(newIdentifierInformation);
				}
			}
			// If we fail, add
			if (!atLeastOneMatchFound) {
				equivalenceClasses.add(Pair.create(null,
						Sets.newHashSet(newIdentifierInformation)));
			}
		}
		return equivalenceClasses;
	}

	/**
	 * Get the actual match
	 *
	 * @param state
	 * @param editList
	 * @param unmatchedNewIdentifiers
	 * @param unmatchedIitts
	 * @param possibleMatchedIds
	 * @param iitt
	 */
	private void matchIittToIdentifier(
			final Collection<IdentifierInformationThroughTime> state,
			final EditList editList,
			final Set<IdentifierInformation> unmatchedNewIdentifiers,
			final Set<IdentifierInformationThroughTime> unmatchedIitts,
			final Set<IdentifierInformation> possibleMatchedIds,
			final IdentifierInformationThroughTime iitt) {
		final Range<Integer> newLineNumber = getNewLineGivenOld(
				iitt.getLast().declarationLineNumber, editList);
		final Set<IdentifierInformation> availableMatches = Sets
				.newIdentityHashSet();
		for (final IdentifierInformation idInfo : possibleMatchedIds) {
			if (newLineNumber.contains(idInfo.declarationLineNumber)
					&& unmatchedNewIdentifiers.contains(idInfo)
					&& !rangeTooBroad(newLineNumber)) {
				availableMatches.add(idInfo);
			}
		}
		if (!availableMatches.isEmpty()) {
			final Set<IdentifierInformation> fullMatchindIdentifiers = Sets
					.filter(availableMatches,
							new Predicate<IdentifierInformation>() {
								@Override
								public boolean apply(
										final IdentifierInformation t) {
									return t.areProbablyIdentical(iitt
											.getLast());
								}
							});
			if (fullMatchindIdentifiers.size() == 1) { // try to match exact
				final IdentifierInformation idInfo = fullMatchindIdentifiers
						.iterator().next();
				iitt.addInformation(idInfo);
				unmatchedNewIdentifiers.remove(idInfo);
				checkArgument(unmatchedIitts.remove(iitt));
				return;
			}
			// Find the one that matches best, or fail
			if (availableMatches.size() == 1) {
				final IdentifierInformation idInfo = availableMatches
						.iterator().next();
				iitt.addInformation(idInfo);
				unmatchedNewIdentifiers.remove(idInfo);
				checkArgument(unmatchedIitts.remove(iitt));
				return;
			}
			// otherwise fail miserably
		}

		// We failed to match, the id was deleted/renamed
		iitt.setIdentifierDeleted();
		checkArgument(unmatchedIitts.remove(iitt));

	}

	/**
	 * Heuristically decide if this range is too broad
	 *
	 * @param newLineNumber
	 * @return
	 */
	private boolean rangeTooBroad(final Range<Integer> newLineNumber) {
		return newLineNumber.upperEndpoint() - newLineNumber.lowerEndpoint() > MAX_LINE_RANGE_SIZE;
	}

	/**
	 * Update the current state with the new identifiers. Alternative
	 * implementations of this might someday be able to detect renamings.
	 *
	 * @param state
	 * @param newIdentifierInfo
	 * @param editList
	 * @param currentFilePath
	 */
	private void updateIdentifierInfoState(
			final Collection<IdentifierInformationThroughTime> state,
			final Set<IdentifierInformation> newIdentifierInfo,
			final EditList editList, final String currentFilePath) {
		// Match pairs of compatible variables
		final Collection<Pair<IdentifierInformationThroughTime, Set<IdentifierInformation>>> matchedPairs = matchByType(
				state, newIdentifierInfo);

		// Do matching
		final Set<IdentifierInformation> unmatchedNewIdentifiers = Sets
				.newIdentityHashSet();
		unmatchedNewIdentifiers.addAll(newIdentifierInfo);

		final Set<IdentifierInformationThroughTime> unmatchedIitts = Sets
				.newIdentityHashSet();
		unmatchedIitts.addAll(state);

		for (final Pair<IdentifierInformationThroughTime, Set<IdentifierInformation>> matchedIds : matchedPairs) {
			// match variables with only one candidate
			final Set<IdentifierInformation> possibleMatchedIds = matchedIds.second;
			final IdentifierInformationThroughTime iitt = matchedIds.first;
			checkArgument(iitt == null ? true : !iitt.isDeleted());
			if (possibleMatchedIds.isEmpty()) {
				// Name was deleted (or renamed). Stop chain here.
				iitt.setIdentifierDeleted();
				checkArgument(unmatchedIitts.remove(iitt));
			} else if (iitt == null) {
				// This is a new identifier
				checkArgument(possibleMatchedIds.size() == 1);
				final IdentifierInformationThroughTime newIitt = new IdentifierInformationThroughTime();
				final IdentifierInformation variableToAdd = possibleMatchedIds
						.iterator().next();
				newIitt.addInformation(variableToAdd);
				unmatchedNewIdentifiers.remove(variableToAdd);
				state.add(newIitt);
			} else {
				checkArgument(possibleMatchedIds.size() >= 1, "Size was %s",
						possibleMatchedIds.size());
				matchIittToIdentifier(state, editList, unmatchedNewIdentifiers,
						unmatchedIitts, possibleMatchedIds, iitt);
			}
		}
		for (final IdentifierInformation ii : unmatchedNewIdentifiers) {
			// We failed to match these, create new ids...
			final IdentifierInformationThroughTime newIitt = new IdentifierInformationThroughTime();
			newIitt.addInformation(ii);
			state.add(newIitt);
		}

		for (final IdentifierInformationThroughTime iitt : unmatchedIitts) {
			if (!iitt.isDeleted()) {
				iitt.setIdentifierDeleted();
			}
		}
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
			System.out.println(commit);
			if (commit.getParentCount() > 0) {
				editListRetriever.retrieveEditListBetweenAndCallback(commit,
						commit.getParent(0), this);
			} else {
				doFirstScan(repositoryDir, commit.getName());
			}
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
					i -> {
						if (!i.isDeleted()) {
							i.setIdentifierDeleted();
						}
					});
			return;
		}
		final String repositoryFolder = repository.getRepository()
				.getWorkTree() + "/";

		final File targetFile = new File(repositoryFolder + entry.getNewPath());
		final Set<IdentifierInformation> newIdentifierInfo = infoScanner
				.scanFile(targetFile, commit.name());
		if (currentStateOfIdentifiers.containsKey(entry.getOldPath())) {
			final Collection<IdentifierInformationThroughTime> state = currentStateOfIdentifiers
					.get(entry.getOldPath());
			final List<IdentifierInformationThroughTime> allIitts = Lists
					.newArrayList(state);
			final Set<IdentifierInformationThroughTime> setIitts = Sets
					.newIdentityHashSet();
			setIitts.addAll(state);
			checkArgument(setIitts.size() == allIitts.size(),
					"Before adding, state was inconsistent for ", targetFile);
			updateIdentifierInfoState(state, newIdentifierInfo, editList,
					entry.getNewPath());

			if (!entry.getOldPath().equals(entry.getNewPath())) {
				currentStateOfIdentifiers.putAll(entry.getNewPath(), state);
				currentStateOfIdentifiers.removeAll(entry.getOldPath());
			}
		} else {
			// This is a new file or a file we failed to index before, add
			// happily...
			// checkArgument(entry.getOldPath().equals("/dev/null"));
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
