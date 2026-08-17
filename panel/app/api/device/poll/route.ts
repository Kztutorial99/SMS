import { NextRequest, NextResponse } from 'next/server'
import { redis, commandsKey } from '@/lib/redis'

export async function POST(req: NextRequest) {
  if (!process.env.DEVICE_API_TOKEN || req.headers.get('x-device-token') !== process.env.DEVICE_API_TOKEN) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  const b = await req.json().catch(() => null)
  if (!b?.deviceId) return NextResponse.json({ error: 'deviceId required' }, { status: 400 })
  const commands = await redis.lrange(commandsKey(String(b.deviceId)), 0, 20)
  if (commands.length) await redis.del(commandsKey(String(b.deviceId)))
  return NextResponse.json({ commands })
}
