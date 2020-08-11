package de.upb.swt.soot.core.util.printer;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2020 Markus Schmidt
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

import de.upb.swt.soot.core.jimple.common.stmt.Stmt;
import de.upb.swt.soot.core.jimple.javabytecode.stmt.JSwitchStmt;
import de.upb.swt.soot.core.model.Body;

/**
 * StmtPrinter implementation for normal (full) Jimple for OldSoot
 *
 * <p>List of differences between old and current Jimple: - tableswitch and lookupswitch got merged
 * into switch - now imports are possible - disabled
 *
 * @author Markus Schmidt
 */
public class LegacyJimplePrinter extends NormalStmtPrinter {

  public LegacyJimplePrinter(Body b) {
    super(b);
  }

  @Override
  void enableImports(boolean enable) {
    if (enable) {
      throw new RuntimeException(
          "Imports are not supported in Legacy Jimple: don't enable UseImports");
    }
  }

  @Override
  public void stmt(Stmt currentStmt) {
    startStmt(currentStmt);
    // replace switch with lookupswitch
    if (currentStmt instanceof JSwitchStmt) {
      // prepend to switch Stmt
      literal(((JSwitchStmt) currentStmt).isTableSwitch() ? "table" : "lookup");
    }
    currentStmt.toString(this);
    endStmt(currentStmt);
    literal(";");
    newline();
  }
}
