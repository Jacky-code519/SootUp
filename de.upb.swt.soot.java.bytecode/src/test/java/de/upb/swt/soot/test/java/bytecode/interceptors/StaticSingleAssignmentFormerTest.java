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
import de.upb.swt.soot.java.bytecode.interceptors.StaticSingleAssignmentFormer;
import de.upb.swt.soot.java.core.JavaIdentifierFactory;
import de.upb.swt.soot.java.core.language.JavaJimple;
import de.upb.swt.soot.java.core.types.JavaClassType;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** @author Zun Wang */
@Category(Java8Test.class)
public class StaticSingleAssignmentFormerTest {

  // Preparation
  JavaIdentifierFactory factory = JavaIdentifierFactory.getInstance();
  StmtPositionInfo noStmtPositionInfo = StmtPositionInfo.createNoStmtPositionInfo();
  JavaJimple javaJimple = JavaJimple.getInstance();

  JavaClassType intType = factory.getClassType("int");
  JavaClassType classType = factory.getClassType("Test");
  JavaClassType refType = factory.getClassType("ref");
  MethodSignature methodSignature =
      new MethodSignature(classType, "test", Collections.emptyList(), VoidType.getInstance());
  IdentityRef identityRef = JavaJimple.newThisRef(classType);
  ClassType exception = factory.getClassType("Exception");
  IdentityRef caughtExceptionRef = javaJimple.newCaughtExceptionRef();

  // build locals
  Local l0 = JavaJimple.newLocal("l0", intType);
  Local l1 = JavaJimple.newLocal("l1", intType);
  Local l2 = JavaJimple.newLocal("l2", intType);
  Local l3 = JavaJimple.newLocal("l3", intType);
  Local stack4 = JavaJimple.newLocal("stack4", refType);

  Stmt startingStmt = JavaJimple.newIdentityStmt(l0, identityRef, noStmtPositionInfo);
  Stmt stmt1 = JavaJimple.newAssignStmt(l1, IntConstant.getInstance(1), noStmtPositionInfo);
  Stmt stmt2 = JavaJimple.newAssignStmt(l2, IntConstant.getInstance(1), noStmtPositionInfo);
  Stmt stmt3 = JavaJimple.newAssignStmt(l3, IntConstant.getInstance(0), noStmtPositionInfo);
  Stmt stmt4 =
      JavaJimple.newIfStmt(
          JavaJimple.newLtExpr(l3, IntConstant.getInstance(100)), noStmtPositionInfo);
  Stmt stmt5 =
      JavaJimple.newIfStmt(
          JavaJimple.newLtExpr(l2, IntConstant.getInstance(20)), noStmtPositionInfo);
  Stmt stmt6 = JavaJimple.newReturnStmt(l2, noStmtPositionInfo);
  Stmt stmt7 = JavaJimple.newAssignStmt(l2, l1, noStmtPositionInfo);
  Stmt stmt8 =
      JavaJimple.newAssignStmt(
          l3, JavaJimple.newAddExpr(l3, IntConstant.getInstance(1)), noStmtPositionInfo);
  Stmt stmt9 = JavaJimple.newAssignStmt(l2, l3, noStmtPositionInfo);
  Stmt stmt10 =
      JavaJimple.newAssignStmt(
          l3, JavaJimple.newAddExpr(l3, IntConstant.getInstance(2)), noStmtPositionInfo);
  Stmt stmt11 = JavaJimple.newGotoStmt(noStmtPositionInfo);

  Stmt handlerStmt = JavaJimple.newIdentityStmt(stack4, caughtExceptionRef, noStmtPositionInfo);
  Stmt l2eq0 = JavaJimple.newAssignStmt(l2, IntConstant.getInstance(0), noStmtPositionInfo);
  Stmt goTo = JavaJimple.newGotoStmt(noStmtPositionInfo);

  Trap trap = new Trap(exception, stmt7, stmt8, handlerStmt);

  @Test
  public void testSSA() {
    StaticSingleAssignmentFormer ssa = new StaticSingleAssignmentFormer();
    Body.BodyBuilder builder = new Body.BodyBuilder(createBody(), Collections.emptySet());
    ssa.interceptBody(builder);

    String expectedBodyString =
        "{\n"
            + "    int l0, l1, l2, l3, l0#0, l1#1, l2#2, l3#3, l3#4, l2#5, l2#6, l3#7, l2#8, l3#9, l3#10, l2#11;\n"
            + "\n"
            + "\n"
            + "    l0#0 := @this: Test;\n"
            + "\n"
            + "    l1#1 = 1;\n"
            + "\n"
            + "    l2#2 = 1;\n"
            + "\n"
            + "    l3#3 = 0;\n"
            + "\n"
            + "  label1:\n"
            + "    l3#4 = phi(l3#3, l3#10);\n"
            + "\n"
            + "    l2#5 = phi(l2#2, l2#11);\n"
            + "\n"
            + "    if l3#4 < 100 goto label3;\n"
            + "\n"
            + "    if l2#5 < 20 goto label2;\n"
            + "\n"
            + "    l2#8 = l1#1;\n"
            + "\n"
            + "    l3#9 = l3#4 + 1;\n"
            + "\n"
            + "    l3#10 = phi(l3#7, l3#9);\n"
            + "\n"
            + "    l2#11 = phi(l2#6, l2#8);\n"
            + "\n"
            + "    goto label1;\n"
            + "\n"
            + "  label2:\n"
            + "    l2#6 = l3#4;\n"
            + "\n"
            + "    l3#7 = l3#4 + 2;\n"
            + "\n"
            + "  label3:\n"
            + "    return l2#5;\n"
            + "}\n";

    Assert.assertEquals(expectedBodyString, builder.build().toString());
  }

  @Test
  public void testTrapedSSA() {
    StaticSingleAssignmentFormer ssa = new StaticSingleAssignmentFormer();
    Body.BodyBuilder builder = new Body.BodyBuilder(createTrapedBody(), Collections.emptySet());

    ssa.interceptBody(builder);

    String expectedBodyString =
        "{\n"
            + "    int l0, l1, l2, l3, l0#0, l1#1, l2#2, l3#3, l3#4, l2#5, l2#6, l3#7, l2#8, l2#10, l2#11, l3#12, l3#13, l2#14;\n"
            + "    ref stack4, stack4#9;\n"
            + "\n"
            + "\n"
            + "    l0#0 := @this: Test;\n"
            + "\n"
            + "    l1#1 = 1;\n"
            + "\n"
            + "    l2#2 = 1;\n"
            + "\n"
            + "    l3#3 = 0;\n"
            + "\n"
            + "  label1:\n"
            + "    l3#4 = phi(l3#3, l3#13);\n"
            + "\n"
            + "    l2#5 = phi(l2#2, l2#14);\n"
            + "\n"
            + "    if l3#4 < 100 goto label6;\n"
            + "\n"
            + "    if l2#5 < 20 goto label5;\n"
            + "\n"
            + "  label2:\n"
            + "    l2#8 = l1#1;\n"
            + "\n"
            + "  label3:\n"
            + "    l2#11 = phi(l2#8, l2#10);\n"
            + "\n"
            + "    l3#12 = l3#4 + 1;\n"
            + "\n"
            + "    l3#13 = phi(l3#7, l3#12);\n"
            + "\n"
            + "    l2#14 = phi(l2#6, l2#11);\n"
            + "\n"
            + "    goto label1;\n"
            + "\n"
            + "  label4:\n"
            + "    stack4#9 := @caughtexception;\n"
            + "\n"
            + "    l2#10 = 0;\n"
            + "\n"
            + "    goto label3;\n"
            + "\n"
            + "  label5:\n"
            + "    l2#6 = l3#4;\n"
            + "\n"
            + "    l3#7 = l3#4 + 2;\n"
            + "\n"
            + "  label6:\n"
            + "    return l2#5;\n"
            + "\n"
            + " catch Exception from label2 to label3 with label4;\n"
            + "}\n";

    Assert.assertEquals(expectedBodyString, builder.build().toString());
  }

  /**
   *
   *
   * <pre>
   *    l0 := @this Test
   *    l1 = 1
   *    l2 = 1
   *    l3 = 0
   * label1:
   *    if l3 < 100 goto label3
   *    if l2 < 20 goto label 2
   *    l2 = l1
   *    l3 = l3 + 1
   *    goto label1;
   * label2:
   *    l2 = l3
   *    l3 = l3 + 2
   * label3:
   *    return l2
   * </pre>
   */
  private Body createBody() {
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
    builder.addFlow(stmt4, stmt6);
    builder.addFlow(stmt5, stmt7);
    builder.addFlow(stmt5, stmt9);
    builder.addFlow(stmt7, stmt8);
    builder.addFlow(stmt9, stmt10);
    builder.addFlow(stmt8, stmt11);
    builder.addFlow(stmt10, stmt11);
    builder.addFlow(stmt11, stmt4);

    // build startingStmt
    builder.setStartingStmt(startingStmt);

    // build position
    Position position = NoPositionInformation.getInstance();
    builder.setPosition(position);

    return builder.build();
  }

  /**
   *
   *
   * <pre>
   *    l0 := @this Test
   *    l1 = 1
   *    l2 = 1
   *    l3 = 0
   * label1:
   *    if l3 < 100 goto label6
   *    if l2 < 20 goto label5
   * label2:
   *    l2 = l1
   * label3:
   *    l3 = l3 + 1
   *    goto label1;
   * label4:
   *    stack4 := @caughtexception
   *    l2 = 0;
   *    goto label3;
   * label5:
   *    l2 = l3
   *    l3 = l3 + 2
   * label6:
   *    return l2
   *
   * catch Exception from label2 to label3 with label4;
   * </pre>
   */
  private Body createTrapedBody() {
    Body.BodyBuilder builder = Body.builder();
    builder.setMethodSignature(methodSignature);

    // build set locals
    Set<Local> locals = ImmutableUtils.immutableSet(l0, l1, l2, l3, stack4);
    builder.setLocals(locals);

    // set graph
    builder.addFlow(startingStmt, stmt1);
    builder.addFlow(stmt1, stmt2);
    builder.addFlow(stmt2, stmt3);
    builder.addFlow(stmt3, stmt4);
    builder.addFlow(stmt4, stmt5);
    builder.addFlow(stmt4, stmt6);
    builder.addFlow(stmt5, stmt7);
    builder.addFlow(stmt5, stmt9);
    builder.addFlow(stmt7, stmt8);
    builder.addFlow(stmt9, stmt10);
    builder.addFlow(stmt8, stmt11);
    builder.addFlow(stmt10, stmt11);
    builder.addFlow(stmt11, stmt4);

    builder.addFlow(handlerStmt, l2eq0);
    builder.addFlow(l2eq0, goTo);
    builder.addFlow(goTo, stmt8);

    // build startingStmt
    builder.setStartingStmt(startingStmt);

    // build position
    Position position = NoPositionInformation.getInstance();
    builder.setPosition(position);

    List<Trap> traps = ImmutableUtils.immutableList(trap);
    builder.setTraps(traps);

    return builder.build();
  }
}
