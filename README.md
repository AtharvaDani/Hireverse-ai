# Resume AI Assistant — Setup Guide

End-to-end vertical slice: React frontend → Spring Boot backend → Gemini LLM +
pgvector RAG → Resume Agent (fully implemented), with Job Agent / Interview
Agent stubbed as ready-to-fill templates behind a generic Supervisor router.

```
User → React (upload PDF) → Spring Boot → PDFBox (extract text)
     → chunk + embed (Gemini embeddings) → pgvector (Supabase)
     → Supervisor Agent (Gemini routes to an agent)
     → Resume Agent (RAG retrieval + Gemini generation)
     → JSON response → React dashboard
```

---

## 1. Get a free Gemini API key

1. Go to https://aistudio.google.com/apikey
2. Sign in with a Google account and click **Create API key**.
3. Copy the key — you'll set it as `GEMINI_API_KEY` below.

Gemini's free tier (Flash models) gives you a steady number of free
requests per minute/day with no credit card required, which is what
this project uses (`gemini-2.0-flash` for chat, `text-embedding-004`
for embeddings).

---

## 2. Set up Postgres + pgvector on Supabase (free, no Docker)

1. Go to https://supabase.com and sign up / log in.
2. Click **New Project**. Pick any name, set a database password
   (write it down — you'll need it), choose a region near you.
3. Wait ~2 minutes for the project to provision.
4. Enable pgvector:
   - In the left sidebar, go to **Database** → **Extensions**.
   - Search for `vector`.
   - Toggle it **on**.
5. Get your connection details:
   - Go to **Project Settings** → **Database**.
   - Under **Connection string**, select the **Session pooler** tab
     (NOT "Transaction pooler" — Spring AI's pgvector store needs a
     session-mode connection).
   - Copy the host, port, database name, and username shown there.
   - Your password is the one you set in step 2.

You do **not** need to manually create any tables — Spring AI's
`initialize-schema=true` setting (already configured) creates the
`resume_vector_store` table automatically on first run.

---

## 3. Configure the backend

Set these as environment variables before running the backend
(or put them in a local `.env`/IDE run configuration — never commit
real secrets):

```bash
export GEMINI_API_KEY=your_gemini_api_key_here
export DB_URL=jdbc:postgresql://YOUR_SUPABASE_HOST:5432/postgres
export DB_USERNAME=postgres
export DB_PASSWORD=your_supabase_db_password
export FRONTEND_ORIGIN=http://localhost:5173
```

These map directly to placeholders in
`backend/src/main/resources/application.properties`.

### Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

(If you don't have the Maven wrapper, install Maven and run `mvn spring-boot:run` instead.)

The backend starts on **http://localhost:8080**. On first successful
startup, check the logs for confirmation that the `resume_vector_store`
table was created in Supabase (Table Editor → you should see it appear).

---

## 4. Run the frontend

```bash
cd frontend
npm install
cp .env.example .env   # adjust VITE_API_BASE_URL if needed
npm run dev
```

Opens on **http://localhost:5173**.

---

## 5. Try it out

1. Open the frontend in your browser.
2. Upload a text-based PDF resume (not a scanned image — there's no
   OCR step in this slice).
3. Once it shows "uploaded and indexed", ask a question like
   *"Review my resume and give me feedback"* or click a suggested prompt.
4. The Supervisor will route this to the **Resume Agent**, which
   retrieves relevant chunks of your resume from pgvector and asks
   Gemini to generate a grounded critique.

---

## What's fully built vs. stubbed

| Component | Status |
|---|---|
| PDF upload + text extraction (PDFBox) | ✅ Fully implemented |
| Chunking + embedding into pgvector | ✅ Fully implemented |
| Supervisor routing (Gemini-based intent classification) | ✅ Fully implemented |
| **Resume Agent** | ✅ Fully implemented (real RAG + Gemini) |
| Job Agent | 🚧 Stub — see `JobAgent.java` for fill-in instructions |
| Interview Agent | 🚧 Stub — see `InterviewAgent.java` for fill-in instructions |

To implement Job Agent or Interview Agent for real, copy the pattern in
`ResumeAgent.java`: inject `ChatClient.Builder` + `VectorStore`, run a
similarity search scoped to the resume's `resumeId` metadata tag, build
a system prompt, call Gemini. No changes needed elsewhere — the
Supervisor auto-discovers any `@Service` that implements the `Agent`
interface.

---

## Troubleshooting

- **"Failed to read PDF file"** — make sure you're uploading a real
  PDF, not a renamed file. Password-protected PDFs aren't supported.
- **"No extractable text found in PDF"** — your PDF is likely a scanned
  image with no embedded text layer; this slice doesn't include OCR.
- **Gemini 429 / rate limit errors** — you've hit the free tier's
  requests-per-minute limit; wait a bit and retry.
- **Connection refused to Postgres** — double check you copied the
  **Session pooler** connection string, not the direct connection or
  transaction pooler one.
- **CORS errors in browser console** — confirm `FRONTEND_ORIGIN` env
  var matches exactly where your React dev server is running
  (default `http://localhost:5173`).
