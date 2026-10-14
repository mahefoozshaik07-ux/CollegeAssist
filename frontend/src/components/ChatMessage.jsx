import React from 'react'
import SourceCitations from './SourceCitations'

/**
 * Parses inline formatting: **bold**, `code`, and [markdown links](url)
 */
function renderInlineText(text) {
  if (!text) return null
  const tokens = text.split(/(\*\*.*?\*\*|`.*?`|\[.*?\]\(https?:\/\/[^\s)]+\))/g)

  return tokens.map((token, index) => {
    if (!token) return null

    // Bold text (may wrap a link e.g. **[VTOP Student Portal](https://vtop.vitap.ac.in/)**)
    if (token.startsWith('**') && token.endsWith('**') && token.length >= 4) {
      const inner = token.slice(2, -2)
      const linkMatch = inner.match(/^\[(.*?)\]\((https?:\/\/[^\s)]+)\)$/)
      if (linkMatch) {
        return (
          <strong key={index} className="text-bold">
            <a
              href={linkMatch[2]}
              target="_blank"
              rel="noopener noreferrer"
              className="message-link"
            >
              {linkMatch[1]}
            </a>
          </strong>
        )
      }
      return <strong key={index} className="text-bold">{renderInlineText(inner)}</strong>
    }

    // Inline Code
    if (token.startsWith('`') && token.endsWith('`') && token.length >= 2) {
      return <code key={index} className="text-inline-code">{token.slice(1, -1)}</code>
    }

    // Markdown link: [label](url)
    const linkMatch = token.match(/^\[(.*?)\]\((https?:\/\/[^\s)]+)\)$/)
    if (linkMatch) {
      return (
        <a
          key={index}
          href={linkMatch[2]}
          target="_blank"
          rel="noopener noreferrer"
          className="message-link"
        >
          {linkMatch[1]}
        </a>
      )
    }

    return token
  })
}

/**
 * Formats multi-line structured assistant content into headings, lists, and paragraphs
 */
function renderStructuredContent(content) {
  if (!content) return null

  const lines = content.split('\n')
  const blocks = []
  let currentListItems = []

  const flushList = () => {
    if (currentListItems.length > 0) {
      blocks.push(
        <ul key={`ul-${blocks.length}`} className="message-list">
          {currentListItems.map((item, idx) => (
            <li key={idx} className="message-list-item">{renderInlineText(item)}</li>
          ))}
        </ul>
      )
      currentListItems = []
    }
  }

  lines.forEach((line, lineIndex) => {
    const trimmed = line.trim()

    if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      currentListItems.push(trimmed.slice(2))
    } else if (/^\d+\.\s/.test(trimmed)) {
      flushList()
      const match = trimmed.match(/^(\d+\.)\s*(.*)$/)
      blocks.push(
        <div key={`ol-${lineIndex}`} className="message-numbered-row">
          <span className="numbered-index">{match ? match[1] : '•'}</span>
          <span className="numbered-text">{renderInlineText(match ? match[2] : trimmed)}</span>
        </div>
      )
    } else if (trimmed.startsWith('#### ')) {
      flushList()
      blocks.push(
        <h5 key={`h5-${lineIndex}`} className="message-heading-sub">
          {renderInlineText(trimmed.slice(5))}
        </h5>
      )
    } else if (trimmed.startsWith('### ')) {
      flushList()
      blocks.push(
        <h4 key={`h4-${lineIndex}`} className="message-heading">
          {renderInlineText(trimmed.slice(4))}
        </h4>
      )
    } else if (trimmed === '') {
      flushList()
    } else {
      flushList()
      blocks.push(
        <p key={`p-${lineIndex}`} className="message-paragraph">
          {renderInlineText(trimmed)}
        </p>
      )
    }
  })

  flushList()
  return blocks
}

function ChatMessage({ message }) {
  const isUser = message.sender === 'user'

  if (isUser) {
    return (
      <div className="message-row message-row-user">
        <div className="user-bubble">
          <div className="user-bubble-text">{message.text}</div>
          {message.timestamp && (
            <span className="message-time-meta" aria-label={`Sent at ${message.timestamp}`}>
              {message.timestamp}
            </span>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="message-row message-row-assistant">
      {/* CollegeAssist Assistant Mark Badge */}
      <div className="assistant-mark-badge" aria-hidden="true" title="CollegeAssist AI">
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
        <div className="assistant-bubble">
          <div className="assistant-bubble-content">
            {renderStructuredContent(message.text)}
          </div>

          {/* Genuine Verified Source Citations */}
          {message.sources && message.sources.length > 0 && (
            <SourceCitations sources={message.sources} />
          )}

          {message.timestamp && (
            <div className="assistant-meta-row">
              <span className="message-time-meta">{message.timestamp}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default ChatMessage
