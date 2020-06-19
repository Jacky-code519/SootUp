package de.upb.swt.soot.core.model;
/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 1997 - 1999 Raja Vallee-Rai
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

import de.upb.swt.soot.core.graph.ImmutableStmtGraph;
import de.upb.swt.soot.core.graph.MutableStmtGraph;
import de.upb.swt.soot.core.graph.StmtGraph;
import de.upb.swt.soot.core.jimple.basic.*;
import de.upb.swt.soot.core.jimple.common.ref.JParameterRef;
import de.upb.swt.soot.core.jimple.common.ref.JThisRef;
import de.upb.swt.soot.core.jimple.common.stmt.*;
import de.upb.swt.soot.core.jimple.javabytecode.stmt.JSwitchStmt;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.signatures.MethodSubSignature;
import de.upb.swt.soot.core.signatures.PackageName;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.types.Type;
import de.upb.swt.soot.core.types.VoidType;
import de.upb.swt.soot.core.util.Copyable;
import de.upb.swt.soot.core.util.EscapedWriter;
import de.upb.swt.soot.core.util.ImmutableUtils;
import de.upb.swt.soot.core.util.printer.Printer;
import de.upb.swt.soot.core.validation.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Class that models the Jimple body (code attribute) of a method.
 *
 * @author Linghui Luo
 */
public class Body implements Copyable {

  public static final Body EMPTY_BODY =
      new Body(
          new MethodSignature(
              new ClassType() {
                @Override
                public boolean isBuiltInClass() {
                  return false;
                }

                @Override
                public String getFullyQualifiedName() {
                  return "No.Body";
                }

                @Override
                public String getClassName() {
                  return "Body";
                }

                @Override
                public PackageName getPackageName() {
                  return new PackageName("No");
                }
              },
              new MethodSubSignature("body", Collections.emptyList(), VoidType.getInstance())),
          Collections.emptySet(),
          Collections.emptyList(),
          new MutableStmtGraph(),
          NoPositionInformation.getInstance());

  /** The locals for this Body. */
  private final Set<Local> locals;

  /** The traps for this Body. */
  private final List<Trap> traps;

  /** The stmts for this Body. */
  @Nonnull private final ImmutableStmtGraph cfg;

  /** The Position Information in the Source for this Body. */
  @Nonnull private final Position position;

  /** The method associated with this Body. */
  @Nonnull private MethodSignature methodSignature;

  /** An array containing some validators in order to validate the JimpleBody */
  @Nonnull
  private static final List<BodyValidator> validators =
      ImmutableUtils.immutableList(
          new LocalsValidator(),
          new TrapsValidator(),
          new StmtBoxesValidator(),
          new UsesValidator(),
          new ValueBoxesValidator(),
          new CheckInitValidator(),
          new CheckTypesValidator(),
          new CheckVoidLocalesValidator(),
          new CheckEscapingValidator());

  /**
   * Creates an body which is not associated to any method.
   *
   * @param locals please use {@link LocalGenerator} to generate local for a body.
   */
  public Body(
      @Nonnull MethodSignature methodSignature,
      @Nonnull Set<Local> locals,
      @Nonnull List<Trap> traps,
      @Nonnull StmtGraph stmtGraph,
      @Nonnull Position position) {
    this.methodSignature = methodSignature;
    this.locals = Collections.unmodifiableSet(locals);
    this.traps = Collections.unmodifiableList(traps);
    this.cfg = ImmutableStmtGraph.copyOf(stmtGraph);
    this.position = position;

    // FIXME: [JMP] Virtual method call in constructor
    checkInit();
  }

  @Nonnull
  public static Body getNoBody() {
    return EMPTY_BODY;
  }

  /**
   * Returns the MethodSignature associated with this Body.
   *
   * @return the method that owns this body.
   */
  public MethodSignature getMethodSignature() {
    return methodSignature;
  }

  /** Returns the number of locals declared in this body. */
  public int getLocalCount() {
    return locals.size();
  }

  private void runValidation(BodyValidator validator) {
    final List<ValidationException> exceptionList = new ArrayList<>();
    validator.validate(this, exceptionList);
    if (!exceptionList.isEmpty()) {
      throw exceptionList.get(0);
    }
  }

  /** Verifies that a ValueBox is not used in more than one place. */
  public void validateValueBoxes() {
    runValidation(new ValueBoxesValidator());
  }

  /** Verifies that each Local of getUsesAndDefs() is in this body's locals Chain. */
  public void validateLocals() {
    runValidation(new LocalsValidator());
  }

  /** Verifies that the begin, end and handler units of each trap are in this body. */
  public void validateTraps() {
    runValidation(new TrapsValidator());
  }

  /** Verifies that the StmtBoxes of this Body all point to a Stmt contained within this body. */
  public void validateStmtBoxes() {
    runValidation(new StmtBoxesValidator());
  }

  /** Verifies that each use in this Body has a def. */
  public void validateUses() {
    runValidation(new UsesValidator());
  }

  /** Returns a backed chain of the locals declared in this Body. */
  public Set<Local> getLocals() {
    return locals;
  }

  /** Returns a backed view of the traps found in this Body. */
  public List<Trap> getTraps() {
    return traps;
  }

  /** Return unit containing the \@this-assignment * */
  public Stmt getThisStmt() {
    for (Stmt u : getStmts()) {
      if (u instanceof JIdentityStmt && ((JIdentityStmt) u).getRightOp() instanceof JThisRef) {
        return u;
      }
    }

    throw new RuntimeException("couldn't find this-assignment!" + " in " + getMethodSignature());
  }

  /** Return LHS of the first identity stmt assigning from \@this. */
  public Local getThisLocal() {
    return (Local) (((JIdentityStmt) getThisStmt()).getLeftOp());
  }

  /** Return LHS of the first identity stmt assigning from \@parameter i. */
  public Local getParameterLocal(int i) {
    for (Stmt s : getStmts()) {
      if (s instanceof JIdentityStmt && ((JIdentityStmt) s).getRightOp() instanceof JParameterRef) {
        JIdentityStmt is = (JIdentityStmt) s;
        JParameterRef pr = (JParameterRef) is.getRightOp();
        if (pr.getIndex() == i) {
          return (Local) is.getLeftOp();
        }
      }
    }

    throw new RuntimeException("couldn't find JParameterRef" + i + "! in " + getMethodSignature());
  }

  /**
   * Get all the LHS of the identity statements assigning from parameter references.
   *
   * @return a list of size as per <code>getMethod().getParameterCount()</code> with all elements
   *     ordered as per the parameter index.
   * @throws RuntimeException if a JParameterRef is missing
   */
  @Nonnull
  public Collection<Local> getParameterLocals() {
    final List<Local> retVal = new ArrayList<>();
    // TODO: [ms] performance: don't iterate over all stmt -> lazy vs freedom/error tolerance -> use
    // fixed index positions at the beginning?
    for (Stmt u : cfg.nodes()) {
      if (u instanceof JIdentityStmt) {
        JIdentityStmt is = (JIdentityStmt) u;
        if (is.getRightOp() instanceof JParameterRef) {
          JParameterRef pr = (JParameterRef) is.getRightOp();
          retVal.add(pr.getIndex(), (Local) is.getLeftOp());
        }
      }
    }
    return Collections.unmodifiableCollection(retVal);
  }

  /**
   * Returns the result of iterating through all Stmts in this body. All Stmts thus found are
   * returned. Branching Stmts and statements which use PhiExpr will have Stmts; a Stmt contains a
   * Stmt that is either a target of a branch or is being used as a pointer to the end of a CFG
   * block.
   *
   * <p>This method was typically used for pointer patching, e.g. when the unit chain is cloned.
   *
   * @return A collection of all the Stmts
   */
  @Nonnull
  public Collection<Stmt> getTargetStmtsInBody() {
    List<Stmt> stmtList = new ArrayList<>();
    Iterator<Stmt> iterator = cfg.nodes().iterator();
    while (iterator.hasNext()) {
      Stmt stmt = iterator.next();

      if (stmt instanceof BranchingStmt) {
        if (stmt instanceof JIfStmt) {
          stmtList.add(((JIfStmt) stmt).getTarget(this));
        } else if (stmt instanceof JGotoStmt) {
          stmtList.add(((JGotoStmt) stmt).getTarget(this));
        } else if (stmt instanceof JSwitchStmt) {
          getBranchTargetsOf((BranchingStmt) stmt).forEach(stmtList::add);
        }
      }
    }

    for (Trap item : traps) {
      stmtList.addAll(item.getStmts());
    }
    return Collections.unmodifiableCollection(stmtList);
  }

  /**
   * Returns the statements that make up this body. [ms] just use for tests!
   *
   * @return the statements in this Body
   */
  @Nonnull
  @Deprecated
  public List<Stmt> getStmts() {
    return new ArrayList<>(getStmtGraph().nodes());
  }

  public ImmutableStmtGraph getStmtGraph() {
    return cfg;
  }

  private void checkInit() {
    runValidation(new CheckInitValidator());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    StringWriter writer = new StringWriter();
    try (PrintWriter writerOut = new PrintWriter(new EscapedWriter(writer))) {
      new Printer().printTo(this, writerOut);
    }
    return writer.toString();
  }

  @Nonnull
  public Position getPosition() {
    return position;
  }

  /** returns a List of Branch targets of Branching Stmts */
  @Nonnull
  public List<Stmt> getBranchTargetsOf(@Nonnull BranchingStmt fromStmt) {
    return cfg.successors(fromStmt);
  }

  public boolean isStmtBranchTarget(@Nonnull Stmt targetStmt) {
    final List<Stmt> predecessors = cfg.predecessors(targetStmt);
    if (predecessors.size() > 1) {
      return true;
    }

    final Iterator<Stmt> iterator = predecessors.iterator();
    if (iterator.hasNext()) {
      Stmt pred = iterator.next();
      if (pred instanceof JIfStmt && ((JIfStmt) pred).getTarget(this) == targetStmt) {
        return true;
      }

      if (pred instanceof JGotoStmt) {
        return true;
      }

      if (pred instanceof JSwitchStmt) {
        return true;
      }
    }

    return false;
  }

  public void validateIdentityStatements() {
    runValidation(new IdentityStatementsValidator());
  }

  /** Returns the first non-identity stmt in this body. */
  @Nonnull
  public Stmt getFirstNonIdentityStmt() {
    Iterator<Stmt> it = getStmts().iterator();
    Stmt o = null;
    while (it.hasNext()) {
      if (!((o = it.next()) instanceof JIdentityStmt)) {
        break;
      }
    }
    if (o == null) {
      throw new RuntimeException("no non-id statements!");
    }
    return o;
  }

  /**
   * Returns the results of iterating through all Stmts in this Body and querying them for Values
   * defined. All of the Values found are then returned as a List.
   *
   * @return a List of all the Values for Values defined by this Body's Stmts.
   */
  public Collection<Value> getUses() {
    ArrayList<Value> useList = new ArrayList<>();

    for (Stmt stmt : cfg.nodes()) {
      useList.addAll(stmt.getUses());
    }
    return useList;
  }

  /**
   * Returns the results of iterating through all Stmts in this Body and querying them for Values
   * defined. All of the Values found are then returned as a List.
   *
   * @return a List of all the Values for Values defined by this Body's Stmts.
   */
  public Collection<Value> getDefs() {
    ArrayList<Value> defList = new ArrayList<>();

    for (Stmt stmt : cfg.nodes()) {
      defList.addAll(stmt.getDefs());
    }
    return defList;
  }

  @Nonnull
  public Body withLocals(@Nonnull Set<Local> locals) {
    return new Body(getMethodSignature(), locals, getTraps(), getStmtGraph(), getPosition());
  }

  @Nonnull
  public Body withTraps(@Nonnull List<Trap> traps) {
    return new Body(getMethodSignature(), getLocals(), traps, getStmtGraph(), getPosition());
  }

  @Nonnull
  public Body withStmts(@Nonnull StmtGraph stmtGraph) {
    return new Body(getMethodSignature(), getLocals(), getTraps(), stmtGraph, getPosition());
  }

  @Nonnull
  public Body withPosition(@Nonnull Position position) {
    return new Body(getMethodSignature(), getLocals(), getTraps(), getStmtGraph(), position);
  }

  public static BodyBuilder builder() {
    return new BodyBuilder();
  }

  public static BodyBuilder builder(Body body) {
    return new BodyBuilder(body);
  }

  public static class BodyBuilder {
    @Nonnull private Set<Local> locals = new HashSet<>();
    @Nonnull private final LocalGenerator localGen = new LocalGenerator(locals);

    @Nonnull private List<Trap> traps = new ArrayList<>();
    @Nonnull private Position position;

    @Nullable private MutableStmtGraph cfg;

    @Nullable private Stmt lastAddedStmt = null;
    @Nullable private MethodSignature methodSig = null;

    BodyBuilder() {
      cfg = new MutableStmtGraph();
    }

    BodyBuilder(@Nonnull Body body) {
      this(body, MutableStmtGraph.copyOf(body.getStmtGraph()));
    }

    BodyBuilder(@Nonnull Body body, @Nonnull MutableStmtGraph graphContainer) {
      setMethodSignature(body.getMethodSignature());
      setLocals(body.getLocals());
      setTraps(body.getTraps());
      setPosition(body.getPosition());
      cfg = graphContainer;
    }

    @Nonnull
    public BodyBuilder setFirstStmt(@Nullable Stmt firstStmt) {
      this.cfg.setEntryPoint(firstStmt);
      return this;
    }

    @Nonnull
    public BodyBuilder setLocals(@Nonnull Set<Local> locals) {
      this.locals = locals;
      return this;
    }

    @Nonnull
    public BodyBuilder addLocal(@Nonnull String name, Type type) {
      locals.add(localGen.generateLocal(type));
      return this;
    }

    @Nonnull
    public BodyBuilder addLocal(@Nonnull Local local) {
      locals.add(local);
      return this;
    }

    @Nonnull
    public BodyBuilder setTraps(@Nonnull List<Trap> traps) {
      this.traps = traps;
      return this;
    }

    @Nonnull
    public BodyBuilder addStmt(@Nonnull Stmt stmt) {
      return addStmt(stmt, false);
    }

    @Nonnull
    public BodyBuilder addStmt(@Nonnull Stmt stmt, boolean linkLastStmt) {
      cfg.addNode(stmt);
      if (lastAddedStmt != null) {
        if (linkLastStmt && lastAddedStmt.fallsThrough()) {
          addFlow(lastAddedStmt, stmt);
        }
      } else {
        // automatically set first statement
        cfg.setEntryPoint(stmt);
      }
      lastAddedStmt = stmt;
      return this;
    }

    @Nonnull
    public BodyBuilder removeStmt(@Nonnull Stmt stmt) {
      cfg.removeNode(stmt);
      return this;
    }

    @Nonnull
    public BodyBuilder addFlow(@Nonnull Stmt fromStmt, @Nonnull Stmt toStmt) {
      cfg.putEdge(fromStmt, toStmt);
      return this;
    }

    @Nonnull
    public BodyBuilder removeFlow(@Nonnull Stmt fromStmt, @Nonnull Stmt toStmt) {
      cfg.removeEdge(fromStmt, toStmt);
      return this;
    }

    @Nonnull
    public BodyBuilder setPosition(@Nonnull Position position) {
      this.position = position;
      return this;
    }

    public BodyBuilder setMethodSignature(MethodSignature methodSig) {
      this.methodSig = methodSig;
      return this;
    }

    @Nonnull
    public Body build() {

      StringBuilder debug = new StringBuilder(methodSig + "\n");
      for (Stmt stmt : cfg.nodes()) {
        debug.append(stmt).append(" => ").append(cfg.successors(stmt)).append(" \n");
      }

      // validate statements
      for (Stmt stmt : cfg.nodes()) {

        final List<Stmt> successors = cfg.successors(stmt);
        final int successorCount = successors.size();
        if (stmt instanceof BranchingStmt) {

          for (Stmt target : successors) {
            if (target == stmt) {
              System.out.println(debug);
              throw new IllegalArgumentException(stmt + ": a Stmt cannot branch to itself.");
            }
          }

          if (stmt instanceof JSwitchStmt) {
            if (successorCount != ((JSwitchStmt) stmt).getValueCount()) {
              System.out.println(debug);
              throw new IllegalArgumentException(
                  stmt
                      + ": size of outgoing flows (i.e. "
                      + successorCount
                      + ") does not match the amount of switch statements case labels (i.e. "
                      + ((JSwitchStmt) stmt).getValueCount()
                      + ").");
            }
          } else if (stmt instanceof JIfStmt) {
            if (successorCount != 2) {
              System.out.println(debug);
              throw new IllegalStateException(
                  stmt + ": must have '2' outgoing flow but has '" + successorCount + "'.");
            } else {

              // TODO: [ms] please fix order of targets of ifstmts in frontends i.e. Asmmethodsource
              final List<Stmt> edges = new ArrayList<>(cfg.successors(stmt));
              Stmt currentNextNode = edges.get(0);
              final Iterator<Stmt> iterator = cfg.nodes().iterator();
              //noinspection StatementWithEmptyBody
              while (iterator.hasNext() && iterator.next() != stmt) {}

              // switch edge order if the order is wrong i.e. the first edge is not the following
              // stmt in the node list
              if (iterator.hasNext() && iterator.next() != currentNextNode) {
                edges.set(0, edges.get(1));
                edges.set(1, currentNextNode);
              }
            }
          } else if (stmt instanceof JGotoStmt) {
            if (successorCount != 1) {
              System.out.println(debug);
              throw new IllegalArgumentException(
                  stmt + ": Goto must have '1' outgoing flow but has '" + successorCount + "'.");
            }
          }

        } else if (stmt instanceof JReturnStmt
            || stmt instanceof JReturnVoidStmt
            || stmt instanceof JThrowStmt) {
          if (successorCount != 0) {
            System.out.println(debug);
            throw new IllegalArgumentException(
                stmt + ": must have '0' outgoing flow but has '" + successorCount + "'.");
          }
        } else {
          if (successorCount != 1) {
            System.out.println(debug);
            throw new IllegalArgumentException(
                stmt + ": must have '1' outgoing flow but has '" + successorCount + "'.");
          }
        }
      }

      if (methodSig == null) {
        throw new IllegalArgumentException("There is no MethodSignature set.");
      }

      return new Body(methodSig, locals, traps, cfg, position);
    }
  }
}
