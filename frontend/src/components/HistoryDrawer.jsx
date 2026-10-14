import React, { useState, useEffect } from 'react'
import { fetchConversations, deleteConversation } from '../api/chatApi'
import { loadLocalSessions, deleteLocalConversation } from '../utils/localHistory'

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
  onNewChat,
  onClearAllHistory
}) {
  const [conversations, setConversations] = useState(() => {
    return loadLocalSessions(userEmail).map((s) => ({
      id: s.id,
      title: s.title,
      studentEmail: s.studentEmail,
      createdAt: s.createdAt,
      updatedAt: s.updatedAt,
      messageCount: s.messages ? s.messages.length : 0
    }))
  })
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState(null)
  const [deletingId, setDeletingId] = useState(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [showClearConfirm, setShowClearConfirm] = useState(false)

  // Merge and sync conversations from both local storage and backend
  const reloadConversations = () => {
    const local = loadLocalSessions(userEmail).map((s) => ({
      id: s.id,
      title: s.title,
      studentEmail: s.studentEmail,
      createdAt: s.createdAt,
      updatedAt: s.updatedAt,
      messageCount: s.messages ? s.messages.length : 0
    }))
    setConversations(local)

    setIsLoading(true)
    setErrorMessage(null)
    fetchConversations(userEmail)
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          // Merge unique by ID, backend takes precedence for metadata
          const map = new Map()
          local.forEach((c) => map.set(c.id, c))
          data.forEach((c) => map.set(c.id, c))
          const combined = Array.from(map.values())
          combined.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
          setConversations(combined)
        }
      })
      .catch((err) => {
        // If backend is offline or in database-free mode, local storage is fully functional
        console.info('CollegeAssist: Running in database-free / local history mode:', err.message)
      })
      .finally(() => {
        setIsLoading(false)
      })
  }

  useEffect(() => {
    if (isOpen) {
      reloadConversations()
    }
  }, [isOpen, userEmail])

  // Handle ESC key press to close drawer
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        if (showClearConfirm) {
          setShowClearConfirm(false)
        } else if (deletingId) {
          setDeletingId(null)
        } else {
          onClose()
        }
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose, deletingId, showClearConfirm])

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
      // 1. Delete locally from browser storage
      deleteLocalConversation(userEmail, convId)
      setConversations((prev) => prev.filter((c) => c.id !== convId))
      setDeletingId(null)

      // 2. Also attempt backend deletion if backend store is active
      try {
        await deleteConversation(convId, userEmail)
      } catch {
        // Safe to ignore if backend is in pure local mode
      }

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

  const handleConfirmClearAll = () => {
    if (onClearAllHistory) {
      onClearAllHistory()
    }
    setConversations([])
    setShowClearConfirm(false)
    onClose()
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
              <p className="history-drawer-subheading">Browser-local sessions</p>
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

        {/* Local Storage Indicator Banner */}
        <div style={{
          padding: '8px 16px',
          background: 'var(--color-surface-hover, rgba(0,0,0,0.03))',
          borderBottom: '1px solid var(--color-border, #e5e7eb)',
          fontSize: '0.75rem',
          color: 'var(--color-text-secondary, #6b7280)',
          display: 'flex',
          alignItems: 'center',
          gap: '6px'
        }}>
          <span style={{ fontSize: '0.9rem' }}>🔒</span>
          <span>History is saved locally on this browser profile (no database required).</span>
        </div>

        {/* Action: Start New Chat & Clear */}
        <div className="history-drawer-actions" style={{ display: 'flex', gap: '8px', padding: '12px 16px' }}>
          <button
            type="button"
            className="history-new-chat-action-btn"
            onClick={handleStartNewChat}
            style={{ flex: 1 }}
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
            <span>New Chat</span>
          </button>

          {conversations.length > 0 && (
            <button
              type="button"
              onClick={() => setShowClearConfirm(true)}
              style={{
                background: 'transparent',
                border: '1px solid var(--color-border, #e5e7eb)',
                color: 'var(--color-text-secondary, #6b7280)',
                borderRadius: '8px',
                padding: '6px 12px',
                fontSize: '0.8rem',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '4px'
              }}
              title="Clear all local conversations"
            >
              <span>Clear</span>
            </button>
          )}
        </div>

        {/* Clear All Confirmation Modal/Banner */}
        {showClearConfirm && (
          <div style={{
            margin: '0 16px 12px 16px',
            padding: '12px',
            borderRadius: '8px',
            background: 'var(--color-danger-subtle, #fee2e2)',
            border: '1px solid var(--color-danger, #ef4444)',
            fontSize: '0.8rem'
          }}>
            <p style={{ margin: '0 0 8px 0', color: '#991b1b', fontWeight: 600 }}>
              Clear all saved chats for this profile?
            </p>
            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
              <button
                type="button"
                onClick={() => setShowClearConfirm(false)}
                style={{
                  background: '#ffffff',
                  border: '1px solid #d1d5db',
                  padding: '4px 10px',
                  borderRadius: '6px',
                  fontSize: '0.75rem',
                  cursor: 'pointer'
                }}
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmClearAll}
                style={{
                  background: '#ef4444',
                  color: '#ffffff',
                  border: 'none',
                  padding: '4px 10px',
                  borderRadius: '6px',
                  fontSize: '0.75rem',
                  cursor: 'pointer',
                  fontWeight: 600
                }}
              >
                Yes, Clear All
              </button>
            </div>
          </div>
        )}

        {/* Content Area */}
        <div className="history-drawer-content">
          {/* Loading State */}
          {isLoading && conversations.length === 0 && (
            <div className="history-state-container">
              <div className="history-loading-spinner" aria-label="Loading conversations" />
              <p className="history-state-text">Loading conversations...</p>
            </div>
          )}

          {/* Empty State */}
          {!isLoading && conversations.length === 0 && (
            <div className="history-state-container">
              <div className="history-empty-icon" aria-hidden="true">
                <svg
                  width="36"
                  height="36"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
              </div>
              <h3 className="history-empty-title">No conversations yet</h3>
              <p className="history-empty-desc">
                Ask a question about campus policies, fees, or academics to begin your first thread.
              </p>
            </div>
          )}

          {/* Conversation List */}
          {conversations.length > 0 && (
            <ul className="history-list" role="list">
              {conversations.map((conv) => {
                const isActive = conv.id === currentConversationId
                const isItemDeleting = deletingId === conv.id

                return (
                  <li
                    key={conv.id}
                    className={`history-list-item ${isActive ? 'active' : ''}`}
                    onClick={() => handleSelect(conv.id)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        handleSelect(conv.id)
                      }
                    }}
                    aria-current={isActive ? 'true' : undefined}
                  >
                    <div className="history-item-content">
                      <div className="history-item-header">
                        <span className="history-item-title" title={conv.title}>
                          {conv.title || 'Campus Inquiry'}
                        </span>
                        <span className="history-item-time">
                          {formatTimestamp(conv.updatedAt)}
                        </span>
                      </div>

                      <div className="history-item-footer">
                        <span className="history-item-badge">
                          {conv.messageCount ? `${conv.messageCount} msg` : 'Active'}
                        </span>

                        {isItemDeleting ? (
                          <div
                            className="history-delete-confirm-group"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <span className="history-delete-confirm-label">Delete?</span>
                            <button
                              type="button"
                              className="history-delete-confirm-btn"
                              onClick={(e) => handleConfirmDelete(e, conv.id)}
                              disabled={isDeleting}
                              aria-label="Confirm delete"
                            >
                              Yes
                            </button>
                            <button
                              type="button"
                              className="history-delete-cancel-btn"
                              onClick={(e) => {
                                e.stopPropagation()
                                setDeletingId(null)
                              }}
                              disabled={isDeleting}
                              aria-label="Cancel delete"
                            >
                              No
                            </button>
                          </div>
                        ) : (
                          <button
                            type="button"
                            className="history-item-delete-btn"
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
