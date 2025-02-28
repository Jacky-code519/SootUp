package sootup.core.model;
/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2020 Marcus Nachtigall
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
import java.util.*;
import javax.annotation.Nonnull;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.JIdentityStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.visitor.ReplaceUseStmtVisitor;

/**
 * Util class for the Body
 *
 * @author Marcus Nachtigall, Zun Wang
 */
public class BodyUtils {

  // TODO: [ms] please refactor into respective classes or at least rename to.. StmtUtils?

  /**
   * Collects all defining statements of a Local from a list of statements
   *
   * @param stmts The searched list of statements
   * @return A map of Locals and their using statements
   */
  public static Map<Local, List<Stmt>> collectDefs(List<Stmt> stmts) {
    Map<Local, List<Stmt>> allDefs = new HashMap<>();
    for (Stmt stmt : stmts) {
      List<Value> defs = stmt.getDefs();
      for (Value value : defs) {
        if (value instanceof Local) {
          List<Stmt> localDefs = allDefs.get(value);
          if (localDefs == null) {
            localDefs = new ArrayList<>();
          }
          localDefs.add(stmt);
          allDefs.put((Local) value, localDefs);
        }
      }
    }
    return allDefs;
  }

  /**
   * Collects all using statements of a Local from a list of statements
   *
   * @param stmts The searched list of statements
   * @return A map of Locals and their using statements
   */
  public static Map<Local, List<Stmt>> collectUses(List<Stmt> stmts) {
    Map<Local, List<Stmt>> allUses = new HashMap<>();
    for (Stmt stmt : stmts) {
      List<Value> uses = stmt.getUses();
      for (Value value : uses) {
        if (value instanceof Local) {
          List<Stmt> localUses = allUses.get(value);
          if (localUses == null) {
            localUses = new ArrayList<>();
          }
          localUses.add(stmt);
          allUses.put((Local) value, localUses);
        }
      }
    }
    return allUses;
  }

  public static List<AbstractDefinitionStmt> getDefsOfLocal(Local local, List<Stmt> defs) {
    List<AbstractDefinitionStmt> localDefs = new ArrayList<>();
    for (Stmt stmt : defs) {
      if (stmt instanceof AbstractDefinitionStmt
          && ((AbstractDefinitionStmt) stmt).getLeftOp().equals(local)) {
        localDefs.add((AbstractDefinitionStmt) stmt);
      }
    }
    return localDefs;
  }

  /**
   * Get all definition-stmts which define the given local used by the given stmt.
   *
   * @param graph a stmt graph which contains the given stmts.
   * @param use a local that is used by the given stmt.
   * @param stmt a stmt which uses the given local.
   */
  public static List<Stmt> getDefsForLocalUse(StmtGraph<?> graph, Local use, Stmt stmt) {
    if (!stmt.getUses().contains(use)) {
      throw new RuntimeException(stmt + " doesn't use the local " + use.toString());
    }
    List<Stmt> defStmts = new ArrayList<>();
    Set<Stmt> visited = new HashSet<>();

    Deque<Stmt> queue = new ArrayDeque<>();
    queue.add(stmt);
    while (!queue.isEmpty()) {
      Stmt s = queue.removeFirst();
      if (!visited.contains(s)) {
        visited.add(s);
        if (s instanceof AbstractDefinitionStmt && s.getDefs().get(0).equivTo(use)) {
          defStmts.add(s);
        } else {
          for (Stmt pred : graph.predecessors(s)) {
            queue.add(pred);
          }
        }
      }
    }
    return defStmts;
  }

  /**
   * Use newUse to replace the oldUse in oldStmt.
   *
   * @param oldStmt a Stmt that has oldUse.
   * @param oldUse a Value in the useList of oldStmt.
   * @param newUse a Value is to replace oldUse
   * @return a new Stmt with newUse
   */
  @Nonnull
  public static Stmt withNewUse(
      @Nonnull Stmt oldStmt, @Nonnull Value oldUse, @Nonnull Value newUse) {
    ReplaceUseStmtVisitor visitor = new ReplaceUseStmtVisitor(oldUse, newUse);
    oldStmt.accept(visitor);
    return visitor.getResult();
  }

  /**
   * Use newDef to replace the definition in oldStmt.
   *
   * @param oldStmt a Stmt whose def is to be replaced.
   * @param newDef a Local to replace definition Local of oldStmt.
   * @return a new Stmt with newDef
   */
  @Nonnull
  public static Stmt withNewDef(@Nonnull Stmt oldStmt, @Nonnull Local newDef) {
    if (oldStmt instanceof JAssignStmt) {
      return ((JAssignStmt<?, ?>) oldStmt).withVariable(newDef);
    } else if (oldStmt instanceof JIdentityStmt) {
      return ((JIdentityStmt<?>) oldStmt).withLocal(newDef);
    }
    throw new RuntimeException("The given stmt must be JAssignStmt or JIdentityStmt!");
  }
}
