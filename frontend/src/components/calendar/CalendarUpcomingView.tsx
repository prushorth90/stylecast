import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardActions from '@mui/material/CardActions';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useNavigate } from 'react-router-dom';
import type { CalendarEvent } from '../../api/calendarApi';
import { isSameDay } from './calendarDateUtils';
import { StylingStatusChip } from './StylingStatusChip';

interface CalendarUpcomingViewProps {
  events: CalendarEvent[];
  onSelectEvent: (event: CalendarEvent) => void;
}

function eventTimeLabel(event: CalendarEvent): string {
  if (event.allDay) {
    return 'All day';
  }
  const formatter = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' });
  return `${formatter.format(new Date(event.start))}\u2013${formatter.format(new Date(event.end))}`;
}

/** Whether it's worth showing a "Continue styling" shortcut - anything short of a fresh, ready generation. */
function showContinueStyling(event: CalendarEvent): boolean {
  return event.stylingStatus !== 'RECOMMENDATIONS_READY';
}

/**
 * Groups events by calendar day (in the order the backend already returned
 * them - ascending by start time), showing title, time, location, and
 * styling status per event, with an "Open event" action (details dialog,
 * consistent with the month/week views) and a "Continue styling" shortcut
 * straight to the event's styling page.
 */
export function CalendarUpcomingView({ events, onSelectEvent }: CalendarUpcomingViewProps) {
  const navigate = useNavigate();

  if (events.length === 0) {
    return (
      <Typography variant="body1" color="text.secondary">
        No upcoming events in this range.
      </Typography>
    );
  }

  const groups: { day: Date; events: CalendarEvent[] }[] = [];
  for (const event of events) {
    const eventDay = new Date(event.start);
    const lastGroup = groups[groups.length - 1];
    if (lastGroup && isSameDay(lastGroup.day, eventDay)) {
      lastGroup.events.push(event);
    } else {
      groups.push({ day: eventDay, events: [event] });
    }
  }

  return (
    <Stack spacing={3} component="section" aria-label="Upcoming events">
      {groups.map((group) => (
        <Stack key={group.day.toISOString()} spacing={1}>
          <Typography variant="h6" component="h3">
            {new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).format(group.day)}
          </Typography>
          {group.events.map((event) => (
            <Card key={event.id} variant="outlined">
              <CardContent>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
                  <Typography variant="subtitle1" component="h4">
                    {event.title}
                  </Typography>
                  <StylingStatusChip status={event.stylingStatus} />
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {eventTimeLabel(event)} &middot; {event.location}
                </Typography>
              </CardContent>
              <CardActions>
                <Button size="small" onClick={() => onSelectEvent(event)}>
                  Open event
                </Button>
                {showContinueStyling(event) && (
                  <Button size="small" onClick={() => navigate(`/events/${event.id}/style`)}>
                    Continue styling
                  </Button>
                )}
              </CardActions>
            </Card>
          ))}
        </Stack>
      ))}
    </Stack>
  );
}
