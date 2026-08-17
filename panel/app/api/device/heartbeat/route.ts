import { NextRequest, NextResponse } from 'next/server'
import { redis, deviceKey, type Device } from '@/lib/redis'

export async function POST(req: NextRequest) {
  if (!process.env.DEVICE_API_TOKEN || req.headers.get('x-device-token') !== process.env.DEVICE_API_TOKEN) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  const b = await req.json().catch(() => null)
  if (!b?.deviceId) return NextResponse.json({ error: 'deviceId required' }, { status: 400 })
  const device: Device = { id: String(b.deviceId), label: String(b.deviceLabel || b.deviceId), android: String(b.android || ''), sims: String(b.sims || ''), online: true, lastSeen: Date.now() }
  await redis.set(deviceKey(device.id), device)
  await redis.sadd('sms:devices', device.id)
  return NextResponse.json({ ok: true })
}
