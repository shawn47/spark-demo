name := "Simple Project"

version := "1.0"

scalaVersion := "2.11.3"

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.0.0"

resolvers ++= Seq(
  "Akka Repository" at "http://repo.akka.io/releases/",
  "Spray Repository" at "http://repo.spray.cc/")