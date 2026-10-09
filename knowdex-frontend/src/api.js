import axios from 'axios';

const API_BASE_URL = '/api';

export const folderApi = {
  getAll: () => axios.get(`${API_BASE_URL}/folders`),
  create: (name, description) => axios.post(`${API_BASE_URL}/folders`, { name, description }),
  configureCount: (count) => axios.post(`${API_BASE_URL}/folders/configure-count`, { count }),
  update: (id, name, description) => axios.put(`${API_BASE_URL}/folders/${id}`, { name, description }),
  delete: (id) => axios.delete(`${API_BASE_URL}/folders/${id}`)
};

export const documentApi = {
  getAll: () => axios.get(`${API_BASE_URL}/documents`),
  upload: (file, folderId) => {
    const formData = new FormData();
    formData.append('file', file);
    if (folderId) formData.append('folderId', folderId);
    return axios.post(`${API_BASE_URL}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  bulkUpload: (files, folderCount) => {
    const formData = new FormData();
    for (let i = 0; i < files.length; i++) {
      formData.append('files', files[i]);
    }
    formData.append('folderCount', folderCount);
    return axios.post(`${API_BASE_URL}/documents/bulk`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  update: (id, originalFilename, folderId) => axios.put(`${API_BASE_URL}/documents/${id}`, { originalFilename, folderId }),
  delete: (id) => axios.delete(`${API_BASE_URL}/documents/${id}`),
  getViewUrl: (id) => `${API_BASE_URL}/documents/${id}/view`,
  summarize: (id) => axios.post(`${API_BASE_URL}/documents/${id}/summarize`),
  chat: (id, question) => axios.post(`${API_BASE_URL}/documents/${id}/chat`, { question })
};

export const noteApi = {
  getAll: () => axios.get(`${API_BASE_URL}/notes`),
  create: (title, content, folderId) => axios.post(`${API_BASE_URL}/notes`, { title, content, folderId }),
  update: (id, title, content, folderId) => axios.put(`${API_BASE_URL}/notes/${id}`, { title, content, folderId }),
  delete: (id) => axios.delete(`${API_BASE_URL}/notes/${id}`)
};
