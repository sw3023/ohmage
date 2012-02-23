//
// Generated by JTB 1.3.2
//

package org.ohmage.config.grammar.syntaxtree;

/**
 * Grammar production:
 * f0 -> "and"
 *       | "or"
 */
public class Conjunction implements Node {
	/**
	 * Static-random serialVersionUID.
	 */
	private static final long serialVersionUID = -4974285350336643437L;
	public NodeChoice f0;

	public Conjunction(NodeChoice n0) {
		f0 = n0;
	}

	public void accept(org.ohmage.config.grammar.visitor.Visitor v) {
		v.visit(this);
	}
	public <R,A> R accept(org.ohmage.config.grammar.visitor.GJVisitor<R,A> v, A argu) {
		return v.visit(this,argu);
	}
	public <R> R accept(org.ohmage.config.grammar.visitor.GJNoArguVisitor<R> v) {
		return v.visit(this);
	}
	public <A> void accept(org.ohmage.config.grammar.visitor.GJVoidVisitor<A> v, A argu) {
		v.visit(this,argu);
	}
}

