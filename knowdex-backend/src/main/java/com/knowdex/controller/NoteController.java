package com.knowdex.controller;

import com.knowdex.model.NoteModel;
import com.knowdex.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final StorageService storageService;

    public NoteController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping
    public List<NoteModel> getNotes() {
        return storageService.getNotes();
    }

    @PostMapping
    public ResponseEntity<NoteModel> createNote(@RequestBody Map<String, String> payload) throws IOException {
        String title = payload.get("title");
        String content = payload.get("content");
        String folderId = payload.get("folderId");
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(storageService.createNote(title, content, folderId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteModel> updateNote(@PathVariable String id, @RequestBody Map<String, String> payload) throws IOException {
        String title = payload.get("title");
        String content = payload.get("content");
        String folderId = payload.get("folderId");
        return ResponseEntity.ok(storageService.updateNote(id, title, content, folderId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNote(@PathVariable String id) throws IOException {
        storageService.deleteNote(id);
        return ResponseEntity.noContent().build();
    }
}
