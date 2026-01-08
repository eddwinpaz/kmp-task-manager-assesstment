import express from 'express';
import cors from 'cors';
import swaggerUi from 'swagger-ui-express';
import db from './database.js';
import { sendPushNotification } from './notifications.js';
import { swaggerSpec } from './swagger.js';
import authRoutes, { authenticateToken } from './auth.js';

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());

// Auth routes
app.use('/auth', authRoutes);

// Swagger UI
app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec, {
  customCss: '.swagger-ui .topbar { display: none }',
  customSiteTitle: 'Itau Task API Documentation'
}));

// Serve Swagger JSON
app.get('/api-docs.json', (req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.send(swaggerSpec);
});

// Request logging
app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.path}`);
  next();
});

/**
 * @swagger
 * /health:
 *   get:
 *     summary: Health check endpoint
 *     description: Returns the health status of the API server
 *     tags: [Health]
 *     responses:
 *       200:
 *         description: Server is healthy
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/HealthResponse'
 */
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: Date.now() });
});

/**
 * @swagger
 * /todos:
 *   get:
 *     summary: Get all tasks
 *     description: Retrieves a paginated list of tasks for a user, ordered by creation date (newest first)
 *     tags: [Tasks]
 *     parameters:
 *       - in: query
 *         name: _limit
 *         schema:
 *           type: integer
 *           default: 20
 *         description: Maximum number of tasks to return per page
 *       - in: query
 *         name: _offset
 *         schema:
 *           type: integer
 *           default: 0
 *         description: Number of tasks to skip (for pagination)
 *       - in: query
 *         name: userId
 *         schema:
 *           type: integer
 *           default: 1
 *         description: Filter tasks by user ID
 *     responses:
 *       200:
 *         description: Paginated list of tasks
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 data:
 *                   type: array
 *                   items:
 *                     $ref: '#/components/schemas/Task'
 *                 pagination:
 *                   type: object
 *                   properties:
 *                     total:
 *                       type: integer
 *                     limit:
 *                       type: integer
 *                     offset:
 *                       type: integer
 *                     hasMore:
 *                       type: boolean
 *       500:
 *         description: Server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 */
app.get('/todos', (req, res) => {
  try {
    const limit = parseInt(req.query._limit) || 20;
    const offset = parseInt(req.query._offset) || 0;
    const userId = parseInt(req.query.userId) || 1;

    // Get total count
    const countStmt = db.prepare('SELECT COUNT(*) as total FROM tasks WHERE user_id = ?');
    const { total } = countStmt.get(userId);

    // Get paginated tasks
    const stmt = db.prepare(`
      SELECT id, title, body, completed, user_id as userId, created_at, updated_at
      FROM tasks
      WHERE user_id = ?
      ORDER BY created_at DESC
      LIMIT ? OFFSET ?
    `);

    const tasks = stmt.all(userId, limit, offset).map(task => ({
      ...task,
      completed: task.completed === 1
    }));

    res.json({
      data: tasks,
      pagination: {
        total,
        limit,
        offset,
        hasMore: offset + tasks.length < total
      }
    });
  } catch (error) {
    console.error('Error fetching tasks:', error);
    res.status(500).json({ error: 'Failed to fetch tasks' });
  }
});

/**
 * @swagger
 * /todos/{id}:
 *   get:
 *     summary: Get a single task
 *     description: Retrieves a specific task by its ID
 *     tags: [Tasks]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: Task ID
 *     responses:
 *       200:
 *         description: Task details
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Task'
 *       404:
 *         description: Task not found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 *       500:
 *         description: Server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 */
app.get('/todos/:id', (req, res) => {
  try {
    const { id } = req.params;

    const stmt = db.prepare(`
      SELECT id, title, body, completed, user_id as userId, created_at, updated_at
      FROM tasks
      WHERE id = ?
    `);

    const task = stmt.get(id);

    if (!task) {
      return res.status(404).json({ error: 'Task not found' });
    }

    res.json({
      ...task,
      completed: task.completed === 1
    });
  } catch (error) {
    console.error('Error fetching task:', error);
    res.status(500).json({ error: 'Failed to fetch task' });
  }
});

/**
 * @swagger
 * /todos:
 *   post:
 *     summary: Create a new task
 *     description: Creates a new task and optionally sends a push notification
 *     tags: [Tasks]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             $ref: '#/components/schemas/CreateTaskRequest'
 *     responses:
 *       201:
 *         description: Task created successfully
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Task'
 *       400:
 *         description: Invalid request (missing title)
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 *       500:
 *         description: Server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 */
app.post('/todos', async (req, res) => {
  try {
    const { title, body = '', userId = 1 } = req.body;

    if (!title) {
      return res.status(400).json({ error: 'Title is required' });
    }

    const now = Date.now();
    const stmt = db.prepare(`
      INSERT INTO tasks (title, body, user_id, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?)
    `);

    const result = stmt.run(title, body, userId, now, now);

    const newTask = {
      id: result.lastInsertRowid,
      title,
      body,
      completed: false,
      userId,
      created_at: now,
      updated_at: now
    };

    // Send push notification for new task
    await sendPushNotification(userId, {
      title: 'New Task Created',
      body: `Task "${title}" has been created`
    });

    res.status(201).json(newTask);
  } catch (error) {
    console.error('Error creating task:', error);
    res.status(500).json({ error: 'Failed to create task' });
  }
});

/**
 * @swagger
 * /todos/{id}:
 *   put:
 *     summary: Update a task
 *     description: Updates an existing task. Sends a push notification when task is completed.
 *     tags: [Tasks]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: Task ID
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             $ref: '#/components/schemas/UpdateTaskRequest'
 *     responses:
 *       200:
 *         description: Task updated successfully
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Task'
 *       404:
 *         description: Task not found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 *       500:
 *         description: Server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 */
app.put('/todos/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const { title, body, completed } = req.body;

    // Check if task exists
    const existingStmt = db.prepare('SELECT * FROM tasks WHERE id = ?');
    const existing = existingStmt.get(id);

    if (!existing) {
      return res.status(404).json({ error: 'Task not found' });
    }

    const now = Date.now();
    const stmt = db.prepare(`
      UPDATE tasks
      SET title = ?, body = ?, completed = ?, updated_at = ?
      WHERE id = ?
    `);

    stmt.run(
      title ?? existing.title,
      body ?? existing.body,
      completed !== undefined ? (completed ? 1 : 0) : existing.completed,
      now,
      id
    );

    const updatedTask = {
      id: parseInt(id),
      title: title ?? existing.title,
      body: body ?? existing.body,
      completed: completed !== undefined ? completed : existing.completed === 1,
      userId: existing.user_id,
      created_at: existing.created_at,
      updated_at: now
    };

    // Send push notification if task was completed
    if (completed === true && existing.completed === 0) {
      await sendPushNotification(existing.user_id, {
        title: 'Task Completed',
        body: `Task "${updatedTask.title}" has been marked as complete`
      });
    }

    res.json(updatedTask);
  } catch (error) {
    console.error('Error updating task:', error);
    res.status(500).json({ error: 'Failed to update task' });
  }
});

/**
 * @swagger
 * /todos/{id}:
 *   delete:
 *     summary: Delete a task
 *     description: Permanently deletes a task
 *     tags: [Tasks]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: Task ID
 *     responses:
 *       204:
 *         description: Task deleted successfully
 *       404:
 *         description: Task not found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 *       500:
 *         description: Server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 */
app.delete('/todos/:id', (req, res) => {
  try {
    const { id } = req.params;

    const stmt = db.prepare('DELETE FROM tasks WHERE id = ?');
    const result = stmt.run(id);

    if (result.changes === 0) {
      return res.status(404).json({ error: 'Task not found' });
    }

    res.status(204).send();
  } catch (error) {
    console.error('Error deleting task:', error);
    res.status(500).json({ error: 'Failed to delete task' });
  }
});

/**
 * @swagger
 * /sync:
 *   post:
 *     summary: Batch sync operations
 *     description: |
 *       Synchronizes multiple task operations in a single request.
 *       Supports CREATE, UPDATE, and DELETE operations for offline-first sync.
 *
 *       **Operation Types:**
 *       - `CREATE`: Creates a new task. Requires `localId` and `data` fields.
 *       - `UPDATE`: Updates an existing task. Requires `serverId` and `data` fields.
 *       - `DELETE`: Deletes a task. Requires `serverId` field.
 *     tags: [Sync]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             $ref: '#/components/schemas/SyncRequest'
 *           example:
 *             operations:
 *               - type: CREATE
 *                 localId: "local-123"
 *                 data:
 *                   title: "New task"
 *                   body: "Task description"
 *               - type: UPDATE
 *                 serverId: 1
 *                 data:
 *                   completed: true
 *               - type: DELETE
 *                 serverId: 2
 *     responses:
 *       200:
 *         description: Sync results
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/SyncResponse'
 *       500:
 *         description: Server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 */
app.post('/sync', async (req, res) => {
  try {
    const { operations } = req.body;
    const results = [];

    for (const op of operations) {
      try {
        switch (op.type) {
          case 'CREATE': {
            const now = Date.now();
            const stmt = db.prepare(`
              INSERT INTO tasks (title, body, completed, user_id, created_at, updated_at)
              VALUES (?, ?, ?, ?, ?, ?)
            `);
            const result = stmt.run(
              op.data.title,
              op.data.body || '',
              op.data.completed ? 1 : 0,
              op.data.userId || 1,
              now,
              now
            );
            results.push({
              localId: op.localId,
              serverId: result.lastInsertRowid,
              status: 'success'
            });
            break;
          }
          case 'UPDATE': {
            const now = Date.now();
            const stmt = db.prepare(`
              UPDATE tasks
              SET title = ?, body = ?, completed = ?, updated_at = ?
              WHERE id = ?
            `);
            stmt.run(
              op.data.title,
              op.data.body || '',
              op.data.completed ? 1 : 0,
              now,
              op.serverId
            );
            results.push({
              serverId: op.serverId,
              status: 'success'
            });
            break;
          }
          case 'DELETE': {
            const stmt = db.prepare('DELETE FROM tasks WHERE id = ?');
            stmt.run(op.serverId);
            results.push({
              serverId: op.serverId,
              status: 'success'
            });
            break;
          }
        }
      } catch (opError) {
        results.push({
          localId: op.localId,
          serverId: op.serverId,
          status: 'error',
          error: opError.message
        });
      }
    }

    res.json({ results });
  } catch (error) {
    console.error('Error syncing:', error);
    res.status(500).json({ error: 'Failed to sync' });
  }
});

/**
 * @swagger
 * /devices/register:
 *   post:
 *     summary: Register a device for push notifications
 *     description: Registers a device token to receive push notifications for task updates
 *     tags: [Devices]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             $ref: '#/components/schemas/DeviceRegistration'
 *     responses:
 *       201:
 *         description: Device registered successfully
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Device registered successfully
 *       400:
 *         description: Invalid request
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 *       500:
 *         description: Server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 */
app.post('/devices/register', (req, res) => {
  try {
    const { token, platform, userId = 1 } = req.body;

    if (!token || !platform) {
      return res.status(400).json({ error: 'Token and platform are required' });
    }

    const stmt = db.prepare(`
      INSERT OR REPLACE INTO device_tokens (token, platform, user_id, created_at)
      VALUES (?, ?, ?, ?)
    `);

    stmt.run(token, platform, userId, Date.now());

    res.status(201).json({ message: 'Device registered successfully' });
  } catch (error) {
    console.error('Error registering device:', error);
    res.status(500).json({ error: 'Failed to register device' });
  }
});

/**
 * @swagger
 * /devices/{token}:
 *   delete:
 *     summary: Unregister a device
 *     description: Removes a device token from push notification list
 *     tags: [Devices]
 *     parameters:
 *       - in: path
 *         name: token
 *         required: true
 *         schema:
 *           type: string
 *         description: Device push notification token
 *     responses:
 *       204:
 *         description: Device unregistered successfully
 *       500:
 *         description: Server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/Error'
 */
app.delete('/devices/:token', (req, res) => {
  try {
    const { token } = req.params;

    const stmt = db.prepare('DELETE FROM device_tokens WHERE token = ?');
    stmt.run(token);

    res.status(204).send();
  } catch (error) {
    console.error('Error unregistering device:', error);
    res.status(500).json({ error: 'Failed to unregister device' });
  }
});

// Error handling middleware
app.use((err, req, res, next) => {
  console.error('Unhandled error:', err);
  res.status(500).json({ error: 'Internal server error' });
});

// Start server
app.listen(PORT, () => {
  console.log(`API Server running on http://localhost:${PORT}`);
  console.log(`Swagger UI available at http://localhost:${PORT}/api-docs`);
  console.log('');
  console.log('Available endpoints:');
  console.log('  GET    /health');
  console.log('');
  console.log('  Authentication:');
  console.log('  POST   /auth/login');
  console.log('  POST   /auth/register');
  console.log('  POST   /auth/refresh');
  console.log('  POST   /auth/logout');
  console.log('  GET    /auth/me');
  console.log('  POST   /auth/validate');
  console.log('  POST   /auth/mfa/verify');
  console.log('  POST   /auth/biometric');
  console.log('  POST   /auth/biometric/enroll');
  console.log('  POST   /auth/password/reset');
  console.log('');
  console.log('  Tasks:');
  console.log('  GET    /todos');
  console.log('  GET    /todos/:id');
  console.log('  POST   /todos');
  console.log('  PUT    /todos/:id');
  console.log('  DELETE /todos/:id');
  console.log('  POST   /sync');
  console.log('');
  console.log('  Devices:');
  console.log('  POST   /devices/register');
  console.log('  DELETE /devices/:token');
});
