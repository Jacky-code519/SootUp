package de.upb.swt.soot.test.java.sourcecode.minimaltestsuite.java6;

import categories.Java8Test;
import de.upb.swt.soot.core.model.SootMethod;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.test.java.sourcecode.minimaltestsuite.MinimalSourceTestSuiteBase;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** @author: Hasitha Rajapakse * */
@Category(Java8Test.class)
public class GenericTypeParamOnClassTest extends MinimalSourceTestSuiteBase {
  @Override
  public MethodSignature getMethodSignature() {
    return identifierFactory.getMethodSignature(
        getDeclaredClassSignature(), "genericTypeParamOnClass", "void", Collections.emptyList());
  }

  /**
   *
   *
   * <pre>
   * public void genericTypeParamOnClass() {
   * A<Integer> a = new A<Integer>();
   * a.set(5);
   * int x = a.get();
   * }
   * </pre>
   */
  @Override
  public List<String> expectedBodyStmts() {
    return Stream.of(
            "r0 := @this: GenericTypeParamOnClass",
            "$r1 = new GenericTypeParamOnClass$A",
            "specialinvoke $r1.<GenericTypeParamOnClass$A: void <init>()>()",
            "specialinvoke $r1.<GenericTypeParamOnClass$A: void set(java.lang.Object)>(5)",
            "$r2 = virtualinvoke $r1.<GenericTypeParamOnClass$A: java.lang.Object get()>()",
            "$r3 = (java.lang.Integer) $r2",
            "return")
        .collect(Collectors.toList());
  }

  @Test
  public void test() {
    SootMethod method = loadMethod(getMethodSignature());
    assertJimpleStmts(method, expectedBodyStmts());
  }
}
