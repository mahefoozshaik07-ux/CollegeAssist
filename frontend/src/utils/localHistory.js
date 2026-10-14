// Safe, profile-scoped browser localStorage utility for CollegeAssist Chat history.
// Ensures zero database is required to retain, restore, and clear conversations across browser sessions.

const STORAGE_PREFIX = 'collegeassist_chat_sessions_v4_'

/**
 * Returns safe storage key scoped to the user email profile.
 */
export function getStorageKey(email) {
  const safeEmail = (email && typeof email === 'string' && email.trim())
    ? email.trim().toLowerCase()
    : 'default_student'
  return `${STORAGE_PREFIX}${encodeURIComponent(safeEmail)}`
}

/**
 * Safely loads all conversations for the given user profile from browser localStorage.
 * Handles missing, corrupt, or inaccessible localStorage gracefully.
 */
export function loadLocalSessions(email) {
  try {
    if (typeof window === 'undefined' || !window.localStorage) {
      return []
    }
    const key = getStorageKey(email)
    const raw = window.localStorage.getItem(key)
    if (!raw) return []

    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []

    // Sanitize and validate session structure
    return parsed
      .filter((s) => s && typeof s === 'object' && s.id)
      .map((s) => ({
        id: String(s.id),
        title: typeof s.title === 'string' && s.title.trim() ? s.title.trim() : 'Campus Inquiry',
        createdAt: Number(s.createdAt) || Date.now(),
        updatedAt: Number(s.updatedAt) || Date.now(),
        studentEmail: email,
        messages: Array.isArray(s.messages)
          ? s.messages.map((m) => ({
              id: m.id ? String(m.id) : `msg-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
              sender: m.sender === 'user' ? 'user' : 'assistant',
              text: typeof m.text === 'string' ? m.text : (m.content || ''),
              timestamp: m.timestamp || '',
              status: m.status || 'CONFIGURED',
              sources: Array.isArray(m.sources) ? m.sources : []
            }))
          : []
      }))
  } catch (err) {
    console.warn('CollegeAssist: Unable to read local chat history from localStorage:', err)
    return []
  }
}

/**
 * Safely persists sessions array for a student email.
 */
export function saveLocalSessions(email, sessions) {
  try {
    if (typeof window === 'undefined' || !window.localStorage) {
      return false
    }
    const key = getStorageKey(email)
    if (!Array.isArray(sessions) || sessions.length === 0) {
      window.localStorage.removeItem(key)
      return true
    }

    // Limit to 50 most recent sessions to avoid localStorage quota limits
    const trimmed = sessions.slice(0, 50)
    window.localStorage.setItem(key, JSON.stringify(trimmed))
    return true
  } catch (err) {
    console.warn('CollegeAssist: Unable to save local chat history to localStorage:', err)
    return false
  }
}

/**
 * Upserts a single conversation in local storage.
 */
export function upsertLocalConversation(email, conversationId, messages, optionalTitle = null) {
  if (!conversationId || !Array.isArray(messages) || messages.length === 0) {
    return loadLocalSessions(email)
  }

  const sessions = loadLocalSessions(email)
  const existingIndex = sessions.findIndex((s) => s.id === conversationId)

  let title = optionalTitle
  if (!title) {
    const firstUserMsg = messages.find((m) => m.sender === 'user')
    if (firstUserMsg && firstUserMsg.text) {
      title = firstUserMsg.text.length > 45 ? `${firstUserMsg.text.slice(0, 42).trim()}...` : firstUserMsg.text.trim()
    } else if (existingIndex >= 0) {
      title = sessions[existingIndex].title
    } else {
      title = 'Campus Inquiry'
    }
  }

  const now = Date.now()
  const updatedRecord = {
    id: conversationId,
    title,
    createdAt: existingIndex >= 0 ? sessions[existingIndex].createdAt : now,
    updatedAt: now,
    studentEmail: email,
    messages
  }

  if (existingIndex >= 0) {
    sessions[existingIndex] = updatedRecord
  } else {
    sessions.unshift(updatedRecord)
  }

  // Sort newest first
  sessions.sort((a, b) => b.updatedAt - a.updatedAt)
  saveLocalSessions(email, sessions)
  return sessions
}

/**
 * Deletes a single conversation from local storage.
 */
export function deleteLocalConversation(email, conversationId) {
  if (!conversationId) return loadLocalSessions(email)
  const sessions = loadLocalSessions(email).filter((s) => s.id !== conversationId)
  saveLocalSessions(email, sessions)
  return sessions
}

/**
 * Clears all chat history for the user profile from local storage.
 */
export function clearAllLocalSessions(email) {
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      const key = getStorageKey(email)
      window.localStorage.removeItem(key)
    }
  } catch (err) {
    console.warn('CollegeAssist: Failed to clear localStorage:', err)
  }
}
