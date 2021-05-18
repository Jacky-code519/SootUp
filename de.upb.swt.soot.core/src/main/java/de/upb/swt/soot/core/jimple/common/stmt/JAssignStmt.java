package de.upb.swt.soot.core.jimple.common.stmt;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 1997-2020 Etienne Gagnon, Linghui Luo, Markus Schmidt and others
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

import de.upb.swt.soot.core.jimple.basic.*;
import de.upb.swt.soot.core.jimple.common.expr.AbstractInvokeExpr;
import de.upb.swt.soot.core.jimple.common.ref.JArrayRef;
import de.upb.swt.soot.core.jimple.common.ref.JFieldRef;
import de.upb.swt.soot.core.jimple.visitor.StmtVisitor;
import de.upb.swt.soot.core.util.Copyable;
import de.upb.swt.soot.core.util.printer.StmtPrinter;
import javax.annotation.Nonnull;

/** Represents the assignment of one value to another */
public final class JAssignStmt extends AbstractDefinitionStmt implements Copyable {

  /** The Class LinkedVariableBox. */
  private static class LinkedVariableBox extends VariableBox {
    /** The other box. */
    ValueBox otherBox = null;

    /**
     * Instantiates a new linked variable box.
     *
     * @param v the v
     */
    private LinkedVariableBox(Value v) {
      super(v);
    }

    /**
     * Sets the other box.
     *
     * @param otherBox the new other box
     */
    public void setOtherBox(ValueBox otherBox) {
      this.otherBox = otherBox;
    }

    /*
     * (non-Javadoc)
     *
     * @see de.upb.soot.jimple.VariableBox#canContainValue(de.upb.soot.jimple.Value)
     */
    @Override
    public boolean canContainValue(Value v) {
      if (super.canContainValue(v)) {
        if (otherBox == null) {
          return true;
        }

        Value o = otherBox.getValue();
        return (v instanceof Immediate) || (o instanceof Immediate);
      }
      return false;
    }
  }

  /** The Class LinkedRValueBox. */
  private static class LinkedRValueBox extends RValueBox {

    /** The other box. */
    ValueBox otherBox = null;

    /**
     * Instantiates a new linked R value box.
     *
     * @param v the v
     */
    private LinkedRValueBox(Value v) {
      super(v);
    }

    /**
     * Sets the other box.
     *
     * @param otherBox the new other box
     */
    public void setOtherBox(ValueBox otherBox) {
      this.otherBox = otherBox;
    }

    /*
     * (non-Javadoc)
     *
     * @see de.upb.soot.jimple.RValueBox#canContainValue(de.upb.soot.jimple.Value)
     */
    @Override
    public boolean canContainValue(Value v) {
      if (super.canContainValue(v)) {
        if (otherBox == null) {
          return true;
        }

        Value o = otherBox.getValue();
        return (v instanceof Immediate) || (o instanceof Immediate);
      }
      return false;
    }
  }

  /**
   * Instantiates a new JAssignStmt.
   *
   * @param variable the variable on the left side of the assign statement.
   * @param rValue the value on the right side of the assign statement.
   */
  public JAssignStmt(Value variable, Value rValue, StmtPositionInfo positionInfo) {
    this(new LinkedVariableBox(variable), new LinkedRValueBox(rValue), positionInfo);

    ((LinkedVariableBox) getLeftBox()).setOtherBox(getRightBox());
    ((LinkedRValueBox) getRightBox()).setOtherBox(getLeftBox());

    if (!getLeftBox().canContainValue(variable) || !getRightBox().canContainValue(rValue)) {
      throw new RuntimeException(
          "Illegal assignment statement.  Make sure that either left side or right hand side has a local or constant.");
    }
  }

  /**
   * Instantiates a new JAssignStmt.
   *
   * @param variableBox the variable box on the left side of the assign statement.
   * @param rvalueBox the rvalue box on the right side of the assign statement.
   */
  protected JAssignStmt(ValueBox variableBox, ValueBox rvalueBox, StmtPositionInfo positionInfo) {
    super(variableBox, rvalueBox, positionInfo);
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.AbstractStmt#containsInvokeExpr()
   */
  @Override
  public boolean containsInvokeExpr() {
    return getRightOp() instanceof AbstractInvokeExpr;
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.AbstractStmt#getInvokeExpr()
   */
  @Override
  public AbstractInvokeExpr getInvokeExpr() {
    if (!containsInvokeExpr()) {
      throw new RuntimeException("getInvokeExpr() called with no invokeExpr present!");
    }

    return (AbstractInvokeExpr) getRightBox().getValue();
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.AbstractStmt#getInvokeExprBox()
   */
  @Override
  public ValueBox getInvokeExprBox() {
    if (!containsInvokeExpr()) {
      throw new RuntimeException("getInvokeExpr() called with no invokeExpr present!");
    }

    return getRightBox();
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.AbstractStmt#containsArrayRef()
   */
  /* added by Feng */
  @Override
  public boolean containsArrayRef() {
    return ((getLeftOp() instanceof JArrayRef) || (getRightOp() instanceof JArrayRef));
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.AbstractStmt#getArrayRef()
   */
  @Override
  public JArrayRef getArrayRef() {
    if (getLeftOp() instanceof JArrayRef) {
      return (JArrayRef) getLeftBox().getValue();
    } else if (getRightOp() instanceof JArrayRef) {
      return (JArrayRef) getRightBox().getValue();
    } else {
      throw new RuntimeException("getArrayRef() called with no ArrayRef present!");
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.AbstractStmt#getArrayRefBox()
   */
  @Override
  public ValueBox getArrayRefBox() {
    if (!containsArrayRef()) {
      throw new RuntimeException("getArrayRefBox() called with no ArrayRef present!");
    }

    if (getLeftBox().getValue() instanceof JArrayRef) {
      return getLeftBox();
    } else {
      return getRightBox();
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.AbstractStmt#containsFieldRef()
   */
  @Override
  public boolean containsFieldRef() {
    return ((getLeftOp() instanceof JFieldRef) || (getRightOp() instanceof JFieldRef));
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.AbstractStmt#getFieldRef()
   */
  @Override
  public JFieldRef getFieldRef() {
    if (!containsFieldRef()) {
      throw new RuntimeException("getFieldRef() called with no JFieldRef present!");
    }

    if (getLeftBox().getValue() instanceof JFieldRef) {
      return (JFieldRef) getLeftBox().getValue();
    } else {
      return (JFieldRef) getRightBox().getValue();
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.AbstractStmt#getFieldRefBox()
   */
  @Override
  public ValueBox getFieldRefBox() {
    if (!containsFieldRef()) {
      throw new RuntimeException("getFieldRefBox() called with no JFieldRef present!");
    }

    if (getLeftBox().getValue() instanceof JFieldRef) {
      return getLeftBox();
    } else {
      return getRightBox();
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return getLeftBox().getValue().toString() + " = " + getRightBox().getValue().toString();
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.Stmt#toString(de.upb.soot.StmtPrinter)
   */
  @Override
  public void toString(@Nonnull StmtPrinter up) {
    getLeftBox().toString(up);
    up.literal(" = ");
    getRightBox().toString(up);
  }

  /*
   * (non-Javadoc)
   *
   * @see de.upb.soot.jimple.common.stmt.AbstractStmt#accept(de.upb.soot.jimple.visitor.Visitor)
   */
  @Override
  public void accept(@Nonnull StmtVisitor sw) {
    sw.caseAssignStmt(this);
  }

  @Override
  public boolean equivTo(Object o, @Nonnull JimpleComparator comparator) {
    return comparator.caseAssignStmt(this, o);
  }

  @Override
  public int equivHashCode() {
    return getLeftBox().getValue().equivHashCode() + 31 * getRightBox().getValue().equivHashCode();
  }

  @Nonnull
  public JAssignStmt withVariable(Value variable) {
    return new JAssignStmt(variable, getRightOp(), getPositionInfo());
  }

  @Nonnull
  public JAssignStmt withRValue(Value rValue) {
    return new JAssignStmt(getLeftOp(), rValue, getPositionInfo());
  }

  @Nonnull
  public JAssignStmt withPositionInfo(StmtPositionInfo positionInfo) {
    return new JAssignStmt(getLeftOp(), getRightOp(), positionInfo);
  }
}
