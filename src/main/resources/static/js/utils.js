// 格式化文件大小
export function formatSize(size) {
  if (size == null) return '';
  if (size < 1024) return size + ' B';
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
  if (size < 1024 * 1024 * 1024) return (size / 1024 / 1024).toFixed(1) + ' MB';
  return (size / 1024 / 1024 / 1024).toFixed(1) + ' GB';
}

// 格式化日期
export function formatDate(isoString) {
  if (!isoString) return '';
  const date = new Date(isoString);
  if (isNaN(date.getTime())) return isoString;
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const h = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  const s = String(date.getSeconds()).padStart(2, '0');
  return `${y}-${m}-${d} ${h}:${min}:${s}`;
}

// 通知系统
let notificationId = 0;

// 创建通知元素
function createNotification(message, type = 'info', duration = 5000) {
  const id = `notification-${++notificationId}`;
  const icon = getNotificationIcon(type);
  const bgClass = getNotificationBgClass(type);
  const borderClass = getNotificationBorderClass(type);
  
  const notification = document.createElement('div');
  notification.id = id;
  notification.className = `alert ${bgClass} ${borderClass} alert-dismissible fade show d-flex align-items-center shadow-sm`;
  notification.style.minWidth = '300px';
  notification.style.maxWidth = '400px';
  notification.style.marginBottom = '10px';
  notification.style.animation = 'slideInRight 0.3s ease-out';
  
  notification.innerHTML = `
    <i class="${icon} me-2"></i>
    <div class="flex-grow-1">${message}</div>
    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
  `;
  
  return notification;
}

// 获取通知图标
function getNotificationIcon(type) {
  switch (type) {
    case 'success': return 'bi bi-check-circle-fill text-success';
    case 'error': return 'bi bi-exclamation-triangle-fill text-danger';
    case 'warning': return 'bi bi-exclamation-circle-fill text-warning';
    case 'info': return 'bi bi-info-circle-fill text-info';
    default: return 'bi bi-info-circle-fill text-info';
  }
}

// 获取通知背景类
function getNotificationBgClass(type) {
  switch (type) {
    case 'success': return 'alert-success';
    case 'error': return 'alert-danger';
    case 'warning': return 'alert-warning';
    case 'info': return 'alert-info';
    default: return 'alert-info';
  }
}

// 获取通知边框类
function getNotificationBorderClass(type) {
  switch (type) {
    case 'success': return 'border-success';
    case 'error': return 'border-danger';
    case 'warning': return 'border-warning';
    case 'info': return 'border-info';
    default: return 'border-info';
  }
}

// 显示通知
function showNotification(message, type = 'info', duration = 5000) {
  const container = document.getElementById('notification-container');
  if (!container) {
    console.error('通知容器不存在');
    return;
  }
  
  const notification = createNotification(message, type, duration);
  container.appendChild(notification);
  
  // 自动移除通知
  if (duration > 0) {
    setTimeout(() => {
      removeNotification(notification);
    }, duration);
  }
  
  return notification;
}

// 移除通知
function removeNotification(notification) {
  if (notification && notification.parentNode) {
    notification.style.animation = 'slideOutRight 0.3s ease-in';
    setTimeout(() => {
      if (notification.parentNode) {
        notification.parentNode.removeChild(notification);
      }
    }, 300);
  }
}

// 错误提示
export function showError(message) {
  showNotification(message, 'error', 6000);
}

// 成功提示
export function showSuccess(message) {
  showNotification(message, 'success', 4000);
}

// 警告提示
export function showWarning(message) {
  showNotification(message, 'warning', 5000);
}

// 信息提示
export function showInfo(message) {
  showNotification(message, 'info', 4000);
}

// 确认对话框
export function showConfirm(message, onConfirm, onCancel = null) {
  return new Promise((resolve) => {
    const modal = document.getElementById('confirmModal');
    const messageEl = document.getElementById('confirmMessage');
    const confirmBtn = document.getElementById('confirmBtn');
    
    if (!modal || !messageEl || !confirmBtn) {
      console.error('确认对话框元素不存在');
      resolve(false);
      return;
    }
    
    // 设置消息
    messageEl.innerHTML = message;
    
    // 创建Bootstrap模态框实例
    const bootstrapModal = new bootstrap.Modal(modal);
    
    // 确认按钮事件
    const handleConfirm = () => {
      bootstrapModal.hide();
      cleanup();
      if (onConfirm) onConfirm();
      resolve(true);
    };
    
    // 取消按钮事件
    const handleCancel = () => {
      bootstrapModal.hide();
      cleanup();
      if (onCancel) onCancel();
      resolve(false);
    };
    
    // 清理事件监听器
    const cleanup = () => {
      confirmBtn.removeEventListener('click', handleConfirm);
      modal.removeEventListener('hidden.bs.modal', handleCancel);
    };
    
    // 添加事件监听器
    confirmBtn.addEventListener('click', handleConfirm);
    modal.addEventListener('hidden.bs.modal', handleCancel);
    
    // 显示模态框
    bootstrapModal.show();
  });
} 