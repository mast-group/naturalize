/**
 * 
 */
package renaming.priors;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.java.codeutils.scopes.VariableScopeExtractor;
import codemining.languagetools.Scope;
import codemining.math.probability.DiscreteElementwiseConditionalDistribution;
import codemining.math.probability.IConditionalProbability;
import codemining.util.data.Pair;

import com.google.common.base.Optional;

/**
 * A prior of the names based on the grammar position. Father and grandfather
 * node are considered independent.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class JavaVariableGrammarPrior implements
		IConditionalProbability<String, Pair<Integer, Integer>> {

	final DiscreteElementwiseConditionalDistribution<String, Integer> parentPrior = new DiscreteElementwiseConditionalDistribution<String, Integer>();
	final DiscreteElementwiseConditionalDistribution<String, Integer> grandParentPrior = new DiscreteElementwiseConditionalDistribution<String, Integer>();

	private static final Logger LOGGER = Logger
			.getLogger(JavaVariableNameTypeDistribution.class.getName());

	public static JavaVariableGrammarPrior buildFromFiles(
			final Collection<File> files) {
		final JavaVariableGrammarPrior gp = new JavaVariableGrammarPrior();
		for (final File f : files) {
			try {
				for (final Entry<Scope, String> variable : VariableScopeExtractor
						.getScopeSnippets(f).entries()) {
					gp.parentPrior.addElement(variable.getValue(),
							variable.getKey().astNodeType);
					gp.grandParentPrior.addElement(variable.getValue(),
							variable.getKey().astParentNodeType);
				}
			} catch (final IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}

		return gp;
	}

	private JavaVariableGrammarPrior() {
	}

	@Override
	public Optional<String> getMaximumLikelihoodElement(
			final Pair<Integer, Integer> given) {
		throw new NotImplementedException();
	}

	@Override
	public double getMLProbability(final String element,
			final Pair<Integer, Integer> given) {
		return parentPrior.getMLProbability(element, given.first)
				* grandParentPrior.getMLProbability(element, given.second);
	}

}
