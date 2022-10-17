package de.upb.swt.soot.java.bytecode.inputlocation;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import de.upb.swt.soot.core.IdentifierFactory;
import de.upb.swt.soot.core.frontend.AbstractClassSource;
import de.upb.swt.soot.core.frontend.ClassProvider;
import de.upb.swt.soot.core.inputlocation.AnalysisInputLocation;
import de.upb.swt.soot.core.inputlocation.FileType;
import de.upb.swt.soot.core.model.SourceType;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.util.PathUtils;
import de.upb.swt.soot.core.util.StreamUtils;
import de.upb.swt.soot.core.views.View;
import de.upb.swt.soot.java.bytecode.frontend.AsmJavaClassProvider;
import de.upb.swt.soot.java.bytecode.frontend.AsmModuleSource;
import de.upb.swt.soot.java.core.JavaModuleIdentifierFactory;
import de.upb.swt.soot.java.core.JavaModuleInfo;
import de.upb.swt.soot.java.core.JavaSootClass;
import de.upb.swt.soot.java.core.ModuleInfoAnalysisInputLocation;
import de.upb.swt.soot.java.core.signatures.ModuleSignature;
import de.upb.swt.soot.java.core.types.JavaClassType;
import de.upb.swt.soot.java.core.types.ModuleJavaClassType;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/*-
 * #%L
 * Soot
 * %%
 * Copyright (C) 2018-2020 Manuel Benz, Christian Brüggemann, Markus Schmidt and others
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

/**
 * Base class for {@link PathBasedAnalysisInputLocation}s that can be located by a {@link Path}
 * object.
 *
 * @author Manuel Benz created on 22.05.18
 * @author Kaustubh Kelkar updated on 30.07.2020
 */
public class PathBasedAnalysisInputLocation implements AnalysisInputLocation<JavaSootClass> {
  protected Path path;

  /**
   * Variable to track if user has specified the SourceType. By default, it will be set to false.
   */
  private SourceType srcType = null;

  /**
   * Variable to store AnalysisInputLocation, which can be DirectoryBasedAnalysisInputLocation,
   * WarArchiveAnalysisInputLocation, MultiReleaseJarAnalysisInputLocation,
   * ArchiveBasedAnalysisInputLocation
   */
  PathBasedAnalysisInputLocation pathBasedAnalysisInputLocationObj;

  public PathBasedAnalysisInputLocation getPathBasedAnalysisInputLocationObj() {
    return pathBasedAnalysisInputLocationObj;
  }

  public PathBasedAnalysisInputLocation(@Nonnull Path path) {
    this.path = path;
  }

  public PathBasedAnalysisInputLocation(@Nonnull Path path, @Nullable SourceType srcType) {
    if (Files.isDirectory(path)) {
      pathBasedAnalysisInputLocationObj = new DirectoryBasedAnalysisInputLocation(path, srcType);
    } else if (PathUtils.isArchive(path)) {
      if (PathUtils.hasExtension(path, FileType.WAR)) {
        pathBasedAnalysisInputLocationObj = new WarArchiveAnalysisInputLocation(path, srcType);
      } else if (isMultiReleaseJar(path)) { // check if mainfest contains multi release flag
        pathBasedAnalysisInputLocationObj = new MultiReleaseJarAnalysisInputLocation(path, srcType);
      } else {
        pathBasedAnalysisInputLocationObj = new ArchiveBasedAnalysisInputLocation(path, srcType);
      }
    } else {
      throw new IllegalArgumentException(
          "Path '"
              + path.toAbsolutePath()
              + "' has to be pointing to the root of a class container, e.g. directory, jar, zip, apk, war etc.");
    }
  }

  /**
   * The method sets the value of the variable srcType.
   *
   * @param srcType the source type for the path can be Library, Application, Phantom.
   */
  public void setSpecifiedAsBuiltInByUser(@Nonnull SourceType srcType) {
    this.srcType = srcType;
  }

  /**
   * Create or find a class source for a given type.
   *
   * @param type The type of the class to be found.
   * @param view
   * @return The source entry for that class.
   */
  @Nonnull
  @Override
  public Optional<? extends AbstractClassSource<JavaSootClass>> getClassSource(
      @Nonnull ClassType type, @Nonnull View<?> view) {
    return pathBasedAnalysisInputLocationObj.getClassSource(type, view);
  }

  /**
   * Scan the input location and create ClassSources for every compilation / interpretation unit.
   *
   * @param view
   * @return The source entries.
   */
  @Nonnull
  @Override
  public Collection<? extends AbstractClassSource<JavaSootClass>> getClassSources(
      @Nonnull View<?> view) {
    return pathBasedAnalysisInputLocationObj.getClassSources(view);
  }

  @Override
  public SourceType getSourceType() {
    return srcType;
  }

  private static boolean isMultiReleaseJar(Path path) {
    try {
      FileInputStream inputStream = new FileInputStream(path.toFile());
      JarInputStream jarStream = new JarInputStream(inputStream);
      Manifest mf = jarStream.getManifest();

      if (mf == null) {
        return false;
      }

      Attributes attributes = mf.getMainAttributes();

      String value = attributes.getValue("Multi-Release");

      return Boolean.parseBoolean(value);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return false;
  }

  @Nonnull
  Collection<? extends AbstractClassSource<JavaSootClass>> walkDirectory(
      @Nonnull Path dirPath,
      @Nonnull IdentifierFactory factory,
      @Nonnull ClassProvider<JavaSootClass> classProvider) {
    try {
      final FileType handledFileType = classProvider.getHandledFileType();
      final String moduleInfoFilename = JavaModuleIdentifierFactory.MODULE_INFO_FILE + ".class";
      return Files.walk(dirPath)
          .filter(
              filePath ->
                  PathUtils.hasExtension(filePath, handledFileType)
                      && !filePath.toString().endsWith(moduleInfoFilename))
          .flatMap(
              p ->
                  StreamUtils.optionalToStream(
                      Optional.of(
                          classProvider.createClassSource(this, p, factory.fromPath(dirPath, p)))))
          .collect(Collectors.toList());

    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Nonnull
  protected Optional<? extends AbstractClassSource<JavaSootClass>> getClassSourceInternal(
      @Nonnull JavaClassType signature,
      @Nonnull Path path,
      @Nonnull ClassProvider<JavaSootClass> classProvider) {

    Path pathToClass =
        path.resolve(
            path.getFileSystem()
                .getPath(
                    signature.getFullyQualifiedName().replace('.', '/')
                        + "."
                        + classProvider.getHandledFileType().getExtension()));

    if (!Files.exists(pathToClass)) {
      return Optional.empty();
    }

    return Optional.of(classProvider.createClassSource(this, pathToClass, signature));
  }

  private static class DirectoryBasedAnalysisInputLocation extends PathBasedAnalysisInputLocation {

    private DirectoryBasedAnalysisInputLocation(@Nonnull Path path, @Nullable SourceType srcType) {
      super(path);
      super.setSpecifiedAsBuiltInByUser(srcType);
    }

    @Override
    @Nonnull
    public Collection<? extends AbstractClassSource<JavaSootClass>> getClassSources(
        @Nonnull View<?> view) {
      return walkDirectory(
          path,
          view.getIdentifierFactory(),
          new AsmJavaClassProvider(((View<JavaSootClass>) view).getBodyInterceptors(this)));
    }

    @Override
    @Nonnull
    public Optional<? extends AbstractClassSource<JavaSootClass>> getClassSource(
        @Nonnull ClassType type, @Nonnull View<?> view) {
      return getClassSourceInternal(
          (JavaClassType) type,
          path,
          new AsmJavaClassProvider(((View<JavaSootClass>) view).getBodyInterceptors(this)));
    }
  }

  public static class MultiReleaseJarAnalysisInputLocation extends ArchiveBasedAnalysisInputLocation
      implements ModuleInfoAnalysisInputLocation {

    @Nonnull private final int[] availableVersions;

    @Nonnull
    private final Map<Integer, Map<ModuleSignature, JavaModuleInfo>> moduleInfoMap =
        new HashMap<>();

    @Nonnull
    private final Map<Integer, List<AnalysisInputLocation<JavaSootClass>>> inputLocations =
        new HashMap<>();

    @Nonnull
    private final List<AnalysisInputLocation<JavaSootClass>> baseInputLocations = new ArrayList<>();

    boolean isResolved = false;

    private MultiReleaseJarAnalysisInputLocation(@Nonnull Path path, @Nullable SourceType srcType) {
      super(path, srcType);

      int[] tmp;
      try {
        FileSystem fs = fileSystemCache.get(path);
        final Path archiveRoot = fs.getPath("/");
        tmp =
            Files.list(archiveRoot.getFileSystem().getPath("/META-INF/versions/"))
                .map(dir -> dir.getFileName().toString().replace("/", ""))
                .mapToInt(Integer::new)
                .sorted()
                .toArray();
      } catch (IOException | ExecutionException e) {
        e.printStackTrace();
        tmp = new int[] {};
      }
      availableVersions = tmp;

      discoverInputLocations(srcType);
    }

    /** Discovers all input locations for different java versions in this multi release jar */
    private void discoverInputLocations(@Nullable SourceType srcType) {
      FileSystem fs = null;
      try {
        fs = fileSystemCache.get(path);
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
      final Path archiveRoot = fs.getPath("/");
      final String moduleInfoFilename = JavaModuleIdentifierFactory.MODULE_INFO_FILE + ".class";

      baseInputLocations.add(new PathBasedAnalysisInputLocation(archiveRoot, srcType));

      String sep = archiveRoot.getFileSystem().getSeparator();

      if (!isResolved) {

        for (int i = availableVersions.length - 1; i >= 0; i--) {
          inputLocations.put(availableVersions[i], new ArrayList<>());

          final Path versionRoot =
              archiveRoot
                  .getFileSystem()
                  .getPath("/META-INF/versions/" + availableVersions[i] + sep);

          // only versions >= 9 support java modules
          if (availableVersions[i] > 8) {
            moduleInfoMap.put(availableVersions[i], new HashMap<>());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionRoot)) {
              for (Path entry : stream) {

                Path mi = path.resolve(moduleInfoFilename);

                if (Files.exists(mi)) {
                  JavaModuleInfo moduleInfo = new AsmModuleSource(mi);
                  ModuleSignature moduleSignature = moduleInfo.getModuleSignature();
                  JavaModulePathAnalysisInputLocation inputLocation =
                      new JavaModulePathAnalysisInputLocation(
                          versionRoot.toString(), versionRoot.getFileSystem());

                  inputLocations.get(availableVersions[i]).add(inputLocation);
                  moduleInfoMap.get(availableVersions[i]).put(moduleSignature, moduleInfo);
                }

                if (Files.isDirectory(entry)) {
                  mi = versionRoot.resolve(moduleInfoFilename);

                  if (Files.exists(mi)) {
                    JavaModuleInfo moduleInfo = new AsmModuleSource(mi);
                    ModuleSignature moduleSignature = moduleInfo.getModuleSignature();
                    JavaModulePathAnalysisInputLocation inputLocation =
                        new JavaModulePathAnalysisInputLocation(
                            versionRoot.toString(), versionRoot.getFileSystem());

                    inputLocations.get(availableVersions[i]).add(inputLocation);
                    moduleInfoMap.get(availableVersions[i]).put(moduleSignature, moduleInfo);
                  }
                  // else TODO [bh] can we have automatic modules here?
                }
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          }

          // if there was no module or the version is not > 8, we just add a directory based input
          // location
          if (inputLocations.get(availableVersions[i]).size() == 0) {
            inputLocations
                .get(availableVersions[i])
                .add(new PathBasedAnalysisInputLocation(versionRoot, srcType));
          }
        }
      }

      isResolved = true;
    }

    @Override
    @Nonnull
    public Optional<? extends AbstractClassSource<JavaSootClass>> getClassSource(
        @Nonnull ClassType type, @Nonnull View<?> view) {

      Collection<AnalysisInputLocation<JavaSootClass>> il =
          getBestMatchingInputLocationsRaw(view.getProject().getLanguage().getVersion());

      Collection<AnalysisInputLocation<JavaSootClass>> baseIl = getBaseInputLocations();

      if (type instanceof ModuleJavaClassType) {
        il =
            il.stream()
                .filter(location -> location instanceof ModuleInfoAnalysisInputLocation)
                .collect(Collectors.toList());
        baseIl =
            baseIl.stream()
                .filter(location -> location instanceof ModuleInfoAnalysisInputLocation)
                .collect(Collectors.toList());
      } else {
        il =
            il.stream()
                .filter(location -> !(location instanceof ModuleInfoAnalysisInputLocation))
                .collect(Collectors.toList());
        baseIl =
            baseIl.stream()
                .filter(location -> !(location instanceof ModuleInfoAnalysisInputLocation))
                .collect(Collectors.toList());
      }

      Optional<? extends AbstractClassSource<JavaSootClass>> foundClass =
          il.stream()
              .map(location -> location.getClassSource(type, view))
              .filter(Optional::isPresent)
              .limit(1)
              .map(Optional::get)
              .findAny();

      if (foundClass.isPresent()) {
        return foundClass;
      } else {
        return baseIl.stream()
            .map(location -> location.getClassSource(type, view))
            .filter(Optional::isPresent)
            .limit(1)
            .map(Optional::get)
            .findAny();
      }
    }

    @Nonnull
    @Override
    public Collection<? extends AbstractClassSource<JavaSootClass>> getModulesClassSources(
        @Nonnull ModuleSignature moduleSignature, @Nonnull View<?> view) {
      return inputLocations.get(view.getProject().getLanguage().getVersion()).stream()
          .filter(location -> location instanceof ModuleInfoAnalysisInputLocation)
          .map(
              location ->
                  ((ModuleInfoAnalysisInputLocation) location)
                      .getModulesClassSources(moduleSignature, view))
          .flatMap(Collection::stream)
          .collect(Collectors.toList());
    }

    /**
     * Returns the best matching input locations or the base input location.
     *
     * @param javaVersion version to find best match to
     * @return best match or base input locations
     */
    private Collection<AnalysisInputLocation<JavaSootClass>> getBestMatchingInputLocationsRaw(
        int javaVersion) {
      for (int i = availableVersions.length - 1; i >= 0; i--) {

        if (availableVersions[i] > javaVersion) continue;

        return new ArrayList<>(inputLocations.get(availableVersions[i]));
      }

      return getBaseInputLocations();
    }

    private Collection<AnalysisInputLocation<JavaSootClass>> getBaseInputLocations() {
      return baseInputLocations;
    }

    @Override
    @Nonnull
    public Collection<? extends AbstractClassSource<JavaSootClass>> getClassSources(
        @Nonnull View<?> view) {
      Collection<AnalysisInputLocation<JavaSootClass>> il =
          getBestMatchingInputLocationsRaw(view.getProject().getLanguage().getVersion());

      Collection<AbstractClassSource<JavaSootClass>> result =
          il.stream()
              .map(location -> location.getClassSources(view))
              .flatMap(Collection::stream)
              .collect(Collectors.toList());

      if (il != getBaseInputLocations()) {

        Collection<AbstractClassSource<JavaSootClass>> baseSources =
            getBaseInputLocations().stream()
                .map(location -> location.getClassSources(view))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        baseSources.forEach(
            cs -> {
              // do not add duplicate class sources
              if (result.stream()
                  .noneMatch(
                      bestMatchCS ->
                          bestMatchCS
                              .getClassType()
                              .getFullyQualifiedName()
                              .equals(cs.getClassType().getFullyQualifiedName()))) {
                result.add(cs);
              }
            });
      }

      return result;
    }

    @Nonnull
    @Override
    public Optional<JavaModuleInfo> getModuleInfo(ModuleSignature sig, View<?> view) {
      return Optional.ofNullable(
          moduleInfoMap.get(view.getProject().getLanguage().getVersion()).get(sig));
    }

    @Nonnull
    @Override
    public Set<ModuleSignature> getModules(View<?> view) {
      return inputLocations.get(view.getProject().getLanguage().getVersion()).stream()
          .filter(e -> e instanceof ModuleInfoAnalysisInputLocation)
          .map(e -> ((ModuleInfoAnalysisInputLocation) e).getModules(view))
          .flatMap(Set::stream)
          .collect(Collectors.toSet());
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof PathBasedAnalysisInputLocation)) {
        return false;
      }
      return path.equals(((PathBasedAnalysisInputLocation) o).path);
    }

    @Override
    public int hashCode() {
      return path.hashCode();
    }
  }

  private static class ArchiveBasedAnalysisInputLocation extends PathBasedAnalysisInputLocation {

    // We cache the FileSystem instances as their creation is expensive.
    // The Guava Cache is thread-safe (see JavaDoc of LoadingCache) hence this
    // cache can be safely shared in a static variable.
    protected static final LoadingCache<Path, FileSystem> fileSystemCache =
        CacheBuilder.newBuilder()
            .removalListener(
                (RemovalNotification<Path, FileSystem> removalNotification) -> {
                  try {
                    removalNotification.getValue().close();
                  } catch (IOException e) {
                    throw new RuntimeException(
                        "Could not close file system of " + removalNotification.getKey(), e);
                  }
                })
            .expireAfterAccess(1, TimeUnit.SECONDS)
            .build(
                CacheLoader.from(
                    path -> {
                      try {
                        return FileSystems.newFileSystem(
                            Objects.requireNonNull(path), (ClassLoader) null);
                      } catch (IOException e) {
                        throw new RuntimeException("Could not open file system of " + path, e);
                      }
                    }));

    private ArchiveBasedAnalysisInputLocation(@Nonnull Path path, @Nullable SourceType srcType) {
      super(path);
      super.setSpecifiedAsBuiltInByUser(srcType);
    }

    @Override
    @Nonnull
    public Optional<? extends AbstractClassSource<JavaSootClass>> getClassSource(
        @Nonnull ClassType type, @Nonnull View<?> view) {
      try {
        FileSystem fs = fileSystemCache.get(path);
        final Path archiveRoot = fs.getPath("/");
        return getClassSourceInternal(
            (JavaClassType) type,
            archiveRoot,
            new AsmJavaClassProvider(((View<JavaSootClass>) view).getBodyInterceptors(this)));
      } catch (ExecutionException e) {
        throw new RuntimeException("Failed to retrieve file system from cache for " + path, e);
      }
    }

    @Override
    @Nonnull
    public Collection<? extends AbstractClassSource<JavaSootClass>> getClassSources(
        @Nonnull View<?> view) {
      // we don't use the filesystem cache here as it could close the filesystem after the timeout
      // while we are still iterating
      try (FileSystem fs = FileSystems.newFileSystem(path, null)) {
        final Path archiveRoot = fs.getPath("/");
        return walkDirectory(
            archiveRoot,
            view.getProject().getIdentifierFactory(),
            new AsmJavaClassProvider(((View<JavaSootClass>) view).getBodyInterceptors(this)));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final class WarArchiveAnalysisInputLocation
      extends DirectoryBasedAnalysisInputLocation {
    public List<AnalysisInputLocation<JavaSootClass>> containedInputLocations = new ArrayList<>();
    public static int maxAllowedBytesToExtract =
        1024 * 1024 * 500; // limit of extracted file size to protect against archive bombs

    private WarArchiveAnalysisInputLocation(@Nonnull Path warPath, @Nullable SourceType srcType) {
      super(
          Paths.get(
              System.getProperty("java.io.tmpdir")
                  + File.separator
                  + "sootOutput"
                  + "-war"
                  + warPath.hashCode()
                  + "/"),
          srcType);
      extractWarFile(warPath, path);

      Path webInfPath = path.resolve("WEB-INF");
      // directorystructre as specified in SRV.9.5 of
      // https://download.oracle.com/otn-pub/jcp/servlet-2.4-fr-spec-oth-JSpec/servlet-2_4-fr-spec.pdf?AuthParam=1625059899_16c705c72f7db7f85a8a7926558701fe
      Path classDir = webInfPath.resolve("classes");
      if (Files.exists(classDir)) {
        containedInputLocations.add(new DirectoryBasedAnalysisInputLocation(classDir, srcType));
      }

      Path libDir = webInfPath.resolve("lib");
      if (Files.exists(libDir)) {
        try {
          Files.walk(libDir)
              .filter(f -> PathUtils.hasExtension(f, FileType.JAR))
              .forEach(
                  f ->
                      containedInputLocations.add(
                          new ArchiveBasedAnalysisInputLocation(f, srcType)));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    @Nonnull
    public Collection<? extends AbstractClassSource<JavaSootClass>> getClassSources(
        @Nonnull View<?> view) {

      Set<AbstractClassSource<JavaSootClass>> foundClasses = new HashSet<>();

      for (AnalysisInputLocation<JavaSootClass> inputLoc : containedInputLocations) {
        foundClasses.addAll(inputLoc.getClassSources(view));
      }
      return foundClasses;
    }

    @Override
    @Nonnull
    public Optional<? extends AbstractClassSource<JavaSootClass>> getClassSource(
        @Nonnull ClassType type, @Nonnull View<?> view) {

      for (AnalysisInputLocation<JavaSootClass> inputLocation : containedInputLocations) {
        final Optional<? extends AbstractClassSource<JavaSootClass>> classSource =
            inputLocation.getClassSource(type, view);
        if (classSource.isPresent()) {
          return classSource;
        }
      }

      return Optional.empty();
    }

    /**
     * Extracts the war file at the temporary location to analyze underlying class and jar files
     *
     * <p>[ms] hint: extracting is necessary to access nested (zip)filesystems with java8/java9
     * runtime - nested (zip)filesystems would work with java11 runtime (maybe java10)
     *
     * @param warFilePath The path to war file to be extracted
     */
    protected void extractWarFile(Path warFilePath, final Path destDirectory) {
      int extractedSize = 0;
      try {
        File dest = destDirectory.toFile();
        if (!dest.exists()) {
          if (!dest.mkdir()) {
            throw new RuntimeException(
                "Could not create the directory to extract Warfile: " + destDirectory);
          }
          dest.deleteOnExit();
        }

        ZipInputStream zis = new ZipInputStream(new FileInputStream(warFilePath.toString()));
        ZipEntry zipEntry;
        while ((zipEntry = zis.getNextEntry()) != null) {
          Path filepath = destDirectory.resolve(zipEntry.getName());
          final File file = filepath.toFile();

          file.deleteOnExit();
          if (zipEntry.isDirectory()) {
            file.mkdir();
          } else {
            byte[] incomingValues = new byte[4096];
            int readBytesZip;
            if (file.exists()) {
              // compare contents -> does it contain the extracted war already?
              int readBytesExistingFile;
              final BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
              byte[] bisBuf = new byte[4096];
              while ((readBytesZip = zis.read(incomingValues)) != -1) {
                if (extractedSize > maxAllowedBytesToExtract) {
                  throw new RuntimeException(
                      "The extracted warfile exceeds the size of "
                          + maxAllowedBytesToExtract
                          + " byte. Either the file is a big archive (-> increase PathBasedAnalysisInputLocation.WarArchiveInputLocation.maxAllowedBytesToExtract) or maybe it contains an archive bomb.");
                }
                readBytesExistingFile = bis.read(bisBuf, 0, readBytesZip);
                if (readBytesExistingFile != readBytesZip) {
                  throw new RuntimeException(
                      "Can't extract File \""
                          + file
                          + "\" as it already exists and has a different size.");
                } else if (!Arrays.equals(bisBuf, incomingValues)) {
                  throw new RuntimeException(
                      "Can't extract File \""
                          + file
                          + "\" as it already exists and has a different content which we can't override.");
                }
                extractedSize += readBytesZip;
              }

            } else {
              BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
              while ((readBytesZip = zis.read(incomingValues)) != -1) {
                if (extractedSize > maxAllowedBytesToExtract) {
                  throw new RuntimeException(
                      "The extracted warfile exceeds the size of "
                          + maxAllowedBytesToExtract
                          + " byte. Either the file is a big archive or maybe it contains an archive bomb.");
                }
                bos.write(incomingValues, 0, readBytesZip);
                extractedSize += readBytesZip;
              }
              bos.close();
            }
          }
          zis.closeEntry();
        }

      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Parses the web.xml file to search for the servlet-class classes in the extracted directory
     * after the war file is extracted
     *
     * <p>[ms] helps to set entrypoints for analyses automatically (later)
     *
     * @param extractedWARPath The path where the war file is extracted Adds the classes associated
     *     to servlet-class in a {@link ArrayList} of {@link String}
     */
    @Nonnull
    public List<String> retrieveServletClasses(String extractedWARPath) {
      List<String> classesInXML = new ArrayList<>();
      try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new File(extractedWARPath + "/WEB-INF/web.xml"));
        document.getDocumentElement().normalize();
        NodeList nList = document.getElementsByTagName("servlet");
        for (int temp = 0; temp < nList.getLength(); temp++) {
          Node node = nList.item(temp);
          if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element eElement = (Element) node;
            classesInXML.add(
                eElement.getElementsByTagName("servlet-class").item(0).getTextContent());
          }
        }
      } catch (ParserConfigurationException | SAXException | IOException e) {
        throw new RuntimeException(e);
      }
      return classesInXML;
    }
  }
}
