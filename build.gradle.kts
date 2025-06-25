plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://raw.githubusercontent.com/ahmetaa/maven-repo/master")
}

dependencies {
    // Compile against the latest 1.20.x API for broader server compatibility
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    compileOnly("com.github.booksaw:BetterTeams:4.13.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.rholder:snowball-stemmer:1.3.0.581.1")
    implementation("zemberek-nlp:zemberek-morphology:0.17.1")
    implementation("zemberek-nlp:zemberek-tokenization:0.17.1")
    implementation("me.xdrop:fuzzywuzzy:1.4.0")
    implementation("net.wesjd:anvilgui:1.10.6-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.16.0")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    // Relocation removed due to ASM incompatibility with JDK 21
}
