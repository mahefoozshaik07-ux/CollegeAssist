// Reusable API client helper for CollegeAssist
// Uses VITE_API_BASE_URL if configured, otherwise defaults to http://localhost:8080

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

/**
 * Fetches the real student records from the Spring Boot backend.
 * Endpoint: GET /api/students
 *
 * @returns {Promise<Array>} Array of student objects: { id, name, email, department }
 */
export async function getStudents() {
  const response = await fetch(`${API_BASE_URL}/api/students`, {
    headers: {
      'Accept': 'application/json'
    }
  })

  if (!response.ok) {
    throw new Error(`Failed to fetch students (HTTP ${response.status}: ${response.statusText || 'Error'})`)
  }

  return response.json()
}

export { API_BASE_URL }
