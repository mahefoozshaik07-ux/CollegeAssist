// Reusable API client helper for CollegeAssist AI Chatbox
// Connects to Spring Boot backend at VITE_API_BASE_URL or http://localhost:8080

import { API_BASE_URL } from './studentApi'

/**
 * Sends a student query to the Spring Boot /api/chat endpoint.
 *
 * @param {string} message - The student's question or message.
 * @param {string} [conversationId] - Optional session ID to correlate messages.
 * @param {string} [studentEmail] - Student email to associate ownership.
 * @returns {Promise<Object>} Formatted response object containing content, sources, and status.
 */
export async function sendChatMessage(message, conversationId = 'default-session', studentEmail = null) {
  if (!message || !message.trim()) {
    throw new Error('Message cannot be empty.')
  }

  try {
    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    }
    if (studentEmail) {
      headers['X-Student-Email'] = studentEmail
    }

    const response = await fetch(`${API_BASE_URL}/api/chat`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        message: message.trim(),
        conversationId,
        studentEmail: studentEmail || undefined,
        email: studentEmail || undefined
      })
    })

    if (!response.ok) {
      let errorMessage = `Server error (${response.status})`
      try {
        const errorJson = await response.json()
        errorMessage = errorJson.message || errorJson.error || JSON.stringify(errorJson)
      } catch {
        // Fall back to status text
        errorMessage = response.statusText || errorMessage
      }
      throw new Error(errorMessage)
    }

    return await response.json()
  } catch (err) {
    // Distinguish browser network / CORS failures from application HTTP responses
    if (err.name === 'TypeError') {
      const currentOrigin = typeof window !== 'undefined' ? window.location.origin : 'current origin'
      throw new Error(
        `Unable to reach backend service at ${API_BASE_URL} from ${currentOrigin}. ` +
        `Please ensure the Spring Boot server is running on port 8080 and that CORS allows origin '${currentOrigin}'.`
      )
    }
    throw err
  }
}

/**
 * Fetches all past conversations for the student.
 * Endpoint: GET /api/chat/conversations
 *
 * @param {string} studentEmail - Student's email for ownership.
 * @returns {Promise<Array>} List of conversation summaries.
 */
export async function fetchConversations(studentEmail) {
  try {
    const query = studentEmail ? `?studentEmail=${encodeURIComponent(studentEmail)}` : ''
    const headers = {
      'Accept': 'application/json'
    }
    if (studentEmail) {
      headers['X-Student-Email'] = studentEmail
    }

    const response = await fetch(`${API_BASE_URL}/api/chat/conversations${query}`, {
      method: 'GET',
      headers
    })

    if (!response.ok) {
      throw new Error(`Failed to load chat history (${response.status})`)
    }

    return await response.json()
  } catch (err) {
    console.error('Error fetching conversations:', err)
    throw err
  }
}

/**
 * Fetches full details and message transcript for a specific conversation.
 * Endpoint: GET /api/chat/conversations/{id}
 *
 * @param {string} conversationId - The conversation ID.
 * @param {string} studentEmail - Student's email for ownership check.
 * @returns {Promise<Object>} Conversation details with messages array.
 */
export async function fetchConversationDetail(conversationId, studentEmail) {
  if (!conversationId) throw new Error('Conversation ID is required.')

  try {
    const query = studentEmail ? `?studentEmail=${encodeURIComponent(studentEmail)}` : ''
    const headers = {
      'Accept': 'application/json'
    }
    if (studentEmail) {
      headers['X-Student-Email'] = studentEmail
    }

    const response = await fetch(
      `${API_BASE_URL}/api/chat/conversations/${encodeURIComponent(conversationId)}${query}`,
      {
        method: 'GET',
        headers
      }
    )

    if (!response.ok) {
      throw new Error(`Failed to load conversation details (${response.status})`)
    }

    return await response.json()
  } catch (err) {
    console.error(`Error loading conversation ${conversationId}:`, err)
    throw err
  }
}

/**
 * Deletes a conversation permanently from the database.
 * Endpoint: DELETE /api/chat/conversations/{id}
 *
 * @param {string} conversationId - The conversation ID to delete.
 * @param {string} studentEmail - Student's email for ownership check.
 * @returns {Promise<boolean>} True if successfully deleted.
 */
export async function deleteConversation(conversationId, studentEmail) {
  if (!conversationId) throw new Error('Conversation ID is required.')

  try {
    const query = studentEmail ? `?studentEmail=${encodeURIComponent(studentEmail)}` : ''
    const headers = {
      'Accept': 'application/json'
    }
    if (studentEmail) {
      headers['X-Student-Email'] = studentEmail
    }

    const response = await fetch(
      `${API_BASE_URL}/api/chat/conversations/${encodeURIComponent(conversationId)}${query}`,
      {
        method: 'DELETE',
        headers
      }
    )

    if (!response.ok && response.status !== 204 && response.status !== 200) {
      throw new Error(`Failed to delete conversation (${response.status})`)
    }

    return true
  } catch (err) {
    console.error(`Error deleting conversation ${conversationId}:`, err)
    throw err
  }
}

/**
 * Checks the status of the AI provider and knowledge base integration.
 * Endpoint: GET /api/chat/status
 *
 * @returns {Promise<Object>} Status metadata object.
 */
export async function getChatStatus() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/chat/status`, {
      headers: {
        'Accept': 'application/json'
      }
    })

    if (!response.ok) {
      return {
        aiProvider: 'Google Gemini',
        configured: false,
        backendReachable: false,
        message: `Backend returned HTTP ${response.status}`
      }
    }

    const data = await response.json()
    return {
      ...data,
      backendReachable: true
    }
  } catch (err) {
    const currentOrigin = typeof window !== 'undefined' ? window.location.origin : 'current origin'
    return {
      aiProvider: 'Google Gemini',
      configured: false,
      backendReachable: false,
      message: `Unable to connect to backend at ${API_BASE_URL} from ${currentOrigin} (${err.message || 'server down or CORS blocked'})`
    }
  }
}
