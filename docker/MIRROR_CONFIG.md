# Docker 镜像加速器配置指南

如果遇到Docker镜像下载超时问题，可以配置国内镜像加速器。

## Windows系统配置

### 1. Docker Desktop配置

1. 打开Docker Desktop
2. 点击右上角的设置图标（齿轮）
3. 选择 "Docker Engine"
4. 在JSON配置中添加以下内容：

```json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ]
}
```

5. 点击 "Apply & Restart"

### 2. 验证配置

打开PowerShell或CMD，执行：

```bash
docker info
```

查看输出中是否包含 "Registry Mirrors" 配置。

## 重新启动服务

配置完成后，重新启动Docker服务：

```bash
cd docker
docker-compose down
docker-compose up -d
```

## 查看服务状态

```bash
docker-compose ps
docker-compose logs -f
```

## 备选方案

如果镜像加速器仍然无法解决问题，可以：

### 方案1: 使用本地已安装的服务

修改 `application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/school_system
    username: your_local_username
    password: your_local_password
    
  data:
    redis:
      host: localhost
      port: 6379
      password: your_local_password  # 如果没有密码就删除这行
```

### 方案2: 手动创建数据库

连接到本地PostgreSQL，执行：

```sql
CREATE DATABASE school_system;
CREATE USER school_admin WITH PASSWORD 'school_pass_2024';
GRANT ALL PRIVILEGES ON DATABASE school_system TO school_admin;
```

然后使用Flyway初始化表结构：

```bash
mvn flyway:migrate
```

## 常见镜像源

- 中国科技大学: https://docker.mirrors.ustc.edu.cn
- 网易: https://hub-mirror.c.163.com
- 百度云: https://mirror.baidubce.com
- 阿里云: https://[你的ID].mirror.aliyuncs.com

注意：阿里云镜像需要注册账号获取专属加速地址：
https://cr.console.aliyun.com/cn-hangzhou/instances/mirrors

## 验证镜像拉取

测试拉取镜像：

```bash
docker pull postgres:16-alpine
docker pull redis:7-alpine
```

如果成功，说明镜像加速器配置正确。
