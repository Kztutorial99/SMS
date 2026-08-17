import { NextResponse } from 'next/server'
import { isAdmin } from '@/lib/auth'
import { redis, deviceKey, eventsKey, type Device, type SmsEvent } from '@/lib/redis'

export async function GET() {
  if (!(await isAdmin())) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  const ids = await redis.smembers<string[]>('sms:devices')
  const devices: Device[] = []
  const events: SmsEvent[] = []
  for (const id of ids) {
    const d = await redis.get<Device>(deviceKey(id))
    if (d) devices.push({ ...d, online: Date.now() - d.lastSeen < 60_000 })
    const e = await redis.lrange<SmsEvent>(eventsKey(id), 0, 49)
    events.push(...e)
  }
  events.sort((a, b) => b.receivedAt - a.receivedAt)
  return NextResponse.json({ devices, events: events.slice(0, 100) })
}
