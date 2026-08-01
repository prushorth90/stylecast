/**
 * Pure, dependency-free date-grid math for the custom StyleCast calendar.
 *
 * Every function here operates on local calendar components (`getFullYear`/
 * `getMonth`/`getDate`/`setDate`/...) rather than raw millisecond
 * arithmetic - this is what keeps day-boundary math correct across a
 * daylight-saving transition (a day can be 23 or 25 hours long in the
 * browser's local time zone; adding "24 hours" in milliseconds would land
 * on the wrong calendar day or wrong time-of-day on those days, while
 * `setDate(getDate() + 1)` always lands on the next calendar day at the
 * same wall-clock time). Consistent with the rest of the app: no date
 * library, everything driven by the browser's local time zone via
 * `Intl`/`Date`.
 */

export function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function addDays(date: Date, amount: number): Date {
  const result = startOfDay(date);
  result.setDate(result.getDate() + amount);
  return result;
}

export function addMonths(date: Date, amount: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + amount, 1);
}

export function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** Sunday-based start of the week containing `date`. */
export function startOfWeek(date: Date): Date {
  const start = startOfDay(date);
  start.setDate(start.getDate() - start.getDay());
  return start;
}

export interface DateRange {
  start: Date;
  end: Date;
}

/**
 * The 42 (6x7) days making up the visible month grid for `anchorDate`'s
 * month - starts on the Sunday on or before the 1st, so leading/trailing
 * days from the adjacent months are included (shown dimmed by the caller,
 * but still real, clickable days).
 */
export function getMonthGridDays(anchorDate: Date): Date[] {
  const firstOfMonth = new Date(anchorDate.getFullYear(), anchorDate.getMonth(), 1);
  const gridStart = startOfWeek(firstOfMonth);
  return Array.from({ length: 42 }, (_, i) => addDays(gridStart, i));
}

/** The 7 days (Sunday-Saturday) making up the visible week for `anchorDate`. */
export function getWeekDays(anchorDate: Date): Date[] {
  const start = startOfWeek(anchorDate);
  return Array.from({ length: 7 }, (_, i) => addDays(start, i));
}

/** Fetch range covering the entire visible month grid (half-open: `[start, end)`). */
export function getMonthRange(anchorDate: Date): DateRange {
  const days = getMonthGridDays(anchorDate);
  return { start: days[0], end: addDays(days[days.length - 1], 1) };
}

/** Fetch range covering the entire visible week (half-open: `[start, end)`). */
export function getWeekRange(anchorDate: Date): DateRange {
  const days = getWeekDays(anchorDate);
  return { start: days[0], end: addDays(days[days.length - 1], 1) };
}

/** Fetch range for the upcoming/list view: from the start of `anchorDate`'s day through `days` days ahead. */
export function getUpcomingRange(anchorDate: Date, days = 60): DateRange {
  const start = startOfDay(anchorDate);
  return { start, end: addDays(start, days) };
}

export function formatMonthLabel(anchorDate: Date): string {
  return new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(anchorDate);
}

export function formatWeekRangeLabel(anchorDate: Date): string {
  const days = getWeekDays(anchorDate);
  const first = days[0];
  const last = days[days.length - 1];
  const sameMonth = first.getMonth() === last.getMonth() && first.getFullYear() === last.getFullYear();
  if (sameMonth) {
    const month = new Intl.DateTimeFormat(undefined, { month: 'short' }).format(first);
    return `${month} ${first.getDate()}\u2013${last.getDate()}, ${last.getFullYear()}`;
  }
  const monthDay = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' });
  const monthDayYear = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
  return `${monthDay.format(first)} \u2013 ${monthDayYear.format(last)}`;
}

/** A minimal event shape sufficient for the pure layout/filtering helpers below. */
export interface CalendarLikeEvent {
  start: string;
  end: string;
}

/** Whether `event`'s `[start, end)` interval overlaps the calendar day `day` at all. */
export function eventOverlapsDay(event: CalendarLikeEvent, day: Date): boolean {
  const dayStart = startOfDay(day);
  const dayEnd = addDays(dayStart, 1);
  const start = new Date(event.start);
  const end = new Date(event.end);
  return start < dayEnd && end > dayStart;
}

/** Whether `event` spans more than one local calendar day. */
export function isMultiDayEvent(event: CalendarLikeEvent): boolean {
  return !isSameDay(new Date(event.start), new Date(event.end));
}

export interface TimedEventLayout {
  /** Distance from the top of the day column, as a percentage of the full 24-hour height. */
  topPercent: number;
  /** Height within the day column, as a percentage of the full 24-hour height (clamped to a small visible minimum). */
  heightPercent: number;
}

const MINUTES_PER_DAY = 24 * 60;
const MIN_HEIGHT_PERCENT = 2;

/**
 * Computes a timed event's vertical position/size within one day's hourly
 * column, clamping to the day's boundaries for an event that starts before
 * or ends after this particular day (i.e. a multi-day timed event renders
 * as a full-height block on every day except the first/last).
 */
export function timedEventLayout(day: Date, event: CalendarLikeEvent): TimedEventLayout {
  const dayStart = startOfDay(day);
  const dayEnd = addDays(dayStart, 1);
  const eventStart = new Date(event.start);
  const eventEnd = new Date(event.end);
  const clampedStart = eventStart < dayStart ? dayStart : eventStart;
  const clampedEnd = eventEnd > dayEnd ? dayEnd : eventEnd;

  const startMinutes = (clampedStart.getTime() - dayStart.getTime()) / 60_000;
  const endMinutes = (clampedEnd.getTime() - dayStart.getTime()) / 60_000;

  const topPercent = (startMinutes / MINUTES_PER_DAY) * 100;
  const heightPercent = Math.max(((endMinutes - startMinutes) / MINUTES_PER_DAY) * 100, MIN_HEIGHT_PERCENT);
  return { topPercent, heightPercent };
}
