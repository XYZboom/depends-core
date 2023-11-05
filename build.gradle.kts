plugins {
    kotlin("jvm") version "1.9.0"
    id("java")
    id("maven-publish")
}

group = "cn.emergentdesign.se"
version = "0.9.8-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

publishing {
    repositories {
        maven {
            url = uri("http://47.115.213.131:8080/repository/alex-snapshots/")
            credentials {
                username = properties["maven-username"].toString()
                password = properties["maven-password"].toString()
            }
            isAllowInsecureProtocol = true
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "cn.emergentdesign.se"
            artifactId = "depends-core"
            version = "0.9.8-SNAPSHOT"

            from(components["java"])
        }
        create<MavenPublication>("maven-source") {
            groupId = "cn.emergentdesign.se"
            artifactId = "depends-core"
            version = "0.9.8-SNAPSHOT"

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
    maven {
        url = uri("http://47.115.213.131:8080/repository/alex-release/")
        isAllowInsecureProtocol = true
    }
    mavenCentral()
}

dependencies {
    implementation("cn.emergentdesign.se:utils:0.1.1")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.apache.poi:poi:5.2.2")
    implementation("org.apache.poi:poi-ooxml:5.2.4")
    implementation("info.picocli:picocli:4.7.3")
    implementation("org.codehaus.plexus:plexus-utils:3.5.1")
    implementation("net.sf.ehcache:ehcache-core:2.6.11")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.1")
    testImplementation("junit:junit:4.13.2")
}

tasks.getByName<Test>("test") {
    useJUnit()
}
