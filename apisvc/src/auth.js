import express from 'express';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import db from './database.js';

const router = express.Router();

// JWT Configuration
const JWT_SECRET = process.env.JWT_SECRET || 'itau-dev-secret-key-change-in-production';
const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'itau-dev-refresh-secret-key-change-in-production';
const ACCESS_TOKEN_EXPIRY = '15m';  // 15 minutes
const REFRESH_TOKEN_EXPIRY = '7d';  // 7 days
const REFRESH_TOKEN_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000;

// Constants for security
const MAX_LOGIN_ATTEMPTS = 5;
const LOCK_TIME_MS = 15 * 60 * 1000; // 15 minutes

/**
 * Generate a unique JWT ID
 */
function generateJti() {
  return crypto.randomUUID();
}

/**
 * Generate access token
 */
function generateAccessToken(user, deviceId) {
  return jwt.sign(
    {
      sub: user.id.toString(),
      document: user.document,
      name: user.name,
      email: user.email,
      deviceId,
      jti: generateJti(),
      type: 'access'
    },
    JWT_SECRET,
    { expiresIn: ACCESS_TOKEN_EXPIRY }
  );
}

/**
 * Generate refresh token
 */
function generateRefreshToken(user, deviceId) {
  return jwt.sign(
    {
      sub: user.id.toString(),
      deviceId,
      jti: generateJti(),
      type: 'refresh'
    },
    JWT_REFRESH_SECRET,
    { expiresIn: REFRESH_TOKEN_EXPIRY }
  );
}

/**
 * Check if token is revoked
 */
function isTokenRevoked(jti) {
  const stmt = db.prepare('SELECT id FROM revoked_tokens WHERE token_jti = ?');
  const result = stmt.get(jti);
  return !!result;
}

/**
 * Clean up expired revoked tokens
 */
function cleanupExpiredTokens() {
  const now = Date.now();
  db.prepare('DELETE FROM revoked_tokens WHERE expires_at < ?').run(now);
  db.prepare('DELETE FROM sessions WHERE expires_at < ?').run(now);
}

// Run cleanup periodically (every hour)
setInterval(cleanupExpiredTokens, 60 * 60 * 1000);

/**
 * @swagger
 * /auth/login:
 *   post:
 *     summary: User login
 *     description: Authenticate user with document and password, returns JWT tokens
 *     tags: [Authentication]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - document
 *               - password
 *             properties:
 *               document:
 *                 type: string
 *                 description: User document (Chilean RUT)
 *                 example: "12.345.678-9"
 *               password:
 *                 type: string
 *                 description: User password
 *                 example: "password123"
 *               deviceId:
 *                 type: string
 *                 description: Device identifier
 *               deviceName:
 *                 type: string
 *                 description: Device name
 *               platform:
 *                 type: string
 *                 enum: [android, ios]
 *     responses:
 *       200:
 *         description: Login successful
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/AuthResponse'
 *       401:
 *         description: Invalid credentials
 *       423:
 *         description: Account locked
 */
router.post('/login', async (req, res) => {
  try {
    const { document, password, deviceId, deviceName, platform } = req.body;

    if (!document || !password) {
      return res.status(400).json({
        error: 'MISSING_CREDENTIALS',
        message: 'El documento y la contraseña son requeridos'
      });
    }

    // Find user by document
    const userStmt = db.prepare('SELECT * FROM users WHERE document = ?');
    const user = userStmt.get(document);

    if (!user) {
      return res.status(401).json({
        error: 'INVALID_CREDENTIALS',
        message: 'Documento o contraseña inválidos'
      });
    }

    // Check if account is locked
    if (user.locked_until && user.locked_until > Date.now()) {
      const remainingTime = Math.ceil((user.locked_until - Date.now()) / 60000);
      return res.status(423).json({
        error: 'ACCOUNT_LOCKED',
        message: `Cuenta bloqueada. Intenta de nuevo en ${remainingTime} minutos.`,
        lockedUntil: user.locked_until
      });
    }

    // Verify password
    const validPassword = await bcrypt.compare(password, user.password_hash);

    if (!validPassword) {
      // Increment failed attempts
      const newAttempts = user.failed_login_attempts + 1;
      let lockedUntil = null;

      if (newAttempts >= MAX_LOGIN_ATTEMPTS) {
        lockedUntil = Date.now() + LOCK_TIME_MS;
      }

      db.prepare(`
        UPDATE users
        SET failed_login_attempts = ?, locked_until = ?, updated_at = ?
        WHERE id = ?
      `).run(newAttempts, lockedUntil, Date.now(), user.id);

      if (lockedUntil) {
        return res.status(423).json({
          error: 'ACCOUNT_LOCKED',
          message: 'Demasiados intentos fallidos. Cuenta bloqueada por 15 minutos.',
          lockedUntil
        });
      }

      return res.status(401).json({
        error: 'INVALID_CREDENTIALS',
        message: 'Documento o contraseña inválidos',
        remainingAttempts: MAX_LOGIN_ATTEMPTS - newAttempts
      });
    }

    // Reset failed attempts on successful login
    db.prepare(`
      UPDATE users
      SET failed_login_attempts = 0, locked_until = NULL, updated_at = ?
      WHERE id = ?
    `).run(Date.now(), user.id);

    // Generate tokens
    const accessToken = generateAccessToken(user, deviceId);
    const refreshToken = generateRefreshToken(user, deviceId);

    // Store session
    const expiresAt = Date.now() + REFRESH_TOKEN_EXPIRY_MS;
    db.prepare(`
      INSERT INTO sessions (user_id, refresh_token, device_id, device_name, platform, ip_address, user_agent, expires_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      user.id,
      refreshToken,
      deviceId || null,
      deviceName || null,
      platform || null,
      req.ip,
      req.get('user-agent'),
      expiresAt
    );

    // Check if MFA is required
    const requiresMfa = user.mfa_enabled === 1;

    res.json({
      accessToken,
      refreshToken,
      expiresIn: 900, // 15 minutes in seconds
      refreshExpiresIn: 604800, // 7 days in seconds
      tokenType: 'Bearer',
      user: {
        id: user.id,
        document: user.document,
        name: user.name,
        email: user.email,
        phone: user.phone,
        mfaEnabled: user.mfa_enabled === 1,
        biometricEnabled: user.biometric_enabled === 1
      },
      requiresMfa,
      mfaType: requiresMfa ? 'totp' : null
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'LOGIN_FAILED', message: 'Ocurrió un error durante el inicio de sesión' });
  }
});

/**
 * @swagger
 * /auth/refresh:
 *   post:
 *     summary: Refresh access token
 *     description: Get a new access token using a valid refresh token
 *     tags: [Authentication]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - refreshToken
 *             properties:
 *               refreshToken:
 *                 type: string
 *                 description: Valid refresh token
 *     responses:
 *       200:
 *         description: Token refreshed successfully
 *       401:
 *         description: Invalid or expired refresh token
 */
router.post('/refresh', async (req, res) => {
  try {
    const { refreshToken } = req.body;

    if (!refreshToken) {
      return res.status(400).json({
        error: 'MISSING_TOKEN',
        message: 'El token de actualización es requerido'
      });
    }

    // Verify refresh token
    let decoded;
    try {
      decoded = jwt.verify(refreshToken, JWT_REFRESH_SECRET);
    } catch (err) {
      return res.status(401).json({
        error: 'INVALID_TOKEN',
        message: 'Token de actualización inválido o expirado'
      });
    }

    // Check if token is revoked
    if (isTokenRevoked(decoded.jti)) {
      return res.status(401).json({
        error: 'TOKEN_REVOKED',
        message: 'El token ha sido revocado'
      });
    }

    // Find session
    const sessionStmt = db.prepare('SELECT * FROM sessions WHERE refresh_token = ?');
    const session = sessionStmt.get(refreshToken);

    if (!session) {
      return res.status(401).json({
        error: 'SESSION_NOT_FOUND',
        message: 'Sesión no encontrada'
      });
    }

    if (session.expires_at < Date.now()) {
      // Clean up expired session
      db.prepare('DELETE FROM sessions WHERE id = ?').run(session.id);
      return res.status(401).json({
        error: 'SESSION_EXPIRED',
        message: 'La sesión ha expirado'
      });
    }

    // Get user
    const userStmt = db.prepare('SELECT * FROM users WHERE id = ?');
    const user = userStmt.get(session.user_id);

    if (!user) {
      return res.status(401).json({
        error: 'USER_NOT_FOUND',
        message: 'Usuario no encontrado'
      });
    }

    // Generate new access token
    const accessToken = generateAccessToken(user, session.device_id);

    res.json({
      accessToken,
      expiresIn: 900, // 15 minutes in seconds
      tokenType: 'Bearer'
    });
  } catch (error) {
    console.error('Token refresh error:', error);
    res.status(500).json({ error: 'REFRESH_FAILED', message: 'Ocurrió un error al actualizar el token' });
  }
});

/**
 * @swagger
 * /auth/logout:
 *   post:
 *     summary: User logout
 *     description: Invalidate the current session and revoke tokens
 *     tags: [Authentication]
 *     security:
 *       - bearerAuth: []
 *     requestBody:
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               refreshToken:
 *                 type: string
 *                 description: Refresh token to revoke
 *               allDevices:
 *                 type: boolean
 *                 description: Logout from all devices
 *     responses:
 *       200:
 *         description: Logout successful
 *       401:
 *         description: Unauthorized
 */
router.post('/logout', authenticateToken, async (req, res) => {
  try {
    const { refreshToken, allDevices } = req.body;
    const userId = req.user.sub;

    // Revoke current access token
    const accessTokenJti = req.user.jti;
    const accessTokenExp = req.user.exp * 1000; // Convert to ms

    db.prepare(`
      INSERT OR IGNORE INTO revoked_tokens (token_jti, user_id, expires_at)
      VALUES (?, ?, ?)
    `).run(accessTokenJti, userId, accessTokenExp);

    if (allDevices) {
      // Revoke all sessions for this user
      db.prepare('DELETE FROM sessions WHERE user_id = ?').run(userId);
    } else if (refreshToken) {
      // Revoke specific session
      db.prepare('DELETE FROM sessions WHERE refresh_token = ?').run(refreshToken);
    }

    res.json({ message: 'Sesión cerrada exitosamente' });
  } catch (error) {
    console.error('Logout error:', error);
    res.status(500).json({ error: 'LOGOUT_FAILED', message: 'Ocurrió un error al cerrar la sesión' });
  }
});

/**
 * @swagger
 * /auth/me:
 *   get:
 *     summary: Get current user profile
 *     description: Returns the authenticated user's profile information
 *     tags: [Authentication]
 *     security:
 *       - bearerAuth: []
 *     responses:
 *       200:
 *         description: User profile
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/User'
 *       401:
 *         description: Unauthorized
 */
router.get('/me', authenticateToken, (req, res) => {
  try {
    const userId = req.user.sub;

    const userStmt = db.prepare('SELECT * FROM users WHERE id = ?');
    const user = userStmt.get(userId);

    if (!user) {
      return res.status(404).json({
        error: 'USER_NOT_FOUND',
        message: 'Usuario no encontrado'
      });
    }

    res.json({
      id: user.id,
      document: user.document,
      name: user.name,
      email: user.email,
      phone: user.phone,
      mfaEnabled: user.mfa_enabled === 1,
      biometricEnabled: user.biometric_enabled === 1,
      createdAt: user.created_at
    });
  } catch (error) {
    console.error('Get profile error:', error);
    res.status(500).json({ error: 'PROFILE_FETCH_FAILED', message: 'Ocurrió un error al obtener el perfil' });
  }
});

/**
 * @swagger
 * /auth/validate:
 *   post:
 *     summary: Validate access token
 *     description: Check if the provided access token is valid
 *     tags: [Authentication]
 *     security:
 *       - bearerAuth: []
 *     responses:
 *       200:
 *         description: Token is valid
 *       401:
 *         description: Token is invalid or expired
 */
router.post('/validate', authenticateToken, (req, res) => {
  res.json({
    valid: true,
    userId: req.user.sub,
    expiresAt: req.user.exp * 1000
  });
});

/**
 * @swagger
 * /auth/register:
 *   post:
 *     summary: Register a new user
 *     description: Create a new user account
 *     tags: [Authentication]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - document
 *               - password
 *               - name
 *             properties:
 *               document:
 *                 type: string
 *               password:
 *                 type: string
 *               name:
 *                 type: string
 *               email:
 *                 type: string
 *               phone:
 *                 type: string
 *     responses:
 *       201:
 *         description: User created successfully
 *       400:
 *         description: Invalid input
 *       409:
 *         description: User already exists
 */
router.post('/register', async (req, res) => {
  try {
    const { document, password, name, email, phone } = req.body;

    if (!document || !password || !name) {
      return res.status(400).json({
        error: 'MISSING_FIELDS',
        message: 'El documento, la contraseña y el nombre son requeridos'
      });
    }

    // Check password strength
    if (password.length < 8) {
      return res.status(400).json({
        error: 'WEAK_PASSWORD',
        message: 'La contraseña debe tener al menos 8 caracteres'
      });
    }

    // Check if user exists
    const existingUser = db.prepare('SELECT id FROM users WHERE document = ?').get(document);
    if (existingUser) {
      return res.status(409).json({
        error: 'USER_EXISTS',
        message: 'Ya existe un usuario con este documento'
      });
    }

    // Hash password
    const saltRounds = 12;
    const passwordHash = await bcrypt.hash(password, saltRounds);

    // Create user
    const now = Date.now();
    const result = db.prepare(`
      INSERT INTO users (document, password_hash, name, email, phone, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `).run(document, passwordHash, name, email || null, phone || null, now, now);

    res.status(201).json({
      id: result.lastInsertRowid,
      document,
      name,
      email,
      phone,
      message: 'Usuario registrado exitosamente'
    });
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({ error: 'REGISTRATION_FAILED', message: 'Ocurrió un error durante el registro' });
  }
});

/**
 * @swagger
 * /auth/password/reset:
 *   post:
 *     summary: Request password reset
 *     description: Send password reset instructions to user's email
 *     tags: [Authentication]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - document
 *             properties:
 *               document:
 *                 type: string
 *     responses:
 *       200:
 *         description: Reset instructions sent (always returns success for security)
 */
router.post('/password/reset', (req, res) => {
  // Always return success for security (don't reveal if user exists)
  res.json({
    message: 'Si existe una cuenta con este documento, se enviarán las instrucciones para restablecer la contraseña.'
  });
});

/**
 * @swagger
 * /auth/mfa/verify:
 *   post:
 *     summary: Verify MFA code
 *     description: Verify a TOTP code for MFA-enabled accounts
 *     tags: [Authentication]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - code
 *               - tempToken
 *             properties:
 *               code:
 *                 type: string
 *                 description: 6-digit TOTP code
 *               tempToken:
 *                 type: string
 *                 description: Temporary token from login
 *     responses:
 *       200:
 *         description: MFA verified, returns full auth tokens
 *       401:
 *         description: Invalid MFA code
 */
router.post('/mfa/verify', (req, res) => {
  // Simplified MFA verification - in production, implement proper TOTP
  const { code, tempToken } = req.body;

  if (!code || !tempToken) {
    return res.status(400).json({
      error: 'MISSING_FIELDS',
      message: 'El código y el token temporal son requeridos'
    });
  }

  // For demo purposes, accept code "123456"
  if (code === '123456') {
    res.json({
      verified: true,
      message: 'Verificación MFA exitosa'
    });
  } else {
    res.status(401).json({
      error: 'INVALID_MFA_CODE',
      message: 'Código MFA inválido'
    });
  }
});

/**
 * @swagger
 * /auth/biometric:
 *   post:
 *     summary: Biometric login
 *     description: Authenticate using biometric credentials
 *     tags: [Authentication]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - userId
 *               - signature
 *             properties:
 *               userId:
 *                 type: integer
 *               signature:
 *                 type: string
 *                 description: Cryptographic signature from biometric auth
 *               challenge:
 *                 type: string
 *               deviceId:
 *                 type: string
 *     responses:
 *       200:
 *         description: Biometric login successful
 *       401:
 *         description: Biometric authentication failed
 */
router.post('/biometric', (req, res) => {
  try {
    const { userId, signature, challenge, deviceId } = req.body;

    if (!userId || !signature) {
      return res.status(400).json({
        error: 'MISSING_FIELDS',
        message: 'El userId y la firma son requeridos'
      });
    }

    // Get user
    const user = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);

    if (!user) {
      return res.status(401).json({
        error: 'USER_NOT_FOUND',
        message: 'Usuario no encontrado'
      });
    }

    if (user.biometric_enabled !== 1) {
      return res.status(401).json({
        error: 'BIOMETRIC_NOT_ENABLED',
        message: 'La autenticación biométrica no está habilitada para este usuario'
      });
    }

    // In production, verify the cryptographic signature against stored public key
    // For demo, accept any signature if biometric is enabled

    // Generate tokens
    const accessToken = generateAccessToken(user, deviceId);
    const refreshToken = generateRefreshToken(user, deviceId);

    // Store session
    const expiresAt = Date.now() + REFRESH_TOKEN_EXPIRY_MS;
    db.prepare(`
      INSERT INTO sessions (user_id, refresh_token, device_id, platform, ip_address, user_agent, expires_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `).run(user.id, refreshToken, deviceId || null, 'biometric', req.ip, req.get('user-agent'), expiresAt);

    res.json({
      accessToken,
      refreshToken,
      expiresIn: 900,
      refreshExpiresIn: 604800,
      tokenType: 'Bearer',
      user: {
        id: user.id,
        document: user.document,
        name: user.name,
        email: user.email,
        phone: user.phone,
        mfaEnabled: user.mfa_enabled === 1,
        biometricEnabled: true
      }
    });
  } catch (error) {
    console.error('Biometric login error:', error);
    res.status(500).json({ error: 'BIOMETRIC_LOGIN_FAILED', message: 'Ocurrió un error durante el inicio de sesión biométrico' });
  }
});

/**
 * @swagger
 * /auth/biometric/enroll:
 *   post:
 *     summary: Enroll biometric authentication
 *     description: Register a device for biometric authentication
 *     tags: [Authentication]
 *     security:
 *       - bearerAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - publicKey
 *             properties:
 *               publicKey:
 *                 type: string
 *                 description: Public key for biometric verification
 *               deviceId:
 *                 type: string
 *     responses:
 *       200:
 *         description: Biometric enrolled successfully
 *       401:
 *         description: Unauthorized
 */
router.post('/biometric/enroll', authenticateToken, (req, res) => {
  try {
    const { publicKey, deviceId } = req.body;
    const userId = req.user.sub;

    if (!publicKey) {
      return res.status(400).json({
        error: 'MISSING_PUBLIC_KEY',
        message: 'La clave pública es requerida'
      });
    }

    // Store the public key
    db.prepare(`
      UPDATE users
      SET biometric_enabled = 1, biometric_public_key = ?, updated_at = ?
      WHERE id = ?
    `).run(publicKey, Date.now(), userId);

    res.json({
      enrolled: true,
      message: 'Autenticación biométrica registrada exitosamente'
    });
  } catch (error) {
    console.error('Biometric enrollment error:', error);
    res.status(500).json({ error: 'ENROLLMENT_FAILED', message: 'Ocurrió un error durante el registro biométrico' });
  }
});

/**
 * Authentication middleware
 */
export function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({
      error: 'MISSING_TOKEN',
      message: 'El token de acceso es requerido'
    });
  }

  try {
    const decoded = jwt.verify(token, JWT_SECRET);

    // Check if token is revoked
    if (isTokenRevoked(decoded.jti)) {
      return res.status(401).json({
        error: 'TOKEN_REVOKED',
        message: 'El token ha sido revocado'
      });
    }

    req.user = decoded;
    next();
  } catch (err) {
    if (err.name === 'TokenExpiredError') {
      return res.status(401).json({
        error: 'TOKEN_EXPIRED',
        message: 'El token de acceso ha expirado'
      });
    }
    return res.status(401).json({
      error: 'INVALID_TOKEN',
      message: 'Token de acceso inválido'
    });
  }
}

export default router;
