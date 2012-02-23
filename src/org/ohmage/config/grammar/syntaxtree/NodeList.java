//
// Generated by JTB 1.3.2
//

package org.ohmage.config.grammar.syntaxtree;

import java.util.Enumeration;
import java.util.Vector;

/**
 * Represents a grammar list, e.g. ( A )+
 */
public class NodeList implements NodeListInterface {
	/**
	 * Static-random serialVersionUID.
	 */
	private static final long serialVersionUID = -6729043994182356652L;

	public NodeList() {
		nodes = new Vector<Node>();
	}

	public NodeList(Node firstNode) {
		nodes = new Vector<Node>();
		addNode(firstNode);
	}

	public void addNode(Node n) {
		nodes.addElement(n);
	}

	public Enumeration<Node> elements() { return nodes.elements(); }
	public Node elementAt(int i)  { return nodes.elementAt(i); }
	public int size()             { return nodes.size(); }
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

	public Vector<Node> nodes;
}

