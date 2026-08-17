import { NextRequest, NextResponse } from 'next/server'
import { isAdmin } from '@/lib/auth'
import { redis, commandsKey } from '@/lib/redis'

const allowed = new Set(['forwarding_enabled'])

export async function POST(req: NextRequest) {
  if (!(await isAdmin())) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  const b = await req.json().catch(() => null)
  if (!b?.deviceId || !allowed.has(String(b?.type))) return NextResponse.json({ error: 'Invalid command' }, { status: 400 })
  const command = { id: crypto.randomUUID(), type: String(b.type), value: Boolean(b.value), createdAt: Date.now() }
  await redis.lpush(commandsKey(String(b.deviceId)), command)
  return NextResponse.json({ ok: true, command })
}
