package mill.scalalib

import mill._

import scala.util.Properties

/**
 * Provides a [[NativeImageModule.nativeImage task]] to build a native executable using [[https://www.graalvm.org/ Graal VM]].
 *
 * It is recommended to specify a custom JDK that includes the `native-image` Tool.
 * {{{
 * trait AppModule extends NativeImageModule {
 *   def zincWorker = ModuleRef(ZincWorkerGraalvm)
 *
 *   object ZincWorkerGraalvm extends ZincWorkerModule {
 *     def jvmId = "graalvm-community:23.0.1"
 *   }
 * }
 * }}}
 */
@mill.api.experimental
trait NativeImageModule extends RunModule with WithZincWorker {

  /**
   * [[https://www.graalvm.org/latest/reference-manual/native-image/#from-a-class Builds a native executable]] for this
   * module with [[finalMainClass]] as the application entry point.
   *
   * @param args Additional options for the `native-image` Tool. Use for
   *             - passing debug options
   *             - passing target specific options
   */
  def nativeImage: T[PathRef] = Task {
    val dest = T.dest
    val executableName = "native-image"

    val classpath0 = nativeImageClasspath().iterator.map(_.path).mkString(java.io.File.pathSeparator)
    println("$$$ Native Image Classpath:")
    println(classpath0)
    os.write(dest / "native-image-classpath.txt", classpath0)

//    val classpath = nativeImageClasspath().iterator.map(_.path).mkString(" ")
//    val manifestContent = s"""Manifest-Version: 1.0
//                             |Class-Path: $classpath
//                             |""".stripMargin

//    val classpathEntries = nativeImageClasspath().iterator.map(_.path.toString)
//    val manifestClasspath = classpathEntries.grouped(3).map(_.mkString(" ")).mkString("\n Class-Path: ")

    val classpathEntries = nativeImageClasspath().iterator.map(_.path.toString)
    val manifestClasspath = classpathEntries.toList match {
      case first :: rest =>
        (first :: rest.map("  " + _)).mkString("\n")
      case Nil => ""
    }

    val manifestContent = s"""Manifest-Version: 1.0
                             |Class-Path: $manifestClasspath
                             |  scala-library-2.13.11.jar
                             |""".stripMargin
    val scalajar = os.Path("C:\\Users\\runneradmin\\AppData\\Local\\Coursier\\cache\\v1\\https\\repo1.maven.org\\maven2\\org\\scala-lang\\scala-library\\2.13.11\\scala-library-2.13.11.jar")
    os.copy(scalajar, dest / "scala-library-2.13.11.jar")

    val manifestPath = dest / "META-INF" / "MANIFEST.MF"
    os.makeDir.all(manifestPath / os.up)
    os.write(manifestPath, manifestContent)

    val pathingJar = dest / "classpath.jar"
    os.proc("jar", "cfm", pathingJar, manifestPath).call()

    val command = Seq.newBuilder[String]
      .+=(nativeImageTool().path.toString)
      .++=(nativeImageOptions())
      .+=("-cp")
      .+=(pathingJar.toString())
      .+=(finalMainClass())
      .+=(executableName)
      .result()
    os.proc(command).call(cwd = dest, stdout = os.Inherit)
    PathRef(dest / (if (mill.main.client.Util.isWindows) s"${executableName}.exe" else executableName))
  }

  /**
   * The classpath to use to generate the native image. Defaults to [[runClasspath]].
   */
  def nativeImageClasspath: T[Seq[PathRef]] = Task {
    runClasspath()
  }

  /**
   * Additional options for the `native-image` Tool.
   *
   * @note It is recommended to restrict this list to options that can be shared across targets.
   */
  def nativeImageOptions: T[Seq[String]] = Seq.empty[String]

  /**
   * Path to the [[https://www.graalvm.org/latest/reference-manual/native-image/ `native-image` Tool]].
   * Defaults to a path relative to
   *  - [[ZincWorkerModule.javaHome]], if defined
   *  - environment variable `GRAALVM_HOME`, if defined
   *
   * @note The task fails if the `native-image` Tool is not found.
   */
  def nativeImageTool: T[PathRef] = Task {
    zincWorker().javaHome().map(_.path)
      .orElse(sys.env.get("GRAALVM_HOME").map(os.Path(_))) match {
      case Some(home) =>
        val tool = if (Properties.isWin) "native-image.cmd" else "native-image"
        val path = home / "bin" / tool
        if (os.exists(path)) PathRef(path)
        else throw new RuntimeException(s"$path not found")
      case None =>
        throw new RuntimeException("ZincWorkerModule.javaHome/GRAALVM_HOME not defined")
    }
  }
}
