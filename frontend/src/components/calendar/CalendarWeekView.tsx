import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { CalendarEvent } from '../../api/calendarApi';
import { eventOverlapsDay, getWeekDays, isMultiDayEvent, timedEventLayout } from './calendarDateUtils';

const ROW_HEIGHT_PX = 48;
const HOURS = Array.from({ length: 24 }, (_, hour) => hour);

interface CalendarWeekViewProps {
  anchorDate: Date;
  events: CalendarEvent[];
  /** `hour` is `null` for the all-day row. */
  onSelectSlot: (day: Date, hour: number | null) => void;
  onSelectEvent: (event: CalendarEvent) => void;
}

function hourLabel(hour: number): string {
  const reference = new Date(2000, 0, 1, hour);
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric' }).format(reference);
}

function eventTimeRangeLabel(event: CalendarEvent): string {
  if (event.allDay) {
    return 'All day';
  }
  const formatter = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' });
  return `${formatter.format(new Date(event.start))}\u2013${formatter.format(new Date(event.end))}`;
}

function eventAccessibleLabel(event: CalendarEvent): string {
  return `View event: ${event.title}, ${eventTimeRangeLabel(event)}`;
}

/**
 * Seven-day layout with a pinned all-day row and an hourly-labeled,
 * scrollable timed-event grid below it. Each day column overlays timed
 * events (absolutely positioned by {@link timedEventLayout}, computed as a
 * percentage of the full 24-hour column height so it never depends on
 * millisecond/DST-unsafe math) on top of 24 clickable empty-hour slots -
 * clicking an empty slot starts creation prefilled with that date and
 * hour; clicking an event opens its details instead. Drag-and-drop
 * rescheduling is intentionally not implemented (out of scope for Task 18).
 */
export function CalendarWeekView({ anchorDate, events, onSelectSlot, onSelectEvent }: CalendarWeekViewProps) {
  const days = getWeekDays(anchorDate);
  const allDayEvents = days.map((day) => events.filter((event) => event.allDay && eventOverlapsDay(event, day)));
  const timedEvents = days.map((day) => events.filter((event) => !event.allDay && eventOverlapsDay(event, day)));

  return (
    <Box>
      <Box sx={{ display: 'grid', gridTemplateColumns: '64px repeat(7, 1fr)' }}>
        <Box />
        {days.map((day) => (
          <Typography key={day.toISOString()} variant="subtitle2" align="center">
            {new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric' }).format(day)}
          </Typography>
        ))}
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: '64px repeat(7, 1fr)', borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>
          All day
        </Typography>
        {days.map((day, dayIndex) => (
          <ButtonBase
            key={day.toISOString()}
            aria-label={`Create all-day event on ${new Intl.DateTimeFormat(undefined, { dateStyle: 'full' }).format(day)}`}
            onClick={() => onSelectSlot(day, null)}
            sx={{ display: 'block', textAlign: 'left', p: 0.5, minHeight: 32, border: 1, borderColor: 'divider' }}
          >
            <Stack spacing={0.25}>
              {allDayEvents[dayIndex].map((event) => (
                <Typography
                  key={event.id}
                  component="span"
                  role="button"
                  tabIndex={0}
                  aria-label={eventAccessibleLabel(event)}
                  onClick={(clickEvent) => {
                    clickEvent.stopPropagation();
                    onSelectEvent(event);
                  }}
                  onKeyDown={(keyEvent) => {
                    if (keyEvent.key === 'Enter' || keyEvent.key === ' ') {
                      keyEvent.stopPropagation();
                      keyEvent.preventDefault();
                      onSelectEvent(event);
                    }
                  }}
                  variant="caption"
                  noWrap
                  sx={{
                    display: 'block',
                    bgcolor: 'primary.main',
                    color: 'primary.contrastText',
                    borderRadius: 0.5,
                    px: 0.5,
                    cursor: 'pointer',
                  }}
                >
                  {event.title}
                </Typography>
              ))}
            </Stack>
          </ButtonBase>
        ))}
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: '64px repeat(7, 1fr)', maxHeight: 600, overflowY: 'auto' }}>
        <Box>
          {HOURS.map((hour) => (
            <Box key={hour} sx={{ height: ROW_HEIGHT_PX, display: 'flex', alignItems: 'flex-start', justifyContent: 'flex-end', pr: 1 }}>
              <Typography variant="caption" color="text.secondary">
                {hourLabel(hour)}
              </Typography>
            </Box>
          ))}
        </Box>

        {days.map((day, dayIndex) => (
          <Box key={day.toISOString()} sx={{ position: 'relative', borderLeft: 1, borderColor: 'divider' }}>
            {HOURS.map((hour) => (
              <ButtonBase
                key={hour}
                aria-label={`Create event on ${new Intl.DateTimeFormat(undefined, { dateStyle: 'full' }).format(day)} at ${hourLabel(hour)}`}
                onClick={() => onSelectSlot(day, hour)}
                sx={{
                  display: 'block',
                  width: '100%',
                  height: ROW_HEIGHT_PX,
                  border: 0,
                  borderTop: 1,
                  borderColor: 'divider',
                }}
              />
            ))}

            <Box sx={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
              {timedEvents[dayIndex].map((event) => {
                const layout = timedEventLayout(day, event);
                return (
                  <ButtonBase
                    key={event.id}
                    aria-label={
                      isMultiDayEvent(event) ? `${eventAccessibleLabel(event)}, multi-day event` : eventAccessibleLabel(event)
                    }
                    onClick={(clickEvent) => {
                      clickEvent.stopPropagation();
                      onSelectEvent(event);
                    }}
                    sx={{
                      position: 'absolute',
                      top: `${layout.topPercent}%`,
                      height: `${layout.heightPercent}%`,
                      left: 2,
                      right: 2,
                      display: 'block',
                      textAlign: 'left',
                      bgcolor: 'primary.main',
                      color: 'primary.contrastText',
                      borderRadius: 0.5,
                      px: 0.5,
                      overflow: 'hidden',
                      pointerEvents: 'auto',
                    }}
                  >
                    <Typography variant="caption" noWrap sx={{ display: 'block' }}>
                      {eventTimeRangeLabel(event)} {event.title}
                    </Typography>
                  </ButtonBase>
                );
              })}
            </Box>
          </Box>
        ))}
      </Box>
    </Box>
  );
}
