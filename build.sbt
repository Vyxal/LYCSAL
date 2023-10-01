val scala3Version = "3.3.0"

javaCppVersion := "1.5.9"
javaCppPresetLibs ++= Seq(
  "llvm" -> "16.0.4"
)
fork := true

lazy val lycsal = project
  .in(file("."))
  .settings(
    name := "lycsal",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,
    assembly / mainClass := Some("io.github.vyxal.lycsal.CLI"),
    assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case _ => MergeStrategy.preferProject // cha cha real smooth
    },

    // These HAVE to match the ones used by the embedded Vyxal JAR, otherwise bad things could happen
    // Speaking of, running assembly takes at least 4GB of heap, so watch out for that
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "com.github.scopt" %% "scopt" % "4.1.0",
      "com.outr" %% "scribe" % "3.11.9",
      "org.fusesource.jansi" % "jansi" % "2.4.0"
    ),
  )