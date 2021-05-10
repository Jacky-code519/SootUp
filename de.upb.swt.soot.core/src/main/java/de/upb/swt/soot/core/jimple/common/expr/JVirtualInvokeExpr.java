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

/** An expression that invokes a virtual method. */
public final class JVirtualInvokeExpr extends AbstractInstanceInvokeExpr implements Copyable {

  /** Stores the values of new ImmediateBox to the argBoxes array. */
  public JVirtualInvokeExpr(Value base, MethodSignature method, List<? extends Value> args) {
    super(Jimple.newLocalBox(base), method, ValueBoxUtils.toValueBoxes(args));
  }

  @Override
  public boolean equivTo(Object o, @Nonnull JimpleComparator comparator) {
    return comparator.caseVirtualInvokeExpr(this, o);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder
        .append(Jimple.VIRTUALINVOKE + " ")
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
    up.literal(Jimple.VIRTUALINVOKE);
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
    ((ExprVisitor) sw).caseVirtualInvokeExpr(this);
  }

  @Nonnull
  public JVirtualInvokeExpr withBase(Value base) {
    return new JVirtualInvokeExpr(base, getMethodSignature(), getArgs());
  }

  @Nonnull
  public JVirtualInvokeExpr withMethodSignature(MethodSignature methodSignature) {
    return new JVirtualInvokeExpr(getBase(), methodSignature, getArgs());
  }

  @Nonnull
  public JVirtualInvokeExpr withArgs(List<? extends Value> args) {
    return new JVirtualInvokeExpr(getBase(), getMethodSignature(), args);
  }
}
