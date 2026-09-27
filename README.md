# SmartChef AI

Point your phone camera at your ingredients and get recipe ideas back. Built as my final year dissertation project.

**Live app:** https://smartchef-app-ujf2.onrender.com
**Repo:** https://github.com/Razna23/smartchef-app

## How it works

1. Open the app on your phone and scan an ingredient with the camera.
2. Google's Gemini API identifies what's in the photo and adds it to your list.
3. Scan a few more things, then tap "Get recipes."
4. Spoonacular looks up recipes ranked by how many of your ingredients they use, and flags the ones you can cook right now with a "Ready to cook now" badge.

Nothing gets saved anywhere long-term. Everything lives in your browser session and is gone once it ends.

## Stack

```
backend/     Spring Boot REST API (Java 21, Maven)
frontend/    plain HTML/CSS/JS, no build step, Bootstrap loaded from a CDN
```

Two separate Render services: a Docker-based Web Service for the backend and a Static Site for the frontend, talking to each other over CORS.

## Running it locally

You'll need Java 21 and Maven.

Backend:

```bash
cd backend
mvn spring-boot:run
```

This runs on `http://localhost:8080`. You'll need real Gemini and Spoonacular keys for it to do anything useful, see below.

Frontend:

```bash
cd frontend
python3 -m http.server 5500
```

Open `http://localhost:5500` on your phone, or use a mobile viewport in your browser's dev tools. It's mobile-only by design, so the desktop layout isn't really usable.

If you're testing both locally, note that running them on two different ports can break the session cookie, since it's configured for cross-origin use on the deployed version. Easiest fix: copy `frontend/` into `backend/src/main/resources/static/` and set `BACKEND_URL` in `app.js` to an empty string, so both run from the same origin.

## API keys

Both keys are required; there's no offline fallback if either is missing.

**Gemini** - grab a key at aistudio.google.com (no card needed). The model used here, `gemini-3.5-flash-lite`, has a 500 requests/day free tier.

**Spoonacular** - sign up at spoonacular.com/food-api, then Profile > API Keys. Free tier is 150 requests/day.

```bash
GEMINI_API_KEY=your-key SPOONACULAR_API_KEY=your-key mvn spring-boot:run
```

## Deploying

Backend needs these env vars set: `GEMINI_API_KEY`, `SPOONACULAR_API_KEY`, `SESSION_COOKIE_SECURE=true`, `APP_CORS_ALLOWED_ORIGINS` (pointed at the frontend's URL). Frontend needs `BACKEND_URL` in `app.js` pointed at the backend's URL.
