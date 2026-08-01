import { describe, expect, it } from 'vitest';
import {
  addDays,
  addMonths,
  eventOverlapsDay,
  formatMonthLabel,
  formatWeekRangeLabel,
  getMonthGridDays,
  getMonthRange,
  getUpcomingRange,
  getWeekDays,
  getWeekRange,
  isMultiDayEvent,
  isSameDay,
  startOfDay,
  startOfWeek,
  timedEventLayout,
} from './calendarDateUtils';

describe('calendarDateUtils', () => {
  describe('startOfDay', () => {
    it('truncates to local midnight, including right at the day boundary', () => {
      expect(startOfDay(new Date(2026, 7, 15, 23, 59, 59))).toEqual(new Date(2026, 7, 15, 0, 0, 0));
      expect(startOfDay(new Date(2026, 7, 15, 0, 0, 0))).toEqual(new Date(2026, 7, 15, 0, 0, 0));
    });
  });

  describe('addDays', () => {
    it('advances by whole calendar days using component-based arithmetic (DST-safe)', () => {
      // 2026-03-08 is the actual US spring-forward DST transition date - exercised here
      // regardless of the test runner's own time zone, since addDays never does raw
      // millisecond arithmetic (which would be wrong on a 23-hour DST day).
      const dstDay = new Date(2026, 2, 7);
      expect(addDays(dstDay, 1)).toEqual(new Date(2026, 2, 8));
      expect(addDays(dstDay, 2)).toEqual(new Date(2026, 2, 9));
    });

    it('rolls over month and year boundaries correctly', () => {
      expect(addDays(new Date(2026, 11, 31), 1)).toEqual(new Date(2027, 0, 1));
    });
  });

  describe('addMonths', () => {
    it('rolls over to January of the next year', () => {
      expect(addMonths(new Date(2026, 11, 15), 1)).toEqual(new Date(2027, 0, 1));
    });
  });

  describe('isSameDay', () => {
    it('is true only for the same calendar day, ignoring time-of-day', () => {
      expect(isSameDay(new Date(2026, 7, 15, 1, 0), new Date(2026, 7, 15, 23, 0))).toBe(true);
      expect(isSameDay(new Date(2026, 7, 15, 23, 59), new Date(2026, 7, 16, 0, 0))).toBe(false);
    });
  });

  describe('startOfWeek', () => {
    it('returns the preceding (or same) Sunday', () => {
      // 2026-08-19 is a Wednesday.
      expect(startOfWeek(new Date(2026, 7, 19))).toEqual(new Date(2026, 7, 16));
      // A Sunday maps to itself.
      expect(startOfWeek(new Date(2026, 7, 16))).toEqual(new Date(2026, 7, 16));
    });
  });

  describe('getMonthGridDays', () => {
    it('returns 42 days starting on the Sunday on/before the 1st', () => {
      const days = getMonthGridDays(new Date(2026, 7, 15)); // August 2026 starts on a Saturday
      expect(days).toHaveLength(42);
      expect(days[0].getDay()).toBe(0);
      expect(days[0]).toEqual(new Date(2026, 6, 26));
      expect(days[41]).toEqual(new Date(2026, 8, 5));
    });
  });

  describe('getWeekDays', () => {
    it('returns exactly 7 consecutive days starting on Sunday', () => {
      const days = getWeekDays(new Date(2026, 7, 19));
      expect(days).toHaveLength(7);
      expect(days.map((d) => d.getDay())).toEqual([0, 1, 2, 3, 4, 5, 6]);
      expect(days[0]).toEqual(new Date(2026, 7, 16));
    });
  });

  describe('getMonthRange / getWeekRange / getUpcomingRange', () => {
    it('produces a half-open [start, end) range covering the whole grid', () => {
      const monthRange = getMonthRange(new Date(2026, 7, 15));
      expect(monthRange.start).toEqual(new Date(2026, 6, 26));
      expect(monthRange.end).toEqual(new Date(2026, 8, 6));

      const weekRange = getWeekRange(new Date(2026, 7, 19));
      expect(weekRange.start).toEqual(new Date(2026, 7, 16));
      expect(weekRange.end).toEqual(new Date(2026, 7, 23));

      const upcomingRange = getUpcomingRange(new Date(2026, 7, 15, 14, 30), 10);
      expect(upcomingRange.start).toEqual(new Date(2026, 7, 15));
      expect(upcomingRange.end).toEqual(new Date(2026, 7, 25));
    });
  });

  describe('formatMonthLabel / formatWeekRangeLabel', () => {
    it('formats a month label', () => {
      expect(formatMonthLabel(new Date(2026, 7, 15))).toContain('2026');
    });

    it('formats a week range label spanning two months', () => {
      const label = formatWeekRangeLabel(new Date(2026, 7, 30));
      expect(label).toContain('2026');
    });
  });

  describe('eventOverlapsDay', () => {
    it('includes an event that starts before and ends inside the day', () => {
      const event = { start: '2026-08-14T23:00:00.000Z', end: '2026-08-15T02:00:00.000Z' };
      const day = new Date(event.end);
      expect(eventOverlapsDay(event, day)).toBe(true);
    });

    it('excludes an event that ends exactly at the day boundary', () => {
      const day = new Date(2026, 7, 15);
      const event = { start: '2026-08-14T10:00:00', end: '2026-08-15T00:00:00' };
      expect(eventOverlapsDay(event, day)).toBe(false);
    });
  });

  describe('isMultiDayEvent', () => {
    it('is true only when start and end fall on different local calendar days', () => {
      expect(isMultiDayEvent({ start: '2026-08-15T10:00:00', end: '2026-08-15T12:00:00' })).toBe(false);
      expect(isMultiDayEvent({ start: '2026-08-15T10:00:00', end: '2026-08-17T12:00:00' })).toBe(true);
    });
  });

  describe('timedEventLayout', () => {
    it('positions a same-day event proportionally within the 24-hour column', () => {
      const day = new Date(2026, 7, 15);
      const event = { start: '2026-08-15T06:00:00', end: '2026-08-15T09:00:00' };
      const layout = timedEventLayout(day, event);
      expect(layout.topPercent).toBeCloseTo(25, 5);
      expect(layout.heightPercent).toBeCloseTo(12.5, 5);
    });

    it('clamps a multi-day event to a full-height block on a day it only partially covers', () => {
      const day = new Date(2026, 7, 16);
      const event = { start: '2026-08-15T18:00:00', end: '2026-08-17T06:00:00' };
      const layout = timedEventLayout(day, event);
      expect(layout.topPercent).toBeCloseTo(0, 5);
      expect(layout.heightPercent).toBeCloseTo(100, 5);
    });
  });
});
