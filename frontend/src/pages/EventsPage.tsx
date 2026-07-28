import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { CreateEventDialog } from '../components/events/CreateEventDialog';
import { EventList } from '../components/events/EventList';

/**
 * Events page: shows upcoming events chronologically and lets the user
 * manually create a new event. Calendar integration is a later, separately
 * scoped issue.
 */
export function EventsPage() {
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);

  return (
    <>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between', mb: 3 }}
      >
        <Typography variant="h4" component="h1">
          Events
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setIsCreateDialogOpen(true)}
        >
          Create Event
        </Button>
      </Stack>

      <EventList />

      <CreateEventDialog open={isCreateDialogOpen} onClose={() => setIsCreateDialogOpen(false)} />
    </>
  );
}
