import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  interviewApi, type AdminQuestion, type Assignment, type ComposeJob, type Interview,
  type KnowledgeCollection, type KnowledgeDocument, type Profile, type QuestionType,
} from '../api/interviewApi'
import { useAuth } from '../auth/AuthProvider'

export const ecosystemTechnologies = {
  JAVA: [
    'Java', 'Spring Boot', 'Spring Framework', 'Spring MVC', 'Spring Security',
    'Spring Data JPA', 'Hibernate', 'Jakarta EE', 'Quarkus', 'Micronaut',
    'Maven', 'Gradle', 'JUnit', 'Mockito', 'Apache Kafka',
  ],
  PYTHON: [
    'Python', 'Django', 'GraphQL', 'Strawberry GraphQL', 'Flask', 'FastAPI', 'Pydantic', 'SQLAlchemy',
    'Celery', 'pytest', 'NumPy', 'pandas', 'scikit-learn', 'PyTorch',
    'TensorFlow', 'LangChain', 'Jupyter',
  ],
  UI: [
    'HTML5', 'CSS3', 'JavaScript', 'TypeScript', 'React', 'Angular', 'Vue.js',
    'Svelte', 'Next.js', 'Nuxt', 'Vite', 'Tailwind CSS', 'Bootstrap',
    'Material UI', 'Redux', 'Jest', 'Vitest', 'Cypress', 'Playwright',
  ],
  DATABASE: [
    'PostgreSQL', 'MySQL', 'MariaDB', 'Oracle Database', 'Microsoft SQL Server',
    'SQLite', 'MongoDB', 'Redis', 'Apache Cassandra', 'Amazon DynamoDB',
    'Elasticsearch', 'Neo4j', 'CockroachDB', 'Snowflake', 'Google BigQuery',
  ],
  AI: [
    'Generative AI', 'Large Language Models', 'Prompt Engineering',
    'Retrieval-Augmented Generation (RAG)', 'AI Agents', 'Model Context Protocol (MCP)',
    'OpenAI API', 'Anthropic API', 'LangChain', 'LangGraph', 'LlamaIndex',
    'Hugging Face Transformers', 'PyTorch', 'TensorFlow', 'Vector Databases',
    'Embeddings', 'Model Evaluation', 'Fine-tuning', 'LiteLLM', 'Ollama', 'MLflow',
  ],
  SYSTEM_DESIGN: [
    'Scalability', 'High Availability', 'Load Balancing', 'Caching', 'CDN',
    'Database Sharding', 'Replication', 'CAP Theorem', 'Consistency and Consensus',
    'Message Queues', 'Event-Driven Architecture', 'Microservices', 'API Gateway',
    'Rate Limiting', 'Idempotency', 'Distributed Transactions', 'Data Partitioning',
    'Fault Tolerance', 'Observability', 'Capacity Estimation', 'Design Trade-offs',
  ],
  CI_CD: [
    'CI/CD Fundamentals', 'Jenkins', 'GitHub Actions', 'GitLab CI/CD', 'CircleCI',
    'Azure DevOps Pipelines', 'Argo CD', 'Flux CD', 'Tekton', 'Spinnaker',
    'Docker', 'Kubernetes', 'Helm', 'Kustomize', 'Terraform', 'Ansible',
    'SonarQube', 'Trivy', 'JFrog Artifactory', 'GitOps',
    'Blue-Green Deployment', 'Canary Deployment', 'Pipeline as Code',
    'Slack', 'Microsoft Teams', 'Discord', 'Google Chat', 'Mattermost', 'Rocket.Chat',
  ],
  DESIGN_PRINCIPLES: [
    'Single Responsibility Principle', 'Open/Closed Principle', 'Liskov Substitution Principle',
    'Interface Segregation Principle', 'Dependency Inversion Principle', 'DRY', 'KISS', 'YAGNI',
    'Separation of Concerns', 'Cohesion and Coupling', 'Composition over Inheritance',
    'Encapsulation', 'Law of Demeter', 'Dependency Injection', 'Fail Fast',
    'Principle of Least Astonishment', 'Clean Code',
  ],
  DESIGN_PATTERNS: [
    'Design Patterns Overview', 'Singleton', 'Factory Method', 'Abstract Factory', 'Builder',
    'Prototype', 'Adapter', 'Decorator', 'Facade', 'Proxy', 'Composite', 'Bridge',
    'Observer', 'Strategy', 'Command', 'State', 'Template Method', 'Iterator', 'Mediator',
    'Chain of Responsibility', 'Visitor', 'Repository Pattern', 'Model-View-Controller (MVC)',
  ],
  LEADERSHIP: [
    'Team Management', 'Mentoring and Coaching', 'Conflict Resolution', 'Decision Making',
    'Delegation', 'Communication', 'Stakeholder Management', 'Strategic Thinking',
    'Emotional Intelligence', 'Giving and Receiving Feedback', 'Motivation and Influence',
    'Ownership and Accountability', 'Change Management', 'Hiring and Interviewing',
    'Cross-functional Collaboration', 'Time and Priority Management',
  ],
  JAVA_AI: [
    'Spring AI', 'LangChain4j', 'Deep Java Library (DJL)', 'Semantic Kernel (Java)',
    'RAG with Spring AI', 'Embeddings in Java', 'Vector Stores (Java)',
    'Tool/Function Calling (Java)', 'Structured Output (Java)', 'Chat Memory',
    'Prompt Templates (Java)', 'Spring AI Advisors', 'MCP Java SDK',
    'Streaming Responses (Java)', 'Ollama on the JVM', 'LLM Observability (Micrometer)',
  ],
  CLOUD: [
    'AWS', 'AWS Lambda', 'Amazon S3', 'Amazon EKS', 'Amazon Bedrock', 'Amazon RDS',
    'Microsoft Azure', 'Azure Functions', 'Azure OpenAI', 'Azure Kubernetes Service',
    'Google Cloud Platform', 'Cloud Run', 'Vertex AI', 'Serverless Architecture',
    'Infrastructure as Code', 'Cloud IAM', 'Cloud Cost Optimization', 'Multi-Cloud Strategy',
  ],
  ARCHITECTURE: [
    'Domain-Driven Design', 'Bounded Contexts', 'Aggregates and Entities', 'Ubiquitous Language',
    'Hexagonal Architecture', 'Clean Architecture', 'CQRS', 'Event Sourcing',
    'Microservices Decomposition', 'Monolith vs Microservices', 'Modular Monolith',
    'Event-Driven Architecture', 'Saga Pattern', 'Strangler Fig Pattern', 'C4 Model',
    'Architecture Decision Records', 'Architecture Trade-off Analysis', 'API Gateway Pattern',
  ],
  SECURITY: [
    'Application Security', 'OWASP Top 10', 'OAuth 2.0', 'OpenID Connect', 'JWT',
    'Zero Trust Architecture', 'Secrets Management', 'Key Management (KMS)',
    'Encryption at Rest and in Transit', 'TLS and mTLS', 'Threat Modeling', 'SAST and DAST',
    'Supply-Chain Security', 'API Security', 'Identity and Access Management', 'GDPR',
    'SOC 2', 'Secure Coding Practices',
  ],
  INTERVIEW_STYLE: [
    'Tricky Java Questions', 'Concurrency Gotchas', 'JVM Memory Puzzles', 'Collections Edge Cases',
    'Spring Boot Pitfalls', 'Debugging Scenarios', 'Production Incident Scenarios',
    'Performance Troubleshooting', 'System Design Use Cases', 'Real-world Architecture Cases',
    'API Design Trade-off Cases', 'Distributed Systems Failure Scenarios',
    'Security Vulnerability Scenarios', 'Data Modeling Cases', 'Refactoring Scenarios',
    'GenAI Use-Case Design', 'Behavioral Situational (STAR)', 'Estimation Questions',
  ],
  OBSERVABILITY: [
    'Observability Fundamentals', 'Metrics', 'Logging', 'Distributed Tracing', 'OpenTelemetry',
    'Prometheus', 'Grafana', 'Loki', 'Tempo', 'Jaeger', 'Micrometer', 'SLI, SLO, SLA',
    'Error Budgets', 'Golden Signals', 'Alerting', 'Incident Response', 'Postmortems',
    'On-Call Practices', 'Chaos Engineering', 'Capacity Planning',
  ],
  DATA_ENGINEERING: [
    'Data Pipelines', 'ETL and ELT', 'Batch Processing', 'Stream Processing', 'Kafka Streams',
    'Apache Flink', 'Apache Spark', 'Change Data Capture', 'Data Lakes', 'Data Warehouses',
    'Data Lakehouse', 'Apache Airflow', 'dbt', 'Delta Lake', 'Schema Evolution',
    'Feature Stores', 'Data Quality', 'Data Governance', 'Data Mesh',
  ],
  AI_GOVERNANCE: [
    'Responsible AI', 'AI Governance', 'LLM Evaluation', 'Guardrails', 'Hallucination Mitigation',
    'Prompt Injection Defense', 'PII and Data Privacy', 'Bias and Fairness', 'Explainability',
    'Model Registry', 'Model Monitoring', 'Drift Detection', 'LLMOps', 'AI Red Teaming',
    'Content Moderation', 'Human-in-the-Loop', 'AI Cost and Latency Optimization', 'EU AI Act',
    'Model Cards', 'Audit and Traceability',
  ],
  DATA_STRUCTURES: [
    'Arrays', 'Strings', 'Linked Lists', 'Stacks', 'Queues', 'Hash Tables', 'Sets', 'Trees',
    'Binary Search Trees', 'Heaps and Priority Queues', 'Tries', 'Graphs',
    'Balanced Trees (AVL, Red-Black)', 'Segment Trees', 'Fenwick Tree (BIT)',
    'Union-Find (Disjoint Set)', 'LRU Cache', 'Skip Lists', 'Bloom Filters',
  ],
  ALGORITHMS: [
    'Time and Space Complexity', 'Sorting Algorithms', 'Searching Algorithms', 'Binary Search',
    'Two Pointers', 'Sliding Window', 'Recursion', 'Backtracking', 'Divide and Conquer',
    'Greedy Algorithms', 'Dynamic Programming', 'Graph Traversal (BFS and DFS)',
    'Shortest Path (Dijkstra, Bellman-Ford)', 'Minimum Spanning Tree', 'Topological Sort',
    'Bit Manipulation', 'String Matching', 'Hashing Techniques',
  ],
  PERFORMANCE: [
    'Garbage Collection Tuning', 'JIT Compilation', 'Heap and Memory Profiling',
    'Memory Leak Detection', 'Escape Analysis', 'Java Flight Recorder (JFR)', 'async-profiler',
    'VisualVM', 'JMH Microbenchmarking', 'Flame Graphs', 'JMeter', 'Gatling', 'k6',
    'Load and Stress Testing', 'Soak Testing', 'Connection Pooling', 'Caching Strategies',
    'N+1 Query Problem', 'Database Query Optimization', 'Lazy vs Eager Loading', 'Batching',
    'Virtual Threads (Project Loom)', 'Reactive and Non-Blocking (WebFlux)', 'Thread-Pool Tuning',
    'Backpressure', 'Latency vs Throughput', 'Tail Latency (p99)', 'Performance Budgets',
    'Application Performance Monitoring (APM)', 'Compression and Payload Size', 'Amdahl’s Law',
  ],
  TESTING: [
    'Testing Fundamentals', 'Test Pyramid', 'Unit Testing', 'Integration Testing',
    'End-to-End Testing', 'Test-Driven Development (TDD)', 'Behavior-Driven Development (BDD)',
    'JUnit 5', 'Mockito', 'Testcontainers', 'Contract Testing (Pact)', 'Mutation Testing',
    'Property-Based Testing', 'Code Coverage', 'Mocking and Stubbing', 'Test Doubles',
    'Test Automation Strategy', 'Regression Testing', 'Flaky Test Management',
  ],
  API_DESIGN: [
    'API Design Fundamentals', 'REST', 'RESTful Maturity (Richardson)', 'GraphQL', 'gRPC',
    'API Versioning', 'OpenAPI and Swagger', 'Idempotent APIs', 'Pagination and Filtering',
    'HATEOAS', 'Webhooks', 'API Error Handling', 'Authentication for APIs', 'API Rate Limiting',
    'API Contracts', 'Enterprise Integration Patterns', 'Message-Based Integration',
    'Backend for Frontend (BFF)',
  ],
  OS_LINUX: [
    'Operating System Fundamentals', 'Processes and Threads', 'Process Scheduling',
    'Memory Management', 'Virtual Memory', 'Paging and Segmentation', 'File Systems',
    'Inter-Process Communication', 'System Calls', 'Concurrency and Deadlocks',
    'Linux Command Line', 'Shell Scripting', 'Signals', 'Namespaces and cgroups',
    'CPU and I/O Scheduling', 'Kernel vs User Space',
  ],
  NETWORKING: [
    'Networking Fundamentals', 'OSI Model', 'TCP/IP', 'UDP', 'HTTP/1.1', 'HTTP/2',
    'HTTP/3 and QUIC', 'HTTPS and TLS Handshake', 'DNS', 'WebSockets', 'Load Balancing Algorithms',
    'Reverse Proxies', 'Content Delivery Networks', 'Firewalls and NAT', 'Sockets',
    'Network Latency and Bandwidth',
  ],
  MACHINE_LEARNING: [
    'Machine Learning Fundamentals', 'Supervised Learning', 'Unsupervised Learning', 'Regression',
    'Classification', 'Clustering', 'Decision Trees and Random Forests', 'Gradient Boosting (XGBoost)',
    'Feature Engineering', 'Data Preprocessing', 'Model Evaluation Metrics',
    'Overfitting and Regularization', 'Cross-Validation', 'Neural Networks Basics',
    'Model Training and Tuning', 'MLOps', 'Model Deployment',
  ],
  CODE_QUALITY: [
    'Coding Standards and Style Guides', 'Naming Conventions', 'Code Formatting (Spotless)',
    'EditorConfig', 'Checkstyle', 'PMD', 'SpotBugs', 'SonarLint', 'ESLint and Prettier',
    'Static Code Analysis', 'Code Review Practices', 'Pull Request Etiquette',
    'Javadoc and Documentation', 'Readability and Maintainability', 'Cyclomatic Complexity',
    'Technical Debt Management', 'Linting Gates in CI', 'Null-Safety and Optional',
    'Immutability Conventions', 'Error-Handling and Logging Standards',
  ],
  MISCELLANEOUS: [
    'GraphQL', 'Jolt', 'HashiCorp Vault', 'Keycloak', 'Apache Camel',
    'RabbitMQ', 'NGINX', 'Postman', 'OpenAPI and Swagger', 'Webhooks',
  ],
  WORKFLOW: [
    'Activiti', 'Flowable', 'Camunda', 'jBPM', 'Kogito', 'Bonita', 'ProcessMaker',
    'Operaton', 'CIB seven', 'Imixs-Workflow', 'Oracle BPM Suite',
    'IBM Business Automation Workflow', 'Bizagi', 'SAP Signavio',
    'Temporal', 'Netflix Conductor', 'AWS Step Functions',
    'Azure Durable Functions', 'Argo Workflows', 'Apache Airflow',
    'Prefect', 'Dagster', 'n8n',
  ],
} as const

export type Ecosystem = keyof typeof ecosystemTechnologies

export const ecosystemLabels: Record<Ecosystem, string> = {
  JAVA: 'Java', PYTHON: 'Python', UI: 'UI', DATABASE: 'Database', AI: 'AI',
  SYSTEM_DESIGN: 'System Design', CI_CD: 'CI/CD', DESIGN_PRINCIPLES: 'Software Design Principles',
  DESIGN_PATTERNS: 'Design Patterns', LEADERSHIP: 'Leadership Quality',
  JAVA_AI: 'GenAI on the JVM (Java)', CLOUD: 'Cloud & Serverless',
  ARCHITECTURE: 'Software Architecture & DDD', SECURITY: 'Security & Compliance',
  INTERVIEW_STYLE: 'Tricky & Use-Case Questions', OBSERVABILITY: 'Observability & SRE',
  DATA_ENGINEERING: 'Data & Streaming Engineering', AI_GOVERNANCE: 'AI Governance & Responsible AI',
  DATA_STRUCTURES: 'Data Structures', ALGORITHMS: 'Algorithms',
  PERFORMANCE: 'Performance Engineering', TESTING: 'Testing & Quality Engineering',
  API_DESIGN: 'API Design & Integration', OS_LINUX: 'Operating Systems & Linux',
  NETWORKING: 'Networking & Protocols', MACHINE_LEARNING: 'Machine Learning (Classical & MLOps)',
  CODE_QUALITY: 'Code Quality & Standards', MISCELLANEOUS: 'Miscellaneous',
  WORKFLOW: 'Workflow Automation & Orchestration',
}

const sortedEcosystems = (Object.keys(ecosystemLabels) as Ecosystem[])
  .sort((a, b) => ecosystemLabels[a].localeCompare(ecosystemLabels[b]))

const technologyDescriptions: Record<string, string> = {
  Java: 'A general-purpose JVM language widely used for enterprise and backend systems.',
  'Spring Boot': 'A Spring framework for building production-ready Java services with convention-based configuration.',
  'Spring Framework': 'A modular Java application framework providing dependency injection and infrastructure support.',
  'Spring MVC': 'Spring’s web framework for HTTP controllers, request handling, and server-rendered applications.',
  'Spring Security': 'A framework for authentication, authorization, and protection against common application attacks.',
  'Spring Data JPA': 'A repository abstraction that simplifies relational data access in Spring applications.',
  Hibernate: 'An object-relational mapping framework that maps Java entities to relational database tables.',
  'Jakarta EE': 'A collection of enterprise Java specifications for web, persistence, messaging, and distributed systems.',
  Quarkus: 'A Kubernetes-focused Java framework optimized for fast startup and low memory usage.',
  Micronaut: 'A JVM framework using compile-time dependency injection for lightweight services and serverless applications.',
  Maven: 'A Java build and dependency-management tool based on declarative project configuration.',
  Gradle: 'A flexible build automation tool using incremental execution and programmable build scripts.',
  JUnit: 'The standard Java unit-testing framework for defining and running automated tests.',
  Mockito: 'A Java mocking framework used to isolate dependencies in unit tests.',
  'Apache Kafka': 'A distributed event-streaming platform for high-throughput messaging and data pipelines.',
  Python: 'A general-purpose language popular for web development, automation, data science, and AI.',
  Django: 'A batteries-included Python web framework with ORM, routing, forms, and administration features.',
  'Strawberry GraphQL': 'A Python GraphQL library using type annotations, with integrations for Django, FastAPI, Flask, and ASGI.',
  Jolt: 'A Java library for declaratively transforming JSON documents with chainable specifications.',
  'HashiCorp Vault': 'A secrets-management platform for credentials, encryption, dynamic secrets, and access auditing.',
  Keycloak: 'An identity and access-management platform supporting SSO, OAuth 2.0, and OpenID Connect.',
  'Apache Camel': 'An integration framework implementing enterprise integration patterns through routes and connectors.',
  RabbitMQ: 'A message broker supporting reliable queues, routing, acknowledgements, and asynchronous workloads.',
  NGINX: 'A high-performance web server, reverse proxy, load balancer, and API gateway building block.',
  Postman: 'An API development platform for designing, testing, documenting, and automating API workflows.',
  Slack: 'A team messaging platform with incoming webhooks, bot apps, and Block Kit — widely used for ChatOps and CI/CD deploy notifications.',
  'Microsoft Teams': 'A Microsoft 365 collaboration platform with channels, bots, and incoming webhooks — used for enterprise ChatOps and CI/CD notifications.',
  Discord: 'A community and team chat platform with servers, channels, and webhooks — commonly used for bot integrations and CI/CD alerts.',
  'Google Chat': 'Google Workspace team messaging with spaces, bots, and webhooks — used for ChatOps and pipeline notifications.',
  Mattermost: 'An open-source, self-hostable team messaging platform with webhooks and slash commands — a private-cloud Slack alternative for ChatOps.',
  'Rocket.Chat': 'An open-source team communication platform with webhooks, bots, and integrations — a self-hosted Slack alternative.',
  Flask: 'A lightweight Python web framework suited to small services and flexible application architectures.',
  FastAPI: 'A typed Python API framework with automatic validation and OpenAPI documentation.',
  Pydantic: 'A Python library for typed data validation, parsing, and application settings.',
  SQLAlchemy: 'A Python SQL toolkit and ORM supporting expressive relational database access.',
  Celery: 'A distributed Python task queue for background jobs and scheduled processing.',
  pytest: 'A Python testing framework offering concise tests, fixtures, and a rich plugin ecosystem.',
  NumPy: 'A numerical-computing library providing fast multidimensional arrays and mathematical operations.',
  pandas: 'A data-analysis library built around tabular DataFrames and data transformation tools.',
  'scikit-learn': 'A machine-learning library for preprocessing, classical models, evaluation, and pipelines.',
  PyTorch: 'A tensor and deep-learning framework widely used for research and production AI workloads.',
  TensorFlow: 'An end-to-end machine-learning platform for training and serving neural-network models.',
  LangChain: 'A framework for composing LLM applications from models, tools, retrieval, and memory.',
  Jupyter: 'An interactive notebook environment for executable code, analysis, and documentation.',
  HTML5: 'The semantic markup standard used to structure modern web pages and applications.',
  CSS3: 'The styling language used for responsive layout, presentation, and animation on the web.',
  JavaScript: 'The primary programming language for interactive browser and full-stack web applications.',
  TypeScript: 'A typed superset of JavaScript that improves tooling and large-codebase maintainability.',
  React: 'A component-based JavaScript library for building interactive user interfaces.',
  Angular: 'A full-featured TypeScript framework for structured, large-scale web applications.',
  'Vue.js': 'A progressive JavaScript framework for reactive, component-based user interfaces.',
  Svelte: 'A compiler-based UI framework that produces small, efficient browser code.',
  'Next.js': 'A React framework supporting routing, server rendering, data fetching, and full-stack applications.',
  Nuxt: 'A Vue framework for server rendering, routing, and full-stack web development.',
  Vite: 'A fast frontend development server and production build tool.',
  'Tailwind CSS': 'A utility-first CSS framework for composing designs directly in markup.',
  Bootstrap: 'A responsive UI toolkit providing layout utilities and reusable components.',
  'Material UI': 'A React component library implementing Google’s Material Design system.',
  Redux: 'A predictable state container commonly used for complex JavaScript application state.',
  Jest: 'A JavaScript testing framework with assertions, mocking, and snapshot testing.',
  Vitest: 'A Vite-native unit-testing framework with fast execution and Jest-compatible APIs.',
  Cypress: 'A browser-based end-to-end testing framework with interactive debugging.',
  Playwright: 'A cross-browser automation framework for reliable end-to-end testing.',
  PostgreSQL: 'An open-source relational database known for correctness, extensibility, and advanced SQL.',
  MySQL: 'A widely deployed relational database used by web and transactional applications.',
  MariaDB: 'A community-developed MySQL-compatible relational database.',
  'Oracle Database': 'An enterprise relational database with extensive security, availability, and analytics features.',
  'Microsoft SQL Server': 'Microsoft’s relational database platform with transactional and analytics tooling.',
  SQLite: 'An embedded, serverless relational database stored in a single local file.',
  MongoDB: 'A document database that stores flexible JSON-like records.',
  Redis: 'An in-memory data store used for caching, messaging, sessions, and fast data structures.',
  'Apache Cassandra': 'A distributed wide-column database designed for availability and large write workloads.',
  'Amazon DynamoDB': 'A managed key-value and document database with automatic scaling on AWS.',
  Elasticsearch: 'A distributed search and analytics engine built around indexed documents.',
  Neo4j: 'A graph database designed for highly connected data and relationship queries.',
  CockroachDB: 'A distributed SQL database designed for horizontal scale and resilience.',
  Snowflake: 'A managed cloud data platform for warehousing, analytics, and data sharing.',
  'Google BigQuery': 'A serverless cloud data warehouse for large-scale SQL analytics.',
  'Generative AI': 'AI systems that create new text, images, code, or other content from learned patterns.',
  'Large Language Models': 'Neural language models trained at scale to understand and generate natural language.',
  'Prompt Engineering': 'The practice of designing instructions and context that guide model behavior.',
  'Retrieval-Augmented Generation (RAG)': 'An architecture that grounds model responses in retrieved external knowledge.',
  'AI Agents': 'Model-driven systems that reason, use tools, and take actions toward a goal.',
  'Model Context Protocol (MCP)': 'An open protocol for connecting AI applications to tools and contextual data.',
  'OpenAI API': 'An API platform for integrating OpenAI language, reasoning, audio, and multimodal models.',
  'Anthropic API': 'An API platform for building applications with Anthropic Claude models.',
  LangGraph: 'A graph-based orchestration framework for stateful and multi-step agent workflows.',
  LlamaIndex: 'A framework for connecting LLM applications with private and external data sources.',
  'Hugging Face Transformers': 'A library providing pretrained transformer models and training utilities.',
  'Vector Databases': 'Databases optimized for storing embeddings and performing similarity search.',
  Embeddings: 'Dense numerical representations that capture semantic similarity between data items.',
  'Model Evaluation': 'The systematic measurement of model quality, safety, reliability, and task performance.',
  'Fine-tuning': 'Additional model training on specialized examples to adapt behavior or domain knowledge.',
  LiteLLM: 'A model gateway offering a consistent API across multiple LLM providers.',
  Ollama: 'A local runtime for downloading and serving open-weight language models.',
  MLflow: 'A platform for tracking experiments, packaging models, and managing ML lifecycles.',
  Scalability: 'Designing a system to handle growing load by scaling vertically or horizontally.',
  'High Availability': 'Keeping a system operational with minimal downtime through redundancy and failover.',
  'Load Balancing': 'Distributing incoming traffic across multiple servers to improve throughput and reliability.',
  Caching: 'Storing frequently accessed data in fast storage to reduce latency and backend load.',
  CDN: 'A content delivery network that serves assets from edge locations close to users.',
  'Database Sharding': 'Partitioning data across multiple databases to scale writes and storage horizontally.',
  Replication: 'Copying data across nodes for availability, read scaling, and durability.',
  'CAP Theorem': 'The trade-off between consistency, availability, and partition tolerance in distributed systems.',
  'Consistency and Consensus': 'Agreement protocols (e.g. Raft, Paxos) and consistency models for distributed state.',
  'Message Queues': 'Asynchronous, decoupled communication between services via durable message brokers.',
  'Event-Driven Architecture': 'Designing systems around the production, detection, and reaction to events.',
  Microservices: 'Structuring an application as small, independently deployable, bounded services.',
  'API Gateway': 'A single entry point that routes, throttles, and secures requests to backend services.',
  'Rate Limiting': 'Controlling request volume per client to protect services and ensure fair usage.',
  Idempotency: 'Ensuring repeated operations produce the same result, key to safe retries.',
  'Distributed Transactions': 'Coordinating atomic changes across services using patterns like saga and outbox.',
  'Data Partitioning': 'Splitting data by key or range to distribute load and enable horizontal scale.',
  'Fault Tolerance': 'Designing systems to keep working correctly despite component failures.',
  Observability: 'Understanding system state through metrics, logs, and distributed traces.',
  'Capacity Estimation': 'Sizing traffic, storage, and bandwidth to plan infrastructure and bottlenecks.',
  'Design Trade-offs': 'Reasoning about competing goals such as latency, consistency, cost, and complexity.',
  'CI/CD Fundamentals': 'Core practices of continuous integration and delivery: automated build, test, and release on every change.',
  Jenkins: 'An extensible open-source automation server for building, testing, and deploying software pipelines.',
  'GitHub Actions': 'GitHub-native CI/CD that runs YAML-defined workflows triggered by repository events.',
  'GitLab CI/CD': 'GitLab’s built-in pipeline engine configured via .gitlab-ci.yml with runners, stages, and environments.',
  CircleCI: 'A cloud CI/CD platform running fast, parallelized pipelines defined in config.yml.',
  'Azure DevOps Pipelines': 'Microsoft’s CI/CD service for multi-stage YAML pipelines across build, test, and release.',
  'Argo CD': 'A declarative GitOps continuous-delivery tool that syncs Kubernetes state from a Git repository.',
  'Flux CD': 'A GitOps operator that keeps Kubernetes clusters reconciled with configuration stored in Git.',
  Tekton: 'A Kubernetes-native framework for defining CI/CD pipelines as custom resources.',
  Spinnaker: 'A multi-cloud continuous-delivery platform for advanced deployment strategies and pipeline management.',
  Docker: 'A container platform for packaging applications and dependencies into portable, reproducible images.',
  Kubernetes: 'A container orchestration system that automates deployment, scaling, and management of workloads.',
  Helm: 'A Kubernetes package manager that templates and versions application deployments as charts.',
  Kustomize: 'A template-free way to customize Kubernetes manifests through declarative overlays.',
  Terraform: 'An infrastructure-as-code tool that provisions cloud resources from declarative configuration.',
  Ansible: 'An agentless automation tool for configuration management, provisioning, and deployment.',
  SonarQube: 'A static-analysis platform that gates pipelines on code quality, coverage, and security findings.',
  Trivy: 'A scanner that detects vulnerabilities and misconfigurations in containers, code, and IaC.',
  'JFrog Artifactory': 'A universal artifact repository manager for binaries, packages, and container images.',
  GitOps: 'An operating model that uses Git as the single source of truth for declarative infrastructure and apps.',
  'Blue-Green Deployment': 'A release strategy that switches traffic between two identical environments to minimize downtime.',
  'Canary Deployment': 'A progressive rollout that shifts a small traffic share to a new version before full release.',
  'Pipeline as Code': 'Defining build and deployment pipelines in version-controlled files alongside application code.',
  'Single Responsibility Principle': 'A class or module should have one, and only one, reason to change.',
  'Open/Closed Principle': 'Software entities should be open for extension but closed for modification.',
  'Liskov Substitution Principle': 'Subtypes must be substitutable for their base types without breaking behavior.',
  'Interface Segregation Principle': 'Clients should not be forced to depend on interfaces they do not use.',
  'Dependency Inversion Principle': 'Depend on abstractions, not concretions; high-level modules should not depend on low-level ones.',
  DRY: 'Don’t Repeat Yourself — every piece of knowledge should have a single, authoritative representation.',
  KISS: 'Keep It Simple — favor the simplest design that solves the problem.',
  YAGNI: 'You Aren’t Gonna Need It — don’t build functionality until it is actually required.',
  'Separation of Concerns': 'Divide a system into distinct sections, each addressing a separate responsibility.',
  'Cohesion and Coupling': 'Aim for high cohesion within modules and low coupling between them.',
  'Composition over Inheritance': 'Prefer assembling behavior from components over deep inheritance hierarchies.',
  Encapsulation: 'Hide internal state and expose behavior through a well-defined interface.',
  'Law of Demeter': 'A unit should only talk to its immediate collaborators, not reach through them.',
  'Dependency Injection': 'Supply a component’s dependencies from outside rather than constructing them internally.',
  'Fail Fast': 'Detect and surface errors as early as possible instead of continuing in a bad state.',
  'Principle of Least Astonishment': 'Designs should behave the way users and developers reasonably expect.',
  'Clean Code': 'Readable, well-named, small-function code that is easy to understand and change.',
  'Design Patterns Overview': 'Reusable, named solutions to recurring software design problems.',
  Singleton: 'Ensures a class has a single instance with a global access point.',
  'Factory Method': 'Defers object creation to subclasses via a common creation interface.',
  'Abstract Factory': 'Creates families of related objects without specifying their concrete classes.',
  Builder: 'Constructs a complex object step by step, separating construction from representation.',
  Prototype: 'Creates new objects by cloning an existing instance.',
  Adapter: 'Converts one interface into another that clients expect.',
  Decorator: 'Adds responsibilities to an object dynamically without changing its class.',
  Facade: 'Provides a simplified interface to a larger, complex subsystem.',
  Proxy: 'A surrogate that controls access to another object.',
  Composite: 'Treats individual objects and compositions of objects uniformly in a tree structure.',
  Bridge: 'Decouples an abstraction from its implementation so they can vary independently.',
  Observer: 'Notifies dependent objects automatically when a subject’s state changes.',
  Strategy: 'Encapsulates interchangeable algorithms behind a common interface.',
  Command: 'Encapsulates a request as an object, enabling queuing, logging, and undo.',
  State: 'Lets an object alter its behavior when its internal state changes.',
  'Template Method': 'Defines the skeleton of an algorithm, deferring some steps to subclasses.',
  Iterator: 'Provides sequential access to elements of a collection without exposing its structure.',
  Mediator: 'Centralizes complex communication between related objects.',
  'Chain of Responsibility': 'Passes a request along a chain of handlers until one handles it.',
  Visitor: 'Adds operations to object structures without modifying their classes.',
  'Repository Pattern': 'Abstracts data access behind a collection-like interface.',
  'Model-View-Controller (MVC)': 'Separates an application into model, view, and controller responsibilities.',
  'Team Management': 'Leading, organizing, and developing a team to deliver effectively.',
  'Mentoring and Coaching': 'Growing others’ skills and careers through guidance and feedback.',
  'Conflict Resolution': 'Addressing disagreements constructively to reach workable outcomes.',
  'Decision Making': 'Weighing options and trade-offs to choose a course of action under uncertainty.',
  Delegation: 'Assigning ownership and authority appropriately to develop the team and scale impact.',
  Communication: 'Conveying ideas clearly and listening actively across audiences.',
  'Stakeholder Management': 'Aligning expectations and building trust with partners across the organization.',
  'Strategic Thinking': 'Connecting day-to-day work to longer-term goals and direction.',
  'Emotional Intelligence': 'Recognizing and managing one’s own and others’ emotions effectively.',
  'Giving and Receiving Feedback': 'Exchanging candid, actionable feedback to drive improvement.',
  'Motivation and Influence': 'Inspiring and persuading others without relying solely on authority.',
  'Ownership and Accountability': 'Taking responsibility for outcomes and following through on commitments.',
  'Change Management': 'Guiding teams through organizational or technical change.',
  'Hiring and Interviewing': 'Assessing candidates fairly and building strong, diverse teams.',
  'Cross-functional Collaboration': 'Working effectively across disciplines and organizational boundaries.',
  'Time and Priority Management': 'Focusing effort on the highest-impact work amid competing demands.',
  'Spring AI': 'Spring’s framework for building AI applications with portable model, RAG, and tool abstractions.',
  LangChain4j: 'A Java library for LLM apps — chains, agents, RAG, memory, and tool calling on the JVM.',
  'Deep Java Library (DJL)': 'An engine-agnostic deep-learning library for training and inference in Java.',
  'Semantic Kernel (Java)': 'Microsoft’s SDK for orchestrating LLMs, plugins, and planners from Java.',
  'RAG with Spring AI': 'Building retrieval-augmented generation pipelines using Spring AI’s document and vector APIs.',
  'Embeddings in Java': 'Generating and using vector embeddings for search and similarity from Java.',
  'Vector Stores (Java)': 'Integrating pgvector, Redis, Milvus, or Pinecone through Java/Spring vector-store clients.',
  'Tool/Function Calling (Java)': 'Letting an LLM invoke Java methods/functions as tools during a conversation.',
  'Structured Output (Java)': 'Coercing LLM responses into typed Java objects or JSON schemas.',
  'Chat Memory': 'Persisting and windowing conversation history for multi-turn LLM interactions.',
  'Prompt Templates (Java)': 'Parameterized, reusable prompt definitions managed in Java.',
  'Spring AI Advisors': 'Interceptors that augment Spring AI calls with RAG, memory, logging, or safety.',
  'MCP Java SDK': 'Building Model Context Protocol clients and servers in Java to expose tools and resources.',
  'Streaming Responses (Java)': 'Consuming token-streamed LLM output reactively (Flux) in Java services.',
  'Ollama on the JVM': 'Running and calling local models via Ollama from Java/Spring applications.',
  'LLM Observability (Micrometer)': 'Instrumenting token usage, latency, and cost of LLM calls with Micrometer metrics and traces.',
  AWS: 'Amazon Web Services — the market-leading cloud platform of compute, storage, and managed services.',
  'AWS Lambda': 'Serverless functions that run code on demand without managing servers.',
  'Amazon S3': 'Highly durable object storage for files, backups, and data lakes.',
  'Amazon EKS': 'Amazon’s managed Kubernetes service for running containerized workloads.',
  'Amazon Bedrock': 'A managed service for building GenAI apps with foundation models via one API.',
  'Amazon RDS': 'Managed relational databases (PostgreSQL, MySQL, etc.) with automated operations.',
  'Microsoft Azure': 'Microsoft’s cloud platform for compute, data, identity, and AI services.',
  'Azure Functions': 'Azure’s event-driven serverless compute service.',
  'Azure OpenAI': 'Enterprise-hosted OpenAI models with Azure security, networking, and compliance.',
  'Azure Kubernetes Service': 'Azure’s managed Kubernetes (AKS) for container orchestration.',
  'Google Cloud Platform': 'Google’s cloud for compute, data, and AI, including Vertex AI.',
  'Cloud Run': 'Google’s serverless platform for running containers that scale to zero.',
  'Vertex AI': 'Google Cloud’s managed platform for training, tuning, and serving ML and GenAI models.',
  'Serverless Architecture': 'Designing systems from managed, event-driven, auto-scaling building blocks.',
  'Infrastructure as Code': 'Provisioning and versioning cloud infrastructure through declarative code.',
  'Cloud IAM': 'Managing identities, roles, and least-privilege access to cloud resources.',
  'Cloud Cost Optimization': 'Right-sizing, autoscaling, and governance to control cloud spend.',
  'Multi-Cloud Strategy': 'Designing portable workloads and governance across more than one cloud provider.',
  'Domain-Driven Design': 'Modeling software around the business domain and its ubiquitous language.',
  'Bounded Contexts': 'Explicit boundaries within which a domain model and its terms are consistent.',
  'Aggregates and Entities': 'Consistency boundaries and identity-bearing objects in a domain model.',
  'Ubiquitous Language': 'A shared, precise vocabulary used by developers and domain experts alike.',
  'Hexagonal Architecture': 'Ports-and-adapters design that isolates the core domain from external concerns.',
  'Clean Architecture': 'Layered design with dependencies pointing inward toward stable business rules.',
  CQRS: 'Command Query Responsibility Segregation — separate models for writes and reads.',
  'Event Sourcing': 'Persisting state as an append-only log of domain events.',
  'Microservices Decomposition': 'Splitting a system into independently deployable, domain-aligned services.',
  'Monolith vs Microservices': 'Choosing between a single deployable and distributed services by trade-off.',
  'Modular Monolith': 'A single deployable with strong internal module boundaries.',
  'Saga Pattern': 'Managing distributed transactions as a sequence of compensable local steps.',
  'Strangler Fig Pattern': 'Incrementally replacing a legacy system by routing features to the new one.',
  'C4 Model': 'A hierarchy of context, container, component, and code diagrams for architecture.',
  'Architecture Decision Records': 'Lightweight documents that capture significant architectural decisions and rationale.',
  'Architecture Trade-off Analysis': 'Reasoning about competing quality attributes to justify a design choice.',
  'API Gateway Pattern': 'A single entry point that routes, secures, and aggregates calls to services.',
  'Application Security': 'Protecting applications against threats across their design, code, and runtime.',
  'OWASP Top 10': 'The most critical web application security risks and how to mitigate them.',
  'OAuth 2.0': 'A delegated authorization framework for granting scoped access via tokens.',
  'OpenID Connect': 'An identity layer over OAuth 2.0 for authenticating users.',
  JWT: 'JSON Web Tokens — signed, self-contained tokens for claims and authorization.',
  'Zero Trust Architecture': 'Never-trust-always-verify security with per-request authentication and authorization.',
  'Secrets Management': 'Securely storing, rotating, and accessing credentials and keys.',
  'Key Management (KMS)': 'Centralized creation, rotation, and control of cryptographic keys.',
  'Encryption at Rest and in Transit': 'Protecting data both when stored and while moving across networks.',
  'TLS and mTLS': 'Transport encryption and mutual certificate-based service authentication.',
  'Threat Modeling': 'Systematically identifying and mitigating potential attacks on a system.',
  'SAST and DAST': 'Static and dynamic application security testing in the delivery pipeline.',
  'Supply-Chain Security': 'Securing dependencies, builds, and artifacts against tampering (SBOM, signing).',
  'API Security': 'Authentication, authorization, rate limiting, and input validation for APIs.',
  'Identity and Access Management': 'Governing who can access what across systems and environments.',
  GDPR: 'The EU regulation governing personal-data privacy and protection.',
  'SOC 2': 'An audit framework for security, availability, and confidentiality controls.',
  'Secure Coding Practices': 'Writing code that avoids common vulnerabilities by design.',
  'Tricky Java Questions': 'Subtle Java behaviors and gotchas that test deep language understanding.',
  'Concurrency Gotchas': 'Race conditions, visibility, and synchronization pitfalls in multithreaded code.',
  'JVM Memory Puzzles': 'Heap, stack, GC, and memory-model scenarios that probe JVM internals.',
  'Collections Edge Cases': 'Corner cases in equals/hashCode, iteration, ordering, and mutability.',
  'Spring Boot Pitfalls': 'Common misconfigurations and surprising behaviors in Spring Boot apps.',
  'Debugging Scenarios': 'Diagnosing a described failure from symptoms, logs, and reasoning.',
  'Production Incident Scenarios': 'Handling realistic outages: triage, root cause, and mitigation.',
  'Performance Troubleshooting': 'Finding and fixing latency, throughput, and resource bottlenecks.',
  'System Design Use Cases': 'Designing a system for a concrete real-world requirement end to end.',
  'Real-world Architecture Cases': 'Case studies that ask for architecture decisions and trade-offs.',
  'API Design Trade-off Cases': 'Choosing between REST/gRPC/GraphQL and versioning strategies for a scenario.',
  'Distributed Systems Failure Scenarios': 'Reasoning about partitions, retries, idempotency, and consistency under failure.',
  'Security Vulnerability Scenarios': 'Spotting and remediating a vulnerability from a described situation.',
  'Data Modeling Cases': 'Designing schemas and access patterns for a given domain and workload.',
  'Refactoring Scenarios': 'Improving a described legacy design while preserving behavior.',
  'GenAI Use-Case Design': 'Designing a practical AI feature: RAG, agents, evaluation, cost, and guardrails.',
  'Behavioral Situational (STAR)': 'Situation-Task-Action-Result questions on collaboration and leadership.',
  'Estimation Questions': 'Back-of-the-envelope capacity, throughput, and sizing estimates.',
  'Observability Fundamentals': 'Understanding system behavior from metrics, logs, and traces together.',
  Metrics: 'Numeric time-series signals (rates, latencies, saturation) for monitoring health.',
  Logging: 'Structured, searchable event records for diagnosing behavior across services.',
  'Distributed Tracing': 'Following a single request end to end across services via correlated spans.',
  OpenTelemetry: 'A vendor-neutral standard and SDKs for generating metrics, logs, and traces.',
  Prometheus: 'A time-series database and monitoring system with a powerful query language (PromQL).',
  Grafana: 'A dashboarding and visualization platform for metrics, logs, and traces.',
  Loki: 'A horizontally scalable, label-based log aggregation system by Grafana.',
  Tempo: 'A high-scale distributed tracing backend by Grafana.',
  Jaeger: 'An open-source, end-to-end distributed tracing system.',
  Micrometer: 'A JVM metrics facade that instruments applications for many monitoring backends.',
  'SLI, SLO, SLA': 'Service level indicators, objectives, and agreements that define reliability targets.',
  'Error Budgets': 'The allowable amount of unreliability derived from an SLO, balancing risk and velocity.',
  'Golden Signals': 'Latency, traffic, errors, and saturation — the core signals of service health.',
  Alerting: 'Detecting and notifying on conditions that require attention, minimizing noise.',
  'Incident Response': 'Coordinated triage, mitigation, and communication during outages.',
  Postmortems: 'Blameless analysis of incidents to capture causes and preventive actions.',
  'On-Call Practices': 'Sustainable rotations, escalation, and runbooks for operating services.',
  'Chaos Engineering': 'Deliberately injecting failures to validate resilience assumptions.',
  'Capacity Planning': 'Forecasting resource needs to meet demand without over-provisioning.',
  'Data Pipelines': 'Automated flows that move and transform data between systems.',
  'ETL and ELT': 'Extract-transform-load patterns for populating warehouses and lakes.',
  'Batch Processing': 'Processing large, bounded datasets on a schedule.',
  'Stream Processing': 'Processing unbounded event streams continuously and with low latency.',
  'Kafka Streams': 'A Java library for building stateful stream-processing apps on Kafka.',
  'Apache Flink': 'A distributed engine for stateful stream and batch processing.',
  'Apache Spark': 'A unified analytics engine for large-scale batch and streaming data.',
  'Change Data Capture': 'Streaming row-level database changes to downstream systems in near real time.',
  'Data Lakes': 'Central repositories storing raw structured and unstructured data at scale.',
  'Data Warehouses': 'Optimized stores for structured analytical queries and reporting.',
  'Data Lakehouse': 'An architecture combining lake flexibility with warehouse management and performance.',
  'Apache Airflow': 'A platform to author, schedule, and monitor data workflows as code.',
  dbt: 'A tool for transforming warehouse data with version-controlled, tested SQL models.',
  'Delta Lake': 'A storage layer bringing ACID transactions and versioning to data lakes.',
  'Schema Evolution': 'Managing changes to data schemas without breaking producers or consumers.',
  'Feature Stores': 'Centralized management of curated ML features for training and serving.',
  'Data Quality': 'Ensuring accuracy, completeness, and consistency of data with checks and monitoring.',
  'Data Governance': 'Policies for data ownership, lineage, access, and compliance.',
  'Data Mesh': 'A decentralized approach treating data as a product owned by domain teams.',
  'Responsible AI': 'Building AI that is fair, transparent, accountable, and safe.',
  'AI Governance': 'Policies, roles, and controls for how AI systems are built, approved, and operated.',
  'LLM Evaluation': 'Measuring model output quality, accuracy, and safety with datasets and judges.',
  Guardrails: 'Input/output constraints that keep model behavior within safe, allowed bounds.',
  'Hallucination Mitigation': 'Techniques (grounding, citations, verification) to reduce fabricated outputs.',
  'Prompt Injection Defense': 'Protecting LLM systems from malicious instructions embedded in inputs.',
  'PII and Data Privacy': 'Detecting and protecting personal data in prompts, outputs, and logs.',
  'Bias and Fairness': 'Identifying and reducing unfair or discriminatory model behavior.',
  Explainability: 'Making model decisions interpretable to stakeholders and auditors.',
  'Model Registry': 'A versioned catalog of models with lineage, stages, and metadata.',
  'Model Monitoring': 'Tracking model quality, latency, cost, and safety in production.',
  'Drift Detection': 'Spotting shifts in input data or model performance over time.',
  LLMOps: 'Operational practices for deploying, versioning, and monitoring LLM applications.',
  'AI Red Teaming': 'Adversarially testing AI systems to uncover harmful or unsafe behavior.',
  'Content Moderation': 'Filtering unsafe or policy-violating model inputs and outputs.',
  'Human-in-the-Loop': 'Inserting human review or approval into AI-driven workflows.',
  'AI Cost and Latency Optimization': 'Managing token cost, caching, routing, and latency of LLM systems.',
  'EU AI Act': 'The EU’s risk-based regulation governing AI systems and their obligations.',
  'Model Cards': 'Standardized documentation of a model’s intended use, data, and limitations.',
  'Audit and Traceability': 'Recording AI decisions, prompts, and versions for accountability and review.',
  Arrays: 'Contiguous, index-based collections with constant-time access.',
  Strings: 'Sequences of characters and the algorithms that operate on them.',
  'Linked Lists': 'Node-based sequences (singly/doubly) with efficient insertion and deletion.',
  Stacks: 'Last-in-first-out structures used for recursion, undo, and parsing.',
  Queues: 'First-in-first-out structures, including deques and circular queues.',
  'Hash Tables': 'Key-value maps offering average constant-time lookup via hashing.',
  Sets: 'Collections of unique elements with fast membership tests.',
  Trees: 'Hierarchical node structures underpinning search, ordering, and indexing.',
  'Binary Search Trees': 'Ordered trees enabling logarithmic search, insert, and delete when balanced.',
  'Heaps and Priority Queues': 'Partially ordered trees giving fast access to the min or max element.',
  Tries: 'Prefix trees for efficient string storage, lookup, and autocomplete.',
  Graphs: 'Nodes and edges modeling networks, dependencies, and relationships.',
  'Balanced Trees (AVL, Red-Black)': 'Self-balancing BSTs that guarantee logarithmic operations.',
  'Segment Trees': 'Trees for efficient range queries and updates over an array.',
  'Fenwick Tree (BIT)': 'A binary indexed tree for fast prefix-sum queries and updates.',
  'Union-Find (Disjoint Set)': 'A structure for grouping elements and testing connectivity efficiently.',
  'LRU Cache': 'A cache that evicts the least-recently-used entry, backed by a map and linked list.',
  'Skip Lists': 'Probabilistic layered lists giving logarithmic search without tree balancing.',
  'Bloom Filters': 'Space-efficient probabilistic sets that answer membership with no false negatives.',
  'Time and Space Complexity': 'Analyzing algorithm cost with Big-O for runtime and memory.',
  'Sorting Algorithms': 'Ordering data with quicksort, mergesort, heapsort, and their trade-offs.',
  'Searching Algorithms': 'Locating elements efficiently in sorted or unsorted data.',
  'Binary Search': 'Halving the search space each step to find items in sorted data in log time.',
  'Two Pointers': 'Using two indices to scan or converge over a sequence efficiently.',
  'Sliding Window': 'Maintaining a moving range to solve subarray/substring problems in linear time.',
  Recursion: 'Solving problems by having a function call itself on smaller inputs.',
  Backtracking: 'Exploring candidates and abandoning paths that cannot lead to a solution.',
  'Divide and Conquer': 'Breaking a problem into subproblems, solving, and combining results.',
  'Greedy Algorithms': 'Making the locally optimal choice at each step to reach a global solution.',
  'Dynamic Programming': 'Solving overlapping subproblems by storing and reusing results.',
  'Graph Traversal (BFS and DFS)': 'Systematically visiting graph nodes breadth-first or depth-first.',
  'Shortest Path (Dijkstra, Bellman-Ford)': 'Finding minimum-cost paths in weighted graphs.',
  'Minimum Spanning Tree': 'Connecting all nodes at minimum total edge weight (Kruskal, Prim).',
  'Topological Sort': 'Ordering nodes of a DAG so every edge points forward.',
  'Bit Manipulation': 'Solving problems with bitwise operations and bit tricks.',
  'String Matching': 'Finding patterns in text with KMP, Rabin-Karp, and related algorithms.',
  'Hashing Techniques': 'Designing hash functions and handling collisions for fast lookups.',
  'Garbage Collection Tuning': 'Configuring GC (G1, ZGC, Shenandoah) to balance pause time and throughput.',
  'JIT Compilation': 'How the JVM compiles hot code to native, and warmup and inlining effects.',
  'Heap and Memory Profiling': 'Analyzing heap usage, allocations, and retained sizes to reduce memory pressure.',
  'Memory Leak Detection': 'Finding objects that are unintentionally retained and cause OutOfMemory errors.',
  'Escape Analysis': 'JVM optimization that can stack-allocate or eliminate objects that never escape.',
  'Java Flight Recorder (JFR)': 'Low-overhead JVM event recording for production profiling and diagnostics.',
  'async-profiler': 'A low-overhead sampling profiler for CPU, allocation, and lock analysis on the JVM.',
  VisualVM: 'A GUI tool for monitoring, profiling, and heap-dump analysis of JVM applications.',
  'JMH Microbenchmarking': 'The Java Microbenchmark Harness for accurate, warmup-aware micro-benchmarks.',
  'Flame Graphs': 'A visualization of sampled stack traces to spot where CPU time is spent.',
  JMeter: 'A tool for load and functional testing of applications and APIs.',
  Gatling: 'A code-driven, high-throughput load-testing tool with expressive scenarios.',
  k6: 'A developer-centric load-testing tool with JavaScript-scripted scenarios.',
  'Load and Stress Testing': 'Validating behavior and limits under expected and extreme traffic.',
  'Soak Testing': 'Running sustained load over long periods to reveal leaks and degradation.',
  'Connection Pooling': 'Reusing database/HTTP connections to avoid per-request setup overhead.',
  'Caching Strategies': 'Choosing cache placement, eviction, and invalidation to cut latency and load.',
  'N+1 Query Problem': 'Detecting and eliminating repeated per-row queries in ORM data access.',
  'Database Query Optimization': 'Improving queries with indexing, execution-plan analysis, and rewrites.',
  'Lazy vs Eager Loading': 'Trading off deferred and upfront data fetching for latency and memory.',
  Batching: 'Grouping operations or requests to amortize overhead and boost throughput.',
  'Virtual Threads (Project Loom)': 'Lightweight JVM threads that scale blocking-style concurrency cheaply.',
  'Reactive and Non-Blocking (WebFlux)': 'Non-blocking, backpressure-aware processing for high concurrency.',
  'Thread-Pool Tuning': 'Sizing and configuring pools to maximize utilization without contention.',
  Backpressure: 'Controlling flow so fast producers do not overwhelm slower consumers.',
  'Latency vs Throughput': 'Balancing per-request speed against total processed volume.',
  'Tail Latency (p99)': 'Measuring and reducing worst-case latencies, not just averages.',
  'Performance Budgets': 'Setting explicit limits on latency, size, or resource use to enforce speed.',
  'Application Performance Monitoring (APM)': 'End-to-end tracking of app latency, errors, and bottlenecks in production.',
  'Compression and Payload Size': 'Reducing transfer size (gzip, protobuf) to improve response times.',
  'Amdahl’s Law': 'The limit that a program’s serial fraction places on parallel speedup.',
  'Testing Fundamentals': 'Principles of effective automated testing and what makes tests valuable.',
  'Test Pyramid': 'Balancing many fast unit tests against fewer slow integration and E2E tests.',
  'Unit Testing': 'Testing individual units in isolation for correctness.',
  'Integration Testing': 'Verifying that components work together, including databases and services.',
  'End-to-End Testing': 'Validating complete user flows through the running system.',
  'Test-Driven Development (TDD)': 'Writing a failing test first, then code, then refactoring.',
  'Behavior-Driven Development (BDD)': 'Specifying behavior in business-readable Given-When-Then scenarios.',
  'JUnit 5': 'The modern Java testing framework with extensions, parameterized, and nested tests.',
  Testcontainers: 'Spinning up real dependencies (DBs, brokers) in containers for reliable tests.',
  'Contract Testing (Pact)': 'Verifying provider and consumer agree on an API contract independently.',
  'Mutation Testing': 'Measuring test quality by introducing code mutations and checking tests catch them.',
  'Property-Based Testing': 'Generating many inputs to assert properties hold across a wide space.',
  'Code Coverage': 'Measuring which code is exercised by tests, with its uses and limits.',
  'Mocking and Stubbing': 'Replacing real collaborators with controlled test substitutes.',
  'Test Doubles': 'Mocks, stubs, fakes, spies, and dummies used to isolate the unit under test.',
  'Test Automation Strategy': 'Deciding what, when, and how to automate across the pipeline.',
  'Regression Testing': 'Re-running tests to ensure changes don’t break existing behavior.',
  'Flaky Test Management': 'Detecting, quarantining, and fixing non-deterministic tests.',
  'API Design Fundamentals': 'Principles of clear, consistent, evolvable interfaces between systems.',
  REST: 'Resource-oriented HTTP APIs using verbs, status codes, and representations.',
  'RESTful Maturity (Richardson)': 'The maturity levels from plain HTTP to hypermedia-driven REST.',
  GraphQL: 'A query language letting clients request exactly the data they need from one endpoint.',
  'gRPC': 'A high-performance, contract-first RPC framework over HTTP/2 using Protocol Buffers.',
  'API Versioning': 'Evolving APIs without breaking clients via URI, header, or media-type strategies.',
  'OpenAPI and Swagger': 'Describing REST APIs in a standard spec for docs, clients, and validation.',
  'Idempotent APIs': 'Designing operations that produce the same result when safely retried.',
  'Pagination and Filtering': 'Returning large result sets efficiently with limits, cursors, and queries.',
  HATEOAS: 'Hypermedia links that let clients navigate an API dynamically.',
  Webhooks: 'Server-to-server callbacks that push events to subscribers.',
  Activiti: 'A lightweight Java BPMN workflow and business-process management engine.',
  Flowable: 'An embeddable Java platform supporting BPMN, CMMN, DMN, forms, and event-driven processes.',
  jBPM: 'A Java business-process engine supporting executable BPMN 2.0 processes and human tasks.',
  Kogito: 'A cloud-native process automation platform derived from jBPM and Drools.',
  Bonita: 'A BPMN-oriented platform for building process applications with forms, tasks, and connectors.',
  ProcessMaker: 'A low-code BPMN platform for designing and automating human-centric business processes.',
  Operaton: 'An open-source BPMN process engine continuing the Camunda 7 technology lineage.',
  'CIB seven': 'A Camunda 7-compatible open-source platform for BPMN and DMN process automation.',
  'Imixs-Workflow': 'An open-source Jakarta EE workflow engine using BPMN models for business applications.',
  'Oracle BPM Suite': 'An enterprise platform for BPMN process modeling, execution, human tasks, and monitoring.',
  'IBM Business Automation Workflow': 'An enterprise platform combining BPM workflows and case management.',
  Bizagi: 'A BPMN-based process modeling and enterprise automation platform.',
  'SAP Signavio': 'A process-transformation suite for BPMN modeling, governance, and workflow automation.',
  Temporal: 'A durable-execution platform for reliable, long-running application workflows and activities.',
  Camunda: 'A process-orchestration platform for BPMN workflows, decisions, tasks, and human approvals.',
  'Netflix Conductor': 'A distributed orchestration engine for defining and operating service workflows.',
  'AWS Step Functions': 'A managed AWS service for orchestrating applications with state-machine workflows.',
  'Azure Durable Functions': 'An Azure Functions extension for stateful, reliable serverless orchestration.',
  'Argo Workflows': 'A Kubernetes-native engine for orchestrating container-based parallel jobs and pipelines.',
  Prefect: 'A Python workflow orchestration platform emphasizing observable and resilient data flows.',
  Dagster: 'A data orchestrator built around software-defined assets, lineage, testing, and observability.',
  n8n: 'A low-code automation platform for connecting APIs, applications, events, and human workflows.',
  'API Error Handling': 'Consistent, informative error responses and status-code semantics.',
  'Authentication for APIs': 'Securing APIs with tokens, keys, OAuth, and mTLS.',
  'API Rate Limiting': 'Throttling clients to protect capacity and ensure fair use.',
  'API Contracts': 'Explicit, versioned agreements defining request/response shapes.',
  'Enterprise Integration Patterns': 'Proven messaging patterns (routing, transformation, aggregation) for integration.',
  'Message-Based Integration': 'Connecting systems asynchronously through queues and topics.',
  'Backend for Frontend (BFF)': 'A tailored API layer per client type that aggregates downstream services.',
  'Operating System Fundamentals': 'Core OS concepts: processes, memory, I/O, and the kernel’s role.',
  'Processes and Threads': 'Units of execution and how they share (or isolate) resources.',
  'Process Scheduling': 'How the OS decides which process/thread runs next.',
  'Memory Management': 'Allocating, protecting, and reclaiming memory across processes.',
  'Virtual Memory': 'Giving each process a large private address space backed by paging.',
  'Paging and Segmentation': 'Mapping virtual addresses to physical memory in fixed or variable blocks.',
  'File Systems': 'Organizing, storing, and accessing files, inodes, and directories.',
  'Inter-Process Communication': 'Pipes, sockets, shared memory, and message passing between processes.',
  'System Calls': 'The interface programs use to request services from the kernel.',
  'Concurrency and Deadlocks': 'Coordinating concurrent execution and avoiding deadlock and starvation.',
  'Linux Command Line': 'Navigating and operating a system with core Linux commands.',
  'Shell Scripting': 'Automating tasks with bash/sh scripts and pipelines.',
  Signals: 'Asynchronous notifications delivered to processes by the OS.',
  'Namespaces and cgroups': 'Linux primitives that isolate and limit resources behind containers.',
  'CPU and I/O Scheduling': 'Prioritizing compute and disk/network operations for throughput and fairness.',
  'Kernel vs User Space': 'The privilege boundary separating the kernel from application code.',
  'Networking Fundamentals': 'How data moves across networks and the layers involved.',
  'OSI Model': 'The seven-layer reference model for network communication.',
  'TCP/IP': 'The connection-oriented, reliable transport and addressing suite of the internet.',
  UDP: 'A lightweight, connectionless transport for low-latency, loss-tolerant traffic.',
  'HTTP/1.1': 'The classic request/response web protocol with keep-alive connections.',
  'HTTP/2': 'A binary, multiplexed HTTP version reducing latency over a single connection.',
  'HTTP/3 and QUIC': 'HTTP over QUIC/UDP for faster, connection-migrating, head-of-line-free transport.',
  'HTTPS and TLS Handshake': 'Establishing encrypted, authenticated connections and negotiating keys.',
  DNS: 'The distributed system that resolves human-readable names to IP addresses.',
  WebSockets: 'A full-duplex, persistent connection for real-time bidirectional messaging.',
  'Load Balancing Algorithms': 'Distributing traffic across servers (round-robin, least-connections, hashing).',
  'Reverse Proxies': 'Front-facing servers that route, terminate TLS, cache, and protect backends.',
  'Content Delivery Networks': 'Edge networks that cache content close to users to cut latency.',
  'Firewalls and NAT': 'Filtering traffic and translating addresses at network boundaries.',
  Sockets: 'The endpoint abstraction for sending and receiving data over a network.',
  'Network Latency and Bandwidth': 'The delay and capacity characteristics that shape performance.',
  'Machine Learning Fundamentals': 'Core concepts of learning patterns from data to make predictions.',
  'Supervised Learning': 'Training models on labeled examples to predict outputs.',
  'Unsupervised Learning': 'Finding structure in unlabeled data (clusters, embeddings).',
  Regression: 'Predicting continuous values from input features.',
  Classification: 'Assigning inputs to discrete categories.',
  Clustering: 'Grouping similar data points without labels.',
  'Decision Trees and Random Forests': 'Tree-based models and ensembles for classification and regression.',
  'Gradient Boosting (XGBoost)': 'Powerful boosted-tree ensembles widely used for tabular data.',
  'Feature Engineering': 'Creating and selecting informative inputs to improve model performance.',
  'Data Preprocessing': 'Cleaning, encoding, scaling, and splitting data for modeling.',
  'Model Evaluation Metrics': 'Accuracy, precision/recall, F1, ROC-AUC, RMSE, and when to use each.',
  'Overfitting and Regularization': 'Preventing models from memorizing noise via regularization and validation.',
  'Cross-Validation': 'Estimating generalization by training/testing across data folds.',
  'Neural Networks Basics': 'Layers, activations, and backpropagation fundamentals.',
  'Model Training and Tuning': 'Fitting models and optimizing hyperparameters.',
  MLOps: 'Operationalizing ML with pipelines, CI/CD, versioning, and monitoring.',
  'Model Deployment': 'Serving models reliably via batch, real-time, or edge inference.',
  'Coding Standards and Style Guides': 'Team-wide conventions (e.g. Google/Sun Java Style) that keep code consistent.',
  'Naming Conventions': 'Consistent, intention-revealing names for classes, methods, and variables.',
  'Code Formatting (Spotless)': 'Automated, enforced formatting so style is never debated in review.',
  EditorConfig: 'A shared file that keeps indentation and whitespace consistent across editors.',
  Checkstyle: 'A static tool that enforces Java coding conventions and style rules.',
  PMD: 'A source analyzer that flags likely bugs, dead code, and bad practices.',
  SpotBugs: 'A bytecode analyzer that detects common bug patterns in Java.',
  SonarLint: 'An IDE tool that surfaces code smells, bugs, and vulnerabilities as you type.',
  'ESLint and Prettier': 'Linting and formatting for JavaScript/TypeScript codebases.',
  'Static Code Analysis': 'Analyzing code without running it to catch defects, smells, and risks.',
  'Code Review Practices': 'Effective, respectful review that improves quality and shares knowledge.',
  'Pull Request Etiquette': 'Small, focused PRs with clear descriptions and constructive feedback.',
  'Javadoc and Documentation': 'Documenting APIs and intent so code is understandable and maintainable.',
  'Readability and Maintainability': 'Writing code that is easy to read, reason about, and change safely.',
  'Cyclomatic Complexity': 'A metric of branching complexity used to flag hard-to-test methods.',
  'Technical Debt Management': 'Tracking, prioritizing, and paying down accumulated shortcuts.',
  'Linting Gates in CI': 'Failing the build when style or quality checks are not met.',
  'Null-Safety and Optional': 'Avoiding null-pointer errors with Optional and null-safety conventions.',
  'Immutability Conventions': 'Favoring immutable objects and final fields for safer, simpler code.',
  'Error-Handling and Logging Standards': 'Consistent exception handling and structured, useful logging.',
}

const initialForm = {
  title: '', description: '', ecosystem: 'JAVA' as Ecosystem, technologies: ['Java'] as string[],
  topics: [] as string[],
  difficulty: 'MEDIUM',
  questionMode: 'MANUAL', knowledgeCollectionId: '',
  durationMinutes: 60, questionCount: 0, passingPercentage: 70,
  mcqSingle: 0, mcqMultiple: 0, shortText: 0, longText: 0,
}

type QuestionDraft = {
  id?: string
  order: number
  prompt: string
  maxScore: number
  type: QuestionType
  optionsText: string
  correctText: string
}

const emptyQuestion = (order = 1): QuestionDraft => ({
  order, prompt: '', maxScore: 10, type: 'LONG_TEXT', optionsText: '', correctText: '',
})

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : 'Unexpected request failure'
}

export function InterviewCard({ interview, candidates, notify, reload, showQuestions = true, showAssignment = true, collapseQuestions = false }: {
  interview: Interview
  candidates: Profile[]
  notify: (message: string, error?: boolean) => void
  reload: () => Promise<void>
  showQuestions?: boolean
  showAssignment?: boolean
  collapseQuestions?: boolean
}) {
  const [questionsOpen, setQuestionsOpen] = useState(!collapseQuestions)
  const [questions, setQuestions] = useState<AdminQuestion[]>([])
  const [draft, setDraft] = useState<QuestionDraft>(emptyQuestion())
  const [candidateId, setCandidateId] = useState('')
  const [startsAt, setStartsAt] = useState(() => toLocalInput(new Date()))
  const [endsAt, setEndsAt] = useState(() => toLocalInput(new Date(Date.now() + interview.durationMinutes * 60_000)))
  const [assignments, setAssignments] = useState<Assignment[]>([])
  const [generating, setGenerating] = useState(false)
  const [composeReport, setComposeReport] = useState<{ rounds: number; questionCount: number }>()
  const questionFormRef = useRef<HTMLFormElement>(null)
  const isMcq = draft.type === 'MCQ_SINGLE' || draft.type === 'MCQ_MULTIPLE'
  const actualComposition = questions.reduce((counts, question) => ({
    ...counts,
    [question.type]: counts[question.type] + 1,
  }), {MCQ_SINGLE: 0, MCQ_MULTIPLE: 0, SHORT_TEXT: 0, LONG_TEXT: 0})
  const expectedComposition = interview.questionComposition
  const compositionMatches = questions.length === interview.questionCount
    && actualComposition.MCQ_SINGLE === expectedComposition.mcqSingle
    && actualComposition.MCQ_MULTIPLE === expectedComposition.mcqMultiple
    && actualComposition.SHORT_TEXT === expectedComposition.shortText
    && actualComposition.LONG_TEXT === expectedComposition.longText

  async function loadQuestions() {
    try {
      const loaded = await interviewApi.listQuestions(interview.id)
      setQuestions(loaded)
      if (!draft.id) setDraft(emptyQuestion(loaded.length + 1))
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  useEffect(() => { void loadQuestions() }, [interview.id])

  async function loadAssignments() {
    if (!showAssignment || interview.status !== 'PUBLISHED') return
    try {
      setAssignments(await interviewApi.listAssignments(interview.id))
    } catch (error) {
      notify(messageOf(error), true)
    }
  }
  useEffect(() => { void loadAssignments() }, [interview.id, interview.status])

  async function archive() {
    if (!window.confirm(`Delete interview "${interview.title}"? It will be removed from your lists.`)) return
    try {
      await interviewApi.archiveInterview(interview.id)
      notify('Interview deleted.')
      await reload()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  async function removeAssignment(assignmentId: string) {
    try {
      await interviewApi.unassign(interview.id, assignmentId)
      notify('Candidate unassigned.')
      await loadAssignments()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }
  useEffect(() => {
    if (draft.id) questionFormRef.current?.scrollIntoView({behavior: 'smooth', block: 'start'})
  }, [draft.id])

  async function saveQuestion(event: FormEvent) {
    event.preventDefault()
    const body = {
      order: draft.order,
      prompt: draft.prompt,
      maxScore: draft.maxScore,
      type: draft.type,
      options: isMcq
        ? draft.optionsText.split('\n').map((value) => value.trim()).filter(Boolean)
        : [],
      correctAnswers: isMcq
        ? draft.correctText.split(',').map((value) => value.trim()).filter(Boolean)
        : [],
    }
    try {
      if (draft.id) await interviewApi.updateQuestion(interview.id, draft.id, body)
      else await interviewApi.addQuestion(interview.id, body)
      notify(draft.id ? 'Question updated.' : 'Question added.')
      setDraft(emptyQuestion(questions.length + (draft.id ? 0 : 2)))
      await loadQuestions()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  function edit(question: AdminQuestion) {
    setDraft({
      id: question.id,
      order: question.order,
      prompt: question.prompt,
      maxScore: question.maxScore,
      type: question.type,
      optionsText: question.options.join('\n'),
      correctText: question.correctAnswers.join(', '),
    })
  }

  async function remove(questionId: string) {
    if (!window.confirm('Delete this draft question?')) return
    try {
      await interviewApi.deleteQuestion(interview.id, questionId)
      notify('Question deleted.')
      await loadQuestions()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  async function generate() {
    setGenerating(true)
    notify('Generating questions through the AI Gateway…')
    try {
      await interviewApi.generateQuestions(interview.id)
      notify('AI questions generated and saved in PostgreSQL.')
      await loadQuestions()
    } catch (error) {
      notify(messageOf(error), true)
    } finally {
      setGenerating(false)
    }
  }
  async function compose() {
    setGenerating(true)
    setComposeReport(undefined)
    notify('Composition agent is running in the background — planning, generating and critiquing…')
    try {
      const started = await interviewApi.composeQuestions(interview.id)
      const job = await pollComposeJob(started.jobId)
      if (job.status === 'SUCCEEDED') {
        setComposeReport({ rounds: job.rounds, questionCount: job.questionCount })
        notify(`Agent composed ${job.questionCount} question(s) over ${job.rounds} round(s).`)
        await loadQuestions()
      } else {
        notify(job.error || 'Composition failed.', true)
      }
    } catch (error) {
      notify(messageOf(error), true)
    } finally {
      setGenerating(false)
    }
  }

  async function pollComposeJob(jobId: string): Promise<ComposeJob> {
    for (let attempt = 0; attempt < 150; attempt++) { // up to ~5 min at 2s intervals
      const job = await interviewApi.composeJob(jobId)
      if (job.status === 'SUCCEEDED' || job.status === 'FAILED') return job
      await new Promise((resolve) => setTimeout(resolve, 2000))
    }
    return { jobId, status: 'FAILED', questionCount: 0, rounds: 0,
      error: 'Timed out waiting for the composition agent.' }
  }

  async function publish() {
    try {
      await interviewApi.publish(interview.id)
      notify('Interview published.')
      await reload()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  async function assign(event: FormEvent) {
    event.preventDefault()
    try {
      await interviewApi.assign(interview.id, {
        candidateId,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
        maxAttempts: 1,
      })
      notify('Candidate assignment scheduled.')
      setCandidateId('')
      await loadAssignments()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  return (
    <article className="card interview-card">
      <div className="card-heading">
        <div><h2>{interview.title}</h2><p>{interview.skills.join(', ')} · {interview.difficulty} · {interview.durationMinutes} min</p></div>
        <div className="card-heading-actions">
          <span className="badge">{interview.status}</span>
          <button type="button" className="danger-button" onClick={() => void archive()}>Delete</button>
        </div>
      </div>
      <p><strong>Questions:</strong> {questions.length} / {interview.questionCount}</p>
      <p><strong>Question mix:</strong> {expectedComposition.mcqSingle} single-answer MCQ,{' '}
        {expectedComposition.mcqMultiple} multiple-answer MCQ, {expectedComposition.shortText} short,{' '}
        {expectedComposition.longText} long</p>
      <p><strong>Passing score:</strong> {interview.passingPercentage}%</p>

      {showQuestions && questions.length > 0 && collapseQuestions && <button type="button"
        className="secondary-button" aria-expanded={questionsOpen}
        onClick={() => setQuestionsOpen((open) => !open)}>
        {questionsOpen ? 'Hide questions' : `Show ${questions.length} questions`}
      </button>}

      {showQuestions && questions.length > 0 && questionsOpen && <div className="question-list">
        {questions.map((question) => (
          <div className="question-preview" key={question.id}>
            <div><strong>{question.order}. {question.type.replaceAll('_', ' ')}</strong><p>{question.prompt}</p></div>
            {question.options.length > 0 && <ol type="A">{question.options.map((option) => <li key={option}>{option}</li>)}</ol>}
            {question.citations?.length > 0 && <div className="citation-list">
              <strong>Sources</strong>
              {question.citations.map((citation) => <p key={citation.chunkId}>
                {citation.fileName} · section {citation.chunkIndex + 1}: {citation.excerpt}
              </p>)}
            </div>}
            {interview.status === 'DRAFT' && <div className="compact-actions">
              <button type="button" className="secondary-button" onClick={() => edit(question)}>Edit</button>
              <button type="button" className="danger-button" onClick={() => void remove(question.id)}>Delete</button>
            </div>}
          </div>
        ))}
      </div>}

      {interview.status === 'DRAFT' && <>
        {(interview.questionMode === 'DIRECT_LLM' || interview.questionMode === 'RAG') && !draft.id &&
          <>
            <div className="compact-actions">
              <button type="button" disabled={generating} onClick={() => void generate()}
                title="One AI call. Produces your full question mix (MCQ + text) fast, without self-review.">
                {generating ? 'Generating questions…' : 'Generate AI questions'}
              </button>
              <button type="button" className="secondary-button" disabled={generating} onClick={() => void compose()}
                title="An agent loop: generate → self-critique (LLM judge) → de-duplicate, repeating rounds until the question mix is met. Slower, higher quality.">
                🤖 Compose (agentic)
              </button>
            </div>
            <p className="field-hint"><strong>Generate</strong> is a single AI call — fast, no self-review.{' '}
              <strong>Compose (agentic)</strong> runs a loop that generates, has a second AI critique
              each question, and drops duplicates, repeating rounds until your question mix is met.</p>
            {generating && <div className="generation-progress" role="progressbar"
              aria-label="Generating AI questions" aria-valuetext="Generation in progress">
              <div className="generation-progress-bar" />
              <span>AI Gateway is creating and validating the question set…</span>
            </div>}
            {composeReport && !generating && <div className="agent-trace">
              <strong>🤖 Agent composed {composeReport.questionCount} question(s) over {composeReport.rounds} round(s)</strong>
            </div>}
          </>}
        {(interview.questionMode === 'MANUAL' || draft.id) &&
          <form ref={questionFormRef} className="question-builder" onSubmit={(event) => void saveQuestion(event)}>
          <h3>{draft.id ? 'Edit question' : 'Add question'}</h3>
          <div className="inline-fields">
            <label>Order<input type="number" min="1" max="100" value={draft.order} onChange={(e) => setDraft({...draft, order: Number(e.target.value)})} /></label>
            <label>Type<select value={draft.type} onChange={(e) => setDraft({...draft, type: e.target.value as QuestionType})}>
              <option value="LONG_TEXT">Long text</option>
              <option value="SHORT_TEXT">Short text</option>
              <option value="MCQ_SINGLE">MCQ — single answer</option>
              <option value="MCQ_MULTIPLE">MCQ — multiple answers</option>
            </select></label>
            <label>Points<input type="number" min="1" max="100" value={draft.maxScore} onChange={(e) => setDraft({...draft, maxScore: Number(e.target.value)})} /></label>
          </div>
          <label>Prompt<textarea required value={draft.prompt} onChange={(e) => setDraft({...draft, prompt: e.target.value})} /></label>
          {isMcq && <>
            <label>Options (one per line)<textarea required value={draft.optionsText} onChange={(e) => setDraft({...draft, optionsText: e.target.value})} /></label>
            <label>Correct answer{draft.type === 'MCQ_MULTIPLE' ? 's (comma separated)' : ''}
              <input required value={draft.correctText} onChange={(e) => setDraft({...draft, correctText: e.target.value})} />
            </label>
          </>}
          <div className="compact-actions">
            <button type="submit">{draft.id ? 'Save changes' : 'Add question'}</button>
            {draft.id && <button type="button" className="secondary-button" onClick={() => setDraft(emptyQuestion(questions.length + 1))}>Cancel</button>}
          </div>
        </form>}
        <button type="button" disabled={!compositionMatches} onClick={() => void publish()}>
          Publish {!compositionMatches && '(question mix incomplete)'}
        </button>
      </>}

      {showAssignment && interview.status === 'PUBLISHED' && <form className="assignment-form" onSubmit={(event) => void assign(event)}>
        <label>Candidate<select required value={candidateId} onChange={(e) => setCandidateId(e.target.value)}>
          <option value="">Select a candidate</option>
          {candidates.map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.displayName} — {candidate.email}</option>)}
        </select></label>
        <p className="assignment-window-note">Prefilled to start now for {interview.durationMinutes} minutes — edit if you want a different window.</p>
        <label>Starts<input required type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} /></label>
        <label>Ends<input required type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} /></label>
        <button type="submit">Assign candidate</button>
      </form>}
      {showAssignment && interview.status === 'PUBLISHED' && assignments.length > 0 && <div className="assignment-list">
        <strong>Assigned candidates</strong>
        {assignments.map((assignment) => {
          const person = candidates.find((candidate) => candidate.id === assignment.candidateId)
          return <div className="assignment-row" key={assignment.id}>
            <span>{person ? `${person.displayName} — ${person.email}` : assignment.candidateId} · {assignment.status}</span>
            <button type="button" className="secondary-button" onClick={() => void removeAssignment(assignment.id)}>Unassign</button>
          </div>
        })}
      </div>}
    </article>
  )
}

export function InterviewerDashboard() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [activeView, setActiveView] = useState<'create' | 'drafts' | 'assign' | 'history' | 'knowledge'>('create')
  const [interviews, setInterviews] = useState<Interview[]>([])
  const [candidates, setCandidates] = useState<Profile[]>([])
  const [collections, setCollections] = useState<KnowledgeCollection[]>([])
  const [form, setForm] = useState(initialForm)
  const [techQuery, setTechQuery] = useState('')
  const [suggestedTopics, setSuggestedTopics] = useState<string[]>([])
  const [topicsBusy, setTopicsBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [hasError, setHasError] = useState(false)
  const [messageId, setMessageId] = useState(0)

  const notify = (text: string, error = false) => {
    setMessage(text); setHasError(error); setMessageId((id) => id + 1)
  }
  useEffect(() => {
    if (!message) return
    const timer = window.setTimeout(() => setMessage(''), 6000)
    return () => window.clearTimeout(timer)
  }, [message, messageId])
  const load = async () => {
    const [owned, availableCandidates, availableCollections] = await Promise.all([
      interviewApi.listOwned(), interviewApi.candidates(), interviewApi.listKnowledgeCollections(),
    ])
    setInterviews(owned)
    setCandidates(availableCandidates)
    setCollections(availableCollections)
  }

  useEffect(() => { void load().catch((error) => notify(messageOf(error), true)) }, [])

  async function create(event: FormEvent) {
    event.preventDefault()
    try {
      await interviewApi.create({
        title: form.title,
        description: form.description,
        skills: [...form.technologies, ...form.topics],
        difficulty: form.difficulty,
        questionMode: form.questionMode,
        knowledgeCollectionId: form.questionMode === 'RAG' ? form.knowledgeCollectionId : null,
        durationMinutes: form.durationMinutes,
        questionCount: form.questionCount,
        passingPercentage: form.passingPercentage,
        questionComposition: {
          mcqSingle: form.mcqSingle,
          mcqMultiple: form.mcqMultiple,
          shortText: form.shortText,
          longText: form.longText,
        },
      })
      setForm(initialForm)
      setSuggestedTopics([])
      notify('Draft interview created.')
      await load()
      setActiveView('drafts')
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  function setComposition(
    field: 'mcqSingle' | 'mcqMultiple' | 'shortText' | 'longText',
    value: number,
  ) {
    const next = {...form, [field]: Math.max(0, value)}
    next.questionCount = next.mcqSingle + next.mcqMultiple + next.shortText + next.longText
    setForm(next)
  }

  function setEcosystem(ecosystem: Ecosystem) {
    // Ecosystem is a browse filter, not a hard reset: switching it keeps the technologies
    // already picked (from any ecosystem) so an interview can mix across ecosystems.
    setTechQuery('')
    setForm({...form, ecosystem})
  }

  function toggleTechnology(technology: string) {
    const selected = form.technologies.includes(technology)
      ? form.technologies.filter((item) => item !== technology)
      : [...form.technologies, technology]
    setForm({...form, technologies: selected})
  }

  function toggleTopic(topic: string) {
    const selected = form.topics.includes(topic)
      ? form.topics.filter((item) => item !== topic)
      : [...form.topics, topic]
    setForm({...form, topics: selected})
  }

  async function suggestTopics() {
    if (form.technologies.length === 0) return
    setTopicsBusy(true)
    try {
      const { topics } = await interviewApi.suggestTopics(form.technologies, form.difficulty)
      setSuggestedTopics(topics)
      // keep only still-valid selections
      setForm((current) => ({ ...current, topics: current.topics.filter((t) => topics.includes(t)) }))
      if (topics.length === 0) notify('No topics were suggested for the selected technologies.', true)
    } catch (error) {
      notify(messageOf(error), true)
    } finally {
      setTopicsBusy(false)
    }
  }

  return (
    <main className="dashboard">
      <div className="dashboard-header">
        <div><p className="eyebrow">Interviewer workspace</p><h1>Interview management</h1></div>
        <button className="secondary-button" onClick={auth.logout}>Sign out</button>
      </div>
      {message && <div className={`toast ${hasError ? 'toast-error' : 'toast-success'}`}
        role={hasError ? 'alert' : 'status'} aria-live={hasError ? 'assertive' : 'polite'}>
        <span>{message}</span>
        <button type="button" className="toast-close" aria-label="Dismiss" onClick={() => setMessage('')}>×</button>
      </div>}
      <div className="workspace-layout">
        <nav className="workspace-nav" aria-label="Interviewer workspace">
          <button className={activeView === 'create' ? 'active' : ''} onClick={() => setActiveView('create')}>
            <span>1</span>Create interview draft
          </button>
          <button className={activeView === 'knowledge' ? 'active' : ''} onClick={() => setActiveView('knowledge')}>
            <span>📚</span>Knowledge base (RAG)
          </button>
          <button className={activeView === 'drafts' ? 'active' : ''} onClick={() => setActiveView('drafts')}>
            <span>2</span>Edit and publish
          </button>
          <button className={activeView === 'assign' ? 'active' : ''} onClick={() => setActiveView('assign')}>
            <span>3</span>Assign candidate
          </button>
          <button className={activeView === 'history' ? 'active' : ''} onClick={() => setActiveView('history')}>
            <span>4</span>Interview history
          </button>
          <button onClick={() => navigate('/interviewer/submissions')}>
            <span>5</span>Review submissions
          </button>
          <button onClick={() => navigate('/interviewer/education')}>
            <span>6</span>Educate Yourself
          </button>
        </nav>

        <section className="workspace-content">
          {activeView === 'create' && <form className="form-grid" onSubmit={(event) => void create(event)}>
            <h2>Create interview draft</h2>
            <label>Title<input required value={form.title} onChange={(e) => setForm({...form, title: e.target.value})} /></label>
            <label>Description<textarea required value={form.description} onChange={(e) => setForm({...form, description: e.target.value})} /></label>
            <div className="inline-fields">
              <label>Ecosystem<select value={form.ecosystem}
                onChange={(e) => setEcosystem(e.target.value as Ecosystem)}>
                {sortedEcosystems.map((value) =>
                  <option key={value} value={value}>{ecosystemLabels[value]}</option>)}
              </select></label>
              <div className="technology-field">
                <span>Technologies</span>
                <details className="technology-dropdown">
                  <summary>{form.technologies.length > 0
                    ? `${form.technologies.length} selected`
                    : 'Select technologies'}</summary>
                  <div className="technology-options">
                    <input className="technology-search" type="text" value={techQuery}
                      placeholder="Search technologies…" aria-label="Search technologies"
                      onChange={(e) => setTechQuery(e.target.value)} />
                    {ecosystemTechnologies[form.ecosystem]
                      .filter((technology) => technology.toLowerCase().includes(techQuery.trim().toLowerCase()))
                      .map((technology) =>
                        <label key={technology}>
                          <input type="checkbox" checked={form.technologies.includes(technology)}
                            onChange={() => toggleTechnology(technology)} />
                          {technology}
                        </label>)}
                    {ecosystemTechnologies[form.ecosystem]
                      .filter((technology) => technology.toLowerCase().includes(techQuery.trim().toLowerCase()))
                      .length === 0 && <p className="technology-empty">No technologies match “{techQuery}”.</p>}
                  </div>
                </details>
                <small>Pick from any ecosystem — switch the Ecosystem filter to add more; selections are kept.</small>
                {form.technologies.length > 0 && <div className="selected-technologies" aria-label="Selected technologies">
                  {form.technologies.map((technology) =>
                    <span key={technology} className="tech-chip">
                      {technology}
                      <button type="button" aria-label={`Remove ${technology}`}
                        onClick={() => toggleTechnology(technology)}>×</button>
                    </span>)}
                </div>}
                <div className="technology-description" role="status" aria-live="polite">
                  {form.technologies.length === 0
                    ? <span>Select a technology to see its description.</span>
                    : form.technologies.map((technology) =>
                      <div className="technology-description-item" key={technology}>
                        <strong>{technology}</strong>
                        <span>{technologyDescriptions[technology]}</span>
                        <span>AI-generated questions will include this technology.</span>
                      </div>)}
                </div>
              </div>
            </div>
            <div className="technology-field">
              <span>Topics <small>(optional — narrow the generated questions)</small></span>
              <div className="compact-actions">
                <button type="button" className="secondary-button"
                  disabled={topicsBusy || form.technologies.length === 0}
                  onClick={() => void suggestTopics()}>
                  {topicsBusy ? 'Suggesting…' : '🤖 Suggest topics'}</button>
                {form.topics.length > 0 && <span className="badge">{form.topics.length} selected</span>}
              </div>
              {suggestedTopics.length > 0 && <div className="topic-options">
                {suggestedTopics.map((topic) =>
                  <label key={topic}>
                    <input type="checkbox" checked={form.topics.includes(topic)}
                      onChange={() => toggleTopic(topic)} />
                    {topic}
                  </label>)}
              </div>}
              <small>{suggestedTopics.length === 0
                ? 'Pick technologies above, then suggest topics to focus on specific areas.'
                : 'Select topics to focus generation; leave all unchecked to cover the technologies broadly.'}</small>
            </div>
            <fieldset className="question-composition">
              <legend>Types of questions</legend>
              <p>Choose how many questions of each type the interview should contain.</p>
              <div className="inline-fields">
                <label>MCQ (one answer)<input type="number" min="0" max="100" value={form.mcqSingle}
                  onChange={(e) => setComposition('mcqSingle', Number(e.target.value))} /></label>
                <label>MCQ (multiple answers)<input type="number" min="0" max="100" value={form.mcqMultiple}
                  onChange={(e) => setComposition('mcqMultiple', Number(e.target.value))} /></label>
                <label>Short answer (one line)<input type="number" min="0" max="100" value={form.shortText}
                  onChange={(e) => setComposition('shortText', Number(e.target.value))} /></label>
                <label>Long answer<input type="number" min="0" max="100" value={form.longText}
                  onChange={(e) => setComposition('longText', Number(e.target.value))} /></label>
              </div>
              <strong>Total questions: {form.questionCount}</strong>
              {form.questionCount === 0 && <span className="field-error"> Select at least one question.</span>}
              {form.questionCount > 100 && <span className="field-error"> Maximum 100 questions.</span>}
            </fieldset>
            <label>Difficulty<select value={form.difficulty} onChange={(e) => setForm({...form, difficulty: e.target.value})}><option>EASY</option><option>MEDIUM</option><option>HARD</option><option>MIXED</option></select></label>
            <label>Question mode<select value={form.questionMode}
              onChange={(e) => setForm({...form, questionMode: e.target.value, knowledgeCollectionId: ''})}>
              <option>MANUAL</option><option>DIRECT_LLM</option><option>RAG</option>
            </select></label>
            {form.questionMode === 'RAG' && <label>Knowledge collection<select required
              value={form.knowledgeCollectionId}
              onChange={(e) => setForm({...form, knowledgeCollectionId: e.target.value})}>
              <option value="">Select an ingested collection</option>
              {collections.map((collection) =>
                <option key={collection.id} value={collection.id}>{collection.name}</option>)}
            </select></label>}
            <label>Duration (minutes)<input type="number" min="5" max="480" value={form.durationMinutes} onChange={(e) => setForm({...form, durationMinutes: Number(e.target.value)})} /></label>
            <label>Passing percentage<input type="number" min="1" max="100" value={form.passingPercentage} onChange={(e) => setForm({...form, passingPercentage: Number(e.target.value)})} /></label>
            <button type="submit" disabled={form.technologies.length === 0
              || form.questionCount < 1 || form.questionCount > 100
              || (form.questionMode === 'RAG' && !form.knowledgeCollectionId)}>Create draft</button>
          </form>}

          {activeView === 'knowledge' && <KnowledgeBaseView notify={notify} />}
          {activeView === 'drafts' && <>
            <div className="section-heading"><h2>Edit and publish</h2><p>Complete questions, edit content, and publish ready drafts.</p></div>
            <div className="card-grid">
              {interviews.filter((interview) => interview.status === 'DRAFT').map((interview) =>
                <InterviewCard key={interview.id} interview={interview} candidates={candidates} notify={notify} reload={load} />)}
              {!interviews.some((interview) => interview.status === 'DRAFT') &&
                <p className="empty-state">No draft interviews. Create a draft to get started.</p>}
            </div>
          </>}

          {activeView === 'history' && <>
            <div className="section-heading"><h2>Interview history</h2><p>Read-only record of published interviews.</p></div>
            <div className="history-list">
              {interviews.filter((interview) => interview.status !== 'DRAFT').map((interview) =>
                <InterviewCard key={interview.id} interview={interview} candidates={candidates}
                  notify={notify} reload={load} showAssignment={false} collapseQuestions />)}
              {!interviews.some((interview) => interview.status !== 'DRAFT') &&
                <p className="empty-state">No published interviews yet.</p>}
            </div>
          </>}

          {activeView === 'assign' && <>
            <div className="section-heading"><h2>Assign candidate</h2><p>Schedule a candidate for any published interview, old or new.</p></div>
            <div className="card-grid">
              {interviews.filter((interview) => interview.status === 'PUBLISHED').map((interview) =>
                <InterviewCard key={interview.id} interview={interview} candidates={candidates}
                  notify={notify} reload={load} showQuestions={false} />)}
              {!interviews.some((interview) => interview.status === 'PUBLISHED') &&
                <p className="empty-state">No published interviews are available for assignment.</p>}
            </div>
          </>}
        </section>
      </div>
    </main>
  )
}

function KnowledgeBaseView({ notify }: { notify: (message: string, error?: boolean) => void }) {
  const [collections, setCollections] = useState<KnowledgeCollection[]>([])
  const [selected, setSelected] = useState('')
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([])
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [docName, setDocName] = useState('')
  const [docContent, setDocContent] = useState('')
  const [busy, setBusy] = useState('')
  const [source, setSource] = useState<'manual' | 'upload' | 'github'>('manual')
  const [repoUrl, setRepoUrl] = useState('')
  const [file, setFile] = useState<File>()

  async function loadCollections() {
    try { setCollections(await interviewApi.listKnowledgeCollections()) }
    catch (error) { notify(messageOf(error), true) }
  }
  async function loadDocuments(id: string) {
    if (!id) { setDocuments([]); return }
    try { setDocuments(await interviewApi.listCollectionDocuments(id)) }
    catch (error) { notify(messageOf(error), true) }
  }
  useEffect(() => { void loadCollections() }, [])
  useEffect(() => { void loadDocuments(selected) }, [selected])

  async function createCollection(event: FormEvent) {
    event.preventDefault()
    setBusy('collection')
    try {
      const created = await interviewApi.createCollection(name, description)
      setName(''); setDescription('')
      await loadCollections()
      setSelected(created.id)
      notify('Collection created.')
    } catch (error) { notify(messageOf(error), true) } finally { setBusy('') }
  }
  async function addDocument(event: FormEvent) {
    event.preventDefault()
    setBusy('add')
    try {
      const doc = await interviewApi.addDocument(selected, docName, docContent)
      await interviewApi.ingestDocument(doc.id)
      setDocName(''); setDocContent('')
      await loadDocuments(selected)
      notify('Document added and ingested — ready for RAG.')
    } catch (error) { notify(messageOf(error), true) } finally { setBusy('') }
  }
  async function ingest(documentId: string) {
    setBusy(documentId)
    try {
      await interviewApi.ingestDocument(documentId)
      await loadDocuments(selected)
      notify('Document ingested and ready for RAG.')
    } catch (error) { notify(messageOf(error), true) } finally { setBusy('') }
  }
  async function uploadFile(event: FormEvent) {
    event.preventDefault()
    if (!file) return
    setBusy('upload')
    try {
      const doc = await interviewApi.uploadDocument(selected, file)
      await interviewApi.ingestDocument(doc.id)
      setFile(undefined)
      await loadDocuments(selected)
      notify('File uploaded, text extracted, and ingested — ready for RAG.')
    } catch (error) { notify(messageOf(error), true) } finally { setBusy('') }
  }
  async function importGithub(event: FormEvent) {
    event.preventDefault()
    setBusy('github')
    try {
      const { name, text } = await fetchGithubText(repoUrl)
      const doc = await interviewApi.addDocument(selected, name, text)
      await interviewApi.ingestDocument(doc.id)
      setRepoUrl('')
      await loadDocuments(selected)
      notify('GitHub docs imported and ingested — ready for RAG.')
    } catch (error) { notify(messageOf(error), true) } finally { setBusy('') }
  }

  return <>
    <div className="section-heading"><h2>Knowledge base (RAG)</h2>
      <p>Create a collection, add a document, and ingest it. Ingested collections become
        selectable in RAG question mode.</p></div>
    <form className="form-grid" onSubmit={(e) => void createCollection(e)}>
      <label>Collection name<input required value={name} onChange={(e) => setName(e.target.value)} /></label>
      <label>Description<input value={description} onChange={(e) => setDescription(e.target.value)} /></label>
      <button type="submit" disabled={busy === 'collection'}>{busy === 'collection' ? 'Creating…' : 'Create collection'}</button>
    </form>
    {collections.length > 0 && <label>Collection<select value={selected} onChange={(e) => setSelected(e.target.value)}>
      <option value="">Select a collection</option>
      {collections.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
    </select></label>}
    {selected && <>
      <div className="assignment-list">
        <strong>Documents</strong>
        {documents.length === 0 && <p className="empty-state">No documents yet.</p>}
        {documents.map((d) => <div className="assignment-row" key={d.id}>
          <span>{d.fileName} · {d.status}</span>
          {d.status !== 'READY' && <button className="secondary-button" disabled={busy === d.id} onClick={() => void ingest(d.id)}>
            {busy === d.id ? 'Ingesting…' : 'Ingest'}</button>}
        </div>)}
      </div>
      <label>RAG Source<select value={source} onChange={(e) => setSource(e.target.value as 'manual' | 'upload' | 'github')}>
        <option value="manual">Manual entry</option>
        <option value="upload">Upload document (.txt .md .pdf .doc .ppt .xls)</option>
        <option value="github">GitHub repository</option>
      </select></label>
      {source === 'manual' && <form className="form-grid" onSubmit={(e) => void addDocument(e)}>
        <label>Document name<input required value={docName} onChange={(e) => setDocName(e.target.value)} placeholder="e.g. system-design-notes.md" /></label>
        <label>Content (markdown or plain text)<textarea required rows={8} value={docContent} onChange={(e) => setDocContent(e.target.value)} /></label>
        <button type="submit" disabled={busy === 'add'}>{busy === 'add' ? 'Adding…' : 'Add document + ingest'}</button>
      </form>}
      {source === 'upload' && <form className="form-grid" onSubmit={(e) => void uploadFile(e)}>
        <label>File<input required type="file" accept=".txt,.md,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx"
          onChange={(e) => setFile(e.target.files?.[0])} /></label>
        <p className="assignment-window-note">Text is extracted server-side (PDF, Word, PowerPoint, Excel, text).</p>
        <button type="submit" disabled={busy === 'upload' || !file}>{busy === 'upload' ? 'Uploading & ingesting…' : 'Upload + ingest'}</button>
      </form>}
      {source === 'github' && <form className="form-grid" onSubmit={(e) => void importGithub(e)}>
        <label>Public repository URL<input required value={repoUrl} onChange={(e) => setRepoUrl(e.target.value)} placeholder="https://github.com/owner/repo" /></label>
        <p className="assignment-window-note">Pulls README and Markdown/text docs from the repo's default branch.</p>
        <button type="submit" disabled={busy === 'github'}>{busy === 'github' ? 'Importing & ingesting…' : 'Import + ingest'}</button>
      </form>}
    </>}
  </>
}

async function fetchGithubText(repoUrl: string): Promise<{ name: string; text: string }> {
  const match = repoUrl.match(/github\.com\/([^/]+)\/([^/#?]+)/)
  if (!match) throw new Error('Enter a URL like https://github.com/owner/repo')
  const owner = match[1]
  const repo = match[2].replace(/\.git$/, '')
  const meta = await fetch(`https://api.github.com/repos/${owner}/${repo}`)
  if (!meta.ok) throw new Error(`Repository not found or private (HTTP ${meta.status})`)
  const branch = (await meta.json() as { default_branch: string }).default_branch
  const treeRes = await fetch(`https://api.github.com/repos/${owner}/${repo}/git/trees/${branch}?recursive=1`)
  if (!treeRes.ok) throw new Error(`Could not read repository tree (HTTP ${treeRes.status})`)
  const tree = (await treeRes.json() as { tree: Array<{ path: string; type: string }> }).tree
  const files = tree.filter((t) => t.type === 'blob' && /\.(md|markdown|txt|rst)$/i.test(t.path)).slice(0, 20)
  if (files.length === 0) throw new Error('No Markdown/text docs found in the repository')
  let text = ''
  for (const f of files) {
    const raw = await fetch(`https://raw.githubusercontent.com/${owner}/${repo}/${branch}/${f.path}`)
    if (!raw.ok) continue
    text += `\n\n# ${f.path}\n\n${await raw.text()}`
    if (text.length > 420000) break
  }
  return { name: `${owner}-${repo}.md`, text: text.slice(0, 450000).trim() }
}

function toLocalInput(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}
