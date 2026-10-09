package com.knowdex.controller;

import com.knowdex.model.DocumentModel;
import com.knowdex.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final StorageService storageService;

    public DocumentController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping
    public List<DocumentModel> getDocuments() {
        return storageService.getDocuments();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) String folderId) {
        try {
            DocumentModel doc = storageService.savePdfDocument(file, folderId);
            return ResponseEntity.ok(doc);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> bulkUpload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "folderCount", defaultValue = "8") int folderCount) {
        try {
            List<DocumentModel> docs = storageService.bulkUploadAndCluster(files, folderCount);
            return ResponseEntity.ok(docs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentModel> updateDocument(
            @PathVariable String id,
            @RequestBody Map<String, String> payload) throws IOException {
        String name = payload.get("originalFilename");
        String folderId = payload.get("folderId");
        return ResponseEntity.ok(storageService.updateDocument(id, name, folderId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) throws IOException {
        storageService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> viewDocument(@PathVariable String id) throws IOException {
        Path filePath = storageService.getDocumentPath(id);
        Resource resource = new UrlResource(filePath.toUri());

        if (resource.exists() || resource.isReadable()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/summarize")
    public ResponseEntity<?> summarizeDocument(@PathVariable String id) {
        try {
            String summary = storageService.generateSummary(id);
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<?> chatWithDocument(
            @PathVariable String id,
            @RequestBody Map<String, String> payload) {
        try {
            String question = payload.get("question");
            if (question == null || question.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Question cannot be empty"));
            }
            String answer = storageService.chatWithDocument(id, question);
            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
