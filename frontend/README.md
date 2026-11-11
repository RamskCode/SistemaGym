# SistemaGym frontend

React, TypeScript and Vite application. Requires Node.js 24+ and npm 11+.

Run these commands from the `frontend` directory:

```powershell
npm.cmd ci
npm.cmd run dev
npm.cmd run test
npm.cmd run build
```

Open the local URL printed by Vite (normally `http://localhost:5173`). The demo
dashboard is the initial page; the base routes are `/`, `/socios`, `/asistencia`,
`/actividad`, `/reportes` and `/revisiones`. Unknown paths display a 404 page.

During development, Vite proxies `/api` requests to `http://localhost:8080`.
Start the backend separately for API requests. Keep backend secrets and database
credentials in the backend runtime environment; they do not belong in this frontend.
