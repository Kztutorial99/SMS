import './globals.css'
import type { Metadata } from 'next'

export const metadata: Metadata = { title: 'SMS Control', description: 'Private SMS notification control panel' }
export default function RootLayout({ children }: { children: React.ReactNode }) { return <html lang="id"><body>{children}</body></html> }
