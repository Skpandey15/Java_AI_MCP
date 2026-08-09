import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { interviewApi } from '../api/interviewApi'
import { useAuth } from '../auth/AuthProvider'
import { ecosystemLabels, ecosystemTechnologies, type Ecosystem } from './InterviewerDashboard'

const curatedTopics: Record<string, string[]> = {
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
    setBusy(true); setError(''); setTopic('')
    interviewApi.suggestTopics([technology], 'MEDIUM')
      .then(({ topics: loaded }) => setTopics(Array.from(new Set([
        ...(curatedTopics[technology] ?? []), ...loaded,
      ]))))
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Unable to load topics'))
      .finally(() => setBusy(false))
  }, [technology])

  function changeEcosystem(value: Ecosystem) {
    setEcosystem(value)
    setTechnology(ecosystemTechnologies[value][0])
  }

  function showDetails(variant: 'guide' | 'notes' = 'guide') {
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
      </div>
      {error && <p className="error-message" role="alert">{error}</p>}
    </section>
  </main>
}
