import { showError } from './utils.js';

const API_BASE = 'http://localhost:8080';

// 跳转到文件管理页
function navigateToFiles(bucket) {
  window.location.href = window.location.origin + '/pages/files.html?bucket=' + encodeURIComponent(bucket);
}

// 渲染 bucket 列表
function renderBuckets(buckets) {
  const ul = document.getElementById('buckets');
  ul.innerHTML = '';
  
  if (!buckets.length) {
    ul.innerHTML = '<li class="list-group-item text-center text-secondary">暂无 Bucket</li>';
    return;
  }

  buckets.forEach(bucket => {
    const li = document.createElement('li');
    li.textContent = bucket;
    li.className = 'list-group-item list-group-item-action';
    li.style.cursor = 'pointer';
    li.addEventListener('click', () => navigateToFiles(bucket));
    ul.appendChild(li);
  });
}

// 获取 bucket 列表
function listBuckets() {
  fetch(`${API_BASE}/buckets`)
    .then(res => {
      if (!res.ok) throw new Error('获取 bucket 失败');
      return res.json();
    })
    .then(renderBuckets)
    .catch(e => showError(e.message));
}

window.listBuckets = listBuckets;
window.onload = listBuckets; 