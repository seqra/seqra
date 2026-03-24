import OpenTaintTestUtilDependency.opentaintSastTestUtil

plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repository.jboss.org/nexus/content/groups/public/")
    maven("https://repo.jenkins-ci.org/public/")
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
    compileOnly(opentaintSastTestUtil)

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

    // Pebble for Pebble SSTI sink samples (com.mitchellbosecke.pebble package)
    implementation("com.mitchellbosecke:pebble:2.4.0")

    // Jinjava for Jinjava SSTI sink samples
    implementation("com.hubspot.jinjava:jinjava:2.7.2")

    // Velocity engine for Velocity SSTI sink samples
    implementation("org.apache.velocity:velocity-engine-core:2.3")

    // Javax EL API for EL injection samples
    implementation("javax.el:javax.el-api:3.0.1-b06")

    // Spring Expression for SpEL injection samples
    implementation("org.springframework:spring-expression:5.3.39")

    // YAML parsing for rule validation
    implementation("org.yaml:snakeyaml:2.3")

    // Apache Commons Digester3 for Digester XXE samples
    implementation("org.apache.commons:commons-digester3:3.2")

    // Spring WebSocket for WebSocketHandler source samples
    implementation("org.springframework:spring-websocket:5.3.39")

    // RabbitMQ AMQP client for RabbitMQ source samples
    implementation("com.rabbitmq:amqp-client:5.20.0")

    // Netty for ChannelInboundHandler / decoder source samples
    implementation("io.netty:netty-all:4.1.108.Final")

    // Stapler (Jenkins) for StaplerRequest + annotation source samples
    implementation("org.kohsuke.stapler:stapler:1.263")

    // Apache Commons Net for FTPClient source samples
    implementation("commons-net:commons-net:3.10.0")

    // Jakarta XML Bind for AttachmentUnmarshaller source samples
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:3.0.1")

    // Bean Validation API for ConstraintValidator source samples
    implementation("javax.validation:validation-api:2.0.1.Final")

    // Apache Shiro for AuthenticationToken source samples
    implementation("org.apache.shiro:shiro-core:1.13.0")

    // XmlPull for XmlPullParser source samples
    implementation("xmlpull:xmlpull:1.1.3.1")

    // Play Framework for Http.Request/RequestHeader source samples
    implementation("com.typesafe.play:play-java_2.13:2.8.21")

    // Ratpack for ratpack.http.Request source samples
    implementation("io.ratpack:ratpack-core:1.9.0")

    // Apache HttpCore5 for HttpRequestHandler source samples
    implementation("org.apache.httpcomponents.core5:httpcore5:5.2.4")

    // Jenkins core for hudson.FilePath file source samples
    compileOnly("org.jenkins-ci.main:jenkins-core:2.426.3")

    // Apache Commons IO for path-traversal sink samples
    implementation("commons-io:commons-io:2.15.1")

    // Guava for com.google.common.io.Files sink samples
    implementation("com.google.guava:guava:33.0.0-jre")

    // Jackson for ObjectMapper.readValue/writeValue(File,...) sink samples
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    // Jakarta Activation for FileDataSource sink samples
    implementation("jakarta.activation:jakarta.activation-api:2.1.2")

    // Jakarta Faces for ExternalContext path-traversal sink samples
    implementation("jakarta.faces:jakarta.faces-api:4.0.1")

    // javax.activation for FileDataSource path-traversal sink samples
    implementation("javax.activation:javax.activation-api:1.2.0")

    // XStream for XStream.fromXML(File) path-traversal sink samples
    implementation("com.thoughtworks.xstream:xstream:1.4.20")

    // Undertow for PathResourceManager sink samples
    implementation("io.undertow:undertow-core:2.3.10.Final")

    // ANTLR 3 runtime for ANTLRFileStream sink samples
    implementation("org.antlr:antlr-runtime:3.5.3")

    // Apache Ant for Ant task/classloader sink samples
    implementation("org.apache.ant:ant:1.10.14")

    // JMH for ChainedOptionsBuilder.result sink samples
    implementation("org.openjdk.jmh:jmh-core:1.37")

    // zip4j for ZipFile path-traversal sink samples
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // Kotlin stdlib for kotlin.io.FilesKt sink samples
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")

    // Apache HttpComponents 5 client for SSRF sink samples
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3")

    // HikariCP for SSRF sink samples (JDBC URL injection)
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Eclipse Jetty HTTP client for SSRF sink samples
    implementation("org.eclipse.jetty:jetty-client:9.4.54.v20240208")

    // JSch for SSH connection SSRF sink samples
    implementation("com.jcraft:jsch:0.1.55")

    // Spring WebFlux for WebClient SSRF sink samples
    implementation("org.springframework:spring-webflux:5.3.39")

    // Apache Commons HttpClient 3.x for HTTP parameter pollution sink samples
    implementation("commons-httpclient:commons-httpclient:3.1")

    // OkHttp3 for OkHttpClient SSRF sink samples
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Jakarta WS RS API for JAX-RS SSRF sink samples
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")

    // JDBI for Jdbi.create SSRF sink samples
    implementation("org.jdbi:jdbi3-core:3.43.0")

    // InfluxDB client for InfluxDBFactory.connect SSRF sink samples
    implementation("org.influxdb:influxdb-java:2.24")

    // Spring Boot for DataSourceBuilder SSRF sink samples
    compileOnly("org.springframework.boot:spring-boot:2.7.18")

    // Log4j2 API for LogBuilder / Logger log-injection sink samples
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")

    // Google Flogger for LoggingApi log-injection sink samples
    implementation("com.google.flogger:flogger:0.8")

    // Apache CXF core for LogUtils log-injection sink samples
    implementation("org.apache.cxf:cxf-core:3.5.9")

    // SciJava common for org.scijava.log.Logger log-injection sink samples
    implementation("org.scijava:scijava-common:2.97.1")

    // Hibernate Core for SharedSessionContract/QueryProducer SQL injection sink samples
    implementation("org.hibernate:hibernate-core:5.6.15.Final")

    // MyBatis for SqlRunner SQL injection sink samples
    implementation("org.mybatis:mybatis:3.5.16")

    // Couchbase Java Client for Cluster SQL injection sink samples
    implementation("com.couchbase.client:java-client:3.6.0")

    // Liquibase Core for JdbcConnection/RawSqlStatement SQL injection sink samples
    implementation("org.liquibase:liquibase-core:4.25.1")

    // Alibaba Druid for SchemaRepository SQL injection sink samples
    implementation("com.alibaba:druid:1.2.21")

    // JDO API for PersistenceManager/Query SQL injection sink samples
    implementation("javax.jdo:jdo-api:3.2.1")

    // Vert.x SQL Client for SqlClient/SqlConnection SQL injection sink samples
    implementation("io.vertx:vertx-sql-client:4.5.1")

    // javax.persistence API for EntityManager SQL injection sink samples
    implementation("javax.persistence:javax.persistence-api:2.2")

    // Apache Torque for BasePeer SQL injection sink samples
    implementation("org.apache.torque:torque-runtime:3.3")

    // Apache Commons Exec for command injection sink samples
    implementation("org.apache.commons:commons-exec:1.3")

    // Apache Commons JEXL 2 for JEXL injection sink samples
    implementation("org.apache.commons:commons-jexl:2.1.1")

    // Apache Commons JEXL 3 for JEXL injection sink samples
    implementation("org.apache.commons:commons-jexl3:3.3")

    // MVEL 2 for MVEL injection sink samples
    implementation("org.mvel:mvel2:2.5.2.Final")

    // Apache Struts 2 for OGNL injection sink samples (OgnlValueStack, TextProvider, etc.)
    compileOnly("org.apache.struts:struts2-core:2.5.33")

    // UnboundID LDAP SDK for LDAPConnection LDAP injection sink samples
    implementation("com.unboundid:unboundid-ldapsdk:6.0.11")

    // Apache Directory LDAP API for LdapConnection LDAP injection sink samples
    implementation("org.apache.directory.api:api-ldap-client-api:2.1.6")

    // Spring LDAP Core for LdapTemplate LDAP injection sink samples
    implementation("org.springframework.ldap:spring-ldap-core:2.4.1")

    // Caucho Hessian for Hessian/Burlap unsafe deserialization sink samples
    implementation("com.caucho:hessian:4.0.66")

    // Alibaba Hessian Lite (Dubbo fork) for Alibaba Hessian deserialization sink samples
    implementation("com.alibaba:hessian-lite:3.2.13")

    // json-io (CedarSoftware) for JsonReader deserialization sink samples
    implementation("com.cedarsoftware:json-io:4.14.0")

    // YamlBeans for YamlReader deserialization sink samples
    implementation("com.esotericsoftware.yamlbeans:yamlbeans:1.17")

    // Apache Commons Lang (old) for SerializationUtils deserialization sink samples
    implementation("commons-lang:commons-lang:2.6")

    // Apache Commons Lang3 for SerializationUtils deserialization sink samples
    implementation("org.apache.commons:commons-lang3:3.14.0")

    // Castor XML for Unmarshaller deserialization sink samples
    implementation("org.codehaus.castor:castor-xml:1.4.1")

    // JYaml for org.ho.yaml deserialization sink samples
    implementation("org.jyaml:jyaml:1.3")

    // Jabsorb for JSONSerializer deserialization sink samples
    implementation("org.jabsorb:jabsorb:1.3.2")

    // dom4j for dom4j XPath injection sink samples, SAXReader XXE samples
    implementation("org.dom4j:dom4j:2.1.4")

    // Saxon-HE for Saxon XSLT injection sink samples
    implementation("net.sf.saxon:Saxon-HE:12.5")

    // org.json for JSONObject in MongoDB $where injection test (JSONObject → parse pattern)
    implementation("org.json:json:20231013")

    // Portlet API for PortletContext url-forward sink samples
    implementation("javax.portlet:portlet-api:2.0")

    // Note: CXF XPathUtils is in cxf-core (already added above for LogUtils)
    // Note: CXF XSLTUtils is in cxf-rt-features-transform; test is omitted (pattern uses Argument[this]/Argument[0] taint propagation)
}

tasks.withType<Jar> {
    isZip64 = true
}

// CI helper: validate that all rules are valid YAML and covered by tests
tasks.register<JavaExec>("checkRulesCoverage") {
    group = "verification"
    description = "Validates YAML rules and ensures each active rule is covered by a @PositiveRuleSample test."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "rules.RuleCoverageCheck"
}
