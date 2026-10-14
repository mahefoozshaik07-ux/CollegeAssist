import React, { useState, useEffect } from 'react'
import { getStudents, API_BASE_URL } from '../api/studentApi'

function StudentsSection() {
  const [students, setStudents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const fetchStudentData = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getStudents()
      setStudents(data)
    } catch (err) {
      setError(err.message || 'Failed to connect to backend service')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let ignore = false
    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        const data = await getStudents()
        if (!ignore) setStudents(data)
      } catch (err) {
        if (!ignore) setError(err.message || 'Failed to connect to backend service')
      } finally {
        if (!ignore) setLoading(false)
      }
    }
    load()
    return () => {
      ignore = true
    }
  }, [])

  return (
    <section className="students-section" aria-label="Enrolled Students">
      <div className="section-header students-header">
        <div>
          <div className="section-title-row">
            <h2 className="section-title">🎓 Enrolled Students</h2>
            <span className="live-api-badge">Live MySQL API</span>
          </div>
          <span className="section-subtitle">
            Records loaded from Spring Boot backend (<code>{API_BASE_URL}/api/students</code>)
          </span>
        </div>

        <button
          type="button"
          className="refresh-btn"
          onClick={fetchStudentData}
          disabled={loading}
          title="Refresh student records from backend"
        >
          🔄 {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      {/* 1. Loading State */}
      {loading && (
        <div className="students-state-card loading-card">
          <div className="loading-spinner" />
          <p className="state-title">Loading students...</p>
          <p className="state-desc">Fetching real records from <code>{API_BASE_URL}/api/students</code></p>
        </div>
      )}

      {/* 2. Error State */}
      {!loading && error && (
        <div className="students-state-card error-card">
          <span className="state-icon">⚠️</span>
          <p className="state-title">Unable to Load Students</p>
          <p className="state-desc">{error}</p>
          <p className="state-hint">
            Ensure your Spring Boot backend is running at <code>{API_BASE_URL}</code> and connected to MySQL.
          </p>
          <button
            type="button"
            className="retry-btn"
            onClick={fetchStudentData}
          >
            Try Again
          </button>
        </div>
      )}

      {/* 3. Empty State */}
      {!loading && !error && students.length === 0 && (
        <div className="students-state-card empty-card">
          <span className="state-icon">📭</span>
          <p className="state-title">No Students Found</p>
          <p className="state-desc">
            The database connected successfully, but no student records exist in the <code>students</code> table yet.
          </p>
          <p className="state-hint">
            You can add new student records using <code>POST /api/students</code> on your Spring Boot backend.
          </p>
        </div>
      )}

      {/* 4. Loaded Data State */}
      {!loading && !error && students.length > 0 && (
        <div className="students-table-container">
          <table className="students-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Student Name</th>
                <th>Email Address</th>
                <th>Department</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {students.map((student) => (
                <tr key={student.id}>
                  <td>
                    <span className="student-id-pill">#{student.id}</span>
                  </td>
                  <td>
                    <div className="student-name-cell">
                      <span className="student-avatar-letter">
                        {student.name ? student.name.charAt(0).toUpperCase() : '?'}
                      </span>
                      <span className="student-name-text">{student.name}</span>
                    </div>
                  </td>
                  <td>
                    <span className="student-email-text">{student.email}</span>
                  </td>
                  <td>
                    <span className="student-dept-tag">{student.department}</span>
                  </td>
                  <td>
                    <span className="student-status-badge">Active</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="students-table-footer">
            <span>Showing {students.length} student{students.length === 1 ? '' : 's'} registered in MySQL</span>
          </div>
        </div>
      )}
    </section>
  )
}

export default StudentsSection
