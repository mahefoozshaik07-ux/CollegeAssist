import React, { useState, useEffect, useRef } from 'react'
import Header from './components/Header'
import ChatBox from './components/ChatBox'
import HistoryDrawer from './components/HistoryDrawer'
import LogoutModal from './components/LogoutModal'
import { getChatStatus, fetchConversationDetail } from './api/chatApi'
import {
  loadLocalSessions,
  upsertLocalConversation,
  clearAllLocalSessions
} from './utils/localHistory'
import './App.css'

const USER_EMAIL_KEY = 'collegeassist_student_email_v3'
const THEME_KEY = 'collegeassist_theme'
const DEFAULT_STUDENT_EMAIL = 'alex.student@university.edu'

function App() {
  // Theme state: light or dark
  const [theme, setTheme] = useState(() => {
    try {
      const saved = localStorage.getItem(THEME_KEY)
      if (saved === 'dark' || saved === 'light') return saved
    } catch {
      // Fallback
    }
    return 'light'
  })

  // Apply theme to document root
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    try {
      localStorage.setItem(THEME_KEY, theme)
    } catch {
      // Ignore
    }
  }, [theme])

  const handleToggleTheme = () => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'))
  }

  // Authenticated student email
  const [userEmail, setUserEmail] = useState(() => {
    try {
      const saved = localStorage.getItem(USER_EMAIL_KEY)
      if (saved && saved.trim()) return saved.trim()
    } catch {
      // Fallback
    }
    return DEFAULT_STUDENT_EMAIL
  })

  const [logoutModalOpen, setLogoutModalOpen] = useState(false)
  const [isHistoryOpen, setIsHistoryOpen] = useState(false)
  const [backendStatus, setBackendStatus] = useState(null)

  // Active conversation ID restored from local history
  const [currentSessionId, setCurrentSessionId] = useState(() => {
    const sessions = loadLocalSessions(DEFAULT_STUDENT_EMAIL)
    return sessions.length > 0 ? sessions[0].id : null
  })

  // Messages in active conversation restored from local history
  const [messages, setMessages] = useState(() => {
    const sessions = loadLocalSessions(DEFAULT_STUDENT_EMAIL)
    return sessions.length > 0 ? (sessions[0].messages || []) : []
  })

  // Check backend integration status on initial load
  useEffect(() => {
    let isMounted = true
    const checkStatus = async () => {
      try {
        const status = await getChatStatus()
        if (isMounted) {
          setBackendStatus(status)
        }
      } catch {
        // Fallback when backend is not ready
      }
    }
    checkStatus()
    return () => {
      isMounted = false
    }
  }, [])

  // Persist student email profile
  useEffect(() => {
    try {
      localStorage.setItem(USER_EMAIL_KEY, userEmail)
    } catch {
      // Ignore
    }
  }, [userEmail])

  // Automatically sync messages to browser localStorage on every turn
  useEffect(() => {
    if (currentSessionId && Array.isArray(messages) && messages.length > 0) {
      upsertLocalConversation(userEmail, currentSessionId, messages)
    }
  }, [messages, currentSessionId, userEmail])

  // Reset to fresh welcome screen
  const handleNewChat = () => {
    setCurrentSessionId(null)
    setMessages([])
  }

  // Update messages in the current conversation
  const handleUpdateMessages = (newMessages) => {
    setMessages(newMessages)
  }

  // Handle first message sent in a new conversation
  const handleNewChatStarted = () => {
    if (!currentSessionId) {
      const newId = `session-${Date.now()}`
      setCurrentSessionId(newId)
    }
  }

  // Load a conversation from history (checks local storage first, then backend)
  const handleSelectConversation = async (selectedId) => {
    // 1. Instant load from browser local history
    const localSessions = loadLocalSessions(userEmail)
    const localMatch = localSessions.find((s) => s.id === selectedId)
    if (localMatch && Array.isArray(localMatch.messages) && localMatch.messages.length > 0) {
      setCurrentSessionId(selectedId)
      setMessages(localMatch.messages)
      return
    }

    // 2. Fallback to backend API if available
    try {
      const detail = await fetchConversationDetail(selectedId, userEmail)
      if (detail && Array.isArray(detail.messages)) {
        const formatted = detail.messages.map((m) => ({
          id: m.id ? String(m.id) : `msg-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          sender: m.sender || 'assistant',
          text: m.content || m.text || '',
          timestamp: m.timestamp || '',
          status: m.status || 'CONFIGURED',
          sources: m.sources || []
        }))
        setCurrentSessionId(selectedId)
        setMessages(formatted)
        upsertLocalConversation(userEmail, selectedId, formatted, detail.title)
      }
    } catch (err) {
      console.warn('Could not load conversation from backend, using local state:', err)
    }
  }

  // Clear all local chat history
  const handleClearAllHistory = () => {
    clearAllLocalSessions(userEmail)
    setCurrentSessionId(null)
    setMessages([])
  }

  // Confirm logout: clear active messages and reset
  const handleConfirmLogout = () => {
    setLogoutModalOpen(false)
    setMessages([])
    setCurrentSessionId(null)
    setUserEmail(DEFAULT_STUDENT_EMAIL)
    try {
      localStorage.removeItem(USER_EMAIL_KEY)
    } catch {
      // Ignore
    }
  }

  return (
    <div className="college-app-layout">
      {/* Top Compact Navigation */}
      <Header
        userEmail={userEmail}
        onLogout={() => setLogoutModalOpen(true)}
        onNewChat={handleNewChat}
        onOpenHistory={() => setIsHistoryOpen(true)}
        theme={theme}
        onToggleTheme={handleToggleTheme}
        hasActiveChat={messages.length > 0}
        backendStatus={backendStatus}
      />

      {/* Main Academic Chat Experience */}
      <main className="college-main-content">
        <ChatBox
          messages={messages}
          onUpdateMessages={handleUpdateMessages}
          conversationId={currentSessionId || 'default-session'}
          onNewChatStarted={handleNewChatStarted}
          userEmail={userEmail}
        />
      </main>

      {/* Chat History Panel */}
      {isHistoryOpen && (
        <HistoryDrawer
          isOpen={isHistoryOpen}
          onClose={() => setIsHistoryOpen(false)}
          userEmail={userEmail}
          currentConversationId={currentSessionId}
          onSelectConversation={handleSelectConversation}
          onNewChat={handleNewChat}
          onClearAllHistory={handleClearAllHistory}
        />
      )}

      {/* Subtle Logout Confirmation Modal */}
      <LogoutModal
        isOpen={logoutModalOpen}
        onClose={() => setLogoutModalOpen(false)}
        onConfirm={handleConfirmLogout}
        userEmail={userEmail}
      />
    </div>
  )
}

export default App
