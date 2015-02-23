/**
 *
 */
package renaming.history;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import com.google.common.base.Objects;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class IdentifierInformation {

	public final String SHA;
	public final String filePath;
	public final String name;
	public final String type;
	public final List<String> declaringAstParent;
	public final int declarationLineNumber;

	/**
	 *
	 */
	public IdentifierInformation(final String sha, final String filePath,
			final String name, final String type, final int declarationLine,
			final List<String> declaringAstParents) {
		this.SHA = checkNotNull(sha);
		this.filePath = checkNotNull(filePath);
		this.name = checkNotNull(name);
		this.type = checkNotNull(type);
		this.declarationLineNumber = declarationLine;
		this.declaringAstParent = declaringAstParents;
	}

	/**
	 * Check if an identifier is of the same type and name.
	 *
	 * @param other
	 * @return
	 */
	public boolean areProbablyIdentical(final IdentifierInformation other) {
		return name.equals(other.name) && type.equals(other.type)
				&& declaringAstParent.equals(other.declaringAstParent);
	}

	public boolean areTypeEqual(final IdentifierInformation other) {
		return type.equals(other.type)
				&& declaringAstParent.equals(other.declaringAstParent);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final IdentifierInformation other = (IdentifierInformation) obj;
		return Objects.equal(SHA, other.SHA)
				&& Objects.equal(filePath, other.filePath)
				&& Objects.equal(name, other.name)
				&& Objects.equal(type, other.type)
				&& Objects.equal(declarationLineNumber,
						other.declarationLineNumber);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hashCode(SHA, filePath, name, type,
				declarationLineNumber);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append(SHA);
		builder.append(":");
		builder.append(filePath);
		builder.append(":");
		builder.append(name);
		builder.append(":");
		builder.append(type);
		builder.append(":");
		builder.append(declarationLineNumber);
		return builder.toString();
	}

}
