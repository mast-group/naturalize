/**
 * 
 */
package renaming.evaluation;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.RandomUtils;

import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.INGramIdentifierRenamer.Renaming;
import codemining.java.codeutils.scopes.ScopedIdentifierRenaming;
import codemining.java.codeutils.scopes.ScopesTUI;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;
import codemining.languagetools.Scope;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * A class to get some output for human evaluation of our errors.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class HumanEvaluationOutput {

	/**
	 * Struct class for containing data.
	 * 
	 */
	public static class RenamingsData {
		public SortedSet<Renaming> renamings;
		String ground;

		@Override
		public String toString() {
			return ground + "\n" + renamings.toString();
		}
	}

	private static final Logger LOGGER = Logger
			.getLogger(HumanEvaluationOutput.class.getName());

	/**
	 * @param args
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws ClassNotFoundException
	 */
	public static void main(String[] args) throws InstantiationException,
			IllegalAccessException, ClassNotFoundException {
		if (args.length < 5) {
			System.err
					.println("Usage: <projectDir> <tokenizerClass> variable|method|class examplesToGenerate <renamerClass> [renamerParams]");
			return;
		}

		final File directory = new File(args[0]);
		final IScopeExtractor extractor = ScopesTUI
				.getScopeExtractorByName(args[2]);
		final long nExamples = Long.parseLong(args[3]);

		final Class<? extends ITokenizer> tokenizerName = (Class<? extends ITokenizer>) Class
				.forName(args[1]);
		final ITokenizer tokenizer = tokenizerName.newInstance();

		final String renamerClass = args[4];

		final HumanEvaluationOutput heo = new HumanEvaluationOutput(directory,
				extractor, renamerClass, args.length == 5 ? null : args[5],
				tokenizer, nExamples);
		heo.getOutput();

	}

	private final List<File> allFiles;

	private final ITokenizer tokenizer;

	private final String renamerClass;

	private final long nExamples;

	private final String renamerConstructorParam;

	private final IScopeExtractor scopeExtractor;

	private final Map<Integer, RenamingsData> cheatsheet = Maps.newTreeMap();

	private int nextQuestionId = 1;

	/**
	 * 
	 */
	public HumanEvaluationOutput(final File directory,
			final IScopeExtractor extractor, final String renamerClass,
			final String renamerConstructorParam, final ITokenizer tokenizer,
			final long nExamples) {
		allFiles = Lists.newArrayList(FileUtils.listFiles(directory,
				tokenizer.getFileFilter(), DirectoryFileFilter.DIRECTORY));
		Collections.shuffle(allFiles);
		this.tokenizer = tokenizer;
		this.nExamples = nExamples;
		this.renamerClass = renamerClass;
		this.renamerConstructorParam = renamerConstructorParam;
		scopeExtractor = extractor;
	}

	public void getOutput() {
		for (long i = 0; i < nExamples; i++) {
			try {
				final int idx = RandomUtils.nextInt(allFiles.size());
				final File testFile = allFiles.get(idx);

				final Collection<File> trainFiles = Sets.newTreeSet(allFiles);
				checkArgument(trainFiles.remove(testFile));

				final AbstractIdentifierRenamings renamer = getRenamer(
						renamerClass, renamerConstructorParam);
				renamer.buildRenamingModel(trainFiles);

				// Get scopes
				final Multimap<Scope, String> scopes = scopeExtractor
						.getFromFile(testFile);
				if (scopes.isEmpty()) {
					i--;
					continue;
				}
				final List<Entry<Scope, String>> allIdentifiers = Lists
						.newArrayList(scopes.entries());
				Collections.shuffle(allIdentifiers);
				final Entry<Scope, String> selected = allIdentifiers
						.get(RandomUtils.nextInt(allIdentifiers.size()));

				// Get results
				final SortedSet<Renaming> renamings = renamer.getRenamings(
						selected.getKey(), selected.getValue());

				if (renamings.isEmpty()
						|| renamings.first().name.equals(selected.getValue())) {
					i--;
					continue;
				}

				// Print possible terms/snippet and store
				boolean includedGround = false;
				final List<String> alternatives = Lists.newArrayList();
				int counter = 0;
				for (final Renaming renaming : renamings) {
					if ((counter <= 3) || (counter <= 4 && includedGround)) {
						counter++;
					} else {
						break;
					}
					final String nextName = renaming.name;
					if (nextName.equals(selected.getValue())) {
						includedGround = true;
					}
					alternatives.add(nextName);
				}
				if (!includedGround) {
					alternatives.add(selected.getValue());
				}
				if (alternatives.size() != 5) {
					i--;
					continue;
				}

				// Now output and add to cheatsheet
				Collections.shuffle(alternatives);
				System.out.println("============Renaming " + nextQuestionId
						+ "==============");
				final ScopedIdentifierRenaming sir = new ScopedIdentifierRenaming(
						scopeExtractor, ParseType.COMPILATION_UNIT);
				final String renamed = sir.getFormattedRenamedCode(
						selected.getKey().code, selected.getValue(),
						"GUESS_IT", FileUtils.readFileToString(testFile));
				System.out.println(renamed);
				System.out
						.println("===================Alternatives==================");
				for (final String alternative : alternatives) {
					System.out.println(alternative);
				}
				System.out.println("=====================================");
				final RenamingsData rd = new RenamingsData();
				rd.renamings = renamings;
				rd.ground = selected.getValue();
				cheatsheet.put(nextQuestionId, rd);
				nextQuestionId++;
			} catch (Throwable e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}
		printCheatsheet();
	}

	public AbstractIdentifierRenamings getRenamer(final String renamerClass,
			final String renamerConstructorParams) {
		AbstractIdentifierRenamings renamer;
		try {
			if (renamerConstructorParams == null) {
				renamer = (AbstractIdentifierRenamings) Class
						.forName(renamerClass)
						.getDeclaredConstructor(ITokenizer.class)
						.newInstance(tokenizer);
			} else {
				renamer = (AbstractIdentifierRenamings) Class
						.forName(renamerClass)
						.getDeclaredConstructor(ITokenizer.class, String.class)
						.newInstance(tokenizer, renamerConstructorParams);
			}
		} catch (IllegalArgumentException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (SecurityException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (InstantiationException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (IllegalAccessException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (InvocationTargetException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (NoSuchMethodException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		} catch (ClassNotFoundException e) {
			LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
			throw new IllegalArgumentException(e);
		}
		return renamer;
	}

	public void printCheatsheet() {
		System.out.println("==============Cheatsheet====================");
		for (final Entry<Integer, RenamingsData> entry : cheatsheet.entrySet()) {
			System.out.println(entry.getKey() + ". " + entry.getValue());
		}
		System.out.println("=========================================");
	}
}
