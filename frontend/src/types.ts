export interface Business { id: string; slug: string; name: string; category: string; zoneId: string; address: string }
export interface Service { id: string; name: string; description: string; durationMinutes: number; priceCents: number }
export interface Professional { id: string; name: string; specialty: string; serviceIds: string[] }
export interface Catalog { business: Business; services: Service[]; professionals: Professional[]; suggestedDate: string; horizonDays: number }
export interface Slot { startsAt: string; endsAt: string }
export interface Availability { date: string; zoneId: string; slots: Slot[] }
export interface Receipt extends Slot { reference: string; serviceName: string; professionalName: string; priceCents: number; customerAccessToken?: string | null; customerAccessValidUntil?: string | null }
export interface Appointment extends Receipt { id: string; professionalId: string; serviceId: string; customerName: string; customerPhone: string; status: 'BOOKED' | 'CANCELLED'; version: number }
export interface Block extends Slot { id: string; professionalId: string; reason: string }
export interface DayAgenda { date: string; zoneId: string; appointments: Appointment[]; blocks: Block[] }
export interface BusinessSetup { business: Business; published: boolean; version: number; services: number; professionals: number; hasHours: boolean; emailVerified?: boolean; verificationRequired?: boolean; verificationSimulated?: boolean; horizonDays?: number; ready: boolean; hasBookableProfessional: boolean }
export interface CurrentUser { name: string; email: string; business: Business }

export interface WeeklyWindow { weekday: number; opensAt: string; closesAt: string }
export interface ProfessionalConfiguration extends Professional { active: boolean; version: number; hours: WeeklyWindow[] }

export interface DayWindow { opensAt: string; closesAt: string }
export interface DateException { date: string; reason: string; hours: DayWindow[] }
export interface DateCalendar { version: number; firstDate: string; lastDate: string; exceptions: DateException[]; weeklyHours: WeeklyWindow[] }