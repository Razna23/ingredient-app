/**
 * Dependency-free Node.js mock server used ONLY to verify the API contract and front-end
 * integration in this sandbox, where Maven Central is blocked and the real Spring Boot
 * back-end cannot be compiled. It deliberately mirrors the exact same routes, JSON shapes,
 * mock-detection pool, and cookie-based session behaviour as:
 *   - com.ingredientapp.controller.IngredientController
 *   - com.ingredientapp.service.ClarifaiService (mock mode)
 *   - com.ingredientapp.service.SpoonacularService (mock mode)
 *
 * This file is NOT part of the deliverable and is not deployed anywhere - it only exists to
 * prove out the request/response contract that the real Java classes implement.
 */
const http = require("http");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { URL } = require("url");

const PORT = 8080;
const ALLOWED_ORIGIN = "http://localhost:5500";
const CONFIDENCE_THRESHOLD = 0.70;

// SANDBOX-TEST-ONLY: also serve the front-end from this same origin, so the browser's
// SameSite=None (non-Secure, since this is plain http:// in the sandbox) cookie isn't
// silently dropped - modern browsers require Secure+HTTPS for SameSite=None cookies to be
// cross-origin. On Render, both services get real HTTPS, so the true cross-origin setup
// (separate Static Site + Web Service, as designed in Chapter 3) works correctly there.
const FRONTEND_DIR = path.join(__dirname, "..", "..", "frontend");
const MIME = { ".html": "text/html", ".css": "text/css", ".js": "application/javascript" };

const POOL = [
  { name: "onion", confidence: 0.93 },
  { name: "tomato", confidence: 0.89 },
  { name: "garlic", confidence: 0.85 },
  { name: "cheese", confidence: 0.81 },
  { name: "chicken breast", confidence: 0.78 },
  { name: "bell pepper", confidence: 0.74 },
  { name: "potato", confidence: 0.71 },
  { name: "carrot", confidence: 0.69 }, // deliberately below threshold, same as ClarifaiService
];

const sessions = new Map(); // sessionId -> { ingredients: [] }

function getSessionId(req) {
  const cookie = req.headers.cookie || "";
  const match = cookie.match(/SESSIONID=([a-f0-9]+)/);
  return match ? match[1] : null;
}

function getOrCreateSession(req, res) {
  let id = getSessionId(req);
  let isNew = false;
  if (!id || !sessions.has(id)) {
    id = crypto.randomBytes(16).toString("hex");
    sessions.set(id, { ingredients: [] });
    isNew = true;
  }
  if (isNew) {
    // Same-origin in this sandbox test (front-end + mock API both on :8080), so a plain
    // SameSite=Lax cookie works fine over http:// - no Secure/None needed here. The real
    // Render deployment is genuinely cross-origin and relies on SameSite=None; Secure
    // over HTTPS instead (see WebConfig.java and application.properties).
    res.setHeader("Set-Cookie", `SESSIONID=${id}; Path=/`);
  }
  return sessions.get(id);
}

function mockDetect() {
  const shuffled = [...POOL].sort(() => Math.random() - 0.5);
  const count = 1 + Math.floor(Math.random() * 2); // 1 or 2, same as ClarifaiService
  return shuffled.slice(0, count).filter((i) => i.confidence >= CONFIDENCE_THRESHOLD);
}

function capitalise(text) {
  if (!text) return "Mystery";
  return text.split(" ").map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(" ").trim();
}

function mockRecipes(ingredients) {
  const have = ingredients.length;
  const joined = ingredients.slice(0, 2).map((i) => i.name).join(" & ");
  const base = capitalise(joined);
  return [
    { id: 1, title: `${base} Bake`, imageUrl: "https://via.placeholder.com/300x200?text=Recipe+1", usedIngredientCount: Math.min(have, 4), missedIngredientCount: Math.max(0, 5 - have) },
    { id: 2, title: `${base} Soup`, imageUrl: "https://via.placeholder.com/300x200?text=Recipe+2", usedIngredientCount: Math.min(have, 3), missedIngredientCount: Math.max(0, 4 - have) },
    { id: 3, title: `${base} Stir Fry`, imageUrl: "https://via.placeholder.com/300x200?text=Recipe+3", usedIngredientCount: Math.min(have, 3), missedIngredientCount: Math.max(0, 5 - have) },
  ];
}

function mockRecipeDetail(id, ingredients) {
  const names = ingredients.map((i) => i.name);
  const joined = names.slice(0, 2).join(" & ");
  const ingredientLines = names.length ? names.map((n) => `1 portion ${n}`) : ["1 mystery ingredient"];
  return {
    id,
    title: `${capitalise(joined)} Bake`,
    imageUrl: "https://via.placeholder.com/300x200?text=Recipe",
    servings: 2,
    readyInMinutes: 25,
    ingredientLines,
    instructionSteps: [
      `Prepare and wash all your ingredients (${names.length ? names.join(", ") : "whatever you've scanned"}).`,
      "Combine everything in a pan or oven dish and season to taste.",
      `Cook until done, plate up, and enjoy your ${capitalise(joined)}.`,
    ],
  };
}

function sendJson(res, status, body) {
  const json = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(json);
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = "";
    req.on("data", (chunk) => (data += chunk));
    req.on("end", () => resolve(data ? JSON.parse(data) : {}));
  });
}

const server = http.createServer(async (req, res) => {
  res.setHeader("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
  res.setHeader("Access-Control-Allow-Credentials", "true");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  const url = new URL(req.url, `http://localhost:${PORT}`);

  if (!url.pathname.startsWith("/api/")) {
    const filePath = path.join(FRONTEND_DIR, url.pathname === "/" ? "index.html" : url.pathname);
    if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
      const ext = path.extname(filePath);
      res.writeHead(200, { "Content-Type": MIME[ext] || "application/octet-stream" });
      fs.createReadStream(filePath).pipe(res);
      return;
    }
    res.writeHead(404);
    res.end("Not found");
    return;
  }

  const session = getOrCreateSession(req, res);

  if (req.method === "POST" && url.pathname === "/api/detect-ingredients") {
    await readBody(req); // (image payload not needed for the mock)
    const detected = mockDetect();
    for (const d of detected) {
      if (!session.ingredients.some((i) => i.name.toLowerCase() === d.name.toLowerCase())) {
        session.ingredients.push(d);
      }
    }
    sendJson(res, 200, session.ingredients);
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/ingredients") {
    sendJson(res, 200, session.ingredients);
    return;
  }

  if (req.method === "DELETE" && url.pathname.startsWith("/api/ingredients/")) {
    const name = decodeURIComponent(url.pathname.split("/").pop());
    session.ingredients = session.ingredients.filter((i) => i.name.toLowerCase() !== name.toLowerCase());
    sendJson(res, 200, session.ingredients);
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/recipes") {
    if (session.ingredients.length === 0) {
      sendJson(res, 400, { error: "No ingredients in this session yet. Scan at least one ingredient first." });
      return;
    }
    sendJson(res, 200, mockRecipes(session.ingredients));
    return;
  }

  const recipeIdMatch = url.pathname.match(/^\/api\/recipes\/(\d+)$/);
  if (req.method === "GET" && recipeIdMatch) {
    sendJson(res, 200, mockRecipeDetail(Number(recipeIdMatch[1]), session.ingredients));
    return;
  }

  sendJson(res, 404, { error: "Not found" });
});

server.listen(PORT, () => {
  console.log(`Mock backend (for sandbox testing only) listening on http://localhost:${PORT}`);
});
