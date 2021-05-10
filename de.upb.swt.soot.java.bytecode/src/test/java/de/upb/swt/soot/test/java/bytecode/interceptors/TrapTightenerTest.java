package de.upb.swt.soot.test.java.bytecode.interceptors;

import categories.Java8Test;
import de.upb.swt.soot.core.jimple.basic.*;
import de.upb.swt.soot.core.jimple.common.constant.IntConstant;
import de.upb.swt.soot.core.jimple.common.ref.IdentityRef;
import de.upb.swt.soot.core.jimple.common.stmt.Stmt;
import de.upb.swt.soot.core.model.Body;
import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.types.VoidType;
import de.upb.swt.soot.core.util.ImmutableUtils;
import de.upb.swt.soot.java.bytecode.interceptors.TrapTightener;
import de.upb.swt.soot.java.core.JavaIdentifierFactory;
import de.upb.swt.soot.java.core.language.JavaJimple;
import de.upb.swt.soot.java.core.types.JavaClassType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** @author Zun Wang */
@Category(Java8Test.class)
public class TrapTightenerTest {
  JavaIdentifierFactory factory = JavaIdentifierFactory.getInstance();
  StmtPositionInfo noStmtPositionInfo = StmtPositionInfo.createNoStmtPositionInfo();

  JavaClassType intType = factory.getClassType("int");
  JavaClassType classType = factory.getClassType("Test");
  MethodSignature methodSignature =
      new MethodSignature(classType, "test", Collections.emptyList(), VoidType.getInstance());
  IdentityRef identityRef = JavaJimple.newThisRef(classType);

  // build locals
  Local l0 = JavaJimple.newLocal("l0", intType);
  Local l1 = JavaJimple.newLocal("l1", intType);
  Local l2 = JavaJimple.newLocal("l2", intType);
  Local l3 = JavaJimple.newLocal("l3", intType);

  ClassType exception = factory.getClassType("java.lang.Throwable");
  JavaJimple javaJimple = JavaJimple.getInstance();
  IdentityRef caughtExceptionRef = javaJimple.newCaughtExceptionRef();
  Stmt startingStmt = JavaJimple.newIdentityStmt(l0, identityRef, noStmtPositionInfo);
  Stmt ret = JavaJimple.newReturnVoidStmt(noStmtPositionInfo);

  // stmts
  Stmt stmt1 = JavaJimple.newAssignStmt(l1, IntConstant.getInstance(1), noStmtPositionInfo);
  Stmt stmt2 = JavaJimple.newEnterMonitorStmt(l1, noStmtPositionInfo);
  Stmt stmt3 = JavaJimple.newAssignStmt(l2, l1, noStmtPositionInfo);
  Stmt stmt4 = JavaJimple.newExitMonitorStmt(l2, noStmtPositionInfo);
  Stmt stmt5 = JavaJimple.newGotoStmt(noStmtPositionInfo);

  Stmt stmt6 = JavaJimple.newIdentityStmt(l3, caughtExceptionRef, noStmtPositionInfo);
  Stmt stmt7 = JavaJimple.newAssignStmt(l2, IntConstant.getInstance(2), noStmtPositionInfo);
  Stmt stmt8 = JavaJimple.newExitMonitorStmt(l2, noStmtPositionInfo);
  Stmt stmt9 = JavaJimple.newThrowStmt(l3, noStmtPositionInfo);
  Stmt stmt10 = JavaJimple.newAssignStmt(l2, IntConstant.getInstance(3), noStmtPositionInfo);
  Stmt stmt11 = JavaJimple.newAssignStmt(l2, IntConstant.getInstance(4), noStmtPositionInfo);
  // trap
  JTrap trap1 = new JTrap(exception, stmt2, stmt5, stmt6);
  JTrap trap2 = new JTrap(exception, stmt1, stmt5, stmt6);
  JTrap trap3 = new JTrap(exception, stmt7, stmt10, stmt6);

  /**
   *
   *
   * <pre>
   *    l0 := @this Test;
   *  label1:
   *    l1 = 1;
   *    l2 = 2;
   *    l2 = 3;
   *  label2:
   *    goto label4;
   *  label3:
   *    l3 := @caughtexception;
   *    l2 = 4;
   *    throw l3;
   *  label4:
   *    return;
   *  catch Exception from label1 to label2 with label3;
   * </pre>
   *
   * after run trapTightener
   *
   * <pre>
   *    l0 := @this Test;
   *    l1 = 1;
   *  label1:
   *    l2 = 2;
   *  label2:
   *    l2 = 3;
   *    goto label4;
   *  label3:
   *    l3 := @caughtexception;
   *    l2 = 4;
   *    throw l3;
   *  label4:
   *    return;
   *  catch Exception from label1 to label2 with label3;
   * </pre>
   */
  @Test
  public void testSimpleBody() {

    Body body = creatSimpleBody();
    Body.BodyBuilder builder = Body.builder(body, Collections.emptySet());

    // modify exceptionalStmtGraph
    builder.removeDestinations(stmt1);
    builder.removeDestinations(stmt10);

    TrapTightener trapTightener = new TrapTightener();
    trapTightener.interceptBody(builder);

    List<Trap> excepted = new ArrayList<>();
    excepted.add(trap3);
    List<Trap> actual = builder.getTraps();
    AssertUtils.assertTrapsEquiv(excepted, actual);
  }
  /**
   *
   *
   * <pre>
   *    l0 := @this Test;
   *    l1 = 1;
   *  label1:
   *    entermonitor l1;
   *    l2 = l1;
   *    exitmonitor l2;
   *  label2:
   *    goto label4;
   *  label3:
   *    l3 := @caughtexception;
   *    l2 = 2;
   *    exitmonitor l2;
   *    throw l3;
   *  label4:
   *    return;
   *  catch Exception from label1 to label2 with label3;
   * </pre>
   */
  @Test
  public void testMinitoredBody() {

    Body body = creatBodyWithMonitor();
    Body.BodyBuilder builder = Body.builder(body, Collections.emptySet());

    // modify exceptionalStmtGraph
    builder.removeDestinations(stmt2);
    builder.removeDestinations(stmt4);

    TrapTightener trapTightener = new TrapTightener();
    trapTightener.interceptBody(builder);

    List<Trap> excepted = new ArrayList<>();
    excepted.add(trap1);
    List<Trap> actual = builder.getTraps();
    AssertUtils.assertTrapsEquiv(excepted, actual);
  }

  private Body creatSimpleBody() {
    Body.BodyBuilder builder = Body.builder();
    builder.setMethodSignature(methodSignature);

    // build set locals
    Set<Local> locals = ImmutableUtils.immutableSet(l0, l1, l2, l3);
    builder.setLocals(locals);

    // set graph
    builder.addFlow(startingStmt, stmt1);
    builder.addFlow(stmt1, stmt7);
    builder.addFlow(stmt7, stmt10);
    builder.addFlow(stmt10, stmt5);
    builder.addFlow(stmt6, stmt11);
    builder.addFlow(stmt11, stmt9);
    builder.addFlow(stmt5, ret);

    // build startingStmt
    builder.setStartingStmt(startingStmt);

    // build position
    Position position = NoPositionInformation.getInstance();
    builder.setPosition(position);

    // build trap
    List<Trap> traps = new ArrayList<>();
    traps.add(trap2);
    builder.setTraps(traps);

    return builder.build();
  }

  private Body creatBodyWithMonitor() {
    Body.BodyBuilder builder = Body.builder();
    builder.setMethodSignature(methodSignature);

    // build set locals
    Set<Local> locals = ImmutableUtils.immutableSet(l0, l1, l2, l3);
    builder.setLocals(locals);

    // set graph
    builder.addFlow(startingStmt, stmt1);
    builder.addFlow(stmt1, stmt2);
    builder.addFlow(stmt2, stmt3);
    builder.addFlow(stmt3, stmt4);
    builder.addFlow(stmt4, stmt5);
    builder.addFlow(stmt6, stmt7);
    builder.addFlow(stmt7, stmt8);
    builder.addFlow(stmt8, stmt9);
    builder.addFlow(stmt5, ret);

    // build startingStmt
    builder.setStartingStmt(startingStmt);

    // build position
    Position position = NoPositionInformation.getInstance();
    builder.setPosition(position);

    // build trap
    List<Trap> traps = new ArrayList<>();
    traps.add(trap1);
    builder.setTraps(traps);

    return builder.build();
  }
}
