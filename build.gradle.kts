import org.ajoberstar.grgit.Grgit
import org.kohsuke.github.GitHub
import org.kohsuke.github.GHReleaseBuilder
import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.CurseArtifact
import com.matthewprenger.cursegradle.CurseRelation
import com.matthewprenger.cursegradle.Options

buildscript {
    dependencies {
        classpath("org.kohsuke:github-api:${project.property("github_api_version") as String}")
    }
}

plugins {
    id("maven-publish")
    id("fabric-loom")
    id("org.ajoberstar.grgit")
    id("com.matthewprenger.cursegradle")
    id("com.modrinth.minotaur")
}

operator fun Project.get(property: String): String {
    return property(property) as String
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

version = "${project["minecraft_version"]}-${project["mod_version"]}"
group = project["maven_group"]
base.archivesName.set("${name.split("-").let{it.subList(0, it.size-1)}.joinToString("-")}-${project["fdrf_version"]}-compat")

val environment: Map<String, String> = System.getenv()
val releaseName = "${project["release_prefix"]} ${project["fdrf_version"]}"
val releaseType = "BETA"
val releaseFile = "${buildDir}/libs/${base.archivesName.get()}-${version}.jar"
val cfGameVersion = project["seasons_version"].split("+")[1].let{ if(!project["minecraft_version"].contains("-") && project["minecraft_version"].startsWith(it)) project["minecraft_version"] else "$it-Snapshot"}

fun getChangeLog(): String {
    return "A changelog can be found at https://github.com/sazulo/$name/commits/"
}

fun getBranch(): String {
    environment["GITHUB_REF"]?.let { branch ->
        return branch.substring(branch.lastIndexOf("/") + 1)
    }
    val grgit = try {
        extensions.getByName("grgit") as Grgit
    }catch (ignored: Exception) {
        return "unknown"
    }
    val branch = grgit.branch.current().name
    return branch.substring(branch.lastIndexOf("/") + 1)
}

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "Curse Maven"
        url = uri("https://cursemaven.com")
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
    }
    maven {
        name = "Cafeteria"
        url = uri("https://maven.cafeteria.dev/releases")
    }
    maven {
        url = uri("https://maven.firstdarkdev.xyz/snapshots")
    }
    maven {
        name = "Greenhouse Maven"
        url = uri("https://repo.greenhouse.house/releases/")
    }
    maven {
        url = uri("https://mvn.devos.one/snapshots/") 
    }
    maven { 
        url = uri("https://mvn.devos.one/releases/")
    } 
    maven { // for Porting Lib: Fabric ASM
        url = uri("https://jitpack.io/")
    }    
    maven { // REI and deps
        url = uri("https://maven.architectury.dev/") 
    }
    maven { // REI and deps
        url = uri("https://maven.shedaniel.me/")
    } 
    maven { // Mod Menu, EMI
        url = uri("https://maven.terraformersmc.com/")
    } 
    maven {  // Temporary Porting Lib
        url = uri("https://repo.greenhouse.house/snapshots/")  
    }
    mavenLocal()
}

dependencies {
    minecraft("com.mojang:minecraft:${project["minecraft_version"]}")
    mappings("net.fabricmc:yarn:${project["yarn_mappings"]}:v2")

    modImplementation("net.fabricmc:fabric-loader:${project["loader_version"]}")

    modImplementation("io.github.lucaargolo:fabric-seasons:${project["seasons_version"]}")

	modImplementation("vectorwing:FarmersDelight:${project["fdrf_version"]}") {
        exclude("net.fabricmc")
        exclude("io.github.fabricators_of_create")
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    inputs.property("version", "${project.version}-${project["fdrf_version"]}")

    from(sourceSets["main"].resources.srcDirs) {
        include("fabric.mod.json")
        expand(mutableMapOf("version" to "${project.version}-${project["fdrf_version"]}"))
    }

    from(sourceSets["main"].resources.srcDirs) {
        exclude("fabric.mod.json")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

java {
    withSourcesJar()
}

tasks.jar {
    from("LICENSE")
}

//Github publishing
task("github") {
    dependsOn(tasks.remapJar)
    group = "upload"

    onlyIf { environment.containsKey("GITHUB_TOKEN") }

    doLast {
        val github = GitHub.connectUsingOAuth(environment["GITHUB_TOKEN"])
        val repository = github.getRepository(environment["GITHUB_REPOSITORY"])

        val releaseBuilder = GHReleaseBuilder(repository, version as String)
        releaseBuilder.name(releaseName)
        releaseBuilder.body(getChangeLog())
        releaseBuilder.commitish(getBranch())

        val ghRelease = releaseBuilder.create()
        ghRelease.uploadAsset(file(releaseFile), "application/java-archive")
    }
}

//Curseforge publishing
// curseforge {
//     environment["CURSEFORGE_API_KEY"]?.let { apiKey = it }

//     project(closureOf<CurseProject> {
//         id = project["curseforge_id"]
//         changelog = getChangeLog()
//         releaseType = this@Build_gradle.releaseType.toLowerCase()
//         addGameVersion(cfGameVersion)
//         addGameVersion("Fabric")

//         mainArtifact(file(releaseFile), closureOf<CurseArtifact> {
//             displayName = releaseName
//             relations(closureOf<CurseRelation> {
//                 requiredDependency("fabric-seasons")
//                 requiredDependency("farmers-delight-fabric")
//             })
//         })

//         afterEvaluate {
//             uploadTask.dependsOn("remapJar")
//         }

//     })

//     options(closureOf<Options> {
//         forgeGradleIntegration = false
//     })
// }

//Modrinth publishing
modrinth {
    environment["DEBUG_MODE"]?.let { debugMode.set(it) }
    environment["MODRINTH_TOKEN"]?.let { token.set(it) }

    projectId.set(project["modrinth_id"])
    changelog.set(getChangeLog())

    versionNumber.set(version as String)
    versionName.set(releaseName)
    versionType.set(releaseType.toLowerCase())

    uploadFile.set(tasks.remapJar.get())

    gameVersions.add(project["minecraft_version"])
    loaders.add("fabric")

    dependencies {
        required.project("fabric-seasons")
        required.project("farmers-delight-refabricated")
    }
}
tasks.modrinth.configure {
    group = "upload"
}