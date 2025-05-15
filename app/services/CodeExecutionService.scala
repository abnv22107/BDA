package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process._
import play.api.Configuration
import models._
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import scala.util.{Try, Success, Failure}

@Singleton
class CodeExecutionService @Inject()(
  config: Configuration
)(implicit ec: ExecutionContext) {
  
  private val baseImage = config.get[String]("docker.baseImage")
  private val timeoutSeconds = config.get[Int]("docker.timeoutSeconds")
  private val memory = config.get[String]("docker.memory")
  private val cpuCount = config.get[Int]("docker.cpuCount")
  private val tempDir = Files.createTempDirectory("code-execution")
  
  private val languageConfigs = Map(
    "scala" -> LanguageConfig(
      extension = "scala",
      compileCmd = Some("scalac {file}"),
      runCmd = "scala {className}",
      baseImage = "hseeberger/scala-sbt:11.0.12-1.5.5_2.13.6"
    ),
    "java" -> LanguageConfig(
      extension = "java",
      compileCmd = Some("javac {file}"),
      runCmd = "java {className}",
      baseImage = "openjdk:11-jdk"
    ),
    "python" -> LanguageConfig(
      extension = "py",
      compileCmd = None,
      runCmd = "python3 {file}",
      baseImage = "python:3.9-slim"
    )
  )
  
  case class LanguageConfig(
    extension: String,
    compileCmd: Option[String],
    runCmd: String,
    baseImage: String
  )
  
  def executeCode(
    code: String,
    language: String,
    testCases: List[TestCase]
  ): Future[List[TestResult]] = {
    val langConfig = languageConfigs.getOrElse(language.toLowerCase,
      throw new IllegalArgumentException(s"Unsupported language: $language"))
    
    val executionId = UUID.randomUUID().toString
    val workDir = tempDir.resolve(executionId)
    Files.createDirectory(workDir)
    
    try {
      val sourceFile = createSourceFile(workDir, code, langConfig.extension)
      val containerName = s"code-execution-$executionId"
      
      for {
        // Compile if needed
        _ <- langConfig.compileCmd match {
          case Some(cmd) => compileCode(workDir, sourceFile, cmd, containerName, langConfig)
          case None => Future.successful(())
        }
        
        // Execute each test case
        results <- Future.sequence(testCases.map { testCase =>
          executeTestCase(workDir, sourceFile, testCase, containerName, langConfig)
        })
      } yield results
    } finally {
      // Cleanup
      cleanupExecution(workDir, executionId)
    }
  }
  
  private def createSourceFile(workDir: Path, code: String, extension: String): Path = {
    val sourceFile = workDir.resolve(s"Main.$extension")
    val writer = new PrintWriter(sourceFile.toFile)
    try {
      writer.write(code)
    } finally {
      writer.close()
    }
    sourceFile
  }
  
  private def compileCode(
    workDir: Path,
    sourceFile: Path,
    compileCmd: String,
    containerName: String,
    langConfig: LanguageConfig
  ): Future[Unit] = Future {
    val cmd = compileCmd
      .replace("{file}", sourceFile.getFileName.toString)
      .replace("{className}", "Main")
    
    val dockerCmd = Seq(
      "docker", "run",
      "--name", s"$containerName-compile",
      "--rm",
      "-v", s"${workDir.toAbsolutePath}:/code",
      "-w", "/code",
      langConfig.baseImage,
      "sh", "-c", cmd
    )
    
    val result = dockerCmd.!
    if (result != 0) {
      throw new RuntimeException("Compilation failed")
    }
  }
  
  private def executeTestCase(
    workDir: Path,
    sourceFile: Path,
    testCase: TestCase,
    containerName: String,
    langConfig: LanguageConfig
  ): Future[TestResult] = Future {
    val inputFile = workDir.resolve("input.txt")
    Files.write(inputFile, testCase.input.getBytes)
    
    val runCmd = langConfig.runCmd
      .replace("{file}", sourceFile.getFileName.toString)
      .replace("{className}", "Main")
    
    val dockerCmd = Seq(
      "docker", "run",
      "--name", s"$containerName-run",
      "--rm",
      "--memory", memory,
      "--cpus", cpuCount.toString,
      "-v", s"${workDir.toAbsolutePath}:/code",
      "-w", "/code",
      langConfig.baseImage,
      "sh", "-c", s"$runCmd < input.txt"
    )
    
    val startTime = System.currentTimeMillis()
    val processBuilder = Process(dockerCmd)
    val outputBuilder = new StringBuilder
    val errorBuilder = new StringBuilder
    
    val exitCode = processBuilder.!(ProcessLogger(
      out => outputBuilder.append(out + "\n"),
      err => errorBuilder.append(err + "\n")
    ))
    
    val executionTime = System.currentTimeMillis() - startTime
    val output = outputBuilder.toString.trim
    val error = errorBuilder.toString.trim
    
    TestResult(
      testCaseId = testCase.hashCode().toString,
      passed = exitCode == 0 && output == testCase.expectedOutput,
      executionTime = executionTime,
      memoryUsed = 0, // TODO: Implement memory tracking
      output = output,
      error = if (error.isEmpty) None else Some(error)
    )
  }
  
  private def cleanupExecution(workDir: Path, executionId: String): Unit = {
    Try {
      // Stop and remove containers if they exist
      s"docker stop code-execution-$executionId-compile".!
      s"docker stop code-execution-$executionId-run".!
      s"docker rm code-execution-$executionId-compile".!
      s"docker rm code-execution-$executionId-run".!
      
      // Delete temporary files
      Files.walk(workDir)
        .map[File](_.toFile)
        .forEach(_.delete())
      Files.deleteIfExists(workDir)
    }
  }
}