# CareerForge - Step 1: Project Architecture

## Agent Competition Result

Three agents reviewed the project brief:

- Backend Architecture Agent: proposed a clean Spring Boot REST API with MySQL.
- UI/UX Design Agent: proposed a modern Bootstrap SaaS dashboard experience.
- Judge Agent: compared both and selected the best practical direction for a college mini-project.

Winner: **Layered Spring Boot REST API + MySQL + modern Bootstrap dashboard UI**.

This approach wins because it is professional, easy to explain in a viva, simple enough to complete step by step, and future-ready for AI, JWT, and React upgrades later.

## What We Are Creating

CareerForge is a full stack Java web platform where students can:

- Register and login
- Create a student profile
- Add current skills
- Select a career goal
- Compare current skills with required career skills
- See missing skills
- Generate a learning roadmap
- Track roadmap progress
- Add portfolio projects
- View learning resources
- Use a modern dashboard
- Allow admin to manage careers, skills, and resources

For version 1, we will not use a real AI API. The backend will use predefined career roles and Java logic to calculate skill gaps.

## Final Architecture

```text
Frontend HTML/CSS/JavaScript/Bootstrap
        |
        | REST API calls using fetch()
        v
Spring Boot Controller Layer
        |
        v
Spring Boot Service Layer
        |
        v
Spring Data JPA Repository Layer
        |
        v
MySQL Database
```

## Why This Architecture Is Best

This architecture is best for your mini-project because:

- It follows real industry-style backend structure.
- It is easy to build step by step.
- It keeps business logic separate from API code.
- It works well with Postman testing.
- It supports a clean Bootstrap frontend.
- It can be uploaded to GitHub professionally.
- It can later support JWT login, AI recommendations, resume analysis, and React frontend.

## Main Folder Structure

Create the project like this:

```text
CareerForge/
|
+-- backend/
|   +-- careerforge-api/
|       +-- src/main/java/com/careerforge/
|       |   +-- CareerForgeApplication.java
|       |
|       |   +-- config/
|       |   |   +-- CorsConfig.java
|       |
|       |   +-- controller/
|       |   |   +-- AuthController.java
|       |   |   +-- UserController.java
|       |   |   +-- SkillController.java
|       |   |   +-- CareerController.java
|       |   |   +-- RoadmapController.java
|       |   |   +-- ProjectController.java
|       |   |   +-- ResourceController.java
|       |   |   +-- AdminController.java
|       |
|       |   +-- service/
|       |   |   +-- AuthService.java
|       |   |   +-- UserService.java
|       |   |   +-- SkillService.java
|       |   |   +-- CareerService.java
|       |   |   +-- RoadmapService.java
|       |   |   +-- ProjectService.java
|       |   |   +-- ResourceService.java
|       |
|       |   +-- repository/
|       |   |   +-- UserRepository.java
|       |   |   +-- SkillRepository.java
|       |   |   +-- CareerRepository.java
|       |   |   +-- RoadmapRepository.java
|       |   |   +-- ProjectRepository.java
|       |   |   +-- ResourceRepository.java
|       |
|       |   +-- model/
|       |   |   +-- User.java
|       |   |   +-- Skill.java
|       |   |   +-- UserSkill.java
|       |   |   +-- Career.java
|       |   |   +-- CareerSkill.java
|       |   |   +-- Roadmap.java
|       |   |   +-- RoadmapStep.java
|       |   |   +-- Project.java
|       |   |   +-- Resource.java
|       |
|       |   +-- dto/
|       |   |   +-- request/
|       |   |   +-- response/
|       |
|       |   +-- exception/
|       |   |   +-- GlobalExceptionHandler.java
|       |   |   +-- ResourceNotFoundException.java
|       |
|       |   +-- util/
|       |       +-- SkillGapCalculator.java
|       |
|       +-- src/main/resources/
|       |   +-- application.properties
|       |
|       +-- pom.xml
|
+-- frontend/
|   +-- index.html
|   +-- login.html
|   +-- register.html
|   +-- dashboard.html
|   +-- skills.html
|   +-- careers.html
|   +-- skill-gap.html
|   +-- roadmap.html
|   +-- projects.html
|   +-- profile.html
|   +-- admin.html
|
|   +-- css/
|   |   +-- style.css
|
|   +-- js/
|   |   +-- api.js
|   |   +-- auth.js
|   |   +-- dashboard.js
|   |   +-- skills.js
|   |   +-- careers.js
|   |   +-- roadmap.js
|   |   +-- projects.js
|
|   +-- assets/
|       +-- images/
|
+-- database/
|   +-- schema.sql
|   +-- sample-data.sql
|
+-- docs/
|   +-- api-endpoints.md
|   +-- project-report.md
|   +-- screenshots/
|
+-- README.md
+-- .gitignore
+-- LICENSE
```

## Backend Layer Explanation

### 1. Controller Layer

Controllers receive API requests from the frontend or Postman.

Example APIs:

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/skills
POST /api/users/{id}/skills
GET  /api/careers
GET  /api/users/{id}/skill-gap/{careerId}
GET  /api/users/{id}/roadmap
PUT  /api/roadmap/{id}
GET  /api/users/{id}/projects
POST /api/users/{id}/projects
```

Controllers should not contain complex logic. They call service classes.

### 2. Service Layer

Services contain the main project logic.

Important service tasks:

- Register user
- Login user
- Save profile
- Add skills
- Compare student skills with career skills
- Calculate readiness percentage
- Generate roadmap from missing skills
- Update roadmap progress
- Add completed projects
- Manage admin operations

### 3. Repository Layer

Repositories connect Spring Boot with MySQL using Spring Data JPA.

Example:

```text
UserRepository
SkillRepository
CareerRepository
RoadmapRepository
ProjectRepository
ResourceRepository
```

### 4. Model Layer

Models represent database tables.

Main models:

```text
User
Skill
UserSkill
Career
CareerSkill
Roadmap
RoadmapStep
Project
Resource
```

## Database Design Overview

Recommended tables:

```text
users
skills
user_skills
careers
career_skills
roadmaps
roadmap_steps
projects
resources
```

High-level relationships:

```text
User 1 -> many UserSkill
Skill 1 -> many UserSkill

Career 1 -> many CareerSkill
Skill 1 -> many CareerSkill

User 1 -> many RoadmapStep
Career 1 -> many RoadmapStep

User 1 -> many Project
Skill 1 -> many Resource
Career 1 -> many Resource
```

Simple meaning:

- A user can have many skills.
- A career requires many skills.
- Skill gap analysis compares user skills with career required skills.
- Missing skills are converted into roadmap steps.
- A user can track roadmap progress.
- A user can add multiple portfolio projects.
- Resources are linked to skills or careers.

## Skill Gap Algorithm

The first version will use rule-based Java logic.

Example:

```text
Student skills:
Java, HTML, CSS

Career:
Full Stack Java Developer

Required skills:
Java, HTML, CSS, JavaScript, Spring Boot, MySQL, REST API, Git

Completed:
Java, HTML, CSS

Missing:
JavaScript, Spring Boot, MySQL, REST API, Git

Readiness:
3 / 8 * 100 = 37.5%
```

This logic will go inside:

```text
backend/careerforge-api/src/main/java/com/careerforge/util/SkillGapCalculator.java
```

## UI/UX Direction

CareerForge should look like a real modern SaaS dashboard, not a basic college project.

Use:

- Bootstrap 5
- Bootstrap Icons
- Clean white content area
- Dark sidebar
- Blue primary accent
- Cards
- Progress bars
- Tables
- Badges
- Responsive layout

Recommended colors:

```text
Background: #F8FAFC
Sidebar:    #111827
Primary:    #2563EB
Success:    #16A34A
Warning:    #F59E0B
Danger:     #DC2626
Text:       #1F2937
```

Dashboard layout:

```text
Dark Sidebar
  - Dashboard
  - Skills
  - Careers
  - Skill Gap
  - Roadmap
  - Projects
  - Profile
  - Logout

Top Bar
  - Page title
  - Search
  - User profile

Main Content
  - Career goal card
  - Skills completed card
  - Career readiness card
  - Projects completed card
  - Roadmap progress card
  - Recent skills
  - Current roadmap
  - Recommended skills
```

## Main User Flow

```text
Register/Login
    |
    v
Complete Profile
    |
    v
Add Current Skills
    |
    v
Select Career Goal
    |
    v
View Skill Gap
    |
    v
Generate Roadmap
    |
    v
Track Progress
    |
    v
Add Portfolio Projects
```

## Admin Flow

```text
Admin Login
    |
    v
View Users
    |
    v
Manage Careers
    |
    v
Manage Required Skills
    |
    v
Manage Resources
```

## Future Upgrade Plan

Do not implement these now, but keep the architecture ready for:

- JWT authentication
- AI career recommendations
- Resume analysis
- Job recommendations
- Internship recommendations
- GitHub profile analysis
- AI chatbot
- React frontend

## Command For This Step

No coding command is required in Step 1.

You only need to understand and confirm the architecture.

Expected output:

```text
CareerForge architecture is ready.
Next step: Install required software and create the Spring Boot project.
```

## Common Mistakes To Avoid

- Do not put all code inside controllers.
- Do not store skills as comma-separated text.
- Do not start with AI APIs.
- Do not create React frontend now.
- Do not skip database relationships.
- Do not make the admin panel too large in version 1.
- Do not build UI before understanding backend data flow.

## Next Step

Step 2 will cover installing required software:

- Java JDK
- Maven
- MySQL
- MySQL Workbench
- VS Code or IntelliJ IDEA
- Postman
- Git
