package de.upb.swt.soot.test.java.bytecode.minimaltestsuite.java6;

import static org.junit.Assert.assertEquals;

import categories.Java8Test;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.model.SootMethod;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.test.java.bytecode.minimaltestsuite.MinimalBytecodeTestSuiteBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.experimental.categories.Category;

/** @author Kaustubh Kelkar */
@Category(Java8Test.class)
public class InstanceOfCheckTest extends MinimalBytecodeTestSuiteBase {
  @Override
  public MethodSignature getMethodSignature() {
    return identifierFactory.getMethodSignature(
        "instanceOfCheckMethod", getDeclaredClassSignature(), "void", Collections.emptyList());
  }

  @org.junit.Test
  public void test() {
    SootMethod method = loadMethod(getMethodSignature());
    assertJimpleStmts(method, expectedBodyStmts());
    SootClass<?> sootClass = loadClass(getDeclaredClassSignature());
    if (sootClass.getSuperclass().isPresent()) {
      assertEquals("InstanceOfCheckSuper", sootClass.getSuperclass().get().getClassName());
    }
  }

  /**
   *
   *
   * <pre>
   * public void instanceOfCheckMethod(){
   * InstanceOfCheck obj= new InstanceOfCheck();
   * System.out.println(obj instanceof InstanceOfCheckSuper);
   * }
   *
   * </pre>
   */
  @Override
  public List<String> expectedBodyStmts() {
    return Stream.of(
            "l0 := @this: InstanceOfCheck",
            "$stack2 = new InstanceOfCheck",
            "specialinvoke $stack2.<InstanceOfCheck: void <init>()>()",
            "l1 = $stack2",
            "$stack4 = <java.lang.System: java.io.PrintStream out>",
            "$stack3 = l1 instanceof InstanceOfCheckSuper",
            "virtualinvoke $stack4.<java.io.PrintStream: void println(boolean)>($stack3)",
            "return")
        .collect(Collectors.toCollection(ArrayList::new));
  }
}
