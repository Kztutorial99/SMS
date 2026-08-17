import { SignJWT, jwtVerify } from 'jose'
import { cookies } from 'next/headers'

const secret = new TextEncoder().encode(process.env.SESSION_SECRET || 'change-me-in-vercel')
const COOKIE = 'sms_panel_session'

export async function createSession() {
  const token = await new SignJWT({ role: 'admin' }).setProtectedHeader({ alg: 'HS256' }).setIssuedAt().setExpirationTime('7d').sign(secret)
  const jar = await cookies()
  jar.set(COOKIE, token, { httpOnly: true, secure: true, sameSite: 'lax', path: '/', maxAge: 60 * 60 * 24 * 7 })
}

export async function isAdmin() {
  try {
    const token = (await cookies()).get(COOKIE)?.value
    if (!token) return false
    const { payload } = await jwtVerify(token, secret)
    return payload.role === 'admin'
  } catch { return false }
}

export async function destroySession() {
  (await cookies()).delete(COOKIE)
}
