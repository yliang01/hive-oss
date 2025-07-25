# OSS 管理前端

基于原生技术栈（HTML + CSS + JavaScript）的 OSS 管理系统前端。

## 项目结构

```
oss-manager/
├── pages/              # 页面文件
│   ├── bucket.html    # Bucket 列表页
│   └── files.html     # 文件管理页
├── js/                # JavaScript 文件
│   ├── bucket.js      # Bucket 列表逻辑
│   ├── files.js       # 文件管理逻辑
│   └── utils.js       # 工具函数
├── css/               # 样式文件
│   └── style.css      # 自定义样式
├── serve.json         # 服务器配置
└── README.md          # 项目说明
```

## 开发环境

```bash
# 安装依赖（如果需要）
npm install -g serve

# 启动开发服务器
npx serve . --config serve.json
```

## 访问地址

- Bucket 列表页：http://localhost:3000/pages/bucket.html
- 文件管理页：http://localhost:3000/pages/files.html?bucket=xxx

## 技术栈

- 原生 JavaScript (ES Modules)
- Bootstrap 5.3.2
- RESTful API 