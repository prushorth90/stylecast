export interface HealthStatus {
  status: string;
  service: string;
}

/**
 * Fetches the backend application health status.
 *
 * The request path is relative ("/api/health") rather than an absolute URL,
 * so it works both in local development (proxied by Vite to the backend)
 * and in Docker (proxied by Nginx to the "backend" Compose service). This
 * component must never hard-code a host such as "localhost".
 */
export async function getHealth(): Promise<HealthStatus> {
  const response = await fetch('/api/health');

  if (!response.ok) {
    throw new Error(`Health request failed with status ${response.status}`);
  }

  return (await response.json()) as HealthStatus;
}
