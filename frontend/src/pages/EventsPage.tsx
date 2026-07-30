import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { EventSetupModal } from '../components/events/EventSetupModal';
import { EventList } from '../components/events/EventList';

/**
 * Events page: shows upcoming events chronologically and lets the user
 * create a new event via the two-step event setup modal (event details,
 * then styling preferences). Calendar integration is a later, separately
 * scoped issue.
 */
export function EventsPage() {
  const [isSetupModalOpen, setIsSetupModalOpen] = useState(false);
  const navigate = useNavigate();

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
          onClick={() => setIsSetupModalOpen(true)}
        >
          Create Event
        </Button>
      </Stack>

      <EventList />

      <EventSetupModal
        open={isSetupModalOpen}
        onClose={() => setIsSetupModalOpen(false)}
        onCompleted={(eventId) => navigate(`/events/${eventId}/style`)}
      />
    </>
  );
}

