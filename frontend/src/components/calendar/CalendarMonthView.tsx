import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Popover from '@mui/material/Popover';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import type { CalendarEvent } from '../../api/calendarApi';
import { eventOverlapsDay, formatMonthLabel, getMonthGridDays, isMultiDayEvent } from './calendarDateUtils';

const MAX_VISIBLE_EVENTS_PER_DAY = 3;
const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

interface CalendarMonthViewProps {
  anchorDate: Date;
  events: CalendarEvent[];
  onSelectDay: (day: Date) => void;
  onSelectEvent: (event: CalendarEvent) => void;
}

function eventTimeLabel(event: CalendarEvent): string {
  if (event.allDay) {
    return 'All day';
  }
  return new Intl.DateTimeFormat(undefined, { timeStyle: 'short' }).format(new Date(event.start));
}

function eventAccessibleLabel(event: CalendarEvent): string {
  return `View event: ${event.title}, ${eventTimeLabel(event)}`;
}

/**
 * Standard 6x7 month grid. Each day cell shows up to {@link
 * MAX_VISIBLE_EVENTS_PER_DAY} events (title + time), a "+N more" action for
 * the rest, and multi-day events get a distinct left accent bar (not color
 * alone - also gets a continuation note in its accessible label) so they
 * read differently from single-day events at a glance. Clicking anywhere
 * in an empty part of a day cell starts event creation prefilled to that
 * date; clicking an event opens its details instead (the click handler
 * stops propagation so both never fire together).
 */
export function CalendarMonthView({ anchorDate, events, onSelectDay, onSelectEvent }: CalendarMonthViewProps) {
  const [overflowAnchor, setOverflowAnchor] = useState<{ element: HTMLElement; day: Date } | null>(null);
  const days = getMonthGridDays(anchorDate);
  const currentMonth = anchorDate.getMonth();
  const currentYear = anchorDate.getFullYear();
  const today = new Date();

  const eventsByDay = days.map((day) => events.filter((event) => eventOverlapsDay(event, day)));

  return (
    <Box role="grid" aria-label={`${formatMonthLabel(anchorDate)} calendar`}>
      <Box role="row" sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)' }}>
        {WEEKDAY_LABELS.map((label) => (
          <Typography
            key={label}
            role="columnheader"
            variant="caption"
            color="text.secondary"
            sx={{ textAlign: 'center', py: 0.5 }}
          >
            {label}
          </Typography>
        ))}
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gridTemplateRows: 'repeat(6, 1fr)' }}>
        {days.map((day, index) => {
          const dayEvents = eventsByDay[index];
          const visibleEvents = dayEvents.slice(0, MAX_VISIBLE_EVENTS_PER_DAY);
          const hiddenCount = dayEvents.length - visibleEvents.length;
          const isCurrentMonth = day.getMonth() === currentMonth && day.getFullYear() === currentYear;
          const isToday =
            day.getFullYear() === today.getFullYear()
            && day.getMonth() === today.getMonth()
            && day.getDate() === today.getDate();
          const dayLabel = new Intl.DateTimeFormat(undefined, { dateStyle: 'full' }).format(day);

          return (
            <ButtonBase
              key={day.toISOString()}
              role="gridcell"
              aria-label={`Create event on ${dayLabel}`}
              onClick={() => onSelectDay(day)}
              sx={{
                display: 'block',
                textAlign: 'left',
                alignItems: 'stretch',
                justifyContent: 'flex-start',
                border: 1,
                borderColor: 'divider',
                p: 0.5,
                minHeight: 96,
                bgcolor: isCurrentMonth ? 'background.paper' : 'action.hover',
                opacity: isCurrentMonth ? 1 : 0.6,
              }}
            >
              <Stack spacing={0.5} sx={{ width: '100%' }}>
                <Typography
                  variant="caption"
                  sx={{
                    fontWeight: isToday ? 700 : 400,
                    color: isToday ? 'primary.main' : 'text.primary',
                  }}
                >
                  {day.getDate()}
                </Typography>
                {visibleEvents.map((event) => (
                  <ButtonBase
                    key={event.id}
                    aria-label={
                      isMultiDayEvent(event)
                        ? `${eventAccessibleLabel(event)}, multi-day event`
                        : eventAccessibleLabel(event)
                    }
                    onClick={(clickEvent) => {
                      clickEvent.stopPropagation();
                      onSelectEvent(event);
                    }}
                    sx={{
                      display: 'block',
                      width: '100%',
                      textAlign: 'left',
                      borderLeft: isMultiDayEvent(event) ? 3 : 0,
                      borderColor: 'secondary.main',
                      pl: isMultiDayEvent(event) ? 0.5 : 0,
                      bgcolor: 'primary.main',
                      color: 'primary.contrastText',
                      borderRadius: 0.5,
                      px: 0.5,
                    }}
                  >
                    <Typography variant="caption" noWrap sx={{ width: '100%' }}>
                      {eventTimeLabel(event)} {event.title}
                    </Typography>
                  </ButtonBase>
                ))}
                {hiddenCount > 0 && (
                  <ButtonBase
                    aria-label={`Show ${hiddenCount} more event${hiddenCount === 1 ? '' : 's'} on ${dayLabel}`}
                    onClick={(clickEvent) => {
                      clickEvent.stopPropagation();
                      setOverflowAnchor({ element: clickEvent.currentTarget, day });
                    }}
                  >
                    <Typography variant="caption" color="text.secondary">
                      +{hiddenCount} more
                    </Typography>
                  </ButtonBase>
                )}
              </Stack>
            </ButtonBase>
          );
        })}
      </Box>

      <Popover
        open={Boolean(overflowAnchor)}
        anchorEl={overflowAnchor?.element ?? null}
        onClose={() => setOverflowAnchor(null)}
      >
        {overflowAnchor && (
          <Stack spacing={0.5} sx={{ p: 1, minWidth: 220 }}>
            <Typography variant="subtitle2">
              {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(overflowAnchor.day)}
            </Typography>
            {events
              .filter((event) => eventOverlapsDay(event, overflowAnchor.day))
              .map((event) => (
                <ButtonBase
                  key={event.id}
                  aria-label={eventAccessibleLabel(event)}
                  onClick={() => {
                    setOverflowAnchor(null);
                    onSelectEvent(event);
                  }}
                  sx={{ display: 'block', width: '100%', textAlign: 'left' }}
                >
                  <Typography variant="body2">
                    {eventTimeLabel(event)} &ndash; {event.title}
                  </Typography>
                </ButtonBase>
              ))}
          </Stack>
        )}
      </Popover>
    </Box>
  );
}
