import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { CalendarEvent } from '../../api/calendarApi';
import { useDeleteEvent } from '../../hooks/useEvents';
import { StylingStatusChip } from './StylingStatusChip';

interface CalendarEventDetailsDialogProps {
  event: CalendarEvent | null;
  open: boolean;
  onClose: () => void;
  onEdit: (event: CalendarEvent) => void;
}

function formatDateTimeRange(event: CalendarEvent): string {
  if (event.allDay) {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'full' }).format(new Date(event.start));
  }
  const dateFormatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'full' });
  const timeFormatter = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' });
  const start = new Date(event.start);
  const end = new Date(event.end);
  return `${dateFormatter.format(start)}, ${timeFormatter.format(start)}\u2013${timeFormatter.format(end)}`;
}

/**
 * Event details popup shared by every calendar view: shows the event's
 * details and styling status, and lets the owner edit or delete it, or
 * jump straight into the existing styling workflow.
 */
export function CalendarEventDetailsDialog({ event, open, onClose, onEdit }: CalendarEventDetailsDialogProps) {
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const navigate = useNavigate();
  const deleteEventMutation = useDeleteEvent();

  function handleClose() {
    setConfirmingDelete(false);
    deleteEventMutation.reset();
    onClose();
  }

  if (!event) {
    return null;
  }

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>{event.title}</DialogTitle>
      <DialogContent>
        <Stack spacing={1.5}>
          <Typography variant="body1">{formatDateTimeRange(event)}</Typography>
          <Typography variant="body1">{event.location}</Typography>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
            <Chip size="small" label={event.setting === 'INDOOR' ? 'Indoor' : 'Outdoor'} />
            {event.dressCode && <Chip size="small" label={event.dressCode} />}
            <StylingStatusChip status={event.stylingStatus} />
          </Stack>

          {deleteEventMutation.isError && (
            <Alert severity="error" role="alert">
              {deleteEventMutation.error instanceof Error
                ? deleteEventMutation.error.message
                : 'Unable to delete this event.'}
            </Alert>
          )}

          {confirmingDelete && (
            <Alert
              severity="warning"
              action={
                <Stack direction="row" spacing={1}>
                  <Button
                    color="inherit"
                    size="small"
                    onClick={() => setConfirmingDelete(false)}
                    disabled={deleteEventMutation.isPending}
                  >
                    Cancel
                  </Button>
                  <Button
                    color="error"
                    size="small"
                    onClick={() => {
                      deleteEventMutation.mutate(event.id, {
                        onSuccess: () => {
                          handleClose();
                        },
                      });
                    }}
                    disabled={deleteEventMutation.isPending}
                  >
                    Confirm delete
                  </Button>
                </Stack>
              }
            >
              Delete this event? This can&apos;t be undone.
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Close</Button>
        <Button
          onClick={() => navigate(`/events/${event.id}/style`)}
        >
          Open styling workflow
        </Button>
        <Button onClick={() => onEdit(event)}>Edit</Button>
        {!confirmingDelete && (
          <Button color="error" onClick={() => setConfirmingDelete(true)}>
            Delete
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
