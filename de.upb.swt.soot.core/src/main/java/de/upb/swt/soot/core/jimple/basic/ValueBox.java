package de.upb.swt.soot.core.jimple.basic;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 1997-2020 Raja Vallee-Rai, Markus Schmidt
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

import de.upb.swt.soot.core.util.printer.StmtPrinter;
import javax.annotation.Nonnull;

/**
 * A box which can contain values.
 *
 * @see Value
 */
public abstract class ValueBox {

  /** Returns true if the given Value fits in this box. */
  public abstract boolean canContainValue(Value value);

  private Value value;

  public ValueBox(Value value) {
    setValue(value);
  }

  /** Violates immutability. Only use this for legacy code. */
  @Deprecated
  private void setValue(Value value) {
    if (value == null) {
      throw new IllegalArgumentException("value may not be null");
    }
    if (canContainValue(value)) {
      this.value = value;
    } else {
      throw new RuntimeException(
          "Box " + this + " cannot contain value: " + value + " (" + value.getClass() + ")");
    }
  }

  /** Returns the value contained in this box. */
  public Value getValue() {
    return value;
  }

  public void toString(@Nonnull StmtPrinter up) {
    // up.startValueBox(this);
    value.toString(up);
    // up.endValueBox(this);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + value + ")";
  }

  /** This class is for internal use only. It will be removed in the future. */
  @Deprecated
  public static class $Accessor {
    // This class deliberately starts with a $-sign to discourage usage
    // of this Soot implementation detail.

    /** Violates immutability. Only use this for legacy code. */
    @Deprecated
    public static void setValue(ValueBox box, Value value) {
      box.setValue(value);
    }

    private $Accessor() {}
  }
}
