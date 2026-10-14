import React from 'react'

const SUGGESTION_CARDS = [
  {
    id: 'attendance',
    title: 'Attendance & Condonation',
    description: 'Understand attendance requirements and procedures.',
    query: 'What are the attendance requirements and condonation procedures?',
    accent: 'mint',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="4" width="18" height="18" rx="3" ry="3" />
        <line x1="16" y1="2" x2="16" y2="6" />
        <line x1="8" y1="2" x2="8" y2="6" />
        <line x1="3" y1="10" x2="21" y2="10" />
        <polyline points="9 16 11 18 15 14" />
      </svg>
    )
  },
  {
    id: 'examination',
    title: 'Examination Rules',
    description: 'Find information about assessments and academic regulations.',
    query: 'What are the examination rules, grading criteria, and academic regulations?',
    accent: 'blue',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5Z" />
        <path d="M9 8h6" />
        <path d="M9 12h6" />
        <path d="M9 16h4" />
      </svg>
    )
  },
  {
    id: 'fees',
    title: 'Fees & Payments',
    description: 'Ask about fee policies and payment information.',
    query: 'What are the fee policies, payment schedules, and tuition deadlines?',
    accent: 'lavender',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="2" y="5" width="20" height="14" rx="2" />
        <line x1="2" y1="10" x2="22" y2="10" />
        <circle cx="7" cy="15" r="1" fill="currentColor" />
        <line x1="12" y1="15" x2="17" y2="15" />
      </svg>
    )
  },
  {
    id: 'hostel',
    title: 'Hostel Guidelines',
    description: 'Explore hostel rules and residential policies.',
    query: 'What are the hostel guidelines, residential policies, and campus timings?',
    accent: 'mint',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 10l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" />
        <polyline points="9 22 9 12 15 12 15 22" />
      </svg>
    )
  }
]

function WelcomeScreen({ onSelectSuggestion, isLoading = false }) {
  return (
    <div className="welcome-screen">
      <div className="welcome-hero">
        {/* Soft Academic Pastel Illustration */}
        <div className="welcome-illustration-wrapper" aria-hidden="true">
          <svg
            className="welcome-illustration-svg"
            width="128"
            height="100"
            viewBox="0 0 128 100"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            {/* Background Pastel Geometric Shapes */}
            <circle cx="34" cy="48" r="28" fill="#DCEAF7" fillOpacity="0.85" />
            <circle cx="88" cy="40" r="32" fill="#CDE8D5" fillOpacity="0.85" />
            <ellipse cx="64" cy="74" rx="42" ry="16" fill="#E9E2F5" fillOpacity="0.8" />

            {/* Academic Cap Elements */}
            <g transform="translate(32, 16)">
              {/* Cap Base Shadow */}
              <ellipse cx="32" cy="46" rx="22" ry="7" fill="#28332F" fillOpacity="0.08" />
              {/* Cap Skull Underneath */}
              <path
                d="M18 34C18 34 20 45 32 45C44 45 46 34 46 34"
                stroke="#28332F"
                strokeWidth="2.5"
                strokeLinecap="round"
                fill="#FAF9F4"
              />
              {/* Mortarboard Rhombus */}
              <path
                d="M32 18L10 28L32 38L54 28L32 18Z"
                fill="#28332F"
                stroke="#28332F"
                strokeWidth="2"
                strokeLinejoin="round"
              />
              {/* Tassel */}
              <path
                d="M48 30V43"
                stroke="#28332F"
                strokeWidth="2"
                strokeLinecap="round"
              />
              <circle cx="48" cy="44.5" r="2.5" fill="#28332F" />
            </g>

            {/* Subtle Decorative Pastel Sparkles */}
            <path
              d="M18 20L20 14L22 20L28 22L22 24L20 30L18 24L12 22L18 20Z"
              fill="#CDE8D5"
              stroke="#28332F"
              strokeWidth="0.8"
            />
            <path
              d="M106 24L107.5 19L109 24L114 25.5L109 27L107.5 32L106 27L101 25.5L106 24Z"
              fill="#E9E2F5"
              stroke="#28332F"
              strokeWidth="0.8"
            />
            <circle cx="20" cy="62" r="3" fill="#DCEAF7" stroke="#28332F" strokeWidth="0.8" />
            <circle cx="110" cy="58" r="3.5" fill="#CDE8D5" stroke="#28332F" strokeWidth="0.8" />
          </svg>
        </div>

        {/* Heading & Supporting Text */}
        <h1 className="welcome-heading">
          Hi! How can I help with your college questions?
        </h1>
        <p className="welcome-supporting-text">
          Your helpful, empathetic, and reliable student-support assistant for VIT-AP University. Ask about rules, exams, attendance, fees, or actionable next steps when facing academic challenges.
        </p>
      </div>

      {/* Four Clickable Suggestion Cards */}
      <div className="suggestion-cards-grid" role="group" aria-label="Suggested topics">
        {SUGGESTION_CARDS.map((card) => (
          <button
            key={card.id}
            type="button"
            className={`suggestion-card suggestion-card-${card.accent}`}
            onClick={() => onSelectSuggestion(card.query)}
            disabled={isLoading}
            aria-label={`${card.title}: ${card.description}`}
          >
            <div className={`card-icon-pill pill-${card.accent}`} aria-hidden="true">
              {card.icon}
            </div>
            <div className="card-text-wrapper">
              <h2 className="card-title">{card.title}</h2>
              <p className="card-description">{card.description}</p>
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}

export default WelcomeScreen
