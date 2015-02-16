/**
 *
 */
package renaming.history;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A single identifier name through the history of the repository. When a
 * renaming happens, this object is "locked".
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class NameThroughHistory {

	public static final class ChangeEvent {
		final boolean wasRenamed;
		final String changeSha;
		final int declarationLine;

		public ChangeEvent(final String changeSha, final boolean wasRenamed,
				final int declarationLine) {
			this.changeSha = changeSha;
			this.wasRenamed = wasRenamed;
			this.declarationLine = declarationLine;
		}
	}

	public final String name;

	private List<ChangeEvent> changeEvents = Lists.newArrayList();

	public NameThroughHistory(final String name, final String appearedSha,
			final int initialDeclarationLine) {
		this.name = name;
		final ChangeEvent ce = new ChangeEvent(appearedSha, false,
				initialDeclarationLine);
		changeEvents.add(ce);
	}

	/**
	 * Add a line to the history of this name.
	 *
	 * @param sha
	 * @param newDeclarationLine
	 * @param wasRenamed
	 */
	public void changeWasMade(final String sha, final int newDeclarationLine,
			final boolean wasRenamed) {
		final ChangeEvent ce = new ChangeEvent(sha, wasRenamed,
				newDeclarationLine);
		changeEvents.add(ce);
		if (wasRenamed) {
			changeEvents = ImmutableList.copyOf(changeEvents);
		}
	}

	public List<ChangeEvent> getChangeEvents() {
		return changeEvents;
	}

}
