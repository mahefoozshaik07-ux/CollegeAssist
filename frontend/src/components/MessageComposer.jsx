import React, { useRef, useEffect } from 'react'

function MessageComposer({
  value,
  onChange,
  onSend,
  isLoading = false,
  placeholder = 'Ask about rules, exams, fees, attendance, or next steps...'
}) {
  const textareaRef = useRef(null)

  // Auto-resize textarea according to scrollHeight (up to 160px)
  useEffect(() => {
    const textarea = textareaRef.current
    if (textarea) {
      textarea.style.height = 'auto'
      const newHeight = Math.min(Math.max(textarea.scrollHeight, 24), 160)
      textarea.style.height = `${newHeight}px`
    }
  }, [value])

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      if (value.trim() && !isLoading) {
        onSend()
      }
    }
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (value.trim() && !isLoading) {
      onSend()
    }
  }

  const isSendDisabled = !value.trim() || isLoading

  return (
    <div className="composer-dock-container">
      <div className="composer-wrapper">
        <form className="composer-card-form" onSubmit={handleSubmit}>
          <textarea
            ref={textareaRef}
            rows={1}
            className="composer-textarea"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={placeholder}
            aria-label="Ask CollegeAssist"
            disabled={isLoading}
          />

          <button
            type="submit"
            className="composer-send-btn"
            disabled={isSendDisabled}
            aria-label="Send query"
            title="Send query"
          >
            {/* Clear arrow icon */}
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <line x1="12" y1="19" x2="12" y2="5" />
              <polyline points="5 12 12 5 19 12" />
            </svg>
          </button>
        </form>

        <p className="composer-disclaimer-text">
          CollegeAssist provides accurate, empathetic guidance based on verified university documents.
        </p>
      </div>
    </div>
  )
}

export default MessageComposer
