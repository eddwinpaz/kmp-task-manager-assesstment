import swaggerJsdoc from 'swagger-jsdoc';

const options = {
  definition: {
    openapi: '3.0.0',
    info: {
      title: 'Itau Task Management API',
      version: '1.0.0',
      description: 'Backend API service for the Itau Task Management mobile application. Supports offline-first sync, CRUD operations for tasks, and push notifications.',
      contact: {
        name: 'API Support',
        email: 'support@itau.com'
      },
      license: {
        name: 'MIT',
        url: 'https://opensource.org/licenses/MIT'
      }
    },
    servers: [
      {
        url: 'http://localhost:3000',
        description: 'Development server'
      }
    ],
    tags: [
      {
        name: 'Health',
        description: 'Health check endpoints'
      },
      {
        name: 'Authentication',
        description: 'JWT authentication and user management'
      },
      {
        name: 'Tasks',
        description: 'Task management operations'
      },
      {
        name: 'Sync',
        description: 'Offline-first synchronization'
      },
      {
        name: 'Devices',
        description: 'Push notification device management'
      }
    ],
    components: {
      schemas: {
        Task: {
          type: 'object',
          properties: {
            id: {
              type: 'integer',
              description: 'Unique task identifier',
              example: 1
            },
            title: {
              type: 'string',
              description: 'Task title',
              example: 'Complete project documentation'
            },
            body: {
              type: 'string',
              description: 'Task description',
              example: 'Write comprehensive API documentation'
            },
            completed: {
              type: 'boolean',
              description: 'Task completion status',
              example: false
            },
            userId: {
              type: 'integer',
              description: 'User ID who owns the task',
              example: 1
            },
            created_at: {
              type: 'integer',
              description: 'Creation timestamp in milliseconds',
              example: 1704067200000
            },
            updated_at: {
              type: 'integer',
              description: 'Last update timestamp in milliseconds',
              example: 1704067200000
            }
          }
        },
        CreateTaskRequest: {
          type: 'object',
          required: ['title'],
          properties: {
            title: {
              type: 'string',
              description: 'Task title',
              example: 'New task'
            },
            body: {
              type: 'string',
              description: 'Task description',
              example: 'Task description here'
            },
            userId: {
              type: 'integer',
              description: 'User ID',
              example: 1
            }
          }
        },
        UpdateTaskRequest: {
          type: 'object',
          properties: {
            title: {
              type: 'string',
              description: 'Task title',
              example: 'Updated task title'
            },
            body: {
              type: 'string',
              description: 'Task description',
              example: 'Updated description'
            },
            completed: {
              type: 'boolean',
              description: 'Task completion status',
              example: true
            }
          }
        },
        SyncOperation: {
          type: 'object',
          required: ['type'],
          properties: {
            type: {
              type: 'string',
              enum: ['CREATE', 'UPDATE', 'DELETE'],
              description: 'Type of sync operation'
            },
            localId: {
              type: 'string',
              description: 'Local ID for CREATE operations',
              example: 'local-123456'
            },
            serverId: {
              type: 'integer',
              description: 'Server ID for UPDATE/DELETE operations',
              example: 1
            },
            data: {
              type: 'object',
              description: 'Task data for CREATE/UPDATE operations',
              properties: {
                title: { type: 'string' },
                body: { type: 'string' },
                completed: { type: 'boolean' },
                userId: { type: 'integer' }
              }
            }
          }
        },
        SyncRequest: {
          type: 'object',
          required: ['operations'],
          properties: {
            operations: {
              type: 'array',
              items: {
                $ref: '#/components/schemas/SyncOperation'
              }
            }
          }
        },
        SyncResult: {
          type: 'object',
          properties: {
            localId: {
              type: 'string',
              description: 'Local ID (for CREATE operations)'
            },
            serverId: {
              type: 'integer',
              description: 'Server ID'
            },
            status: {
              type: 'string',
              enum: ['success', 'error'],
              description: 'Operation result status'
            },
            error: {
              type: 'string',
              description: 'Error message if status is error'
            }
          }
        },
        SyncResponse: {
          type: 'object',
          properties: {
            results: {
              type: 'array',
              items: {
                $ref: '#/components/schemas/SyncResult'
              }
            }
          }
        },
        DeviceRegistration: {
          type: 'object',
          required: ['token', 'platform'],
          properties: {
            token: {
              type: 'string',
              description: 'Device push notification token',
              example: 'fcm-token-abc123...'
            },
            platform: {
              type: 'string',
              enum: ['ios', 'android'],
              description: 'Device platform',
              example: 'android'
            },
            userId: {
              type: 'integer',
              description: 'User ID to associate with device',
              example: 1
            }
          }
        },
        HealthResponse: {
          type: 'object',
          properties: {
            status: {
              type: 'string',
              example: 'ok'
            },
            timestamp: {
              type: 'integer',
              description: 'Current server timestamp',
              example: 1704067200000
            }
          }
        },
        Error: {
          type: 'object',
          properties: {
            error: {
              type: 'string',
              description: 'Error message',
              example: 'Task not found'
            }
          }
        },
        User: {
          type: 'object',
          properties: {
            id: {
              type: 'integer',
              description: 'User ID',
              example: 1
            },
            document: {
              type: 'string',
              description: 'User document (CPF)',
              example: '12345678901'
            },
            name: {
              type: 'string',
              description: 'User full name',
              example: 'João Silva'
            },
            email: {
              type: 'string',
              description: 'User email',
              example: 'joao@example.com'
            },
            phone: {
              type: 'string',
              description: 'User phone number',
              example: '+5511999999999'
            },
            mfaEnabled: {
              type: 'boolean',
              description: 'Whether MFA is enabled',
              example: false
            },
            biometricEnabled: {
              type: 'boolean',
              description: 'Whether biometric login is enabled',
              example: true
            }
          }
        },
        AuthResponse: {
          type: 'object',
          properties: {
            accessToken: {
              type: 'string',
              description: 'JWT access token',
              example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...'
            },
            refreshToken: {
              type: 'string',
              description: 'JWT refresh token',
              example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...'
            },
            expiresIn: {
              type: 'integer',
              description: 'Access token expiry in seconds',
              example: 900
            },
            refreshExpiresIn: {
              type: 'integer',
              description: 'Refresh token expiry in seconds',
              example: 604800
            },
            tokenType: {
              type: 'string',
              description: 'Token type',
              example: 'Bearer'
            },
            user: {
              $ref: '#/components/schemas/User'
            },
            requiresMfa: {
              type: 'boolean',
              description: 'Whether MFA verification is required',
              example: false
            },
            mfaType: {
              type: 'string',
              description: 'MFA type if required',
              enum: ['totp', 'sms', 'email'],
              example: 'totp'
            }
          }
        },
        LoginRequest: {
          type: 'object',
          required: ['document', 'password'],
          properties: {
            document: {
              type: 'string',
              description: 'User document (CPF)',
              example: '12345678901'
            },
            password: {
              type: 'string',
              description: 'User password',
              example: 'password123'
            },
            deviceId: {
              type: 'string',
              description: 'Device identifier'
            },
            deviceName: {
              type: 'string',
              description: 'Device name'
            },
            platform: {
              type: 'string',
              enum: ['android', 'ios'],
              description: 'Device platform'
            }
          }
        },
        RefreshTokenRequest: {
          type: 'object',
          required: ['refreshToken'],
          properties: {
            refreshToken: {
              type: 'string',
              description: 'Valid refresh token'
            }
          }
        },
        TokenResponse: {
          type: 'object',
          properties: {
            accessToken: {
              type: 'string',
              description: 'New JWT access token'
            },
            expiresIn: {
              type: 'integer',
              description: 'Token expiry in seconds',
              example: 900
            },
            tokenType: {
              type: 'string',
              example: 'Bearer'
            }
          }
        }
      },
      securitySchemes: {
        bearerAuth: {
          type: 'http',
          scheme: 'bearer',
          bearerFormat: 'JWT',
          description: 'JWT access token'
        }
      }
    }
  },
  apis: ['./src/index.js', './src/auth.js']
};

export const swaggerSpec = swaggerJsdoc(options);
