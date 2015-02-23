package renaming.history;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.revwalk.RevCommit;

import renaming.history.RepentDataParser.Renaming;
import codemining.java.codeutils.binding.JavaApproximateVariableBindingExtractor;
import codemining.java.codeutils.binding.JavaMethodDeclarationBindingExtractor;
import codemining.java.codeutils.binding.tui.JavaBindingsToJson.SerializableResolvedSourceCode;
import codemining.languagetools.bindings.ResolvedSourceCode;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import committools.data.RepositoryFileWalker;

public class RenamingDatasetExtractor {

	public static class BindingExtractor extends RepositoryFileWalker {

		List<RenamedSerializableResolvedSourceCode> renamedVariablesDatapoints = Lists
				.newArrayList();

		List<RenamedSerializableResolvedSourceCode> renamedMethodDeclarationsDatapoints = Lists
				.newArrayList();

		final Multimap<String, Renaming> renamings;

		private final JavaApproximateVariableBindingExtractor variableExtractor = new JavaApproximateVariableBindingExtractor();
		private final JavaMethodDeclarationBindingExtractor methodDeclExtractor = new JavaMethodDeclarationBindingExtractor();

		public BindingExtractor(final String repositoryDirectory,
				final Multimap<String, Renaming> renamings) throws IOException {
			super(repositoryDirectory, RepositoryFileWalker.BASE_WALK);
			this.renamings = renamings;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see
		 * committools.data.RepositoryFileWalker#isVisitableCommit(org.eclipse
		 * .jgit.revwalk.RevCommit)
		 */
		@Override
		public boolean isVisitableCommit(final RevCommit commit) {
			return renamings.containsKey(commit.name());
		}

		private boolean matchRenaming(final Renaming renaming, final ASTNode n) {
			if (!n.toString().equals(renaming.nameAfter)) {
				return false;
			}
			final CompilationUnit cu = (CompilationUnit) n.getRoot();
			return renaming.linesAfter.contains(cu.getLineNumber(n
					.getStartPosition()));
		}

		@Override
		public void visitCommitFiles(final RevCommit commit) {
			System.out.println("Visiting " + commit + " for renamings");
			final Collection<Renaming> renamingsAtThisPoint = renamings
					.get(commit.name());
			for (final Renaming renaming : renamingsAtThisPoint) {
				// Get the file
				final File currentFile = new File(
						repositoryDir.getAbsolutePath() + renaming.filename);
				checkArgument(currentFile.exists());

				try {
					ResolvedSourceCode resolvedCode = variableExtractor
							.getResolvedSourceCode(currentFile,
									n -> matchRenaming(renaming, n));
					if (!resolvedCode.getAllBindings().isEmpty()) {
						renamedVariablesDatapoints
								.add(RenamedSerializableResolvedSourceCode
										.fromResolvedSourceCode(
												resolvedCode,
												Lists.newArrayList(renaming.nameBefore)));
					} else {
						resolvedCode = methodDeclExtractor
								.getResolvedSourceCode(currentFile,
										n -> matchRenaming(renaming, n));
						if (!resolvedCode.getAllBindings().isEmpty()) {
							renamedMethodDeclarationsDatapoints
									.add(RenamedSerializableResolvedSourceCode.fromResolvedSourceCode(
											resolvedCode,
											Lists.newArrayList(renaming.nameBefore)));
						}
					}
				} catch (final IOException e) {
					// File always exists, since we checked above.
					throw new IllegalStateException(e);
				}
			}
		}
	}

	public static class RenamedSerializableResolvedSourceCode extends
			SerializableResolvedSourceCode {

		public static RenamedSerializableResolvedSourceCode fromResolvedSourceCode(
				final ResolvedSourceCode rsc, final List<String> previousNames) {
			return new RenamedSerializableResolvedSourceCode(rsc, previousNames);
		}

		public final List<String> previousNames;

		protected RenamedSerializableResolvedSourceCode(
				final ResolvedSourceCode rsc, final List<String> previousNames) {
			super(rsc);
			this.previousNames = previousNames;
		}

	}

	public static void main(final String[] args) throws NoWorkTreeException,
			NoHeadException, IOException, GitAPIException {
		if (args.length != 4) {
			System.err
					.println("Usage <datafile> <prefix> <repositoryDir> <outputFilePrefix>");
			System.exit(-1);
		}
		final SvnToGitMapper mapper = new SvnToGitMapper(args[2]);
		final RepentDataParser rdp = new RepentDataParser(new File(args[0]),
				mapper.mapSvnToGit(), args[1], new Predicate<Integer>() {

					@Override
					public boolean test(final Integer t) {
						return t > 250;
					}
				});
		final List<Renaming> renamings = rdp.parse();
		final Multimap<String, Renaming> renamingsPerSha = mapRenamingsToTargetSha(renamings);
		final BindingExtractor be = new BindingExtractor(args[2],
				renamingsPerSha);
		be.doWalk();

		writeJson(args[3], "_variables.json", be.renamedVariablesDatapoints);
		writeJson(args[3], "_methoddeclarations.json",
				be.renamedMethodDeclarationsDatapoints);
	}

	public static Multimap<String, Renaming> mapRenamingsToTargetSha(
			final List<Renaming> renamings) {
		final Multimap<String, Renaming> renamingsPerSha = HashMultimap
				.create();
		renamings.forEach(r -> renamingsPerSha.put(r.toVersion, r));
		return renamingsPerSha;
	}

	/**
	 * @param outputFilePrefix
	 * @param outputFileSuffix
	 * @param renamedVariablesDatapoints
	 * @throws IOException
	 * @throws JsonIOException
	 */
	public static void writeJson(
			final String outputFilePrefix,
			final String outputFileSuffix,
			final List<RenamedSerializableResolvedSourceCode> renamedVariablesDatapoints)
			throws IOException, JsonIOException {
		final File outputFile = new File(outputFilePrefix + outputFileSuffix);
		final FileWriter writer = new FileWriter(outputFile);
		try {
			final Gson gson = new Gson();
			gson.toJson(renamedVariablesDatapoints, writer);
		} finally {
			writer.close();
		}
	}

}
