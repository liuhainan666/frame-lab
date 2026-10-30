# 10 · 可靠性扩展与国际化设计（E-01 ～ E-04）

> 本文定义四项**追加为 P0** 的能力：受理幂等、自动重试、断点续跑、国际化。它们共享同一套任务状态机与持久化边界，不引入消息队列、Redis、分布式锁或第二个服务进程。
> 关联文档：[01 范围](01-requirements.md)、[02 领域模型](02-domain-model.md)、[03 数据库](03-database.md)、[04 API](04-api.md)、[05 架构](05-architecture.md)、[06 前端](06-frontend-design.md)、[07 测试与部署](07-testing-ci-deploy.md)、[08 验收](08-acceptance.md)。

## 1. 范围与共同约束

### 1.1 四项扩展

| 编号 | 能力 | 一句话合同 | 主要证据 |
|---|---|---|---|
| E-01 | 受理幂等 | 同一用户 + 同一 `clientRequestId` 只产生一个任务；重放返回原任务 | 唯一索引、重放响应头、并发测试 |
| E-02 | 自动重试 | 可重试失败进入 `RETRYING`，按上限与指数退避自动重试；每次尝试留痕 | `task_step_attempt`、`next_retry_at`、尝试历史 UI |
| E-03 | 断点续跑 | 服务重启后 `RUNNING` 任务回到队列，从第一个未成功步骤继续 | 启动恢复、`INTERRUPTED` 尝试、`resume_count` |
| E-04 | 国际化 | 中文 / English 界面、模板文案、API 错误与任务消息；选择持久化 | 词典、`MessageSource`、翻译表、`Accept-Language` |

### 1.2 共同原则

1. **持久化优先**：幂等、重试、续跑的判断全部以 MySQL 已提交状态为准；内存中的队列、定时器或前端状态不作为事实源。
2. **状态机唯一**：四项扩展不新增并行状态机；所有变化都落在 `task`、`task_step`、`task_step_attempt` 三张表上。
3. **每次执行可审计**：任何一次步骤执行都有独立的 attempt 记录，成功、失败、被中断都可追溯。
4. **不隐藏不确定性**：无法确定结果时宁可让任务回到可重试路径，也不把查询失败、进程中断写成“任务失败”。
5. **单实例前提**：启动恢复与 worker 认领假设同一时刻只有一个 Java 进程写任务表。多实例需要租约/心跳，明确不在 P0。

### 1.3 新增持久化对象

| 对象 | 用途 | 详见 |
|---|---|---|
| `task` 新字段 | 重试上限、重试次数、续跑次数、下次重试时间、最后错误 | [03 · task](03-database.md#3-建表-ddl) |
| `task_step` 新字段 | 已消耗尝试次数、消息参数、最后错误 | [03 · task_step](03-database.md#3-建表-ddl) |
| `task_step_attempt` | 每次执行的独立记录：`RUNNING / SUCCEEDED / FAILED / INTERRUPTED` | [03 · task_step_attempt](03-database.md#3-建表-ddl) |
| `template_category_translation` | 分类名称的 `en-US` 文案 | [03 · 翻译表](03-database.md#3-建表-ddl) |
| `template_translation` | 模板标题与描述的 `en-US` 文案 | [03 · 翻译表](03-database.md#3-建表-ddl) |
| `simulation_setting.fail_retryable` | 模拟失败是否可重试 | [03 · 模拟配置](03-database.md#3-建表-ddl) |

> 文案不落“已翻译文本”：任务只存稳定错误码、消息键与 JSON 参数，读取时按请求语言渲染。这样历史记录在切换语言后仍能正确显示，也不会把某一种语言的句子写死进业务数据。

## 2. E-01 受理幂等

### 2.1 合同

| 项 | 规则 |
|---|---|
| 幂等键 | 请求体必填 `clientRequestId`，UUID 字符串，长度 36 |
| 作用域 | `(user_id, client_request_id)`；不同用户可以使用相同值 |
| 首次请求 | 校验通过、任务落库，返回 `202 Accepted` + 任务快照 + `Location: /api/tasks/{id}` |
| 重复请求 | 返回 `200 OK` + 原任务快照 + `Idempotent-Replay: true`，不重复创建、不重复执行 |
| 载荷不一致 | 同一键但 `templateId` 或素材列表（含顺序）不同，返回 `409 IDEMPOTENCY_KEY_REUSED` |
| 并发请求 | 数据库唯一索引兜底；唯一键冲突的请求转为“重放读取”，不返回 500 |
| 重放优先级 | 先按键查任务，再校验模板与素材；模板后来下架、素材被引用，都不影响重放 |
| 保留期 | 与任务同寿命；P0 不清理任务，因此键也不回收 |

### 2.2 前端标识生命周期

```
进入制作台
  → 生成 draftId（仅用于本地状态，不提交）
点击“开始制作”
  → const clientRequestId = crypto.randomUUID()
  → 禁用按钮，发起 POST /api/tasks
  → 202/200 成功：记录 taskId，清空本次 clientRequestId，允许下一次制作生成新值
  → 网络错误 / 超时 / 5xx：保留 clientRequestId，按钮显示“重试提交”
       → 用户重试时复用同一个 clientRequestId
  → 4xx 校验错误：保留标识也可，但修正参数后应视为新的提交意图，生成新值
```

规则：

1. **一次提交意图对应一个 UUID**；同一次意图的所有网络重试复用同一个值。
2. 页面刷新会丢失未完成的值；P0 不在 `sessionStorage` 持久化提交意图。刷新后重新点击提交属于新的意图，可以生成新值。
3. 不允许用“素材集合哈希”或“模板 ID”充当幂等键；它们不能表达用户的重试意图。
4. 按钮防抖只是体验优化，**不能替代服务端幂等**。

### 2.3 服务端算法

```
BEGIN
  1. SELECT task WHERE user_id = ? AND client_request_id = ? FOR UPDATE
  2. 若存在：
       a. 读取 task_material（按 slot_index）与请求的 assetIds 比较
       b. template_id 或素材序列不一致 → 409 IDEMPOTENCY_KEY_REUSED
       c. 一致 → COMMIT，返回 200 + 原任务 + Idempotent-Replay: true
  3. 校验模板存在且上架
  4. 校验素材数量、归属、类型、大小
  5. INSERT task(... client_request_id ...) + 3 个 task_step + task_material
COMMIT
  → 202 + 任务快照
```

并发窗口：

- 两个请求同时走到步骤 1 都查不到，随后都尝试 INSERT；唯一索引 `uk_task_client_request (user_id, client_request_id)` 只允许一个成功。
- 失败方捕获 `DuplicateKeyException`，回滚后重新执行步骤 1，按重放返回；不得把数据库异常包装成 500。
- 若失败方在重新读取时事务隔离级别看不到已提交行（不会发生在 READ COMMITTED/REPEATABLE READ 的新事务中），重试一次读取；仍失败才返回 `409 IDEMPOTENCY_CONFLICT` 并记录日志。

### 2.4 载荷一致性

比较范围固定为：

1. `templateId`；
2. `assetIds` 的**顺序**（顺序决定结果槽位）；
3. 素材数量隐含在序列长度中。

不比较图片字节、模板版本或客户端时间。素材内容不可变，因此序列相同即视为同一请求。

### 2.5 响应差异

| 场景 | 状态码 | `Idempotent-Replay` | 位置 |
|---|---|---|---|
| 新建成功 | 202 | 无 | `Location: /api/tasks/{id}` |
| 重放成功 | 200 | `true` | `Location: /api/tasks/{id}` |
| 同键不同载荷 | 409 | 无 | — |
| 键格式非法 | 400 | 无 | — |

前端对 202 与 200 使用同一处理分支：都视为“任务已受理”，以响应体 `id` 为准。202 只表达“已受理、尚未完成”，不代表任务一定成功。

### 2.6 边界

- 幂等只覆盖 `POST /api/tasks`；上传、登录、模拟配置不在此列。
- 不提供“删除幂等键”接口；任务不删除，键就不回收。
- 幂等不保证响应字节完全一致：重放返回的是**当前**任务快照，可能已经 `RUNNING` 或 `SUCCEEDED`。
- `clientRequestId` 不是安全凭证；它只在当前用户作用域内生效。

## 3. E-02 自动重试

### 3.1 失败分类

| 失败码 | 阶段 | 默认可重试 | 理由 |
|---|---|---|---|
| `MATERIAL_CHECK_FAILED` | MATERIAL_CHECK | 否 | 素材数量、解码、尺寸问题不会因重试改变 |
| `GENERATION_FAILED` | GENERATE | 否 | 确定性版式错误；模拟注入时可由管理员标记为可重试 |
| `RESULT_RENDER_FAILED` | FINALIZE | 是 | 字体加载、临时 IO、渲染资源可能瞬时恢复 |
| `STORAGE_UNAVAILABLE` | 任意 | 是 | 磁盘/权限/原子移动问题可能瞬时恢复 |
| `INTERNAL_ERROR` | 任意 | 是 | 未分类异常按瞬时故障处理，但受次数上限约束 |

规则：

1. **可重试性是失败码的属性**，不是前端或调用方自由决定的。
2. 模拟控制提供 `failure.retryable` 覆盖：仅对本次注入生效；重试等待期间记录 `last_error_simulated=1`，最终失败时转存为 `failure_simulated=1`。
3. 任务创建时快照 `max_step_attempts`（默认 3），之后修改全局配置不影响已创建任务。
4. 重试按**步骤**进行；某个步骤重试成功后可继续后续步骤。已成功步骤永不重跑。
5. 不可重试失败、达到上限的失败都按原失败模型收口：当前步骤 `FAILED`、后续 `SKIPPED`、任务 `FAILED`。

### 3.2 计数口径

| 字段 | 位置 | 口径 |
|---|---|---|
| `max_step_attempts` | `task` | 每个步骤最多消耗的尝试次数（默认 3，含首次执行，范围 1～5） |
| `attempt_count` | `task_step` | 当前步骤**已消耗**的尝试次数；被中断的尝试不计入（恢复时回退） |
| `retry_count` | `task` | 本任务已安排的自动重试总次数；跨步骤累加，可大于 `max_step_attempts` |
| `attempt_no` | `task_step_attempt` | 步骤内递增序号，从 1 开始；中断也占号，便于审计 |
| `resume_count` | `task` | 被启动恢复回收的次数；与重试预算无关 |

`retry_count` 与 `attempt_count` 的关系：

```
某步骤第 1 次失败且还有预算 → retry_count += 1，attempt_count 保持 1
该步骤第 2 次失败且还有预算 → retry_count += 1，attempt_count = 2
第 3 次失败且达到上限   → 不增加 retry_count，任务 FAILED
```

### 3.3 退避

```
delaySeconds = min(backoffBaseSeconds × 2^(attempt_count - 1), backoffMaxSeconds)
```

默认 `backoffBaseSeconds=2`、`backoffMaxSeconds=30`：

| 第几次失败 | 下次重试等待 |
|---|---|
| 1 | 2 秒 |
| 2 | 4 秒 |
| 3 | 8 秒；默认上限 3 时不重试，任务失败 |
| 4 | 16 秒；仅当上限 ≥ 5 |
| 5 | 不重试（上限 5 封顶） |

约定：

- 不使用随机抖动，保证测试与演示可复现。
- `next_retry_at` 由数据库时间计算：`DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? SECOND)`，避免应用与数据库时钟偏差。
- worker 认领条件同时接受 `QUEUED` 与到期的 `RETRYING`；未到期的 `RETRYING` 不被提前执行。
- 退避期间任务不占用线程，worker 继续处理其他任务。

### 3.4 尝试记录

步骤开始状态必须与“认领”或“前一步成功”在同一事务中写入：

- 首个/重试步骤：由 worker 认领事务创建 attempt、置 step RUNNING、更新 task。
- 后续步骤：由前一步的成功事务同时创建下一 attempt、置下一步 RUNNING、更新 `current_stage`。
- 不另开“步骤开始”事务，避免出现任务 RUNNING 但没有 RUNNING 步骤的已提交状态。

事务内写入：

```
INSERT task_step_attempt(task_id, step_id, attempt_no, status='RUNNING', started_at=now)
UPDATE task_step SET status='RUNNING', started_at=now, finished_at=NULL, error_code=NULL,
                     attempt_count=attempt_count+1
UPDATE task SET status='RUNNING', current_stage=?, started_at=COALESCE(started_at, now)
```

执行结束后，按结果写回：

| 结果 | attempt | step | task |
|---|---|---|---|
| 成功 | `SUCCEEDED`、`finished_at` | `SUCCEEDED`、消息参数、清空错误 | 下一步的 `RUNNING` + 新 attempt（同一事务）或 `SUCCEEDED` |
| 可重试失败且有预算 | `FAILED`、错误码、`finished_at` | `FAILED`、`error_code`、`finished_at` | `RETRYING`、`next_retry_at`、`last_error_*`、`retry_count+1` |
| 不可重试或预算耗尽 | `FAILED`、错误码、`finished_at` | `FAILED` | `FAILED`、失败字段、清空 `last_error_*`、后续 `SKIPPED` |
| 进程中断 | `INTERRUPTED`、`finished_at` | `PENDING`、清空时间与错误 | `QUEUED`、`resume_count+1` |

事务边界：

- **认领/前一步成功 + attempt 开始**、**attempt 结束 + step 结束 + task 变化** 各为一个事务；睡眠与渲染绝不放在事务内。
- 结束事务同时更新 attempt、step、task，保证不会出现“attempt 成功但 step 仍 RUNNING”的提交状态。
- 所有更新带 `WHERE status IN (...)` 与 `row_version` 条件；影响行数为 0 说明发生了并发/迟到写入，放弃并记录警告。

### 3.5 上限、终止与后续步骤

1. 失败发生在步骤 `S`，则 `seq > S` 的步骤全部 `SKIPPED`；`seq < S` 的步骤保持 `SUCCEEDED`。
2. `SKIPPED` 步骤没有 attempt 记录。
3. 任务终态后，任何重试调度、续跑或迟到写入都必须被拒绝。
4. 重试等待期间，任务仍属于“活跃任务”，前端继续轮询并展示倒计时/等待状态。
5. 每次重试都重跑该步骤的完整逻辑，不保留步骤内部的中间结果。

### 3.6 模拟控制配合

`PUT /api/admin/simulation` 的失败对象增加 `retryable`：

```json
{
  "failure": {
    "stage": "GENERATE",
    "remaining": 2,
    "retryable": true
  }
}
```

语义：

- `remaining` 按**每次进入该阶段**扣减；同一任务的重试会再次消耗。
- `retryable=true` 时，注入的 `GENERATION_FAILED` 按可重试失败处理；`remaining=2`、上限 3 时，前两次尝试失败，第三次成功。
- `retryable=false`（默认）时，第一次命中即 `FAILED`，不进入 `RETRYING`。
- 每次注入都写 `last_error_simulated=1`（最终失败时转存 `failure_simulated=1`）；attempt 记录保留每次注入的失败码。
- 读取与扣减在同一事务中完成，保证并发下次数准确。

### 3.7 配置

| 环境变量 | 默认 | 范围 | 说明 |
|---|---|---|---|
| `FRAMELAB_RETRY_MAX_STEP_ATTEMPTS` | 3 | 1～5 | 新任务快照的上限 |
| `FRAMELAB_RETRY_BACKOFF_BASE_SECONDS` | 2 | 1～60 | 首次重试等待 |
| `FRAMELAB_RETRY_BACKOFF_MAX_SECONDS` | 30 | 1～600 | 退避封顶 |
| `FRAMELAB_WORKER_POLL_MS` | 500 | 100～5000 | 认领轮询间隔 |

配置只在任务创建时快照上限；退避参数在调度时读取。修改配置不迁移已有任务。

### 3.8 可观测性

- 日志字段：`taskId`、`step`、`attemptNo`、`status`、`errorCode`、`delaySeconds`、`resumeCount`。
- 指标（日志聚合即可，不引入 Prometheus）：重试次数、续跑次数、各失败码计数。
- 任务详情接口返回 attempt 历史；前端可展开查看每次失败原因与时间。
- 任务列表只返回 `retryCount`、`attemptCount`、`nextRetryAt` 等摘要，避免列表过重。

## 4. E-03 断点续跑

### 4.1 恢复原则

1. **只回收 `RUNNING`**：`QUEUED` 从未开始，保持原样；`RETRYING` 已有下次执行时间，保持原样；终态永不触碰。
2. **按步骤续跑**：已 `SUCCEEDED` 的步骤保留结果与时间戳；第一个非 `SUCCEEDED` 步骤重新执行。
3. **中断步骤整步重跑**：不保留步骤内部的渲染进度、临时变量或部分输出。
4. **不消耗重试预算**：被中断的尝试记为 `INTERRUPTED`，不计入 `attempt_count`、不增加 `retry_count`。
5. **启动恢复先于 worker**：恢复事务提交后才允许 worker 认领，避免旧任务与新进程竞争。
6. **恢复可重复执行**：恢复逻辑本身幂等；进程再次崩溃后重跑不会重复计数。

### 4.2 启动恢复算法

应用启动顺序：

```
Flyway 迁移
  → 恢复事务（单线程，进程内只执行一次）
  → 启动 worker 线程
  → 健康检查转为 READY
```

恢复事务：

```
BEGIN
  1. 选出所有 status='RUNNING' 的任务（按 created_at 排序）
  2. 对每个任务加行锁，读取其 steps 与 attempts
  3. 将仍为 RUNNING 的 attempt 改为 INTERRUPTED，finished_at=now
  4. 将仍为 RUNNING 的 step 改为 PENDING：
       started_at=NULL, finished_at=NULL, error_code=NULL
       若该 step 存在刚被中断的 attempt，则 attempt_count = attempt_count - 1
  5. 若存在被中断 attempt 但没有 RUNNING step（异常组合）：
       以 attempt 的 step 为准执行第 4 步
  6. task.status = 'QUEUED'
     task.next_retry_at = NULL
     task.finished_at = NULL
     task.current_stage = 保持最后阶段（用于前端说明“从中断处继续”）
     task.resume_count = resume_count + 1
     task.row_version = row_version + 1
     task.updated_at = now
  7. 清理该任务专属的临时目录 storage/tmp/task-{taskId}/
COMMIT
```

补充规则：

- 若发现任务所有步骤均已 `SUCCEEDED` 且结果字段完整，说明只差最终状态提交（正常事务下不应出现）；恢复为 `SUCCEEDED` 而不是重跑。
- 若任务处于 `RUNNING` 但没有任何 RUNNING step/attempt（崩溃发生在两个步骤之间），仍执行第 6 步，worker 会从第一个非 `SUCCEEDED` 步骤继续。
- 若恢复过程中数据库不可用，应用启动失败并重试；不允许在未恢复的情况下启动 worker。
- 恢复事务逐任务提交或整批提交均可，但必须在 worker 启动前完成；P0 数据量小，整批提交即可。

### 4.3 worker 续跑顺序

worker 认领任务后：

```
读取 steps ORDER BY seq
  → 跳过所有 SUCCEEDED
  → 找到第一个非 SUCCEEDED 步骤
  → 若该步骤为 PENDING：开始新 attempt
  → 若该步骤为 FAILED（只可能来自 RETRYING 到期）：开始新 attempt
  → 执行成功后进入下一步
  → 遇到可重试失败：按 E-02 调度或终止
```

约束：

- **已成功步骤不重跑**：不重新校验、不重新渲染、不更新其时间戳。
- **后续步骤不得提前执行**：前序非 `SUCCEEDED` 时，后续保持 `PENDING`。
- **恢复后的任务不重新生成任务快照**：模板标题、素材、画布以创建时为准。
- **`current_stage`** 在续跑开始时更新为当前步骤；恢复期间保留最后阶段，仅用于展示。

### 4.4 步骤幂等与临时文件

| 步骤 | 中断后重跑的安全性 | 保障 |
|---|---|---|
| MATERIAL_CHECK | 只读检查，天然幂等 | 重新读取素材文件；素材内容不可变 |
| GENERATE | 只计算版式，不落最终文件 | 结果写入任务临时目录；重跑覆盖同名文件 |
| FINALIZE | 可能已写临时文件/结果文件 | 先写 `storage/tmp/task-{id}/result-{attemptNo}.tmp`，`fsync` 后原子移动到最终路径；重跑覆盖同一路径 |

- 结果路径由任务 ID 确定：`results/{userId}/{taskId}.jpg`；重跑不会产生第二份结果。
- 临时目录在恢复时清理；正常完成后也删除。
- 数据库只在文件原子移动成功后提交成功状态；提交失败时文件可能已存在，下次重跑覆盖。
- 若结果文件已存在但任务未成功，允许覆盖；无需先删除。

### 4.5 计数与预算

| 场景 | `attempt_count` | `retry_count` | `resume_count` |
|---|---|---|---|
| 首次开始执行 | +1 | 不变 | 不变 |
| 可重试失败并安排重试 | 不变 | +1 | 不变 |
| 不可重试失败 | 不变 | 不变 | 不变 |
| 进程中断并恢复 | −1（抵消开始时的 +1） | 不变 | +1 |
| 恢复后重新执行 | +1 | 不变 | 不变 |

效果：无论重启多少次，只要步骤没有真正失败，重试预算不会减少；`resume_count` 单独记录中断次数，供 UI 与排障使用。

### 4.6 与 `RETRYING`、终态的关系

- 恢复只处理 `RUNNING`；`RETRYING` 任务的下次执行时间不变，重启不会重置退避。
- 若 `RETRYING` 在重启期间到期，worker 启动后会正常认领。
- `SUCCEEDED` / `FAILED` 任务不进入恢复扫描。
- 恢复后的任务仍是原任务、原 ID、原创建时间；不产生新任务，不影响历史排序。
- 前端可能看到 `RUNNING → QUEUED → RUNNING` 的状态回退；这是恢复语义，不是失败。UI 对 `QUEUED` 且 `resumeCount>0` 的任务显示“正在恢复”，不显示“排队中”作为唯一文案。

### 4.7 限制

- 只支持**步骤边界**续跑；步骤内部（例如已渲染一半的像素）不保留。
- 只在单实例部署下安全；两个实例同时启动会互相回收对方的 `RUNNING` 任务。
- 不恢复 `RETRYING` 的剩余等待时间以外的状态；等待时间本身不因重启延长或缩短。
- 不实现“取消任务”；恢复后的任务会继续执行到终态。

## 5. E-04 国际化

### 5.1 语言与协商

| 项 | 规则 |
|---|---|
| 支持语言 | `zh-CN`（默认）、`en-US` |
| 前端选择 | 顶栏语言切换；写 `NEXT_LOCALE` Cookie（`Path=/`、`SameSite=Lax`、一年有效） |
| 首次访问 | 无 Cookie 时使用 `zh-CN`；不根据浏览器语言自动切换，避免 SSR 与客户端不一致 |
| API 协商 | 读取 `Accept-Language`，支持 `en`, `en-US`, `zh`, `zh-CN` 及带 `q` 权重；无法匹配时回退 `zh-CN` |
| 响应头 | `Content-Language: zh-CN` 或 `en-US`；`Vary: Accept-Language` |
| 不支持的语言 | 不报错，按默认语言返回 |
| URL | P0 不使用 `/en/...` 前缀；同一路径按 Cookie/请求头切换，SEO 多语言列 P1 |

`Accept-Language` 解析示例：

```
en-US,en;q=0.9,zh-CN;q=0.8  → en-US
fr-FR,fr;q=0.9               → zh-CN（回退）
zh-TW                        → zh-CN（P0 不区分繁体）
```

### 5.2 前端结构

```
web/src/i18n/
├── config.ts              # SUPPORTED_LOCALES、默认语言、Cookie 名
├── messages/
│   ├── zh-CN.ts           # 基准词典（键的唯一定义源）
│   └── en-US.ts           # 必须与基准键集合一致
├── provider.tsx           # LocaleProvider、useI18n、useLocale
├── locale-cookie.ts       # 读写 NEXT_LOCALE（客户端）
└── format.ts              # Intl 日期、时间、数字、相对时间
```

规则：

1. `zh-CN.ts` 是键的唯一定义源；`en-US.ts` 使用同一类型的 `satisfies` 约束，缺键在类型检查阶段失败。
2. 词典只放**界面文案**：导航、按钮、状态标签、表单校验、空态、错误提示、语言名称。
3. 模板标题、模板描述、分类名称不放词典；它们来自 API 的翻译表。
4. 任务失败原因与步骤消息不放词典；它们来自 API 的本地化结果，避免同一句话维护两份。
5. 语言切换不刷新整页：写 Cookie、更新 Provider、更新 `<html lang>`、清空并重取查询缓存。
6. 服务端组件读取 Cookie 决定首屏语言，保证 SSR 与客户端一致，避免 hydration 闪烁。
7. 词典键使用点分层级，例如 `templates.empty.title`、`tasks.status.RETRYING`。

### 5.3 词典与文案边界

| 文案 | 来源 | 切换语言后 |
|---|---|---|
| 导航、按钮、状态标签 | 前端词典 | 立即变化 |
| 模板标题、描述、分类名称 | API + 翻译表 | 重新请求后变化 |
| 任务失败原因、步骤消息 | API `MessageSource` | 重新请求后变化 |
| 服务端校验错误 | API `ProblemDetail.detail` / `errors[].message` | 重新请求后变化 |
| 图片水印 | Java2D 固定英文 | 不变（D-09） |
| 用户上传的图片内容 | 用户 | 不变 |

### 5.4 后端消息

```
api/src/main/resources/i18n/
├── messages.properties          # 默认（等同 zh-CN）
├── messages_zh_CN.properties
└── messages_en_US.properties
```

- `MessageSource` 配置 `basename=classpath:i18n/messages`、`defaultEncoding=UTF-8`、`fallbackToSystemLocale=false`。
- 通过 `LocaleContextHolder` 或显式 `Locale` 解析；API 过滤器从 `Accept-Language` 写入请求上下文。
- `ProblemDetail` 的 `detail`、字段错误 `errors[].message`、任务失败与步骤消息都从消息键渲染。
- `errorCode` / `failureCode` 始终是稳定英文枚举，不随语言变化；前端优先按键映射，服务端文案作为兜底。
- 消息参数来自数据库 JSON，例如 `{ "checked": 2, "required": 2 }`；渲染时按当前语言插值。
- 异常堆栈、SQL、绝对路径只进日志，不进任何语言的响应。

消息键约定：

| 键 | 用途 |
|---|---|
| `error.<CODE>` | 请求错误与任务失败码的通用文案 |
| `task.failure.<CODE>` | 任务失败摘要（可带 `simulated` 前缀） |
| `task.step.<STAGE>.succeeded` | 步骤成功消息 |
| `task.step.<STAGE>.failed` | 步骤失败消息的兜底（优先 `task.failure.<CODE>`） |
| `task.step.<STAGE>.running` | 可选的过程说明 |
| `task.attempt.INTERRUPTED` | 尝试因服务重启中断 |
| `validation.<FIELD>.<RULE>` | 字段校验 |
| `language.zh-CN` / `language.en-US` | 语言名称（也可放前端词典） |

### 5.5 数据库翻译

基础表保留中文种子列，翻译表按 `locale` 覆盖：

```text
template_category_translation (
  category_id VARCHAR(32) NOT NULL,
  locale      VARCHAR(10) NOT NULL,
  name        VARCHAR(80) NOT NULL,
  PRIMARY KEY (category_id, locale)
)

template_translation (
  template_id VARCHAR(64) NOT NULL,
  locale      VARCHAR(10) NOT NULL,
  title       VARCHAR(80) NOT NULL,
  description VARCHAR(500) NOT NULL,
  PRIMARY KEY (template_id, locale)
)
```

解析顺序：

1. 请求语言为 `zh-CN` → 使用 `template_category.name` / `template.title` / `template.description` 基础列；
2. 请求语言为 `en-US` → 优先取翻译行；
3. 翻译行缺失 → 回退基础中文列，并在开发环境记录一次缺失警告；
4. 模板已下架或不存在 → 按 [04](04-api.md) 的 404 处理，不返回翻译。

任务快照与翻译：

- `task.template_title` 是创建时的中文快照，用于模板被下架/删除后的兜底。
- 查询任务时，模板仍存在则取当前语言翻译；缺翻译取模板基础列；模板不存在时返回任务快照标题。
- 封面、画布、素材顺序始终以任务快照为准，不随模板更新。

### 5.6 任务消息本地化

任务表不存“已翻译句子”，只存稳定标识：

| 字段 | 示例 | 说明 |
|---|---|---|
| `failure_code` | `GENERATION_FAILED` | 稳定失败码 |
| `failure_args` | `{"simulated":true}` | 消息插值参数 |
| `failure_simulated` | `1` | 是否模拟注入 |
| `failure_detail` | `simulated failure at GENERATE` | 技术诊断，仅日志/管理员排障，不直接展示 |
| `task_step.message_args` | `{"checked":2,"required":2}` | 步骤消息参数 |
| `task_step.error_code` | `MATERIAL_CHECK_FAILED` | 失败步骤的稳定码 |
| `task_step_attempt.error_code` | `RESULT_RENDER_FAILED` | 单次尝试的稳定码 |

读取时：

```
status=SUCCEEDED → 消息键 task.step.<STAGE>.succeeded + message_args
status=FAILED    → 消息键 task.failure.<error_code> + failure_args（任务级）
attempt FAILED   → 消息键 task.failure.<error_code> + 该尝试的参数
attempt INTERRUPTED → 消息键 task.attempt.INTERRUPTED
```

- 同一任务在中文与英文下读取，历史消息随语言变化；这是“按请求语言渲染”的预期行为。
- 技术诊断字段不参与翻译，也不返回给普通用户；管理员排障日志可引用。
- 模拟失败在用户文案中明确标注“模拟”，并通过 `failure.simulated` 供前端显示标记。

### 5.7 缓存、请求与刷新

1. API 客户端从当前语言读取并发送 `Accept-Language`；不依赖浏览器默认头。
2. TanStack Query 的 key 必须包含语言：
   - `['templates', locale, category]`
   - `['template', locale, templateId]`
   - `['tasks', locale, filters]`
   - `['task', locale, taskId]`
3. 切换语言时：
   - 写 Cookie；
   - 更新 Provider 与 `<html lang>`；
   - `queryClient.invalidateQueries()` 清空模板、任务、任务详情查询；
   - 取消在途请求（`AbortController`），避免旧语言响应覆盖新语言界面。
4. 任务轮询在语言切换后继续，但使用新语言 key；旧 key 的轮询停止。
5. 服务端响应带 `Vary: Accept-Language`；任务与素材响应为 `Cache-Control: no-store`，模板可短缓存但必须按语言区分。
6. 登录、退出、切换用户时清空全部查询缓存；语言 Cookie 不随退出清除（属于设备偏好）。
7. 日期、时间、相对时间、数字统一走 `Intl`：
   - `Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' })`
   - `Intl.RelativeTimeFormat(locale, { numeric: 'auto' })`
   - 时区使用浏览器本地时区；服务端时间戳保持 UTC。

### 5.8 排版与验收

- 英文界面使用同一套 Token，但需检查：
  - 顶栏导航、按钮、状态标签在 360px 下不溢出；
  - 模板卡片标题最多两行，英文长词允许断行；
  - 任务卡片失败原因可换行，不截断关键信息；
  - 尝试历史中的时间与错误码对齐；
  - 语言切换按钮有 `aria-label`，键盘可操作，焦点可见。
- 英文文案不使用全大写长句；按钮使用动词开头，保持简洁。
- 数字、日期、文件大小按语言格式化；文件大小单位可用 KB/MB，两种语言一致。
- 视觉验收需分别截取 `zh-CN` 与 `en-US` 的 UI-01/02/03。

### 5.9 回退规则

| 缺失项 | 行为 |
|---|---|
| 前端词典缺键 | 回退 `zh-CN` 同键；开发环境 console 警告；构建期类型检查应提前失败 |
| 后端消息缺键 | 回退默认 `messages.properties`；记录警告；不得返回空字符串 |
| 模板翻译缺失 | 回退基础中文列 |
| 分类翻译缺失 | 回退基础中文列 |
| 不支持的语言 | 归一化为 `zh-CN`，响应头写实际语言 |
| 语言 Cookie 非法 | 忽略并按 `zh-CN` 渲染，下一次切换时覆盖 |

### 5.10 边界

- 不翻译用户上传图片中的文字，也不翻译图片水印。
- 不提供第三种语言；不提供按地区自动切换货币/时区。
- 不做 URL 语言前缀与 `hreflang`；分享链接的语言取决于接收者的 Cookie/默认值（P1）。
- 翻译表只覆盖模板与分类；模拟配置、日志、错误码保持英文。
- 语言切换不改变任务执行、失败码、重试或续跑行为。

## 6. 组合状态机与不变量

### 6.1 任务状态

```
                 ┌──────────────┐
   提交成功 ────▶ │    QUEUED    │
                 └──────┬───────┘
                        │ worker 认领
                        ▼
                 ┌──────────────┐  最后一步成功   ┌────────────┐
                 │   RUNNING    │ ─────────────▶ │ SUCCEEDED  │
                 └───┬──────┬───┘                └────────────┘
    可重试失败且有预算 │      │ 不可重试或预算耗尽
                     ▼      ▼
              ┌───────────┐  ┌─────────┐
              │ RETRYING  │  │ FAILED  │
              └─────┬─────┘  └─────────┘
                    │ 到期被认领
                    └──────────────▶ RUNNING

   服务重启：RUNNING ──恢复──▶ QUEUED（resume_count+1，不进入 FAILED）
```

| 当前 | 事件 | 结果 | 关键写入 |
|---|---|---|---|
| — | 提交成功 | `QUEUED` | task + 3 steps + materials |
| `QUEUED` | worker 认领 | `RUNNING` | `started_at`、`current_stage`、step RUNNING、attempt RUNNING |
| `RUNNING` | 非最后一步成功 | `RUNNING` | step SUCCEEDED、下一步 RUNNING + 新 attempt（同一事务） |
| `RUNNING` | 最后一步成功 | `SUCCEEDED` | 结果字段、`finished_at`、step3 SUCCEEDED |
| `RUNNING` | 可重试失败且有预算 | `RETRYING` | step FAILED、attempt FAILED、`next_retry_at`、`last_error_*`、`retry_count+1` |
| `RUNNING` | 不可重试失败或预算耗尽 | `FAILED` | 失败字段、`finished_at`、清空 `last_error_*`、后续 SKIPPED |
| `RETRYING` | 到期被认领 | `RUNNING` | `next_retry_at=NULL`、step RUNNING、新 attempt |
| `RUNNING` | 服务重启恢复 | `QUEUED` | attempt INTERRUPTED、step PENDING、`resume_count+1` |
| `SUCCEEDED` / `FAILED` | 任意事件 | 不变 | 拒绝写入 |

### 6.2 步骤与尝试

```
Step:    PENDING ──▶ RUNNING ──成功──▶ SUCCEEDED
            ▲           │
            │           ├─可重试失败──▶ FAILED ──(任务 RETRYING)──┐
            │           │                                        │
            │           └─不可重试/耗尽──▶ FAILED（终态）         │
            └───────────恢复（attempt INTERRUPTED）◀──────────────┘

Attempt: RUNNING ──成功──▶ SUCCEEDED
              ├──失败──▶ FAILED
              └──重启──▶ INTERRUPTED
```

- 步骤 `FAILED` 在任务 `RETRYING` 时是“等待重试的失败快照”，下一次执行会重新置为 `RUNNING`。
- `SKIPPED` 只在前序失败且任务进入 `FAILED` 时写入。
- 失败步骤的 `started_at` / `finished_at` 反映最近一次尝试；更早尝试在 `task_step_attempt` 中查。
- `task_step_attempt` 只增不改（除 RUNNING→终态），作为审计记录保留。

### 6.3 不变量

1. `task.status = RUNNING` ⇒ 有且仅有一个 `RUNNING` 步骤，且该步骤有且仅有一个 `RUNNING` attempt。
2. `task.status = RETRYING` ⇒ `next_retry_at` 非空，`current_stage` 为失败阶段，存在一个 `FAILED` 步骤，且其后步骤全部 `PENDING`。
3. `task.status = QUEUED` 且 `resume_count > 0` ⇒ 可能存在非空 `started_at` 与 `current_stage`（恢复语义）。
4. `task.status = SUCCEEDED` ⇒ 三个步骤全部 `SUCCEEDED`，结果字段与 `finished_at` 非空。
5. `task.status = FAILED` ⇒ 存在一个 `FAILED` 步骤，其后步骤全部 `SKIPPED`，失败码非空，`finished_at` 非空。
6. `task.status = RETRYING` ⇒ `finished_at` 为空；`failure_code` 为空（失败尚未定案），`last_error_code` 非空。
7. 终态任务不得再被认领；认领条件只接受 `QUEUED` 或到期的 `RETRYING`。
8. `task_step.attempt_count` 等于该步骤非 `INTERRUPTED` 的 attempt 数；恢复时随中断回退。
9. 每个步骤的 `attempt_no` 唯一且从 1 递增；同一时刻最多一个 `RUNNING` attempt。
10. `task.retry_count` 等于所有步骤已安排的重试总数；中断不计入。
11. 消息字段只存码与参数，不存已翻译文本；API 读取时按语言渲染。
12. 幂等键在 `(user_id, client_request_id)` 上唯一；任务终态不释放键。

### 6.4 状态 × 轮询 × UI

| 任务状态 | 是否活跃 | 是否轮询 | 前端主文案 | 进度表现 |
|---|---|---|---|---|
| `QUEUED`（首次） | 是 | 是 | 排队中 | 三步均未开始 |
| `QUEUED`（`resumeCount>0`） | 是 | 是 | 正在恢复 | 保留已完成步骤，当前步骤显示恢复 |
| `RUNNING` | 是 | 是 | 处理中 · 当前阶段 | 步骤进度 + attempt 次数 |
| `RETRYING` | 是 | 是 | 等待重试 | 失败阶段 + 下次重试时间 + 尝试次数 |
| `SUCCEEDED` | 否 | 否 | 已完成 | 结果预览 |
| `FAILED` | 否 | 否 | 失败 | 失败阶段 + 原因 + 尝试历史 |
| 查询异常 | 保持最后快照 | 按退避重试 | 暂时无法更新 | 不改变任务状态 |

轮询生命周期见 [05 · 前端状态与轮询](05-architecture.md#8-前端状态与轮询) 与 [06 · 状态矩阵](06-frontend-design.md#11-状态反馈与文案)。

## 7. 配置项

| 配置 | 默认 | 归属 | 说明 |
|---|---|---|---|
| `FRAMELAB_RETRY_MAX_STEP_ATTEMPTS` | 3 | 后端 | 新任务每步尝试上限，1～5 |
| `FRAMELAB_RETRY_BACKOFF_BASE_SECONDS` | 2 | 后端 | 首次重试等待 |
| `FRAMELAB_RETRY_BACKOFF_MAX_SECONDS` | 30 | 后端 | 退避封顶 |
| `FRAMELAB_WORKER_POLL_MS` | 500 | 后端 | 含到期 `RETRYING` 的认领轮询 |
| `FRAMELAB_WORKER_THREADS` | 2 | 后端 | 并发处理任务数 |
| `NEXT_LOCALE` | 无 | 前端 Cookie | 语言偏好，一年有效 |
| `Accept-Language` | 无 | 请求头 | 后端消息与模板翻译协商 |

完整环境变量表见 [07 · 环境变量](07-testing-ci-deploy.md#66-环境变量)。

## 8. 演示脚本

> 以下命令假设 API 运行在 `http://localhost:8080`，已登录并持有 `$TOKEN`；任务创建需要预先上传素材，示例用 `$ASSET_ID` 占位。

### 8.1 幂等

```bash
BODY='{"templateId":"travel-postcard","clientRequestId":"11111111-1111-4111-8111-111111111111","assetIds":["'"$ASSET_ID"'"]}'

# 首次：202 + Location
curl -i -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "$BODY"

# 重放：200 + Idempotent-Replay: true，返回同一个任务 id
curl -i -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "$BODY"

# 同键不同素材序列：409 IDEMPOTENCY_KEY_REUSED
curl -i -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "${BODY/\"$ASSET_ID\"/\"another-asset-id\"}"
```

验收点：第二次响应头含 `Idempotent-Replay: true`，两次响应体 `id` 相同，数据库只有一行该键。

### 8.2 自动重试

```bash
# 让 GENERATE 连续失败 2 次，且可重试；默认上限 3 次尝试
curl -X PUT http://localhost:8080/api/admin/simulation \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"delays":{"materialCheckMs":800,"generateMs":3000,"finalizeMs":1200},"failure":{"stage":"GENERATE","remaining":2,"retryable":true}}'

# 创建任务后轮询
curl -s http://localhost:8080/api/tasks/$TASK_ID \
  -H "Authorization: Bearer $TOKEN" | jq '{status, retryCount, nextRetryAt, steps: [.steps[] | {stage, status, attemptCount, attempts}]}'
```

预期：

1. 第 1 次生成失败 → `RETRYING`，约 2 秒后重试；
2. 第 2 次生成失败 → `RETRYING`，约 4 秒后重试；
3. 第 3 次生成成功 → `FINALIZE` → `SUCCEEDED`；
4. `steps[1].attempts` 有三条记录：`FAILED`、`FAILED`、`SUCCEEDED`。

将 `remaining` 设为 3 或以上，可看到第 3 次失败后任务 `FAILED`、后续步骤 `SKIPPED`。

### 8.3 断点续跑

```bash
# 延长 GENERATE 耗时，确保重启发生在执行中
curl -X PUT http://localhost:8080/api/admin/simulation \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"delays":{"materialCheckMs":800,"generateMs":15000,"finalizeMs":1200},"failure":{"stage":null,"remaining":0,"retryable":false}}'

# 创建任务，等待进入 GENERATE 后重启 API
docker compose restart backend

# 观察启动日志中的 recovery，并轮询任务
curl -s http://localhost:8080/api/tasks/$TASK_ID \
  -H "Authorization: Bearer $TOKEN" | jq '{status, resumeCount, steps: [.steps[] | {stage, status, attemptCount, attempts}]}'
```

预期：

- 启动日志出现 `recoveredRunningTasks` 与任务 ID；
- `resumeCount=1`；
- 已成功的 `MATERIAL_CHECK` 不重跑；
- 当前步骤的 attempt 出现 `INTERRUPTED`，随后出现新的 `SUCCEEDED`；
- 任务最终 `SUCCEEDED`，`retryCount` 未因重启增加。

### 8.4 国际化

```bash
# 英文模板列表
curl -s http://localhost:8080/api/templates \
  -H 'Accept-Language: en-US' | jq '.items[0] | {title, description, category}'

# 中文（默认/回退）
curl -s http://localhost:8080/api/templates \
  -H 'Accept-Language: fr-FR' | jq '.items[0] | {title, category}'

# 英文任务详情：失败原因与步骤消息为英文
curl -s http://localhost:8080/api/tasks/$TASK_ID \
  -H "Authorization: Bearer $TOKEN" -H 'Accept-Language: en-US' \
  | jq '{status, steps: [.steps[] | {stage, status, message}]}'
```

UI 验收：

1. 顶栏切换 `中文 / English`，界面文案立即变化；
2. 模板标题与描述变为英文；
3. 刷新页面后语言保持；
4. 历史任务的失败原因随语言变化；
5. 结果为英文水印，不随语言变化。

## 9. 测试要点

| 范围 | 用例 | 断言 |
|---|---|---|
| E-01 | 首次 + 重放 | 202 / 200、同一 ID、`Idempotent-Replay: true`、单行 |
| E-01 | 并发 10 次提交 | 只创建 1 个任务，其余为重放或冲突 |
| E-01 | 同键不同载荷 | 409 `IDEMPOTENCY_KEY_REUSED` |
| E-01 | 模板下架后重放 | 仍返回原任务 |
| E-02 | 可重试失败 | `RETRYING`、`next_retry_at`、`retryCount=1` |
| E-02 | 退避时间 | 第 1 次 2s、第 2 次 4s（注入 Clock） |
| E-02 | 达到上限 | `FAILED`、后续 `SKIPPED`、attempt 数 = 上限 |
| E-02 | 不可重试失败 | 直接 `FAILED`，不进入 `RETRYING` |
| E-02 | 模拟 retryable | 注入失败按可重试处理；重试期间 `lastError.simulated=true`，最终 `failure.simulated=true` |
| E-02 | 成功步骤不重跑 | 只有失败步骤新增 attempt |
| E-03 | 重启恢复 | `RUNNING→QUEUED`、`resumeCount+1`、attempt `INTERRUPTED` |
| E-03 | 已成功步骤 | 时间戳与结果不变 |
| E-03 | 续跑不耗预算 | `attemptCount` 回退，`retryCount` 不变 |
| E-03 | 多次重启 | 仍能最终成功，`resumeCount` 累加 |
| E-03 | `RETRYING` 重启 | 不进入恢复，退避时间不被重置 |
| E-04 | 语言协商 | `en-US` / `en` / `zh-CN` / 不支持 → 默认 |
| E-04 | 错误本地化 | `detail` 与字段错误为英文；错误码不变 |
| E-04 | 任务消息 | 失败原因、步骤消息按语言渲染 |
| E-04 | 翻译回退 | 缺 en-US 翻译时返回中文，不返回空 |
| E-04 | 前端切换 | Cookie 写入、`<html lang>` 更新、查询重取 |
| E-04 | 缓存隔离 | 两种语言的模板/任务缓存不串 |
| 状态机 | 不变量 | 每个状态转换后校验 6.3 的 12 条 |

测试分层：

- 单元：失败分类、退避计算、消息渲染、语言协商、幂等载荷比较。
- 集成（Testcontainers MySQL）：认领、重试调度、启动恢复、并发幂等、翻译查询。
- 前端（Vitest + MSW）：`RETRYING` 卡片、attempt 展开、语言切换、缓存失效、英文布局。
- 手工/容器：8 的四个演示脚本。

完整矩阵见 [07 · 测试矩阵](07-testing-ci-deploy.md#3-测试矩阵)，验收条目见 [08](08-acceptance.md)。

## 10. 边界与后续

| 边界 | 说明 | 后续方向 |
|---|---|---|
| 单实例 | 启动恢复与认领依赖单写者 | 心跳/租约、多实例 worker |
| 步骤级续跑 | 不保留步骤内部进度 | 渲染分块与中间结果 |
| 无抖动退避 | 演示可复现，可能同时重试 | 加 jitter 与优先级队列 |
| 重试上限固定 | 每步 1～5 次 | 按模板/用户配置 |
| 幂等键不回收 | 与任务同寿命 | 归档任务时归档键 |
| 无 URL 语言前缀 | 分享链接语言依赖接收者 | `/en/...`、`hreflang` |
| 两种语言 | 不做地区变体 | 增加 `zh-TW`、`ja-JP` 等 |
| 无人工重试任务 | 失败后可重新创建 | 任务重试按钮（需权限与审计） |
| 模拟全局共享 | 失败落在最先进入阶段的任务 | 按用户/模板定向注入 |
