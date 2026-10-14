import React from 'react'

function StatCard({ icon, title, value, subtitle, badgeText, badgeType = 'info', onClick }) {
  return (
    <div 
      className={`stat-card stat-${badgeType}`}
      onClick={onClick}
      role={onClick ? 'button' : 'region'}
      tabIndex={onClick ? 0 : undefined}
    >
      <div className="stat-card-header">
        <div className="stat-icon-wrapper">
          <span className="stat-icon">{icon}</span>
        </div>
        {badgeText && (
          <span className={`stat-badge badge-${badgeType}`}>
            {badgeText}
          </span>
        )}
      </div>

      <div className="stat-card-body">
        <h3 className="stat-title">{title}</h3>
        <div className="stat-value">{value}</div>
        <p className="stat-subtitle">{subtitle}</p>
      </div>
    </div>
  )
}

export default StatCard
