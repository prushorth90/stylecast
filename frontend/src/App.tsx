import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { EventDetailPage } from './pages/EventDetailPage';
import { EventsPage } from './pages/EventsPage';
import { EventStylePage } from './pages/EventStylePage';

function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<EventsPage />} />
        <Route path="/events" element={<EventsPage />} />
        <Route path="/events/:eventId" element={<EventDetailPage />} />
        <Route path="/events/:eventId/style" element={<EventStylePage />} />
      </Routes>
    </AppShell>
  );
}

export default App;
