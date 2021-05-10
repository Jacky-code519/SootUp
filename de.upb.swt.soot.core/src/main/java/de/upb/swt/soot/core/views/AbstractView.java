package de.upb.swt.soot.core.views;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2018-2020 Linghui Luo, Christian Brüggemann and others
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

import de.upb.swt.soot.core.IdentifierFactory;
import de.upb.swt.soot.core.Project;
import de.upb.swt.soot.core.Scope;
import de.upb.swt.soot.core.model.SootClass;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Abstract class for view.
 *
 * @author Linghui Luo
 */
public abstract class AbstractView<T extends SootClass<?>> implements View<T> {

  @Nonnull private final Project<? extends View<T>, T> project;

  @Nonnull private final Map<ModuleDataKey<?>, Object> moduleData = new HashMap<>();

  public AbstractView(@Nonnull Project<? extends View<?>, ?> project) {
    this.project = (Project<? extends View<T>, T>) project;
  }

  @Override
  @Nonnull
  public IdentifierFactory getIdentifierFactory() {
    return this.getProject().getIdentifierFactory();
  }

  @Override
  @Nonnull
  public Optional<Scope> getScope() {
    // TODO Auto-generated methodRef stub
    return null;
  }

  @Nonnull
  public Project<? extends View<T>, T> getProject() {
    return project;
  }

  @SuppressWarnings("unchecked") // Safe because we only put T in putModuleData
  @Override
  @Nullable
  public <K> K getModuleData(@Nonnull ModuleDataKey<K> key) {
    return (K) moduleData.get(key);
  }

  @Override
  public <K> void putModuleData(@Nonnull ModuleDataKey<K> key, @Nonnull K value) {
    moduleData.put(key, value);
  }
}
