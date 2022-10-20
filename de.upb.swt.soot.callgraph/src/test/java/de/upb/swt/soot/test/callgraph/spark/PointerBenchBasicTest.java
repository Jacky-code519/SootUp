package de.upb.swt.soot.test.callgraph.spark;

import static junit.framework.TestCase.*;

import com.google.common.collect.Sets;
import de.upb.swt.soot.callgraph.spark.pag.nodes.Node;
import de.upb.swt.soot.core.jimple.basic.Local;
import de.upb.swt.soot.core.model.SootMethod;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.java.core.types.JavaClassType;
import java.util.*;
import org.junit.Test;

public class PointerBenchBasicTest extends SparkTestBase {

  /**
   * <pre>
   * java code:
   * main(){
   *     A a = new A();
   *     A b = a;
   * }
   *
   * description:
   * - simple assignment
   *
   * expected:
   * - a and b point to same object
   */
  @Test
  public void testSimpleAlias1() {
    setUpPointerBench("basic.SimpleAlias1");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "main", "void", Collections.singletonList("java.lang.String[]"));
    SootMethod targetMethod = getTargetMethod(targetMethodSig);
    Map<Integer, Local> lineNumberToA =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.A", new ArrayList<>());

    Local a = lineNumberToA.get(21);
    Local b = lineNumberToA.get(23);

    Set<Node> aPointsTo = spark.getPointsToSet(a);
    Set<Node> bPointsTo = spark.getPointsToSet(b);

    // a must point to 1 object
    assertTrue(aPointsTo.size() == 1);
    // b must point to 1 object
    assertTrue(bPointsTo.size() == 1);
    // a and b must point to same set of objects
    assertTrue(aPointsTo.equals(bPointsTo));
  }

  /**
   * <pre>
   * java code:
   * main(){
   *     int i = 0;
   *     A a = new A();
   *     A b = new A();
   *     if (i < 0)
   *       a = b;
   * }
   *
   * description:
   * - conditional assigment
   *
   * expected:
   * - i cannot point to same objects as a and b
   * - a points to 2 objects
   * - b points to 1 object
   * - a and b points to a common object
   */
  @Test
  public void testBranching1() {
    setUpPointerBench("basic.Branching1");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "main", "void", Collections.singletonList("java.lang.String[]"));
    SootMethod targetMethod = getTargetMethod(targetMethodSig);
    Map<Integer, Local> lineNumberToInt =
        getLineNumberToLocalMap(targetMethod, "int", new ArrayList<>());
    Map<Integer, Local> lineNumberToA =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.A", new ArrayList<>());

    Local i = lineNumberToInt.get(19);

    Local a = lineNumberToA.get(22);
    Local b = lineNumberToA.get(24);

    Set<Node> iPointsTo = spark.getPointsToSet(i);
    Set<Node> aPointsTo = spark.getPointsToSet(a);
    Set<Node> bPointsTo = spark.getPointsToSet(b);

    // i and a never point to a common object
    assertTrue(Sets.intersection(iPointsTo, bPointsTo).isEmpty());
    // i and b never point to a common object
    assertTrue(Sets.intersection(iPointsTo, aPointsTo).isEmpty());
    // a may point to 2 objects
    assertTrue(aPointsTo.size() == 2);
    // b may point to 1 object
    assertTrue(bPointsTo.size() == 1);
    // a and b may point to a common object
    assertFalse(Sets.intersection(aPointsTo, bPointsTo).isEmpty());
    // a and b must not point to same set of objects
    assertFalse(aPointsTo.equals(bPointsTo));
  }

  /**
   * <pre>
   * java code:
   * main(){
   *     A a = new A();
   *     test(a);
   * }
   *
   * static test(A x){
   *     A b = x;
   * }
   *
   * description:
   * - parameter is being passed to a static method
   *
   * expected:
   * - b and x point to same object
   */
  @Test
  public void testParameter1() {
    setUpPointerBench("basic.Parameter1");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "test", "void", Collections.singletonList("benchmark.objects.A"));
    SootMethod targetMethod = getTargetMethod(targetMethodSig);
    List<Local> params = new ArrayList<>();
    Map<Integer, Local> lineNumberToA =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.A", params);

    Local x = params.get(0);
    Local b = lineNumberToA.get(19);

    Set<Node> xPointsTo = spark.getPointsToSet(x);
    Set<Node> bPointsTo = spark.getPointsToSet(b);

    // x must point to 1 object
    assertTrue(xPointsTo.size() == 1);
    // b must point to 1 object
    assertTrue(bPointsTo.size() == 1);
    // x and b must point to same set of objects
    assertTrue(xPointsTo.equals(bPointsTo));
  }

  /**
   *
   *
   * <pre>
   * java code:
   * main(){
   *     A a = new A();
   *     Parameter2 p2 = new Parameter2();
   *     p2.test(a);
   * }
   *
   * class Parameter2{
   *    test(A x) {
   *      A b = x;
   *    }
   * }
   *
   * description:
   * parameter is being passed to an instance method
   *
   * expected:
   * - b and x point to same object
   * </pre>
   */
  @Test
  public void testParameter2() {
    setUpPointerBench("basic.Parameter2");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "test", "void", Collections.singletonList("benchmark.objects.A"));
    SootMethod targetMethod = getTargetMethod(targetMethodSig);
    List<Local> params = new ArrayList<>();
    Map<Integer, Local> lineNumberToA =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.A", params);

    Local x = params.get(0);
    Local b = lineNumberToA.get(21);

    Set<Node> xPointsTo = spark.getPointsToSet(x);
    Set<Node> bPointsTo = spark.getPointsToSet(b);

    // x must point to 1 object
    assertTrue(xPointsTo.size() == 1);
    // b must point to 1 object
    assertTrue(bPointsTo.size() == 1);
    // x and b must point to same set of objects
    assertTrue(xPointsTo.equals(bPointsTo));
  }

  /**
   *
   *
   * <pre>
   * java code:
   * main() {
   *     A a = new A();
   *     A b = new A();
   *
   *     b.f = new B();
   *     alloc(a, b);
   *
   *     B x = a.f;
   *     B y = b.f;
   * }
   *
   * static alloc(A x, A y) {
   *     x.f = y.f;
   * }
   *
   * description:
   * - a field of an object is being assigned to a field of another object inside a static method
   *
   * expected:
   * - b.f does not point to the same object as a and b
   * - x and y point to same objet
   * </pre>
   */
  @Test
  public void testInterprocedural1() {
    setUpPointerBench("basic.Interprocedural1");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "main", "void", Collections.singletonList("java.lang.String[]"));
    SootMethod targetMethod = getTargetMethod(targetMethodSig);
    Map<Integer, Local> lineNumberToA =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.A", new ArrayList<>());
    Map<Integer, Local> lineNumberToB =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.B", new ArrayList<>());

    Local a = lineNumberToA.get(25);
    Local b = lineNumberToA.get(26);
    Local bDotF = lineNumberToB.get(29);
    Local x = lineNumberToB.get(32);
    Local y = lineNumberToB.get(33);

    Set<Node> aPointsTo = spark.getPointsToSet(a);
    Set<Node> bPointsTo = spark.getPointsToSet(b);
    Set<Node> bDotFPointsTo = spark.getPointsToSet(bDotF);
    Set<Node> xPointsTo = spark.getPointsToSet(x);
    Set<Node> yPointsTo = spark.getPointsToSet(y);

    // b.f and a must not point to a common object
    assertTrue(Sets.intersection(bDotFPointsTo, aPointsTo).isEmpty());
    // b.f and b must not point to a common object
    assertTrue(Sets.intersection(bDotFPointsTo, bPointsTo).isEmpty());
    // x and y must point to same set of objects
    assertTrue(xPointsTo.equals(yPointsTo));
  }

  /**
   *
   *
   * <pre>
   * java code:
   * main() {
   *     A a = new A();
   *     A b = new A();
   *
   *     b.f = new B();
   *     Interprocedural2 m2 = new Interprocedural2();
   *     m2.alloc(a, b);
   *
   *     B x = a.f;
   *     B y = b.f;
   *   }
   *
   * class Interprocedural2 {
   *   alloc(A x, A y) {
   *     x.f = y.f;
   *   }
   * }
   *
   * description:
   * - a field of an object is being assigned to a field of another object inside an instance method
   *
   * expected:
   * - b.f does not point to the same object as a, b, and m2
   * - x and y point to same objet
   * </pre>
   */
  @Test
  public void testInterprocedural2() {
    setUpPointerBench("basic.Interprocedural2");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "main", "void", Collections.singletonList("java.lang.String[]"));
    SootMethod targetMethod = getTargetMethod(targetMethodSig);
    Map<Integer, Local> lineNumberToA =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.A", new ArrayList<>());
    Map<Integer, Local> lineNumberToB =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.B", new ArrayList<>());
    Map<Integer, Local> lineNumberToInterprocedural2 =
        getLineNumberToLocalMap(targetMethod, "basic.Interprocedural2", new ArrayList<>());

    Local a = lineNumberToA.get(27);
    Local b = lineNumberToA.get(28);
    Local bDotF = lineNumberToB.get(31);
    Local m2 = lineNumberToInterprocedural2.get(32);
    Local x = lineNumberToB.get(35);
    Local y = lineNumberToB.get(36);

    Set<Node> aPointsTo = spark.getPointsToSet(a);
    Set<Node> bPointsTo = spark.getPointsToSet(b);
    Set<Node> bDotFPointsTo = spark.getPointsToSet(bDotF);
    Set<Node> m2PointsTo = spark.getPointsToSet(m2);
    Set<Node> xPointsTo = spark.getPointsToSet(x);
    Set<Node> yPointsTo = spark.getPointsToSet(y);

    // b.f and a must not point to a common object
    assertTrue(Sets.intersection(bDotFPointsTo, aPointsTo).isEmpty());
    // b.f and b must not point to a common object
    assertTrue(Sets.intersection(bDotFPointsTo, bPointsTo).isEmpty());
    // b.f and m2 must not point to a common object
    assertTrue(Sets.intersection(bDotFPointsTo, m2PointsTo).isEmpty());
    // x and y must point to same set of objects
    assertTrue(xPointsTo.equals(yPointsTo));
  }

  /**
   *
   *
   * <pre>
   * java code:
   * main() {
   *     A a = new A();
   *     A b = id(a);
   * }
   * static A id(A x) {
   *     return x;
   * }
   *
   * description:
   * - assignment of the return value of a static method
   *
   * expected:
   * - a and b points to same object
   * </pre>
   */
  @Test
  public void testReturnValue1() {
    setUpPointerBench("basic.ReturnValue1");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "main", "void", Collections.singletonList("java.lang.String[]"));
    SootMethod targetMethod = getTargetMethod(targetMethodSig);
    Map<Integer, Local> lineNumberToA =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.A", new ArrayList<>());

    Local a = lineNumberToA.get(25);
    Local b = lineNumberToA.get(26);

    Set<Node> aPointsTo = spark.getPointsToSet(a);
    Set<Node> bPointsTo = spark.getPointsToSet(b);

    // a and b must point to same set of objects
    assertTrue(aPointsTo.equals(bPointsTo));
  }

  /**
   *
   *
   * <pre>
   * java code:
   * main() {
   *     A a = new A();
   *     ReturnValue2 rv2 = new ReturnValue2();
   *     A b = rv2.id(a);
   * }
   *
   * class ReturnValue2{
   *   public A id(A x) {
   *     return x;
   *   }
   * }
   *
   * description:
   * - assignment of the return value of an instance method
   *
   * expected:
   * - a and b points to same object
   * </pre>
   */
  @Test
  public void testReturnValue2() {
    setUpPointerBench("basic.ReturnValue2");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "main", "void", Collections.singletonList("java.lang.String[]"));
    SootMethod targetMethod = getTargetMethod(targetMethodSig);
    Map<Integer, Local> lineNumberToA =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.A", new ArrayList<>());
    Map<Integer, Local> lineNumberToReturnValue2 =
        getLineNumberToLocalMap(targetMethod, "basic.ReturnValue2", new ArrayList<>());

    Local a = lineNumberToA.get(27);
    Local rv2 = lineNumberToReturnValue2.get(28);
    Local b = lineNumberToA.get(29);

    Set<Node> aPointsTo = spark.getPointsToSet(a);
    Set<Node> bPointsTo = spark.getPointsToSet(b);
    Set<Node> rv2PointsTo = spark.getPointsToSet(rv2);

    // a and b must point to same set of objects
    assertTrue(aPointsTo.equals(bPointsTo));
    // a and rv2 must not have a common object
    assertTrue(Sets.intersection(aPointsTo, rv2PointsTo).isEmpty());
  }

  /**
   *
   *
   * <pre>
   * java code:
   * main() {
   *     A a = new A();
   *     A b = id(a);
   *     B x = b.f;
   *     B y = a.f;
   * }
   *
   * static A id(A x) {
   *     A y = new A();
   *     y.f = new B();
   *     return y;
   * }
   *
   * description:
   * - an object is beign passed to a static method, which returns a different object
   *
   * expected:
   * - a, b and y does not point to same object
   * </pre>
   */
  @Test
  public void testReturnValue3() {
    setUpPointerBench("basic.ReturnValue3");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "main", "void", Collections.singletonList("java.lang.String[]"));
    SootMethod targetMethod = getTargetMethod(targetMethodSig);
    Map<Integer, Local> lineNumberToA =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.A", new ArrayList<>());
    Map<Integer, Local> lineNumberToB =
        getLineNumberToLocalMap(targetMethod, "benchmark.objects.B", new ArrayList<>());

    MethodSignature idMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature,
            "id",
            "benchmark.objects.A",
            Collections.singletonList("benchmark.objects.A"));
    SootMethod idMethod = getTargetMethod(idMethodSig);
    Map<Integer, Local> lineNumberToBInId =
        getLineNumberToLocalMap(idMethod, "benchmark.objects.B", new ArrayList<>());

    Local a = lineNumberToA.get(28);
    Local b = lineNumberToA.get(29);
    Local x = lineNumberToB.get(30);
    Local y = lineNumberToB.get(31);

    Local yDotF = lineNumberToBInId.get(22);

    Set<Node> aPointsTo = spark.getPointsToSet(a);
    Set<Node> bPointsTo = spark.getPointsToSet(b);
    Set<Node> xPointsTo = spark.getPointsToSet(x);
    Set<Node> yPointsTo = spark.getPointsToSet(y);
    Set<Node> yDotFPointsTo = spark.getPointsToSet(yDotF);

    // a, b and y must not have a common object
    assertTrue(
        Sets.intersection(
                Sets.intersection(aPointsTo, bPointsTo), Sets.intersection(bPointsTo, yPointsTo))
            .isEmpty());
    // x and y.f in id() must point to a common object
    assertFalse(Sets.intersection(xPointsTo, yDotFPointsTo).isEmpty());
  }

  /**
   *
   *
   * <pre>
   * java code:
   * class N {
   *     public String value = "";
   *     public N next;
   *
   *     public N() {
   *       next = null;
   *     }
   * }
   *
   * test() {
   *     N node = new N();
   *
   *     int i = 0;
   *     while (i < 10) {
   *       node = node.next;
   *       i++;
   *     }
   *
   *     N o = node.next;
   *     N p = node.next.next;
   *     N q = node.next.next.next;
   * }
   *
   * description:
   * - An object with nested structure is being traversed
   *
   * expected:
   * - node and o,p,q,i do not point to same object
   * </pre>
   */
  @Test
  public void testLoops1() {
    setUpPointerBench("basic.Loops1");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "test", "void", Collections.emptyList());
    SootMethod targetMethod = getTargetMethod(targetMethodSig);
    Map<Integer, Local> lineNumberToN =
        getLineNumberToLocalMap(targetMethod, "basic.Loops1$N", new ArrayList<>());
    Map<Integer, Local> lineNumberToInt =
        getLineNumberToLocalMap(targetMethod, "int", new ArrayList<>());

    Local node = lineNumberToN.get(28);
    Local i = lineNumberToInt.get(30);
    Local o = lineNumberToN.get(36);
    Local p = lineNumberToN.get(37);
    Local q = lineNumberToN.get(38);

    Set<Node> nodePointsTo = spark.getPointsToSet(node);
    Set<Node> iPointsTo = spark.getPointsToSet(i);
    Set<Node> oPointsTo = spark.getPointsToSet(o);
    Set<Node> pPointsTo = spark.getPointsToSet(p);
    Set<Node> qPointsTo = spark.getPointsToSet(q);

    // node and o must not point to a common object
    assertTrue(Sets.intersection(nodePointsTo, oPointsTo).isEmpty());
    // node and p must not point to a common object
    assertTrue(Sets.intersection(nodePointsTo, pPointsTo).isEmpty());
    // node and q must not point to a common object
    assertTrue(Sets.intersection(nodePointsTo, qPointsTo).isEmpty());
    // node and i must not point to a common object
    assertTrue(Sets.intersection(nodePointsTo, iPointsTo).isEmpty());
  }

  /**
   *
   *
   * <pre>
   * java code:
   * class N {
   *     public String value = "";
   *     public N next;
   *
   *     public N() {
   *       next = new N();
   *     }
   * }
   *
   * test() {
   *     N node = new N();
   *
   *     int i = 0;
   *     while (i < 10) {
   *       node = node.next;
   *       i++;
   *     }
   *
   *     N o = node.next;
   *     N p = node.next.next;
   * }
   *
   * description:
   * - An object with nested structure is being traversed
   *
   * expected:
   *  - node and o,p,i do not point to same object
   *  - node.next and o point to same object
   * </pre>
   */
  @Test
  public void testLoops2() {
    setUpPointerBench("basic.Loops2");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "test", "void", Collections.emptyList());
    SootMethod targetMethod = getTargetMethod(targetMethodSig);

    JavaClassType NClassSignature = identifierFactory.getClassType("basic.Loops2$N");
    MethodSignature NMethodSig =
        identifierFactory.getMethodSignature(
            NClassSignature, "<init>", "void", Collections.emptyList());
    SootMethod NMethod = getTargetMethodFromClass(NMethodSig, NClassSignature);

    Map<Integer, Local> lineNumberToN =
        getLineNumberToLocalMap(targetMethod, "basic.Loops2$N", new ArrayList<>());
    Map<Integer, Local> lineNumberToInt =
        getLineNumberToLocalMap(targetMethod, "int", new ArrayList<>());
    Map<Integer, Local> lineNumberToNinN =
        getLineNumberToLocalMap(NMethod, "basic.Loops2$N", new ArrayList<>());

    Local nextInN = lineNumberToNinN.get(23);
    Local node = lineNumberToN.get(29);
    Local i = lineNumberToInt.get(31);
    Local o = lineNumberToN.get(37);
    Local p = lineNumberToN.get(38);

    Set<Node> nextInNPointsTo = spark.getPointsToSet(nextInN);
    Set<Node> nodePointsTo = spark.getPointsToSet(node);
    Set<Node> iPointsTo = spark.getPointsToSet(i);
    Set<Node> oPointsTo = spark.getPointsToSet(o);
    Set<Node> pPointsTo = spark.getPointsToSet(p);

    // node and o must not point to same set of objects
    assertFalse(nodePointsTo.equals(oPointsTo));
    // node and p must not point to same set of objects
    assertFalse(nodePointsTo.equals(pPointsTo));
    // node and i must not point to a common object
    assertTrue(Sets.intersection(nodePointsTo, iPointsTo).isEmpty());
    // node.next and o must point to the same set of objects
    assertTrue(oPointsTo.equals(nextInNPointsTo));
  }

  /**
   *
   *
   * <pre>
   * java code:
   * class N {
   *     public String value;
   *     public N next;
   *
   *     public N(String value) {
   *       this.value = value;
   *       next = null;
   *     }
   * }
   *
   * class Recursion1 {
   *     N recursive(int i, N m) {
   *     if (i < 10) {
   *       int j = i + 1;
   *       return recursive(j, m.next);
   *     }
   *     return m;
   *   }
   * }
   *
   * test() {
   *     N node = new N("");
   *
   *     Recursion1 r1 = new Recursion1();
   *     N n = r1.recursive(0, node);
   *
   *     N o = node.next;
   *     N p = node.next.next;
   *     N q = node.next.next.next;
   * }
   *
   * description:
   * - An object with nested structure is being traversed inside an instance method
   *
   * expected:
   * - node and n point to same object
   * - node and o,p,q do not point to same object
   * </pre>
   */
  @Test
  public void testRecursion1() {
    setUpPointerBench("basic.Recursion1");
    MethodSignature targetMethodSig =
        identifierFactory.getMethodSignature(
            mainClassSignature, "test", "void", Collections.emptyList());
    SootMethod targetMethod = getTargetMethod(targetMethodSig);

    Map<Integer, Local> lineNumberToN =
        getLineNumberToLocalMap(targetMethod, "basic.Recursion1$N", new ArrayList<>());

    Local node = lineNumberToN.get(39);
    Local n = lineNumberToN.get(42);
    Local o = lineNumberToN.get(44);
    Local p = lineNumberToN.get(45);
    Local q = lineNumberToN.get(46);

    Set<Node> nodePointsTo = spark.getPointsToSet(node);
    Set<Node> nPointsTo = spark.getPointsToSet(n);
    Set<Node> oPointsTo = spark.getPointsToSet(o);
    Set<Node> pPointsTo = spark.getPointsToSet(p);
    Set<Node> qPointsTo = spark.getPointsToSet(q);

    // node and n must point to same set of objects
    assertTrue(nodePointsTo.equals(nPointsTo));
    // node and o must not point to same set of objects
    assertTrue(Sets.intersection(nodePointsTo, oPointsTo).isEmpty());
    // node and p must not point to same set of objects
    assertTrue(Sets.intersection(nodePointsTo, pPointsTo).isEmpty());
    // node and q must not point to same set of objects
    assertTrue(Sets.intersection(nodePointsTo, qPointsTo).isEmpty());
  }
}
