// Single source of truth for the Educate Yourself lenses. The button labels, loading headings,
// error messages, and export-filename suffixes all live here so adding or renaming a variant is a
// one-place change (previously these were duplicated across EducationPage and TopicDetailsPage).
// The backend accepts the same variant keys (ai-service topic_agent + orchestrator AiQuestionService).

export type EducationVariant = 'guide' | 'notes' | 'design' | 'release'

export const educationVariants: Record<EducationVariant, {
  button: string
  buttonTitle: string
  primary: boolean
  loadingHeading: string
  errorText: string
  fileSuffix: string
}> = {
  guide: {
    button: 'Show Details',
    buttonTitle: 'A full zero-to-hero learning guide for this topic.',
    primary: true,
    loadingHeading: 'Building your zero-to-hero guide…',
    errorText: 'Unable to generate guide',
    fileSuffix: 'zero-to-hero',
  },
  notes: {
    button: 'Interview Notes',
    buttonTitle: 'Concise, interview-focused notes: key concepts, likely questions with answers, '
      + 'gotchas, and a quick summary.',
    primary: false,
    loadingHeading: 'Preparing your interview notes…',
    errorText: 'Unable to generate notes',
    fileSuffix: 'interview-notes',
  },
  design: {
    button: 'Design Perspective',
    buttonTitle: 'Where this topic fits in software design: layer, trade-offs, alternatives, '
      + 'interactions, pitfalls, and the design-interview angle.',
    primary: false,
    loadingHeading: 'Mapping the design perspective…',
    errorText: 'Unable to generate the design perspective',
    fileSuffix: 'design-perspective',
  },
  release: {
    button: "What's New",
    buttonTitle: "What's new in this version: headline features, API additions, deprecations, "
      + 'migration notes, and the interview angle. Best for JDK & Spring versions.',
    primary: false,
    loadingHeading: 'Gathering what’s new in this version…',
    errorText: "Unable to generate the what's-new summary",
    fileSuffix: 'whats-new',
  },
}

export const educationVariantKeys = Object.keys(educationVariants) as EducationVariant[]

export function toVariant(raw: string | null | undefined): EducationVariant {
  return raw === 'notes' || raw === 'design' || raw === 'release' ? raw : 'guide'
}
