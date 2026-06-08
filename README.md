# 校园失物招领系统

一个面向学校值班室、宿管站或学院服务点的 JavaFX 桌面程序，用于登记、查询、认领和管理校园失物信息。项目使用 SQLite 做本地持久化，数据文件保存在程序运行目录，重启后数据不会丢失。

## 功能概览

- 用户登录进入管理主界面
- 失物信息录入：物品名称、类别、拾取地点、拾取时间、拾取人或联系方式、物品描述、当前状态
- 失物列表展示
- 按名称、类别、地点、状态查询或筛选
- 修改和删除失物信息
- 办理认领登记，记录认领人、联系方式、认领时间和经办人
- 图片路径保存与图片预览
- 认领历史记录查询
- 数据持久化保存
- 基本输入合法性校验和异常提示

## 技术栈

- Java 21
- JavaFX 21
- SQLite
- MyBatis
- Maven

## 默认账号

首次启动时，如果 `user` 表为空，系统会自动创建默认管理员账号：

```text
用户名：admin
密码：123456
```

密码不会明文保存到数据库中，数据库保存的是 `salt + SHA-256` 哈希结果。

## 数据存储设计

数据库文件：

```text
lostfound.db
```

图片文件目录：

```text
data/photos/
```

图片不会以二进制形式存入数据库。用户选择图片后，程序会把图片复制到 `data/photos/`，并按物品 UUID 重命名：

```text
data/photos/<物品UUID>.<扩展名>
```

数据库中的 `lost_item.image_path` 只保存相对路径。

主要数据表：

```text
user
- id
- username
- password_hash
- salt
- role
- created_at

lost_item
- id
- name
- category
- location
- found_time
- finder_contact
- description
- image_path
- status
- claimer
- claim_time
- created_at
- updated_at

claim_record
- id
- item_id
- claimer
- contact
- claim_time
- operator
```

其中 `lost_item.id` 使用 UUID 字符串保存，避免自增编号在迁移、导入或文件命名时发生重复。`claim_record.item_id` 关联 `lost_item.id`，用于支持一件物品的认领历史查询。

## 项目结构

```text
src/main/java/com/campus/lostfound/
├── App.java                         # JavaFX 应用主类，负责登录和主界面切换
├── Launcher.java                    # 程序启动入口
├── db/
│   └── Database.java                # 数据库初始化门面
├── mapper/                          # MyBatis Mapper 接口
├── model/                           # 实体模型
├── service/                         # 业务服务层
├── ui/
│   └── MainView.java                # 主界面
└── util/                            # MyBatis、密码、图片存储工具

src/main/resources/
├── mybatis-config.xml               # MyBatis 配置
├── schema.sql                       # 建表 SQL
└── mapper/                          # MyBatis XML 映射文件
```

## 运行方式

### IntelliJ IDEA

1. 使用 IntelliJ IDEA 打开项目根目录。
2. 等待 Maven 自动导入依赖。
3. 确认 Project SDK 为 Java 21。
4. 运行 `com.campus.lostfound.Launcher`。

### Maven 命令

如果本机已经配置好 Maven：

```bash
mvn javafx:run
```

或先打包编译：

```bash
mvn -DskipTests package
```

## 使用说明

1. 启动程序后使用默认账号登录。
2. 在右侧表单填写失物信息，点击“新增”保存。
3. 如需保存图片，先点击“选择图片”，保存后图片会被复制到 `data/photos/`。
4. 点击列表中的某条记录，可将该记录载入右侧表单进行编辑。
5. 选中记录后点击“办理认领”，填写认领人和联系方式。
6. 选中记录后点击“认领历史”，查看该物品的认领记录。
7. 顶部查询栏可以按名称、类别、地点和状态筛选记录。

## 答辩要点

- 使用 MyBatis 替代手写 JDBC DAO，减少 `ResultSet` 映射和 SQL 样板代码。
- 动态查询使用 MyBatis XML 动态 SQL 和参数绑定，不直接拼接用户输入。
- 图片只保存路径，不保存二进制，符合“图片路径保存与图片预览”的要求。
- 物品 ID 使用 UUID，同时图片文件名与物品 ID 对应，避免重名。
- 认领信息拆到 `claim_record` 表，天然支持认领历史查询。
- 登录密码使用哈希存储，避免明文密码写死或落库。
