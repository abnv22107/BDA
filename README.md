# Competitive Programming Platform

A LeetCode/HackerRank-style platform with enhanced analytics to help users track their progress, identify weaknesses, and improve their coding skills efficiently.

## Features

### 1. Problem Management
- Problem Repository with metadata (difficulty, tags, acceptance rate)
- Test Case Management (hidden/public test cases)
- Dynamic Problem Creation via web interface

### 2. Code Submission & Evaluation
- Secure code execution in Docker containers
- Real-time feedback (compilation errors, runtime errors, test results)
- Performance metrics (time/memory usage comparison)
- Support for multiple programming languages (Scala, Java, Python)

### 3. User Analytics & Insights
- Skill Gap Analysis
- Performance Benchmarking
- Progress Tracking with visualizations
- Personalized problem recommendations

### 4. Competitive Features
- Real-time Leaderboard (global & category-specific)
- Custom Contests with flexible scheduling
- Virtual Battles (head-to-head coding duels)

### 5. Social & Gamification
- Achievements & Badges
- Discussion Forums
- Follow top coders

## Technical Stack

### Backend
- Scala 2.13.12
- Play Framework
- Akka Actors for concurrency
- MongoDB for data storage
- Docker for secure code execution

### Frontend (Optional)
- Scala.js or React.js
- D3.js/Chart.js for analytics visualizations

## Prerequisites

1. JDK 11 or higher
2. sbt (Scala Build Tool)
3. MongoDB 4.4 or higher
4. Docker

## Setup

1. Clone the repository:
```bash
git clone <repository-url>
cd competitive-programming-platform
```

2. Start MongoDB:
```bash
mongod --dbpath /path/to/data/directory
```

3. Configure the application:
- Update `conf/application.conf` with your MongoDB URI and other settings
- Ensure Docker is running for code execution

4. Run the application:
```bash
sbt run
```

The application will be available at `http://localhost:9000`

## API Endpoints

### Problem Management
- `POST /api/problems` - Create a new problem
- `GET /api/problems/:id` - Get problem details
- `GET /api/problems` - List problems with filters
- `PUT /api/problems/:id` - Update a problem
- `POST /api/problems/:id/submit` - Submit a solution

### Contest Management
- `POST /api/contests` - Create a new contest
- `GET /api/contests/:id` - Get contest details
- `GET /api/contests/active` - List active contests
- `POST /api/contests/:id/register` - Register for a contest
- `POST /api/contests/:contestId/problems/:problemId/submit` - Submit contest solution
- `GET /api/contests/:id/leaderboard` - Get contest leaderboard

### Analytics
- `GET /api/users/:id/performance` - Get user performance metrics
- `GET /api/users/:id/insights` - Get personalized insights
- `GET /api/users/:id/recommendations` - Get recommended problems

## Docker Setup

The platform uses Docker for secure code execution. Each submission is run in an isolated container with:
- Memory limits
- CPU limits
- Network isolation
- Filesystem restrictions

Supported language images:
- Scala: `hseeberger/scala-sbt:11.0.12-1.5.5_2.13.6`
- Java: `openjdk:11-jdk`
- Python: `python:3.9-slim`

## Development

### Adding a New Language

1. Update `LanguageConfig` in `CodeExecutionService`:
```scala
"new_language" -> LanguageConfig(
  extension = "ext",
  compileCmd = Some("compile command"),
  runCmd = "run command",
  baseImage = "docker/image:tag"
)
```

2. Add appropriate Docker image to your environment

### Adding New Problem Types

1. Extend the `Problem` model with new fields
2. Update the problem creation/update endpoints
3. Implement new test case validation logic

## Security

- All code execution is sandboxed in Docker containers
- Rate limiting on submissions
- Input validation and sanitization
- JWT-based authentication
- Role-based access control

## Monitoring

The platform can be monitored using:
- Prometheus for metrics
- Grafana for visualization
- Application logs
- MongoDB performance metrics

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.