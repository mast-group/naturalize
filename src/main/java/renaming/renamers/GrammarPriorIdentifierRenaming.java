/**
 * 
 */
package renaming.renamers;

import java.io.File;
import java.util.Collection;
import java.util.logging.Logger;

import renaming.priors.JavaVariableGrammarPrior;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope;
import codemining.util.data.Pair;

import com.google.common.math.DoubleMath;

/**
 * Identifier Renaming with grammar priors.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class GrammarPriorIdentifierRenaming extends BaseIdentifierRenamings {

	private static final Logger LOGGER = Logger
			.getLogger(GrammarPriorIdentifierRenaming.class.getName());

	JavaVariableGrammarPrior gp;

	/**
	 * 
	 */
	public GrammarPriorIdentifierRenaming(final ITokenizer tokenizer) {
		super(tokenizer);
	}

	@Override
	protected double addScopePriors(final String identifierName,
			final Scope scope) {
		final double prob = gp.getMLProbability(identifierName,
				Pair.create(scope.astNodeType, scope.astParentNodeType));
		if (prob > 0) {
			return -DoubleMath.log2(prob);
		} else if (!this.isTrueUNK(identifierName)) {
			return 100;
		} else {
			return 0;
		}
	}

	@Override
	public void buildRenamingModel(final Collection<File> trainingFiles) {
		super.buildRenamingModel(trainingFiles);
		gp = JavaVariableGrammarPrior.buildFromFiles(trainingFiles);
	}

}
