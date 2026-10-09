import React, { useState, useEffect, useRef } from 'react';
import { 
  Folder, FileText, Upload, Sparkles, Send, Eye, Trash2, Edit2, Plus,
  Bot, Settings, X, ChevronRight, Loader2 
} from 'lucide-react';
import { folderApi, documentApi } from './api';
import MarkdownText from './MarkdownText';

export default function App() {
  const [folders, setFolders] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [folderCount, setFolderCount] = useState(8);
  const [selectedFolder, setSelectedFolder] = useState(null);
  
  // Modals & Editing State
  const [showCreateFolderModal, setShowCreateFolderModal] = useState(false);
  const [editingFolder, setEditingFolder] = useState(null);
  const [folderNameInput, setFolderNameInput] = useState('');

  // Loading States
  const [loadingState, setLoadingState] = useState({ active: false, title: '', subtitle: '' });

  // AI & Viewer States
  const [activeDoc, setActiveDoc] = useState(null);
  const [chatMessages, setChatMessages] = useState([]);
  const [chatQuestion, setChatQuestion] = useState('');
  const [isChatting, setIsChatting] = useState(false);
  const [summaryText, setSummaryText] = useState('');
  const [isSummarizing, setIsSummarizing] = useState(false);
  const [activeAiTab, setActiveAiTab] = useState('chat');
  const [dragOverFolderId, setDragOverFolderId] = useState(null);

  const fileInputRef = useRef(null);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [fRes, dRes] = await Promise.all([
        folderApi.getAll(),
        documentApi.getAll()
      ]);
      setFolders(fRes.data);
      setDocuments(dRes.data);
      if (fRes.data.length > 0 && !selectedFolder) {
        setSelectedFolder(fRes.data[0]);
      }
    } catch (err) {
      console.error('Failed to load data:', err);
    }
  };

  // Configure number of folders
  const handleConfigFolderCount = async (newCount) => {
    const count = parseInt(newCount, 10);
    if (isNaN(count) || count < 1 || count > 16) return;
    setFolderCount(count);
    setLoadingState({ active: true, title: 'Updating Folders...', subtitle: 'Re-arranging layout' });
    try {
      const res = await folderApi.configureCount(count);
      setFolders(res.data);
      if (res.data.length > 0) setSelectedFolder(res.data[0]);
    } catch (err) {
      console.error('Failed to configure folder count', err);
    } finally {
      setLoadingState({ active: false, title: '', subtitle: '' });
    }
  };

  // Create Folder
  const handleCreateFolder = async (e) => {
    e.preventDefault();
    if (!folderNameInput.trim()) return;
    setLoadingState({ active: true, title: 'Creating Folder...', subtitle: 'Saving to storage' });
    try {
      await folderApi.create(folderNameInput.trim(), 'User created folder');
      setFolderNameInput('');
      setShowCreateFolderModal(false);
      await loadData();
    } catch (err) {
      alert('Failed to create folder');
    } finally {
      setLoadingState({ active: false, title: '', subtitle: '' });
    }
  };

  // Rename Folder
  const handleRenameFolder = async (e) => {
    e.preventDefault();
    if (!editingFolder || !folderNameInput.trim()) return;
    setLoadingState({ active: true, title: 'Renaming Folder...', subtitle: 'Updating folder metadata' });
    try {
      await folderApi.update(editingFolder.id, folderNameInput.trim(), editingFolder.description);
      setEditingFolder(null);
      setFolderNameInput('');
      await loadData();
    } catch (err) {
      alert('Failed to rename folder');
    } finally {
      setLoadingState({ active: false, title: '', subtitle: '' });
    }
  };

  // Delete Folder
  const handleDeleteFolder = async (e, folderId) => {
    e.stopPropagation();
    if (window.confirm('Delete this folder? Files in this folder will be unallocated.')) {
      setLoadingState({ active: true, title: 'Deleting Folder...', subtitle: 'Cleaning folder reference' });
      try {
        await folderApi.delete(folderId);
        if (selectedFolder?.id === folderId) {
          setSelectedFolder(null);
        }
        await loadData();
      } catch (err) {
        alert('Failed to delete folder');
      } finally {
        setLoadingState({ active: false, title: '', subtitle: '' });
      }
    }
  };

  // Delete File
  const handleDeleteFile = async (e, docId) => {
    e.stopPropagation();
    if (window.confirm('Permanently delete this PDF document?')) {
      setLoadingState({ active: true, title: 'Deleting File...', subtitle: 'Removing PDF from local disk' });
      try {
        await documentApi.delete(docId);
        if (activeDoc?.id === docId) {
          setActiveDoc(null);
        }
        await loadData();
      } catch (err) {
        alert('Failed to delete file');
      } finally {
        setLoadingState({ active: false, title: '', subtitle: '' });
      }
    }
  };

  // Upload handler (Single or Bulk with Loading Screen)
  const handleFileUpload = async (e) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    if (files.length === 1) {
      setLoadingState({ active: true, title: 'Uploading PDF Document...', subtitle: 'Extracting text and indexing file' });
    } else {
      setLoadingState({ active: true, title: 'Bulk Uploading & AI Clustering...', subtitle: 'Gemini AI is analyzing document topics and allocating folders' });
    }

    try {
      if (files.length === 1) {
        await documentApi.upload(files[0], selectedFolder ? selectedFolder.id : null);
      } else {
        await documentApi.bulkUpload(files, folderCount);
      }
      await loadData();
    } catch (err) {
      const errMsg = err.response?.data?.error || err.message || 'Upload failed.';
      alert('Upload failed: ' + errMsg);
    } finally {
      setLoadingState({ active: false, title: '', subtitle: '' });
    }
  };

  // Drag and Drop handlers
  const handleDragStart = (e, docId) => {
    e.dataTransfer.setData('text/plain', docId);
  };

  const handleDragOver = (e, folderId) => {
    e.preventDefault();
    setDragOverFolderId(folderId);
  };

  const handleDragLeave = () => {
    setDragOverFolderId(null);
  };

  const handleDrop = async (e, targetFolderId) => {
    e.preventDefault();
    setDragOverFolderId(null);
    const docId = e.dataTransfer.getData('text/plain');
    if (!docId) return;

    setLoadingState({ active: true, title: 'Moving File...', subtitle: 'Updating folder assignment' });
    try {
      await documentApi.update(docId, null, targetFolderId);
      await loadData();
    } catch (err) {
      console.error('Failed to move document', err);
    } finally {
      setLoadingState({ active: false, title: '', subtitle: '' });
    }
  };

  // AI Chat & Summary
  const handleSelectDoc = (doc) => {
    setActiveDoc(doc);
    setSummaryText(doc.summary || '');
    setChatMessages([]);
  };

  const handleSendChat = async (e) => {
    e.preventDefault();
    if (!chatQuestion.trim() || !activeDoc || isChatting) return;

    const q = chatQuestion;
    setChatQuestion('');
    setChatMessages(prev => [...prev, { role: 'user', text: q }]);
    setIsChatting(true);

    try {
      const res = await documentApi.chat(activeDoc.id, q);
      setChatMessages(prev => [...prev, { role: 'assistant', text: res.data.answer }]);
    } catch (err) {
      setChatMessages(prev => [...prev, { role: 'assistant', text: 'Error connecting to AI service.' }]);
    } finally {
      setIsChatting(false);
    }
  };

  const handleGenerateSummary = async () => {
    if (!activeDoc) return;
    setIsSummarizing(true);
    try {
      const res = await documentApi.summarize(activeDoc.id);
      setSummaryText(res.data.summary);
      loadData();
    } catch (err) {
      setSummaryText('Failed to generate summary.');
    } finally {
      setIsSummarizing(false);
    }
  };

  // Radial Folder Math
  const radius = 240;
  const getFolderPosition = (index, total) => {
    const angle = (index / total) * 2 * Math.PI - Math.PI / 2;
    const x = radius * Math.cos(angle);
    const y = radius * Math.sin(angle);
    return { x, y };
  };

  const displayedDocs = selectedFolder 
    ? documents.filter(d => d.folderId === selectedFolder.id)
    : documents;

  return (
    <div className="workspace-layout">
      {/* HIDDEN FILE INPUT */}
      <input 
        type="file" 
        ref={fileInputRef} 
        style={{ display: 'none' }} 
        multiple 
        accept="application/pdf"
        onChange={handleFileUpload}
      />

      {/* GLOBAL LOADING SCREEN OVERLAY */}
      {loadingState.active && (
        <div className="modal-overlay">
          <div className="loading-box">
            <div className="spinner"></div>
            <div>
              <h3 style={{ fontSize: '1.2rem', color: '#fff', marginBottom: '0.3rem' }}>{loadingState.title}</h3>
              <p style={{ fontSize: '0.85rem', color: '#94a3b8' }}>{loadingState.subtitle}</p>
            </div>
          </div>
        </div>
      )}

      {/* LEFT CANVAS AREA */}
      <div className="canvas-container">
        {/* HEADER CONTROLS */}
        <div className="canvas-header">
          <div className="brand-title">Knowdex</div>
          <div className="folder-config-box">
            <Settings size={15} />
            <span>Folders:</span>
            <input 
              type="number" 
              value={folderCount} 
              min="1" 
              max="16" 
              onChange={(e) => handleConfigFolderCount(e.target.value)} 
            />
          </div>
          <button 
            className="btn btn-secondary" 
            style={{ fontSize: '0.8rem', padding: '0.4rem 0.8rem' }}
            onClick={() => {
              setFolderNameInput('');
              setShowCreateFolderModal(true);
            }}
          >
            <Plus size={14} /> New Folder
          </button>
        </div>

        {/* SVG CONNECTING LINES */}
        <svg className="lines-overlay">
          {folders.map((folder, index) => {
            const pos = getFolderPosition(index, folders.length);
            const windowW = window.innerWidth - 460;
            const windowH = window.innerHeight;
            const cx = windowW / 2;
            const cy = windowH / 2;
            return (
              <line 
                key={folder.id} 
                x1={cx} 
                y1={cy} 
                x2={cx + pos.x} 
                y2={cy + pos.y} 
              />
            );
          })}
        </svg>

        {/* CENTRAL UPLOAD HUB */}
        <div className="upload-hub" onClick={() => fileInputRef.current.click()}>
          <Upload size={36} color="#fff" />
          <h3>UPLOAD</h3>
          <p>Single or Bulk PDF</p>
        </div>

        {/* RADIAL FOLDERS */}
        {folders.map((folder, index) => {
          const pos = getFolderPosition(index, folders.length);
          const count = documents.filter(d => d.folderId === folder.id).length;
          const isSelected = selectedFolder?.id === folder.id;
          const isDragOver = dragOverFolderId === folder.id;

          return (
            <div 
              key={folder.id}
              className={`radial-folder ${isSelected ? 'active' : ''} ${isDragOver ? 'drag-over' : ''}`}
              style={{
                transform: `translate(${pos.x}px, ${pos.y}px)`
              }}
              onClick={() => setSelectedFolder(folder)}
              onDragOver={(e) => handleDragOver(e, folder.id)}
              onDragLeave={handleDragLeave}
              onDrop={(e) => handleDrop(e, folder.id)}
            >
              <div style={{ display: 'flex', width: '100%', justifyContent: 'space-between', alignItems: 'center' }}>
                <button 
                  className="action-btn" 
                  title="Rename Folder"
                  onClick={(e) => {
                    e.stopPropagation();
                    setEditingFolder(folder);
                    setFolderNameInput(folder.name);
                  }}
                >
                  <Edit2 size={12} />
                </button>
                <Folder size={24} color={isSelected ? '#6366f1' : '#8b5cf6'} />
                <button 
                  className="action-btn" 
                  title="Delete Folder"
                  onClick={(e) => handleDeleteFolder(e, folder.id)}
                >
                  <Trash2 size={12} color="#ef4444" />
                </button>
              </div>

              <div className="folder-name" title={folder.name}>{folder.name}</div>
              <div className="folder-count-badge">{count} {count === 1 ? 'file' : 'files'}</div>
            </div>
          );
        })}
      </div>

      {/* RIGHT PANEL */}
      <div className="right-panel">
        <div className="panel-header">
          <span>{selectedFolder ? selectedFolder.name : 'All Files'}</span>
          <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>Drag files to move</span>
        </div>

        {/* FILE LIST */}
        <div className="file-list">
          {displayedDocs.length === 0 ? (
            <p style={{ color: '#94a3b8', fontSize: '0.9rem', textAlign: 'center', marginTop: '2rem' }}>
              No files in this folder.<br />Click Upload or drag files here.
            </p>
          ) : (
            displayedDocs.map(doc => (
              <div 
                key={doc.id} 
                className="file-item-card"
                draggable
                onDragStart={(e) => handleDragStart(e, doc.id)}
                onClick={() => handleSelectDoc(doc)}
                style={{
                  borderColor: activeDoc?.id === doc.id ? '#6366f1' : 'rgba(255,255,255,0.1)'
                }}
              >
                <div className="file-info">
                  <FileText size={20} color="#6366f1" />
                  <div>
                    <div className="file-title">{doc.originalFilename}</div>
                    <div className="file-size">{(doc.fileSize / 1024).toFixed(1)} KB</div>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: '0.3rem', alignItems: 'center' }}>
                  <button 
                    className="btn btn-secondary" 
                    style={{ padding: '0.3rem 0.6rem', fontSize: '0.75rem' }}
                    onClick={(e) => { e.stopPropagation(); handleSelectDoc(doc); }}
                  >
                    <Eye size={12} /> Select
                  </button>
                  <button 
                    className="action-btn"
                    title="Delete File"
                    onClick={(e) => handleDeleteFile(e, doc.id)}
                  >
                    <Trash2 size={15} color="#ef4444" />
                  </button>
                </div>
              </div>
            ))
          )}
        </div>

        {/* AI DRAWER */}
        {activeDoc && (
          <div className="ai-drawer">
            <div className="panel-header" style={{ fontSize: '0.9rem' }}>
              <span>AI Panel: {activeDoc.originalFilename}</span>
              <div style={{ display: 'flex', gap: '0.4rem' }}>
                <button 
                  className={`btn ${activeAiTab === 'chat' ? 'btn-primary' : 'btn-secondary'}`}
                  style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                  onClick={() => setActiveAiTab('chat')}
                >
                  <Bot size={12} /> Chat
                </button>
                <button 
                  className={`btn ${activeAiTab === 'summary' ? 'btn-primary' : 'btn-secondary'}`}
                  style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                  onClick={() => {
                    setActiveAiTab('summary');
                    if (!summaryText) handleGenerateSummary();
                  }}
                >
                  <Sparkles size={12} /> Summary
                </button>
              </div>
            </div>

            {activeAiTab === 'chat' ? (
              <>
                <div className="chat-history">
                  {chatMessages.length === 0 && (
                    <p style={{ color: '#94a3b8', fontSize: '0.85rem', textAlign: 'center' }}>
                      Ask anything about this document...
                    </p>
                  )}
                  {chatMessages.map((msg, i) => (
                    <div key={i} className={`chat-bubble ${msg.role}`}>
                      {msg.role === 'assistant' ? <MarkdownText text={msg.text} /> : msg.text}
                    </div>
                  ))}
                  {isChatting && <div className="chat-bubble assistant">Analyzing...</div>}
                </div>

                <form className="chat-input-bar" onSubmit={handleSendChat}>
                  <input 
                    type="text" 
                    placeholder="Ask question..." 
                    value={chatQuestion}
                    onChange={(e) => setChatQuestion(e.target.value)}
                  />
                  <button type="submit" className="btn btn-primary" disabled={isChatting}>
                    <Send size={14} />
                  </button>
                </form>
              </>
            ) : (
              <div className="summary-body">
                {isSummarizing
                  ? 'Generating summary with Gemini AI...'
                  : (summaryText ? <MarkdownText text={summaryText} /> : 'No summary available.')}
              </div>
            )}
          </div>
        )}
      </div>

      {/* CREATE FOLDER MODAL */}
      {showCreateFolderModal && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">Create New Folder</div>
            <form onSubmit={handleCreateFolder}>
              <div className="form-group">
                <label>Folder Name</label>
                <input 
                  type="text" 
                  required 
                  autoFocus
                  placeholder="e.g. Finance, Research..." 
                  value={folderNameInput} 
                  onChange={(e) => setFolderNameInput(e.target.value)} 
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowCreateFolderModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Create</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* RENAME FOLDER MODAL */}
      {editingFolder && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">Rename Folder</div>
            <form onSubmit={handleRenameFolder}>
              <div className="form-group">
                <label>Folder Name</label>
                <input 
                  type="text" 
                  required 
                  autoFocus
                  value={folderNameInput} 
                  onChange={(e) => setFolderNameInput(e.target.value)} 
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setEditingFolder(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Save</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
