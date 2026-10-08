# CollegeAssist

CollegeAssist is a production-quality, AI-powered college student support platform designed to streamline student inquiries, institutional knowledge access, and issue tracking.

---

## 📌 Project Overview

CollegeAssist centralizes campus communications and academic guidelines. It enables students to query official college policies and submit complaints, while providing administrators with tools to maintain documents and resolve student issues.

### Key Capabilities

**For Students:**
- **Information Retrieval:** Ask questions regarding college regulations, academic policies, exam guidelines, fee structures, departments, placements, hostel accommodations, and official notices.
- **Search System:** Search through college notices and knowledge base documents.
- **Issue & Complaint Ticketing:** Raise institutional complaints or inquiries.
- **Ticket Status Tracking:** Monitor real-time status updates and responses on submitted tickets.
- **AI-Powered Assistance (Planned):** Receive grounded, accurate answers via Retrieval-Augmented Generation (RAG) referencing verified college documents.

**For Administrators:**
- **User Management:** Oversee student profiles and role assignments.
- **Document Management:** Upload, organize, and update official institutional policies and circulars.
- **FAQ & Notice Management:** Publish and maintain campus notices and frequently asked questions.
- **Issue Resolution:** Review, triage, assign, and update student complaint tickets.
- **System Monitoring:** Monitor query analytics and chatbot operational performance.

---

## 🏛️ Planned Architecture

The application follows a decoupled client-server architecture with dedicated services for processing and knowledge retrieval:

```
┌─────────────────────────────────┐
│     Frontend (Client Tier)      │
│     React (JavaScript) + SPA    │
└────────────────┬────────────────┘
                 │
                 │ REST APIs / HTTPS (JSON)
                 │ JWT Bearer Authentication
                 ▼
┌─────────────────────────────────┐
│     Backend (Server Tier)       │
│     Java Spring Boot REST API   │
│ ┌─────────────────────────────┐ │
│ │ Security & JWT Auth Filter  │ │
│ ├─────────────────────────────┤ │
│ │ Service & Controller Layers │ │
│ ├─────────────────────────────┤ │
│ │ Issue & Ticket Management   │ │
│ ├─────────────────────────────┤ │
│ │ AI & RAG Orchestration      │ │
│ └─────────────────────────────┘ │
└───────┬─────────────────┬───────┘
        │                 │
        │ Spring Data JPA │ Gemini API & Vector Search
        ▼                 ▼
┌──────────────┐   ┌───────────────────────────┐
│ MySQL DB     │   │ AI & RAG Infrastructure   │
│ - Users      │   │ - Gemini API (Inference)  │
│ - Issues     │   │ - Text Embeddings         │
│ - Metadata   │   │ - Vector Database         │
└──────────────┘   └───────────────────────────┘
```

---

## 🛠️ Planned Technology Stack

- **Frontend:** React (JavaScript), Single Page Application (SPA), REST client
- **Backend:** Java Spring Boot (Spring Web, Spring Data JPA, Spring Security)
- **Database:** MySQL (Relational persistence for users, roles, issues, metadata)
- **Authentication:** Spring Security with stateless JWT (JSON Web Tokens)
- **AI Engine:** Google Gemini API
- **RAG Architecture:** Text embedding generation paired with a vector store for semantic document retrieval
- **API Style:** RESTful JSON APIs
- **Version Control:** Git

---

## 📁 Repository Structure

```
CollegeAssist/
├── frontend/               # React client application (completely decoupled)
├── backend/                # Java Spring Boot application (REST API services)
├── docs/                   # Institutional documentation repository for RAG ingestion
│   ├── college-rules/      # General institutional regulations and policies
│   ├── exam-rules/         # Examination guidelines, grading, and schedules
│   ├── attendance/         # Attendance policies and condonation rules
│   ├── fees/               # Tuition, fee breakdowns, and refund policies
│   ├── hostel/             # Residential guidelines, mess rules, and allocations
│   ├── placements/         # Career development, eligibility, and placement rules
│   └── notices/            # Official circulars and academic notices
├── database/               # Database initialization scripts, migrations, and DDL
├── .gitignore              # Git ignore rules for node, java, logs, and secrets
└── README.md               # Project architecture and development guide
```

---

## 🚀 Development Phases

The project follows a structured, phased implementation roadmap:

| Phase | Milestone | Scope | Status |
| :--- | :--- | :--- | :--- |
| **Phase 1** | **Project Initialization** | Directory structure setup, `.gitignore`, and architectural roadmap. | **Completed** |
| **Phase 2** | **Database & Data Modeling** | Relational schema design (Users, Roles, Issues, Notices) in MySQL with DDL migrations. | Planned |
| **Phase 3** | **Backend Core & APIs** | Spring Boot initialization, JPA entities, repositories, and core CRUD REST endpoints. | Planned |
| **Phase 4** | **Authentication & Authorization** | Spring Security integration with JWT authentication and role-based access (Student, Admin). | Planned |
| **Phase 5** | **Frontend Application** | React setup, layout, routing, issue submission forms, and student dashboard. | Planned |
| **Phase 6** | **Issue Tracking Workflows** | End-to-end complaint submission, status tracking, admin review, and audit logs. | Planned |
| **Phase 7** | **Document Processing & Knowledge Base** | Ingestion pipeline for `docs/` directories, document chunking, and embedding generation. | Planned |
| **Phase 8** | **AI & RAG Integration** | Gemini API integration, semantic search via vector database, and grounded response generation. | Planned |
| **Phase 9** | **Admin Control Panel** | Document upload management, notice publishing, issue triage panel, and system monitoring. | Planned |
| **Phase 10** | **Testing, Hardening & Deployment** | Integration testing, security audit, environment configuration, and production build. | Planned |

---

## 🔒 Security & Configuration Guidelines

1. **Zero Secrets in Source Control:** Never commit API keys (Gemini API keys), database credentials, or JWT signing secrets to version control.
2. **Environment Variables:** All sensitive properties must be injected via externalized configuration / environment variables.
3. **Decoupled Architecture:** Maintain strict separation between frontend and backend codebases.
