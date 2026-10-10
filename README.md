# Knowdex

**A basic prototype / proof of concept** for an AI-powered knowledge workspace: organize PDFs into folders, read them in the browser, generate summaries, and ask questions about a selected document using Google Gemini.



## Status

Knowdex is a student/demo prototype designed to demonstrate the idea end to end:

- Local PDF and folder workspace
- PDF text extraction
- Gemini-powered summarization and question answering using extracted document text

It is intentionally simple. There is currently no vector database, embeddings, or semantic retrieval. Documents are processed by extracting their text and passing it to Gemini, with long documents truncated when they exceed the character limit rather than being properly chunked and indexed.

**Future improvements could include implementing a Retrieval-Augmented Generation (RAG) pipeline** with document chunking, embedding generation, vector storage, and semantic similarity search. These enhancements could enable more accurate, context-aware answers and efficient retrieval from larger documents.

## What It Does

- Create and manage topic folders
- Upload PDFs individually or in bulk and organize them into folders
- Use Gemini to suggest folder names and assign documents by topic during bulk uploads
- View PDFs directly in the browser
- Generate structured Markdown summaries of selected PDFs
- Chat with a selected PDF using only that document's extracted text
- Persist files and metadata on the local filesystem (`~/.knowdex_storage/`)

## Tech Stack

| Layer | Technology |
| --- | --- |
| Frontend | React, Vite, Axios, Lucide icons |
| Backend | Java 21, Spring Boot 3.2.5 |
| PDF Text Extraction | Apache PDFBox 3.x |
| AI Integration | Google Gemini REST API (`generateContent`) |
| Storage | Local filesystem and JSON files (no database) |

## Project Structure

```text
├── knowdex-backend/
├── knowdex-frontend/
└── README.md
```

- `knowdex-backend/` — Spring Boot API (port 8087)
- `knowdex-frontend/` — React UI powered by Vite (port 5173 by default, or 3000 if configured)

Core backend logic lives in:

`knowdex-backend/src/main/java/com/knowdex/service/StorageService.java`

This service handles filesystem operations, PDF text extraction, Gemini API calls, and folder clustering.

## Prerequisites

- JDK 21 or later
- Apache Maven
- Node.js 18 or later and npm
- A [Google Gemini API key](https://aistudio.google.com/apikey)

## Setup

### 1. Configure the Gemini API Key

Edit `knowdex-backend/src/main/resources/application.properties`:

```properties
gemini.api.key=YOUR_GEMINI_API_KEY
server.port=8087
```

Replace `YOUR_GEMINI_API_KEY` with your actual Gemini API key.

**Security note:** Do not commit your real API key to version control.

### 2. Start the Backend

```bash
cd knowdex-backend
mvn spring-boot:run
```

Backend API: `http://localhost:8087`

### 3. Start the Frontend

Open another terminal:

```bash
cd knowdex-frontend
npm install
npm run dev
```

Open the URL printed by Vite, usually `http://localhost:5173`.

To run the frontend on port 3000:

```bash
npm run dev -- --port 3000
```

The Vite development server proxies `/api` requests to `http://localhost:8087`.

## Data Storage

Knowdex stores uploaded files and metadata locally in:

```text
~/.knowdex_storage/
```

Files and metadata persist between application restarts.

## Prototype Limitations

- **Truncation instead of indexing:** Long PDFs are truncated when they exceed the configured character limit, potentially excluding relevant information.
- **Single-document chat:** Questions are answered using the selected document rather than searching across the entire library.
- **No RAG pipeline:** The current implementation does not use document chunking, embeddings, a vector database, or semantic similarity search.
- **Local filesystem storage:** JSON-based metadata storage is intended for a local demo and is not designed for robust concurrent or multi-user access.
- **Text-based PDFs only:** Scanned PDFs without extractable text are not supported by the current extraction workflow.
- **API key configuration:** Storing the Gemini API key in `application.properties` is suitable for local experimentation but is not recommended for production deployments.

## Future Enhancements

Potential improvements include:

- **RAG implementation:** Split documents into smaller, overlapping chunks and retrieve relevant passages for each question.
- **Embedding generation:** Convert document chunks and user queries into vector representations.
- **Vector database integration:** Store and retrieve embeddings using a suitable vector store such as Chroma or FAISS.
- **Semantic search:** Retrieve relevant passages based on meaning instead of passing the entire extracted document to the AI model.
- **Library-wide question answering:** Retrieve relevant information across multiple PDFs in the workspace.
- **Improved PDF support:** Add OCR to extract text from scanned documents.
- **Persistent database:** Replace JSON metadata files with a database for improved querying and data management.
- **Secure configuration:** Use environment variables or a secrets manager to manage API credentials.

## Project Screenshot

![Knowdex application interface](https://github.com/user-attachments/assets/2e8b0a70-469a-4c55-a0ae-0bb311947d11)

---

**Knowdex** is an early-stage prototype exploring how AI can simplify personal document organization and knowledge discovery. Its current implementation provides basic PDF management and Gemini-powered document interactions, with RAG and semantic retrieval identified as potential future enhancements.
