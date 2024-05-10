plugins {
    id("java")
    application
    `maven-publish`
}

group = "com.github.XYZboom"
version = "1.0.0-alpha0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
publishing {
    repositories {
        mavenLocal()
    }
    publications {
        create<MavenPublication>("depend-core") {
            groupId = "com.github.XYZboom"
            artifactId = "depends-core"
            version = "1.0.0-alpha0"

            from(components["java"])
        }
        create<MavenPublication>("depend-core-source") {
            groupId = "com.github.XYZboom"
            artifactId = "depends-core"
            version = "1.0.0-alpha0"

            // 配置要上传的源码
            artifact(tasks.register<Jar>("sourcesJar") {
                from(sourceSets.main.get().allSource)
                archiveClassifier.set("sources")
            }) {
                classifier = "sources"
            }
        }
    }
}
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains:annotations:24.0.1")
    implementation("org.antlr:antlr4-runtime:4.13.1")
    implementation("com.github.multilang-depends:utils:04855aebf3")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.apache.poi:poi:5.2.2")
    implementation("org.apache.poi:poi-ooxml:5.2.4")
    implementation("info.picocli:picocli:4.7.3")
    implementation("org.codehaus.plexus:plexus-utils:3.5.1")
    implementation("net.sf.ehcache:ehcache-core:2.6.11")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.1")
    runtimeOnly("com.github.XYZboom:depends-java:1.0.0-alpha1")
    runtimeOnly("com.github.XYZboom:depends-kotlin:v1.0.0-alpha0")
    testImplementation("junit:junit:4.13.2")
}

tasks.getByName<Test>("test") {
    useJUnit()
}

application {
    mainClass.set("depends.Main")
}