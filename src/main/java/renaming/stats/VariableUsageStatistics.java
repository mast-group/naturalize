/**
 * 
 */
package renaming.stats;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import codemining.java.codeutils.scopes.ScopesTUI;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Longs;

/**
 * Get the usage of the variables.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class VariableUsageStatistics {

	/**
	 * A struct containing information about variable usage.
	 * 
	 */
	private static class UsageStats implements Comparable<UsageStats> {
		public long timesSeen = 0;
		public long sumContexts = 0;

		@Override
		public int compareTo(UsageStats other) {
			return Longs.compare(timesSeen, other.timesSeen);
		}
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.err.println("Usage <projectFolder> variable|method");
			return;
		}

		final IScopeExtractor scopeExtractor = ScopesTUI
				.getScopeExtractorByName(args[1]);

		final File directory = new File(args[0]);
		final VariableUsageStatistics vus = new VariableUsageStatistics(
				directory, new JavaTokenizer(), scopeExtractor);
		vus.extractStats();
		vus.printStats();
	}

	private final Collection<File> allFiles;

	private final IScopeExtractor scopeExtractor;

	private final ITokenizer tokenizer;

	private final Map<String, UsageStats> statistics;

	/**
	 * 
	 */
	public VariableUsageStatistics(final File directory,
			final ITokenizer tokenizer, final IScopeExtractor scopeExtractor) {
		allFiles = FileUtils.listFiles(directory, tokenizer.getFileFilter(),
				DirectoryFileFilter.DIRECTORY);
		this.scopeExtractor = scopeExtractor;
		statistics = Maps.newTreeMap();
		this.tokenizer = tokenizer;
	}

	public void extractStats() throws IOException {
		for (final File f : allFiles) {
			final Multimap<Scope, String> scopes = scopeExtractor
					.getFromFile(f);
			for (final Entry<Scope, String> entry : scopes.entries()) {
				final String varName = entry.getValue();
				final List<String> tokens = tokenizer.tokenListFromCode(entry
						.getKey().code.toCharArray());
				int count = 0;
				for (final String token : tokens) {
					if (token.equals(varName)) {
						count++;
					}
				}
				final UsageStats stats;
				if (statistics.containsKey(varName)) {
					stats = statistics.get(varName);
				} else {
					stats = new UsageStats();
					statistics.put(varName, stats);
				}
				stats.sumContexts += count;
				stats.timesSeen++;
			}
		}
	}

	public void printStats() {
		for (final Entry<String, UsageStats> entry : statistics.entrySet()) {

			final UsageStats stats = entry.getValue();
			final double avgContextSize = ((double) stats.sumContexts)
					/ stats.timesSeen;
			System.out.println(entry.getKey() + "\t" + stats.timesSeen + "\t"
					+ avgContextSize);
		}
	}
}
