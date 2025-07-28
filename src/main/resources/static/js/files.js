import { formatSize, formatDate, showError, showSuccess, showWarning, showInfo, showConfirm } from './utils.js';

const API_BASE = 'http://localhost:8080';
const pageSize = 50;
let currentPage = 1;
let totalFiles = 0;
let currentBucket = '';

// 获取当前 bucket
function getCurrentBucket() {
  const url = new URL(window.location.href);
  return url.searchParams.get('bucket');
}

// 返回 bucket 列表页
function backToBuckets() {
  window.location.href = window.location.origin + '/pages/bucket.html';
}

// 新增：刷新单个文件信息
function refreshSingleFile(fileKey) {
  const currentBucket = getCurrentBucket();
  if (!currentBucket) {
    showError('未指定 bucket');
    return;
  }
  
  fetch(`${API_BASE}/buckets/${currentBucket}/files/${fileKey}`)
    .then(res => {
      if (!res.ok) throw new Error('获取文件信息失败');
      return res.json();
    })
    .then(fileData => {
      updateFileRow(fileKey, fileData);
    })
    .catch(e => showError(e.message));
}

// 新增：更新文件行内容
function updateFileRow(fileKey, fileData) {
  const row = document.querySelector(`tr[data-filekey="${fileKey}"]`);
  if (row) {
    row.innerHTML = generateFileRowHTML(fileData);
  }
}

// 新增：将新文件添加到列表最上端
function addNewFileToTop(fileData) {
  const tbody = document.querySelector('#file-table tbody');
  
  // 如果当前显示的是"暂无文件"，先清空
  if (tbody.innerHTML.includes('暂无文件')) {
    tbody.innerHTML = '';
  }
  
  // 创建新的文件行
  const tr = document.createElement('tr');
  tr.setAttribute('data-filekey', fileData.fileKey);
  tr.innerHTML = generateFileRowHTML(fileData);
  
  // 添加到表格的最上端
  if (tbody.firstChild) {
    tbody.insertBefore(tr, tbody.firstChild);
  } else {
    tbody.appendChild(tr);
  }
  
  // 添加高亮效果
  tr.classList.add('new-file-highlight');
  
  // 2秒后移除动画类
  setTimeout(() => {
    tr.classList.remove('new-file-highlight');
  }, 2000);
}

// 新增：生成文件行HTML
function generateFileRowHTML(file) {
  return `
    <td class="filename-cell" title="${file.fileName}">${file.fileName}</td>
    <td class="filekey-cell" title="${file.fileKey}">${file.fileKey}</td>
    <td>${file.zipped ? '是' : '否'}</td>
    <td>${formatSize(file.size)}</td>
    <td>${file.status}</td>
    <td>${formatDate(file.updateTime)}</td>
    <td>${file.unfreezeTime ? formatDate(file.unfreezeTime) : '-'}</td>
    <td>
      <div class="d-flex flex-row align-items-center mb-1 gap-2">
        <button class='btn btn-sm btn-warning' onclick="unfreezeFile('${file.fileKey}')" ${!file.restorable ? 'disabled' : ''} title="${file.restorable ? '解冻文件' : '该文件不可解冻'}">解冻</button>
        <button class="btn btn-sm btn-primary download-btn" data-filekey="${file.fileKey}" onclick="downloadFile('${file.fileKey}')" ${file.downloadable === false ? 'disabled title=\'该文件不可下载\'' : ''}>下载</button>
        <button class="btn btn-sm btn-danger" onclick="deleteFile('${file.fileKey}')" ${!file.deletable ? 'disabled' : ''} title="${file.deletable ? '删除文件' : '该文件不可删除'}">删除</button>
      </div>
      <div class="d-flex flex-row align-items-center gap-2">
        ${file.localPath ? `<button class='btn btn-outline-secondary btn-sm' onclick='copyLocalPath("${file.localPath}")' title='复制本地路径'>复制路径</button>` : ''}
        ${file.localPath ? `<button class='btn btn-outline-danger btn-sm' onclick='releaseLocalFile("${file.fileKey}", "${file.localPath}")' title='释放本地文件'>释放本地</button>` : ''}
        ${file.localPath ? `<span class="ms-1" title="${file.localPathExists === undefined ? '未知' : (file.localPathExists ? '本地文件存在' : '本地文件不存在')}">
          ${file.localPathExists === undefined ? '' : (file.localPathExists ? '<span style=\'color:green;font-size:1.2em;\'>&#10003;</span>' : `<span style=\'color:red;font-size:1.2em;cursor:pointer;\' onclick=\'handleLocalPathMissingDownload("${file.fileKey}")\'>&#10007;</span>`)}
        </span>` : ''}
        <span class="download-progress text-info small" id="download-progress-${file.fileKey}"></span>
      </div>
    </td>
  `;
}

// 渲染文件列表
function renderFiles(files) {
  const tbody = document.querySelector('#file-table tbody');
  tbody.innerHTML = '';
  
  // 新增：判断是否有DB_ONLY或TO_BE_DELETED的文件
  const confirmDeleteBtn = document.getElementById('sync-local-btn');
  const hasDeletable = files.some(f => f.status === 'DB_ONLY' || f.status === 'TO_BE_DELETED');
  if (confirmDeleteBtn) {
    confirmDeleteBtn.disabled = !hasDeletable;
    confirmDeleteBtn.title = hasDeletable ? '' : '没有可确认删除的文件';
  }

  if (!files.length) {
    tbody.innerHTML = '<tr><td colspan="7" class="text-center text-secondary">暂无文件</td></tr>';
    return;
  }

  files.forEach(file => {
    const tr = document.createElement('tr');
    tr.setAttribute('data-filekey', file.fileKey); // 新增：为每行添加fileKey属性
    tr.innerHTML = generateFileRowHTML(file);
    tbody.appendChild(tr);
  });
}

// 获取文件列表
function listFiles() {
  currentBucket = getCurrentBucket();
  if (!currentBucket) {
    showError('未指定 bucket');
    return Promise.reject(new Error('未指定 bucket'));
  }
  
  document.getElementById('current-bucket').textContent = currentBucket;
  
  // 添加loading状态
  const tbody = document.querySelector('#file-table tbody');
  tbody.innerHTML = '<tr><td colspan="7" class="text-center">加载中...</td></tr>';
  
  return fetch(`${API_BASE}/buckets/${currentBucket}/files?page=${currentPage - 1}&pageSize=${pageSize}`)
    .then(res => {
      if (!res.ok) throw new Error('获取文件列表失败');
      return res.json();
    })
    .then(data => {
      if (data && Array.isArray(data.files)) {
        totalFiles = data.total || 0;
        window.allFiles = data.files; // 缓存到全局变量
        renderFiles(data.files);
        renderPagination({
          totalPages: Math.ceil(data.total / data.pageSize),
          number: data.page,
          first: data.page === 0,
          last: (data.page + 1) * data.pageSize >= data.total
        });
      } else {
        throw new Error('响应格式错误');
      }
    })
    .catch(e => {
      showError(e.message);
      throw e;
    });
}

// 渲染分页
function renderPagination(data) {
  if (!data) return;
  
  const {
    totalPages = 0,
    number: currentPageFromServer = 0,
    first = true,
    last = true
  } = data;
  
  // 更新当前页码（后端返回的页码从0开始，我们显示从1开始）
  currentPage = currentPageFromServer + 1;
  
  const pagination = document.getElementById('pagination');
  pagination.innerHTML = '';
  
  if (totalPages <= 1) return;
  
  const ul = document.createElement('ul');
  ul.className = 'pagination justify-content-center';
  
  // 首页
  const firstLi = document.createElement('li');
  firstLi.className = `page-item ${first ? 'disabled' : ''}`;
  firstLi.innerHTML = `<a class="page-link" href="#" onclick="event.preventDefault(); ${first ? '' : 'changePage(1)'}">首页</a>`;
  ul.appendChild(firstLi);
  
  // 上一页
  const prevLi = document.createElement('li');
  prevLi.className = `page-item ${first ? 'disabled' : ''}`;
  prevLi.innerHTML = `<a class="page-link" href="#" onclick="event.preventDefault(); ${first ? '' : `changePage(${currentPage - 1})`}">上一页</a>`;
  ul.appendChild(prevLi);
  
  // 页码
  // 显示当前页码前后各2页
  const startPage = Math.max(1, currentPage - 2);
  const endPage = Math.min(totalPages, currentPage + 2);
  
  if (startPage > 1) {
    ul.appendChild(createPageItem('...', null, true));
  }
  
  for (let i = startPage; i <= endPage; i++) {
    const li = document.createElement('li');
    li.className = `page-item ${currentPage === i ? 'active' : ''}`;
    li.innerHTML = `<a class="page-link" href="#" onclick="event.preventDefault(); changePage(${i})">${i}</a>`;
    ul.appendChild(li);
  }
  
  if (endPage < totalPages) {
    ul.appendChild(createPageItem('...', null, true));
  }
  
  // 下一页
  const nextLi = document.createElement('li');
  nextLi.className = `page-item ${last ? 'disabled' : ''}`;
  nextLi.innerHTML = `<a class="page-link" href="#" onclick="event.preventDefault(); ${last ? '' : `changePage(${currentPage + 1})`}">下一页</a>`;
  ul.appendChild(nextLi);
  
  // 末页
  const lastLi = document.createElement('li');
  lastLi.className = `page-item ${last ? 'disabled' : ''}`;
  lastLi.innerHTML = `<a class="page-link" href="#" onclick="event.preventDefault(); ${last ? '' : `changePage(${totalPages})`}">末页</a>`;
  ul.appendChild(lastLi);
  
  pagination.appendChild(ul);
  
  // 添加页码信息
  const pageInfo = document.createElement('div');
  pageInfo.className = 'text-center mt-2';
  pageInfo.innerHTML = `第 ${currentPage} 页 / 共 ${totalPages} 页（共 ${totalFiles} 条记录）`;
  pagination.appendChild(pageInfo);
}

// 创建分页按钮
function createPageItem(text, page, disabled = false) {
  const li = document.createElement('li');
  li.className = `page-item ${disabled ? 'disabled' : ''}`;
  if (page) {
    li.innerHTML = `<a class="page-link" href="#" onclick="changePage(${page})">${text}</a>`;
  } else {
    li.innerHTML = `<span class="page-link">${text}</span>`;
  }
  return li;
}

// 切换页码
function changePage(page) {
  if (page < 1) return;
  currentPage = page;
  listFiles();
}

// 下载文件（异步下载任务）
function downloadFile(fileKey) {
  // 只要fileKey存在即可，不依赖文件对象
  if (!fileKey) {
    showError('未指定文件key');
    return;
  }
  const currentBucket = getCurrentBucket();
  if (!currentBucket) {
    showError('未指定 bucket');
    return;
  }
  // 优化按钮选择器，避免依赖onclick字符串
  const btn = document.querySelector(`.download-btn[data-filekey="${fileKey}"]`);
  if (btn) {
    btn.disabled = true;
    btn.textContent = '下载准备中...';
  }
  fetch(`${API_BASE}/buckets/${currentBucket}/files/download-task/${fileKey}`, { method: 'POST' })
    .then(res => {
      if (!res.ok) throw new Error('无法触发下载任务');
      showDownloadProgress('下载准备中...', fileKey);
      pollDownloadStatus(fileKey, btn);
    })
    .catch(e => {
      if (btn) {
        btn.disabled = false;
        btn.textContent = '下载';
      }
      showDownloadProgress('', fileKey);
      showError(e.message);
    });
}

function pollDownloadStatus(fileKey, btn, tryCount = 0) {
  if (tryCount > 120) {
    if (btn) {
      btn.disabled = false;
      btn.textContent = '下载';
    }
    showDownloadProgress('', fileKey);
    showError('下载超时，请重试');
    return;
  }
  const currentBucket = getCurrentBucket();
  if (!currentBucket) {
    if (btn) {
      btn.disabled = false;
      btn.textContent = '下载';
    }
    showDownloadProgress('', fileKey);
    showError('未指定 bucket');
    return;
  }
  fetch(`${API_BASE}/buckets/${currentBucket}/files/download-task-status/${fileKey}`)
    .then(res => {
      if (!res.ok) throw new Error('无法获取下载进度');
      return res.json();
    })
    .then(data => {
      if (data.status === 'success') {
        showDownloadProgress('下载成功', fileKey);
        if (btn) {
          btn.disabled = false;
          btn.textContent = '下载';
        }
        // 自动触发浏览器下载动作
        if (data.downloadUrl) {
          triggerBrowserDownload(data.downloadUrl, fileKey);
        }
        setTimeout(() => { listFiles(); }, 1000); // 下载成功后刷新文件列表
      } else if (data.status === 'failed') {
        if (btn) {
          btn.disabled = false;
          btn.textContent = '下载';
        }
        showDownloadProgress('下载失败', fileKey);
        showError('下载失败');
      } else if (data.status === 'downloading') {
        showDownloadProgress(`下载中... ${data.progress || 0}%`, fileKey);
        setTimeout(() => pollDownloadStatus(fileKey, btn, tryCount + 1), 3000);
      } else {
        showDownloadProgress('', fileKey);
        showError('未知下载状态');
        if (btn) {
          btn.disabled = false;
          btn.textContent = '下载';
        }
      }
    })
    .catch(() => setTimeout(() => pollDownloadStatus(fileKey, btn, tryCount + 1), 3000));
}

function triggerBrowserDownload(url, fileKey) {
  // 创建隐藏a标签自动下载
  const a = document.createElement('a');
  a.href = url;
  a.download = '';
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  setTimeout(() => document.body.removeChild(a), 100);
}

// 下载进度展示（可自定义UI，这里简单用alert和console）
function showDownloadProgress(msg, fileKey) {
  // 在对应行的下载按钮右侧显示进度
  const span = document.getElementById(`download-progress-${fileKey}`);
  if (!span) return;
  span.textContent = msg;
  // 下载成功或失败时自动清空
  if (msg.includes('成功') || msg.includes('失败')) {
    setTimeout(() => { span.textContent = ''; }, 2000);
  }
}

// 删除文件
function deleteFile(fileKey) {
  showConfirm('确定要删除该文件吗？', () => {
    const currentBucket = getCurrentBucket();
    if (!currentBucket) {
      showError('未指定 bucket');
      return;
    }
    fetch(`${API_BASE}/buckets/${currentBucket}/files/${fileKey}`, { method: 'DELETE' })
      .then(res => {
        if (!res.ok) throw new Error('删除文件失败');
        showSuccess('删除操作已提交');
        // 删除后刷新该文件的状态
        refreshSingleFile(fileKey);
      })
      .catch(e => showError(e.message));
  });
}



// 本地同步（改为确认删除）
function confirmDeleteFiles() {
  const confirmBtn = document.getElementById('sync-local-btn');
  const confirmIcon = confirmBtn.querySelector('.sync-icon');
  const currentBucket = getCurrentBucket();
  if (!currentBucket) {
    showError('未指定 bucket');
    return;
  }
  confirmBtn.disabled = true;
  confirmIcon.classList.add('spinning');
  fetch(`${API_BASE}/files/confirm-delete/${currentBucket}`, {
    method: 'POST'
  })
    .then(res => {
      if (!res.ok) throw new Error('确认删除失败');
      showSuccess('确认删除成功');
      listFiles();
    })
    .catch(e => showError(e.message))
    .finally(() => {
      confirmBtn.disabled = false;
      confirmIcon.classList.remove('spinning');
    });
}

// 远程同步
function syncRemoteFiles() {
  const syncBtn = document.getElementById('sync-remote-btn');
  const syncIcon = syncBtn.querySelector('.sync-icon');
  const currentBucket = getCurrentBucket();
  if (!currentBucket) {
    showError('未指定 bucket');
    return;
  }
  syncBtn.disabled = true;
  syncIcon.classList.add('spinning');
  fetch(`${API_BASE}/files/sync-remote/${currentBucket}`, {
    method: 'POST'
  })
    .then(res => {
      if (!res.ok) throw new Error('远程同步失败');
      return res.json();
    })
    .then(data => {
      // 展示统计信息
      if (typeof data.ossTotal !== 'undefined') {
        const message = `远程同步完成！<br>
          <strong>OSS总数:</strong> ${data.ossTotal}<br>
          <strong>OSS独有:</strong> ${data.ossOnly}<br>
          <strong>OSS与DB匹配:</strong> ${data.ossToDbMatched}<br>
          <strong>OSS与DB不匹配:</strong> ${data.ossToDbMismatched}<br>
          <strong>DB与OSS不匹配:</strong> ${data.dbToOssMismatched}`;
        showInfo(message);
      } else {
        showSuccess('远程同步成功');
      }
      listFiles();
    })
    .catch(e => showError(e.message))
    .finally(() => {
      syncBtn.disabled = false;
      syncIcon.classList.remove('spinning');
    });
}

// 解冻文件
function unfreezeFile(fileKey) {
  const btn = document.querySelector(`button[onclick="unfreezeFile('${fileKey}')"]`);
  if (!btn) return;
  btn.disabled = true;
  const originalText = btn.textContent;
  btn.textContent = '解冻中...';

  const currentBucket = getCurrentBucket();
  if (!currentBucket) {
    btn.disabled = false;
    btn.textContent = originalText;
    showError('未指定 bucket');
    return;
  }

  fetch(`${API_BASE}/buckets/${currentBucket}/files/unfreeze/${fileKey}`, { method: 'POST' })
    .then(res => {
      if (!res.ok) throw new Error('解冻请求失败');
      // 开始轮询解冻状态
      pollUnfreezeStatus(fileKey, btn, originalText);
    })
    .catch(e => {
      btn.disabled = false;
      btn.textContent = originalText;
      showError(e.message);
    });
}

function pollUnfreezeStatus(fileKey, btn, originalText, tryCount = 0) {
  // 最多轮询60次（约3分钟）
  if (tryCount > 60) {
    btn.disabled = false;
    btn.textContent = originalText;
    showError('解冻超时，请稍后重试');
    return;
  }
  const currentBucket = getCurrentBucket();
  if (!currentBucket) {
    btn.disabled = false;
    btn.textContent = originalText;
    showError('未指定 bucket');
    return;
  }
  fetch(`${API_BASE}/buckets/${currentBucket}/files/unfreeze-status/${fileKey}`)
    .then(res => {
      if (!res.ok) throw new Error('查询解冻状态失败');
      return res.json();
    })
    .then(data => {
      if (data && data.unfrozen === true) {
        btn.textContent = '已解冻';
        btn.classList.remove('btn-warning');
        btn.classList.add('btn-success');
        setTimeout(() => {
          // 刷新单个文件信息
          refreshSingleFile(fileKey);
        }, 1000);
      } else {
        setTimeout(() => pollUnfreezeStatus(fileKey, btn, originalText, tryCount + 1), 3000);
      }
    })
    .catch(e => {
      setTimeout(() => pollUnfreezeStatus(fileKey, btn, originalText, tryCount + 1), 3000);
    });
}



// 复制本地路径到剪贴板
function copyLocalPath(path) {
  navigator.clipboard.writeText(path).then(() => {
    showSuccess('已复制本地路径');
  });
}

// 获取当前文件对象
function getFileByKey(fileKey) {
  // 由于文件列表是动态渲染的，这里通过页面上的tbody查找
  // 你可以根据实际数据结构优化此方法
  const tbody = document.querySelector('#file-table tbody');
  if (!tbody) return null;
  for (const tr of tbody.children) {
    const keyCell = tr.querySelector('.filekey-cell');
    if (keyCell && keyCell.textContent === fileKey) {
      // 通过tr.dataset或其他方式存储file对象更好，这里简单返回null
      // 实际项目建议将文件数据缓存到全局变量
      return null;
    }
  }
  // 推荐：将文件数据缓存到全局变量allFiles，在listFiles时赋值
  if (window.allFiles) {
    return window.allFiles.find(f => f.fileKey === fileKey);
  }
  return null;
}

// 新增：处理本地文件不存在时的重新下载逻辑
function handleLocalPathMissingDownload(fileKey) {
  showConfirm('本地文件不存在，是否重新下载？', () => {
    // 让下载按钮可用（无视downloadable）
    const btn = document.querySelector(`.download-btn[data-filekey="${fileKey}"]`);
    if (btn) {
      btn.disabled = false;
      btn.removeAttribute('title');
      // 立即触发下载
      downloadFile(fileKey);
    }
  });
}

// 新增：释放本地文件
function releaseLocalFile(fileKey, localPath) {
  showConfirm('确定要释放本地文件吗？此操作会删除本地副本，但不会影响云端文件。', () => {
    const currentBucket = getCurrentBucket();
    if (!currentBucket) {
      showError('未指定 bucket');
      return;
    }
    fetch(`${API_BASE}/buckets/${currentBucket}/files/release-local/${fileKey}`, { method: 'POST' })
      .then(res => {
        if (!res.ok) throw new Error('释放本地文件失败');
        showSuccess('本地文件已释放');
        // 刷新单个文件信息
        refreshSingleFile(fileKey);
      })
      .catch(e => showError(e.message));
  });
}

// 新增：上传相关函数
function openUploadModal() {
  const modal = new bootstrap.Modal(document.getElementById('uploadModal'));
  modal.show();
  setupUploadArea();
}

function setupUploadArea() {
  const uploadArea = document.getElementById('uploadArea');
  const fileInput = document.getElementById('fileInput');
  
  // 点击上传区域触发文件选择
  uploadArea.addEventListener('click', () => {
    fileInput.click();
  });
  
  // 文件选择事件
  fileInput.addEventListener('change', handleFileSelect);
  
  // 拖拽事件
  uploadArea.addEventListener('dragover', (e) => {
    e.preventDefault();
    uploadArea.classList.add('dragover');
  });
  
  uploadArea.addEventListener('dragleave', (e) => {
    e.preventDefault();
    uploadArea.classList.remove('dragover');
  });
  
  uploadArea.addEventListener('drop', (e) => {
    e.preventDefault();
    uploadArea.classList.remove('dragover');
    const files = e.dataTransfer.files;
    if (files.length > 0) {
      fileInput.files = files;
      handleFileSelect();
    }
  });
}

function handleFileSelect() {
  const fileInput = document.getElementById('fileInput');
  const file = fileInput.files[0];
  const fileInfo = document.getElementById('fileInfo');
  const fileName = document.getElementById('fileName');
  const fileSize = document.getElementById('fileSize');
  const uploadBtn = document.getElementById('uploadBtn');
  
  if (file) {
    // 显示文件信息
    fileName.textContent = file.name;
    fileSize.textContent = formatSize(file.size);
    fileInfo.style.display = 'block';
    
    // 启用上传按钮
    uploadBtn.disabled = false;
  } else {
    fileInfo.style.display = 'none';
    uploadBtn.disabled = true;
  }
}

function clearFileSelection() {
  const fileInput = document.getElementById('fileInput');
  const fileInfo = document.getElementById('fileInfo');
  const uploadBtn = document.getElementById('uploadBtn');
  
  fileInput.value = '';
  fileInfo.style.display = 'none';
  uploadBtn.disabled = true;
}

function uploadFile() {
  const fileInput = document.getElementById('fileInput');
  const uploadBtn = document.getElementById('uploadBtn');
  const uploadProgress = document.getElementById('uploadProgress');
  const progressBar = uploadProgress.querySelector('.progress-bar');
  const uploadStatus = document.getElementById('uploadStatus');
  
  const file = fileInput.files[0];
  const currentBucket = getCurrentBucket();
  
  if (!file) {
    showError('请选择要上传的文件');
    return;
  }
  
  if (!currentBucket) {
    showError('未指定 bucket');
    return;
  }
  
  // 检查文件大小（100MB限制）
  if (file.size > 100 * 1024 * 1024) {
    showError('文件大小不能超过100MB');
    return;
  }
  
  // 显示进度条
  uploadProgress.style.display = 'block';
  uploadBtn.disabled = true;
  uploadStatus.textContent = '准备上传...';
  progressBar.style.width = '0%';
  
  // 创建FormData
  const formData = new FormData();
  formData.append('file', file);
  
  // 创建XMLHttpRequest以支持进度监控
  const xhr = new XMLHttpRequest();
  
  xhr.upload.addEventListener('progress', (e) => {
    if (e.lengthComputable) {
      const percentComplete = (e.loaded / e.total) * 100;
      progressBar.style.width = percentComplete + '%';
      uploadStatus.textContent = `上传中... ${Math.round(percentComplete)}%`;
    }
  });
  
  xhr.addEventListener('load', () => {
    if (xhr.status === 200) {
      try {
        const response = JSON.parse(xhr.responseText);
        
        progressBar.style.width = '100%';
        uploadStatus.textContent = '上传成功！';
        showSuccess('文件上传成功');
        
        // 关闭模态框
        const modal = bootstrap.Modal.getInstance(document.getElementById('uploadModal'));
        modal.hide();
        
        // 根据fileKey获取文件信息并添加到列表顶部
        if (response.fileKey) {
          // 获取刚上传文件的详细信息
          const currentBucket = getCurrentBucket();
          fetch(`${API_BASE}/buckets/${currentBucket}/files/${response.fileKey}`)
            .then(res => {
              if (!res.ok) throw new Error('获取文件信息失败');
              return res.json();
            })
            .then(fileData => {
              addNewFileToTop(fileData);
            })
            .catch(e => {
              console.error('获取文件信息失败:', e);
              // 即使获取详细信息失败，也显示上传成功
            });
        }
        
        // 重置表单
        clearFileSelection();
        uploadProgress.style.display = 'none';
      } catch (e) {
        showError('上传响应格式错误');
      }
    } else {
      // 根据不同的HTTP状态码显示不同的错误信息
      let errorMessage = '上传失败';
      try {
        const errorResponse = JSON.parse(xhr.responseText);
        if (errorResponse.error) {
          errorMessage = errorResponse.error;
        }
      } catch (e) {
        // 如果无法解析错误响应，使用默认错误信息
        switch (xhr.status) {
          case 400:
            errorMessage = '文件格式不支持或文件过大';
            break;
          case 409:
            errorMessage = '文件已存在';
            break;
          case 413:
            errorMessage = '文件大小超限';
            break;
          default:
            errorMessage = '上传失败';
        }
      }
      showError(errorMessage);
    }
    uploadBtn.disabled = false;
  });
  
  xhr.addEventListener('error', () => {
    showError('网络错误，上传失败');
    uploadBtn.disabled = false;
    uploadProgress.style.display = 'none';
  });
  
  xhr.addEventListener('abort', () => {
    showError('上传已取消');
    uploadBtn.disabled = false;
    uploadProgress.style.display = 'none';
  });
  
  // 发送请求
  xhr.open('POST', `${API_BASE}/buckets/${currentBucket}/files/upload`);
  xhr.send(formData);
}

// 刷新文件列表（带动画效果）
function refreshFileList() {
  const refreshBtn = document.getElementById('refresh-btn');
  if (refreshBtn) {
    refreshBtn.disabled = true;
    refreshBtn.classList.add('spinning');
  }
  
  listFiles().finally(() => {
    if (refreshBtn) {
      refreshBtn.disabled = false;
      refreshBtn.classList.remove('spinning');
    }
  });
}

window.backToBuckets = backToBuckets;
window.listFiles = listFiles; // 新增：暴露文件列表刷新函数
window.refreshFileList = refreshFileList; // 新增：暴露带动画的刷新函数
window.downloadFile = downloadFile;
window.deleteFile = deleteFile;
window.changePage = changePage;
window.confirmDeleteFiles = confirmDeleteFiles;
window.syncRemoteFiles = syncRemoteFiles;
window.unfreezeFile = unfreezeFile;
window.copyLocalPath = copyLocalPath;
window.handleLocalPathMissingDownload = handleLocalPathMissingDownload;
window.releaseLocalFile = releaseLocalFile;
window.refreshSingleFile = refreshSingleFile; // 新增：暴露刷新函数
window.updateFileRow = updateFileRow; // 新增：暴露更新函数
window.addNewFileToTop = addNewFileToTop; // 新增：暴露添加新文件函数
window.generateFileRowHTML = generateFileRowHTML; // 新增：暴露生成HTML函数
window.openUploadModal = openUploadModal; // 新增：暴露上传模态框函数
window.clearFileSelection = clearFileSelection; // 新增：暴露清除文件选择函数
window.uploadFile = uploadFile; // 新增：暴露上传文件函数

window.onload = listFiles; 