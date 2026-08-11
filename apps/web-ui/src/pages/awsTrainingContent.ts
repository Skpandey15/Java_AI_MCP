// Course content for "AWS Zero to Production Hero — 15-Day Program".
// Phase 1 (Days 1–3) is fully authored; Phases 2–5 (Days 4–15) carry their focus and are
// marked as upcoming so the dependent Phase → Day → Content Type dropdowns work end-to-end.

export type AwsContentType = 'theoretical' | 'practical'

export interface AwsMcq {
  q: string
  options: string[]
  answer: number // index into options
}

export type AwsBlock =
  | { kind: 'lead'; text: string }
  | { kind: 'objectives'; items: string[] }
  | { kind: 'topics'; heading?: string; items: string[] }
  | { kind: 'diagram'; caption?: string; code: string }
  | { kind: 'callout'; tone?: 'cost' | 'info' | 'warn'; heading: string; items: string[] }
  | { kind: 'steps'; heading?: string; items: string[] }
  | { kind: 'checklist'; heading?: string; items: string[] }
  | { kind: 'code'; heading?: string; code: string }
  | { kind: 'scenarios'; heading?: string; items: string[] }
  | { kind: 'mcqs'; heading?: string; items: AwsMcq[] }
  | { kind: 'interview'; heading?: string; q: string; a: string }
  | { kind: 'links'; heading?: string; items: { label: string; href: string }[] }
  | { kind: 'deliverable'; text: string }

export interface AwsDayContent {
  title: string
  blocks: AwsBlock[]
}

export interface AwsDay {
  day: number
  theoretical?: AwsDayContent
  practical?: AwsDayContent
}

export interface AwsPhase {
  id: number
  label: string
  focus: string
  days: number[]
}

export const awsPhases: AwsPhase[] = [
  { id: 1, label: 'Phase 1', focus: 'AWS foundations, IAM and networking', days: [1, 2, 3] },
  { id: 2, label: 'Phase 2', focus: 'Compute, storage and databases', days: [4, 5, 6] },
  { id: 3, label: 'Phase 3', focus: 'Containers, ECR, ECS and EKS', days: [7, 8, 9] },
  { id: 4, label: 'Phase 4', focus: 'CI/CD, GitOps, security and observability', days: [10, 11, 12] },
  { id: 5, label: 'Phase 5', focus: 'Production architecture and capstone deployment', days: [13, 14, 15] },
]

export const contentTypeLabels: Record<AwsContentType, string> = {
  theoretical: 'Theoretical',
  practical: 'Practical',
}

const day1Theory: AwsDayContent = {
  title: 'AWS account, global infrastructure and cost control',
  blocks: [
    { kind: 'lead', text: 'Understand how AWS is organised globally and how to keep an account secure and cost-controlled from day one.' },
    {
      kind: 'objectives',
      items: [
        'Explain the AWS global infrastructure: Regions, Availability Zones and edge locations.',
        'Differentiate IaaS, PaaS and SaaS and place AWS services within those models.',
        'Describe the shared-responsibility model and where your duties begin.',
        'Use the Console, CLI and CloudShell to interact with AWS.',
        'Set up proactive cost controls: Budgets, Cost Explorer and anomaly detection.',
      ],
    },
    {
      kind: 'topics',
      heading: 'Sections',
      items: [
        'Cloud computing fundamentals',
        'IaaS, PaaS, SaaS and AWS service models',
        'AWS Regions',
        'Availability Zones',
        'Edge locations',
        'Regional versus global AWS services',
        'AWS account structure',
        'Shared-responsibility model',
        'AWS Console, CLI and CloudShell',
        'Service quotas',
        'Resource tagging',
        'Cost Explorer',
        'AWS Budgets',
        'Cost Anomaly Detection',
      ],
    },
    {
      kind: 'diagram',
      caption: 'A Region contains multiple isolated Availability Zones; edge locations sit closer to users.',
      code: `flowchart TB
  U["Users worldwide"] --> E["Edge locations / CloudFront"]
  E --> R
  subgraph R["AWS Region (e.g. eu-west-1)"]
    direction LR
    AZa["Availability Zone A<br/>(1+ data centres)"]
    AZb["Availability Zone B"]
    AZc["Availability Zone C"]
  end
  AZa <--> AZb
  AZb <--> AZc
  AZa <--> AZc`,
    },
    {
      kind: 'callout',
      tone: 'cost',
      heading: 'Five expensive resources to watch',
      items: [
        'NAT Gateway — hourly charge plus per-GB data processing.',
        'Unused Elastic IP — billed while allocated but not attached to a running instance.',
        'Large EC2 instance — compute cost scales with size and runtime.',
        'EKS control plane — a fixed hourly charge per cluster.',
        'RDS Multi-AZ database — roughly doubles instance and storage cost for the standby.',
      ],
    },
    {
      kind: 'callout',
      tone: 'info',
      heading: 'Important interview notes',
      items: [
        'An Availability Zone is one or more discrete data centres with redundant power and networking; a Region is a collection of AZs.',
        'IAM, Route 53, CloudFront and WAF are global services; EC2, VPC, RDS and S3 buckets are Regional.',
        'Under the shared-responsibility model AWS secures the cloud; you secure what you put in it (data, IAM, config, patching).',
        'Cost control is a security control: anomaly detection surfaces both waste and compromised-credential crypto-mining.',
      ],
    },
    {
      kind: 'mcqs',
      heading: 'Ten MCQs',
      items: [
        { q: 'What is an AWS Availability Zone?', options: ['A single data centre', 'One or more isolated data centres within a Region', 'A global edge cache', 'A billing boundary'], answer: 1 },
        { q: 'Which service is global rather than Regional?', options: ['EC2', 'RDS', 'IAM', 'VPC'], answer: 2 },
        { q: 'Under the shared-responsibility model, who patches the guest OS on an EC2 instance?', options: ['AWS', 'The customer', 'Nobody', 'The Region'], answer: 1 },
        { q: 'Which best describes IaaS?', options: ['Fully managed application', 'Managed runtime/platform', 'Virtualised compute, storage and networking', 'A SaaS dashboard'], answer: 2 },
        { q: 'Which resource commonly incurs cost even when "unused"?', options: ['A stopped EC2 instance root volume snapshot', 'An unattached Elastic IP', 'An empty S3 bucket', 'An IAM user'], answer: 1 },
        { q: 'What does Cost Anomaly Detection primarily provide?', options: ['Hard spending caps', 'ML-based alerts on unusual spend', 'Reserved-instance purchases', 'Free tier tracking only'], answer: 1 },
        { q: 'Edge locations are mainly used by which service?', options: ['CloudFront', 'RDS', 'EKS', 'EBS'], answer: 0 },
        { q: 'Which tool gives a browser-based shell with your credentials pre-configured?', options: ['CloudTrail', 'CloudShell', 'CloudFormation', 'CloudWatch'], answer: 1 },
        { q: 'Service quotas exist primarily to…', options: ['Increase latency', 'Protect accounts from runaway usage and cost', 'Replace IAM', 'Enable global services'], answer: 1 },
        { q: 'Resource tagging most directly enables…', options: ['Faster networking', 'Cost allocation and organisation', 'Encryption at rest', 'Multi-AZ failover'], answer: 1 },
      ],
    },
    {
      kind: 'interview',
      heading: '“Region vs Availability Zone” interview question',
      q: 'What is the difference between an AWS Region and an Availability Zone, and why does it matter for a production deployment?',
      a: 'A Region is a named geographic area (e.g. eu-west-1) containing multiple Availability Zones. An Availability Zone is one or more physically isolated data centres within that Region, with independent power, cooling and networking, connected to the other AZs by low-latency links. It matters because deploying across two or more AZs makes a workload resilient to a single data-centre failure, while cross-Region design addresses larger disasters, data-residency and latency to users. Regional isolation also means most service quotas, endpoints and outages are scoped per Region.',
    },
    {
      kind: 'links',
      heading: 'Study-material links',
      items: [
        { label: 'AWS Global Infrastructure', href: 'https://aws.amazon.com/about-aws/global-infrastructure/' },
        { label: 'Shared Responsibility Model', href: 'https://aws.amazon.com/compliance/shared-responsibility-model/' },
        { label: 'AWS Budgets', href: 'https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-managing-costs.html' },
        { label: 'AWS Cost Anomaly Detection', href: 'https://docs.aws.amazon.com/cost-management/latest/userguide/getting-started-ad.html' },
      ],
    },
  ],
}

const day1Practical: AwsDayContent = {
  title: 'Secure and prepare an AWS learning account',
  blocks: [
    { kind: 'lead', text: 'Harden a fresh AWS account and wire up cost controls before you deploy anything.' },
    {
      kind: 'steps',
      heading: 'Lab steps',
      items: [
        'Secure the root account with MFA.',
        'Verify that root access keys do not exist.',
        'Create an administrative learning identity.',
        'Configure the AWS CLI.',
        'Select a primary Region.',
        'Create a recurring monthly budget.',
        'Configure budget email alerts.',
        'Enable Cost Explorer.',
        'Configure Cost Anomaly Detection.',
        'Establish a resource-tagging standard.',
      ],
    },
    {
      kind: 'code',
      heading: 'Run CLI authentication validation',
      code: `aws sts get-caller-identity
aws configure get region`,
    },
    {
      kind: 'checklist',
      heading: 'Completion checklist',
      items: [
        'Root MFA enabled',
        'Root access keys absent',
        'Administrative identity configured',
        'CLI authentication successful',
        'Primary Region configured',
        'Budget active',
        'Anomaly alerts active',
        'Tagging standard documented',
      ],
    },
    { kind: 'deliverable', text: 'Secured AWS account with CLI access and cost controls.' },
  ],
}

const day2Theory: AwsDayContent = {
  title: 'IAM and AWS security fundamentals',
  blocks: [
    { kind: 'lead', text: 'Learn how AWS decides who can do what: identities, policies, evaluation logic and temporary credentials.' },
    {
      kind: 'objectives',
      items: [
        'Distinguish authentication from authorization in AWS.',
        'Read and reason about an IAM policy document.',
        'Apply least privilege using roles and temporary credentials.',
        'Explain the IAM policy evaluation order (explicit deny wins).',
      ],
    },
    {
      kind: 'topics',
      heading: 'Sections',
      items: [
        'Authentication versus authorization',
        'IAM users', 'IAM groups', 'IAM roles', 'IAM policies',
        'Identity-based policies', 'Resource-based policies', 'Policy JSON structure',
        'IAM policy evaluation', 'Explicit Allow', 'Explicit Deny', 'Least privilege',
        'Temporary credentials', 'AWS STS', 'AssumeRole', 'Workload identities',
        'AWS KMS introduction', 'CloudTrail introduction', 'IAM Access Analyzer',
        'Root-user security practices',
      ],
    },
    {
      kind: 'diagram',
      caption: 'Policy evaluation: an explicit Deny always overrides any Allow.',
      code: `flowchart TD
  A["Request"] --> B{"Explicit Deny?"}
  B -- Yes --> D["DENY"]
  B -- No --> C{"Explicit Allow?"}
  C -- No --> D
  C -- Yes --> E["ALLOW"]`,
    },
    {
      kind: 'scenarios',
      heading: 'Assessment content',
      items: [
        'Interpret an IAM policy.',
        'Correct an over-permissive policy.',
        'Explain why root access keys are prohibited.',
        'Troubleshoot an AccessDenied response.',
      ],
    },
    {
      kind: 'links',
      heading: 'Study-material links',
      items: [
        { label: 'IAM policy evaluation logic', href: 'https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_evaluation-logic.html' },
        { label: 'AWS STS AssumeRole', href: 'https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html' },
        { label: 'IAM Access Analyzer', href: 'https://docs.aws.amazon.com/IAM/latest/UserGuide/what-is-access-analyzer.html' },
      ],
    },
  ],
}

const day2Practical: AwsDayContent = {
  title: 'Implement and validate least-privilege access',
  blocks: [
    { kind: 'lead', text: 'Create a restricted role, assume it, and prove that allowed actions succeed and prohibited actions are denied.' },
    {
      kind: 'steps',
      heading: 'Lab steps',
      items: [
        'Create a restricted IAM role.',
        'Create a least-privilege IAM policy.',
        'Attach the policy to the role.',
        'Assume the role through AWS CLI.',
        'Perform an allowed operation.',
        'Perform a prohibited operation.',
        'Verify the expected AccessDenied result.',
        'Inspect the associated CloudTrail event.',
        'Run IAM Access Analyzer.',
        'Remove unnecessary permissions.',
      ],
    },
    {
      kind: 'code',
      heading: 'Example validation commands',
      code: `aws sts assume-role \\
  --role-arn arn:aws:iam::<account-id>:role/AwsTrainingRole \\
  --role-session-name aws-training

aws cloudtrail lookup-events \\
  --lookup-attributes AttributeKey=EventName,AttributeValue=AssumeRole`,
    },
    { kind: 'deliverable', text: 'Role-based access with verified least-privilege behaviour.' },
  ],
}

const day3Theory: AwsDayContent = {
  title: 'VPC and AWS networking',
  blocks: [
    { kind: 'lead', text: 'Design a Virtual Private Cloud: subnets, routing, gateways and the controls that keep workloads reachable yet private.' },
    {
      kind: 'objectives',
      items: [
        'Lay out public, private and database subnets across two Availability Zones.',
        'Route traffic correctly with route tables, an Internet Gateway and a NAT Gateway.',
        'Choose between security groups and network ACLs.',
        'Diagnose common connectivity failures.',
      ],
    },
    {
      kind: 'topics',
      heading: 'Sections',
      items: [
        'VPC fundamentals', 'IPv4 and CIDR ranges', 'Public subnet', 'Private subnet',
        'Route tables', 'Internet Gateway', 'NAT Gateway', 'Elastic IP',
        'Security groups', 'Network ACLs', 'Security group versus NACL', 'DNS and DHCP',
        'Route 53 Resolver', 'VPC endpoints', 'Gateway and interface endpoints', 'VPC Flow Logs',
        'Multi-AZ networking', 'Public and private workload placement', 'Network troubleshooting',
        'Production VPC design',
      ],
    },
    {
      kind: 'diagram',
      caption: 'A two-AZ VPC: public subnets reach the internet via an IGW; private subnets egress via a NAT Gateway.',
      code: `flowchart TB
  IGW["Internet Gateway"]
  subgraph VPC["VPC 10.0.0.0/16"]
    subgraph AZ1["Availability Zone A"]
      PUB1["Public subnet"] --> NAT["NAT Gateway"]
      APP1["Private app subnet"]
      DB1["Private DB subnet"]
    end
    subgraph AZ2["Availability Zone B"]
      PUB2["Public subnet"]
      APP2["Private app subnet"]
      DB2["Private DB subnet"]
    end
  end
  IGW --> PUB1
  IGW --> PUB2
  APP1 --> NAT
  APP2 --> NAT
  NAT --> IGW`,
    },
    {
      kind: 'scenarios',
      heading: 'Assessment scenarios',
      items: [
        'EC2 instance has no internet access.',
        'Route table is incorrectly configured.',
        'Security group blocks application traffic.',
        'Private database is publicly exposed.',
        'Private subnet cannot download software updates.',
      ],
    },
    {
      kind: 'links',
      heading: 'Study-material links',
      items: [
        { label: 'VPC user guide', href: 'https://docs.aws.amazon.com/vpc/latest/userguide/what-is-amazon-vpc.html' },
        { label: 'Security groups vs network ACLs', href: 'https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-comparison.html' },
        { label: 'NAT Gateways', href: 'https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html' },
      ],
    },
  ],
}

const day3Practical: AwsDayContent = {
  title: 'Build a production-style two-AZ VPC',
  blocks: [
    { kind: 'lead', text: 'Provision a resilient VPC across two Availability Zones with public, application and database tiers.' },
    {
      kind: 'steps',
      heading: 'Resources to create',
      items: [
        'One VPC',
        'Two public subnets',
        'Two private application subnets',
        'Two private database subnets',
        'Two Availability Zones',
        'Internet Gateway',
        'Public and private route tables',
        'NAT Gateway',
        'Security groups',
        'VPC Flow Logs',
        'Temporary EC2 test instance',
      ],
    },
    {
      kind: 'checklist',
      heading: 'Completion checklist',
      items: [
        'VPC and six subnets span two AZs',
        'Internet Gateway attached and routed from public subnets',
        'NAT Gateway provides egress for private subnets',
        'Security groups scoped to least privilege',
        'VPC Flow Logs enabled',
        'Test instance reaches the internet from a public subnet only as intended',
      ],
    },
    { kind: 'deliverable', text: 'A resilient two-AZ VPC ready to host application and database tiers.' },
  ],
}

export const awsDays: Record<number, AwsDay> = {
  1: { day: 1, theoretical: day1Theory, practical: day1Practical },
  2: { day: 2, theoretical: day2Theory, practical: day2Practical },
  3: { day: 3, theoretical: day3Theory, practical: day3Practical },
}

// Days 4–15 are scaffolded from their phase focus until authored.
function upcoming(day: number, phaseFocus: string, type: AwsContentType): AwsDayContent {
  return {
    title: `${contentTypeLabels[type]} content — coming soon`,
    blocks: [
      { kind: 'lead', text: `Day ${day} covers "${phaseFocus}". Detailed ${contentTypeLabels[type].toLowerCase()} material for this day is being authored.` },
    ],
  }
}

for (const phase of awsPhases) {
  for (const day of phase.days) {
    if (!awsDays[day]) {
      awsDays[day] = {
        day,
        theoretical: upcoming(day, phase.focus, 'theoretical'),
        practical: upcoming(day, phase.focus, 'practical'),
      }
    }
  }
}
