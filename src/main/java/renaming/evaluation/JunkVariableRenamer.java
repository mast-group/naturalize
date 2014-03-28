/**
 * 
 */
package renaming.evaluation;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.distribution.ZipfDistribution;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.codeutils.scopes.ScopedIdentifierRenaming;
import codemining.java.codeutils.scopes.VariableScopeExtractor;
import codemining.java.codeutils.scopes.VariableScopeExtractor.Variable;
import codemining.languagetools.ParseType;
import codemining.util.SettingsLoader;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Rename a percent of variable names to junk and see how it affects us.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class JunkVariableRenamer {

	public static final int ZIPFS_ELEMENTS = (int) SettingsLoader
			.getNumericSetting("JunkVariableRenamer.zipfsElements", 1000);

	public static final double ZIPFS_SLOPE = SettingsLoader.getNumericSetting(
			"zipfsSlope", 1.08);

	public static void main(final String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage <file> <%selection>");
		}

		final double percentToRename = Double.parseDouble(args[1]);
		final File inputFile = new File(args[0]);
		final JunkVariableRenamer jvi = new JunkVariableRenamer();
		System.out.println(jvi.renameAllVarsInFile(percentToRename, inputFile));
	}

	/**
	 * @param vars
	 * @param entry
	 * @return
	 */
	private Set<Integer> getCurrentlyUsedNames(
			final Multimap<ASTNode, Variable> vars,
			final Entry<ASTNode, Variable> entry) {
		// Create a list of all the names that are used in that scope
		final Set<Variable> scopeUsedNames = Sets.newHashSet();
		// Check all the parents & self
		ASTNode currentNode = entry.getKey();
		while (currentNode != null) {
			scopeUsedNames.addAll(vars.get(currentNode));
			currentNode = currentNode.getParent();
		}
		// and now add all children
		final ASTVisitor v = new ASTVisitor() {
			@Override
			public void preVisit(final ASTNode node) {
				scopeUsedNames.addAll(vars.get(node));
			}
		};
		entry.getKey().accept(v);
		// and we're done
		return getUsedIds(scopeUsedNames);
	}

	private Set<Integer> getUsedIds(final Set<Variable> variables) {
		final Set<Integer> usedIds = Sets.newTreeSet();
		for (final Variable variable : variables) {
			if (variable.name.matches("^junk[0-9]+$")) {
				usedIds.add(Integer.parseInt(variable.name.substring(4)));
			}
		}
		return usedIds;
	}

	/**
	 * @param percentToRename
	 * @param inputFile
	 * @return
	 * @throws Exception
	 * @deprecated this is not fair for small files.
	 */
	@Deprecated
	public String renameAllVarsInFile(final double percentToRename,
			final File inputFile) throws Exception {

		String file = FileUtils.readFileToString(inputFile);
		final Multimap<ASTNode, Variable> vars = VariableScopeExtractor
				.getVariableScopes(inputFile);
		final int cnt = (int) (vars.size() * percentToRename);

		for (int i = 0; i < cnt; i++) {
			file = renameSingleVariablesToJunk(file);
		}

		final JavaASTExtractor ex = new JavaASTExtractor(false);
		return ex.getBestEffortAstNode(file).toString();
	}

	/**
	 * Rename a random variable to junk.
	 * 
	 * @param file
	 * @return
	 * @throws Exception
	 */
	public String renameSingleVariablesToJunk(final String file)
			throws Exception {
		final Multimap<ASTNode, Variable> vars = VariableScopeExtractor
				.getVariableScopes(file, ParseType.COMPILATION_UNIT);
		final List<Entry<ASTNode, Variable>> selected = Lists.newArrayList();
		for (final Entry<ASTNode, Variable> entry : vars.entries()) {
			if (!entry.getValue().name.matches("^junk[0-9]+$")) {
				selected.add(entry);
			}
		}

		if (selected.size() == 0) {
			return file;
		}
		Collections.shuffle(selected);
		final Entry<ASTNode, Variable> entry = selected.get(0);

		final Set<Integer> scopeUsedNames = getCurrentlyUsedNames(vars, entry);

		// Find an unused name, at random.
		final ZipfDistribution z = new ZipfDistribution(ZIPFS_ELEMENTS,
				ZIPFS_SLOPE);

		int unusedName = z.sample();
		while (scopeUsedNames.contains(unusedName)) {
			unusedName = z.sample();
		}
		final ScopedIdentifierRenaming scr = new ScopedIdentifierRenaming(
				new VariableScopeExtractor.VariableScopeSnippetExtractor(),
				ParseType.COMPILATION_UNIT);

		final String renamed = scr.getRenamedCode(entry.getKey().toString(),
				entry.getValue().name, "junk" + unusedName, file);

		// Format code naively
		final JavaASTExtractor ex = new JavaASTExtractor(false);
		return ex.getBestEffortAstNode(renamed).toString();
	}
}
