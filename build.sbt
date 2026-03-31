// ============================================================
// 齊天 (Qi-Tian) — RISC-V AI 晶片
// Chisel 3 + ScalaTest 建置設定
// ============================================================

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "qitian"

val chiselVersion     = "3.6.1"
val chiselTestVersion = "0.6.2"

lazy val root = (project in file("."))
  .settings(
    name := "Qi-Tian",

    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3"     % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest"  % chiselTestVersion % Test
    ),

    // Chisel 3 編譯器插件（必要）
    addCompilerPlugin(
      "edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full
    ),

    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    )
  )
