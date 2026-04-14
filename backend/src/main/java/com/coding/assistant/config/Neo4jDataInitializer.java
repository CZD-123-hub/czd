package com.coding.assistant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jDataInitializer {

    private final Driver neo4jDriver;

    @EventListener(ApplicationReadyEvent.class)
    public void initNeo4jData() {
        try (Session session = neo4jDriver.session()) {
            // Check if data already exists
            Result countResult = session.run("MATCH (n:Knowledge) RETURN count(n) AS cnt");
            long count = countResult.single().get("cnt").asLong();

            if (count > 0) {
                log.info("Neo4j already has {} Knowledge nodes, skipping initialization", count);
                return;
            }

            log.info("Neo4j is empty, initializing knowledge graph data...");

            // Create constraints and indexes
            session.run("CREATE CONSTRAINT knowledge_id IF NOT EXISTS FOR (k:Knowledge) REQUIRE k.id IS UNIQUE");
            session.run("CREATE CONSTRAINT knowledge_name IF NOT EXISTS FOR (k:Knowledge) REQUIRE k.name IS UNIQUE");
            session.run("CREATE INDEX knowledge_category IF NOT EXISTS FOR (k:Knowledge) ON (k.category)");
            session.run("CREATE INDEX knowledge_difficulty IF NOT EXISTS FOR (k:Knowledge) ON (k.difficulty)");

            // Create all nodes and relationships in a single transaction
            session.run(INIT_CYPHER);

            Result newCount = session.run("MATCH (n:Knowledge) RETURN count(n) AS cnt");
            log.info("Neo4j initialization complete: {} Knowledge nodes created", newCount.single().get("cnt").asLong());
        } catch (Exception e) {
            log.warn("Failed to initialize Neo4j data (Neo4j may not be running): {}", e.getMessage());
        }
    }

    private static final String INIT_CYPHER = """
        // Programming Languages
        CREATE (java:Knowledge {id: 'java', name: 'Java', category: 'language', difficulty: 'beginner', description: 'A widely-used, object-oriented programming language designed for portability and performance.', keywords: ['java', 'jdk', 'jvm', 'object-oriented']})
        CREATE (python:Knowledge {id: 'python', name: 'Python', category: 'language', difficulty: 'beginner', description: 'A high-level, interpreted programming language known for its readability and versatility.', keywords: ['python', 'scripting', 'data-science']})
        CREATE (javascript:Knowledge {id: 'javascript', name: 'JavaScript', category: 'language', difficulty: 'beginner', description: 'The language of the web. Used for both frontend and backend development.', keywords: ['javascript', 'js', 'ecmascript', 'web']})
        CREATE (typescript:Knowledge {id: 'typescript', name: 'TypeScript', category: 'language', difficulty: 'intermediate', description: 'A typed superset of JavaScript that compiles to plain JavaScript.', keywords: ['typescript', 'ts', 'typed', 'javascript']})
        CREATE (sql:Knowledge {id: 'sql', name: 'SQL', category: 'language', difficulty: 'beginner', description: 'Structured Query Language for managing and querying relational databases.', keywords: ['sql', 'query', 'database', 'relational']})

        // Java Ecosystem
        CREATE (springBoot:Knowledge {id: 'spring-boot', name: 'Spring Boot', category: 'framework', difficulty: 'intermediate', description: 'An opinionated framework for building production-ready Spring applications with minimal configuration.', keywords: ['spring', 'spring-boot', 'java', 'backend']})
        CREATE (springMVC:Knowledge {id: 'spring-mvc', name: 'Spring MVC', category: 'framework', difficulty: 'intermediate', description: 'A web framework within Spring for building HTTP-based REST APIs and web applications.', keywords: ['spring-mvc', 'mvc', 'web', 'controller']})
        CREATE (springSecurity:Knowledge {id: 'spring-security', name: 'Spring Security', category: 'framework', difficulty: 'advanced', description: 'A powerful authentication and authorization framework for securing Spring-based applications.', keywords: ['spring-security', 'authentication', 'authorization', 'security']})
        CREATE (springData:Knowledge {id: 'spring-data', name: 'Spring Data', category: 'framework', difficulty: 'intermediate', description: 'Simplifies data access with repository abstractions for relational and NoSQL databases.', keywords: ['spring-data', 'repository', 'data-access']})
        CREATE (springCloud:Knowledge {id: 'spring-cloud', name: 'Spring Cloud', category: 'framework', difficulty: 'advanced', description: 'A set of tools for building microservice architectures.', keywords: ['spring-cloud', 'microservices', 'service-discovery']})
        CREATE (myBatis:Knowledge {id: 'mybatis', name: 'MyBatis', category: 'framework', difficulty: 'intermediate', description: 'A persistence framework that maps SQL statements to Java methods.', keywords: ['mybatis', 'orm', 'sql-mapping', 'persistence']})
        CREATE (myBatisPlus:Knowledge {id: 'mybatis-plus', name: 'MyBatis-Plus', category: 'framework', difficulty: 'intermediate', description: 'An enhancement of MyBatis that provides CRUD operations, pagination, and code generation.', keywords: ['mybatis-plus', 'orm', 'crud', 'pagination']})
        CREATE (jpa:Knowledge {id: 'jpa', name: 'JPA', category: 'framework', difficulty: 'intermediate', description: 'Java Persistence API - a specification for object-relational mapping in Java.', keywords: ['jpa', 'orm', 'hibernate', 'persistence']})
        CREATE (maven:Knowledge {id: 'maven', name: 'Maven', category: 'tool', difficulty: 'beginner', description: 'A build automation and project management tool for Java projects.', keywords: ['maven', 'build', 'dependency', 'pom']})

        // Frontend
        CREATE (vue:Knowledge {id: 'vue3', name: 'Vue.js', category: 'framework', difficulty: 'intermediate', description: 'A progressive JavaScript framework for building user interfaces.', keywords: ['vue', 'vue3', 'frontend', 'reactive']})
        CREATE (react:Knowledge {id: 'react', name: 'React', category: 'framework', difficulty: 'intermediate', description: 'A JavaScript library for building user interfaces using a component-based architecture.', keywords: ['react', 'frontend', 'component', 'jsx']})
        CREATE (angular:Knowledge {id: 'angular', name: 'Angular', category: 'framework', difficulty: 'advanced', description: 'A full-featured TypeScript-based framework for building large-scale SPAs.', keywords: ['angular', 'frontend', 'typescript', 'spa']})
        CREATE (nodejs:Knowledge {id: 'nodejs', name: 'Node.js', category: 'runtime', difficulty: 'intermediate', description: 'A JavaScript runtime built on Chrome V8 engine.', keywords: ['nodejs', 'node', 'javascript', 'server']})
        CREATE (vite:Knowledge {id: 'vite', name: 'Vite', category: 'tool', difficulty: 'beginner', description: 'A fast build tool and dev server for modern web projects.', keywords: ['vite', 'build', 'dev-server', 'esm']})
        CREATE (elementPlus:Knowledge {id: 'element-plus', name: 'Element Plus', category: 'framework', difficulty: 'beginner', description: 'A Vue 3 UI component library with enterprise-grade components.', keywords: ['element-plus', 'ui', 'component', 'vue']})
        CREATE (vueRouter:Knowledge {id: 'vue-router', name: 'Vue Router', category: 'framework', difficulty: 'intermediate', description: 'The official router for Vue.js.', keywords: ['vue-router', 'router', 'navigation', 'spa']})
        CREATE (pinia:Knowledge {id: 'pinia', name: 'Pinia', category: 'framework', difficulty: 'intermediate', description: 'The official state management library for Vue.js.', keywords: ['pinia', 'state-management', 'store', 'vue']})

        // Databases
        CREATE (mysql:Knowledge {id: 'mysql', name: 'MySQL', category: 'database', difficulty: 'beginner', description: 'An open-source relational database management system.', keywords: ['mysql', 'database', 'relational', 'sql']})
        CREATE (redis:Knowledge {id: 'redis', name: 'Redis', category: 'database', difficulty: 'intermediate', description: 'An in-memory data structure store used as a database, cache, and message broker.', keywords: ['redis', 'cache', 'in-memory', 'nosql']})
        CREATE (neo4jDb:Knowledge {id: 'neo4j', name: 'Neo4j', category: 'database', difficulty: 'intermediate', description: 'A leading graph database that stores data as nodes and relationships.', keywords: ['neo4j', 'graph-database', 'cypher', 'nosql']})
        CREATE (mongodb:Knowledge {id: 'mongodb', name: 'MongoDB', category: 'database', difficulty: 'intermediate', description: 'A document-oriented NoSQL database using JSON-like documents.', keywords: ['mongodb', 'nosql', 'document', 'json']})
        CREATE (postgresql:Knowledge {id: 'postgresql', name: 'PostgreSQL', category: 'database', difficulty: 'intermediate', description: 'A powerful open-source object-relational database.', keywords: ['postgresql', 'postgres', 'database', 'sql']})

        // DevOps & Tools
        CREATE (docker:Knowledge {id: 'docker', name: 'Docker', category: 'tool', difficulty: 'intermediate', description: 'A platform for building, shipping, and running applications in containers.', keywords: ['docker', 'container', 'devops', 'deployment']})
        CREATE (kubernetes:Knowledge {id: 'kubernetes', name: 'Kubernetes', category: 'tool', difficulty: 'advanced', description: 'An open-source container orchestration platform.', keywords: ['kubernetes', 'k8s', 'container', 'orchestration']})
        CREATE (git:Knowledge {id: 'git', name: 'Git', category: 'tool', difficulty: 'beginner', description: 'A distributed version control system.', keywords: ['git', 'version-control', 'scm', 'branch']})
        CREATE (linux:Knowledge {id: 'linux', name: 'Linux', category: 'tool', difficulty: 'intermediate', description: 'An open-source operating system family widely used for servers.', keywords: ['linux', 'os', 'unix', 'shell']})
        CREATE (nginx:Knowledge {id: 'nginx', name: 'Nginx', category: 'tool', difficulty: 'intermediate', description: 'A high-performance HTTP server and reverse proxy.', keywords: ['nginx', 'web-server', 'reverse-proxy', 'load-balancer']})
        CREATE (jenkins:Knowledge {id: 'jenkins', name: 'Jenkins', category: 'tool', difficulty: 'intermediate', description: 'An open-source CI/CD automation server.', keywords: ['jenkins', 'ci', 'cd', 'automation']})

        // Fundamentals
        CREATE (dataStructures:Knowledge {id: 'data-structures', name: 'Data Structures', category: 'fundamental', difficulty: 'beginner', description: 'Core data organization concepts: arrays, linked lists, trees, graphs, hash tables.', keywords: ['data-structures', 'array', 'tree', 'graph', 'hash']})
        CREATE (algorithms:Knowledge {id: 'algorithms', name: 'Algorithms', category: 'fundamental', difficulty: 'intermediate', description: 'Problem-solving techniques: sorting, searching, dynamic programming.', keywords: ['algorithms', 'sorting', 'searching', 'dynamic-programming']})
        CREATE (designPatterns:Knowledge {id: 'design-patterns', name: 'Design Patterns', category: 'fundamental', difficulty: 'intermediate', description: 'Reusable solutions to common software design problems.', keywords: ['design-patterns', 'singleton', 'factory', 'observer', 'strategy']})
        CREATE (oop:Knowledge {id: 'oop', name: 'Object-Oriented Programming', category: 'fundamental', difficulty: 'beginner', description: 'A programming paradigm based on objects, encapsulation, inheritance, and polymorphism.', keywords: ['oop', 'object-oriented', 'encapsulation', 'inheritance']})
        CREATE (networking:Knowledge {id: 'networking', name: 'Computer Networking', category: 'fundamental', difficulty: 'intermediate', description: 'Fundamentals of network protocols, TCP/IP, HTTP, DNS.', keywords: ['networking', 'tcp', 'http', 'dns', 'protocol']})

        // Concepts
        CREATE (restApi:Knowledge {id: 'rest-api', name: 'REST API', category: 'concept', difficulty: 'beginner', description: 'Representational State Transfer - an architectural style for designing networked APIs.', keywords: ['rest', 'api', 'http', 'endpoint']})
        CREATE (microservices:Knowledge {id: 'microservices', name: 'Microservices', category: 'concept', difficulty: 'advanced', description: 'An architectural style with loosely coupled, independently deployable services.', keywords: ['microservices', 'distributed', 'service', 'architecture']})
        CREATE (jwt:Knowledge {id: 'jwt', name: 'JWT', category: 'concept', difficulty: 'intermediate', description: 'JSON Web Token for authentication between two parties.', keywords: ['jwt', 'token', 'authentication', 'json']})
        CREATE (cicd:Knowledge {id: 'ci-cd', name: 'CI/CD', category: 'concept', difficulty: 'intermediate', description: 'Continuous Integration and Continuous Delivery practices.', keywords: ['ci', 'cd', 'pipeline', 'automation']})
        CREATE (rag:Knowledge {id: 'rag', name: 'RAG', category: 'concept', difficulty: 'advanced', description: 'Retrieval-Augmented Generation - enhances LLM responses by retrieving relevant context.', keywords: ['rag', 'retrieval', 'llm', 'knowledge-base']})

        // DEPENDS_ON relationships
        CREATE (springBoot)-[:DEPENDS_ON]->(java)
        CREATE (springMVC)-[:DEPENDS_ON]->(java)
        CREATE (springSecurity)-[:DEPENDS_ON]->(springBoot)
        CREATE (springData)-[:DEPENDS_ON]->(springBoot)
        CREATE (springCloud)-[:DEPENDS_ON]->(springBoot)
        CREATE (myBatis)-[:DEPENDS_ON]->(java)
        CREATE (myBatis)-[:DEPENDS_ON]->(sql)
        CREATE (myBatisPlus)-[:DEPENDS_ON]->(myBatis)
        CREATE (jpa)-[:DEPENDS_ON]->(java)
        CREATE (maven)-[:DEPENDS_ON]->(java)
        CREATE (vue)-[:DEPENDS_ON]->(javascript)
        CREATE (react)-[:DEPENDS_ON]->(javascript)
        CREATE (angular)-[:DEPENDS_ON]->(typescript)
        CREATE (typescript)-[:DEPENDS_ON]->(javascript)
        CREATE (nodejs)-[:DEPENDS_ON]->(javascript)
        CREATE (vite)-[:DEPENDS_ON]->(javascript)
        CREATE (elementPlus)-[:DEPENDS_ON]->(vue)
        CREATE (vueRouter)-[:DEPENDS_ON]->(vue)
        CREATE (pinia)-[:DEPENDS_ON]->(vue)
        CREATE (restApi)-[:DEPENDS_ON]->(networking)
        CREATE (microservices)-[:DEPENDS_ON]->(restApi)
        CREATE (microservices)-[:DEPENDS_ON]->(docker)
        CREATE (jwt)-[:DEPENDS_ON]->(restApi)
        CREATE (cicd)-[:DEPENDS_ON]->(docker)
        CREATE (cicd)-[:DEPENDS_ON]->(git)
        CREATE (kubernetes)-[:DEPENDS_ON]->(docker)
        CREATE (mysql)-[:DEPENDS_ON]->(sql)
        CREATE (postgresql)-[:DEPENDS_ON]->(sql)
        CREATE (algorithms)-[:DEPENDS_ON]->(dataStructures)
        CREATE (designPatterns)-[:DEPENDS_ON]->(oop)

        // CONTAINS relationships
        CREATE (springBoot)-[:CONTAINS]->(springMVC)
        CREATE (springBoot)-[:CONTAINS]->(springSecurity)
        CREATE (springBoot)-[:CONTAINS]->(springData)

        // RELATED_TO relationships
        CREATE (java)-[:RELATED_TO]->(python)
        CREATE (vue)-[:RELATED_TO]->(react)
        CREATE (react)-[:RELATED_TO]->(angular)
        CREATE (mysql)-[:RELATED_TO]->(postgresql)
        CREATE (redis)-[:RELATED_TO]->(mongodb)
        CREATE (docker)-[:RELATED_TO]->(nginx)
        CREATE (jenkins)-[:RELATED_TO]->(cicd)
        CREATE (myBatis)-[:RELATED_TO]->(jpa)
        CREATE (springData)-[:RELATED_TO]->(jpa)
        CREATE (git)-[:RELATED_TO]->(linux)
        CREATE (rag)-[:RELATED_TO]->(neo4jDb)
        """;
}