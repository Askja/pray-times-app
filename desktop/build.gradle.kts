import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

group = "ru.admiral.praytimes"
version = "0.1.1"

application {
    mainClass.set("ru.admiral.praytimes.desktop.DesktopAppKt")
    applicationName = "PrayTimesDesktop"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin", "../app/src/main/java")
            include("ru/admiral/praytimes/R.kt")
            include("ru/admiral/praytimes/desktop/**")
            include("ru/admiral/praytimes/adhan/**")
            include("ru/admiral/praytimes/domain/CalculationMethodSelector.kt")
            include("ru/admiral/praytimes/domain/LocationCalculationMethodResolver.kt")
            include("ru/admiral/praytimes/domain/PrayerDayCalculator.kt")
            include("ru/admiral/praytimes/holiday/**")
            include("ru/admiral/praytimes/sunnah/**")
        }
        resources {
            srcDir("../app/src/main/res/drawable-nodpi")
            include("islamic_texture_tile.png", "holiday_texture_tile.png")
        }
    }
}

val desktopImageOutput = layout.buildDirectory.dir("jpackage")

tasks.register<Delete>("cleanDesktopImage") {
    delete(desktopImageOutput)
}

tasks.register<Exec>("desktopImage") {
    dependsOn(tasks.named("installDist"))
    dependsOn(tasks.named("cleanDesktopImage"))

    val installLib = layout.buildDirectory.dir("install/${application.applicationName}/lib")

    inputs.dir(installLib)
    outputs.dir(desktopImageOutput)

    args(
        "--type",
        "app-image",
        "--name",
        "PrayTimesDesktop",
        "--app-version",
        project.version.toString(),
        "--vendor",
        "Admiral",
        "--input",
        installLib.get().asFile.absolutePath,
        "--main-jar",
        tasks.named<Jar>("jar").get().archiveFileName.get(),
        "--main-class",
        application.mainClass.get(),
        "--dest",
        desktopImageOutput.get().asFile.absolutePath,
        "--add-modules",
        "java.desktop",
        "--java-options",
        "-Dfile.encoding=UTF-8",
    )

    executable = "jpackage"
}
