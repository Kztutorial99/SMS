import { NextRequest, NextResponse } from 'next/server'
import { createSession } from '@/lib/auth'

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => ({}))
  if (!process.env.ADMIN_PASSWORD || body.password !== process.env.ADMIN_PASSWORD) return NextResponse.json({ error: 'Invalid credentials' }, { status: 401 })
  await createSession()
  return NextResponse.json({ ok: true })
}
