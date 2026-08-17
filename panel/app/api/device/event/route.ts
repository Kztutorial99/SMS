import { NextRequest, NextResponse } from 'next/server'
import { redis, deviceKey, eventsKey, type Device, type SmsEvent } from '@/lib/redis'

function authorized(req: NextRequest) { return !!process.env.DEVICE_API_TOKEN && req.headers.get('x-device-token') === process.env.DEVICE_API_TOKEN }

export async function POST(req: NextRequest) {
  if (!authorized(req)) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  const body = await req.json().catch(() => null)
  if (!body?.deviceId || !body?.text && !body?.title) return NextResponse.json({ error: 'Invalid payload' }, { status: 400 })

  const now = Date.now()
  const device: Device = { id: String(body.deviceId), label: String(body.deviceLabel || body.deviceId), android: String(body.android || ''), sims: String(body.sims || ''), online: true, lastSeen: now }
  const event: SmsEvent = { id: crypto.randomUUID(), deviceId: device.id, deviceLabel: device.label, packageName: String(body.packageName || ''), appLabel: String(body.appLabel || ''), title: String(body.title || ''), text: String(body.text || ''), android: device.android, sims: device.sims, receivedAt: now }

  await redis.set(deviceKey(device.id), device)
  await redis.lpush(eventsKey(device.id), event)
  await redis.ltrim(eventsKey(device.id), 0, 199)
  return NextResponse.json({ ok: true, id: event.id })
}
