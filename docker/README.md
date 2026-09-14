# Docker 环境说明

## 服务列表

| 服务 | 容器名 | 端口 | 用户名 | 密码 |
|------|--------|------|--------|------|
| PostgreSQL | school-postgres | 5432 | school_admin | school_pass_2024 |
| Redis | school-redis | 6379 | - | redis_pass_2024 |

## 快速启动

### 启动所有服务
```bash
cd docker
docker-compose up -d
```

### 查看服务状态
```bash
docker-compose ps
```

### 查看日志
```bash
# 查看所有日志
docker-compose logs -f

# 查看PostgreSQL日志
docker-compose logs -f postgres

# 查看Redis日志
docker-compose logs -f redis
```

### 停止服务
```bash
docker-compose stop
```

### 停止并删除容器
```bash
docker-compose down
```

### 停止并删除容器及数据卷
```bash
docker-compose down -v
```

## 连接数据库

### PostgreSQL
```bash
# 使用psql客户端连接
docker exec -it school-postgres psql -U school_admin -d school_system

# 使用外部工具连接
Host: localhost
Port: 5432
Database: school_system
Username: school_admin
Password: school_pass_2024
```

### Redis
```bash
# 使用redis-cli连接
docker exec -it school-redis redis-cli -a redis_pass_2024

# 测试连接
docker exec -it school-redis redis-cli -a redis_pass_2024 ping
```

## 数据持久化

数据文件存储在以下目录：
- PostgreSQL: `./data/postgres/`
- Redis: `./data/redis/`

**注意**: 这些目录已被添加到 `.gitignore`，不会被提交到版本控制。

## 健康检查

两个服务都配置了健康检查：
- PostgreSQL: 每10秒检查一次，使用 `pg_isready` 命令
- Redis: 每10秒检查一次，使用 `redis-cli ping` 命令

查看健康状态：
```bash
docker-compose ps
```

## 备份与恢复

### PostgreSQL备份
```bash
# 备份整个数据库
docker exec school-postgres pg_dump -U school_admin school_system > backup.sql

# 备份到容器内再拷贝出来
docker exec school-postgres pg_dump -U school_admin school_system -f /tmp/backup.sql
docker cp school-postgres:/tmp/backup.sql ./backup.sql
```

### PostgreSQL恢复
```bash
# 恢复数据库
docker exec -i school-postgres psql -U school_admin school_system < backup.sql
```

### Redis备份
```bash
# Redis会自动持久化到 ./data/redis/appendonly.aof
# 也可以手动触发RDB快照
docker exec school-redis redis-cli -a redis_pass_2024 BGSAVE

# 拷贝RDB文件
docker cp school-redis:/data/dump.rdb ./redis-backup.rdb
```
