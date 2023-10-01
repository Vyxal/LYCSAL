package io.github.vyxal.lycsal

import scopt.OParser
import java.io.File
import org.fusesource.jansi.AnsiConsole
import org.fusesource.jansi.Ansi.Color
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.Ansi.Erase
import scribe.format._
import scribe.Logger
import scala.io.Source
import java.io.BufferedWriter
import java.io.FileWriter
import scala.concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

case class Config(
    input: Option[File] = Some(File(".")),
    output: Option[File] = None,
    emitLLIR: Boolean = false,
    optimization: Boolean = false,
    clang: Option[File] = None,
    useEmbeddedClang: Boolean = false,
)

val builder = OParser.builder[Config]
val parser = {
    import builder._
    OParser.sequence(
        programName("lycsal"),
        head("LYCSAL - the Laconic vYxal Compiler System tArgeting Llvm"),
        head(s"Targeting Vyxal ${LYCSAL.vyxalVersion}"),
        opt[File]('o', "output")
            .valueName("<file>")
            .action((file, config) => config.copy(output = Some(file)))
            .text("The filename to output to. Defaults to the name of the input file."),
        opt[Unit]('l', "llir")
            .action((_, config) => config.copy(emitLLIR = true))
            .text("Emit generated IR instead of compiling"),
        opt[Unit]('O', "optimize")
            .action((_, config) => config.copy(optimization = true))
            .text("Enable optimization"),
        opt[File]("clang")
            .action((file, config) => config.copy(clang = Some(file)))
            .text("Supply a path to a clang executable. If not provided, LYCSAL will search for an installed clang-16."),
        opt[Unit]("use-embedded-clang")
            .action((_, config) => config.copy(useEmbeddedClang = true))
            .text("Use the embedded Clang-16 executable. Disabled by default due to not working on the dev's machine."),
        arg[String]("<file>")
            .action((path, config) => config.copy(input = if path == "-" then None else Some(File(path))))
            .text("The file to read the program from. Use - for stdin."),
        note(ansi().fgBrightBlack().a("\n... There but for the grace of Dog go I ...").reset().toString()),
        checkConfig(config => 
            if config.input.isEmpty && config.output.isEmpty
            then failure("Must specify an output filename if taking input from stdin!")
            else success
        )
    )
}

object CLI:
    Logger.root
        .clearHandlers()
        .withHandler(formatter = formatter"[$levelColored] $messages")
        .replace()

    def findExecutable(name: String): Option[File] =
        System.getenv("PATH").split(File.pathSeparator).map(File(_, name)).filter(_.isFile()).filter(_.canExecute()).headOption

    def compile(config: Config) =
        val program = config.input match
            case Some(file) =>
                scribe.info(s"loading ${file.getName()}")
                Source.fromFile(file).mkString
            case None => Source.stdin.mkString
        val outputFilename = config.output match
                case Some(file) => file.getName()
                case None => config.input.get.getName().split("\\.").init.:+(if config.emitLLIR then "ll" else "").mkString(".").stripSuffix(".")
        scribe.info(s"compiling to ${outputFilename}")
        val module = LYCSAL.compile(program)
        if config.optimization then
            scribe.info("optimizing")
            LYCSAL.optimize(module)
        else
            scribe.info("forgoing optimization, user didn't ask for it")
        if config.emitLLIR then
            val writer = new BufferedWriter(new FileWriter(config.output.getOrElse(File(outputFilename))))
            writer.write(LYCSAL.stringify(module))
            writer.close()
        else
            scribe.info("linking")
            LYCSAL.link(
              module,
              outputFilename,
              config.clang.orElse(
                Option.when(!config.useEmbeddedClang) {
                  findExecutable("clang-16").orElse(findExecutable("clang")) match
                    case Some(file) => file
                    case None =>
                      throw RuntimeException(
                        "Unable to find clang-16 or clang in PATH! Make sure you have it installed, or alternately try running LYCSAL again with --use-embedded-clang, which probably won't work but is at least better than nothing. I'd advise installing clang if possible though."
                      )
                }
              )
            )

    def main(args: Array[String]): Unit =
        AnsiConsole.systemInstall()
        OParser.parse(parser, args, Config()) match
            case Some(config) =>
                scribe.info(s"welcome to LYCSAL ${CLI.getClass().getPackage().getImplementationVersion()}")
                val startedAt = System.currentTimeMillis().toInt / 1000
                try
                    compile(config)
                    val endedAt = System.currentTimeMillis().toInt / 1000
                    scribe.info(s"success! compiled in ${endedAt - startedAt}s") // hopefully it never takes over a minute
                catch
                    case NonFatal(error) =>
                        val endedAt = System.currentTimeMillis().toInt / 1000
                        scribe.error(":( an error occured whilst compiling!")
                        scribe.error(s"specifically: ${error.getMessage()}")
                        scribe.error("compilation aborted, figure it out yourself.")
                        scribe.error("stack trace follows.")
                        for msg <- error.getStackTrace() do
                            scribe.error(s"    $msg")
                        scribe.error(s"compilation FAILED in ${endedAt - startedAt}s. have fun fixing this one!")
            case None => ()
        