/**
 * Front-end logic for the AI-Powered Ingredient Detection and Recipe Generation prototype.
 *
 * Implements the Data Flow described in Chapter 3 / Figure 6:
 *   1-2. capture a frame from the rear camera and draw it to a canvas (base64 JPEG)
 *   3.   POST /api/detect-ingredients
 *   6.   render the updated cumulative ingredient list
 *   7-8. GET /api/recipes on request
 *   9.   render ranked recipe results
 *
 * IMPORTANT: change BACKEND_URL below once the back-end is deployed to Render.
 */
const BACKEND_URL = "http://localhost:8080";

// ---- Screen navigation -----------------------------------------------------

const screens = {
  scan: document.getElementById("scan-screen"),
  list: document.getElementById("list-screen"),
  recipes: document.getElementById("recipes-screen"),
  detail: document.getElementById("detail-screen"),
};

function showScreen(name) {
  Object.values(screens).forEach((el) => el.classList.add("d-none"));
  screens[name].classList.remove("d-none");
}

// ---- Camera setup (getUserMedia, rear-facing camera) -----------------------

const video = document.getElementById("camera-feed");
const canvas = document.getElementById("capture-canvas");
const cameraPlaceholder = document.getElementById("camera-placeholder");
const captureBtn = document.getElementById("capture-btn");
const enableCameraBtn = document.getElementById("enable-camera-btn");

async function startCamera() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "environment" },
      audio: false,
    });
    video.srcObject = stream;
    cameraPlaceholder.classList.add("d-none");
    captureBtn.disabled = false;
  } catch (err) {
    // SR5: degrade gracefully with a clear message if permission is denied, rather than
    // failing silently.
    cameraPlaceholder.classList.remove("d-none");
    cameraPlaceholder.querySelector("p").textContent =
      "Camera permission was denied or is unavailable. Enable it in your browser settings and try again.";
    captureBtn.disabled = true;
    console.error("getUserMedia failed:", err);
  }
}

enableCameraBtn.addEventListener("click", startCamera);
// Attempt automatically on load too, since most browsers only need the one prompt.
startCamera();

function captureFrameAsBase64() {
  canvas.width = video.videoWidth || 720;
  canvas.height = video.videoHeight || 960;
  const ctx = canvas.getContext("2d");
  ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
  const dataUrl = canvas.toDataURL("image/jpeg", 0.85);
  return dataUrl.split(",")[1]; // strip the "data:image/jpeg;base64," prefix
}

// ---- API helpers ------------------------------------------------------------

async function apiPost(path, body) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include", // required so the session cookie is sent cross-origin
    body: JSON.stringify(body),
  });
  return res;
}

async function apiGet(path) {
  return fetch(`${BACKEND_URL}${path}`, { credentials: "include" });
}

async function apiDelete(path) {
  return fetch(`${BACKEND_URL}${path}`, { method: "DELETE", credentials: "include" });
}

function showAlert(el, message, variant) {
  el.textContent = message;
  el.className = `alert alert-${variant} mt-2`;
  el.classList.remove("d-none");
}

// ---- Scan screen behaviour ---------------------------------------------------

const scanCountEl = document.getElementById("scan-count");
const scanStatusEl = document.getElementById("scan-status");
let currentIngredientCount = 0;

captureBtn.addEventListener("click", async () => {
  captureBtn.disabled = true;
  captureBtn.textContent = "Detecting...";
  scanStatusEl.classList.add("d-none");

  try {
    const image = captureFrameAsBase64();
    const res = await apiPost("/api/detect-ingredients", { image });

    if (!res.ok) {
      showAlert(scanStatusEl, "Detection failed. Please try again.", "danger");
      return;
    }

    const ingredients = await res.json();
    const newCount = ingredients.length;
    const gained = newCount - currentIngredientCount;
    currentIngredientCount = newCount;
    scanCountEl.textContent = `${currentIngredientCount} ingredient${currentIngredientCount === 1 ? "" : "s"} scanned`;

    if (gained > 0) {
      showAlert(scanStatusEl, `Detected ${gained} new ingredient${gained === 1 ? "" : "s"}.`, "success");
    } else {
      showAlert(scanStatusEl, "No new ingredients detected in that frame - try a clearer angle.", "warning");
    }
  } catch (err) {
    console.error(err);
    showAlert(scanStatusEl, "Could not reach the server. Is the back-end running?", "danger");
  } finally {
    captureBtn.disabled = false;
    captureBtn.textContent = "Capture";
  }
});

document.getElementById("view-list-btn").addEventListener("click", () => {
  showScreen("list");
  loadIngredientList();
});

// ---- Ingredient list screen behaviour ----------------------------------------

const ingredientListEl = document.getElementById("ingredient-list");
const emptyListMessage = document.getElementById("empty-list-message");
const recipesStatusEl = document.getElementById("recipes-status");

async function loadIngredientList() {
  try {
    const res = await apiGet("/api/ingredients");
    const ingredients = await res.json();
    renderIngredientList(ingredients);
    currentIngredientCount = ingredients.length;
    scanCountEl.textContent = `${currentIngredientCount} ingredient${currentIngredientCount === 1 ? "" : "s"} scanned`;
  } catch (err) {
    console.error(err);
  }
}

function renderIngredientList(ingredients) {
  ingredientListEl.innerHTML = "";
  emptyListMessage.classList.toggle("d-none", ingredients.length > 0);

  ingredients.forEach((ingredient) => {
    const li = document.createElement("li");
    li.className = "list-group-item";

    const label = document.createElement("span");
    const pct = Math.round(ingredient.confidence * 100);
    label.textContent = `${capitalise(ingredient.name)} (${pct}%)`;

    const removeBtn = document.createElement("button");
    removeBtn.className = "remove-ingredient-btn";
    removeBtn.setAttribute("aria-label", `Remove ${ingredient.name}`);
    removeBtn.textContent = "✕"; // X
    removeBtn.addEventListener("click", () => removeIngredient(ingredient.name));

    li.appendChild(label);
    li.appendChild(removeBtn);
    ingredientListEl.appendChild(li);
  });
}

async function removeIngredient(name) {
  try {
    const res = await apiDelete(`/api/ingredients/${encodeURIComponent(name)}`);
    const ingredients = await res.json();
    renderIngredientList(ingredients);
    currentIngredientCount = ingredients.length;
    scanCountEl.textContent = `${currentIngredientCount} ingredient${currentIngredientCount === 1 ? "" : "s"} scanned`;
  } catch (err) {
    console.error(err);
  }
}

function capitalise(text) {
  return text.replace(/\b\w/g, (c) => c.toUpperCase());
}

document.getElementById("scan-another-btn").addEventListener("click", () => {
  showScreen("scan");
});

document.getElementById("get-recipes-btn").addEventListener("click", async () => {
  recipesStatusEl.classList.add("d-none");
  try {
    const res = await apiGet("/api/recipes");
    if (!res.ok) {
      const err = await res.json();
      showAlert(recipesStatusEl, err.error || "Could not fetch recipes.", "warning");
      return;
    }
    const recipes = await res.json();
    renderRecipes(recipes);
    showScreen("recipes");
  } catch (err) {
    console.error(err);
    showAlert(recipesStatusEl, "Could not reach the server. Is the back-end running?", "danger");
  }
});

// ---- Recipe results screen behaviour -----------------------------------------

const recipeCardsEl = document.getElementById("recipe-cards");

function renderRecipes(recipes) {
  recipeCardsEl.innerHTML = "";

  if (recipes.length === 0) {
    recipeCardsEl.innerHTML = '<p class="text-muted text-center">No matching recipes found.</p>';
    return;
  }

  recipes.forEach((recipe) => {
    const card = document.createElement("div");
    card.className = "recipe-card";
    card.setAttribute("role", "button");
    card.setAttribute("tabindex", "0");
    card.addEventListener("click", () => openRecipeDetail(recipe.id));
    card.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") openRecipeDetail(recipe.id);
    });

    const img = document.createElement("img");
    img.src = recipe.imageUrl || "https://via.placeholder.com/84";
    img.alt = recipe.title;

    const info = document.createElement("div");
    const title = document.createElement("div");
    title.className = "recipe-title";
    title.textContent = recipe.title;

    const meta = document.createElement("div");
    meta.className = "recipe-meta";
    meta.textContent = `Uses ${recipe.usedIngredientCount} of your ingredients` +
      (recipe.missedIngredientCount > 0 ? ` · needs ${recipe.missedIngredientCount} more` : "");

    const hint = document.createElement("div");
    hint.className = "recipe-tap-hint";
    hint.textContent = "Tap for full recipe";

    info.appendChild(title);
    info.appendChild(meta);
    info.appendChild(hint);
    card.appendChild(img);
    card.appendChild(info);
    recipeCardsEl.appendChild(card);
  });
}

document.getElementById("back-to-list-btn").addEventListener("click", () => {
  showScreen("list");
});

// ---- Recipe detail screen behaviour -------------------------------------------

const detailTitleEl = document.getElementById("detail-title");
const detailImageEl = document.getElementById("detail-image");
const detailMetaEl = document.getElementById("detail-meta");
const detailStatusEl = document.getElementById("detail-status");
const detailIngredientsEl = document.getElementById("detail-ingredients");
const detailInstructionsEl = document.getElementById("detail-instructions");

async function openRecipeDetail(id) {
  showScreen("detail");
  detailTitleEl.textContent = "Loading recipe...";
  detailImageEl.classList.add("d-none");
  detailMetaEl.textContent = "";
  detailIngredientsEl.innerHTML = "";
  detailInstructionsEl.innerHTML = "";
  detailStatusEl.classList.add("d-none");

  try {
    const res = await apiGet(`/api/recipes/${id}`);
    if (!res.ok) {
      showAlert(detailStatusEl, "Could not load this recipe. Please try another one.", "danger");
      detailTitleEl.textContent = "Recipe";
      return;
    }
    const detail = await res.json();
    renderRecipeDetail(detail);
  } catch (err) {
    console.error(err);
    showAlert(detailStatusEl, "Could not reach the server. Is the back-end running?", "danger");
    detailTitleEl.textContent = "Recipe";
  }
}

function renderRecipeDetail(detail) {
  detailTitleEl.textContent = detail.title;

  if (detail.imageUrl) {
    detailImageEl.src = detail.imageUrl;
    detailImageEl.alt = detail.title;
    detailImageEl.classList.remove("d-none");
  }

  const metaParts = [];
  if (detail.servings) metaParts.push(`Serves ${detail.servings}`);
  if (detail.readyInMinutes) metaParts.push(`${detail.readyInMinutes} min`);
  detailMetaEl.textContent = metaParts.join(" · ");

  detailIngredientsEl.innerHTML = "";
  (detail.ingredientLines || []).forEach((line) => {
    const li = document.createElement("li");
    li.className = "list-group-item";
    li.textContent = line;
    detailIngredientsEl.appendChild(li);
  });

  detailInstructionsEl.innerHTML = "";
  (detail.instructionSteps || []).forEach((step) => {
    const li = document.createElement("li");
    li.textContent = step;
    detailInstructionsEl.appendChild(li);
  });
}

document.getElementById("back-to-recipes-btn").addEventListener("click", () => {
  showScreen("recipes");
});
