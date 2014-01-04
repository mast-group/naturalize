/**
 * 
 */
package renaming.ngram;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import renaming.renamers.INGramIdentifierRenamer;
import codemining.lm.ngram.NGram;

import com.google.common.collect.Lists;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PotentialRenamingsTest {

	@Test
	public void testSubstitution() {
		final List<String> lst = Lists.newArrayList();
		lst.add("aa");
		lst.add(INGramIdentifierRenamer.WILDCARD_TOKEN);
		lst.add("cc");
		final NGram<String> ng = new NGram<String>(lst, 0, 3);

		NGram<String> sub = NGram.substituteTokenWith(ng,
				INGramIdentifierRenamer.WILDCARD_TOKEN, "bb");

		assertEquals(sub.get(0), "aa");
		assertEquals(sub.get(1), "bb");
		assertEquals(sub.get(2), "cc");
		assertEquals(sub.size(), 3);

		lst.set(1, "var%" + INGramIdentifierRenamer.WILDCARD_TOKEN + "%");
		sub = NGram.substituteTokenWith(ng,
				INGramIdentifierRenamer.WILDCARD_TOKEN, "bb");

		assertEquals(sub.get(0), "aa");
		assertEquals(sub.get(1), "var%bb%");
		assertEquals(sub.get(2), "cc");
		assertEquals(sub.size(), 3);
	}

}
