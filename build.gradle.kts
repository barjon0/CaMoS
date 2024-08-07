// Define the path to the cplex folder here, before bin folder
val cplexPath = "C:/Program Files/IBM/ILOG/CPLEX_Studio2211/cplex/"


plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.osgeo.org/repository/release/")
    }
    maven {
        url = uri("https://repo.osgeo.org/repository/snapshot/")
    }
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.json:json:20220320")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.5.5")
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
    implementation("com.graphhopper:graphhopper-core:6.2")
    implementation("org.locationtech.jts:jts-core:1.19.0")
    implementation("org.geotools:gt-main:27.1")
    implementation("org.geotools:gt-epsg-hsql:27.1")
    implementation("org.geotools:gt-shapefile:27.2")
    implementation("com.graphhopper:jsprit-core:1.9.0-beta.11")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    implementation(files(cplexPath + "lib/cplex.jar"))
    implementation("org.openjdk.nashorn:nashorn-core:15.4")
    implementation("org.jfree:jfreechart:1.5.3")
    implementation("org.jfree:jcommon:1.0.24")

}

tasks.test {
    useJUnitPlatform()
}



application {
    applicationDefaultJvmArgs = setOf("-Djava.library.path=$cplexPath" + "bin/x64_win64")
    mainClass.set("mobilitysimulation.GeneralManager")
}