import React from 'react'

function ErrorNotice({
  message = "We couldn't connect to CollegeAssist. Please try again.",
  onRetry,
  canRetry = false
}) {
  return (
    <div className="calm-error-container" role="alert">
      <div className="calm-error-card">
        <div className="calm-error-icon" aria-hidden="true">
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>

        <div className="calm-error-body">
          <p className="calm-error-message">{message}</p>
        </div>

        {canRetry && onRetry && (
          <button
            type="button"
            className="calm-error-retry-btn"
            onClick={onRetry}
            title="Retry last question"
          >
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <polyline points="1 4 1 10 7 10" />
              <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" />
            </svg>
            <span>Retry</span>
          </button>
        )}
      </div>
    </div>
  )
}

export default ErrorNotice
