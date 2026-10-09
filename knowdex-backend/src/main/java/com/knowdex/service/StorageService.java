package com.knowdex.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.knowdex.model.DocumentModel;
import com.knowdex.model.FolderModel;
import com.knowdex.model.NoteModel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StorageService {

    private final Path baseStorageDir = Paths.get(System.getProperty("user.home"), ".knowdex_storage").toAbsolutePath();
    private final Path pdfsDir = baseStorageDir.resolve("pdfs");
    private final Path metadataDir = baseStorageDir.resolve("metadata");

    private final Path foldersJson = metadataDir.resolve("folders.json");
    private final Path documentsJson = metadataDir.resolve("documents.json");
    private final Path notesJson = metadataDir.resolve("notes.json");

    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public StorageService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(pdfsDir);
            Files.createDirectories(metadataDir);

            if (!Files.exists(foldersJson)) {
                saveFolders(new ArrayList<>());
            }
            if (!Files.exists(documentsJson)) {
                saveDocuments(new ArrayList<>());
            }
            if (!Files.exists(notesJson)) {
                saveNotes(new ArrayList<>());
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize local storage directory", e);
        }
    }

    // FOLDERS
    public synchronized List<FolderModel> getFolders() {
        try {
            if (!Files.exists(foldersJson)) return new ArrayList<>();
            return objectMapper.readValue(foldersJson.toFile(), new TypeReference<List<FolderModel>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private synchronized void saveFolders(List<FolderModel> folders) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(foldersJson.toFile(), folders);
    }

    public FolderModel createFolder(String name, String description) throws IOException {
        List<FolderModel> folders = getFolders();
        FolderModel folder = new FolderModel(UUID.randomUUID().toString(), name, description, LocalDateTime.now());
        folders.add(folder);
        saveFolders(folders);
        return folder;
    }

    public synchronized List<FolderModel> setFolderCountAndRecluster(int count) throws IOException {
        List<FolderModel> folders = getFolders();
        if (folders.size() < count) {
            int needed = count - folders.size();
            for (int i = 1; i <= needed; i++) {
                int nextNum = folders.size() + 1;
                folders.add(new FolderModel(UUID.randomUUID().toString(), "Folder " + nextNum, "Auto-created folder", LocalDateTime.now()));
            }
            saveFolders(folders);
        } else if (folders.size() > count) {
            List<FolderModel> retained = new ArrayList<>(folders.subList(0, count));
            Set<String> retainedIds = retained.stream().map(FolderModel::getId).collect(Collectors.toSet());
            
            String fallbackId = retained.isEmpty() ? null : retained.get(0).getId();
            List<DocumentModel> docs = getDocuments();
            for (DocumentModel d : docs) {
                if (d.getFolderId() != null && !retainedIds.contains(d.getFolderId())) {
                    d.setFolderId(fallbackId);
                }
            }
            saveDocuments(docs);
            saveFolders(retained);
        }

        List<DocumentModel> allDocs = getDocuments();
        if (!allDocs.isEmpty()) {
            Map<String, String> docTexts = new HashMap<>();
            for (DocumentModel d : allDocs) {
                String text = extractPdfText(d.getId());
                docTexts.put(d.getId(), text.length() > 2000 ? text.substring(0, 2000) : text);
            }
            clusterAndGenerateFolderNamesWithAI(allDocs, docTexts, getFolders());
        }

        return getFolders();
    }

    public FolderModel updateFolder(String id, String name, String description) throws IOException {
        List<FolderModel> folders = getFolders();
        for (FolderModel f : folders) {
            if (f.getId().equals(id)) {
                if (name != null && !name.trim().isEmpty()) f.setName(name);
                if (description != null) f.setDescription(description);
                saveFolders(folders);
                return f;
            }
        }
        throw new RuntimeException("Folder not found: " + id);
    }

    public void deleteFolder(String id) throws IOException {
        List<FolderModel> folders = getFolders();
        folders.removeIf(f -> f.getId().equals(id));
        saveFolders(folders);

        List<DocumentModel> docs = getDocuments();
        boolean docsChanged = false;
        for (DocumentModel d : docs) {
            if (id.equals(d.getFolderId())) {
                d.setFolderId(null);
                docsChanged = true;
            }
        }
        if (docsChanged) saveDocuments(docs);
    }

    // DOCUMENTS
    public synchronized List<DocumentModel> getDocuments() {
        try {
            if (!Files.exists(documentsJson)) return new ArrayList<>();
            return objectMapper.readValue(documentsJson.toFile(), new TypeReference<List<DocumentModel>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private synchronized void saveDocuments(List<DocumentModel> documents) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(documentsJson.toFile(), documents);
    }

    public DocumentModel savePdfDocument(MultipartFile file, String folderId) throws IOException {
        if (file.isEmpty() || !Objects.requireNonNull(file.getOriginalFilename()).toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Invalid file. Please upload a non-empty PDF document.");
        }

        String documentId = UUID.randomUUID().toString();
        String safeFilename = documentId + "_" + file.getOriginalFilename().replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
        Path targetPath = pdfsDir.resolve(safeFilename);

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        DocumentModel doc = new DocumentModel(
                documentId,
                file.getOriginalFilename(),
                safeFilename,
                file.getSize(),
                (folderId != null && !folderId.trim().isEmpty() && !folderId.equals("none")) ? folderId : null,
                LocalDateTime.now()
        );

        List<DocumentModel> docs = getDocuments();
        docs.add(doc);
        saveDocuments(docs);
        return doc;
    }

    // BULK UPLOAD WITH DYNAMIC AI FOLDER NAMING & CLUSTERING
    public List<DocumentModel> bulkUploadAndCluster(MultipartFile[] files, int folderCount) throws IOException {
        List<FolderModel> currentFolders = getFolders();
        if (currentFolders.size() < folderCount) {
            setFolderCountAndRecluster(folderCount);
            currentFolders = getFolders();
        }

        for (MultipartFile file : files) {
            if (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                savePdfDocument(file, null);
            }
        }

        List<DocumentModel> allDocs = getDocuments();
        if (!allDocs.isEmpty()) {
            Map<String, String> docTexts = new HashMap<>();
            for (DocumentModel d : allDocs) {
                String text = extractPdfText(d.getId());
                docTexts.put(d.getId(), text.length() > 2000 ? text.substring(0, 2000) : text);
            }
            clusterAndGenerateFolderNamesWithAI(allDocs, docTexts, currentFolders);
        }

        return getDocuments();
    }

    private void clusterAndGenerateFolderNamesWithAI(List<DocumentModel> docs, Map<String, String> docTexts, List<FolderModel> folders) {
        if (folders.isEmpty() || docs.isEmpty()) return;

        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are an expert document topic classifier. You have ").append(folders.size()).append(" folders available:\n");
            for (FolderModel f : folders) {
                prompt.append("- Folder ID: ").append(f.getId()).append("\n");
            }

            prompt.append("\nCategorize the following documents into these ").append(folders.size()).append(" topic folders.\n");
            prompt.append("For each folder, generate a short, clean, descriptive topic name (1 to 3 words max, e.g., 'Circuit Design', 'Analog Electronics', 'Digital Signal', 'Mathematics', 'Syllabus').\n\n");
            prompt.append("Documents:\n");
            for (DocumentModel d : docs) {
                prompt.append("Doc ID: ").append(d.getId()).append("\n");
                prompt.append("Filename: ").append(d.getOriginalFilename()).append("\n");
                prompt.append("Sample Text: ").append(docTexts.get(d.getId()).replaceAll("\\s+", " ")).append("\n---\n");
            }

            prompt.append("\nRespond ONLY with a valid JSON object formatted exactly as below (no markdown block wrapper):\n");
            prompt.append("{\n");
            prompt.append("  \"folderNames\": [ {\"folderId\": \"ID_1\", \"name\": \"Category Name\"} ],\n");
            prompt.append("  \"assignments\": [ {\"docId\": \"DOC_1\", \"folderId\": \"ID_1\"} ]\n");
            prompt.append("}");

            String aiResponse = callGeminiApi(prompt.toString()).trim();
            if (aiResponse.contains("{")) {
                aiResponse = aiResponse.substring(aiResponse.indexOf("{"), aiResponse.lastIndexOf("}") + 1);
            }

            Map<String, Object> result = objectMapper.readValue(aiResponse, new TypeReference<Map<String, Object>>() {});
            
            List<Map<String, String>> folderNames = (List<Map<String, String>>) result.get("folderNames");
            if (folderNames != null) {
                List<FolderModel> allFolders = getFolders();
                for (Map<String, String> fn : folderNames) {
                    String fId = fn.get("folderId");
                    String newName = fn.get("name");
                    for (FolderModel f : allFolders) {
                        if (f.getId().equals(fId) && newName != null && !newName.trim().isEmpty()) {
                            f.setName(newName.replaceAll("[\"']", "").trim());
                        }
                    }
                }
                saveFolders(allFolders);
            }

            List<Map<String, String>> assignments = (List<Map<String, String>>) result.get("assignments");
            if (assignments != null) {
                Map<String, String> docToFolder = new HashMap<>();
                for (Map<String, String> a : assignments) {
                    docToFolder.put(a.get("docId"), a.get("folderId"));
                }
                List<DocumentModel> allDocs = getDocuments();
                for (DocumentModel d : allDocs) {
                    if (docToFolder.containsKey(d.getId())) {
                        d.setFolderId(docToFolder.get(d.getId()));
                    }
                }
                saveDocuments(allDocs);
            }

        } catch (Exception e) {
            try {
                List<DocumentModel> allDocs = getDocuments();
                List<FolderModel> allFolders = getFolders();
                for (int i = 0; i < docs.size(); i++) {
                    String docId = docs.get(i).getId();
                    String folderId = allFolders.get(i % allFolders.size()).getId();
                    for (DocumentModel d : allDocs) {
                        if (d.getId().equals(docId)) {
                            d.setFolderId(folderId);
                        }
                    }
                }
                saveDocuments(allDocs);
            } catch (IOException ignored) {}
        }
    }

    public DocumentModel updateDocument(String id, String newName, String folderId) throws IOException {
        List<DocumentModel> docs = getDocuments();
        for (DocumentModel d : docs) {
            if (d.getId().equals(id)) {
                if (newName != null && !newName.trim().isEmpty()) {
                    d.setOriginalFilename(newName);
                }
                if (folderId != null) {
                    d.setFolderId(folderId.equals("none") ? null : folderId);
                }
                saveDocuments(docs);
                return d;
            }
        }
        throw new RuntimeException("Document not found");
    }

    public void deleteDocument(String id) throws IOException {
        List<DocumentModel> docs = getDocuments();
        DocumentModel target = null;
        for (DocumentModel d : docs) {
            if (d.getId().equals(id)) {
                target = d;
                break;
            }
        }
        if (target != null) {
            docs.remove(target);
            saveDocuments(docs);
            Path filePath = pdfsDir.resolve(target.getStoredFilename());
            Files.deleteIfExists(filePath);
        }
    }

    public Path getDocumentPath(String id) {
        List<DocumentModel> docs = getDocuments();
        for (DocumentModel d : docs) {
            if (d.getId().equals(id)) {
                return pdfsDir.resolve(d.getStoredFilename());
            }
        }
        throw new RuntimeException("Document not found with ID: " + id);
    }

    public String extractPdfText(String id) throws IOException {
        Path pdfPath = getDocumentPath(id);
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text == null || text.trim().isEmpty()) {
                return "No extractable text found in this PDF document.";
            }
            return text;
        }
    }

    // NOTES
    public synchronized List<NoteModel> getNotes() {
        try {
            if (!Files.exists(notesJson)) return new ArrayList<>();
            return objectMapper.readValue(notesJson.toFile(), new TypeReference<List<NoteModel>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private synchronized void saveNotes(List<NoteModel> notes) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(notesJson.toFile(), notes);
    }

    public NoteModel createNote(String title, String content, String folderId) throws IOException {
        NoteModel note = new NoteModel(
                UUID.randomUUID().toString(),
                title,
                content,
                (folderId != null && !folderId.trim().isEmpty()) ? folderId : null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        List<NoteModel> notes = getNotes();
        notes.add(note);
        saveNotes(notes);
        return note;
    }

    public NoteModel updateNote(String id, String title, String content, String folderId) throws IOException {
        List<NoteModel> notes = getNotes();
        for (NoteModel n : notes) {
            if (n.getId().equals(id)) {
                if (title != null) n.setTitle(title);
                if (content != null) n.setContent(content);
                if (folderId != null) n.setFolderId(folderId.equals("none") ? null : folderId);
                n.setUpdatedAt(LocalDateTime.now());
                saveNotes(notes);
                return n;
            }
        }
        throw new RuntimeException("Note not found: " + id);
    }

    public void deleteNote(String id) throws IOException {
        List<NoteModel> notes = getNotes();
        notes.removeIf(n -> n.getId().equals(id));
        saveNotes(notes);
    }

    // AI INTEGRATION WITH GEMINI REST API
    public String generateSummary(String documentId) throws IOException {
        List<DocumentModel> docs = getDocuments();
        DocumentModel targetDoc = docs.stream().filter(d -> d.getId().equals(documentId)).findFirst().orElse(null);
        if (targetDoc == null) {
            throw new RuntimeException("Document not found");
        }

        if (targetDoc.getSummary() != null && !targetDoc.getSummary().trim().isEmpty()) {
            return targetDoc.getSummary();
        }

        String pdfText = extractPdfText(documentId);
        if (pdfText.startsWith("No extractable text")) {
            return "Unable to generate summary: The document contains no readable text or is scanned image-only.";
        }

        if (pdfText.length() > 25000) {
            pdfText = pdfText.substring(0, 25000) + "\n\n[Document truncated for summarization...]";
        }

        String prompt = "You are an expert AI document analyzer. Provide a comprehensive structured summary of the following PDF text.\n" +
                "Include these sections:\n" +
                "1. Overview & Core Subject\n" +
                "2. Main Topics & Key Concepts\n" +
                "3. Important Detailed Points\n" +
                "4. Actionable Key Takeaways\n\n" +
                "Formatting rules (Markdown):\n" +
                "- Use ## headings for each section, each on its own line.\n" +
                "- Put every numbered or bulleted item on its own line.\n" +
                "- Use **bold** for key terms, not for entire paragraphs.\n" +
                "- Use a blank line between sections.\n" +
                "- Do not wrap the whole response in a code fence.\n\n" +
                "PDF Text:\n" + pdfText;

        String aiResponse = callGeminiApi(prompt);
        targetDoc.setSummary(aiResponse);
        saveDocuments(docs);
        return aiResponse;
    }

    public String chatWithDocument(String documentId, String question) throws IOException {
        String pdfText = extractPdfText(documentId);
        if (pdfText.startsWith("No extractable text")) {
            return "This PDF does not contain extractable text to answer questions.";
        }

        if (pdfText.length() > 30000) {
            pdfText = pdfText.substring(0, 30000);
        }

        String prompt = "You are a precise AI research assistant answering questions based SOLELY on the provided document context below.\n" +
                "Formatting & Style Rules:\n" +
                "- Reply in clean Markdown.\n" +
                "- Organize your answer using ## headings and bullet points, with each item on its own line.\n" +
                "- Use **bold** for key concepts or terms.\n" +
                "- Avoid dense, unbroken paragraph walls.\n" +
                "- Answer the user's question directly using ONLY facts present in the text.\n" +
                "- If the document does NOT contain sufficient information to answer the question, clearly state: \"The provided document does not contain enough information to answer this question.\"\n" +
                "- Do NOT fabricate page numbers, quotes, or information not found in the text.\n" +
                "- Do not wrap the whole response in a code fence.\n\n" +
                "DOCUMENT CONTEXT:\n" + pdfText + "\n\n" +
                "USER QUESTION: " + question;

        return callGeminiApi(prompt);
    }

    private String callGeminiApi(String promptText) {
        String[] modelEndpoints = new String[] {
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-3.8-flash",
            "gemini-3.6-flash",
            "gemini-flash-latest"
        };

        for (String model : modelEndpoints) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                Map<String, Object> textPart = Map.of("text", promptText);
                Map<String, Object> parts = Map.of("parts", List.of(textPart));
                Map<String, Object> contents = Map.of("contents", List.of(parts));

                String jsonPayload = objectMapper.writeValueAsString(contents);
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + geminiApiKey;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> map = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) map.get("candidates");
                    if (candidates != null && !candidates.isEmpty()) {
                        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                        List<Map<String, Object>> resParts = (List<Map<String, Object>>) content.get("parts");
                        if (resParts != null && !resParts.isEmpty()) {
                            return (String) resParts.get(0).get("text");
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return "Unable to connect to AI model. Please verify your Gemini API key and network connection.";
    }
}
