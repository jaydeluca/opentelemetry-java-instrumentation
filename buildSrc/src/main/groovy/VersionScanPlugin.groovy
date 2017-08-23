import com.google.common.collect.Sets
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.impl.DefaultServiceLocator
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.version.Version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

import java.util.concurrent.atomic.AtomicReference
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Version syntax: https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html
 */
class VersionScanPlugin implements Plugin<Project> {

  void apply(Project project) {
    RepositorySystem system = newRepositorySystem()
    RepositorySystemSession session = newRepositorySystemSession(system)

    project.extensions.create("versionScan", VersionScanExtension)
    project.task('scanVersions') {
      description = "Queries for all versions of configured modules and finds key classes"
    }

    if (!project.gradle.startParameter.taskNames.contains('scanVersions')) {
      return
    }
    println "Adding scan tasks for $project"

    Set<String> allInclude = Sets.newConcurrentHashSet()
    Set<String> allExclude = Sets.newConcurrentHashSet()
    AtomicReference<Set<String>> keyPresent = new AtomicReference(Collections.emptySet())
    AtomicReference<Set<String>> keyMissing = new AtomicReference(Collections.emptySet())
    def scanVersionsReport = project.task('scanVersionsReport') {
      description = "Prints the result of the scanVersions task"
      doLast {
        def inCommonPresent = keyPresent.get().size()
        def inCommonMissing = keyMissing.get().size()
        keyPresent.get().removeAll(allExclude)
        keyMissing.get().removeAll(allInclude)
        def exclusivePresent = keyPresent.get().size()
        def exclusiveMissing = keyMissing.get().size()

        if (project.hasProperty("showClasses")) {
          println "keyPresent: ${allInclude.size()}->$inCommonPresent->$exclusivePresent - ${keyPresent.get()}"
          println "+++++++++++++++++++++"
          println "keyMissing: ${allExclude.size()}->$inCommonMissing->$exclusiveMissing - ${keyMissing.get()}"
        } else {
          println "keyPresent: $inCommonPresent->$exclusivePresent"
          println "keyMissing: $inCommonMissing->$exclusiveMissing"
        }
      }
    }
    project.tasks.scanVersions.finalizedBy(scanVersionsReport)

    project.repositories {
      mavenCentral()
      jcenter()
    }

    project.afterEvaluate {
      String group = project.versionScan.group
      String module = project.versionScan.module
      String versions = project.versionScan.versions
      Artifact artifact = new DefaultArtifact(group, module, "jar", versions)
      Artifact allVersions = new DefaultArtifact(group, module, "jar", "(,)")

      String legacyGroup = project.versionScan.legacyGroup == null ? group : project.versionScan.legacyGroup
      String legacyModule = project.versionScan.legacyModule == null ? module : project.versionScan.legacyModule
      Artifact allLegacyVersions = new DefaultArtifact(legacyGroup, legacyModule, "jar", "(,)")

      VersionRangeRequest rangeRequest = new VersionRangeRequest()
      rangeRequest.setRepositories(newRepositories(system, session))

      rangeRequest.setArtifact(artifact)
      VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest)
      rangeRequest.setArtifact(allVersions)
      VersionRangeResult allResult = system.resolveVersionRange(session, rangeRequest)

      def includeVersionSet = Sets.newHashSet(filter(rangeResult.versions).collect {
        new DefaultArtifact(group, module, "jar", it.toString())
      })
      def excludeVersionSet = Sets.newHashSet(filter(allResult.versions).collect {
        new DefaultArtifact(group, module, "jar", it.toString())
      })
      excludeVersionSet.removeAll(includeVersionSet)

      if (allVersions != allLegacyVersions) {
//        println "Adding legacy versions $allLegacyVersions"
        rangeRequest.setArtifact(allLegacyVersions)
        VersionRangeResult allLegacyResult = system.resolveVersionRange(session, rangeRequest)
        def legacyVersions = filter(allLegacyResult.versions)
//        println "Found ${legacyVersions.size()} legacy versions for $legacyGroup:$legacyModule"
        excludeVersionSet.addAll(legacyVersions.collect {
          new DefaultArtifact(legacyGroup, legacyModule, "jar", it.toString())
        })
      }

      if (excludeVersionSet.empty) {
        println "Found ${includeVersionSet.size()} versions, but none to exclude. Skipping..."
        scanVersionsReport.enabled = false
        return
      }

//      println "Scanning ${includeVersionSet.size()} included and ${excludeVersionSet.size()} excluded versions.  Included: ${includeVersionSet.collect { it.version }}}"

      includeVersionSet.each { version ->
        addScanTask("Include", new DefaultArtifact(version.groupId, version.artifactId, "jar", version.version), keyPresent, allInclude, project)
      }

      excludeVersionSet.each { version ->
        addScanTask("Exclude", new DefaultArtifact(version.groupId, version.artifactId, "jar", version.version), keyMissing, allExclude, project)
      }
    }
  }

  def addScanTask(String label, Artifact artifact, AtomicReference<Set<String>> keyIdentifiers, Set<String> allIdentifiers, Project project) {
    def name = "scanVersion$label-$artifact.groupId-$artifact.artifactId-$artifact.version"
    def config = project.configurations.create(name)
    config.dependencies.add(project.dependencies.create("$artifact.groupId:$artifact.artifactId:$artifact.version") {
      transitive = project.versionScan.scanDependencies
    })

    def task = project.task(name) {
      doLast {
        Set<String> contentSet = Sets.newConcurrentHashSet()
        project.configurations.getByName(name).resolvedConfiguration.files.each { jarFile ->
          def jar = new JarFile(jarFile)
          for (jarEntry in jar.entries()) {
            if (jarEntry.name.endsWith(".class")) {
              if (project.versionScan.scanMethods) {
                findMethodNames(jar, jarEntry).each {
                  contentSet.add("$jarEntry.name|$it")
                }
              } else {
                contentSet.add("$jarEntry.name")
              }
            }
          }
        }
        allIdentifiers.addAll(contentSet)

        if (!keyIdentifiers.compareAndSet(Collections.emptySet(), contentSet)) {
          def intersection = Sets.intersection(keyIdentifiers.get(), contentSet)
          keyIdentifiers.get().retainAll(intersection)
        }
      }
    }
    project.tasks.scanVersions.finalizedBy(task)
    project.tasks.scanVersionsReport.mustRunAfter(task)
  }

  def filter(List<Version> list) {
    list.removeIf {
      def version = it.toString().toLowerCase()
      return version.contains("rc") ||
        version.contains("alpha") ||
        version.contains("beta") ||
        version.contains("-b") ||
        version.contains(".m") ||
        version.contains("-dev")
    }
    return list
  }

  def findMethodNames(JarFile jar, JarEntry entry) {
    def stream = jar.getInputStream(entry)

    def classNode = new ClassNode()
    def cr = new ClassReader(stream)
    cr.accept(classNode, 0)

    return classNode.methods.collect { it.name }
  }

  RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator()
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class)
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class)

    return locator.getService(RepositorySystem.class)
  }

  DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession()

    def tempDir = File.createTempDir()
    tempDir.deleteOnExit()
    LocalRepository localRepo = new LocalRepository(tempDir)
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo))

    return session
  }

  static List<RemoteRepository> newRepositories(RepositorySystem system, RepositorySystemSession session) {
    return new ArrayList<RemoteRepository>(Arrays.asList(newCentralRepository()))
  }

  private static RemoteRepository newCentralRepository() {
    return new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").build()
  }
}
