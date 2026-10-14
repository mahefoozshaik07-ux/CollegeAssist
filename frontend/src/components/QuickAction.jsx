import React from 'react'

function QuickAction({ icon, title, description, badge, onClick }) {
  return (
    <button 
      type="button" 
      className="quick-action-card" 
      onClick={onClick}
    >
      <div className="quick-action-icon-box">
        <span className="quick-action-icon">{icon}</span>
      </div>
      <div className="quick-action-info">
        <div className="quick-action-header-row">
          <span className="quick-action-title">{title}</span>
          {badge && <span className="quick-action-badge">{badge}</span>}
        </div>
        <span className="quick-action-desc">{description}</span>
      </div>
      <span className="quick-action-arrow">→</span>
    </button>
  )
}

export default QuickAction
