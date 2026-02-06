import SeqraTestUtilDependency.seqraSastTestUtil

plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repository.jboss.org/nexus/content/groups/public/")
}


sourceSets {
    main {
        resources {
            srcDirs("../")
            exclude("test/**")
        }
    }
}

dependencies {
    compileOnly(seqraSastTestUtil)

    // Servlet + OGNL + Groovy dependencies for rule samples
    implementation("javax.servlet:javax.servlet-api:4.0.1")
    implementation("ognl:ognl:3.3.4")
    implementation("org.codehaus.groovy:groovy:3.0.21")

    // Spring Web for Spring MVC-based rule samples
    implementation("org.springframework:spring-web:5.3.39")
    // Spring MVC & core context for @Controller/@Model usage
    implementation("org.springframework:spring-webmvc:5.3.39")
    implementation("org.springframework:spring-context:5.3.39")
    // Spring JDBC for JdbcTemplate-based SQL samples
    implementation("org.springframework:spring-jdbc:5.3.39")

    // Spring Security for CSRF rule samples
    implementation("org.springframework.security:spring-security-config:5.8.13")
    implementation("org.springframework.security:spring-security-web:5.8.13")

    // MongoDB drivers for data-query-injection samples (legacy + modern APIs)
    implementation("org.mongodb:mongo-java-driver:3.12.14")
    implementation("org.mongodb:mongodb-driver-sync:4.11.2")


    // Apache Commons Text for HTML escaping in XSS safe samples
    implementation("org.apache.commons:commons-text:1.11.0")

    // Apache Wicket for wicket-xss samples
    implementation("org.apache.wicket:wicket-core:9.17.0")

    // Auth0 Java JWT for JWT samples using com.auth0.jwt.*
    implementation("com.auth0:java-jwt:4.4.0")

    // JJWT (io.jsonwebtoken.*) for JWT & JWS/JWE samples
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // Apache Commons Email & HttpClient for mail and HTTP samples
    implementation("org.apache.commons:commons-email:1.6.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")

    // Apache Commons FileUpload for file upload samples
    implementation("commons-fileupload:commons-fileupload:1.5")

    // Apache Commons BeanUtils & Codec for bean/introspection & digest samples
    implementation("commons-beanutils:commons-beanutils:1.9.4")
    implementation("commons-codec:commons-codec:1.16.0")

    // JMS API for insecure JMS deserialization samples
    implementation("javax.jms:javax.jms-api:2.0.1")

    // OpenSAML
    implementation("org.opensaml:opensaml-core:4.3.0")
    implementation("org.opensaml:xmltooling:1.4.4")

    // JAX-RS API + RESTEasy implementation for RESTEasy deserialization samples
    implementation("javax.ws.rs:javax.ws.rs-api:2.1.1")
    implementation("org.jboss.resteasy:resteasy-jaxrs:3.15.6.Final")

    // Apache XML-RPC client/server for XML-RPC deserialization samples
    implementation("org.apache.xmlrpc:xmlrpc-client:3.1.3")
    implementation("org.apache.xmlrpc:xmlrpc-server:3.1.3")

    // Hazelcast for symmetric encryption config samples
    implementation("com.hazelcast:hazelcast:3.12.13")


    // JSF API for FacesContext samples
    implementation("javax.faces:javax.faces-api:2.3")

    // JBoss Seam for @Name and logging samples (from JBoss public repository)
    implementation("org.jboss.seam:jboss-seam:2.3.1.Final")

    // Freemarker for SSTI servlet samples
    implementation("org.freemarker:freemarker:2.3.32")

    // Thymeleaf + Spring integration for Spring SSTI samples
    implementation("org.thymeleaf:thymeleaf-spring5:3.1.2.RELEASE")

    // Javax EL API for EL injection samples
    implementation("javax.el:javax.el-api:3.0.1-b06")

    // Spring Expression for SpEL injection samples
    implementation("org.springframework:spring-expression:5.3.39")

    // YAML parsing for rule validation
    implementation("org.yaml:snakeyaml:2.3")
}

// CI helper: validate that all rules are valid YAML and covered by tests
tasks.register<JavaExec>("checkRulesCoverage") {
    group = "verification"
    description = "Validates YAML rules and ensures each active rule is covered by a @PositiveRuleSample test."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "rules.RuleCoverageCheck"
}
