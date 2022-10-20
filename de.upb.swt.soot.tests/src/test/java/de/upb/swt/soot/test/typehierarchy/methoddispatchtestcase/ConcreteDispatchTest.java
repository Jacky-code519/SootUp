package de.upb.swt.soot.test.typehierarchy.methoddispatchtestcase;

import static junit.framework.TestCase.*;

import categories.Java8Test;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.typehierarchy.MethodDispatchResolver;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.test.typehierarchy.MethodDispatchBase;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** @author: Hasitha Rajapakse * */
@Category(Java8Test.class)
public class ConcreteDispatchTest extends MethodDispatchBase {
  @Test
  public void method() {
    ClassType sootClassTypeA = getClassType("A");
    ClassType sootClassTypeB = getClassType("B");

    MethodSignature sootMethod1 =
        identifierFactory.getMethodSignature(
            sootClassTypeA, "method", "void", Collections.emptyList());
    MethodSignature sootMethod2 =
        identifierFactory.getMethodSignature(
            sootClassTypeA, "method2", "void", Collections.emptyList());
    MethodSignature sootMethod3 =
        identifierFactory.getMethodSignature(
            sootClassTypeB, "method2", "void", Collections.emptyList());

    MethodSignature candidate1 =
        MethodDispatchResolver.resolveConcreteDispatch(customTestWatcher.getView(), sootMethod1);
    assertEquals(candidate1, sootMethod1);

    MethodSignature candidate2 =
        MethodDispatchResolver.resolveConcreteDispatch(customTestWatcher.getView(), sootMethod2);
    assertEquals(candidate2, sootMethod3);
  }
}
