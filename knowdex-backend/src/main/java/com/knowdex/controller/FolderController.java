package com.knowdex.controller;

import com.knowdex.model.FolderModel;
import com.knowdex.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final StorageService storageService;

    public FolderController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping
    public List<FolderModel> getFolders() {
        return storageService.getFolders();
    }

    @PostMapping
    public ResponseEntity<FolderModel> createFolder(@RequestBody Map<String, String> payload) throws IOException {
        String name = payload.get("name");
        String description = payload.get("description");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(storageService.createFolder(name, description));
    }

    @PostMapping("/configure-count")
    public ResponseEntity<List<FolderModel>> configureFolderCount(@RequestBody Map<String, Integer> payload) throws IOException {
        Integer count = payload.get("count");
        if (count == null || count < 1) count = 8;
        return ResponseEntity.ok(storageService.setFolderCountAndRecluster(count));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FolderModel> updateFolder(@PathVariable String id, @RequestBody Map<String, String> payload) throws IOException {
        String name = payload.get("name");
        String description = payload.get("description");
        return ResponseEntity.ok(storageService.updateFolder(id, name, description));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String id) throws IOException {
        storageService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }
}
