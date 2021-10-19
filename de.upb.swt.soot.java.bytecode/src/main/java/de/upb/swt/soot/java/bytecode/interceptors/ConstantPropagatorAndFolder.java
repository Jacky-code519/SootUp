package de.upb.swt.soot.java.bytecode.interceptors;
/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 1997-2020 Raja Vallée-Rai, Christian Brüggemann
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
import de.upb.swt.soot.core.jimple.basic.Local;
import de.upb.swt.soot.core.jimple.basic.Value;
import de.upb.swt.soot.core.jimple.common.constant.*;
import de.upb.swt.soot.core.jimple.common.expr.*;
import de.upb.swt.soot.core.jimple.common.stmt.AbstractDefinitionStmt;
import de.upb.swt.soot.core.jimple.common.stmt.JAssignStmt;
import de.upb.swt.soot.core.jimple.common.stmt.JReturnStmt;
import de.upb.swt.soot.core.jimple.common.stmt.Stmt;
import de.upb.swt.soot.core.model.Body;
import de.upb.swt.soot.core.model.BodyUtils;
import de.upb.swt.soot.core.transform.BodyInterceptor;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Does constant propagation and folding. Constant folding is the compile-time evaluation of
 * constant expressions (i.e. 2 * 3).
 *
 * @author Marcus Nachtigall
 */
public class ConstantPropagatorAndFolder implements BodyInterceptor {

  @Override
  public void interceptBody(@Nonnull Body.BodyBuilder builder) {
    List<Stmt> defs = new ArrayList<>();
    List<Stmt> remainingStmts = builder.getStmts();

    builder.enableDeferredStmtGraphChanges();
    // Perform a constant/local propagation pass
    // go through each use in each statement
    for (Stmt stmt : remainingStmts) {
      // propagation pass
      if (stmt instanceof JAssignStmt) {
        Value rhs = ((AbstractDefinitionStmt) stmt).getRightOp();
        if (rhs instanceof AbstractBinopExpr) {
          Value op1 = ((AbstractBinopExpr) rhs).getOp1();
          Value op2 = ((AbstractBinopExpr) rhs).getOp2();

          if (op1 instanceof NumericConstant && op2 instanceof NumericConstant) {
            JAssignStmt assignStmt =
                new JAssignStmt(
                    ((AbstractDefinitionStmt) stmt).getLeftOp(), rhs, stmt.getPositionInfo());
            builder.replaceStmt(stmt, assignStmt);
            stmt = assignStmt;
            defs.add(assignStmt);
          }
        }
      } else if (stmt instanceof JReturnStmt) {
        for (Value value : stmt.getUses()) {
          if (value instanceof Local) {
            List<Stmt> defsOfUse = BodyUtils.getDefsOfLocal((Local) value, defs);
            if (defsOfUse.size() == 1) {
              AbstractDefinitionStmt definitionStmt = (AbstractDefinitionStmt) defsOfUse.get(0);
              Value rhs = definitionStmt.getRightOp();
              if (rhs instanceof NumericConstant
                  || rhs instanceof StringConstant
                  || rhs instanceof NullConstant) {
                JReturnStmt returnStmt = new JReturnStmt((Immediate) rhs, stmt.getPositionInfo());
                builder.replaceStmt(stmt, returnStmt);
                stmt = returnStmt;
                defs.add(returnStmt);
              }
            }
          }
        }
      }

      // folding pass
      for (Value value : stmt.getUses()) {
        if (!(value instanceof Constant)) {
          if (Evaluator.isValueConstantValue(value)) {
            value = Evaluator.getConstantValueOf(value);
            if (stmt instanceof JAssignStmt) {
              JAssignStmt assignStmt = ((JAssignStmt) stmt).withRValue(value);
              builder.replaceStmt(stmt, assignStmt);
              defs.remove(stmt);
              defs.add(assignStmt);
            } else if (stmt instanceof JReturnStmt && value != null) {
              JReturnStmt returnStmt = ((JReturnStmt) stmt).withReturnValue((Immediate) value);
              builder.replaceStmt(stmt, returnStmt);
            }
          }
        }
      }
    }
    builder.commitDeferredStmtGraphChanges();
  }
}
