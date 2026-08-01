import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { CalendarEventDetailsDialog } from '../components/calendar/CalendarEventDetailsDialog';
import { CalendarMonthView } from '../components/calendar/CalendarMonthView';
import { CalendarToolbar, type CalendarViewMode } from '../components/calendar/CalendarToolbar';
import { CalendarUpcomingView } from '../components/calendar/CalendarUpcomingView';
import { CalendarWeekView } from '../components/calendar/CalendarWeekView';
import {
  addDays,
  addMonths,
  formatMonthLabel,
  formatWeekRangeLabel,
  getMonthRange,
  getUpcomingRange,
  getWeekRange,
} from '../components/calendar/calendarDateUtils';
import { EventSetupModal } from '../components/events/EventSetupModal';
import type { CalendarEvent } from '../api/calendarApi';
import { useCalendarEvents } from '../hooks/useCalendarEvents';
import { useEvent } from '../hooks/useEvents';
import { useEventStylePreferences } from '../hooks/useStylePreferences';

const UPCOMING_WINDOW_DAYS = 60;

interface CreateSlot {
  startTime: string;
  endTime: string;
}

/**
 * Protected `/calendar` page: month/week/upcoming views over the current
 * user's own events, backed by a single bounded-range fetch (`GET
 * /api/events/calendar`). Event creation/editing reuses the existing
 * `EventSetupModal` unchanged (only a date/time prefill is added); the
 * modal's own mutations already invalidate every `['events', ...]`-keyed
 * query (see `useCreateEvent`/`useUpdateEvent`/`useDeleteEvent`), so the
 * calendar's own range query refetches automatically after a create/edit/
 * delete - no separate invalidation wiring is needed here.
 */
export function CalendarPage() {
  const [view, setView] = useState<CalendarViewMode>('month');
  const [anchorDate, setAnchorDate] = useState(() => new Date());
  const [selectedEvent, setSelectedEvent] = useState<CalendarEvent | null>(null);
  const [createSlot, setCreateSlot] = useState<CreateSlot | null>(null);
  const [editingEventId, setEditingEventId] = useState<string | null>(null);

  const range = useMemo(() => {
    if (view === 'month') {
      return getMonthRange(anchorDate);
    }
    if (view === 'week') {
      return getWeekRange(anchorDate);
    }
    return getUpcomingRange(anchorDate, UPCOMING_WINDOW_DAYS);
  }, [view, anchorDate]);

  const { data: events, isPending, isError, error } = useCalendarEvents(range.start, range.end);

  const { data: editingEvent } = useEvent(editingEventId ?? undefined);
  const { data: editingPreferences } = useEventStylePreferences(editingEventId ?? undefined);

  const rangeLabel = useMemo(() => {
    if (view === 'month') {
      return formatMonthLabel(anchorDate);
    }
    if (view === 'week') {
      return formatWeekRangeLabel(anchorDate);
    }
    return `Upcoming (${new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(range.start)} \u2013 ${new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(addDays(range.end, -1))})`;
  }, [view, anchorDate, range]);

  function handlePrevious() {
    if (view === 'month') {
      setAnchorDate((current) => addMonths(current, -1));
    } else if (view === 'week') {
      setAnchorDate((current) => addDays(current, -7));
    } else {
      setAnchorDate((current) => addDays(current, -UPCOMING_WINDOW_DAYS / 2));
    }
  }

  function handleNext() {
    if (view === 'month') {
      setAnchorDate((current) => addMonths(current, 1));
    } else if (view === 'week') {
      setAnchorDate((current) => addDays(current, 7));
    } else {
      setAnchorDate((current) => addDays(current, UPCOMING_WINDOW_DAYS / 2));
    }
  }

  function handleToday() {
    setAnchorDate(new Date());
  }

  function openCreateForDay(day: Date, hour: number | null) {
    const start = new Date(day);
    if (hour !== null) {
      start.setHours(hour, 0, 0, 0);
    }
    const end = new Date(start);
    if (hour === null) {
      end.setDate(end.getDate() + 1);
    } else {
      end.setHours(end.getHours() + 1);
    }
    setCreateSlot({ startTime: start.toISOString(), endTime: end.toISOString() });
  }

  function handleSelectEvent(event: CalendarEvent) {
    setSelectedEvent(event);
  }

  function handleEdit(event: CalendarEvent) {
    setSelectedEvent(null);
    setEditingEventId(event.id);
  }

  const isSetupModalOpen = createSlot !== null || editingEventId !== null;

  function closeSetupModal() {
    setCreateSlot(null);
    setEditingEventId(null);
  }

  return (
    <Stack spacing={2}>
      <Typography variant="h4" component="h1">
        Calendar
      </Typography>

      <CalendarToolbar
        view={view}
        onViewChange={setView}
        rangeLabel={rangeLabel}
        onPrevious={handlePrevious}
        onNext={handleNext}
        onToday={handleToday}
      />

      {isPending && (
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
          <CircularProgress size={20} />
          <Typography variant="body1">Loading calendar…</Typography>
        </Stack>
      )}

      {isError && (
        <Alert severity="error" role="alert">
          {error instanceof Error ? error.message : 'Unable to load your calendar.'}
        </Alert>
      )}

      {!isPending && !isError && (
        <>
          {events.length === 0 && view !== 'upcoming' && (
            <Typography variant="body2" color="text.secondary">
              No events in this range yet.
            </Typography>
          )}

          {view === 'month' && (
            <CalendarMonthView
              anchorDate={anchorDate}
              events={events}
              onSelectDay={(day) => openCreateForDay(day, null)}
              onSelectEvent={handleSelectEvent}
            />
          )}

          {view === 'week' && (
            <CalendarWeekView
              anchorDate={anchorDate}
              events={events}
              onSelectSlot={openCreateForDay}
              onSelectEvent={handleSelectEvent}
            />
          )}

          {view === 'upcoming' && <CalendarUpcomingView events={events} onSelectEvent={handleSelectEvent} />}
        </>
      )}

      <CalendarEventDetailsDialog
        event={selectedEvent}
        open={selectedEvent !== null}
        onClose={() => setSelectedEvent(null)}
        onEdit={handleEdit}
      />

      <EventSetupModal
        open={isSetupModalOpen}
        onClose={closeSetupModal}
        eventId={editingEventId ?? undefined}
        initialEvent={editingEvent}
        initialPreferences={editingPreferences ?? null}
        initialStartTime={createSlot?.startTime}
        initialEndTime={createSlot?.endTime}
        onCompleted={closeSetupModal}
      />
    </Stack>
  );
}
