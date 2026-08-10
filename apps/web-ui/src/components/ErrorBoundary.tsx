import { Component, type ErrorInfo, type ReactNode } from 'react'

// App-level safety net. React unmounts the whole tree if a render throws and is not
// caught by a boundary, leaving the user on a blank white screen. This boundary catches
// those errors, keeps the shell alive, and shows a recoverable message instead. Error
// boundaries must be class components — React has no hook equivalent.

type Props = {
  children: ReactNode
  /** Optional custom fallback; receives the error and a reset callback. */
  fallback?: (error: Error, reset: () => void) => ReactNode
}

type State = { error: Error | null }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Surface it for local debugging and any console-forwarding observability.
    // Not user data — just the render error and component stack.
    console.error('Unhandled UI error:', error, info.componentStack)
  }

  private reset = (): void => this.setState({ error: null })

  render(): ReactNode {
    const { error } = this.state
    if (!error) return this.props.children
    if (this.props.fallback) return this.props.fallback(error, this.reset)

    return (
      <div className="app-error" role="alert">
        <div className="app-error__card">
          <h1 className="app-error__title">Something went wrong</h1>
          <p className="app-error__body">
            The page hit an unexpected error. Your work isn’t lost — try again, and if it keeps
            happening, reload the app.
          </p>
          <div className="app-error__actions">
            <button type="button" className="app-error__btn" onClick={this.reset}>
              Try again
            </button>
            <button
              type="button"
              className="app-error__btn app-error__btn--ghost"
              onClick={() => window.location.reload()}
            >
              Reload
            </button>
          </div>
        </div>
      </div>
    )
  }
}
