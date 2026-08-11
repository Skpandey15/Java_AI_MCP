// Course content for "AWS Zero to Production Hero — 15-Day Program".
//
// The page presents four dependent dropdowns: Phase -> Day -> Content Type -> Topic.
// Selecting a topic opens an AI-generated details page for that topic. So the data model here
// is the list of TOPICS per day and content type (not the prose itself — that is produced by AI).
// Phase 1 (Days 1-3) is fully specified; Phases 2-5 (Days 4-15) are marked upcoming.

export type AwsContentType = 'theoretical' | 'practical'

// A topic is either a single selectable entry or a labelled group of entries (rendered as an
// <optgroup>), e.g. "Five expensive-resource examples" with its individual resources.
export type AwsTopicEntry = string | { group: string; items: string[] }

export interface AwsDayContent {
  title: string
  topics: AwsTopicEntry[]
}

export interface AwsDay {
  day: number
  theoretical: AwsDayContent
  practical: AwsDayContent
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

export const awsDays: Record<number, AwsDay> = {
  1: {
    day: 1,
    theoretical: {
      title: 'AWS account, global infrastructure and cost control',
      topics: [
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
        {
          group: 'Five expensive-resource examples',
          items: [
            'NAT Gateway',
            'Unused Elastic IP',
            'Large EC2 instance',
            'EKS control plane',
            'RDS Multi-AZ database',
          ],
        },
      ],
    },
    practical: {
      title: 'Secure and prepare an AWS learning account',
      topics: [
        'Secure the root account with MFA',
        'Verify that root access keys do not exist',
        'Create an administrative learning identity',
        'Configure the AWS CLI',
        'Select a primary Region',
        'Create a recurring monthly budget',
        'Configure budget email alerts',
        'Enable Cost Explorer',
        'Configure Cost Anomaly Detection',
        'Establish a resource-tagging standard',
      ],
    },
  },
  2: {
    day: 2,
    theoretical: {
      title: 'IAM and AWS security fundamentals',
      topics: [
        'Authentication versus authorization',
        'IAM users',
        'IAM groups',
        'IAM roles',
        'IAM policies',
        'Identity-based policies',
        'Resource-based policies',
        'Policy JSON structure',
        'IAM policy evaluation',
        'Explicit Allow',
        'Explicit Deny',
        'Least privilege',
        'Temporary credentials',
        'AWS STS',
        'AssumeRole',
        'Workload identities',
        'AWS KMS introduction',
        'CloudTrail introduction',
        'IAM Access Analyzer',
        'Root-user security practices',
      ],
    },
    practical: {
      title: 'Implement and validate least-privilege access',
      topics: [
        'Create a restricted IAM role',
        'Create a least-privilege IAM policy',
        'Attach the policy to the role',
        'Assume the role through AWS CLI',
        'Perform an allowed operation',
        'Perform a prohibited operation',
        'Verify the expected AccessDenied result',
        'Inspect the associated CloudTrail event',
        'Run IAM Access Analyzer',
        'Remove unnecessary permissions',
      ],
    },
  },
  3: {
    day: 3,
    theoretical: {
      title: 'VPC and AWS networking',
      topics: [
        'VPC fundamentals',
        'IPv4 and CIDR ranges',
        'Public subnet',
        'Private subnet',
        'Route tables',
        'Internet Gateway',
        'NAT Gateway',
        'Elastic IP',
        'Security groups',
        'Network ACLs',
        'Security group versus NACL',
        'DNS and DHCP',
        'Route 53 Resolver',
        'VPC endpoints',
        'Gateway and interface endpoints',
        'VPC Flow Logs',
        'Multi-AZ networking',
        'Public and private workload placement',
        'Network troubleshooting',
        'Production VPC design',
      ],
    },
    practical: {
      title: 'Build a production-style two-AZ VPC',
      topics: [
        'Create a VPC',
        'Create two public subnets',
        'Create two private application subnets',
        'Create two private database subnets',
        'Span two Availability Zones',
        'Create an Internet Gateway',
        'Configure public and private route tables',
        'Create a NAT Gateway',
        'Configure security groups',
        'Enable VPC Flow Logs',
        'Launch a temporary EC2 test instance',
      ],
    },
  },
}

// Days 4-15 are scaffolded from their phase focus until their curriculum is authored.
for (const phase of awsPhases) {
  for (const day of phase.days) {
    if (!awsDays[day]) {
      const title = `${phase.focus} — coming soon`
      awsDays[day] = {
        day,
        theoretical: { title, topics: [] },
        practical: { title, topics: [] },
      }
    }
  }
}

// Flatten a topic list (expanding groups) — used to know whether a day has any selectable topics.
export function flattenTopics(entries: AwsTopicEntry[]): string[] {
  return entries.flatMap((e) => (typeof e === 'string' ? [e] : e.items))
}
