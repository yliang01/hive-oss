import { formatSize, formatDate, showError } from './utils.js';

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
    tr.innerHTML = `
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
    tbody.appendChild(tr);
  });
}

// 获取文件列表
function listFiles() {
  currentBucket = getCurrentBucket();
  if (!currentBucket) {
    showError('未指定 bucket');
    return;
  }
  
  document.getElementById('current-bucket').textContent = currentBucket;
  
  // 添加loading状态
  const tbody = document.querySelector('#file-table tbody');
  tbody.innerHTML = '<tr><td colspan="7" class="text-center">加载中...</td></tr>';
  
  fetch(`${API_BASE}/buckets/${currentBucket}/files?page=${currentPage - 1}&pageSize=${pageSize}`)
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
    .catch(e => showError(e.message));
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
  // 优化按钮选择器，避免依赖onclick字符串
  const btn = document.querySelector(`.download-btn[data-filekey="${fileKey}"]`);
  if (btn) {
    btn.disabled = true;
    btn.textContent = '下载准备中...';
  }
  fetch(`${API_BASE}/files/download-task/${fileKey}`, { method: 'POST' })
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
  fetch(`${API_BASE}/files/download-task-status/${fileKey}`)
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
  if (!confirm('确定要删除该文件吗？')) return;
  
  fetch(`${API_BASE}/files/${fileKey}`, {
    method: 'DELETE'
  })
    .then(res => {
      if (!res.ok) throw new Error('删除文件失败');
      listFiles();
    })
    .catch(e => showError(e.message));
}

// 同步文件
function syncFiles() {
  const syncBtn = document.getElementById('sync-btn');
  const syncIcon = syncBtn.querySelector('.sync-icon');
  
  // 获取当前bucket
  const currentBucket = getCurrentBucket();
  if (!currentBucket) {
    showError('未指定 bucket');
    return;
  }
  
  // 禁用按钮并开始旋转动画
  syncBtn.disabled = true;
  syncIcon.classList.add('spinning');
  
  fetch(`${API_BASE}/files/sync/${currentBucket}`, {
    method: 'POST'
  })
    .then(res => {
      if (!res.ok) throw new Error('同步失败');
      showSuccess('同步成功');
      listFiles(); // 刷新文件列表
    })
    .catch(e => showError(e.message))
    .finally(() => {
      // 恢复按钮状态
      syncBtn.disabled = false;
      syncIcon.classList.remove('spinning');
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
        alert(
          `远程同步完成：\n` +
          `OSS总数: ${data.ossTotal}\n` +
          `OSS独有: ${data.ossOnly}\n` +
          `OSS与DB匹配: ${data.ossToDbMatched}\n` +
          `OSS与DB不匹配: ${data.ossToDbMismatched}\n` +
          `DB与OSS不匹配: ${data.dbToOssMismatched}`
        );
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

  fetch(`${API_BASE}/files/unfreeze/${fileKey}`, { method: 'POST' })
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
  fetch(`${API_BASE}/files/unfreeze-status/${fileKey}`)
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
          // 刷新文件列表，恢复按钮
          listFiles();
        }, 1000);
      } else {
        setTimeout(() => pollUnfreezeStatus(fileKey, btn, originalText, tryCount + 1), 3000);
      }
    })
    .catch(e => {
      setTimeout(() => pollUnfreezeStatus(fileKey, btn, originalText, tryCount + 1), 3000);
    });
}

// 显示成功提示
function showSuccess(message) {
  // 如果utils.js中已经有类似函数，可以移除这个函数并使用utils中的
  alert(message);
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
  if (confirm('本地文件不存在，是否重新下载？')) {
    // 让下载按钮可用（无视downloadable）
    const btn = document.querySelector(`.download-btn[data-filekey="${fileKey}"]`);
    if (btn) {
      btn.disabled = false;
      btn.removeAttribute('title');
      // 立即触发下载
      downloadFile(fileKey);
    }
  }
}

// 新增：释放本地文件
function releaseLocalFile(fileKey, localPath) {
  if (!confirm('确定要释放本地文件吗？此操作会删除本地副本，但不会影响云端文件。')) return;
  fetch(`${API_BASE}/files/release-local/${fileKey}`, { method: 'POST' })
    .then(res => {
      if (!res.ok) throw new Error('释放本地文件失败');
      showSuccess('本地文件已释放');
      listFiles();
    })
    .catch(e => showError(e.message));
}

window.backToBuckets = backToBuckets;
window.downloadFile = downloadFile;
window.deleteFile = deleteFile;
window.changePage = changePage;
window.syncFiles = syncFiles;
window.confirmDeleteFiles = confirmDeleteFiles;
window.syncRemoteFiles = syncRemoteFiles;
window.unfreezeFile = unfreezeFile;
window.copyLocalPath = copyLocalPath;
window.handleLocalPathMissingDownload = handleLocalPathMissingDownload;
window.releaseLocalFile = releaseLocalFile;

window.onload = listFiles; 