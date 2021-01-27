package de.upb.swt.soot.core.jimple.common.expr;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 1999-2020 Patrick Lam, Christian Brüggemann, Markus Schmidt and others
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

/*
 * Modified by the Sable Research Group and others 1997-1999.
 * See the 'credits' file distributed with Soot for the complete list of
 * contributors.  (Soot is distributed at http://www.sable.mcgill.ca/soot)
 */

import de.upb.swt.soot.core.jimple.Jimple;
import de.upb.swt.soot.core.jimple.basic.JimpleComparator;
import de.upb.swt.soot.core.jimple.basic.Value;
import de.upb.swt.soot.core.jimple.visitor.ExprVisitor;
import de.upb.swt.soot.core.jimple.visitor.Visitor;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.util.Copyable;
import de.upb.swt.soot.core.util.printer.StmtPrinter;
import java.util.List;
import javax.annotation.Nonnull;

/** An expression that invokes an interface method. */
public final class JInterfaceInvokeExpr extends AbstractInstanceInvokeExpr implements Copyable {

  /**
   * Assigns bootstrapArgs to bsmArgBoxes, an array of type ValueBox. And methodArgs to an array
   * argBoxes.
   */
  public JInterfaceInvokeExpr(Value base, MethodSignature method, List<? extends Value> args) {
    super(Jimple.newLocalBox(base), method, ValueBoxUtils.toValueBoxes(args));

    // FIXME: [JMP] Move this into view or somewhere, where `SootClass` and its context are
    // available
    //    // Check that the method's class is resolved enough
    //    // CheckLevel returns without doing anything because we can be not 'done' resolving
    //    Optional<AbstractClass> declaringClass = view.getClass(method.declClassSignature);
    //    if (declaringClass.isPresent()) {
    //      SootClass cls = (SootClass) declaringClass.get();
    //      // now check if the class is valid
    //      if (!cls.isInterface() && !cls.isPhantomClass()) {
    //        throw new RuntimeException("Trying to create interface invoke expression for
    // non-interface type: " + cls
    //            + " Use JVirtualInvokeExpr or JSpecialInvokeExpr instead!");
    //      }
    //    }
  }

  @Override
  public boolean equivTo(Object o, JimpleComparator comparator) {
    return comparator.caseInterfaceInvokeExpr(this, o);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder
        .append(Jimple.INTERFACEINVOKE)
        .append(" ")
        .append(getBase().toString())
        .append(".")
        .append(getMethodSignature())
        .append("(");
    argBoxesToString(builder);
    builder.append(")");
    return builder.toString();
  }

  /** Converts a parameter of type StmtPrinter to a string literal. */
  @Override
  public void toString(@Nonnull StmtPrinter up) {
    up.literal(Jimple.INTERFACEINVOKE);
    up.literal(" ");
    getBaseBox().toString(up);
    up.literal(".");
    up.methodSignature(getMethodSignature());
    up.literal("(");
    argBoxesToPrinter(up);
    up.literal(")");
  }

  @Override
  public void accept(@Nonnull Visitor sw) {
    ((ExprVisitor) sw).caseInterfaceInvokeExpr(this);
  }

  // Value base, MethodSignature method, List<? extends Value> args

  @Nonnull
  public JInterfaceInvokeExpr withBase(Value base) {
    return new JInterfaceInvokeExpr(base, getMethodSignature(), getArgs());
  }

  @Nonnull
  public JInterfaceInvokeExpr Method(MethodSignature method) {
    return new JInterfaceInvokeExpr(getBase(), method, getArgs());
  }

  @Nonnull
  public JInterfaceInvokeExpr withArgs(List<? extends Value> args) {
    return new JInterfaceInvokeExpr(getBase(), getMethodSignature(), args);
  }
}
