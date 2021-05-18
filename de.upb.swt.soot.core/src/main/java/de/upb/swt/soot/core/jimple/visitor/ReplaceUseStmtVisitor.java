package de.upb.swt.soot.core.jimple.visitor;

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

import de.upb.swt.soot.core.jimple.basic.Immediate;
import de.upb.swt.soot.core.jimple.basic.Value;
import de.upb.swt.soot.core.jimple.common.expr.AbstractConditionExpr;
import de.upb.swt.soot.core.jimple.common.expr.Expr;
import de.upb.swt.soot.core.jimple.common.ref.Ref;
import de.upb.swt.soot.core.jimple.common.stmt.*;
import de.upb.swt.soot.core.jimple.javabytecode.stmt.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Replace old use(Value) of a Stmt with a new use(Value)
 *
 * @author Zun Wang
 */
public class ReplaceUseStmtVisitor extends AbstractStmtVisitor {

  @Nonnull private final Value oldUse;
  @Nonnull private final Value newUse;
  @Nullable private Stmt newStmt = null;

  public ReplaceUseStmtVisitor(@Nonnull Value oldUse, @Nonnull Value newUse) {
    this.oldUse = oldUse;
    this.newUse = newUse;
  }

  @Nonnull
  @Override
  public void caseBreakpointStmt(@Nonnull JBreakpointStmt stmt) {
    defaultCase(stmt);
  }

  @Nonnull
  @Override
  public void caseInvokeStmt(@Nonnull JInvokeStmt stmt) {
    Expr invokeExpr = stmt.getInvokeExpr();
    ReplaceUseExprVisitor exprVisitor = new ReplaceUseExprVisitor(oldUse, newUse);
    invokeExpr.accept(exprVisitor);
    if (!exprVisitor.getNewExpr().equivTo(invokeExpr)) {
      newStmt = stmt.withInvokeExpr(exprVisitor.getNewExpr());
    } else {
      defaultCase(stmt);
    }
  }

  @Nonnull
  @Override
  public void caseAssignStmt(@Nonnull JAssignStmt stmt) {
    Value rValue = stmt.getRightOp();
    Value newRValue = null;

    if (rValue instanceof Immediate) {
      if ((newUse instanceof Immediate) && rValue.equivTo(oldUse)) newRValue = newUse;

    } else if (rValue instanceof Ref) {

      ReplaceUseRefVisitor refVisitor = new ReplaceUseRefVisitor(oldUse, newUse);
      ((Ref) rValue).accept(refVisitor);
      if (!refVisitor.getNewRef().equivTo(rValue)) {
        newRValue = refVisitor.getNewRef();
      }

    } else if (rValue instanceof Expr) {

      ReplaceUseExprVisitor exprVisitor = new ReplaceUseExprVisitor(oldUse, newUse);
      ((Expr) rValue).accept(exprVisitor);
      if (!exprVisitor.getNewExpr().equivTo(rValue)) {
        newRValue = exprVisitor.getNewExpr();
      }
    }
    if (newRValue != null) {
      newStmt = stmt.withRValue(newRValue);
    } else {
      defaultCase(stmt);
    }
  }

  @Nonnull
  @Override
  public void caseIdentityStmt(@Nonnull JIdentityStmt stmt) {
    defaultCase(stmt);
  }

  @Nonnull
  @Override
  public void caseEnterMonitorStmt(@Nonnull JEnterMonitorStmt stmt) {
    if (newUse instanceof Immediate && stmt.getOp().equivTo(oldUse)) {
      newStmt = stmt.withOp(newUse);
    } else {
      defaultCase(stmt);
    }
  }

  @Nonnull
  @Override
  public void caseExitMonitorStmt(@Nonnull JExitMonitorStmt stmt) {
    if (newUse instanceof Immediate && stmt.getOp().equivTo(oldUse)) {
      newStmt = stmt.withOp(newUse);
    } else {
      defaultCase(stmt);
    }
  }

  @Nonnull
  @Override
  public void caseGotoStmt(@Nonnull JGotoStmt stmt) {
    defaultCase(stmt);
  }

  @Nonnull
  @Override
  public void caseIfStmt(@Nonnull JIfStmt stmt) {
    Expr condition = stmt.getCondition();
    ReplaceUseExprVisitor exprVisitor = new ReplaceUseExprVisitor(oldUse, newUse);
    condition.accept(exprVisitor);
    if (!exprVisitor.getNewExpr().equivTo(condition)) {
      newStmt = stmt.withCondition((AbstractConditionExpr) exprVisitor.getNewExpr());
    } else {
      defaultCase(stmt);
    }
  }

  @Nonnull
  @Override
  public void caseNopStmt(@Nonnull JNopStmt stmt) {
    defaultCase(stmt);
  }

  @Nonnull
  @Override
  public void caseRetStmt(@Nonnull JRetStmt stmt) {
    if (newUse instanceof Immediate && stmt.getStmtAddress().equivTo(oldUse)) {
      newStmt = stmt.withStmtAddress((Immediate) newUse);
    } else {
      defaultCase(stmt);
    }
  }

  @Nonnull
  @Override
  public void caseReturnStmt(@Nonnull JReturnStmt stmt) {
    if (newUse instanceof Immediate && stmt.getOp().equivTo(oldUse)) {
      newStmt = stmt.withReturnValue((Immediate) newUse);
    } else {
      defaultCase(stmt);
    }
  }

  @Nonnull
  @Override
  public void caseReturnVoidStmt(@Nonnull JReturnVoidStmt stmt) {
    defaultCase(stmt);
  }

  @Nonnull
  @Override
  public void caseSwitchStmt(@Nonnull JSwitchStmt stmt) {
    if (newUse instanceof Immediate && stmt.getKey().equivTo(oldUse)) {
      newStmt = stmt.withKey((Immediate) newUse);
    } else {
      defaultCase(stmt);
    }
  }

  @Nonnull
  @Override
  public void caseThrowStmt(@Nonnull JThrowStmt stmt) {
    if (newUse instanceof Immediate && stmt.getOp().equivTo(oldUse)) {
      newStmt = stmt.withOp(newUse);
    } else {
      defaultCase(stmt);
    }
  }

  @Nonnull
  @Override
  public void defaultCase(@Nonnull Object obj) {
    newStmt = (Stmt) obj;
  }

  @Nullable
  public Stmt getNewStmt() {
    return newStmt;
  }
}
