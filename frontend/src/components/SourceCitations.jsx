import React from 'react'

/**
 * Extracts and cleans a display filename from filename or documentPath
 */
function getDisplayFilename(src) {
  if (src.filename && src.filename.trim()) {
    return src.filename.trim()
  }
  if (src.documentPath && src.documentPath.trim()) {
    const parts = src.documentPath.trim().replace(/\\/g, '/').split('/')
    return parts[parts.length - 1] || src.documentPath.trim()
  }
  return 'University Document'
}

/**
 * Extracts clean section title if valid and meaningful
 */
function getDisplaySection(src) {
  if (src.sectionTitle && typeof src.sectionTitle === 'string') {
    const trimmed = src.sectionTitle.trim()
    if (trimmed && trimmed.toLowerCase() !== 'general') {
      return trimmed
    }
  }
  return null
}

/**
 * Deduplicate source citations by filename and section title
 */
function deduplicateSources(sources) {
  if (!sources || !Array.isArray(sources) || sources.length === 0) {
    return []
  }

  const seen = new Set()
  const unique = []

  for (const src of sources) {
    if (!src) continue
    const filename = getDisplayFilename(src)
    const sectionTitle = getDisplaySection(src)
    const dedupeKey = `${filename.toLowerCase()}::${(sectionTitle || '').toLowerCase()}`

    if (!seen.has(dedupeKey)) {
      seen.add(dedupeKey)
      unique.push({
        id: dedupeKey,
        filename,
        sectionTitle,
        category: src.category || null
      })
    }
  }

  return unique
}

function SourceCitations({ sources = [] }) {
  const uniqueSources = deduplicateSources(sources)

  if (uniqueSources.length === 0) {
    return null
  }

  return (
    <div className="citations-section" aria-label="Referenced institutional sources">
      <div className="citations-header">
        <svg
          className="citations-header-icon"
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
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
          <line x1="16" y1="13" x2="8" y2="13" />
          <line x1="16" y1="17" x2="8" y2="17" />
          <polyline points="10 9 9 9 8 9" />
        </svg>
        <span className="citations-title">Sources</span>
      </div>

      <div className="citations-chips-grid">
        {uniqueSources.map((item, index) => (
          <div key={`${item.id}-${index}`} className="citation-chip">
            <svg
              className="citation-file-icon"
              width="13"
              height="13"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
              <polyline points="14 2 14 8 20 8" />
            </svg>

            <span className="citation-filename" title={item.filename}>
              {item.filename}
            </span>

            {item.sectionTitle && (
              <span className="citation-section-badge" title={`Section: ${item.sectionTitle}`}>
                {item.sectionTitle}
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

export default SourceCitations
