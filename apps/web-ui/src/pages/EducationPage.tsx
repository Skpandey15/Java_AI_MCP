import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { interviewApi } from '../api/interviewApi'
import { useAuth } from '../auth/AuthProvider'
import { ecosystemLabels, ecosystemTechnologies, type Ecosystem } from './InterviewerDashboard'

// Versions for the "Language & Framework Versions" ecosystem. Kept separate from curatedTopics
// because these product names (Spring Boot, Python, Django, …) are also learning technologies in
// other ecosystems — this keeps version lists from leaking into their learning-topic dropdowns.
const versionTopics: Record<string, string[]> = {
  JDK: [
    'Java 8 (LTS)', 'Java 9', 'Java 10', 'Java 11 (LTS)', 'Java 12', 'Java 13', 'Java 14',
    'Java 15', 'Java 16', 'Java 17 (LTS)', 'Java 18', 'Java 19', 'Java 20', 'Java 21 (LTS)',
    'Java 22', 'Java 23', 'Java 24', 'Java 25 (LTS)',
  ],
  'Spring Boot': [
    'Spring Boot 2.0', 'Spring Boot 2.1', 'Spring Boot 2.2', 'Spring Boot 2.3', 'Spring Boot 2.4',
    'Spring Boot 2.5', 'Spring Boot 2.6', 'Spring Boot 2.7', 'Spring Boot 3.0', 'Spring Boot 3.1',
    'Spring Boot 3.2', 'Spring Boot 3.3', 'Spring Boot 3.4',
  ],
  'Spring Framework': [
    'Spring Framework 5.0', 'Spring Framework 5.1', 'Spring Framework 5.2', 'Spring Framework 5.3',
    'Spring Framework 6.0', 'Spring Framework 6.1', 'Spring Framework 6.2',
  ],
  'Spring Security': [
    'Spring Security 5.7', 'Spring Security 6.0', 'Spring Security 6.1', 'Spring Security 6.2',
    'Spring Security 6.3',
  ],
  'Spring Cloud': [
    'Spring Cloud 2021.0 (Jubilee)', 'Spring Cloud 2022.0 (Kilburn)',
    'Spring Cloud 2023.0 (Leyton)', 'Spring Cloud 2024.0 (Moorgate)',
  ],
  Python: [
    'Python 3.6', 'Python 3.7', 'Python 3.8', 'Python 3.9', 'Python 3.10', 'Python 3.11',
    'Python 3.12', 'Python 3.13',
  ],
  Django: [
    'Django 2.0', 'Django 2.1', 'Django 2.2 (LTS)', 'Django 3.0', 'Django 3.1', 'Django 3.2 (LTS)',
    'Django 4.0', 'Django 4.1', 'Django 4.2 (LTS)', 'Django 5.0', 'Django 5.1',
  ],
}

const curatedTopics: Record<string, string[]> = {
  'Distributed Systems': [
    'CAP theorem and consistency models', 'Consensus with Raft and Paxos',
    'Replication, quorums, and read/write paths', 'Partitioning and sharding strategies',
    'Leader election and coordination', 'Logical clocks, ordering, and causality',
    'Failure detection and fault tolerance', 'Idempotency and exactly-once semantics',
    'Distributed transactions and the saga pattern', 'Observability and debugging distributed systems',
  ],
  'Event Sourcing System': [
    'Event store design and the append-only log', 'Commands, events, and aggregates',
    'Rebuilding state and snapshots', 'CQRS and read-model projections',
    'Eventual consistency and read-your-writes', 'Event versioning and upcasting',
    'Idempotent consumers and deduplication', 'Ordering, partitioning, and replay',
    'Sagas and process managers', 'Auditing, GDPR, and event immutability',
  ],
  'Payment System': [
    'Payment gateway and PSP integration', 'Idempotency and exactly-once charges',
    'Double-entry ledger and balances', 'Distributed transactions and sagas',
    'Failure handling, retries, and reconciliation', 'PCI-DSS, tokenization, and vaulting',
    'Money representation and multi-currency', 'Fraud detection and risk scoring',
    'Webhooks and payment status updates', 'Scaling to high TPS and hot accounts',
  ],
  'URL Shortener': [
    'Key generation (counter, hash, base62)', 'Collision handling and uniqueness',
    'Read-heavy scaling and caching', 'Storage schema and capacity estimation',
    'Redirection (301 vs 302) and latency', 'Custom aliases and link expiration',
    'Analytics and click tracking', 'Rate limiting and abuse prevention',
    'Database sharding and replication', 'High availability and global distribution',
  ],
  'Rate Limiter': [
    'Token bucket vs leaky bucket', 'Fixed window vs sliding window',
    'Distributed rate limiting with Redis', 'Per-user, per-IP, and global limits',
    'Handling bursts and fairness', 'Synchronous vs asynchronous enforcement',
    'Race conditions and atomic counters', 'Response headers and client back-off',
    'Fail-open vs fail-closed', 'Scaling and hot-key mitigation',
  ],
  'Distributed Cache': [
    'Cache-aside, read-through, and write-through', 'Eviction policies (LRU, LFU, TTL)',
    'Consistent hashing and rebalancing', 'Cache invalidation strategies',
    'Hot keys and the thundering herd', 'Write-back and durability trade-offs',
    'Replication and high availability', 'Cache stampede and request coalescing',
    'Consistency with the source of truth', 'Sizing, sharding, and eviction tuning',
  ],
  'Chat / Messaging System': [
    'Real-time delivery (WebSocket, long-poll)', 'Message ordering and delivery guarantees',
    'Online presence and typing indicators', 'Fan-out for group chats',
    'Message storage and history', 'Read receipts and delivery status',
    'Push notifications and offline delivery', 'End-to-end encryption',
    'Scaling connections and sharding', 'Multi-device synchronization',
  ],
  'News Feed System': [
    'Fan-out on write vs fan-out on read', 'Feed ranking and relevance',
    'Handling celebrities and hot users', 'Pagination and infinite scroll',
    'Caching and precomputation', 'Storage schema for posts and the social graph',
    'Real-time updates and freshness', 'Deduplication and consistency',
    'Scaling reads and hot partitions', 'Media handling and CDNs',
  ],
  'Notification System': [
    'Multi-channel delivery (push, email, SMS)', 'Templating and personalization',
    'Prioritization and rate limiting', 'Deduplication and idempotency',
    'Retries, dead-letter queues, and failures', 'User preferences and opt-out',
    'Fan-out and batching', 'Delivery tracking and analytics',
    'Scheduling and time zones', 'Scaling and provider failover',
  ],
  'Search Autocomplete': [
    'Tries and prefix data structures', 'Top-k ranking by popularity',
    'Sharding the trie', 'Caching and latency budgets',
    'Real-time updates and freshness', 'Fuzzy matching and typo tolerance',
    'Personalization and context', 'Data collection and aggregation',
    'Scaling QPS and hot prefixes', 'Memory vs storage trade-offs',
  ],
  'Ride-Hailing System': [
    'Geospatial indexing (geohash, QuadTree)', 'Matching riders and drivers',
    'Real-time location updates', 'Surge pricing',
    'ETA estimation and routing', 'Trip lifecycle and state management',
    'Payments, receipts, and splits', 'Consistency and location hotspots',
    'Scaling high-volume location writes', 'Reliability and regional failover',
  ],
  'Distributed File Storage': [
    'Chunking and deduplication', 'Metadata service design',
    'Sync and conflict resolution', 'Consistency and versioning',
    'Resumable upload and download', 'Sharing and permissions',
    'Replication and durability', 'Caching and CDNs',
    'Storage tiering and cost', 'Scaling metadata and blob storage',
  ],
  'E-commerce & Inventory System': [
    'Catalog and product search', 'Shopping cart design',
    'Inventory reservation and consistency', 'Order placement and checkout flow',
    'Preventing oversell under concurrency', 'Payments and order fulfillment',
    'Pricing, discounts, and promotions', 'Idempotent orders and retries',
    'Flash sales and hot-item hotspots', 'Scaling reads, writes, and search',
  ],
  'Ticket Booking System': [
    'Seat inventory and availability', 'Concurrency and seat locking / holds',
    'Preventing double-booking', 'Reservation timeouts and release',
    'Payment integration and order flow', 'Handling high-demand on-sales',
    'Consistency vs availability trade-offs', 'Waiting rooms and virtual queues',
    'Idempotency and retries', 'Scaling reads and write hotspots',
  ],
  Slack: [
    'Slack Platform Fundamentals', 'Workspaces, Channels, and Messages',
    'Incoming Webhooks', 'Slack Apps, Bot Users, and OAuth Scopes',
    'Block Kit Message Formatting', 'Slash Commands and Interactivity',
    'Web API and Events API', 'ChatOps and CI/CD Deploy Notifications',
    'Integrating with GitHub Actions and Argo CD', 'Security: Signing Secrets and Token Handling',
    'Rate Limits, Retries, and Best Practices',
  ],
  'Microsoft Teams': [
    'Teams Platform Fundamentals', 'Teams, Channels, and Chats',
    'Incoming Webhooks and Connectors', 'Adaptive Cards and Message Formatting',
    'Bots with the Bot Framework', 'Message Extensions and Tabs',
    'Power Automate (Workflows) Integration', 'ChatOps and CI/CD Notifications',
    'Microsoft Graph API and Permissions', 'Security and Compliance',
    'Rate Limits and Best Practices',
  ],
  Discord: [
    'Discord Platform Fundamentals', 'Servers, Channels, and Roles',
    'Incoming Webhooks', 'Bots and the Gateway', 'Slash Commands and Interactions',
    'Embeds and Message Formatting', 'OAuth2 and Permissions',
    'ChatOps and CI/CD Alerts', 'Rate Limits and Sharding',
    'Security and Token Handling', 'Best Practices',
  ],
  'Google Chat': [
    'Google Chat Fundamentals', 'Spaces, Threads, and Messages',
    'Incoming Webhooks', 'Chat Apps and Bots', 'Cards v2 Message Formatting',
    'Slash Commands and Dialogs', 'Events and the Chat API',
    'ChatOps and CI/CD Notifications', 'Google Workspace Auth and Scopes',
    'Security and Best Practices', 'Rate Limits',
  ],
  Mattermost: [
    'Mattermost Fundamentals', 'Teams, Channels, and Messages',
    'Incoming Webhooks', 'Outgoing Webhooks and Slash Commands',
    'Interactive Message Buttons', 'Bot Accounts and the REST API',
    'Plugins and Integrations', 'ChatOps and CI/CD Notifications',
    'Self-Hosting and Deployment', 'Authentication and Access Control',
    'High Availability and Scaling',
  ],
  'Rocket.Chat': [
    'Rocket.Chat Fundamentals', 'Channels, Groups, and Messages',
    'Incoming Webhooks', 'Outgoing Webhooks and Slash Commands',
    'Bots and the Realtime/REST API', 'Message Attachments and Formatting',
    'Apps and Integrations', 'ChatOps and CI/CD Notifications',
    'Self-Hosting and Deployment', 'Authentication and Permissions',
    'Scaling and Administration',
  ],
  GraphQL: [
    'Schema and Type System', 'Queries and Variables', 'Mutations and Input Types',
    'Resolvers and Context', 'Strawberry GraphQL Fundamentals', 'Strawberry with Django',
    'Authentication and Authorization', 'DataLoader and N+1 Queries',
    'Subscriptions', 'Testing GraphQL APIs', 'Performance and Security',
  ],
  'Strawberry GraphQL': [
    'Schema and Object Types', 'Queries and Mutations', 'Resolvers',
    'Strawberry with Django', 'Django ORM Integration', 'Permissions and Authentication',
    'DataLoader', 'Subscriptions', 'Testing Strawberry APIs', 'Production Deployment',
  ],
  Django: [
    'Project Structure', 'Models and ORM', 'Views and URL Routing', 'Templates and Forms',
    'Django REST Framework', 'Strawberry GraphQL Integration', 'Authentication and Permissions',
    'Caching and Performance', 'Testing Django Applications', 'Production Deployment',
  ],
  Jolt: [
    'Jolt Fundamentals', 'Shift Transformations', 'Default and Remove Operations',
    'Cardinality and Modify Operations', 'Wildcards and Ampersands', 'Chained Transformations',
    'Writing Jolt Specifications', 'Testing Transformations', 'Performance and Troubleshooting',
  ],
  'HashiCorp Vault': [
    'Vault Architecture', 'Initialization, Sealing, and Unsealing', 'Tokens and Policies',
    'KV Secrets Engine', 'Dynamic Database Credentials', 'Kubernetes Authentication',
    'AppRole Authentication', 'Transit Encryption', 'Secret Rotation and Leases',
    'High Availability and Disaster Recovery', 'Audit Devices and Production Hardening',
  ],
  Keycloak: [
    'Realms, Clients, and Users', 'OAuth 2.0 and OpenID Connect Flows', 'Roles and Groups',
    'Client Scopes and Claims', 'Service Accounts', 'MFA and Required Actions',
    'Identity Brokering', 'Authorization Services', 'Token Lifecycles',
    'Clustering and Production Hardening',
  ],
  'Apache Camel': [
    'Routes and Endpoints', 'Enterprise Integration Patterns', 'Components and URIs',
    'Processors and Beans', 'Data Formats and Type Conversion', 'Error Handling and Redelivery',
    'Testing Camel Routes', 'Spring Boot Integration', 'Observability and Production Deployment',
  ],
  RabbitMQ: [
    'Exchanges, Queues, and Bindings', 'Routing Keys and Exchange Types',
    'Publish Confirms and Consumer Acknowledgements', 'Dead-Letter Exchanges',
    'Retries and Idempotent Consumers', 'Ordering and Prefetch', 'Quorum Queues',
    'Security and Access Control', 'Monitoring and Production Operations',
  ],
  NGINX: [
    'Server and Location Blocks', 'Reverse Proxying', 'Load-Balancing Algorithms',
    'TLS Termination', 'Caching and Compression', 'Rate Limiting', 'Headers and CORS',
    'Health Checks', 'Logging and Observability', 'Security Hardening',
  ],
  Postman: [
    'Collections and Environments', 'Variables and Secret Handling', 'Request Scripting',
    'Response Tests', 'Authentication', 'Collection Runner', 'Mock Servers',
    'Contract Testing', 'Newman in CI/CD', 'Team Workspaces and Governance',
  ],
  'OpenAPI and Swagger': [
    'OpenAPI Document Structure', 'Paths and Operations', 'Schemas and Composition',
    'Parameters and Request Bodies', 'Responses and Error Models', 'Security Schemes',
    'Examples and Documentation', 'Validation and Linting', 'Code Generation',
    'API-First Development and Versioning',
  ],
  Webhooks: [
    'Webhook Design Fundamentals', 'Event Payloads and Versioning', 'Signature Verification',
    'Retries and Exponential Backoff', 'Idempotency and Deduplication', 'Ordering',
    'Delivery Logs and Replay', 'Testing Webhooks', 'Security and Production Reliability',
  ],
  Activiti: [
    'Activiti Architecture', 'BPMN 2.0 Process Modeling', 'Process Definitions and Instances',
    'User Tasks and Service Tasks', 'Gateways, Events, and Subprocesses',
    'Java and Spring Boot Integration', 'Variables and Forms', 'Listeners and Delegates',
    'Job Executor, Timers, and Async Work', 'Error Handling and Compensation',
    'REST APIs, Testing, and Production Operations',
  ],
  Flowable: [
    'Flowable Architecture and Engines', 'BPMN 2.0 Process Modeling',
    'Process Definitions and Instances', 'User Tasks and Service Tasks',
    'Gateways, Events, and Subprocesses', 'Java and Spring Boot Integration',
    'Variables, Expressions, and Forms', 'Listeners and Service Delegates',
    'Async Executor, Jobs, and Timers', 'CMMN and DMN Integration',
    'REST APIs, Testing, Migration, and Production Operations',
  ],
  jBPM: [
    'jBPM and KIE Architecture', 'BPMN 2.0 Modeling', 'Processes and Human Tasks',
    'Work Items and Service Tasks', 'Rules and Drools Integration', 'Persistence and Transactions',
    'KIE Server APIs', 'Testing, Monitoring, and Production Deployment',
  ],
  Kogito: [
    'Cloud-Native Process Automation', 'BPMN 2.0 Support', 'Process Services',
    'Quarkus and Spring Boot Integration', 'User Tasks', 'Decisions with DMN and Drools',
    'Events and Messaging', 'Persistence, Testing, and Kubernetes Deployment',
  ],
  Bonita: [
    'Bonita Architecture', 'BPMN Process Design', 'Human and Service Tasks',
    'Business Data Model', 'Forms and UI Designer', 'Connectors and REST APIs',
    'Organization and Permissions', 'Testing, Monitoring, and Deployment',
  ],
  ProcessMaker: [
    'BPMN Process Modeling', 'Tasks, Events, and Gateways', 'Forms and Screens',
    'Users, Groups, and Assignments', 'Scripts and Connectors', 'API Integration',
    'Process Versioning', 'Monitoring, Security, and Production Administration',
  ],
  Operaton: [
    'Operaton Architecture', 'BPMN 2.0 Execution', 'Java and Spring Integration',
    'External Tasks', 'Human Tasks and Forms', 'DMN Decisions',
    'Migration from Camunda 7', 'Testing and Production Operations',
  ],
  'CIB seven': [
    'CIB seven Architecture', 'BPMN and DMN Execution', 'Camunda 7 Compatibility',
    'Task and Cockpit Applications', 'Spring Boot Integration', 'Process Migration',
    'Testing, Security, and Production Operations',
  ],
  'Imixs-Workflow': [
    'Imixs Architecture', 'BPMN Model Design', 'Workflow Tasks and Events',
    'Jakarta EE Integration', 'Workitems and Data', 'Security and Access Control',
    'REST APIs', 'Testing and Production Deployment',
  ],
  'Oracle BPM Suite': [
    'Oracle BPM Architecture', 'BPMN 2.0 Modeling', 'Human and Service Tasks',
    'Business Rules', 'Process Composer and BPM Studio', 'SOA Integration',
    'Monitoring, Administration, and High Availability',
  ],
  'IBM Business Automation Workflow': [
    'BAW Architecture', 'BPMN Process Applications', 'Human Services and Coaches',
    'Integration Services', 'Business Objects and Variables', 'Case Management',
    'Governance, Monitoring, and Production Administration',
  ],
  Bizagi: [
    'Bizagi Process Modeler', 'BPMN 2.0 Modeling', 'Process Automation',
    'Data Models and Forms', 'Business Rules', 'Assignments and Work Portals',
    'Integration, Deployment, and Administration',
  ],
  'SAP Signavio': [
    'Process Manager and Collaboration Hub', 'BPMN 2.0 Modeling',
    'Process Governance', 'Workflow Accelerator', 'Process Intelligence',
    'Dictionary and Conventions', 'Integration, Governance, and Transformation',
  ],
  Temporal: [
    'Durable Execution Fundamentals', 'Workflows and Activities', 'Workers and Task Queues',
    'Retries, Timeouts, and Heartbeats', 'Signals, Queries, and Updates', 'Workflow Versioning',
    'Child Workflows and Sagas', 'Testing and Replay Safety', 'Observability and Production Operations',
  ],
  Camunda: [
    'BPMN Fundamentals', 'Process Models and Instances', 'Service and User Tasks',
    'Gateways and Events', 'Job Workers', 'DMN Decisions', 'Forms and Human Workflows',
    'Error Handling and Incidents', 'Testing, Operate, and Production Deployment',
  ],
  'Netflix Conductor': [
    'Workflow and Task Definitions', 'Workers and Task Polling', 'Fork, Join, and Decisions',
    'Sub-Workflows', 'Retries, Timeouts, and Failure Handling', 'Event Tasks',
    'State Persistence', 'Testing and Observability', 'Scaling and Production Operations',
  ],
  'AWS Step Functions': [
    'Amazon States Language', 'Task, Choice, Map, and Parallel States',
    'Standard vs Express Workflows', 'Service Integrations', 'Retries and Catchers',
    'Callback and Human Approval Patterns', 'IAM and Security',
    'Testing, Observability, and Cost Optimization',
  ],
  'Azure Durable Functions': [
    'Orchestrator, Activity, and Client Functions', 'Deterministic Orchestrators',
    'Function Chaining and Fan-Out/Fan-In', 'Async HTTP APIs', 'Human Interaction',
    'Timers and External Events', 'Entities', 'Versioning, Testing, and Production Operations',
  ],
  'Argo Workflows': [
    'Workflow Specifications', 'Steps and DAG Templates', 'Parameters and Artifacts',
    'Loops and Conditionals', 'Retries and Synchronization', 'Workflow Templates',
    'Secrets and Service Accounts', 'Artifact Repositories', 'Scaling and Troubleshooting',
  ],
  'Apache Airflow': [
    'DAGs, Tasks, and Operators', 'Scheduling and Data Intervals', 'TaskFlow API',
    'XComs and Dependencies', 'Sensors and Deferrable Operators', 'Retries and Backfills',
    'Executors', 'Testing DAGs', 'Security, Monitoring, and Production Deployment',
  ],
  Prefect: [
    'Flows and Tasks', 'Deployments and Work Pools', 'Parameters and Results',
    'Retries, Caching, and Concurrency', 'State and Events', 'Blocks and Integrations',
    'Testing Flows', 'Observability and Production Operations',
  ],
  Dagster: [
    'Software-Defined Assets', 'Ops, Jobs, and Graphs', 'Resources and Configuration',
    'Partitions and Backfills', 'Sensors and Schedules', 'Asset Checks and Lineage',
    'Testing', 'Dagster Daemon and Production Deployment',
  ],
  n8n: [
    'Nodes, Triggers, and Connections', 'Expressions and Data Mapping', 'Credentials',
    'Webhooks and API Integrations', 'Branching and Loops', 'Error Workflows and Retries',
    'Sub-Workflows', 'Queue Mode and Scaling', 'Security and Production Deployment',
  ],
}

export function EducationPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const auth = useAuth()
  // Prefer the dashboard the user actually came from (handles dual-role accounts);
  // fall back to role when entered directly by URL.
  const fromPath = (location.state as { from?: string } | null)?.from
  const dashboardPath = fromPath ?? (auth.roles.includes('interviewer') ? '/interviewer' : '/candidate')
  const isInterviewer = dashboardPath === '/interviewer'
  const ecosystems = useMemo(() => (Object.keys(ecosystemLabels) as Ecosystem[])
    .sort((a, b) => ecosystemLabels[a].localeCompare(ecosystemLabels[b])), [])
  const [ecosystem, setEcosystem] = useState<Ecosystem>('JAVA')
  const [technology, setTechnology] = useState<string>(ecosystemTechnologies.JAVA[0])
  const [topic, setTopic] = useState('')
  const [topics, setTopics] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    setError(''); setTopic('')
    // The version ecosystem has a fixed, known list of releases — show only those, no AI topics.
    if (ecosystem === 'VERSIONS') {
      setTopics(versionTopics[technology] ?? [])
      setBusy(false)
      return
    }
    setBusy(true)
    interviewApi.suggestTopics([technology], 'MEDIUM')
      .then(({ topics: loaded }) => setTopics(Array.from(new Set([
        ...(curatedTopics[technology] ?? []), ...loaded,
      ]))))
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Unable to load topics'))
      .finally(() => setBusy(false))
  }, [technology, ecosystem])

  function changeEcosystem(value: Ecosystem) {
    setEcosystem(value)
    setTechnology(ecosystemTechnologies[value][0])
  }

  function showDetails(variant: 'guide' | 'notes' | 'design' | 'release' = 'guide') {
    const params = new URLSearchParams({
      ecosystem: ecosystemLabels[ecosystem], technology, topic, variant })
    navigate(`/education/details?${params}`)
  }

  return <main className="dashboard education-page">
    <div className="dashboard-header">
      <div><p className="eyebrow">{isInterviewer ? 'Interviewer workspace · 6' : 'Candidate workspace'}</p><h1>Educate Yourself</h1></div>
      <button className="secondary-button" onClick={() => navigate(dashboardPath)}>
        {isInterviewer ? 'Interview management' : 'Back to my interviews'}
      </button>
    </div>
    <p className="summary">Choose a technology topic and build a structured zero-to-hero learning guide.</p>
    <section className="education-selector">
      <label>Ecosystem<select value={ecosystem} onChange={(event) => changeEcosystem(event.target.value as Ecosystem)}>
        {ecosystems.map((value) => <option key={value} value={value}>{ecosystemLabels[value]}</option>)}
      </select></label>
      <label>Technology<select value={technology} onChange={(event) => setTechnology(event.target.value)}>
        {ecosystemTechnologies[ecosystem].map((value) => <option key={value}>{value}</option>)}
      </select></label>
      <label>Topic<select value={topic} disabled={busy || topics.length === 0}
        onChange={(event) => setTopic(event.target.value)}>
        <option value="">{busy ? 'Loading topics…' : 'Select a topic'}</option>
        {topics.map((value) => <option key={value}>{value}</option>)}
      </select></label>
      <div className="compact-actions">
        <button disabled={!topic || busy} onClick={() => showDetails('guide')}>Show Details</button>
        <button className="secondary-button" disabled={!topic || busy}
          onClick={() => showDetails('notes')}
          title="Concise, interview-focused notes: key concepts, likely questions with answers, gotchas, and a quick summary.">
          Interview Notes</button>
        <button className="secondary-button" disabled={!topic || busy}
          onClick={() => showDetails('design')}
          title="Where this topic fits in software design: layer, trade-offs, alternatives, interactions, pitfalls, and the design-interview angle.">
          Design Perspective</button>
        <button className="secondary-button" disabled={!topic || busy}
          onClick={() => showDetails('release')}
          title="What's new in this version: headline features, API additions, deprecations, migration notes, and the interview angle. Best for JDK & Spring versions.">
          What's New</button>
      </div>
      {error && <p className="error-message" role="alert">{error}</p>}
    </section>
  </main>
}
