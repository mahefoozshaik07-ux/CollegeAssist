import React from 'react'
import StatCard from '../components/StatCard'
import QuickAction from '../components/QuickAction'
import ChatBox from '../components/ChatBox'
import NoticeCard from '../components/NoticeCard'
import StudentsSection from '../components/StudentsSection'

function Dashboard({ onNavigate }) {
  // Demo notices data clearly marked as sample content
  const demoNotices = [
    {
      id: 1,
      title: '[Demo] Mid-Semester Examination Schedule Notification',
      category: 'Academic Affairs',
      date: '2 days ago',
      summary: 'Provisional timetable for upcoming mid-semester assessments has been prepared for student review.',
      tag: 'Sample Notice',
      priority: 'high'
    },
    {
      id: 2,
      title: '[Demo] Central Library Extended Reading Hours During Study Week',
      category: 'Campus Facilities',
      date: '4 days ago',
      summary: 'The main library reading hall will remain accessible with extended hours for semester study preparations.',
      tag: 'Sample Notice',
      priority: 'normal'
    },
    {
      id: 3,
      title: '[Demo] Campus Placement Orientation & Resume Clinic',
      category: 'Career Development',
      date: '1 week ago',
      summary: 'Registration is open for pre-final and final year student placement readiness sessions and workshops.',
      tag: 'Sample Notice',
      priority: 'normal'
    },
    {
      id: 4,
      title: '[Demo] Hostel Fee Submission & Room Allocation Notice',
      category: 'Student Welfare',
      date: '2 weeks ago',
      summary: 'Residents are reminded to complete semester registration and check room allotment guidelines.',
      tag: 'Sample Notice',
      priority: 'normal'
    }
  ]

  const handleQuickAction = (actionName) => {
    if (onNavigate) {
      if (actionName === 'Ask AI') onNavigate('Ask AI')
      else if (actionName === 'Raise an Issue') onNavigate('My Issues')
      else if (actionName === 'View Notices') onNavigate('Notices')
      else if (actionName === 'Browse Documents') onNavigate('Documents')
    }
  }

  return (
    <div className="dashboard-page">
      {/* 1. Welcome Section */}
      <section className="welcome-banner">
        <div className="welcome-content">
          <div className="welcome-tag">
            <span className="sparkle-icon">✨</span> Student Support Hub
          </div>
          <h1 className="welcome-title">Welcome to CollegeAssist</h1>
          <p className="welcome-subtitle">
            Your intelligent college support assistant
          </p>
          <p className="welcome-greeting">
            Hello, Alex! What would you like guidance on today? Ask AI about college regulations, browse notices, or track campus issues.
          </p>
        </div>
      </section>

      {/* 2. Four Summary Cards */}
      <section className="summary-cards-section" aria-label="Summary Overview">
        <StatCard
          icon="🤖"
          title="Ask the AI"
          value="24/7 Support"
          subtitle="Instant answers on rules, fees & exams"
          badgeText="Demo Mode"
          badgeType="purple"
          onClick={() => handleQuickAction('Ask AI')}
        />
        <StatCard
          icon="🎫"
          title="Active Issues"
          value="2 Tickets"
          subtitle="1 Under Review • 1 Pending"
          badgeText="Sample Data"
          badgeType="amber"
          onClick={() => handleQuickAction('Raise an Issue')}
        />
        <StatCard
          icon="📢"
          title="Recent Notices"
          value="4 Circulars"
          subtitle="Academics, exams & hostel updates"
          badgeText="Sample Data"
          badgeType="blue"
          onClick={() => handleQuickAction('View Notices')}
        />
        <StatCard
          icon="📁"
          title="College Documents"
          value="14 Files"
          subtitle="Handbooks, rules & syllabus guides"
          badgeText="Sample Data"
          badgeType="emerald"
          onClick={() => handleQuickAction('Browse Documents')}
        />
      </section>

      {/* 3. Quick Actions Section */}
      <section className="quick-actions-section">
        <div className="section-header">
          <h2 className="section-title">⚡ Quick Actions</h2>
          <span className="section-subtitle">Common student tasks and navigation</span>
        </div>

        <div className="quick-actions-grid">
          <QuickAction
            icon="💬"
            title="Ask AI"
            description="Query policies, attendance & guidelines"
            badge="Interactive"
            onClick={() => handleQuickAction('Ask AI')}
          />
          <QuickAction
            icon="🎫"
            title="Raise an Issue"
            description="File student grievances or inquiries"
            badge="Workflow"
            onClick={() => handleQuickAction('Raise an Issue')}
          />
          <QuickAction
            icon="📢"
            title="View Notices"
            description="Check the latest campus circulars"
            badge="Updates"
            onClick={() => handleQuickAction('View Notices')}
          />
          <QuickAction
            icon="📁"
            title="Browse Documents"
            description="Access official college PDFs & forms"
            badge="Library"
            onClick={() => handleQuickAction('Browse Documents')}
          />
        </div>
      </section>

      {/* 4. Enrolled Students Section (Live Database Data) */}
      <StudentsSection onNavigate={onNavigate} />

      {/* 5. Split Section: AI Assistant (Left/Main) & Recent Notices (Right) */}
      <div className="dashboard-split-grid">
        {/* Left / Main Column: AI Assistant */}
        <section className="split-column main-col" id="ai-assistant-section">
          <div className="section-header">
            <h2 className="section-title">🤖 AI Assistant</h2>
            <span className="section-subtitle">Simulate queries against institutional documents</span>
          </div>
          <ChatBox />
        </section>

        {/* Right Column: Recent Notices */}
        <section className="split-column side-col" id="recent-notices-section">
          <div className="section-header notices-header">
            <h2 className="section-title">📢 Recent Notices</h2>
            <span className="demo-indicator-pill">Demo Circulars</span>
          </div>

          <div className="notices-list">
            {demoNotices.map((notice) => (
              <NoticeCard
                key={notice.id}
                title={notice.title}
                category={notice.category}
                date={notice.date}
                summary={notice.summary}
                tag={notice.tag}
                priority={notice.priority}
              />
            ))}
          </div>

          <div className="notices-footer-card">
            <span className="info-icon">ℹ️</span>
            <p>All notices listed above are sample/demo data designed for Phase 3 UI evaluation.</p>
          </div>
        </section>
      </div>
    </div>
  )
}

export default Dashboard
