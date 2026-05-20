import org.gradle.kotlin.dsl.implementation

plugins {
    java
    // 코틀린 추가
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21" // Spring 빈 open class 처리
    id("org.springframework.boot") version "4.0.6" // 2차: 4.0.5 → 강사님 버전으로 맞춤
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.2.21" // JPA Entity open class 처리
    kotlin("kapt") version "2.2.21" // 어노테이션 프로세서 (QueryDSL, Lombok)
}

group = "com.back"
version = "0.0.1-SNAPSHOT"
description = "mozu"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24) // 2차: 25 → 강사님 버전으로 맞춤
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot 기본 (2차와 동일)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-webservices")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.redisson:redisson:3.44.0")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 코틀린 추가 - 리플렉션, Jackson 코틀린 모듈
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // JWT (2차와 동일)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Lombok (2차와 동일 - 자바 파일 남아있는 동안 필요)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // QueryDSL - 2차: annotationProcessor → kapt로 변경 (코틀린 환경)
    implementation("com.querydsl:querydsl-jpa:5.0.0:jakarta")
    kapt("com.querydsl:querydsl-apt:5.0.0:jakarta") // 변경
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // Swagger (2차와 동일)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0")

    // Prometheus (2차와 동일)
    implementation("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // DB (2차와 동일)
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")

    // Test (2차와 동일)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kapt {
    keepJavacAnnotationProcessors = true
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict", // Null 안정성 엄격하게
            "-Xannotation-default-target=param-property" // 강사님 추가 옵션
        )
    }
}

// JPA Entity 코틀린에서 쓰려면 open class 필요 → allOpen으로 자동 처리
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}