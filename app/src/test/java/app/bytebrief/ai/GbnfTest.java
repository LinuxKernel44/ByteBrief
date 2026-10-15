package app.bytebrief.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GbnfTest {
    @Test
    public void simpleObjectGrammarHasExpectedShape() {
        String g = Gbnf.grammar(Gbnf.field("ok", Gbnf.bool()), Gbnf.field("name", Gbnf.str(1, 20)));
        assertEquals("root ::= \"{\" ws \"\\\"ok\\\"\" ws \":\" ws bool ws \",\" ws \"\\\"name\\\"\" ws \":\" ws str1x20 ws \"}\"\n"
                + "ws ::= [ \\t\\n]{0,4}\n"
                + "bool ::= \"true\" | \"false\"\n"
                + "int ::= [0-9]{1,3}\n"
                + "char ::= [^\"\\\\\\x00-\\x1f] | \"\\\\\" [\"\\\\/bfnrt]\n"
                + "str1x20 ::= \"\\\"\" char{1,20} \"\\\"\"\n", g);
    }

    @Test
    public void arrayBoundsAreEncoded() {
        String zeroToThree = Gbnf.grammar(Gbnf.field("a", Gbnf.array(Gbnf.str(1, 5), 0, 3)));
        assertTrue(zeroToThree, zeroToThree.contains("\"[\" ws ( str1x5 ( ws \",\" ws str1x5 ){0,2} )? ws \"]\""));
        String oneToFour = Gbnf.grammar(Gbnf.field("a", Gbnf.array(Gbnf.integer(), 1, 4)));
        assertTrue(oneToFour, oneToFour.contains("\"[\" ws int ( ws \",\" ws int ){0,3} ws \"]\""));
    }

    @Test
    public void everyShippedGrammarDefinesAllRulesItUses() {
        for (String g : new String[]{Grammars.SUMMARY, Grammars.TAKEAWAYS, Grammars.EXPLANATION, Grammars.ANSWER, Grammars.BRIEFING}) {
            Set<String> defined = new HashSet<>();
            Matcher d = Pattern.compile("(?m)^([A-Za-z0-9_-]+) ::=").matcher(g);
            while (d.find()) defined.add(d.group(1));
            assertTrue(defined.contains("root"));
            // Strip literals and character classes, then every remaining identifier must be a defined rule.
            String body = g.replaceAll("(?m)^[A-Za-z0-9_-]+ ::=", "")
                    .replaceAll("\"(\\\\.|[^\"\\\\])*\"", " ")
                    .replaceAll("\\[(\\\\.|[^\\]\\\\])*\\]", " ")
                    .replaceAll("\\{[0-9,]*\\}", " ");
            Matcher u = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*").matcher(body);
            while (u.find()) assertTrue("undefined rule " + u.group() + " in\n" + g, defined.contains(u.group()));
        }
    }

    @Test
    public void briefingGrammarEmitsKeysInDeclaredOrder() {
        String g = Grammars.BRIEFING;
        assertTrue(g.indexOf("headline") < g.indexOf("entries"));
        assertTrue(g.indexOf("entries") < g.indexOf("themes"));
    }
}
