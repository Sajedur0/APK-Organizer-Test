allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

gradle.projectsEvaluated {
    subprojects.forEach { subproject ->
        subproject.extensions.findByName("android")?.let { android ->
            try {
                @Suppress("UNCHECKED_CAST")
                val compileOptions =
                    (android as com.android.build.gradle.BaseExtension).compileOptions
                compileOptions.sourceCompatibility = JavaVersion.VERSION_21
                compileOptions.targetCompatibility = JavaVersion.VERSION_21
            } catch (_: Exception) {}
        }
        subproject.tasks.withType<JavaCompile>().configureEach {
            sourceCompatibility = JavaVersion.VERSION_21.toString()
            targetCompatibility = JavaVersion.VERSION_21.toString()
        }
        try {
            subproject.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>()
                .configureEach {
                    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                }
        } catch (_: Exception) {}
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
    pluginManager.withPlugin("com.android.library") {
        if (!project.plugins.hasPlugin("org.jetbrains.kotlin.android")) {
            pluginManager.apply("org.jetbrains.kotlin.android")
        }
    }
    pluginManager.withPlugin("com.android.application") {
        if (!project.plugins.hasPlugin("org.jetbrains.kotlin.android")) {
            pluginManager.apply("org.jetbrains.kotlin.android")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
