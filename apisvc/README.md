# Itau Task API Service

Backend API service for the Itau Task Management mobile app.

## Features

- RESTful API for task management (CRUD operations)
- SQLite database for persistence
- Offline-first sync endpoint for batch operations
- Push notification support (Firebase Cloud Messaging)
- Device registration for notifications
- **Swagger UI** for interactive API documentation

## Getting Started

### Prerequisites

- Node.js 18+
- npm or yarn

### Installation

```bash
cd apisvc
npm install
```

### Running the Server

```bash
# Development mode (with auto-reload)
npm run dev

# Production mode
npm start
```

The server will start on `http://localhost:3000`

## API Documentation (Swagger)

Interactive API documentation is available at:

- **Swagger UI**: http://localhost:3000/api-docs
- **OpenAPI JSON**: http://localhost:3000/api-docs.json

The Swagger UI allows you to:
- Browse all available endpoints
- View request/response schemas
- Test API endpoints directly from the browser

## API Endpoints

### Health Check

```
GET /health
```

### Tasks

```
GET    /todos          - Get all tasks (query: _limit, userId)
GET    /todos/:id      - Get single task
POST   /todos          - Create task
PUT    /todos/:id      - Update task
DELETE /todos/:id      - Delete task
```

### Sync (Offline-First)

```
POST   /sync           - Batch sync operations
```

Request body:
```json
{
  "operations": [
    {
      "type": "CREATE",
      "localId": "local-123",
      "data": { "title": "Task", "body": "Description" }
    },
    {
      "type": "UPDATE",
      "serverId": 1,
      "data": { "title": "Updated", "completed": true }
    },
    {
      "type": "DELETE",
      "serverId": 2
    }
  ]
}
```

### Device Registration (Push Notifications) (Pending)

```
POST   /devices/register    - Register device for push notifications
DELETE /devices/:token      - Unregister device
```

## Push Notifications (Pending)

To enable Firebase push notifications:

1. Create a Firebase project at https://console.firebase.google.com
2. Generate a service account key (Project Settings > Service Accounts)
3. Save the JSON file as `firebase-service-account.json` in the apisvc directory
4. Uncomment the Firebase initialization code in `src/notifications.js`

## Task Data Model

```json
{
  "id": 1,
  "title": "Task title",
  "body": "Task description",
  "completed": false,
  "userId": 1,
  "created_at": 1704067200000,
  "updated_at": 1704067200000
}
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| PORT | 3000 | Server port |
