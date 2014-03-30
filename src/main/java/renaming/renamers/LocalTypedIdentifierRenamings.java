/**
 * 
 */
package renaming.renamers;

import java.io.File;
import java.util.Collection;
import java.util.logging.Logger;

import renaming.priors.JavaVariableNameTypeDistribution;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope;

import com.google.common.math.DoubleMath;

/**
 * Use type information for renaming stuff.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class LocalTypedIdentifierRenamings extends BaseIdentifierRenamings {

	JavaVariableNameTypeDistribution tp;

	private static final Logger LOGGER = Logger
			.getLogger(LocalTypedIdentifierRenamings.class.getName());

	/**
	 * 
	 */
	public LocalTypedIdentifierRenamings(final ITokenizer tokenizer) {
		super(tokenizer);
	}

	@Override
	public double addScopePriors(final String identifierName, final Scope scope) {
		final double prob = tp.getMLProbability(identifierName, scope.type);
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
		tp = JavaVariableNameTypeDistribution.buildFromFiles(trainingFiles);
	}

}
