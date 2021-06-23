package de.upb.swt.soot.java.core;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2018-2020 Andreas Dann, Christian Brüggemann and others
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

import de.upb.swt.soot.core.signatures.*;
import de.upb.swt.soot.java.core.signatures.ModulePackageName;
import de.upb.swt.soot.java.core.signatures.ModuleSignature;
import de.upb.swt.soot.java.core.types.ModuleJavaClassType;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ClassUtils;

public class JavaModuleIdentifierFactory extends JavaIdentifierFactory {

  public static final String MODULE_INFO_FILE = "module-info";

  private static final Map<String, ModuleSignature> modules = new HashMap<>();

  private static final JavaModuleIdentifierFactory INSTANCE = new JavaModuleIdentifierFactory();

  public static JavaModuleIdentifierFactory getInstance() {
    return INSTANCE;
  }

  public static JavaModuleIdentifierFactory getInstance(@Nonnull String module) {
    return getInstance(getModuleSignature(module));
  }

  private static final Map<ModuleSignature, JavaModuleIdentifierFactory>
      moduleIdentifierFactoryWrapper = new HashMap<>();

  public static JavaModuleIdentifierFactory getInstance(@Nonnull ModuleSignature moduleSignature) {
    return moduleIdentifierFactoryWrapper.computeIfAbsent(
        moduleSignature,
        methodSignature -> new JavaModuleIdentifierFactoryWrapper(moduleSignature));
  }

  static {
    /*
     * Represents the unnamed module in Java's module system. Every type that is not defined in any known module but loaded
     * from the classpath is associated with this unnamed module, so as to ensure that every type is associated with a
     * module.
     *
     * <p>{@link ModuleSignature#UNNAMED_MODULE}
     */
    modules.put(ModuleSignature.UNNAMED_MODULE.getModuleName(), ModuleSignature.UNNAMED_MODULE);
  }

  @Override
  public ModuleJavaClassType getClassType(final String className, final String packageName) {
    return getClassType(className, packageName, ModuleSignature.UNNAMED_MODULE.getModuleName());
  }

  @Override
  public ModuleJavaClassType getClassType(String fullyQualyfiedClassNameWithModule) {

    int moduleSplit = fullyQualyfiedClassNameWithModule.indexOf('/');
    String moduleName = null;
    if (moduleSplit >= 0) {
      moduleName = fullyQualyfiedClassNameWithModule.substring(0, moduleSplit);
      fullyQualyfiedClassNameWithModule =
          fullyQualyfiedClassNameWithModule.substring(moduleSplit + 1);
    }

    String className = ClassUtils.getShortClassName(fullyQualyfiedClassNameWithModule);
    String packageName = ClassUtils.getPackageName(fullyQualyfiedClassNameWithModule);

    if (className.equals(MODULE_INFO_FILE)) {
      throw new IllegalArgumentException("module-info is not allowed as classname.");
    }

    if (moduleName == null) {
      return getClassType(className, packageName);
    } else {
      return getClassType(className, packageName, moduleName);
    }
  }

  /**
   * Always creates a new ClassSignature. In opposite to PackageSignatures and ModuleSignatures,
   * ClassSignatures are not cached because the are unique per class, and thus reusing them does not
   * make sense.
   *
   * @param className the simple name of the class
   * @param packageName the declaring package
   * @param moduleName the declaring module
   * @return a ClassSignature for a Java 9 class
   * @throws NullPointerException if the given module name or package name is null. Use the empty
   *     string to denote the unnamed module or the default package.
   */
  public ModuleJavaClassType getClassType(
      final @Nonnull String className,
      final @Nonnull String packageName,
      final @Nonnull String moduleName) {
    ModulePackageName packageIdentifier = getPackageName(packageName, moduleName);
    return new ModuleJavaClassType(className, packageIdentifier);
  }

  public ModuleJavaClassType getClassType(
      final @Nonnull String className,
      final @Nonnull String packageName,
      final @Nonnull ModuleSignature moduleSignature) {
    ModulePackageName packageIdentifier = getPackageName(packageName, moduleSignature);
    return new ModuleJavaClassType(className, packageIdentifier);
  }

  /**
   * Returns a unique ModuleSignature. The methodRef looks up a cache if it already contains a
   * signature with the given module name. If the cache lookup fails a new signature is created.
   * Returns a unique ModuleSignature. The methodRef looks up a cache if it already contains a
   * signature with the given module name. If the cache lookup fails a new signature is created.
   *
   * @param moduleName the module name; Must not be null. Use the empty string for the unnamed
   *     module
   * @return a ModuleSignature
   * @throws NullPointerException if the given module name is null. Use the empty string to denote
   *     the unnamed module.
   */
  public static ModuleSignature getModuleSignature(@Nonnull final String moduleName) {
    ModuleSignature moduleSignature = modules.get(moduleName);
    if (moduleSignature == null) {
      moduleSignature = new ModuleSignature(moduleName);
      modules.put(moduleName, moduleSignature);
    }
    return moduleSignature;
  }

  @Override
  public ModulePackageName getPackageName(@Nonnull final String packageName) {
    return getPackageName(packageName, ModuleSignature.UNNAMED_MODULE.getModuleName());
  }

  /**
   * Returns a unique PackageName. The methodRef looks up a cache if it already contains a signature
   * with the given package and module name. If the cache lookup fails a new signature is created.
   *
   * @param packageName the package name; must not be null use empty string for the default package
   * @param moduleName the module containing the package; must not be null use empty string for the
   *     unnamed module {@link ModuleSignature#UNNAMED_MODULE}
   * @return a ModulePackageName
   * @throws NullPointerException if the given module name or package name is null. Use the empty
   *     string to denote the unnamed module or the default package.
   */
  public ModulePackageName getPackageName(
      @Nonnull final String packageName, @Nonnull final String moduleName) {
    String fqId = moduleName + "." + packageName;
    ModulePackageName packageSignature = (ModulePackageName) packages.get(fqId);
    if (packageSignature == null) {
      ModuleSignature moduleSignature = getModuleSignature(moduleName);
      packageSignature = new ModulePackageName(packageName, moduleSignature);
      packages.put(fqId, packageSignature);
    }
    return packageSignature;
  }

  public ModulePackageName getPackageName(
      @Nonnull final String packageName, @Nonnull final ModuleSignature moduleSignature) {
    String fqId = moduleSignature.getModuleName() + "." + packageName;
    ModulePackageName packageSignature = (ModulePackageName) packages.get(fqId);
    if (packageSignature == null) {
      packageSignature = new ModulePackageName(packageName, moduleSignature);
      packages.put(fqId, packageSignature);
    }
    return packageSignature;
  }

  /** Wrapper which refers to a given ModuleSignature when building stuff */
  private static class JavaModuleIdentifierFactoryWrapper extends JavaModuleIdentifierFactory {

    @Nonnull private final ModuleSignature moduleSignature;

    private JavaModuleIdentifierFactoryWrapper(@Nonnull ModuleSignature moduleSignature) {
      this.moduleSignature = moduleSignature;
    }

    @Override
    public ModuleJavaClassType getClassType(String fullyQualifiedClassName) {
      int moduleSplitPos = fullyQualifiedClassName.indexOf('/');
      ModuleSignature moduleSig;
      if (moduleSplitPos >= 0) {
        String moduleName = fullyQualifiedClassName.substring(0, moduleSplitPos);
        fullyQualifiedClassName = fullyQualifiedClassName.substring(moduleSplitPos + 1);
        moduleSig = getModuleSignature(moduleName);
      } else {
        moduleSig = moduleSignature;
      }

      String className = ClassUtils.getShortClassName(fullyQualifiedClassName);
      String packageName = ClassUtils.getPackageName(fullyQualifiedClassName);
      return getClassType(className, packageName, moduleSig);
    }

    @Override
    public MethodSignature getMethodSignature(
        String methodName,
        String fullyQualifiedNameDeclClass,
        String fqReturnType,
        List<String> parameters) {
      return super.getMethodSignature(
          methodName, fullyQualifiedNameDeclClass, fqReturnType, parameters);
    }

    @Override
    @Nonnull
    public ModuleJavaClassType fromPath(@Nonnull Path file) {
      String fullyQualifiedName = FilenameUtils.removeExtension(file.toString()).replace('/', '.');
      String packageName = "";
      int index = fullyQualifiedName.lastIndexOf(".");
      String className = fullyQualifiedName;
      if (index > 0) {
        className = fullyQualifiedName.substring(index);
        packageName = fullyQualifiedName.substring(0, index);
      }
      return getClassType(className, packageName, moduleSignature);
    }
  }
}
