import React, { useState, useEffect, useRef } from 'react'
import WelcomeScreen from './WelcomeScreen'
import ChatMessage from './ChatMessage'
import MessageComposer from './MessageComposer'
import LoadingIndicator from './LoadingIndicator'
import ErrorNotice from './ErrorNotice'
import { sendChatMessage } from '../api/chatApi'

function generateId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
}

function getFormattedTime() {
  return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function ChatBox({
  messages = [],
  onUpdateMessages,
  conversationId = 'default-session',
  onNewChatStarted,
  userEmail
}) {
  const [inputText, setInputText] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState(null)
  const [lastFailedQuery, setLastFailedQuery] = useState(null)

  const messagesEndRef = useRef(null)

  // Automatically keep latest message visible
  useEffect(() => {
    if (messages.length > 0 || isLoading || errorMessage) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages, isLoading, errorMessage])

  const handleSendMessage = async (textToSend, isRetry = false) => {
    const text = (textToSend || inputText).trim()
    if (!text || isLoading) return

    setErrorMessage(null)
    setLastFailedQuery(null)

    const timeString = getFormattedTime()

    let nextMessages = messages
    if (!isRetry) {
      const userMessage = {
        id: generateId('user'),
        sender: 'user',
        text: text,
        timestamp: timeString
      }

      nextMessages = [...messages, userMessage]
      onUpdateMessages(nextMessages)

      // Notify parent to set session title if first message
      if (messages.length === 0 && onNewChatStarted) {
        onNewChatStarted(text)
      }

      setInputText('')
    }

    setIsLoading(true)

    try {
      const response = await sendChatMessage(text, conversationId, userEmail)
      const assistantMessage = {
        id: response.id || generateId('asst'),
        sender: 'assistant',
        text: response.content || 'I could not find relevant institutional records for your question.',
        timestamp: response.timestamp || timeString,
        status: response.status || 'CONFIGURED',
        sources: response.sources || []
      }

      onUpdateMessages([...nextMessages, assistantMessage])
    } catch (err) {
      // Show calm connection notice as specified in requirements
      const friendlyNotice = err.message && err.message.includes('Server error')
        ? err.message
        : "We couldn't connect to CollegeAssist. Please try again."
      setErrorMessage(friendlyNotice)
      setLastFailedQuery(text)
    } finally {
      setIsLoading(false)
    }
  }

  const handleRetry = () => {
    if (lastFailedQuery && !isLoading) {
      handleSendMessage(lastFailedQuery, true)
    }
  }

  const hasMessages = messages.length > 0

  return (
    <div className="chatbox-canvas">
      {/* Scrollable conversation area */}
      <div className="chat-scroll-area">
        <div className="chat-content-width">
          {!hasMessages ? (
            <WelcomeScreen
              onSelectSuggestion={(query) => handleSendMessage(query, false)}
              isLoading={isLoading}
            />
          ) : (
            <div className="chat-thread-stream">
              {messages.map((msg) => (
                <ChatMessage key={msg.id} message={msg} />
              ))}

              {/* Animated Loading Typing State */}
              {isLoading && <LoadingIndicator />}

              {/* Calm Connection / Service Notice */}
              {errorMessage && (
                <ErrorNotice
                  message={errorMessage}
                  onRetry={handleRetry}
                  canRetry={Boolean(lastFailedQuery && !isLoading)}
                />
              )}

              <div ref={messagesEndRef} className="scroll-anchor" />
            </div>
          )}
        </div>
      </div>

      {/* Docked bottom message composer */}
      <MessageComposer
        value={inputText}
        onChange={setInputText}
        onSend={() => handleSendMessage()}
        isLoading={isLoading}
      />
    </div>
  )
}

export default ChatBox
