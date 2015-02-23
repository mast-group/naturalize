/**
 *
 */
package renaming.history;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.codeutils.MethodUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Scan a file to produce
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class IdentifierInformationScanner {

	private static class DeclarationExtractor extends ASTVisitor {

		public static List<String> getAstParentString(final ASTNode node) {
			final List<String> ancestry = Lists.newArrayList();
			ASTNode parentNode = node.getParent();
			while (parentNode != null) {
				ancestry.add(ASTNode.nodeClassForType(parentNode.getNodeType())
						.getName());
				parentNode = parentNode.getParent();
			}
			return ancestry;
		}

		final Set<IdentifierInformation> identifiers = Sets.newHashSet();
		private final String SHA;

		private final String file;

		private final CompilationUnit cu;

		private DeclarationExtractor(final String sha, final String file,
				final CompilationUnit cu) {
			SHA = sha;
			this.file = file;
			this.cu = cu;
		}

		private int getLineNumber(final ASTNode node) {
			return cu.getLineNumber(node.getStartPosition());
		}

		@Override
		public boolean visit(final FieldDeclaration node) {
			for (final Object fragment : node.fragments()) {
				final VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
				final IdentifierInformation vd = new IdentifierInformation(SHA,
						file, vdf.getName().getIdentifier(), node.getType()
								.toString(), getLineNumber(vdf),
						getAstParentString(vdf));
				identifiers.add(vd);
			}
			return super.visit(node);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom
		 * .MethodDeclaration)
		 */
		@Override
		public boolean visit(final MethodDeclaration node) {
			final String methodType = MethodUtils.getMethodType(node);

			final IdentifierInformation md = new IdentifierInformation(SHA,
					file, node.getName().getIdentifier(), methodType,
					getLineNumber(node), getAstParentString(node));
			identifiers.add(md);
			return super.visit(node);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see
		 * org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom
		 * .SingleVariableDeclaration)
		 */
		@Override
		public boolean visit(final SingleVariableDeclaration node) {
			final IdentifierInformation vd = new IdentifierInformation(SHA,
					file, node.getName().getIdentifier(), node.getType()
							.toString().toString(), getLineNumber(node),
					getAstParentString(node));
			identifiers.add(vd);
			return super.visit(node);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom
		 * .VariableDeclarationExpression)
		 */
		@Override
		public boolean visit(final VariableDeclarationExpression node) {
			for (final Object fragment : node.fragments()) {
				final VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
				final IdentifierInformation vd = new IdentifierInformation(SHA,
						file, vdf.getName().getIdentifier(), node.getType()
								.toString(), getLineNumber(vdf),
						getAstParentString(vdf));
				identifiers.add(vd);
			}
			return super.visit(node);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom
		 * .VariableDeclarationStatement)
		 */
		@Override
		public boolean visit(final VariableDeclarationStatement node) {
			for (final Object fragment : node.fragments()) {
				final VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
				final IdentifierInformation vd = new IdentifierInformation(SHA,
						file, vdf.getName().getIdentifier(), node.getType()
								.toString(), getLineNumber(vdf),
						getAstParentString(vdf));
				identifiers.add(vd);
			}
			return super.visit(node);
		}
	}

	public static final void main(final String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("Usage <file>");
			System.exit(-1);
		}
		final File file = new File(args[0]);
		final IdentifierInformationScanner fs = new IdentifierInformationScanner();
		for (final IdentifierInformation info : fs.scanFile(file, "unknownsha")) {
			System.out.println(info);
		}
	}

	final JavaASTExtractor astExtactor = new JavaASTExtractor(false);

	public final Set<IdentifierInformation> scanFile(final File file,
			final String sha) throws IOException {
		final CompilationUnit cu = astExtactor.getAST(file);
		final DeclarationExtractor de = new DeclarationExtractor(sha,
				file.getAbsolutePath(), cu);
		cu.accept(de);

		final Set<IdentifierInformation> identifiers = de.identifiers;
		return identifiers;
	}

}
