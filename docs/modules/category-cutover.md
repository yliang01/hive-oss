# 分类全数据化上线说明

## SQL 执行前置
1. 先执行 `001_category_group_schema.sql`
2. 再执行 `002_category_seed.sql`
3. 再执行 `003_index_and_constraints.sql`
4. 需要回滚或清理时，按 `004_safe_cleanup.sql` 指引手工处理

## 发布顺序
1. 数据库：由你手工执行 SQL 并确认校验语句通过
2. 后端：发布支持 `category-admin` 与全量 `/categories/*` 路径的新版本
3. 前端：发布分类管理页与文件页分组批量操作

## 兼容性与风险
- 分类 code 作为路径参数必须保持稳定，不建议修改已有 code
- 删除分组前必须先清空关联文件映射
- 删除分类前必须先删除该分类下全部分组
- 建议先以“停用分类”替代“删除分类”
