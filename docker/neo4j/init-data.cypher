// ============================================================
// Intelligent Coding Assistant - Neo4j Knowledge Graph Init
// ============================================================
// Usage (after docker-compose up):
//   cat docker/neo4j/init-data.cypher | docker exec -i ica-neo4j cypher-shell -u neo4j -p neo4j123
// ============================================================

// ----------------------------------------------------------
// Create constraints and indexes
// ----------------------------------------------------------
CREATE CONSTRAINT knowledge_id IF NOT EXISTS FOR (k:Knowledge) REQUIRE k.id IS UNIQUE;
CREATE CONSTRAINT knowledge_name IF NOT EXISTS FOR (k:Knowledge) REQUIRE k.name IS UNIQUE;
CREATE CONSTRAINT resource_url IF NOT EXISTS FOR (r:Resource) REQUIRE r.url IS UNIQUE;
CREATE INDEX knowledge_category IF NOT EXISTS FOR (k:Knowledge) ON (k.category);
CREATE INDEX knowledge_difficulty IF NOT EXISTS FOR (k:Knowledge) ON (k.difficulty);

// ----------------------------------------------------------
// Programming Languages
// ----------------------------------------------------------
CREATE (java:Knowledge {id: 'java', name: 'Java', category: 'language', difficulty: 'beginner', description: 'A widely-used, object-oriented programming language designed for portability and performance. Core language for enterprise and Android development.', keywords: ['java', 'jdk', 'jvm', 'object-oriented']})
CREATE (python:Knowledge {id: 'python', name: 'Python', category: 'language', difficulty: 'beginner', description: 'A high-level, interpreted programming language known for its readability and versatility. Popular in data science, web development, and scripting.', keywords: ['python', 'scripting', 'data-science']})
CREATE (javascript:Knowledge {id: 'javascript', name: 'JavaScript', category: 'language', difficulty: 'beginner', description: 'The language of the web. Used for both frontend and backend development, enabling interactive and dynamic web applications.', keywords: ['javascript', 'js', 'ecmascript', 'web']})
CREATE (typescript:Knowledge {id: 'typescript', name: 'TypeScript', category: 'language', difficulty: 'intermediate', description: 'A typed superset of JavaScript that compiles to plain JavaScript. Adds optional static typing and class-based OOP.', keywords: ['typescript', 'ts', 'typed', 'javascript']})
CREATE (sql:Knowledge {id: 'sql', name: 'SQL', category: 'language', difficulty: 'beginner', description: 'Structured Query Language for managing and querying relational databases.', keywords: ['sql', 'query', 'database', 'relational']})

// ----------------------------------------------------------
// Java Ecosystem / Frameworks
// ----------------------------------------------------------
CREATE (springBoot:Knowledge {id: 'spring-boot', name: 'Spring Boot', category: 'framework', difficulty: 'intermediate', description: 'An opinionated framework for building production-ready Spring applications with minimal configuration.', keywords: ['spring', 'spring-boot', 'java', 'backend']})
CREATE (springMVC:Knowledge {id: 'spring-mvc', name: 'Spring MVC', category: 'framework', difficulty: 'intermediate', description: 'A web framework within Spring for building HTTP-based REST APIs and web applications using the Model-View-Controller pattern.', keywords: ['spring-mvc', 'mvc', 'web', 'controller']})
CREATE (springSecurity:Knowledge {id: 'spring-security', name: 'Spring Security', category: 'framework', difficulty: 'advanced', description: 'A powerful authentication and authorization framework for securing Spring-based applications.', keywords: ['spring-security', 'authentication', 'authorization', 'security']})
CREATE (springData:Knowledge {id: 'spring-data', name: 'Spring Data', category: 'framework', difficulty: 'intermediate', description: 'Simplifies data access with repository abstractions for relational and NoSQL databases.', keywords: ['spring-data', 'repository', 'data-access']})
CREATE (springCloud:Knowledge {id: 'spring-cloud', name: 'Spring Cloud', category: 'framework', difficulty: 'advanced', description: 'A set of tools for building microservice architectures with service discovery, config management, circuit breakers, and more.', keywords: ['spring-cloud', 'microservices', 'service-discovery']})
CREATE (myBatis:Knowledge {id: 'mybatis', name: 'MyBatis', category: 'framework', difficulty: 'intermediate', description: 'A persistence framework that maps SQL statements to Java methods, offering fine-grained control over SQL queries.', keywords: ['mybatis', 'orm', 'sql-mapping', 'persistence']})
CREATE (myBatisPlus:Knowledge {id: 'mybatis-plus', name: 'MyBatis-Plus', category: 'framework', difficulty: 'intermediate', description: 'An enhancement of MyBatis that provides CRUD operations, pagination, and code generation out of the box.', keywords: ['mybatis-plus', 'orm', 'crud', 'pagination']})
CREATE (jpa:Knowledge {id: 'jpa', name: 'JPA', category: 'framework', difficulty: 'intermediate', description: 'Java Persistence API - a specification for object-relational mapping in Java applications.', keywords: ['jpa', 'orm', 'hibernate', 'persistence']})
CREATE (maven:Knowledge {id: 'maven', name: 'Maven', category: 'tool', difficulty: 'beginner', description: 'A build automation and project management tool for Java projects, using XML-based POM files.', keywords: ['maven', 'build', 'dependency', 'pom']})

// ----------------------------------------------------------
// Frontend Frameworks
// ----------------------------------------------------------
CREATE (vue:Knowledge {id: 'vue3', name: 'Vue.js', category: 'framework', difficulty: 'intermediate', description: 'A progressive JavaScript framework for building user interfaces, with a focus on simplicity and flexibility.', keywords: ['vue', 'vue3', 'frontend', 'reactive']})
CREATE (react:Knowledge {id: 'react', name: 'React', category: 'framework', difficulty: 'intermediate', description: 'A JavaScript library for building user interfaces using a component-based architecture and virtual DOM.', keywords: ['react', 'frontend', 'component', 'jsx']})
CREATE (angular:Knowledge {id: 'angular', name: 'Angular', category: 'framework', difficulty: 'advanced', description: 'A full-featured TypeScript-based framework for building large-scale single-page applications.', keywords: ['angular', 'frontend', 'typescript', 'spa']})
CREATE (nodejs:Knowledge {id: 'nodejs', name: 'Node.js', category: 'runtime', difficulty: 'intermediate', description: 'A JavaScript runtime built on Chrome V8 engine, enabling server-side JavaScript execution.', keywords: ['nodejs', 'node', 'javascript', 'server']})
CREATE (vite:Knowledge {id: 'vite', name: 'Vite', category: 'tool', difficulty: 'beginner', description: 'A fast build tool and dev server for modern web projects, leveraging native ES modules.', keywords: ['vite', 'build', 'dev-server', 'esm']})
CREATE (elementPlus:Knowledge {id: 'element-plus', name: 'Element Plus', category: 'framework', difficulty: 'beginner', description: 'A Vue 3 UI component library with a rich set of enterprise-grade components.', keywords: ['element-plus', 'ui', 'component', 'vue']})
CREATE (vueRouter:Knowledge {id: 'vue-router', name: 'Vue Router', category: 'framework', difficulty: 'intermediate', description: 'The official router for Vue.js, enabling navigation between views in a single-page application.', keywords: ['vue-router', 'router', 'navigation', 'spa']})
CREATE (pinia:Knowledge {id: 'pinia', name: 'Pinia', category: 'framework', difficulty: 'intermediate', description: 'The official state management library for Vue.js, offering a simple and intuitive API.', keywords: ['pinia', 'state-management', 'store', 'vue']})

// ----------------------------------------------------------
// Databases
// ----------------------------------------------------------
CREATE (mysql:Knowledge {id: 'mysql', name: 'MySQL', category: 'database', difficulty: 'beginner', description: 'An open-source relational database management system. One of the most popular databases for web applications.', keywords: ['mysql', 'database', 'relational', 'sql']})
CREATE (redis:Knowledge {id: 'redis', name: 'Redis', category: 'database', difficulty: 'intermediate', description: 'An in-memory data structure store used as a database, cache, and message broker.', keywords: ['redis', 'cache', 'in-memory', 'nosql']})
CREATE (neo4jDb:Knowledge {id: 'neo4j', name: 'Neo4j', category: 'database', difficulty: 'intermediate', description: 'A leading graph database that stores data as nodes and relationships, ideal for connected data queries.', keywords: ['neo4j', 'graph-database', 'cypher', 'nosql']})
CREATE (mongodb:Knowledge {id: 'mongodb', name: 'MongoDB', category: 'database', difficulty: 'intermediate', description: 'A document-oriented NoSQL database using JSON-like documents with flexible schemas.', keywords: ['mongodb', 'nosql', 'document', 'json']})
CREATE (postgresql:Knowledge {id: 'postgresql', name: 'PostgreSQL', category: 'database', difficulty: 'intermediate', description: 'A powerful open-source object-relational database with advanced features like JSONB, CTEs, and full-text search.', keywords: ['postgresql', 'postgres', 'database', 'sql']})

// ----------------------------------------------------------
// DevOps & Tools
// ----------------------------------------------------------
CREATE (docker:Knowledge {id: 'docker', name: 'Docker', category: 'tool', difficulty: 'intermediate', description: 'A platform for building, shipping, and running applications in lightweight containers.', keywords: ['docker', 'container', 'devops', 'deployment']})
CREATE (kubernetes:Knowledge {id: 'kubernetes', name: 'Kubernetes', category: 'tool', difficulty: 'advanced', description: 'An open-source container orchestration platform for automating deployment, scaling, and management of containerized apps.', keywords: ['kubernetes', 'k8s', 'container', 'orchestration']})
CREATE (git:Knowledge {id: 'git', name: 'Git', category: 'tool', difficulty: 'beginner', description: 'A distributed version control system for tracking changes in source code during software development.', keywords: ['git', 'version-control', 'scm', 'branch']})
CREATE (linux:Knowledge {id: 'linux', name: 'Linux', category: 'tool', difficulty: 'intermediate', description: 'An open-source operating system family widely used for servers, development environments, and embedded systems.', keywords: ['linux', 'os', 'unix', 'shell']})
CREATE (nginx:Knowledge {id: 'nginx', name: 'Nginx', category: 'tool', difficulty: 'intermediate', description: 'A high-performance HTTP server, reverse proxy, and load balancer.', keywords: ['nginx', 'web-server', 'reverse-proxy', 'load-balancer']})
CREATE (jenkins:Knowledge {id: 'jenkins', name: 'Jenkins', category: 'tool', difficulty: 'intermediate', description: 'An open-source automation server for continuous integration and continuous delivery (CI/CD).', keywords: ['jenkins', 'ci', 'cd', 'automation']})

// ----------------------------------------------------------
// Computer Science Fundamentals
// ----------------------------------------------------------
CREATE (dataStructures:Knowledge {id: 'data-structures', name: 'Data Structures', category: 'fundamental', difficulty: 'beginner', description: 'Core data organization concepts: arrays, linked lists, trees, graphs, hash tables, stacks, queues.', keywords: ['data-structures', 'array', 'tree', 'graph', 'hash']})
CREATE (algorithms:Knowledge {id: 'algorithms', name: 'Algorithms', category: 'fundamental', difficulty: 'intermediate', description: 'Problem-solving techniques: sorting, searching, dynamic programming, graph algorithms, greedy algorithms.', keywords: ['algorithms', 'sorting', 'searching', 'dynamic-programming']})
CREATE (designPatterns:Knowledge {id: 'design-patterns', name: 'Design Patterns', category: 'fundamental', difficulty: 'intermediate', description: 'Reusable solutions to common software design problems: Singleton, Factory, Observer, Strategy, etc.', keywords: ['design-patterns', 'singleton', 'factory', 'observer', 'strategy']})
CREATE (oop:Knowledge {id: 'oop', name: 'Object-Oriented Programming', category: 'fundamental', difficulty: 'beginner', description: 'A programming paradigm based on objects, encapsulation, inheritance, and polymorphism.', keywords: ['oop', 'object-oriented', 'encapsulation', 'inheritance']})
CREATE (networking:Knowledge {id: 'networking', name: 'Computer Networking', category: 'fundamental', difficulty: 'intermediate', description: 'Fundamentals of network protocols, TCP/IP, HTTP, DNS, and network architecture.', keywords: ['networking', 'tcp', 'http', 'dns', 'protocol']})

// ----------------------------------------------------------
// Architecture & Concepts
// ----------------------------------------------------------
CREATE (restApi:Knowledge {id: 'rest-api', name: 'REST API', category: 'concept', difficulty: 'beginner', description: 'Representational State Transfer - an architectural style for designing networked APIs using HTTP methods.', keywords: ['rest', 'api', 'http', 'endpoint']})
CREATE (microservices:Knowledge {id: 'microservices', name: 'Microservices', category: 'concept', difficulty: 'advanced', description: 'An architectural style that structures an application as a collection of loosely coupled, independently deployable services.', keywords: ['microservices', 'distributed', 'service', 'architecture']})
CREATE (jwt:Knowledge {id: 'jwt', name: 'JWT', category: 'concept', difficulty: 'intermediate', description: 'JSON Web Token - a compact, URL-safe means of representing claims to be transferred between two parties for authentication.', keywords: ['jwt', 'token', 'authentication', 'json']})
CREATE (cicd:Knowledge {id: 'ci-cd', name: 'CI/CD', category: 'concept', difficulty: 'intermediate', description: 'Continuous Integration and Continuous Delivery - practices for automating build, test, and deployment pipelines.', keywords: ['ci', 'cd', 'pipeline', 'automation']})
CREATE (rag:Knowledge {id: 'rag', name: 'RAG', category: 'concept', difficulty: 'advanced', description: 'Retrieval-Augmented Generation - a technique that enhances LLM responses by retrieving relevant context from a knowledge base.', keywords: ['rag', 'retrieval', 'llm', 'knowledge-base']})

// ----------------------------------------------------------
// Relationships: DEPENDS_ON (prerequisite knowledge)
// ----------------------------------------------------------
// Spring ecosystem depends on Java
CREATE (springBoot)-[:DEPENDS_ON {description: 'Spring Boot is built on Java'}]->(java)
CREATE (springMVC)-[:DEPENDS_ON {description: 'Spring MVC requires Java knowledge'}]->(java)
CREATE (springSecurity)-[:DEPENDS_ON {description: 'Spring Security requires Spring Boot knowledge'}]->(springBoot)
CREATE (springData)-[:DEPENDS_ON {description: 'Spring Data requires Spring Boot knowledge'}]->(springBoot)
CREATE (springCloud)-[:DEPENDS_ON {description: 'Spring Cloud builds on Spring Boot'}]->(springBoot)
CREATE (myBatis)-[:DEPENDS_ON {description: 'MyBatis requires Java and SQL knowledge'}]->(java)
CREATE (myBatis)-[:DEPENDS_ON {description: 'MyBatis maps SQL queries'}]->(sql)
CREATE (myBatisPlus)-[:DEPENDS_ON {description: 'MyBatis-Plus extends MyBatis'}]->(myBatis)
CREATE (jpa)-[:DEPENDS_ON {description: 'JPA requires Java knowledge'}]->(java)
CREATE (maven)-[:DEPENDS_ON {description: 'Maven is primarily used for Java projects'}]->(java)

// Frontend depends on JS/TS
CREATE (vue)-[:DEPENDS_ON {description: 'Vue.js is a JavaScript framework'}]->(javascript)
CREATE (react)-[:DEPENDS_ON {description: 'React is a JavaScript library'}]->(javascript)
CREATE (angular)-[:DEPENDS_ON {description: 'Angular is built with TypeScript'}]->(typescript)
CREATE (typescript)-[:DEPENDS_ON {description: 'TypeScript extends JavaScript'}]->(javascript)
CREATE (nodejs)-[:DEPENDS_ON {description: 'Node.js runs JavaScript on the server'}]->(javascript)
CREATE (vite)-[:DEPENDS_ON {description: 'Vite is a JavaScript build tool'}]->(javascript)
CREATE (elementPlus)-[:DEPENDS_ON {description: 'Element Plus is a Vue 3 component library'}]->(vue)
CREATE (vueRouter)-[:DEPENDS_ON {description: 'Vue Router is part of the Vue ecosystem'}]->(vue)
CREATE (pinia)-[:DEPENDS_ON {description: 'Pinia is the Vue state management library'}]->(vue)

// Concepts dependencies
CREATE (restApi)-[:DEPENDS_ON {description: 'REST APIs require networking knowledge'}]->(networking)
CREATE (microservices)-[:DEPENDS_ON {description: 'Microservices often use REST APIs'}]->(restApi)
CREATE (microservices)-[:DEPENDS_ON {description: 'Microservices benefit from containerization'}]->(docker)
CREATE (jwt)-[:DEPENDS_ON {description: 'JWT is used in REST API authentication'}]->(restApi)
CREATE (cicd)-[:DEPENDS_ON {description: 'CI/CD often uses Docker'}]->(docker)
CREATE (cicd)-[:DEPENDS_ON {description: 'CI/CD requires version control'}]->(git)
CREATE (kubernetes)-[:DEPENDS_ON {description: 'Kubernetes orchestrates Docker containers'}]->(docker)

// Database dependencies
CREATE (mysql)-[:DEPENDS_ON {description: 'MySQL uses SQL'}]->(sql)
CREATE (postgresql)-[:DEPENDS_ON {description: 'PostgreSQL uses SQL'}]->(sql)

// Algorithms depend on data structures
CREATE (algorithms)-[:DEPENDS_ON {description: 'Algorithms operate on data structures'}]->(dataStructures)
CREATE (designPatterns)-[:DEPENDS_ON {description: 'Design Patterns require OOP concepts'}]->(oop)

// ----------------------------------------------------------
// Relationships: CONTAINS (sub-topics)
// ----------------------------------------------------------
CREATE (springBoot)-[:CONTAINS {description: 'Spring Boot includes Spring MVC'}]->(springMVC)
CREATE (springBoot)-[:CONTAINS {description: 'Spring Boot integrates Spring Security'}]->(springSecurity)
CREATE (springBoot)-[:CONTAINS {description: 'Spring Boot integrates Spring Data'}]->(springData)

// ----------------------------------------------------------
// Relationships: RELATED_TO
// ----------------------------------------------------------
CREATE (java)-[:RELATED_TO {description: 'Both popular backend languages'}]->(python)
CREATE (vue)-[:RELATED_TO {description: 'Both frontend frameworks'}]->(react)
CREATE (react)-[:RELATED_TO {description: 'Both frontend frameworks'}]->(angular)
CREATE (mysql)-[:RELATED_TO {description: 'Both relational databases'}]->(postgresql)
CREATE (redis)-[:RELATED_TO {description: 'Both used as data stores'}]->(mongodb)
CREATE (docker)-[:RELATED_TO {description: 'Docker and Nginx are often used together'}]->(nginx)
CREATE (jenkins)-[:RELATED_TO {description: 'Both CI/CD tools'}]->(cicd)
CREATE (myBatis)-[:RELATED_TO {description: 'Both Java persistence frameworks'}]->(jpa)
CREATE (springData)-[:RELATED_TO {description: 'Spring Data can use JPA'}]->(jpa)
CREATE (git)-[:RELATED_TO {description: 'Git is fundamental to Linux development'}]->(linux)
CREATE (rag)-[:RELATED_TO {description: 'RAG can leverage knowledge graphs'}]->(neo4jDb)

// ----------------------------------------------------------
// Resource nodes with learning materials
// ----------------------------------------------------------
CREATE (r1:Resource {title: 'Spring Boot Official Documentation', url: 'https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/', type: 'documentation'})
CREATE (r2:Resource {title: 'Vue.js Official Guide', url: 'https://vuejs.org/guide/introduction.html', type: 'documentation'})
CREATE (r3:Resource {title: 'MyBatis-Plus Quick Start', url: 'https://baomidou.com/pages/24112f/', type: 'documentation'})
CREATE (r4:Resource {title: 'Docker Getting Started', url: 'https://docs.docker.com/get-started/', type: 'documentation'})
CREATE (r5:Resource {title: 'Neo4j Cypher Manual', url: 'https://neo4j.com/docs/cypher-manual/current/', type: 'documentation'})
CREATE (r6:Resource {title: 'Java Tutorials - Oracle', url: 'https://docs.oracle.com/javase/tutorial/', type: 'documentation'})
CREATE (r7:Resource {title: 'JavaScript MDN Guide', url: 'https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide', type: 'documentation'})
CREATE (r8:Resource {title: 'Python Official Tutorial', url: 'https://docs.python.org/3/tutorial/', type: 'documentation'})
CREATE (r9:Resource {title: 'Redis Documentation', url: 'https://redis.io/docs/', type: 'documentation'})
CREATE (r10:Resource {title: 'MySQL 8.0 Reference Manual', url: 'https://dev.mysql.com/doc/refman/8.0/en/', type: 'documentation'})
CREATE (r11:Resource {title: 'Design Patterns - Refactoring Guru', url: 'https://refactoring.guru/design-patterns', type: 'tutorial'})
CREATE (r12:Resource {title: 'Git Pro Book', url: 'https://git-scm.com/book/en/v2', type: 'book'})
CREATE (r13:Resource {title: 'Spring Security Reference', url: 'https://docs.spring.io/spring-security/reference/', type: 'documentation'})
CREATE (r14:Resource {title: 'Kubernetes Documentation', url: 'https://kubernetes.io/docs/home/', type: 'documentation'})
CREATE (r15:Resource {title: 'React Official Documentation', url: 'https://react.dev/', type: 'documentation'})
CREATE (r16:Resource {title: 'Data Structures & Algorithms Visualizations', url: 'https://visualgo.net/', type: 'tutorial'})
CREATE (r17:Resource {title: 'REST API Design Best Practices', url: 'https://restfulapi.net/', type: 'tutorial'})
CREATE (r18:Resource {title: 'Microservices Patterns', url: 'https://microservices.io/patterns/', type: 'tutorial'})
CREATE (r19:Resource {title: 'JWT Introduction', url: 'https://jwt.io/introduction', type: 'tutorial'})
CREATE (r20:Resource {title: 'Nginx Beginner Guide', url: 'https://nginx.org/en/docs/beginners_guide.html', type: 'documentation'})

// ----------------------------------------------------------
// Relationships: HAS_RESOURCE
// ----------------------------------------------------------
CREATE (springBoot)-[:HAS_RESOURCE]->(r1)
CREATE (vue)-[:HAS_RESOURCE]->(r2)
CREATE (myBatisPlus)-[:HAS_RESOURCE]->(r3)
CREATE (docker)-[:HAS_RESOURCE]->(r4)
CREATE (neo4jDb)-[:HAS_RESOURCE]->(r5)
CREATE (java)-[:HAS_RESOURCE]->(r6)
CREATE (javascript)-[:HAS_RESOURCE]->(r7)
CREATE (python)-[:HAS_RESOURCE]->(r8)
CREATE (redis)-[:HAS_RESOURCE]->(r9)
CREATE (mysql)-[:HAS_RESOURCE]->(r10)
CREATE (designPatterns)-[:HAS_RESOURCE]->(r11)
CREATE (git)-[:HAS_RESOURCE]->(r12)
CREATE (springSecurity)-[:HAS_RESOURCE]->(r13)
CREATE (kubernetes)-[:HAS_RESOURCE]->(r14)
CREATE (react)-[:HAS_RESOURCE]->(r15)
CREATE (dataStructures)-[:HAS_RESOURCE]->(r16)
CREATE (algorithms)-[:HAS_RESOURCE]->(r16)
CREATE (restApi)-[:HAS_RESOURCE]->(r17)
CREATE (microservices)-[:HAS_RESOURCE]->(r18)
CREATE (jwt)-[:HAS_RESOURCE]->(r19)
CREATE (nginx)-[:HAS_RESOURCE]->(r20)

;