import React from 'react'

function NoticeCard({ title, category, date, summary, tag = 'Sample Notice', priority = 'normal' }) {
  return (
    <article className={`notice-card notice-${priority}`}>
      <div className="notice-header">
        <div className="notice-meta">
          <span className="notice-category">{category}</span>
          <span className="notice-bullet">•</span>
          <span className="notice-date">{date}</span>
        </div>
        <span className="notice-demo-tag">{tag}</span>
      </div>

      <h4 className="notice-title">{title}</h4>
      <p className="notice-summary">{summary}</p>
    </article>
  )
}

export default NoticeCard
