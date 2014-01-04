/**
 * 
 */
package renaming.priors;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jdt.core.dom.CompilationUnit;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.codeutils.JavaApproximateTypeInferencer;
import codemining.languagetools.Scope;
import codemining.math.probability.DiscreteElementwiseConditionalDistribution;
import codemining.math.probability.IConditionalProbability;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.base.Optional;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class VariableTypePrior implements
		IConditionalProbability<String, Scope> {

	private static final Logger LOGGER = Logger
			.getLogger(VariableTypePrior.class.getName());

	public static VariableTypePrior buildFromFiles(final Collection<File> files) {
		final VariableTypePrior tp = new VariableTypePrior();
		for (final File f : files) {
			try {
				final JavaASTExtractor ex = new JavaASTExtractor(false);
				final CompilationUnit cu = ex.getAST(f);
				final JavaApproximateTypeInferencer typeInf = new JavaApproximateTypeInferencer(
						cu);
				typeInf.infer();
				final Map<String, String> varTypes = typeInf.getVariableTypes();
				for (final Entry<String, String> variable : varTypes.entrySet()) {
					tp.typePrior.addElement(variable.getKey(),
							variable.getValue());
				}
			} catch (final IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}

		return tp;
	}

	public static void main(final String args[]) throws SerializationException {
		if (args.length != 2) {
			System.err.println("Usage <folderWithTypes> <SerialzedTypePrior>");
			return;
		}

		final Collection<File> files = FileUtils.listFiles(new File(args[0]),
				new RegexFileFilter(".*\\.java$"),
				DirectoryFileFilter.DIRECTORY);

		final VariableTypePrior tp = VariableTypePrior.buildFromFiles(files);
		Serializer.getSerializer().serialize(tp, args[1]);
	}

	DiscreteElementwiseConditionalDistribution<String, String> typePrior = new DiscreteElementwiseConditionalDistribution<String, String>();

	/**
	 * 
	 */
	private VariableTypePrior() {
	}

	@Override
	public double getMLProbability(final String element, final Scope given) {
		if (given == null) {
			return 1;
		}
		return typePrior.getMLProbability(element, given.type);
	}

	@Override
	public Optional<String> getMaximumLikelihoodElement(final Scope given) {
		return typePrior.getMaximumLikelihoodElement(given.type);
	}

}
