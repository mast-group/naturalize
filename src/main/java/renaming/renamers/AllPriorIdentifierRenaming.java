/**
 * 
 */
package renaming.renamers;

import java.io.File;
import java.util.Collection;
import java.util.logging.Logger;

import renaming.priors.JavaVariableGrammarPrior;
import renaming.priors.JavaVariableNameTypeDistribution;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope;
import codemining.util.SettingsLoader;
import codemining.util.data.Pair;

import com.google.common.math.DoubleMath;

/**
 * A renaming class that uses both the grammar and the type prior.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class AllPriorIdentifierRenaming extends BaseIdentifierRenamings {

	JavaVariableNameTypeDistribution tp;

	JavaVariableGrammarPrior gp;
	private static final Logger LOGGER = Logger
			.getLogger(AllPriorIdentifierRenaming.class.getName());

	public static final boolean USE_GRAMMAR = SettingsLoader.getBooleanSetting(
			"useGrammar", true);

	public static final boolean USE_TYPES = SettingsLoader.getBooleanSetting(
			"useTypes", true);

	/**
	 * @param tokenizer
	 */
	public AllPriorIdentifierRenaming(final ITokenizer tokenizer) {
		super(tokenizer);
	}

	private double addGrammarPrior(final String identifierName,
			final Scope scope) {
		if (!USE_GRAMMAR) {
			return 0;
		}
		final double prob = gp.getMLProbability(identifierName,
				Pair.create(scope.astNodeType, scope.astParentNodeType));
		if (prob > 0) {
			return -DoubleMath.log2(prob);
		} else if (!this.isTrueUNK(identifierName)) {
			return 6;
		} else {
			return 0;
		}
	}

	@Override
	public double addScopePriors(final String identifierName, final Scope scope) {
		return addTypePrior(identifierName, scope)
				+ addGrammarPrior(identifierName, scope);
	}

	private double addTypePrior(final String identifierName, final Scope scope) {
		if (!USE_TYPES) {
			return 0;
		}
		final double prob = tp.getMLProbability(identifierName, scope.type);
		if (prob > 0) {
			return -DoubleMath.log2(prob);
		} else if (!this.isTrueUNK(identifierName)) {
			return 6;
		} else {
			return 0;
		}
	}

	@Override
	public void buildRenamingModel(final Collection<File> trainingFiles) {
		super.buildRenamingModel(trainingFiles);
		if (USE_TYPES) {
			tp = JavaVariableNameTypeDistribution.buildFromFiles(trainingFiles);
		}
		if (USE_GRAMMAR) {
			gp = JavaVariableGrammarPrior.buildFromFiles(trainingFiles);
		}
	}

}
