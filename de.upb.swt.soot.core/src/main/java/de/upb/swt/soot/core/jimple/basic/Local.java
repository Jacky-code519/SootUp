package de.upb.swt.soot.core.jimple.basic;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 1999-2020 Patrick Lam, Linghui Luo, Markus Schmidt and others
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

import de.upb.swt.soot.core.jimple.visitor.Acceptor;
import de.upb.swt.soot.core.jimple.visitor.ValueVisitor;
import de.upb.swt.soot.core.model.Body;
import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.types.Type;
import de.upb.swt.soot.core.util.Copyable;
import de.upb.swt.soot.core.util.printer.StmtPrinter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Local variable in {@link Body}. Use {@link LocalGenerator} to generate locals.
 *
 * <p>Prefer to use the factory methods in {@link de.upb.swt.soot.core.jimple.Jimple}.
 *
 * @author Linghui Luo
 */
public class Local implements Immediate, Copyable, Acceptor<ValueVisitor> {

  @Nonnull private final String name;
  @Nonnull private final Type type;
  @Nonnull private final Position position; // position of the declaration

  /** Constructs a JimpleLocal of the given name and type. */
  public Local(@Nonnull String name, @Nonnull Type type) {
    this(name, type, NoPositionInformation.getInstance());
  }

  /** Constructs a JimpleLocal of the given name and type. */
  public Local(@Nonnull String name, @Nonnull Type type, @Nonnull Position position) {
    this.name = name;
    this.type = type;
    this.position = position;
  }

  // Can be safely suppressed, JimpleComparator performs this check
  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Local)) {
      return false;
    }
    return name.equals(((Local) o).getName());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }

  @Override
  public boolean equivTo(@Nonnull Object o, @Nonnull JimpleComparator comparator) {
    return comparator.caseLocal(this, o);
  }

  @Override
  public int equivHashCode() {
    return Objects.hash(name, type);
  }

  /** Returns the name of this object. */
  @Nonnull
  public String getName() {
    return name;
  }

  /** Returns the type of this local. */
  @Nonnull
  @Override
  public Type getType() {
    return type;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public void toString(@Nonnull StmtPrinter up) {
    up.local(this);
  }

  @Override
  public final List<Value> getUses() {
    return Collections.emptyList();
  }

  @Nonnull
  public Position getPosition() {
    return position;
  }

  @Override
  public void accept(@Nonnull ValueVisitor sw) {
    sw.caseLocal(this);
  }

  @Nonnull
  public Local withName(@Nonnull String name) {
    return new Local(name, type);
  }

  @Nonnull
  public Local withType(@Nonnull Type type) {
    return new Local(name, type);
  }
}
