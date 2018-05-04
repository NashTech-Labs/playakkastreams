name := "playakkastreams"

version := "1.0"

lazy val `playakkastreams` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

scalaVersion := "2.11.11"

libraryDependencies ++=
  Seq(
    "com.typesafe.play" %% "play-slick" % "3.0.1",
    "org.postgresql" % "postgresql" % "42.1.4",
    "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "0.18",
    jdbc,
    ehcache,
    ws,
    specs2 % Test,
    guice)

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")

      