import React from 'react'

function Header({
  userEmail = 'student@university.edu',
  onLogout,
  onNewChat,
  onOpenHistory,
  theme = 'light',
  onToggleTheme,
  hasActiveChat = false,
  backendStatus = null
}) {
  return (
    <header className="college-header">
      <div className="header-container">
        {/* Left: Brand Identity & Logo */}
        <div className="header-brand-group">
          <button
            type="button"
            className="header-logo-btn"
            onClick={onNewChat}
            title="CollegeAssist Home"
            aria-label="CollegeAssist Home"
          >
            <div className="header-logo-badge">
              {/* Academic Cap & Spark SVG */}
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M22 10v6M2 10l10-5 10 5-10 5z" />
                <path d="M6 12v5c3 3 9 3 12 0v-5" />
              </svg>
            </div>
          </button>

          <div className="header-brand-text">
            <span className="header-brand-title">CollegeAssist</span>
            <span
              className="header-brand-subtitle"
              title={backendStatus?.backendReachable ? 'CollegeAssist backend connected' : undefined}
            >
              Your campus, made simpler.
            </span>
          </div>
        </div>

        {/* Right: Actions, History, Theme Toggle, Authenticated Email, Subtle Logout */}
        <div className="header-actions-group">
          {/* History drawer trigger button */}
          <button
            type="button"
            className="header-history-btn"
            onClick={onOpenHistory}
            title="View chat history"
            aria-label="View previous conversations"
          >
            <svg
              width="15"
              height="15"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
            <span className="btn-label-desktop">History</span>
          </button>

          {/* New chat action */}
          {hasActiveChat && (
            <button
              type="button"
              className="header-new-chat-btn"
              onClick={onNewChat}
              title="Start a new conversation"
              aria-label="Start new conversation"
            >
              <svg
                width="15"
                height="15"
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
              <span className="btn-label-desktop">New chat</span>
            </button>
          )}

          {/* Sun / Moon Theme Toggle */}
          <button
            type="button"
            className="header-theme-toggle-btn"
            onClick={onToggleTheme}
            title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            {theme === 'dark' ? (
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <circle cx="12" cy="12" r="5" />
                <line x1="12" y1="1" x2="12" y2="3" />
                <line x1="12" y1="21" x2="12" y2="23" />
                <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
                <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
                <line x1="1" y1="12" x2="3" y2="12" />
                <line x1="21" y1="12" x2="23" y2="12" />
                <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
                <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
              </svg>
            ) : (
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
              </svg>
            )}
          </button>

          {/* Authenticated user badge */}
          <div className="header-user-profile" title={`Signed in as ${userEmail}`}>
            <div className="header-avatar-circle" aria-hidden="true">
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
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </div>
            <span className="header-user-email">{userEmail}</span>
          </div>

          {/* Logout button */}
          <button
            type="button"
            className="header-logout-btn"
            onClick={onLogout}
            title="Log out of CollegeAssist"
            aria-label="Log out"
          >
            <span>Logout</span>
          </button>
        </div>
      </div>
    </header>
  )
}

export default Header
