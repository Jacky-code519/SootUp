package de.upb.swt.soot.test.core.printer;

import static org.junit.Assert.*;

import de.upb.swt.soot.core.Project;
import de.upb.swt.soot.core.frontend.OverridingBodySource;
import de.upb.swt.soot.core.frontend.OverridingClassSource;
import de.upb.swt.soot.core.inputlocation.EagerInputLocation;
import de.upb.swt.soot.core.jimple.basic.NoPositionInformation;
import de.upb.swt.soot.core.jimple.basic.StmtPositionInfo;
import de.upb.swt.soot.core.jimple.common.constant.IntConstant;
import de.upb.swt.soot.core.jimple.common.stmt.JNopStmt;
import de.upb.swt.soot.core.jimple.common.stmt.JReturnVoidStmt;
import de.upb.swt.soot.core.jimple.common.stmt.Stmt;
import de.upb.swt.soot.core.jimple.javabytecode.stmt.JSwitchStmt;
import de.upb.swt.soot.core.model.*;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.util.EscapedWriter;
import de.upb.swt.soot.core.util.Utils;
import de.upb.swt.soot.core.util.printer.Printer;
import de.upb.swt.soot.core.views.View;
import de.upb.swt.soot.java.core.JavaProject;
import de.upb.swt.soot.java.core.language.JavaLanguage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import org.junit.Test;

public class LegacyJimplePrinterTest {

  SootClass buildClass(Body.BodyBuilder builder) {

    Project project =
        JavaProject.builder(new JavaLanguage(8)).addInputLocation(new EagerInputLocation()).build();
    View view = project.createOnDemandView();

    MethodSignature methodSignature =
        view.getIdentifierFactory()
            .getMethodSignature("main", "dummyMain", "void", Collections.emptyList());
    Body body =
        builder
            .setMethodSignature(methodSignature)
            .setPosition(NoPositionInformation.getInstance())
            .build();

    System.out.println(Utils.filterJimple(Utils.bodyStmtsAsStrings(body).stream()));

    SootMethod dummyMainMethod =
        new SootMethod(
            new OverridingBodySource(methodSignature, body),
            methodSignature,
            EnumSet.of(Modifier.PUBLIC, Modifier.STATIC),
            Collections.emptyList(),
            NoPositionInformation.getInstance());

    return new SootClass(
        new OverridingClassSource(
            Collections.singleton(dummyMainMethod),
            Collections.emptySet(),
            EnumSet.of(Modifier.PUBLIC),
            Collections.emptySet(),
            null,
            null,
            null,
            null,
            view.getIdentifierFactory().getClassType("dummyMain"),
            new EagerInputLocation()),
        SourceType.Application);
  }

  @Test
  public void printSwitchStmt() {

    StmtPositionInfo noPosInfo = StmtPositionInfo.createNoStmtPositionInfo();
    ArrayList<IntConstant> lookupValues = new ArrayList<>();
    lookupValues.add(IntConstant.getInstance(42));
    lookupValues.add(IntConstant.getInstance(33102));

    final JReturnVoidStmt returnstmt = new JReturnVoidStmt(noPosInfo);
    final JNopStmt jNop = new JNopStmt(noPosInfo);
    final JNopStmt jNop2 = new JNopStmt(noPosInfo);

    Stmt tableSwitch = new JSwitchStmt(IntConstant.getInstance(42), 4, 5, noPosInfo);

    {
      Body.BodyBuilder builder = Body.builder();
      builder.setStartingStmt(tableSwitch);

      builder.addFlow(tableSwitch, jNop);
      builder.addFlow(tableSwitch, jNop2);
      builder.addFlow(tableSwitch, returnstmt);

      builder.addFlow(jNop, jNop2);
      builder.addFlow(jNop2, returnstmt);

      SootClass tableClass = buildClass(builder);

      StringWriter sw = new StringWriter();
      new Printer(Printer.Option.LegacyMode)
          .printTo(tableClass, new PrintWriter(new EscapedWriter(sw)));

      assertEquals(
          Arrays.asList(
              "public static void main()",
              "tableswitch(42)",
              "case 4: goto label1",
              "case 5: goto label2",
              "default: goto label3",
              "label1:",
              "nop",
              "label2:",
              "nop",
              "label3:",
              "return"),
          Utils.filterJimple(sw.toString()));
    }

    {
      Stmt lookupSwitch = new JSwitchStmt(IntConstant.getInstance(123), lookupValues, noPosInfo);

      Body.BodyBuilder builder = Body.builder();
      builder.setStartingStmt(lookupSwitch);

      builder.addFlow(lookupSwitch, jNop);
      builder.addFlow(lookupSwitch, jNop2);
      builder.addFlow(lookupSwitch, returnstmt);

      builder.addFlow(jNop, jNop2);
      builder.addFlow(jNop2, returnstmt);

      SootClass lookupClass = buildClass(builder);

      StringWriter sw2 = new StringWriter();
      new Printer(Printer.Option.LegacyMode)
          .printTo(lookupClass, new PrintWriter(new EscapedWriter(sw2)));

      assertEquals(
          Arrays.asList(
              "public static void main()",
              "lookupswitch(123)",
              "case 42: goto label1",
              "case 33102: goto label2",
              "default: goto label3",
              "label1:",
              "nop",
              "label2:",
              "nop",
              "label3:",
              "return"),
          Utils.filterJimple(sw2.toString()));
    }
  }

  @Test(expected = RuntimeException.class)
  public void testValidOptions() {
    Printer p = new Printer(Printer.Option.UseImports, Printer.Option.LegacyMode);
    p.printTo(buildClass(Body.builder()), new PrintWriter(new StringWriter()));
  }
}
