# Knowdex

**A basic prototype / proof of concept** for an AI-powered knowledge workspace: organize PDFs into folders, read them in the browser, generate summaries, and ask questions about a selected document using Google Gemini.

This is **not a production system** and **not a RAG pipeline**. Long documents are **truncated**, not chunked and indexed.

---

## Status

Knowdex is a student/demo prototype meant to show the idea end to end:

- local PDF + folder workspace
- PDF text extraction
- Gemini summarization and Q&A from stuffed document text

It is intentionally simple. There is no vector database, no embeddings, and no semantic retrieval.

---

## What it does

- Create and manage topic folders
- Upload PDFs (single or bulk) and drag them into folders
- Bulk upload can ask Gemini to name folders and assign documents by topic
- View PDFs in the browser
- Generate a structured Markdown summary of a selected PDF
- Chat with a selected PDF using only that file’s extracted text
- Persist files and metadata on the local filesystem (`~/.knowdex_storage/`)

---

## How AI works (important)

When you summarize or chat:

1. Apache PDFBox extracts **plain text** from the PDF.
2. That text is **cut to a character limit** (about 25,000 for summary, 30,000 for chat).
3. The truncated text is **pasted into a Gemini prompt** with your question or summary instructions.
4. Gemini returns Markdown. Summaries can be cached on the document record.

**Long documents are truncated, not chunked and indexed.**  
Later pages past the length cap are never sent to the model. Questions do not search for the most relevant passages; they always use the same truncated prefix of the selected file.

This is **in-context / full-document prompting**, not Retrieval-Augmented Generation (RAG).

---

## Tech stack

| Layer | Choice |
| --- | --- |
| Frontend | React, Vite, Axios, Lucide icons |
| Backend | Java 21, Spring Boot 3.2.5 |
| PDF text | Apache PDFBox 3.x |
| AI | Google Gemini REST `generateContent` |
| Storage | Local files + JSON (no database) |

---

## Project structure

```
├── knowdex-backend/     Spring Boot API (port 8087)
├── knowdex-frontend/    React UI (Vite, typically port 5173 or 3000)
└── README.md
```

Core backend logic lives in `knowdex-backend/src/main/java/com/knowdex/service/StorageService.java` (disk I/O, PDF extract, Gemini calls, folder clustering).

---

## Prerequisites

- JDK 21+
- Apache Maven
- Node.js 18+ and npm
- A [Google Gemini API key](https://aistudio.google.com/apikey)

---

## Setup

### 1. Configure the Gemini API key

Edit `knowdex-backend/src/main/resources/application.properties`:

```properties
gemini.api.key=YOUR_GEMINI_API_KEY
```

Do not commit a real key. Backend listens on **port 8087** (`server.port=8087`).

### 2. Start the backend

```bash
cd knowdex-backend
mvn spring-boot:run
```

API: `http://localhost:8087`

### 3. Start the frontend

```bash
cd knowdex-frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` to `http://localhost:8087`. Open the URL Vite prints (default is often `http://localhost:5173`).

To force port 3000:

```bash
npm run dev -- --port 3000
```

---

## Data location

```
~/.knowdex_storage/
├── pdfs/                 uploaded PDFs
└── metadata/
    ├── folders.json
    ├── documents.json
    └── notes.json
```

---

## Prototype limits



- **Truncation, not indexing** — long PDFs lose content after the character cap
- **One document at a time** — chat does not search across your whole library
- **No RAG** — no chunking, embeddings, or similarity retrieval
- **Filesystem JSON** — not multi-user, not concurrent-safe beyond a local demo
- **Extractable text only** — scanned PDFs are unsupported
- **API key in properties** — fine for a local prototype; not a production secret setup

---

