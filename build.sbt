name := "competitive-programming-platform"
version := "1.0-SNAPSHOT"
scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.11.1",
  "org.typelevel" %% "cats-core" % "2.10.0",
  "com.github.jwt-scala" %% "jwt-play" % "9.4.4",
  "org.mindrot" % "jbcrypt" % "0.4",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.0",
  "com.typesafe.akka" %% "akka-stream" % "2.8.0",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test,
  "org.mockito" % "mockito-core" % "5.8.0" % Test
)

// Add resolver for JWT dependency
resolvers += "Atlassian Releases" at "https://packages.atlassian.com/maven-public/" 