/*+********************************************************************* 
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation
Foundation, Inc., 59 Temple Place - Suite 330, Boston MA 02111-1307, USA.
************************************************************************/

package monq.jfa;

import java.io.*;
import java.util.*;

/**
 * computes statistical data about an {@link Nfa} or {@link Dfa}.
 */
public class Statistics {
  /** the number of states in the FA */
  public int numStates = 0;

  /** number of states with at least one epsilon move. */
  public int nfaStates = 0;

  /** number of stop states found */
  public int stopStates = 0;

  /** shortest path to a stop state */
  public int shortestPathlen = Integer.MAX_VALUE;

  /** longest loop free path to a stop state */
  public int longestPathlen = -1;

  /** type and number of CharTrans implementations used */
  public Map<Class<?>,Int> charTransTypes = new HashMap<Class<?>,Int>();

  private int currentDepth = 0;
  private StringBuilder sb = new StringBuilder();

  private String lineSeparator 
    = System.getProperty("line.separator", "\n");
  /********************************************************************/
  private Statistics() {}
  /********************************************************************/
  private static class Int { public int i=0; }
  /********************************************************************/
  private static class TrivTupel {
    StringBuilder sb = new StringBuilder();
    int elems = 0;
  }
  /********************************************************************/
  /**
   * dumps the object's contents.
   */
  public void print(PrintWriter pw) {
    pw.println("                    number of states: "+numStates);
    pw.println("     number of states with eps moves: "+nfaStates);
    pw.println("               number of stop states: "+stopStates);
    pw.println("         shortest path to stop state: "+shortestPathlen);
    pw.println("longest loop free path to stop state: "+longestPathlen);
    pw.println("the following transition table types are used:");
    for(Class<?> c: charTransTypes.keySet()) {
      long stats = 0;
      try {
        stats = c.getField("stats").getLong(c);
      } catch( Exception e) { e.printStackTrace(); }
      Int count = charTransTypes.get(c);
      pw.printf("%33s: %4d, used: %8d\n", c.getName(), count.i, stats);
    }
  }
  /********************************************************************/
  /**
   * compute statistical data for a {@link Nfa}. If the given
   * <code>Writer</code> is not <code>null</code>, regular expressions
   * describing the shortest path to stop states are printed.
   */
  public static Statistics getStatistics(Nfa nfa, Writer w) 
    throws IOException 
  {
    Statistics s = new Statistics();
    s.get(nfa.getStart(), w, new HashSet<AbstractFaState>());
    return s;
  }
  /**
   * same as {@link #getStatistics(Nfa,Writer)} but for a {@link
   * Dfa}. 
   */
  public static Statistics getStatistics(Dfa dfa, Writer w) 
    throws IOException 
  {
    Statistics s = new Statistics();
    s.get(dfa.getStart(), w, new HashSet<DfaState>());
    return s;
  }
  /********************************************************************/
  private <STATE extends FaState<STATE>> void 
  get(STATE s, Writer w, Set<STATE> known)
      throws IOException 
  {
    known.add(s);

    numStates += 1;
    if( s.getEps()!=null && s.getEps().length>0 ) nfaStates += 1;

    // handle stop state
    FaAction a = s.getAction();
    if( a!=null ) {
      stopStates += 1;
      if( currentDepth<shortestPathlen ) shortestPathlen = currentDepth;
      if( currentDepth>longestPathlen ) longestPathlen = currentDepth;
      if( w!=null ) {
	w.write(sb.substring(0)); 
	w.write("==>");
	w.write(a.toString());
	w.write(lineSeparator);
      }
    }

    // prepare a set of all child states in order to be able to create
    // a complete regular expression describing the shortest string
    // necessary to reach the node
    Map<STATE,TrivTupel> m = new HashMap<>();
    STATE[] epsChildren = s.getEps();
    if( epsChildren!=null ) {
      int l = epsChildren.length;
      for(int i=0; i<l; i++) {
	STATE child = epsChildren[i];
	if( known.contains(child) ) continue;
	TrivTupel step = m.get(child);
	if( step==null ) m.put(child, step=new TrivTupel());
	step.sb.append("<eps>");
	step.elems += 1;
      }
    }
    
    CharTrans<STATE> t = s.getTrans();
    if( t!=null ) {
      Class<?> c = t.getClass();
      Int count = charTransTypes.get(c);
      if( count==null ) charTransTypes.put(c, count=new Int());
      count.i += 1;

      int l = t.size();
      for(int i=0; i<l; i++) {
	STATE child = t.getAt(i);
	if( known.contains(child) ) continue;
	TrivTupel step = m.get(child);
	if( step==null ) m.put(child, step=new TrivTupel());
	appendStep(step.sb, t.getFirstAt(i), t.getLastAt(i));
	step.elems += 1;
      }
    }

    // this one reset just before return, we set the depth for the
    // children 
    currentDepth += 1;

    int sblen = sb.length();
    Iterator<STATE> it = m.keySet().iterator();
    while( it.hasNext() ) {
      STATE child = it.next();
      TrivTupel tt = m.get(child);
      if( tt.elems>1 ) {
	sb.append('(').append(tt.sb).append(')');
      } else {
	sb.append(tt.sb);
      }
      get(child, w, known);
      sb.setLength(sblen);
    }

    currentDepth -= 1;
  }
  /********************************************************************/
  private static void appendStep(StringBuilder sbuf, char a, char b) {
    if( sbuf.length()>0 ) sbuf.append('|');
    if( a==b ) {
      nc(sbuf, a);
      return;
    }
  
    sbuf.append('[');
    nc(sbuf, a);
    sbuf.append('-');
    nc(sbuf, b);
    sbuf.append(']');
  }
  /********************************************************************/
  private static void nc(StringBuilder b, char ch) {
    if( ch=='\n' ) b.append("\\n");
    else if( ch=='\t' ) b.append("\\t");
    else if( ch=='\r' ) b.append("\\r");
    else if( ch=='[' ) b.append("\\[");
    else if( ch==']' ) b.append("\\]");
    else if( ch=='-' ) b.append("\\-");
    else if( ch<' ' ) b.append("\\u").append((int)ch);
    else b.append(ch);
  }
}