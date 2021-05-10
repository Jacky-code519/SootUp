package de.upb.swt.soot.java.sourcecode.frontend;
/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2018-2020 Linghui Luo, Christian Brüggemann, Markus Schmidt
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
import com.ibm.wala.cast.loader.AstClass;
import com.ibm.wala.cast.loader.AstField;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.AstMethod.DebuggingInformation;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cfg.AbstractCFG;
import com.ibm.wala.cfg.IBasicBlock;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeCT.ClassConstants;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashSetFactory;
import de.upb.swt.soot.core.frontend.OverridingBodySource;
import de.upb.swt.soot.core.frontend.OverridingClassSource;
import de.upb.swt.soot.core.inputlocation.AnalysisInputLocation;
import de.upb.swt.soot.core.jimple.Jimple;
import de.upb.swt.soot.core.jimple.basic.*;
import de.upb.swt.soot.core.jimple.basic.Local;
import de.upb.swt.soot.core.jimple.basic.LocalGenerator;
import de.upb.swt.soot.core.jimple.basic.NoPositionInformation;
import de.upb.swt.soot.core.jimple.basic.StmtPositionInfo;
import de.upb.swt.soot.core.jimple.common.stmt.JReturnVoidStmt;
import de.upb.swt.soot.core.jimple.common.stmt.JThrowStmt;
import de.upb.swt.soot.core.jimple.common.stmt.Stmt;
import de.upb.swt.soot.core.model.Body;
import de.upb.swt.soot.core.model.Modifier;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.model.SootField;
import de.upb.swt.soot.core.model.SootMethod;
import de.upb.swt.soot.core.model.SourceType;
import de.upb.swt.soot.core.signatures.FieldSignature;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.types.NullType;
import de.upb.swt.soot.core.types.PrimitiveType;
import de.upb.swt.soot.core.types.Type;
import de.upb.swt.soot.core.types.VoidType;
import de.upb.swt.soot.java.core.*;
import de.upb.swt.soot.java.core.types.AnnotationType;
import de.upb.swt.soot.java.core.types.JavaClassType;
import de.upb.swt.soot.java.sourcecode.inputlocation.JavaSourcePathAnalysisInputLocation;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import javax.annotation.Nonnull;

/**
 * Converter which converts WALA IR to jimple.
 *
 * @author Linghui Luo
 */
public class WalaIRToJimpleConverter {

  final JavaIdentifierFactory identifierFactory;
  private final AnalysisInputLocation srcNamespace;
  private final HashMap<String, Integer> clsWithInnerCls;
  private final HashMap<String, String> walaToSootNameTable;
  private Set<SootField> sootFields;

  private Stmt rememberedStmt;
  private boolean isFirstStmtSet;

  public WalaIRToJimpleConverter(@Nonnull Set<String> sourceDirPath) {
    srcNamespace = new JavaSourcePathAnalysisInputLocation(sourceDirPath);
    // TODO: [ms] get identifierFactory from view - view can hold a different implementation
    identifierFactory = JavaIdentifierFactory.getInstance();
    clsWithInnerCls = new HashMap<>();
    walaToSootNameTable = new HashMap<>();
  }

  /**
   * Convert a wala {@link AstClass} to {@link SootClass}.
   *
   * @return A SootClass converted from walaClass
   */
  // TODO: remove deprecated
  @Deprecated
  public SootClass convertClass(AstClass walaClass) {
    JavaSootClassSource classSource = convertToClassSource(walaClass);
    // TODO: [ms] fix fixed SourceType - get it from project
    return new JavaSootClass(classSource, SourceType.Application);
  }

  JavaSootClassSource convertToClassSource(AstClass walaClass) {
    String fullyQualifiedClassName = convertClassNameFromWala(walaClass.getName().toString());
    JavaClassType classSig = identifierFactory.getClassType(fullyQualifiedClassName);
    // get super class
    IClass sc = walaClass.getSuperclass();
    JavaClassType superClass = null;
    if (sc != null) {
      superClass =
          identifierFactory.getClassType(convertClassNameFromWala(sc.getName().toString()));
    }

    // get interfaces
    Set<ClassType> interfaces = new HashSet<>();
    for (IClass i : walaClass.getDirectInterfaces()) {
      JavaClassType inter =
          identifierFactory.getClassType(convertClassNameFromWala(i.getName().toString()));
      interfaces.add(inter);
    }

    // get outer class
    JavaClassType outerClass = null;
    if (walaClass instanceof com.ibm.wala.cast.java.loader.JavaSourceLoaderImpl.JavaClass) {
      com.ibm.wala.cast.java.loader.JavaSourceLoaderImpl.JavaClass javaClass =
          (com.ibm.wala.cast.java.loader.JavaSourceLoaderImpl.JavaClass) walaClass;
      IClass ec = javaClass.getEnclosingClass();
      if (ec != null) {
        outerClass =
            identifierFactory.getClassType(convertClassNameFromWala(ec.getName().toString()));
      }
    }

    // add source position
    Position position = walaClass.getSourcePosition();

    // convert modifiers
    EnumSet<Modifier> modifiers = convertModifiers(walaClass);

    // convert fields
    Set<IField> fields = HashSetFactory.make(walaClass.getDeclaredInstanceFields());
    fields.addAll(walaClass.getDeclaredStaticFields());
    sootFields = new HashSet<>();
    for (IField walaField : fields) {
      SootField sootField = convertField(classSig, (AstField) walaField);
      sootFields.add(sootField);
    }

    if (outerClass != null) {
      // create enclosing reference to outerClass
      FieldSignature signature =
          identifierFactory.getFieldSignature("this$0", classSig, outerClass);
      SootField enclosingObject =
          new SootField(signature, EnumSet.of(Modifier.FINAL), NoPositionInformation.getInstance());
      sootFields.add(enclosingObject);
    }

    // convert methods
    Set<SootMethod> sootMethods = new HashSet<>();

    for (IMethod walaMethod : walaClass.getDeclaredMethods()) {
      SootMethod sootMethod = convertMethod(classSig, (AstMethod) walaMethod);
      sootMethods.add(sootMethod);
    }

    return createClassSource(
        walaClass,
        superClass,
        interfaces,
        outerClass,
        sootFields,
        sootMethods,
        position,
        modifiers,
        Collections.emptyList() // TODO:[ms] implement annotations);
        );
  }

  /** Create a {@link OverridingClassSource} object for the given walaClass. */
  public OverridingJavaClassSource createClassSource(
      AstClass walaClass,
      JavaClassType superClass,
      Set<ClassType> interfaces,
      JavaClassType outerClass,
      Set<SootField> sootFields,
      Set<SootMethod> sootMethods,
      Position position,
      EnumSet<Modifier> modifiers,
      Iterable<AnnotationType> annotations) {
    String fullyQualifiedClassName = convertClassNameFromWala(walaClass.getName().toString());
    JavaClassType classSignature = identifierFactory.getClassType(fullyQualifiedClassName);
    URL url = walaClass.getSourceURL();
    Path sourcePath = Paths.get(url.getPath());
    return new OverridingJavaClassSource(
        srcNamespace,
        sourcePath,
        classSignature,
        superClass,
        interfaces,
        outerClass,
        sootFields,
        sootMethods,
        convertPosition(position),
        modifiers,
        Collections.emptyList(), // TODO:[ms] implement annotations
        Collections.emptyList(), // TODO:[ms] implement methodannotations
        Collections.emptyList()); // TODO:[ms] implement fieldannotations
  }

  /**
   * Convert a wala {@link AstField} to {@link SootField}.
   *
   * @param classSig the class owns the field
   * @param walaField the wala field
   * @return A SootField object converted from walaField.
   */
  public SootField convertField(JavaClassType classSig, AstField walaField) {
    Type type = convertType(walaField.getFieldTypeReference());
    EnumSet<Modifier> modifiers = convertModifiers(walaField);
    FieldSignature signature =
        identifierFactory.getFieldSignature(walaField.getName().toString(), classSig, type);
    return new SootField(signature, modifiers, NoPositionInformation.getInstance());
  }

  /**
   * Convert a wala {@link AstMethod} to {@link SootMethod} and add it into the given sootClass.
   *
   * @param classSig the SootClass which should contain the converted SootMethod
   * @param walaMethod the walMethod to be converted
   */
  public SootMethod convertMethod(JavaClassType classSig, AstMethod walaMethod) {
    // create SootMethod instance
    List<Type> paraTypes = new ArrayList<>();
    List<String> sigs = new ArrayList<>();
    if (walaMethod.symbolTable() != null) {

      for (int i = walaMethod.isStatic() ? 0 : 1; i < walaMethod.getNumberOfParameters(); i++) {
        TypeReference type = walaMethod.getParameterType(i);
        Type paraType = convertType(type);
        paraTypes.add(identifierFactory.getType(paraType.toString()));
        sigs.add(paraType.toString());
      }
    }

    Type returnType = convertType(walaMethod.getReturnType());

    EnumSet<Modifier> modifiers = convertModifiers(walaMethod);

    List<ClassType> thrownExceptions = new ArrayList<>();
    try {
      for (TypeReference exception : walaMethod.getDeclaredExceptions()) {
        String exceptionName = convertClassNameFromWala(exception.getName().toString());
        JavaClassType exceptionSig = identifierFactory.getClassType(exceptionName);
        thrownExceptions.add(exceptionSig);
      }
    } catch (UnsupportedOperationException | InvalidClassFileException e) {
      e.printStackTrace();
    }
    // add debug info
    DebuggingInformation debugInfo = walaMethod.debugInfo();
    MethodSignature methodSig =
        identifierFactory.getMethodSignature(
            walaMethod.getName().toString(), classSig, returnType.toString(), sigs);

    Body body = createBody(methodSig, modifiers, walaMethod);
    return new WalaSootMethod(
        new OverridingBodySource(methodSig, body),
        methodSig,
        modifiers,
        thrownExceptions,
        debugInfo);
  }

  public Type convertType(TypeReference type) {
    if (type.isPrimitiveType()) {
      if (type.equals(TypeReference.Boolean)) {
        return PrimitiveType.getBoolean();
      } else if (type.equals(TypeReference.Byte)) {
        return PrimitiveType.getByte();
      } else if (type.equals(TypeReference.Char)) {
        return PrimitiveType.getChar();
      } else if (type.equals(TypeReference.Short)) {
        return PrimitiveType.getShort();
      } else if (type.equals(TypeReference.Int)) {
        return PrimitiveType.getInt();
      } else if (type.equals(TypeReference.Long)) {
        return PrimitiveType.getLong();
      } else if (type.equals(TypeReference.Float)) {
        return PrimitiveType.getFloat();
      } else if (type.equals(TypeReference.Double)) {
        return PrimitiveType.getDouble();
      } else if (type.equals(TypeReference.Void)) {
        return VoidType.getInstance();
      }
    } else if (type.isReferenceType()) {
      if (type.isArrayType()) {
        TypeReference t = type.getInnermostElementType();
        Type baseType = convertType(t);
        int dim = type.getDimensionality();
        return identifierFactory.getArrayType(baseType, dim);
      } else if (type.isClassType()) {
        if (type.equals(TypeReference.Null)) {
          return NullType.getInstance();
        } else {
          String className = convertClassNameFromWala(type.getName().toString());
          return identifierFactory.getClassType(className);
        }
      }
    }
    throw new RuntimeException("Unsupported type: " + type);
  }

  /** Return all modifiers for the given field. */
  public EnumSet<Modifier> convertModifiers(AstField field) {
    EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
    if (field.isFinal()) {
      modifiers.add(Modifier.FINAL);
    }
    if (field.isPrivate()) {
      modifiers.add(Modifier.PRIVATE);
    }
    if (field.isProtected()) {
      modifiers.add(Modifier.PROTECTED);
    }
    if (field.isPublic()) {
      modifiers.add(Modifier.PUBLIC);
    }
    if (field.isStatic()) {
      modifiers.add(Modifier.STATIC);
    }
    if (field.isVolatile()) {
      modifiers.add(Modifier.VOLATILE);
    }
    // TODO: TRANSIENT field
    return modifiers;
  }

  /** Return all modifiers for the given methodRef. */
  public EnumSet<Modifier> convertModifiers(AstMethod method) {
    EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
    if (method.isPrivate()) {
      modifiers.add(Modifier.PRIVATE);
    }
    if (method.isProtected()) {
      modifiers.add(Modifier.PROTECTED);
    }
    if (method.isPublic()) {
      modifiers.add(Modifier.PUBLIC);
    }
    if (method.isStatic()) {
      modifiers.add(Modifier.STATIC);
    }
    if (method.isFinal()) {
      modifiers.add(Modifier.FINAL);
    }
    if (method.isAbstract()) {
      modifiers.add(Modifier.ABSTRACT);
    }
    if (method.isSynchronized()) {
      modifiers.add(Modifier.SYNCHRONIZED);
    }
    if (method.isNative()) {
      modifiers.add(Modifier.NATIVE);
    }
    if (method.isSynthetic()) {
      modifiers.add(Modifier.SYNTHETIC);
    }
    if (method.isBridge()) {
      modifiers.add(Modifier.VOLATILE);
    }
    // TODO: strictfp and annotation
    return modifiers;
  }

  public EnumSet<Modifier> convertModifiers(AstClass klass) {
    int modif = klass.getModifiers();
    EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
    if (klass.isAbstract()) {
      modifiers.add(Modifier.ABSTRACT);
    }
    if (klass.isPrivate()) {
      modifiers.add(Modifier.PRIVATE);
    }
    if (klass.isSynthetic()) {
      modifiers.add(Modifier.SYNTHETIC);
    }
    if (klass.isPublic()) {
      modifiers.add(Modifier.PUBLIC);
    }
    if (klass.isInterface()) {
      modifiers.add(Modifier.INTERFACE);
    }
    if (klass.getSuperclass().getName().toString().equals("Ljava/lang/Enum")) {
      modifiers.add(Modifier.ENUM);
    }
    if ((modif & ClassConstants.ACC_FINAL) != 0) modifiers.add(Modifier.FINAL);
    // TODO:  annotation
    return modifiers;
  }

  private void emitStmt(@Nonnull Body.BodyBuilder bodyBuilder, @Nonnull Stmt stmt) {
    if (rememberedStmt != null) {
      if (rememberedStmt.fallsThrough()) {
        // determine whether successive emitted Stmts have a flow between them
        bodyBuilder.addFlow(rememberedStmt, stmt);
      }
    } else if (!isFirstStmtSet) {
      // determine first stmt to execute
      bodyBuilder.setStartingStmt(stmt);
      isFirstStmtSet = true;
    }
    rememberedStmt = stmt;
  }

  @Nonnull
  private Body createBody(
      MethodSignature methodSignature, EnumSet<Modifier> modifiers, AstMethod walaMethod) {

    // reset linking information
    rememberedStmt = null;
    isFirstStmtSet = false;

    final Body.BodyBuilder builder = Body.builder();
    builder.setMethodSignature(methodSignature);
    List<Trap> traps = new ArrayList<>();

    if (walaMethod.isAbstract()) {
      return builder.build();
    }

    AbstractCFG<?, ?> cfg = walaMethod.cfg();
    if (cfg != null) {
      LocalGenerator localGenerator = new LocalGenerator(new LinkedHashSet<>());
      // convert all wala instructions to jimple statements
      SSAInstruction[] insts = (SSAInstruction[]) cfg.getInstructions();
      if (insts.length > 0) {

        // set position for body
        DebuggingInformation debugInfo = walaMethod.debugInfo();
        Position bodyPos = debugInfo.getCodeBodyPosition();

        /* Look AsmMethodSourceContent.getBody, see AsmMethodSourceContent.emitLocals(); */

        if (!Modifier.isStatic(modifiers)) {
          JavaClassType thisType = (JavaClassType) methodSignature.getDeclClassType();
          Local thisLocal = localGenerator.generateThisLocal(thisType);
          Stmt stmt =
              Jimple.newIdentityStmt(
                  thisLocal,
                  Jimple.newThisRef(thisType),
                  convertPositionInfo(debugInfo.getInstructionPosition(0), null));
          emitStmt(builder, stmt);
        }

        // wala's first parameter is the "this" reference for non-static methods
        if (walaMethod.isStatic()) {
          for (int i = 0; i < walaMethod.getNumberOfParameters(); i++) {
            Type type = convertType(walaMethod.getParameterType(i));
            Local paraLocal = localGenerator.generateParameterLocal(type, i);

            Stmt stmt =
                Jimple.newIdentityStmt(
                    paraLocal,
                    Jimple.newParameterRef(type, i),
                    convertPositionInfo(debugInfo.getInstructionPosition(0), null));
            emitStmt(builder, stmt);
          }
        } else {
          for (int i = 1; i < walaMethod.getNumberOfParameters(); i++) {
            Type type = convertType(walaMethod.getParameterType(i));
            Local paraLocal = localGenerator.generateParameterLocal(type, i);

            Stmt stmt =
                Jimple.newIdentityStmt(
                    paraLocal,
                    Jimple.newParameterRef(type, i - 1),
                    convertPositionInfo(debugInfo.getInstructionPosition(0), null));
            emitStmt(builder, stmt);
          }
        }

        InstructionConverter instConverter =
            new InstructionConverter(this, methodSignature, walaMethod, localGenerator);
        HashMap<Integer, Stmt> index2Stmt = new HashMap<>();
        Stmt stmt = null;
        for (SSAInstruction inst : insts) {
          List<Stmt> retStmts = instConverter.convertInstruction(inst, index2Stmt);
          if (!retStmts.isEmpty()) {
            final int retStmtsSize = retStmts.size();
            stmt = retStmts.get(0);
            emitStmt(builder, stmt);
            index2Stmt.put(inst.iIndex(), stmt);

            for (int i = 1; i < retStmtsSize; i++) {
              stmt = retStmts.get(i);
              emitStmt(builder, stmt);
            }
          }
        }

        // add return void stmt for methods with return type being void
        if (walaMethod.getReturnType().equals(TypeReference.Void)) {
          Stmt ret;
          final boolean isImplicitLastStmtTargetOfBranchStmt = instConverter.hasJumpTarget(-1);
          final boolean validMethodLeaving =
              !(stmt instanceof JReturnVoidStmt || stmt instanceof JThrowStmt);
          if (index2Stmt.isEmpty() || validMethodLeaving || isImplicitLastStmtTargetOfBranchStmt) {
            // TODO? [ms] InstructionPosition of last line in the method seems strange to me ->
            // maybe use lastLine with
            // startcol: -1 because it does not exist in the source explicitly?
            ret =
                Jimple.newReturnVoidStmt(
                    convertPositionInfo(debugInfo.getInstructionPosition(insts.length - 1), null));
            emitStmt(builder, ret);
          } else {
            ret = stmt;
          }
          // needed because referencing a branch to the last stmt refers to: -1
          index2Stmt.put(-1, ret);
        }

        instConverter.setUpTargets(index2Stmt, builder);

        // calculate trap information
        for (Map.Entry<IBasicBlock<SSAInstruction>, TypeReference[]> catchBlockEntry :
            walaMethod.catchTypes().entrySet()) {

          final IBasicBlock<SSAInstruction> block = catchBlockEntry.getKey();
          final TypeReference[] exceptionTypes = catchBlockEntry.getValue();

          // find associated try block
          boolean found = false;
          IBasicBlock<?> itBlock = null;
          int idx = block.getFirstInstructionIndex() - 1;
          while (idx >= 0) {
            itBlock = cfg.getBlockForInstruction(idx);
            if (!itBlock.isCatchBlock()) {
              for (int i = itBlock.getFirstInstructionIndex();
                  i <= itBlock.getLastInstructionIndex();
                  i++) {
                final String instrString = insts[i].toString(walaMethod.symbolTable());
                // find instructions that ends with: #[0-9]{0,}try
                if (instrString.endsWith("try")) {
                  int pos = instrString.length() - 4;
                  while (pos > 0) {
                    // skip numbers
                    if (!('0' <= instrString.charAt(pos) && instrString.charAt(pos) <= '9')) {
                      break;
                    }
                    pos--;
                  }
                  if (instrString.charAt(pos) == '#') {
                    found = true;
                    break;
                  }
                }
              }
              if (found) {
                break;
              }
            }
            idx = itBlock.getFirstInstructionIndex() - 1;
          }

          Stmt from;
          if (found) {
            from = index2Stmt.get(itBlock.getFirstInstructionIndex());
          } else {
            from = index2Stmt.get(0);
          }

          int iidx = block.getFirstInstructionIndex() - 1;
          // search end of previous non catch block
          while (iidx >= 0 && cfg.getBlockForInstruction(iidx).isCatchBlock()) {
            iidx--;
          }
          assert (insts[iidx] instanceof SSAGotoInstruction);
          Stmt to = index2Stmt.get(iidx + 1); // exclusive!

          Stmt handlerStmt = index2Stmt.get(block.getFirstInstructionIndex());
          for (TypeReference type : exceptionTypes) {
            ClassType exception = (ClassType) convertType(type);
            traps.add(new JTrap(exception, from, to, handlerStmt));
          }
        }

        return builder
            .setTraps(traps)
            .setLocals(localGenerator.getLocals())
            .setPosition(convertPosition(bodyPos))
            .build();
      }
    }

    throw new IllegalStateException("can not create Body - no CFG from WALA present.");
  }

  /**
   * Convert className in wala-format to soot-format, e.g., wala-format: Ljava/lang/String ->
   * soot-format: java.lang.String.
   *
   * @param className in wala-format
   * @return className in soot.format
   */
  public String convertClassNameFromWala(String className) {
    String cl = className.intern();
    final String sootName = walaToSootNameTable.get(cl);
    if (sootName != null) {
      return sootName;
    }
    StringBuilder sb = new StringBuilder();
    if (className.startsWith("L")) {
      className = className.substring(1);
      String[] subNames = className.split("/");
      boolean isSpecial = false;
      for (String subName : subNames) {
        if (subName.contains("(") || subName.contains("<")) {
          // handle anonymous or inner classes
          isSpecial = true;
          break;
        }
        sb.append(subName).append('.');
      }
      if (subNames.length != 0) {
        sb.setLength(sb.length() - 1);
      }

      if (isSpecial) {
        String lastSubName = subNames[subNames.length - 1];
        String[] temp = lastSubName.split(">");
        if (temp.length > 0) {
          String name = temp[temp.length - 1];
          if (!name.contains("$")) {
            // This is an inner class
            String outClass = sb.toString();
            int count;
            final Integer innerClassCount = clsWithInnerCls.get(outClass);
            if (innerClassCount != null) {
              count = innerClassCount + 1;
            } else {
              count = 1;
            }
            clsWithInnerCls.put(outClass, count);
            sb.append(count).append("$");
          }
          sb.append(name);
        }
      }
    } else {
      throw new RuntimeException("Can not convert WALA class name: " + className);
    }
    String ret = sb.toString();
    walaToSootNameTable.put(cl, ret);
    return ret;
  }

  /**
   * Convert className in soot-format to wala-format, e.g.,soot-format: java.lang.String.->
   * wala-format: Ljava/lang/String
   */
  public String convertClassNameFromSoot(String signature) {
    return "L" + signature.replace('.', '/');
  }

  protected void addSootField(SootField field) {
    if (this.sootFields != null) {
      this.sootFields.add(field);
    }
  }

  /*
   *   converts Wala Position to Soots Position
   * */
  public static de.upb.swt.soot.core.model.Position convertPosition(Position instructionPosition) {
    return new de.upb.swt.soot.core.model.Position(
        instructionPosition.getFirstLine(),
        instructionPosition.getFirstCol(),
        instructionPosition.getLastLine(),
        instructionPosition.getLastCol());
  }

  public static StmtPositionInfo convertPositionInfo(
      Position instructionPosition, Position[] operandPosition) {

    if (operandPosition == null) {
      return new StmtPositionInfo(convertPosition(instructionPosition), null);
    }
    de.upb.swt.soot.core.model.Position[] operandPos =
        Arrays.stream(operandPosition)
            .map(
                instrPos ->
                    instrPos == null
                        ? null
                        : new de.upb.swt.soot.core.model.Position(
                            instrPos.getFirstLine(),
                            instrPos.getFirstCol(),
                            instrPos.getLastLine(),
                            instrPos.getLastCol()))
            .toArray(de.upb.swt.soot.core.model.Position[]::new);

    return new StmtPositionInfo(convertPosition(instructionPosition), operandPos);
  }
}
