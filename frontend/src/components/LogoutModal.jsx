import React from 'react'

function LogoutModal({ isOpen, onClose, onConfirm, userEmail }) {
  if (!isOpen) return null

  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation">
      <div
        className="modal-panel"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="logout-title"
      >
        <div className="modal-header">
          <div className="modal-icon-badge" aria-hidden="true">
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
          </div>
          <div>
            <h3 id="logout-title" className="modal-title">Sign Out of CollegeAssist</h3>
            <p className="modal-subtitle">
              You are currently authenticated as <strong>{userEmail}</strong>.
            </p>
          </div>
        </div>

        <p className="modal-description">
          Signing out will end your current session and reset conversation history on this device.
        </p>

        <div className="modal-actions">
          <button
            type="button"
            className="modal-btn modal-btn-secondary"
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            type="button"
            className="modal-btn modal-btn-primary"
            onClick={onConfirm}
          >
            Confirm Logout
          </button>
        </div>
      </div>
    </div>
  )
}

export default LogoutModal
