import React from 'react'

function LoadingIndicator() {
  return (
    <div className="message-row message-row-assistant message-row-loading" aria-live="polite">
      {/* CollegeAssist Assistant Mark Badge */}
      <div className="assistant-mark-badge" aria-hidden="true">
        <svg
          width="17"
          height="17"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M22 10v6M2 10l10-5 10 5-10 5z" />
          <path d="M6 12v5c3 3 9 3 12 0v-5" />
        </svg>
      </div>

      <div className="assistant-bubble-container">
        <div className="assistant-bubble loading-bubble">
          <div className="loading-content">
            <div className="typing-dots-pill" aria-hidden="true">
              <span className="dot dot-1" />
              <span className="dot dot-2" />
              <span className="dot dot-3" />
            </div>
            <span className="loading-status-text">
              Looking through college documents...
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default LoadingIndicator
