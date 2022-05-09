package de.upb.swt.soot.core.graph;
/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2020 Zun Wang
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

import de.upb.swt.soot.core.jimple.basic.Trap;
import de.upb.swt.soot.core.jimple.common.stmt.Stmt;
import java.util.*;
import javax.annotation.Nonnull;

/**
 * @author Zun Wang ExceptionalStmtGraph is used to look up exceptional predecessors and successors
 *     for each stmt. Exceptional Successor of a stmt: If a stmt is in a trap, namely, stmt is
 *     between the trap's beginStmt(inclusive) and this trap's endStmt(exclusive), then the trap's
 *     handlerStmt is the exceptional successor of this stmt. Exceptional Predecessors of a
 *     stmt(handlerStmt): If this handlerStmt is another stmt's successor, then the another stmt is
 *     predecessor of this hanlderStmt. Exceptional DestinationTrap of a stmt: if the stmt is in a
 *     trap, then this trap is the destination trap of the stmt.
 */
public class MutableExceptionalStmtGraph extends MutableStmtGraph {

  @Nonnull private ArrayList<List<Stmt>> exceptionalPreds = new ArrayList<>();
  @Nonnull private ArrayList<List<Stmt>> exceptionalSuccs = new ArrayList<>();
  @Nonnull private ArrayList<List<Trap>> exceptionalDestinationTraps = new ArrayList<>();

  /** creates an empty instance of ExceptionalStmtGraph */
  public MutableExceptionalStmtGraph() {
    super();
  }

  /** creates a mutable copy(!) of originalStmtGraph with exceptional info */
  public MutableExceptionalStmtGraph(@Nonnull StmtGraph oriStmtGraph) {
    super(oriStmtGraph);
    setTraps(oriStmtGraph.getTraps());

    // initialize exceptionalPreds and exceptionalSuccs
    int size = oriStmtGraph.nodes().size();

    for (int i = 0; i < size; i++) {
      exceptionalPreds.add(Collections.emptyList());
      exceptionalSuccs.add(Collections.emptyList());
      exceptionalDestinationTraps.add(Collections.emptyList());
    }

    // if there're traps, then infer every stmt's exceptional succs
    if (!oriStmtGraph.getTraps().isEmpty()) {

      List<Trap> traps = oriStmtGraph.getTraps();

      // Map: key: a stmt  | value: position num of corresponding stmt
      Map<Stmt, Integer> stmtToPosInBody = getStmtToPosInBody(oriStmtGraph);

      // This map is using for collecting predecessors for each handlerStmts
      HashMap<Stmt, List<Stmt>> handlerStmtToPreds = new HashMap<>();
      oriStmtGraph
          .getTraps()
          .forEach(trap -> handlerStmtToPreds.put(trap.getHandlerStmt(), new ArrayList<>()));

      // set exceptional destination-traps and exceptional successors for each stmt
      for (Stmt stmt : oriStmtGraph.nodes()) {
        List<Trap> inferedDests = inferExceptionalDestinations(stmt, stmtToPosInBody, traps);
        List<Stmt> inferedSuccs = new ArrayList<>();
        inferedDests.forEach(trap -> inferedSuccs.add(trap.getHandlerStmt()));
        Integer idx = stmtToIdx.get(stmt);
        exceptionalDestinationTraps.set(idx, inferedDests);
        exceptionalSuccs.set(idx, inferedSuccs);
        inferedSuccs.forEach(handlerStmt -> handlerStmtToPreds.get(handlerStmt).add(stmt));
      }

      // set exceptional predecessors for the stmt which is a handlerStmt
      for (Stmt handlerStmt : handlerStmtToPreds.keySet()) {
        Integer index = stmtToIdx.get(handlerStmt);
        exceptionalPreds.set(index, handlerStmtToPreds.get(handlerStmt));
      }
    }
  }

  @Nonnull
  public List<Stmt> exceptionalPredecessors(@Nonnull Stmt stmt) {
    Integer idx = getNodeIdx(stmt);
    List<Stmt> stmts = exceptionalPreds.get(idx);
    return Collections.unmodifiableList(stmts);
  }

  @Nonnull
  public List<Stmt> exceptionalSuccessors(@Nonnull Stmt stmt) {
    Integer idx = getNodeIdx(stmt);
    List<Stmt> stmts = exceptionalSuccs.get(idx);
    return Collections.unmodifiableList(stmts);
  }

  @Nonnull
  public List<Trap> getDestTraps(@Nonnull Stmt stmt) {
    Integer idx = getNodeIdx(stmt);
    return exceptionalDestinationTraps.get(idx);
  }

  @Override
  public ExceptionalStmtGraph unmodifiableStmtGraph() {
    return new ExceptionalStmtGraph(this);
  }

  /**
   * Set the destinationsTrap of the given stmt as empty list
   *
   * @param stmt a given stmt
   */
  public void removeDestinations(@Nonnull Stmt stmt) {
    Integer idx = getNodeIdx(stmt);
    List<Trap> dests = exceptionalDestinationTraps.get(idx);
    exceptionalDestinationTraps.set(idx, Collections.emptyList());
    exceptionalSuccs.set(idx, Collections.emptyList());
    for (Trap trap : dests) {
      Integer i = getNodeIdx(trap.getHandlerStmt());
      exceptionalPreds.get(i).remove(stmt);
    }
  }

  /**
   * Replaced stmt is never a handlerStmt of a Trap.
   *
   * @param oldStmt a stmt which is already in the StmtGraph
   * @param newStmt a new stmt which will replace the old stmt
   */
  @Override
  public void replaceNode(@Nonnull Stmt oldStmt, @Nonnull Stmt newStmt) {

    super.replaceNode(oldStmt, newStmt);

    int idx = stmtToIdx.get(newStmt);

    if (!exceptionalSuccs.isEmpty()) {
      for (Stmt exceptSucc : exceptionalSuccs.get(idx)) {
        Integer exceptSuccIdx = stmtToIdx.get(exceptSucc);
        exceptionalPreds.get(exceptSuccIdx).remove(oldStmt);
        exceptionalPreds.get(exceptSuccIdx).add(newStmt);
      }

      for (Trap trap : getTraps()) {
        if (trap.getBeginStmt() == newStmt || trap.getEndStmt() == newStmt) {
          int hIdx = stmtToIdx.get(trap.getHandlerStmt());
          for (Stmt exceptPred : exceptionalPreds.get(hIdx)) {
            int exceptPredIdx = stmtToIdx.get(exceptPred);
            List<Trap> dests = exceptionalDestinationTraps.get(exceptPredIdx);
            for (Trap dest : dests) {
              if (dest.getHandlerStmt() == trap.getHandlerStmt()
                  && (dest.getBeginStmt() == oldStmt || dest.getEndStmt() == oldStmt)) {
                exceptionalDestinationTraps.get(exceptPredIdx).remove(dest);
                exceptionalDestinationTraps.get(exceptPredIdx).add(trap);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Add a stmt into StmtGraph before a given stmt that is already in StmtGraph
   *
   * @param node a stmt which should be inserted into StmtGraph, it should be not an instance or
   *     JSwitchStmt or JIfStmt
   * @param succNode a stmt that's already in the stmtGraph, it should be not an instance of
   *     JIdentityStmt T TODO: the inserted node is an instance of PhiStmt, for other stmts maybe
   *     some properties should be added
   */
  @Override
  public void insertNode(@Nonnull Stmt node, @Nonnull Stmt succNode) {
    super.insertNode(node, succNode);
    List<Trap> traps = new ArrayList<>(getTraps());
    boolean hasNewTraps = false;
    if (exceptionalSuccessors(succNode).isEmpty()) {
      exceptionalPreds.add(new ArrayList<>());
      exceptionalSuccs.add(new ArrayList<>());
      exceptionalDestinationTraps.add(new ArrayList<>());
      for (Trap trap : traps) {
        if (succNode == trap.getEndStmt()) {
          Trap newTrap =
              new Trap(trap.getExceptionType(), trap.getBeginStmt(), node, trap.getHandlerStmt());
          traps.remove(trap);
          traps.add(newTrap);
          hasNewTraps = true;
        }
      }
    } else {
      List<Stmt> exSuccs = exceptionalSuccessors(succNode);
      exceptionalPreds.add(new ArrayList<>());
      exceptionalSuccs.add(new ArrayList<>(exSuccs));
      exceptionalDestinationTraps.add(new ArrayList<>(getDestTraps(succNode)));
      for (Stmt exSucc : exSuccs) {
        int idx = getNodeIdx(exSucc);
        exceptionalPreds.get(idx).add(node);
      }
      for (Trap trap : traps) {
        if (succNode == trap.getBeginStmt()) {
          Trap newTrap =
              new Trap(trap.getExceptionType(), node, trap.getEndStmt(), trap.getHandlerStmt());
          traps.remove(trap);
          traps.add(newTrap);
          hasNewTraps = true;
        }
      }
    }
    if (hasNewTraps) {
      setTraps(traps);
    }
  }

  /**
   * Build the map for stmt positions in a StmtGraph
   *
   * @param stmtGraph an instance of StmtGraph
   * @return a map with key: stmt value: the corresponding position number
   */
  private Map<Stmt, Integer> getStmtToPosInBody(StmtGraph stmtGraph) {
    Map<Stmt, Integer> stmtToPos = new HashMap<>();
    Integer pos = 0;
    Iterator<Stmt> it = stmtGraph.iterator();
    while (it.hasNext()) {
      Stmt stmt = it.next();
      stmtToPos.put(stmt, pos);
      pos++;
    }
    return stmtToPos;
  }

  /**
   * Check whether the range of trap1 includes the range of trap2 completely
   *
   * @param trap1 a trap maybe with bigger range
   * @param trap2 a trap maybe with smaller range
   * @param posTable a map that maps each stmt to a position num in body
   * @return true if the range of trap1 includes the range of trap2 completely, else false
   */
  private boolean isInclusive(Trap trap1, Trap trap2, Map<Stmt, Integer> posTable) {
    if (!trap1.getExceptionType().equals(trap2.getExceptionType())) {
      return false;
    }
    Integer posb1 = posTable.get(trap1.getBeginStmt());
    Integer pose1 = posTable.get(trap1.getEndStmt());
    Integer posb2 = posTable.get(trap2.getBeginStmt());
    Integer pose2 = posTable.get(trap2.getEndStmt());
    if (posb1 == null) {
      throw new RuntimeException(
          trap1.getBeginStmt().toString() + " is not contained by pos-table!");
    } else if (pose1 == null) {
      throw new RuntimeException(trap1.getEndStmt().toString() + " is not contained by pos-table!");
    } else if (posb2 == null) {
      throw new RuntimeException(
          trap2.getBeginStmt().toString() + " is not contained by pos-table!");
    } else if (pose2 == null) {
      throw new RuntimeException(trap2.getEndStmt().toString() + " is not contained by pos-table!");
    } else {
      return posb1 < posb2 && pose1 > pose2;
    }
  }

  /**
   * Using the information of body position for each stmt and the information of traps infer the
   * exceptional destinations for a given stmt.
   *
   * @param stmt a given stmt
   * @param posTable a map that maps each stmt to its corresponding position number in the body
   * @param traps a given list of traps
   */
  private List<Trap> inferExceptionalDestinations(
      Stmt stmt, Map<Stmt, Integer> posTable, List<Trap> traps) {
    List<Trap> destinations = new ArrayList<>();
    int pos = posTable.get(stmt);
    // 1.step if the stmt in a trap range, then this trap is a candidate for exceptional destination
    // of the stmt
    for (Trap trap : traps) {
      int beginPos = posTable.get(trap.getBeginStmt());
      int endPos = posTable.get(trap.getEndStmt());
      if (pos >= beginPos && pos < endPos) {
        destinations.add(trap);
      }
    }
    if (destinations.isEmpty()) {
      return Collections.emptyList();
    }

    // 2.step if a trap includes another trap completely, then delete this trap-candidate
    List<Trap> removedTraps = new ArrayList<>();
    for (Trap dest : destinations) {
      for (Trap anotherDest : destinations) {
        if (isInclusive(dest, anotherDest, posTable)) {
          removedTraps.add(dest);
        }
      }
    }
    if (!removedTraps.isEmpty()) {
      destinations.removeAll(removedTraps);
    }
    return destinations;
  }

  /** Remove a node from the graph. */
  @Override
  public void removeNode(@Nonnull Stmt node) {
    super.removeNode(node);

    // remove node from exceptional successor list of nodes exceptional predecessors
    final List<Stmt> epreds = exceptionalPreds.get(removedIdx);
    for (Stmt epred : epreds) {
      int predIdx = getNodeIdx(epred);
      exceptionalSuccs.get(predIdx).remove(node);
      List<Trap> dests = new ArrayList<>(exceptionalDestinationTraps.get(predIdx));
      for (Trap dest : dests) {
        if (dest.getHandlerStmt() == node) {
          exceptionalDestinationTraps.get(predIdx).remove(dest);
        }
      }
    }
    exceptionalPreds.set(removedIdx, null);
    exceptionalDestinationTraps.set(removedIdx, null);

    // remove node from exceptional predecessor list of nodes exceptional successors
    final List<Stmt> esuccs = exceptionalSuccs.get(removedIdx);
    esuccs.forEach(esucc -> exceptionalPreds.get(getNodeIdx(esucc)).remove(node));
    exceptionalSuccs.set(removedIdx, null);
  }

  /** This method is used to add a normal node. */
  @Override
  public int addNode(@Nonnull Stmt node) {
    super.addNode(node);
    if (exceptionalPreds == null) {
      exceptionalPreds = new ArrayList<>();
    }
    if (exceptionalSuccs == null) {
      exceptionalSuccs = new ArrayList<>();
    }
    if (exceptionalDestinationTraps == null) {
      exceptionalDestinationTraps = new ArrayList<>();
    }
    exceptionalPreds.add(new ArrayList<>());
    exceptionalSuccs.add(new ArrayList<>());
    exceptionalDestinationTraps.add(new ArrayList<>());
    return stmtToIdx.get(node);
  }

  private int getNodeIdxOrCreate(@Nonnull Stmt node) {
    Integer idx = stmtToIdx.get(node);
    if (idx == null) {
      idx = addNode(node);
    }
    return idx;
  }

  /** Put a normal edge in exceptional StmtGraph */
  @Override
  public void putEdge(@Nonnull Stmt from, @Nonnull Stmt to) {
    int fromIdx = getNodeIdxOrCreate(from);
    int toIdx = getNodeIdxOrCreate(to);

    predecessors.get(toIdx).add(from);
    successors.get(fromIdx).add(to);
  }
}
