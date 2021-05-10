package de.upb.swt.soot.java.bytecode.interceptors;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 1997-2020 John Jorgensen, Zun Wang
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import de.upb.swt.soot.core.graph.ExceptionalStmtGraph;
import de.upb.swt.soot.core.jimple.basic.JTrap;
import de.upb.swt.soot.core.jimple.basic.Trap;
import de.upb.swt.soot.core.jimple.common.stmt.Stmt;
import de.upb.swt.soot.core.jimple.javabytecode.stmt.JEnterMonitorStmt;
import de.upb.swt.soot.core.jimple.javabytecode.stmt.JExitMonitorStmt;
import de.upb.swt.soot.core.model.Body;
import de.upb.swt.soot.core.transform.BodyInterceptor;
import java.util.*;
import javax.annotation.Nonnull;

/* @author Zun Wang **/
public class TrapTightener implements BodyInterceptor {

  @Override
  public void interceptBody(@Nonnull Body.BodyBuilder builder) {
    ExceptionalStmtGraph exceptionalGraph = builder.getStmtGraph();
    List<Stmt> stmtsInPrintOrder = builder.getStmts();

    Set<Stmt> monitoredStmts = monitoredStmts(exceptionalGraph);
    // System.out.println(monitoredStmts);
    List<Trap> traps = builder.getTraps();
    List<Trap> newTraps = new ArrayList<>();
    for (Trap trap : traps) {
      boolean isCatchAll =
          trap.getExceptionType().getFullyQualifiedName().equals("java.lang.Throwable");

      // determine the initial trap-scope
      Stmt trapBegin = trap.getBeginStmt();
      Stmt firstUnstrappedStmt = trap.getEndStmt();
      int idx = stmtsInPrintOrder.indexOf(firstUnstrappedStmt);
      Stmt trapEnd = stmtsInPrintOrder.get(idx - 1);
      // initialize a new trap-scope
      Stmt newTrapBegin = null;
      Stmt newTrapEnd = null;
      // set begin of new trap-scope
      for (int i = stmtsInPrintOrder.indexOf(trapBegin); i < stmtsInPrintOrder.size(); i++) {
        Stmt s = stmtsInPrintOrder.get(i);
        if (mightThrow(exceptionalGraph, s, trap)) {
          newTrapBegin = s;
          break;
        }
        // If trap is a catch-all block and the current stmt has an active monitor, we need to keep
        // the block
        if (isCatchAll && monitoredStmts.contains(s)) {
          if (newTrapBegin == null) {
            newTrapBegin = s;
          }
          break;
        }
      }
      // set end of new trap-scope
      // if new trap begin is null, then trap should be empty trap, so don't need to set trap end
      if (newTrapBegin != null) {
        for (int i = stmtsInPrintOrder.indexOf(trapEnd); i >= 0; i--) {
          Stmt s = stmtsInPrintOrder.get(i);
          if (mightThrow(exceptionalGraph, s, trap)) {
            newTrapEnd = s;
            break;
          }
          // If trap is a catch-all block and the current stmt has an active monitor, we need to
          // keep the block
          if (isCatchAll && monitoredStmts.contains(s)) {
            newTrapEnd = s;
            break;
          }
        }
      }
      Trap newTrap = null;
      if (newTrapBegin != null && (newTrapBegin != trapBegin || newTrapEnd != trapEnd)) {
        if (newTrapEnd != trapEnd) {
          int id = stmtsInPrintOrder.indexOf(newTrapEnd);
          firstUnstrappedStmt = stmtsInPrintOrder.get(id + 1);
        }
        newTrap =
            new JTrap(
                trap.getExceptionType(), newTrapBegin, firstUnstrappedStmt, trap.getHandlerStmt());
      }
      if (newTrap != null) {
        newTraps.add(newTrap);
      } else if (newTrapBegin != null) {
        newTraps.add(trap);
      }
    }
    builder.setTraps(newTraps);
  }

  /**
   * Find out all monitored stmts from a given exceptional graph, collect them into a list
   *
   * @param graph a given exceptionalStmtGraph
   * @return a list of monitored stmts
   */
  private Set<Stmt> monitoredStmts(ExceptionalStmtGraph graph) {
    Set<Stmt> monitoredStmts = new HashSet<>();
    Deque<Stmt> queue = new ArrayDeque<>();
    queue.add(graph.getStartingStmt());
    Set<Stmt> visitedStmts = new HashSet<>();

    while (!queue.isEmpty()) {
      Stmt stmt = queue.removeFirst();
      visitedStmts.add(stmt);
      // enter a monitored block
      if (stmt instanceof JEnterMonitorStmt) {
        Deque<Stmt> monitoredQueue = new ArrayDeque();
        monitoredQueue.add(stmt);
        while (!monitoredQueue.isEmpty()) {
          Stmt monitorStmt = monitoredQueue.removeFirst();
          monitoredStmts.add(monitorStmt);
          visitedStmts.add(monitorStmt);
          if (!(monitorStmt instanceof JExitMonitorStmt)) {
            for (Stmt succ : getMixSuccessors(graph, monitorStmt)) {
              if (!visitedStmts.contains(succ)) {
                monitoredQueue.add(succ);
              }
            }
          } else {
            queue.addAll(getMixSuccessors(graph, monitorStmt));
          }
        }
      } else {
        queue.addAll(getMixSuccessors(graph, stmt));
      }
    }
    return monitoredStmts;
  }

  /**
   * Collect all successors (normal and exceptional) of a given stmt into a list.
   *
   * @param graph a ExceptionalStmtGraph
   * @param stmt is a stmt in the given graph
   * @return a list of successors(normal+exceptional) of the given stmt
   */
  private List<Stmt> getMixSuccessors(ExceptionalStmtGraph graph, Stmt stmt) {
    List<Stmt> succs = new ArrayList<>(graph.successors(stmt));
    succs.addAll(graph.exceptionalSuccessors(stmt));
    return succs;
  }

  /**
   * Check whether trap-destinations of the given stmt contain the given trap.
   *
   * @param graph is a exceptional StmtGraph
   * @param stmt is a stmt in the given graph
   * @param trap is a given trap
   * @return If trap-destinations of the given stmt contain the given trap, return true, otherwise
   *     return false
   */
  private boolean mightThrow(ExceptionalStmtGraph graph, Stmt stmt, Trap trap) {
    for (Trap dest : graph.getDestTraps(stmt)) {
      if (dest == trap) {
        return true;
      }
    }
    return false;
  }
}
