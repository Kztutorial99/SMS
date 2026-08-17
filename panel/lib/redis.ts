import { Redis } from '@upstash/redis'

export const redis = Redis.fromEnv()

export type Device = {
  id: string
  label: string
  android: string
  sims: string
  online: boolean
  lastSeen: number
}

export type SmsEvent = {
  id: string
  deviceId: string
  deviceLabel: string
  packageName: string
  appLabel: string
  title: string
  text: string
  android: string
  sims: string
  receivedAt: number
}

export function deviceKey(id: string) { return `sms:device:${id}` }
export function eventsKey(id: string) { return `sms:events:${id}` }
export function commandsKey(id: string) { return `sms:commands:${id}` }
