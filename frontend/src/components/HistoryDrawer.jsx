import React, { useState, useEffect } from 'react'
import { fetchConversations, deleteConversation } from '../api/chatApi'

function formatTimestamp(isoString) {
  if (!isoString) return ''
  try {
    const date = new Date(isoString)
    if (isNaN(date.getTime())) return ''

    const now = new Date()
    const diffMs = now - date
    const diffMins = Math.floor(diffMs / 60000)
    const diffHours = Math.floor(diffMs / 3600000)
    const diffDays = Math.floor(diffMs / 86400000)

    if (diffMins < 1) return 'Just now'
    if (diffMins < 60) return `${diffMins}m ago`
    if (diffHours < 24 && date.getDate() === now.getDate()) {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
    if (diffDays === 1) return 'Yesterday'
    if (diffDays < 7) return `${diffDays}d ago`
    return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
  } catch {
    return ''
  }
}

function HistoryDrawer({
  isOpen,
  onClose,
  userEmail,
  currentConversationId,
  onSelectConversation,
  onNewChat
}) {
  const [conversations, setConversations] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState(null)
  const [deletingId, setDeletingId] = useState(null)
  const [isDeleting, setIsDeleting] = useState(false)

  const reloadConversations = () => {
    setIsLoading(true)
    setErrorMessage(null)
    fetchConversations(userEmail)
      .then((data) => {
        setConversations(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        setErrorMessage(err.message || 'Unable to load previous conversations.')
      })
      .finally(() => {
        setIsLoading(false)
      })
  }

  useEffect(() => {
    let ignore = false
    fetchConversations(userEmail)
      .then((data) => {
        if (!ignore) {
          setConversations(Array.isArray(data) ? data : [])
          setIsLoading(false)
        }
      })
      .catch((err) => {
        if (!ignore) {
          setErrorMessage(err.message || 'Unable to load previous conversations.')
          setIsLoading(false)
        }
      })

    return () => {
      ignore = true
    }
  }, [userEmail])

  // Handle ESC key press to close drawer
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        if (deletingId) {
          setDeletingId(null)
        } else {
          onClose()
        }
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose, deletingId])

  const handleStartNewChat = () => {
    onNewChat()
    onClose()
  }

  const handleSelect = (convId) => {
    onSelectConversation(convId)
    onClose()
  }

  const handleConfirmDelete = async (e, convId) => {
    e.stopPropagation()
    setIsDeleting(true)
    try {
      await deleteConversation(convId, userEmail)
      setConversations((prev) => prev.filter((c) => c.id !== convId))
      setDeletingId(null)
      // If deleted conversation was active, notify parent to reset to new chat
      if (convId === currentConversationId) {
        onNewChat()
      }
    } catch (err) {
      setErrorMessage(`Could not delete conversation: ${err.message}`)
    } finally {
      setIsDeleting(false)
    }
  }

  if (!isOpen) return null

  return (
    <div className="history-drawer-backdrop" onClick={onClose}>
      <aside
        className="history-drawer-panel"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Chat History"
      >
        {/* Drawer Header */}
        <div className="history-drawer-header">
          <div className="history-drawer-title-group">
            <div className="history-icon-badge" aria-hidden="true">
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
                <polyline points="12 6 12 12 16 14" />
              </svg>
            </div>
            <div>
              <h2 className="history-drawer-heading">Chat History</h2>
              <p className="history-drawer-subheading">Your campus conversations</p>
            </div>
          </div>

          <button
            type="button"
            className="history-drawer-close-btn"
            onClick={onClose}
            aria-label="Close chat history"
            title="Close"
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        {/* Action: Start New Chat */}
        <div className="history-drawer-actions">
          <button
            type="button"
            className="history-new-chat-action-btn"
            onClick={handleStartNewChat}
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            <span>Start new conversation</span>
          </button>
        </div>

        {/* Content Area */}
        <div className="history-drawer-content">
          {/* Loading State */}
          {isLoading && (
            <div className="history-state-container">
              <div className="history-loading-spinner" aria-label="Loading conversations" />
              <p className="history-state-text">Loading previous conversations...</p>
            </div>
          )}

          {/* Error State */}
          {!isLoading && errorMessage && (
            <div className="history-state-container history-error-box">
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="history-error-icon"
                aria-hidden="true"
              >
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              <p className="history-state-text">{errorMessage}</p>
              <button
                type="button"
                className="history-retry-btn"
                onClick={reloadConversations}
              >
                Retry
              </button>
            </div>
          )}

          {/* Empty State */}
          {!isLoading && !errorMessage && conversations.length === 0 && (
            <div className="history-state-container">
              <div className="history-empty-icon" aria-hidden="true">
                <svg
                  width="36"
                  height="36"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
              </div>
              <h3 className="history-empty-title">No conversations yet</h3>
              <p className="history-state-text">
                Your past campus queries and policy answers will appear here.
              </p>
            </div>
          )}

          {/* Conversation List */}
          {!isLoading && !errorMessage && conversations.length > 0 && (
            <ul className="history-conversation-list" role="list">
              {conversations.map((conv) => {
                const isActive = conv.id === currentConversationId
                const isPendingDelete = deletingId === conv.id

                return (
                  <li key={conv.id} className="history-list-item">
                    <div
                      className={`history-item-card ${isActive ? 'history-item-active' : ''}`}
                      onClick={() => handleSelect(conv.id)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault()
                          handleSelect(conv.id)
                        }
                      }}
                      aria-label={`Open conversation: ${conv.title || 'Campus Query'}`}
                    >
                      <div className="history-item-main">
                        <div className="history-item-top">
                          <span className="history-item-title" title={conv.title}>
                            {conv.title || 'Campus Query'}
                          </span>
                          {isActive && (
                            <span className="history-active-tag">Active</span>
                          )}
                        </div>

                        <div className="history-item-meta">
                          <span className="history-meta-date">
                            {formatTimestamp(conv.updatedAt || conv.createdAt)}
                          </span>
                          {conv.messageCount > 0 && (
                            <span className="history-meta-count">
                              {conv.messageCount} {conv.messageCount === 1 ? 'msg' : 'msgs'}
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Delete Action with Safe Confirmation */}
                      <div className="history-item-actions" onClick={(e) => e.stopPropagation()}>
                        {isPendingDelete ? (
                          <div className="history-delete-confirm-box">
                            <span className="history-confirm-prompt">Delete?</span>
                            <button
                              type="button"
                              className="history-confirm-btn history-confirm-yes"
                              onClick={(e) => handleConfirmDelete(e, conv.id)}
                              disabled={isDeleting}
                              title="Confirm delete"
                              aria-label="Confirm delete conversation"
                            >
                              Yes
                            </button>
                            <button
                              type="button"
                              className="history-confirm-btn history-confirm-no"
                              onClick={(e) => {
                                e.stopPropagation()
                                setDeletingId(null)
                              }}
                              disabled={isDeleting}
                              title="Cancel"
                              aria-label="Cancel deletion"
                            >
                              No
                            </button>
                          </div>
                        ) : (
                          <button
                            type="button"
                            className="history-delete-btn"
                            onClick={(e) => {
                              e.stopPropagation()
                              setDeletingId(conv.id)
                            }}
                            title="Delete conversation"
                            aria-label={`Delete conversation ${conv.title}`}
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
                              aria-hidden="true"
                            >
                              <polyline points="3 6 5 6 21 6" />
                              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                            </svg>
                          </button>
                        )}
                      </div>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </div>

        {/* Drawer Footer with Student Info */}
        <div className="history-drawer-footer">
          <span className="history-footer-email">{userEmail}</span>
        </div>
      </aside>
    </div>
  )
}

export default HistoryDrawer
