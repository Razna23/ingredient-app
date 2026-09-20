# AI-Powered Ingredient Detection and Recipe Generation

This is the working prototype behind the dissertation: a mobile-only web app that detects
cooking ingredients from a phone camera (Clarifai Food Model API) and suggests recipes based
on what's been detected (Spoonacular API). It matches the architecture, endpoints, and data
model documented in Chapters 1-3 (system architecture, use case diagram, wireframes, session
data model).

```
ingredient-app/
├── backend/     Java Spring Boot REST API (Maven project)
└── frontend/    Static HTML/CSS/JS front-end (Bootstrap 5 via CDN)
```

The app runs in **mock mode** out of the box - no API keys needed - so you can see the whole
flow (scan → accumulate ingredients → get recipes) working immediately. Section 2 below covers
switching on real detection and real recipes once you have API keys.

---

## 1. Running it locally (mock mode, no API keys needed)

You'll need Java 21 and Maven installed. Check with `java -version` and `mvn -version`.

**Start the back-end:**

```bash
cd backend
mvn spring-boot:run
```

This starts the API on `http://localhost:8080`. With no `CLARIFAI_API_KEY` or
`SPOONACULAR_API_KEY` set, both services automatically fall back to mock responses - you'll
see plausible-looking ingredients and recipes without needing any account yet.

**Serve the front-end:**

The front-end is plain static files, so any static server works, e.g.:

```bash
cd frontend
python3 -m http.server 5500
```

Then open `http://localhost:5500` in a browser on your phone (or your laptop with dev tools
set to a mobile viewport - the layout is capped at phone width by design).

**Important local-testing note:** the deployed version (Section 3) has the front-end and
back-end on two different Render URLs, so the session cookie that tracks your ingredient list
is deliberately configured as cross-origin (`SameSite=None; Secure`), which requires HTTPS.
That's fine on Render, but plain `http://localhost` can't satisfy the `Secure` requirement, so
a real browser will silently refuse to store the session cookie during local testing across
two different localhost ports, and every request will look like a brand-new session.

The simplest fix for local testing: copy the contents of `frontend/` into
`backend/src/main/resources/static/` so Spring Boot serves both the API and the front-end from
the same origin (`http://localhost:8080`) - then update `BACKEND_URL` in `frontend/js/app.js`
(or the copied version) to `""` (empty string, since it's now the same origin) and the session
cookie works over plain HTTP with no changes needed elsewhere. This was confirmed during
development (see Chapter 4 for how this was written up as an implementation challenge).

---

## 2. Getting real API keys

### Clarifai (ingredient detection)

1. Go to https://clarifai.com and click **Sign Up** (a free account is enough for this
   project - the free tier includes 1,000 operations per month, as discussed in Chapter 1).
2. Once logged in, go to your **Personal Access Tokens** page (under your account settings,
   usually **Security** or **API Keys**) and create a new key/token.
3. Copy the key.
4. Run the back-end with it set as an environment variable:
   ```bash
   CLARIFAI_API_KEY=your-key-here mvn spring-boot:run
   ```
   Once this is set, `ClarifaiService` automatically switches out of mock mode and calls the
   real Food Model API (model ID `bd367be194cf45149e75f01d59f77ba7`, Clarifai's public food/
   ingredient recognition model). If Clarifai has changed this model's ID by the time you sign
   up, your dashboard will show the current one under **Explore → Models → Food** - update the
   `CLARIFAI_MODEL_ID` constant in `ClarifaiService.java` if it differs.

### Spoonacular (recipe generation)

1. Go to https://spoonacular.com/food-api and click **Get API Key** / **Sign Up**.
2. The free tier gives 150 requests per day (as documented in Chapter 1's Scope and
   Limitations), which is plenty for development and demonstration.
3. Copy the key from your dashboard (**Profile → API Keys**).
4. Run the back-end with it set too:
   ```bash
   CLARIFAI_API_KEY=your-clarifai-key SPOONACULAR_API_KEY=your-spoonacular-key mvn spring-boot:run
   ```

With both keys set, the app is now doing real ingredient detection and real recipe lookups -
this is the point where Chapter 6 (Testing and Evaluation) can start collecting genuine
accuracy/performance data instead of working from the mock responses.

---

## 3. Deploying to Render (matches Chapter 3's architecture)

1. **Push this project to GitHub** (a new repository, with `backend/` and `frontend/` as
   sub-folders, or as two separate repositories - either works with Render).
2. **Back-end - Render Web Service:**
   - New → Web Service → connect your GitHub repo, root directory `backend/`.
   - Render has no native Java runtime, so choose **Docker** as the language/environment -
     Render will find and build the `backend/Dockerfile` included in this project
     automatically. No build/start command fields are needed with Docker; the Dockerfile
     handles both.
   - Add environment variables: `CLARIFAI_API_KEY`, `SPOONACULAR_API_KEY`,
     `SESSION_COOKIE_SECURE=true`, and once you know your front-end's Render URL,
     `APP_CORS_ALLOWED_ORIGINS=https://your-frontend-name.onrender.com`.
3. **Front-end - Render Static Site:**
   - New → Static Site → connect the same repo, root directory `frontend/`.
   - No build command needed (it's already static HTML/CSS/JS).
   - Once deployed, edit `frontend/js/app.js`'s `BACKEND_URL` constant to your back-end's
     Render URL (e.g. `https://your-backend-name.onrender.com`), commit, and push - Render
     redeploys automatically.
4. Both services get HTTPS automatically from Render, which is what makes the cross-origin
   session cookie (`SameSite=None; Secure`) actually work in production, unlike the local
   `http://` testing caveat in Section 1.

---

## 4. What's mock vs real, and why this matters for the dissertation

- `ClarifaiService.isMockMode()` / `SpoonacularService.isMockMode()` return `true` whenever
  the corresponding API key is blank, which is exactly how the fallback is triggered - no
  separate flag to remember to flip.
- The `backend/mock-server-for-testing/` folder is **not part of the deliverable**. It's a
  small dependency-free Node.js script written purely to verify the front-end and API contract
  in a sandboxed environment that couldn't reach Maven Central to compile the real Spring Boot
  app. It deliberately mirrors the same routes and mock logic as the real Java classes, so
  everything validated against it (session accumulation, de-duplication, removal, recipe
  generation, CORS) applies to the real back-end too. It's safe to delete before submission,
  or keep as evidence of the verification process for Chapter 6.
