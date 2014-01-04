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

import com.google.common.base.Optional;

/**
 * A prior of the names based on the grammar position. Father and grandfather
 * node are not considered independent.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class VariableGrammarPrior implements
		IConditionalProbability<String, Scope> {

	final DiscreteElementwiseConditionalDistribution<String, Integer> parentPrior = new DiscreteElementwiseConditionalDistribution<String, Integer>();
	final DiscreteElementwiseConditionalDistribution<String, Integer> grandParentPrior = new DiscreteElementwiseConditionalDistribution<String, Integer>();

	private static final Logger LOGGER = Logger
			.getLogger(VariableTypePrior.class.getName());

	public static VariableGrammarPrior buildFromFiles(
			final Collection<File> files) {
		final VariableGrammarPrior gp = new VariableGrammarPrior();
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

	private VariableGrammarPrior() {
	}

	@Override
	public double getMLProbability(final String element, final Scope given) {
		return parentPrior.getMLProbability(element, given.astNodeType)
				* grandParentPrior.getMLProbability(element,
						given.astParentNodeType);
	}

	@Override
	public Optional<String> getMaximumLikelihoodElement(final Scope given) {
		throw new NotImplementedException();
	}

}
