# SmartChef AI - Ingredient Scanner & Recipe Generator

Final year dissertation project. Scans ingredients from your phone camera using Google's
Gemini API, then looks up recipes based on what it finds using the Spoonacular API.

Live version: https://smartchef-app-ujf2.onrender.com

## Structure

```
backend/     Spring Boot REST API (Java 21, Maven)
frontend/    plain HTML/CSS/JS, no build step, Bootstrap loaded from CDN
```

## Running it locally

Needs Java 21 and Maven installed.

Backend:

```bash
cd backend
mvn spring-boot:run
```

Runs on `http://localhost:8080`. With no API keys set it just returns fake ingredients/recipes
(mock mode), so you can still try the whole flow without an account.

Frontend:

```bash
cd frontend
python3 -m http.server 5500
```

Then open `http://localhost:5500` on your phone (or a mobile viewport in dev tools - it's
mobile-only by design).

Note: testing locally across two ports can break the session cookie, since it's set up as
cross-origin for the deployed version. Easiest fix if that happens: copy `frontend/` into
`backend/src/main/resources/static/` and set `BACKEND_URL` in `app.js` to `""`, so it all runs
from one origin.

## Real API keys

**Gemini** - aistudio.google.com -> Get API Key. No card needed, free tier is 1500 requests/day.

```bash
GEMINI_API_KEY=your-key mvn spring-boot:run
```

**Spoonacular** - spoonacular.com/food-api -> sign up -> Profile -> API Keys. Free tier is 150
requests/day.

```bash
GEMINI_API_KEY=your-key SPOONACULAR_API_KEY=your-key mvn spring-boot:run
```

## Deployment

Backend is a Render Web Service (Docker), frontend is a Render Static Site. Backend needs these
env vars: `GEMINI_API_KEY`, `SPOONACULAR_API_KEY`, `SESSION_COOKIE_SECURE=true`,
`APP_CORS_ALLOWED_ORIGINS` set to the frontend's URL. Frontend needs `BACKEND_URL` in `app.js`
pointed at the backend's URL.

## Mock mode

`GeminiService` and `SpoonacularService` both fall back to mock data automatically whenever
their API key is blank - no separate flag to toggle.
