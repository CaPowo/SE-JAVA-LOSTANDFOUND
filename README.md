# 校园失物招领系统

一个面向学校值班室、宿管站或学院服务点的 JavaFX 桌面程序，用于登记、查询、认领和管理校园失物信息。项目使用 SQLite 做本地持久化，数据文件保存在程序运行目录，重启后数据不会丢失。

## 功能概览

- 用户登录进入管理主界面
- 失物信息录入：物品名称、类别、拾取地点、拾取时间、拾取人或联系方式、物品描述、当前状态
- 类别可管理，可在界面中新增类别并用于失物登记和查询
- 拾取时间使用日期控件和时分秒选择控件录入
- 失物列表展示
- 支持关键词全局模糊查询，可匹配 UUID、物品名称、类别、地点、联系方式、描述、状态、认领人和认领历史
- 可继续按类别、地点、状态进行筛选
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

如果使用默认密码 `123456` 登录，系统会要求先修改管理员密码，修改成功后才能进入主界面。主界面顶部也提供“修改密码”入口，修改时必须输入正确的当前密码。

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

category
- id
- name
- created_at

lost_item_category
- item_id
- category_id

claim_record
- id
- item_id
- claimer
- contact
- claim_time
- operator
```

其中 `lost_item.id` 使用 UUID 字符串保存，避免自增编号在迁移、导入或文件命名时发生重复。类别由 `category` 表独立维护，`lost_item_category` 作为关联表绑定失物和类别。`claim_record.item_id` 关联 `lost_item.id`，用于支持一件物品的认领历史查询。

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

1. 启动程序后使用默认账号登录；如果仍使用默认密码，按提示先修改管理员密码。
2. 在右侧表单填写失物信息，类别从下拉框选择；如需新类别，点击类别旁边的“新增”。
3. 拾取时间通过日期选择框和时、分、秒控件录入。
4. 如需保存图片，先点击“选择图片”，保存后图片会被复制到 `data/photos/`。
5. 点击列表中的某条记录，可将该记录载入右侧表单进行编辑。
6. 选中记录后点击“办理认领”，填写认领人和联系方式。
7. 选中记录后点击“认领历史”，查看该物品的认领记录。
8. 顶部查询栏支持关键词模糊查询，也可以按类别、地点和状态继续筛选记录。
9. 点击顶部“修改密码”，输入当前密码和新密码后可长期维护管理员密码。

## 答辩要点

- 使用 MyBatis 替代手写 JDBC DAO，减少 `ResultSet` 映射和 SQL 样板代码。
- 动态查询使用 MyBatis XML 动态 SQL 和参数绑定，不直接拼接用户输入。
- 关键词查询会同时匹配失物表、类别关联和认领历史中的多种字段，便于按电话、UUID、物品名等快速定位记录。
- 图片只保存路径，不保存二进制，符合“图片路径保存与图片预览”的要求。
- 物品 ID 使用 UUID，同时图片文件名与物品 ID 对应，避免重名。
- 类别独立成表，并通过 `lost_item_category` 关联失物，支持类别管理和后续扩展。
- 拾取时间用图形化控件录入，减少手工输入格式错误。
- 认领信息拆到 `claim_record` 表，天然支持认领历史查询。
- 登录密码使用哈希存储，避免明文密码写死或落库；首次默认密码登录强制改密，长期改密必须校验当前密码。
