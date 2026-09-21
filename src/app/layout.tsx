import type { Metadata, Viewport } from 'next'
import { Geist } from 'next/font/google'
import './globals.css'
import { Toaster } from '@/components/ui/toaster'
import { ThemeProvider } from 'next-themes'
import { QueryProvider } from '@/components/pinliao/QueryProvider'

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
})

export const metadata: Metadata = {
  title: '聘聊 · 招聘平台',
  description: '聘聊 - 求职者与招聘者的高效沟通平台',
  keywords: ['聘聊', '招聘', '求职', '找工作', '招聘平台'],
  authors: [{ name: 'Pinliao' }],
}

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#F2F5F5' },
    { media: '(prefers-color-scheme: dark)', color: '#0E1718' },
  ],
}

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className={`${geistSans.variable} antialiased`}>
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          <QueryProvider>{children}</QueryProvider>
        </ThemeProvider>
        <Toaster />
      </body>
    </html>
  )
}
