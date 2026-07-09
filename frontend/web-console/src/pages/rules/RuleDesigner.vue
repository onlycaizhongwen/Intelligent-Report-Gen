<template>
  <section class="page rule-page">
    <header class="toolbar">
      <h2>规则编排</h2>
      <div class="toolbar-actions">
        <el-button :loading="submittingReview" :disabled="!selectedRule || selectedRule.status !== 'draft'" @click="submitReview">提交审核</el-button>
        <el-button :loading="approving" :disabled="!selectedRule || selectedRule.status !== 'pending_review'" @click="approveSelectedRule">审批发布</el-button>
        <el-button :loading="runningProduction" :disabled="!selectedRule || selectedRule.status !== 'published'" @click="runProduction">生产运行</el-button>
        <el-button :loading="retryingSchedule" :disabled="!canRetrySchedule" @click="retrySelectedSchedule">人工重试调度</el-button>
        <el-button type="primary" :loading="debugging" :disabled="!selectedRule" @click="runDebug">调试运行</el-button>
      </div>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <section class="rule-layout">
      <aside class="rule-list">
        <div class="rule-list-header">
          <h3>规则列表</h3>
          <div class="rule-list-actions">
            <el-button size="small">+ 新建</el-button>
            <el-button size="small">从模板创建</el-button>
            <el-button size="small">导入规则</el-button>
          </div>
        </div>
        <button
          v-for="rule in rules"
          :key="String(rule.ruleId)"
          class="rule-item"
          :class="{ active: selectedRule?.ruleId === rule.ruleId }"
          type="button"
          @click="selectRule(rule)"
        >
          <strong>{{ rule.name }}</strong>
          <span>{{ ruleStatusLabel(rule.status) }} · v{{ rule.versionId }}</span>
        </button>
        <section class="node-library" aria-label="节点库">
          <h3>节点库</h3>
          <article v-for="group in nodeLibrary" :key="group.title">
            <h4>{{ group.title }}</h4>
            <span v-for="item in group.items" :key="item">{{ item }}</span>
          </article>
        </section>
      </aside>

      <main class="canvas">
        <template v-if="selectedRule">
          <section class="flow-canvas-panel">
            <div class="section-heading">
              <h3>交互式规则画布</h3>
              <span class="flow-hint">拖拽节点调整执行流程。</span>
            </div>
            <div class="flow-canvas-shell">
              <VueFlow
                v-model:nodes="flowNodes"
                v-model:edges="flowEdges"
                class="rule-flow-canvas"
                :fit-view-on-init="true"
                :nodes-draggable="true"
                :elements-selectable="false"
                :zoom-on-scroll="false"
                :pan-on-drag="true"
                @node-drag-stop="handleFlowNodeDragStop"
              >
                <template #node-default="{ id, data }">
                  <article
                    class="flow-node-card"
                    :class="[data.previewStatus ? `preview-${data.previewStatus}` : '', data.topologyStatus ? `topology-${data.topologyStatus}` : '']"
                    :data-testid="`rule-flow-node-${id}`"
                  >
                    <span>{{ data.type }}</span>
                    <strong>{{ data.label }}</strong>
                    <small v-if="data.topologyLabel">{{ data.topologyLabel }}</small>
                    <small v-if="data.previewLabel">{{ data.previewLabel }}</small>
                    <p>{{ data.summary }}</p>
                  </article>
                </template>
              </VueFlow>
            </div>
          </section>

          <section class="node-grid" aria-label="规则节点摘要列表">
            <article v-for="node in nodes" :key="node.id" class="node-card">
              <span>{{ node.type }}</span>
              <strong>{{ node.id }}</strong>
              <p v-if="node.type === 'condition'">{{ node.field }} {{ node.operator }} {{ node.value }}</p>
              <p v-else-if="node.type === 'branch'">
                {{ node.field }} {{ node.operator }} {{ node.value }} ? true/false
              </p>
              <p v-else-if="node.type === 'aggregate'">
                {{ node.operation }} {{ node.sourceField }}.{{ node.valueField || '*' }} -> {{ node.outputField }}
              </p>
              <p v-else-if="node.type === 'approval'">
                审批 {{ approvalNodeModeLabel(approvalNodeMode(node)) }} {{ approvalNodeRoles(node).join(', ') }} -> {{ node.approvalTitle }}
              </p>
              <p v-else-if="node.type === 'action' && node.actionType === 'webhook'">
                {{ node.method || 'POST' }} {{ node.endpoint }}
              </p>
              <p v-else-if="node.type === 'action' && node.actionType === 'create_task'">
                任务报告 {{ node.reportId }} -> 用户 {{ node.assigneeUserId }}
              </p>
            </article>
          </section>

          <section class="edge-list">
            <span
              v-for="edge in edges"
              :key="`${edge.source}-${edge.target}`"
              :class="{ 'topology-broken-edge-label': topologyIssueEdgeKeys.includes(edgeKey(edge)) }"
            >
              <template v-if="topologyIssueEdgeKeys.includes(edgeKey(edge))">断裂路径 </template>{{ edge.source }} -> {{ edge.target }}
            </span>
          </section>

          <section class="canvas-editor">
            <div class="section-heading">
              <h3>规则画布编辑器</h3>
              <el-button type="primary" :loading="savingCanvas" @click="saveCanvasChanges">保存画布变更</el-button>
            </div>
            <div class="template-apply-bar" aria-label="审批模板应用">
              <label class="field">
                <span>审批模板</span>
                <select v-model="selectedApprovalTemplateId" aria-label="审批模板选择">
                  <option value="">选择可复用审批模板</option>
                  <option
                    v-for="template in approvalTemplates"
                    :key="String(template.approvalTemplateId)"
                    :value="String(template.approvalTemplateId)"
                  >
                    {{ template.name }}
                  </option>
                </select>
              </label>
              <el-button :disabled="!selectedApprovalTemplateId" @click="applyApprovalTemplate">应用审批模板</el-button>
              <label class="field">
                <span>插入到节点之后</span>
                <select v-model="approvalTemplateInsertAfterNodeId" aria-label="审批模板插入位置">
                  <option value="">追加到画布末尾</option>
                  <option v-for="node in nodes" :key="node.id" :value="node.id">{{ node.id }}</option>
                </select>
              </label>
              <label v-if="approvalTemplateInsertCandidateEdges.length > 1" class="field">
                <span>分支路径</span>
                <select v-model="approvalTemplateInsertEdgeKey" aria-label="审批模板插入分支路径">
                  <option value="">全部流出路径</option>
                  <option
                    v-for="edge in approvalTemplateInsertCandidateEdges"
                    :key="edgeKey(edge)"
                    :value="edgeKey(edge)"
                  >
                    {{ edge.source }} -> {{ edge.target }}{{ edge.condition ? ` (${edge.condition})` : '' }}
                  </option>
                </select>
              </label>
              <span v-if="approvalTemplatePreviewPath" class="template-preview">{{ approvalTemplatePreviewPath }}</span>
              <div v-if="approvalTemplateEdgePreviewItems.length > 0" class="template-edge-preview" aria-label="审批模板边变更预览">
                <span
                  v-for="item in approvalTemplateEdgePreviewItems"
                  :key="`${item.changeType}-${item.source}-${item.target}-${item.condition ?? ''}`"
                >
                  {{ item.changeType === 'remove' ? '移除连线' : '新增连线' }} {{ edgePreviewLabel(item) }}
                </span>
              </div>
            </div>
            <div class="config-grid">
              <label class="field">
                <span>新增节点编号</span>
                <el-input v-model="newNodeForm.id" aria-label="新增节点编号" />
              </label>
              <label class="field">
                <span>新增节点类型</span>
                <select v-model="newNodeForm.type" aria-label="新增节点类型">
                  <option value="condition">condition</option>
                  <option value="branch">branch</option>
                  <option value="aggregate">aggregate</option>
                  <option value="approval">approval</option>
                  <option value="subprocess">subprocess</option>
                  <option value="action">action</option>
                  <option value="end">end</option>
                </select>
              </label>
              <label class="field" v-if="newNodeForm.type === 'action'">
                <span>新增动作类型</span>
                <el-input v-model="newNodeForm.actionType" aria-label="新增动作类型" />
              </label>
              <label class="field" v-if="newNodeForm.type === 'action'">
                <span>新增动作消息</span>
                <el-input v-model="newNodeForm.message" aria-label="新增动作消息" />
              </label>
              <template v-if="newNodeForm.type === 'action' && newNodeForm.actionType === 'create_task'">
                <label class="field">
                  <span>新增任务报告</span>
                  <select v-model="newNodeForm.reportId" aria-label="新增任务报告选择" @change="loadSelectedTaskReportDetail">
                    <option value="">手动填写报告编号</option>
                    <option v-for="report in taskReportOptions" :key="String(report.reportId)" :value="String(report.reportId)">
                      {{ report.title }} (#{{ report.reportId }})
                    </option>
                  </select>
                </label>
                <label class="field">
                  <span>新增任务报告编号</span>
                  <el-input v-model="newNodeForm.reportId" aria-label="新增任务报告编号" />
                </label>
                <label class="field">
                  <span>新增任务处理人</span>
                  <select v-model="newNodeForm.assigneeUserId" aria-label="新增任务处理人选择">
                    <option value="">手动填写处理人编号</option>
                    <option v-for="user in taskAssigneeOptions" :key="String(user.userId)" :value="String(user.userId)">
                      {{ user.displayName || user.username }} (#{{ user.userId }})
                    </option>
                  </select>
                </label>
                <label class="field">
                  <span>新增任务处理人编号</span>
                  <el-input v-model="newNodeForm.assigneeUserId" aria-label="新增任务处理人编号" />
                </label>
                <label class="field">
                  <span>新增任务内容</span>
                  <el-input v-model="newNodeForm.content" aria-label="新增任务内容" />
                </label>
                <label class="field">
                  <span>新增任务章节编号</span>
                  <el-input v-model="newNodeForm.sectionId" aria-label="新增任务章节编号" />
                </label>
                <label class="field">
                  <span>新增任务起始位置</span>
                  <el-input v-model="newNodeForm.startOffset" aria-label="新增任务起始位置" />
                </label>
                <label class="field">
                  <span>新增任务结束位置</span>
                  <el-input v-model="newNodeForm.endOffset" aria-label="新增任务结束位置" />
                </label>
                <label class="field">
                  <span>新增任务选中文本</span>
                  <el-input v-model="newNodeForm.selectedText" aria-label="新增任务选中文本" />
                </label>
                <div v-if="taskReportSections.length > 0" class="field field-wide task-anchor-picker">
                  <div class="section-heading compact">
                    <h4>任务锚点来源</h4>
                    <el-button size="small" @click="applySelectedTextAsTaskAnchor">使用选中文本作为任务锚点</el-button>
                  </div>
                  <article
                    v-for="section in taskReportSections"
                    :key="String(section.sectionId ?? section.heading)"
                    class="task-anchor-section"
                    :data-task-section-id="String(section.sectionId ?? section.heading)"
                  >
                    <strong>{{ section.heading }}</strong>
                    <p>{{ section.content }}</p>
                  </article>
                </div>
              </template>
              <template v-if="newNodeForm.type === 'condition' || newNodeForm.type === 'branch'">
                <label class="field">
                  <span>新增条件字段</span>
                  <el-input v-model="newNodeForm.field" aria-label="新增条件字段" />
                </label>
                <label class="field">
                  <span>新增条件操作符</span>
                  <el-input v-model="newNodeForm.operator" aria-label="新增条件操作符" />
                </label>
                <label class="field">
                  <span>新增条件值</span>
                  <el-input v-model="newNodeForm.value" aria-label="新增条件值" />
                </label>
              </template>
              <template v-if="newNodeForm.type === 'aggregate'">
                <label class="field">
                  <span>新增聚合源字段</span>
                  <el-input v-model="newNodeForm.sourceField" aria-label="新增聚合源字段" />
                </label>
                <label class="field">
                  <span>新增聚合操作</span>
                  <select v-model="newNodeForm.operation" aria-label="新增聚合操作">
                    <option value="sum">sum</option>
                    <option value="count">count</option>
                  </select>
                </label>
                <label class="field">
                  <span>新增聚合取值字段</span>
                  <el-input v-model="newNodeForm.valueField" aria-label="新增聚合取值字段" />
                </label>
                <label class="field">
                  <span>新增聚合输出字段</span>
                  <el-input v-model="newNodeForm.outputField" aria-label="新增聚合输出字段" />
                </label>
              </template>
              <template v-if="newNodeForm.type === 'subprocess'">
                <label class="field">
                  <span>新增子流程规则编号</span>
                  <el-input v-model="newNodeForm.subprocessRuleId" aria-label="新增子流程规则编号" />
                </label>
                <label class="field">
                  <span>新增子流程名称</span>
                  <el-input v-model="newNodeForm.subprocessName" aria-label="新增子流程名称" />
                </label>
              </template>
              <template v-if="newNodeForm.type === 'approval'">
                <label class="field">
                  <span>新增审批处理组织角色</span>
                  <el-select
                    v-model="selectedApprovalAssigneeRole"
                    aria-label="新增审批处理组织角色"
                    filterable
                    class="role-directory-select"
                    placeholder="从组织目录选择"
                    @change="appendApprovalAssigneeRole"
                  >
                    <el-option
                      v-for="option in roleDirectoryOptions"
                      :key="`approval-assignee-${option.role}`"
                      :label="option.label"
                      :value="option.role"
                    />
                  </el-select>
                </label>
                <label class="field">
                  <span>新增审批处理角色</span>
                  <el-input v-model="newNodeForm.assigneeRoles" aria-label="新增审批处理角色" placeholder="finance_manager, legal_manager" />
                </label>
                <label class="field">
                  <span>新增审批模式</span>
                  <select v-model="newNodeForm.approvalMode" aria-label="新增审批模式">
                    <option value="all">all</option>
                    <option value="any">any</option>
                  </select>
                </label>
                <label class="field">
                  <span>新增审批代理角色</span>
                  <el-select
                    v-model="newNodeForm.delegateRole"
                    aria-label="新增审批代理组织角色"
                    filterable
                    class="role-directory-select"
                    placeholder="从组织目录选择"
                  >
                    <el-option
                      v-for="option in roleDirectoryOptions"
                      :key="`approval-delegate-${option.role}`"
                      :label="option.label"
                      :value="option.role"
                    />
                  </el-select>
                  <el-input v-model="newNodeForm.delegateRole" aria-label="新增审批代理角色" placeholder="finance_delegate" />
                </label>
                <label class="field">
                  <span>新增审批代理生效开始</span>
                  <el-input v-model="newNodeForm.delegateActiveFrom" aria-label="新增审批代理生效开始" placeholder="2026-06-26T08:00:00Z" />
                </label>
                <label class="field">
                  <span>新增审批代理生效结束</span>
                  <el-input v-model="newNodeForm.delegateActiveTo" aria-label="新增审批代理生效结束" placeholder="2026-06-26T18:00:00Z" />
                </label>
                <label class="field">
                  <span>新增审批标题</span>
                  <el-input v-model="newNodeForm.approvalTitle" aria-label="新增审批标题" />
                </label>
                <label class="field">
                  <span>新增审批 SLA 小时</span>
                  <el-input v-model="newNodeForm.slaHours" aria-label="新增审批 SLA 小时" />
                </label>
                <label class="field">
                  <span>新增审批 SLA 升级角色</span>
                  <el-input v-model="newNodeForm.slaEscalationRole" aria-label="新增审批 SLA 升级角色" placeholder="finance_director" />
                </label>
                <label class="field">
                  <span>新增审批 SLA 升级策略</span>
                  <el-input v-model="newNodeForm.slaEscalations" aria-label="新增审批 SLA 升级策略" placeholder="4:finance_director, 8:risk_vp" />
                </label>
              </template>
            </div>
            <div class="editor-actions">
              <el-button @click="addCanvasNode">新增节点</el-button>
              <label class="field">
                <span>删除节点</span>
                <select v-model="deleteNodeId" aria-label="删除节点">
                  <option value="">选择节点</option>
                  <option v-for="node in deletableNodes" :key="node.id" :value="node.id">{{ node.id }}</option>
                </select>
              </label>
              <el-button :disabled="!deleteNodeId" @click="deleteCanvasNode">删除节点</el-button>
            </div>
            <div class="editor-actions">
              <label class="field">
                <span>新增连线源节点</span>
                <select v-model="newEdgeForm.source" aria-label="新增连线源节点">
                  <option value="">选择源节点</option>
                  <option v-for="node in nodes" :key="node.id" :value="node.id">{{ node.id }}</option>
                </select>
              </label>
              <label class="field">
                <span>新增连线目标节点</span>
                <select v-model="newEdgeForm.target" aria-label="新增连线目标节点">
                  <option value="">选择目标节点</option>
                  <option v-for="node in nodes" :key="node.id" :value="node.id">{{ node.id }}</option>
                </select>
              </label>
              <label class="field">
                <span>新增连线条件</span>
                <el-input v-model="newEdgeForm.condition" aria-label="新增连线条件" />
              </label>
              <el-button @click="addCanvasEdge">新增连线</el-button>
              <label class="field">
                <span>删除连线</span>
                <select v-model="deleteEdgeKey" aria-label="删除连线">
                  <option value="">选择连线</option>
                  <option v-for="edge in deletableEdges" :key="edge.key" :value="edge.key">{{ edge.label }}</option>
                </select>
              </label>
              <el-button :disabled="!deleteEdgeKey" @click="deleteCanvasEdge">删除连线</el-button>
            </div>
          </section>

          <section v-if="webhookActionNodes.length > 0" class="webhook-config">
            <div class="section-heading">
              <h3>Webhook 节点配置</h3>
              <el-button type="primary" :loading="savingWebhookConfig" @click="saveWebhookConfig">保存 Webhook 配置</el-button>
            </div>
            <label class="field">
              <span>Webhook 节点</span>
              <select v-model="selectedWebhookNodeId" aria-label="Webhook 节点" @change="syncWebhookConfigFromSelectedRule(selectedWebhookNodeId)">
                <option v-for="node in webhookActionNodes" :key="node.id" :value="node.id">{{ node.id }}</option>
              </select>
            </label>
            <div class="config-grid">
              <label class="field">
                <span>Webhook 地址</span>
                <el-input v-model="webhookConfig.endpoint" aria-label="Webhook 地址" />
              </label>
              <label class="field">
                <span>Webhook 方法</span>
                <el-input v-model="webhookConfig.method" aria-label="Webhook 方法" />
              </label>
              <label class="field">
                <span>Webhook 最大重试次数</span>
                <el-input v-model="webhookConfig.maxRetryCount" aria-label="Webhook 最大重试次数" />
              </label>
              <label class="field">
                <span>Webhook 重试退避秒数</span>
                <el-input v-model="webhookConfig.retryBackoffSeconds" aria-label="Webhook 重试退避秒数" />
              </label>
              <label class="field">
                <span>Webhook 最大异步重放次数</span>
                <el-input v-model="webhookConfig.maxAsyncReplayAttempts" aria-label="Webhook 最大异步重放次数" />
              </label>
              <label class="field">
                <span>Webhook 签名密钥</span>
                <el-input v-model="webhookConfig.signatureSecret" aria-label="Webhook 签名密钥" type="password" show-password />
              </label>
              <label class="field field-wide">
                <span>Webhook 请求头 JSON</span>
                <el-input v-model="webhookConfig.headersJson" type="textarea" :rows="4" aria-label="Webhook 请求头 JSON" />
              </label>
              <label class="field field-wide">
                <span>Webhook 请求体 JSON</span>
                <el-input v-model="webhookConfig.bodyJson" type="textarea" :rows="4" aria-label="Webhook 请求体 JSON" />
              </label>
            </div>
          </section>

          <label class="sample-editor">
            <h3>调试面板</h3>
            <p class="sample-preview">2026年Q3华东区销售额达到1.2亿元人民币，同比增长15.3%。</p>
            <span>调试样本 JSON</span>
            <el-input v-model="sampleJson" type="textarea" :rows="5" aria-label="调试样本 JSON" />
          </label>

          <section v-if="debugResult" class="debug-result">
            <h3>{{ debugResult.output.matched ? '命中' : '未命中' }}</h3>
            <p>已评估节点数：{{ debugResult.output.evaluatedNodes }}</p>
            <div class="trace-list">
              <span v-for="item in debugResult.output.trace" :key="item.nodeId">
                {{ formatTraceItem(item) }}
              </span>
            </div>
          </section>

          <section v-if="productionResult" class="debug-result">
            <h3>生产运行：{{ productionResult.output.matched ? '命中' : '未命中' }}</h3>
            <p>运行类型：{{ ruleRunTypeLabel(productionResult.runType) }} · 版本编号：{{ productionResult.versionId }}</p>
          </section>

          <section class="debug-result">
            <h3>运行历史</h3>
            <div class="trace-list">
              <span v-for="run in runHistory" :key="run.runId" class="run-history-row">
                #{{ run.runId }} · {{ ruleRunTypeLabel(run.runType) }} · {{ ruleRunStatusLabel(run.status) }} · {{ run.matched ? '命中' : '未命中' }}
              </span>
            </div>
          </section>
          <section v-if="runHistory.length > 0" class="debug-result run-topology-actions">
            <h3>运行拓扑</h3>
            <div class="topology-action-list">
              <el-button
                v-for="run in runHistory"
                :key="`topology-${run.runId}`"
                size="small"
                :loading="loadingSubprocessTopologyRunId === run.runId"
                @click="loadSubprocessTopology(run)"
              >
                拓扑 {{ run.runId }}
              </el-button>
            </div>
          </section>
          <section v-if="subprocessTopology" class="debug-result subprocess-topology">
            <h3>子流程拓扑</h3>
            <div class="topology-list" aria-label="子流程拓扑节点">
              <span v-for="node in subprocessTopology.nodes" :key="`${node.role}-${node.runId}`">
                {{ node.role }} 运行 {{ node.runId }} · 规则 {{ node.ruleId }} · {{ ruleRunStatusLabel(node.status) }}
              </span>
            </div>
            <div class="topology-list" aria-label="子流程拓扑连线">
              <span v-for="edge in subprocessTopology.edges" :key="`${edge.parentRunId}-${edge.subprocessRunId}-${edge.nodeId}`">
                {{ edge.nodeId }} · {{ edge.parentRunId }} -> {{ edge.subprocessRunId }} · {{ edge.result }}
              </span>
            </div>
          </section>
          <section class="debug-result">
            <h3>审批记录</h3>
            <div v-if="approvalRecords.length > 0" class="approval-record-list">
              <article v-for="record in approvalRecords" :key="record.approvalRecordId" class="approval-record-row">
                <div>
                  <strong>{{ record.nodeId }} · {{ record.assigneeRole }} · {{ approvalStatusLabel(record.status) }}</strong>
                  <small>{{ record.approvalTitle }}</small>
                  <small v-if="record.approvalComment">{{ record.approvalComment }}</small>
                </div>
                <div v-if="record.status === 'pending'" class="approval-actions">
                  <el-button
                    size="small"
                    type="primary"
                    :loading="approvalActionRecordId === record.approvalRecordId && approvalActionType === 'approve'"
                    @click="handleApprovalRecord(record, 'approve')"
                  >
                    通过
                  </el-button>
                  <el-button
                    size="small"
                    :loading="approvalActionRecordId === record.approvalRecordId && approvalActionType === 'reject'"
                    @click="handleApprovalRecord(record, 'reject')"
                  >
                    驳回
                  </el-button>
                </div>
              </article>
            </div>
            <el-empty v-else description="暂无审批记录" />
          </section>
          <section class="debug-result action-ledger">
            <div class="section-heading">
              <h3>Webhook 执行台账</h3>
              <div class="ledger-actions">
                <el-button :type="actionStatusFilter === 'compensatable' ? 'primary' : 'default'" @click="toggleCompensatableFilter">
                  仅待处理/失败
                </el-button>
                <el-button :loading="batchHandlingActionExecutions" :disabled="selectedActionExecutionIds.length === 0" @click="batchRetryActionExecutions">
                  批量重试
                </el-button>
                <el-button :loading="batchHandlingActionExecutions" :disabled="selectedActionExecutionIds.length === 0" @click="batchIgnoreActionExecutions">
                  批量忽略
                </el-button>
                <el-button :loading="loadingActionExecutions" @click="loadActionExecutions">刷新</el-button>
              </div>
            </div>
            <section class="compensation-summary" aria-label="补偿操作">
              <h4>补偿操作</h4>
              <span>待处理 {{ actionCompensationSummary.pending }}</span>
              <span>已成功 {{ actionCompensationSummary.succeeded }}</span>
              <span>已耗尽 {{ actionCompensationSummary.exhausted }}</span>
              <span>已忽略 {{ actionCompensationSummary.ignored }}</span>
              <strong>成功率 {{ actionCompensationSummary.successRate }}%</strong>
            </section>
            <div v-if="filteredActionExecutions.length > 0" class="ledger-list">
              <article v-for="execution in filteredActionExecutions" :key="execution.actionExecutionId" class="ledger-row">
                <el-checkbox
                  v-if="canRetryActionExecution(execution)"
                  :model-value="selectedActionExecutionIds.includes(execution.actionExecutionId)"
                  :aria-label="`选择动作 ${execution.actionExecutionId}`"
                  @change="(checked: boolean | string | number) => toggleActionSelection(execution.actionExecutionId, Boolean(checked))"
                />
                <span v-else class="ledger-select-placeholder" aria-hidden="true"></span>
                <div>
                  <strong>{{ execution.nodeId }}</strong>
                  <small v-if="execution.errorMessage">失败原因：{{ execution.errorMessage }}</small>
                  <small v-if="execution.sourceActionExecutionId">来源动作 #{{ execution.sourceActionExecutionId }}</small>
                  <span>{{ actionExecutionStatusLabel(execution.status) }} · 尝试 {{ execution.attempt }}/{{ execution.maxRetryCount }}</span>
                </div>
                <p>{{ execution.endpoint }}</p>
                <small>{{ execution.idempotencyKey }}</small>
                <el-button
                  v-if="canRetryActionExecution(execution)"
                  :loading="retryingActionExecutionId === execution.actionExecutionId"
                  @click="retryActionExecution(execution)"
                >
                  重试 {{ execution.actionExecutionId }}
                </el-button>
              </article>
            </div>
            <el-empty v-else description="暂无 Webhook 动作执行记录" />
          </section>
        </template>
        <el-empty v-else description="暂无规则" />
      </main>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { VueFlow, type Edge as FlowEdge, type Node as FlowNode } from '@vue-flow/core';
import '@vue-flow/core/dist/style.css';
import { adminApi, type AdminUser, type OrganizationDirectory } from '../../api/adminApi';
import { isLocalPreviewUnauthorizedError } from '../../api/client';
import { reportApi, type ReportDetail } from '../../api/reportApi';
import { ruleApi, type ApprovalTemplateStepPayload } from '../../api/ruleApi';
import type { ReportSummary } from '../../api/types';

interface RuleNode {
  id: string;
  type: 'start' | 'condition' | 'branch' | 'aggregate' | 'approval' | 'subprocess' | 'action' | 'end';
  position?: {
    x: number;
    y: number;
  };
  field?: string;
  operator?: string;
  value?: string | number | boolean;
  sourceField?: string;
  operation?: 'sum' | 'count';
  valueField?: string;
  outputField?: string;
  subprocessRuleId?: number;
  subprocessName?: string;
  assigneeRole?: string;
  assigneeRoles?: string[];
  approvalTemplateId?: number | string;
  approvalTemplateVersion?: number;
  approvalTemplateStepId?: string;
  approvalMode?: 'all' | 'any';
  delegateRole?: string;
  delegateActiveFrom?: string;
  delegateActiveTo?: string;
  approvalTitle?: string;
  slaHours?: number;
  slaEscalationRole?: string;
  slaEscalations?: Array<{
    afterHours: number;
    role: string;
  }>;
  actionType?: string;
  endpoint?: string;
  method?: string;
  maxRetryCount?: number;
  retryBackoffSeconds?: number;
  maxAsyncReplayAttempts?: number;
  signatureSecret?: string;
  headers?: Record<string, unknown>;
  body?: Record<string, unknown>;
  message?: string;
  reportId?: number;
  assigneeUserId?: number;
  content?: string;
  anchor?: {
    sectionId?: string;
    startOffset: number;
    endOffset: number;
    selectedText: string;
  };
}

interface RuleEdge {
  source: string;
  target: string;
  condition?: string;
}

interface RuleDefinition {
  nodes?: RuleNode[];
  edges?: RuleEdge[];
}

interface NewNodeForm {
  id: string;
  type: RuleNode['type'];
  actionType: string;
  message: string;
  field: string;
  operator: string;
  value: string;
  sourceField: string;
  operation: 'sum' | 'count';
  valueField: string;
  outputField: string;
  subprocessRuleId: string;
  subprocessName: string;
  assigneeRoles: string;
  approvalMode: 'all' | 'any';
  delegateRole: string;
  delegateActiveFrom: string;
  delegateActiveTo: string;
  approvalTitle: string;
  slaHours: string;
  slaEscalationRole: string;
  slaEscalations: string;
  reportId: string;
  assigneeUserId: string;
  content: string;
  sectionId: string;
  startOffset: string;
  endOffset: string;
  selectedText: string;
}

interface RuleSummary {
  ruleId: number | string;
  name: string;
  status: string;
  versionId: number | string;
  definition: RuleDefinition;
  scheduleEnabled?: boolean;
  scheduleIntervalSeconds?: number | null;
  nextRunAt?: string;
  failureCount?: number;
  maxRetryCount?: number;
  scheduleInput?: Record<string, unknown>;
}

interface ApprovalTemplateSummary {
  approvalTemplateId: number | string;
  version?: number;
  name: string;
  status: string;
  steps: ApprovalTemplateStepPayload[];
}

interface DebugResult {
  output: {
    matched: boolean;
    evaluatedNodes: number;
    trace: Array<{
      nodeId: string;
      type: string;
      matched: boolean;
      operation?: string;
      outputField?: string;
      value?: string | number | boolean;
      selectedPath?: 'true' | 'false';
      nextNodeId?: string;
    }>;
  };
}

interface ProductionRunResult extends DebugResult {
  debugRunId?: number | string;
  runId?: number | string;
  ruleId?: number | string;
  runType: string;
  versionId: number | string;
  status?: string;
  triggeredByUserId?: number | string | null;
  durationMs?: number | null;
  errorMessage?: string | null;
}

interface RuleRunHistoryItem {
  runId: number | string;
  ruleId: number | string;
  versionId: number | string;
  status: string;
  runType: string;
  matched: boolean;
  triggeredByUserId?: number | string | null;
  durationMs?: number | null;
  errorMessage?: string | null;
}

interface RuleSubprocessTopologyNode {
  runId: number | string;
  ruleId: number | string;
  versionId?: number | string;
  role: 'parent' | 'subprocess' | string;
  status: string;
  runType: string;
  durationMs?: number | null;
  errorMessage?: string | null;
}

interface RuleSubprocessTopologyEdge {
  operationLogId?: number | string;
  parentRunId: number | string;
  subprocessRunId: number | string;
  nodeId: string;
  subprocessRuleId: number | string;
  subprocessVersionId?: number | string;
  result: string;
  errorMessage?: string | null;
  createdAt?: string | null;
}

interface RuleSubprocessTopology {
  ruleId: number | string;
  runId: number | string;
  nodes: RuleSubprocessTopologyNode[];
  edges: RuleSubprocessTopologyEdge[];
}

interface RuleApprovalRecordItem {
  approvalRecordId: number | string;
  ruleId: number | string;
  runId: number | string;
  nodeId: string;
  assigneeRole: string;
  approvalTitle: string;
  status: string;
  createdByUserId?: number | string | null;
  approvedByUserId?: number | string | null;
  approvalComment?: string | null;
  approvedAt?: string | null;
  createdAt?: string | null;
}

interface RuleActionExecutionItem {
  actionExecutionId: number | string;
  ruleId: number | string;
  runId: number | string;
  sourceActionExecutionId?: number | string | null;
  nodeId: string;
  actionType: string;
  status: string;
  attempt: number;
  maxRetryCount: number;
  endpoint: string;
  idempotencyKey: string;
  nextRetryAt?: string;
  errorMessage?: string | null;
  metadata?: Record<string, unknown>;
}

const rules = ref<RuleSummary[]>([]);
const selectedRule = ref<RuleSummary | null>(null);
const sampleJson = ref('{"daysOverdue":45}');
const debugResult = ref<DebugResult | null>(null);
const productionResult = ref<ProductionRunResult | null>(null);
const runHistory = ref<RuleRunHistoryItem[]>([]);
const subprocessTopology = ref<RuleSubprocessTopology | null>(null);
const approvalRecords = ref<RuleApprovalRecordItem[]>([]);
const actionExecutions = ref<RuleActionExecutionItem[]>([]);
type RuleFlowNodeData = {
  label: string;
  type: string;
  summary: string;
  previewStatus?: 'insert-point' | 'target' | 'approval-step';
  previewLabel?: string;
  topologyStatus?: 'blocked';
  topologyLabel?: string;
};
type ApprovalTemplateEdgePreviewItem = {
  changeType: 'remove' | 'add';
  source: string;
  target: string;
  condition?: string;
};
type RuleTopologyValidationResult = {
  message: string;
  blockedNodeIds: string[];
  blockedEdgeKeys: string[];
};

const flowNodes = ref<Array<FlowNode<RuleFlowNodeData>>>([]);
const flowEdges = ref<Array<FlowEdge>>([]);
const topologyIssueNodeIds = ref<string[]>([]);
const topologyIssueEdgeKeys = ref<string[]>([]);
const taskReportOptions = ref<ReportSummary[]>([]);
const taskAssigneeOptions = ref<AdminUser[]>([]);
const organizationDirectory = ref<OrganizationDirectory>({ departments: [], roles: [] });
const approvalTemplates = ref<ApprovalTemplateSummary[]>([]);
const taskReportSections = ref<ReportDetail['sections']>([]);
const selectedApprovalAssigneeRole = ref('');
const selectedApprovalTemplateId = ref('');
const approvalTemplateInsertAfterNodeId = ref('');
const approvalTemplateInsertEdgeKey = ref('');
const selectedActionExecutionIds = ref<Array<number | string>>([]);
const actionStatusFilter = ref<'all' | 'compensatable'>('all');
const debugging = ref(false);
const submittingReview = ref(false);
const approving = ref(false);
const runningProduction = ref(false);
const retryingSchedule = ref(false);
const approvalActionRecordId = ref<number | string | null>(null);
const approvalActionType = ref<'approve' | 'reject' | null>(null);
const loadingActionExecutions = ref(false);
const retryingActionExecutionId = ref<number | string | null>(null);
const loadingSubprocessTopologyRunId = ref<number | string | null>(null);
const batchHandlingActionExecutions = ref(false);
const savingWebhookConfig = ref(false);
const savingCanvas = ref(false);
const errorMessage = ref('');
const nodeLibrary = [
  { title: '输入节点', items: ['文件输入', '文本输入', '数据库读取'] },
  { title: '解析节点', items: ['文本解析', '表格解析', '关键词提取'] },
  { title: '分类节点', items: ['规则匹配', '正则分类', '语义分类'] },
  { title: '输出节点', items: ['知识库入库', '数据导出', '通知推送'] }
];
const selectedWebhookNodeId = ref('');
const deleteNodeId = ref('');
const deleteEdgeKey = ref('');
const newNodeForm = ref<NewNodeForm>({
  id: '',
  type: 'action' as RuleNode['type'],
  actionType: 'notify',
  message: '',
  field: '',
  operator: '',
  value: '',
  sourceField: '',
  operation: 'sum',
  valueField: '',
  outputField: '',
  subprocessRuleId: '',
  subprocessName: '',
  assigneeRoles: '',
  approvalMode: 'all',
  delegateRole: '',
  delegateActiveFrom: '',
  delegateActiveTo: '',
  approvalTitle: '',
  slaHours: '',
  slaEscalationRole: '',
  slaEscalations: '',
  reportId: '',
  assigneeUserId: '',
  content: '',
  sectionId: '',
  startOffset: '',
  endOffset: '',
  selectedText: ''
});
const newEdgeForm = ref({
  source: '',
  target: '',
  condition: ''
});
const webhookConfig = ref({
  endpoint: '',
  method: 'POST',
  maxRetryCount: '0',
  retryBackoffSeconds: '0',
  maxAsyncReplayAttempts: '3',
  signatureSecret: '',
  headersJson: '{}',
  bodyJson: '{}'
});

const nodes = computed(() => selectedRule.value?.definition.nodes ?? []);
const edges = computed(() => selectedRule.value?.definition.edges ?? []);
const roleDirectoryOptions = computed(() => {
  const options = new Map<string, { role: string; label: string }>();
  organizationDirectory.value.roles.forEach((roleItem) => {
    const role = roleItem.role.trim();
    if (!role || options.has(role)) {
      return;
    }
    const user = roleItem.users[0];
    const displayName = user?.displayName?.trim() || user?.username || role;
    options.set(role, {
      role,
      label: `${roleItem.department || 'No department'} / ${roleItem.position || 'No position'} / ${role} / ${displayName}`
    });
  });
  return Array.from(options.values()).sort((left, right) => left.label.localeCompare(right.label));
});
const webhookActionNodes = computed(() => nodes.value.filter((node) => node.type === 'action' && node.actionType === 'webhook'));
const deletableNodes = computed(() => nodes.value.filter((node) => node.type !== 'start'));
const deletableEdges = computed(() => edges.value.map((edge) => ({
  ...edge,
  key: edgeKey(edge),
  label: `${edge.source}->${edge.target}`
})));
const filteredActionExecutions = computed(() => {
  if (actionStatusFilter.value === 'all') {
    return actionExecutions.value;
  }
  return actionExecutions.value.filter((execution) => canRetryActionExecution(execution));
});
const actionCompensationSummary = computed(() => {
  const webhookExecutions = actionExecutions.value.filter((execution) => execution.actionType === 'webhook');
  const succeeded = webhookExecutions.filter((execution) => execution.status === 'succeeded').length;
  const pending = webhookExecutions.filter((execution) => execution.status === 'pending_retry' || execution.status === 'failed').length;
  const exhausted = webhookExecutions.filter((execution) => execution.status === 'compensation_exhausted').length;
  const ignored = webhookExecutions.filter((execution) => execution.status === 'compensation_ignored').length;
  const total = webhookExecutions.length;
  return {
    pending,
    succeeded,
    exhausted,
    ignored,
    successRate: total === 0 ? 0 : Math.round((succeeded / total) * 100)
  };
});
const canRetrySchedule = computed(() => {
  if (!selectedRule.value || selectedRule.value.status !== 'published' || !selectedRule.value.scheduleEnabled) {
    return false;
  }
  const failureCount = selectedRule.value.failureCount ?? 0;
  const maxRetryCount = selectedRule.value.maxRetryCount ?? 3;
  return maxRetryCount >= 0 && failureCount >= maxRetryCount;
});
const selectedApprovalTemplate = computed(() => approvalTemplates.value.find(
  (item) => String(item.approvalTemplateId) === selectedApprovalTemplateId.value
) ?? null);
const approvalTemplateInsertCandidateEdges = computed(() => {
  const insertAfterNodeId = approvalTemplateInsertAfterNodeId.value;
  if (!insertAfterNodeId) {
    return [];
  }
  return edges.value.filter((edge) => edge.source === insertAfterNodeId);
});
const approvalTemplateTargetOutgoingEdges = computed(() => {
  const selectedEdgeKey = approvalTemplateInsertEdgeKey.value;
  if (!selectedEdgeKey) {
    return approvalTemplateInsertCandidateEdges.value;
  }
  return approvalTemplateInsertCandidateEdges.value.filter((edge) => edgeKey(edge) === selectedEdgeKey);
});
const approvalTemplatePreviewPath = computed(() => {
  const template = selectedApprovalTemplate.value;
  if (!template || template.steps.length === 0) {
    return '';
  }
  const prefix = `tpl${template.approvalTemplateId}_`;
  const templateNodeIds = template.steps.map((step, index) => `${prefix}${step.stepId || `step${index + 1}`}`);
  const insertAfterNodeId = approvalTemplateInsertAfterNodeId.value;
  if (!insertAfterNodeId) {
    return `Preview path ${templateNodeIds.join(' -> ')}`;
  }
  const outgoingTargets = approvalTemplateTargetOutgoingEdges.value.map((edge) => edge.target);
  return `Preview path ${[insertAfterNodeId, ...templateNodeIds, ...outgoingTargets].join(' -> ')}`;
});
const approvalTemplateEdgePreviewItems = computed(() => {
  const template = selectedApprovalTemplate.value;
  const insertAfterNodeId = approvalTemplateInsertAfterNodeId.value;
  if (!template || template.steps.length === 0 || !insertAfterNodeId) {
    return [];
  }
  const prefix = `tpl${template.approvalTemplateId}_`;
  const templateNodeIds = template.steps.map((step, index) => `${prefix}${step.stepId || `step${index + 1}`}`);
  const firstTemplateNodeId = templateNodeIds[0];
  const lastTemplateNodeId = templateNodeIds[templateNodeIds.length - 1];
  return approvalTemplateTargetOutgoingEdges.value.flatMap((edge) => [
    {
      changeType: 'remove' as const,
      source: edge.source,
      target: edge.target,
      condition: edge.condition
    },
    {
      changeType: 'add' as const,
      source: insertAfterNodeId,
      target: firstTemplateNodeId,
      condition: edge.condition
    },
    {
      changeType: 'add' as const,
      source: lastTemplateNodeId,
      target: edge.target,
      condition: edge.condition
    }
  ]);
});

watch(
  () => [
    selectedRule.value?.definition,
    selectedApprovalTemplate.value,
    approvalTemplateInsertAfterNodeId.value,
    approvalTemplateInsertEdgeKey.value
  ],
  () => refreshFlowPreview(),
  { immediate: true, deep: true }
);

watch(approvalTemplateInsertAfterNodeId, () => {
  approvalTemplateInsertEdgeKey.value = '';
});

async function loadRules() {
  errorMessage.value = '';
  try {
    const [result] = await Promise.all([
      ruleApi.listRules({ page: 1, pageSize: 10 }) as unknown as Promise<{ items: RuleSummary[] }>,
      loadTaskReferenceOptions(),
      loadApprovalTemplates()
    ]);
    rules.value = result.items ?? [];
    selectedRule.value = rules.value[0] ?? null;
    syncWebhookConfigFromSelectedRule();
    productionResult.value = null;
    await refreshRuleAuxiliaryData();
  } catch (error) {
    if (isLocalPreviewUnauthorizedError(error)) {
      rules.value = previewRules();
      selectedRule.value = rules.value[0] ?? null;
      sampleJson.value = JSON.stringify({
        text: '2026年Q3华东区销售额达到1.2亿元人民币，同比增长15.3%。其中上海区域贡献最大，达到6800万元，杭州区域3800万元。'
      }, null, 2);
      syncWebhookConfigFromSelectedRule();
      productionResult.value = null;
      return;
    }
    errorMessage.value = error instanceof Error ? error.message : '规则加载失败';
  }
}

function previewRules(): RuleSummary[] {
  return [
    {
      ruleId: 'preview-sales',
      name: '销售考核规则',
      status: 'draft',
      versionId: 1,
      definition: {
        nodes: [
          { id: 'fileInput', type: 'start', position: { x: 40, y: 40 } },
          { id: 'parseText', type: 'condition', field: 'text', operator: 'contains', value: '销售额', position: { x: 260, y: 40 } },
          { id: 'extractKeyword', type: 'aggregate', sourceField: 'sales', operation: 'sum', valueField: 'amount', outputField: 'totalSales', position: { x: 480, y: 40 } },
          { id: 'classify', type: 'branch', field: 'totalSales', operator: '>=', value: 100000000, position: { x: 700, y: 40 } },
          { id: 'saveKnowledge', type: 'action', actionType: 'notify', message: '入库并通知分析师', position: { x: 920, y: 40 } },
          { id: 'end', type: 'end', position: { x: 1140, y: 40 } }
        ],
        edges: [
          { source: 'fileInput', target: 'parseText' },
          { source: 'parseText', target: 'extractKeyword' },
          { source: 'extractKeyword', target: 'classify' },
          { source: 'classify', target: 'saveKnowledge', condition: 'true' },
          { source: 'saveKnowledge', target: 'end' }
        ]
      }
    },
    {
      ruleId: 'preview-finance',
      name: '财务数据提取规则',
      status: 'published',
      versionId: 2,
      definition: {
        nodes: [
          { id: 'input', type: 'start' },
          { id: 'tableParse', type: 'condition', field: 'fileType', operator: '=', value: 'excel' },
          { id: 'end', type: 'end' }
        ],
        edges: [
          { source: 'input', target: 'tableParse' },
          { source: 'tableParse', target: 'end' }
        ]
      }
    },
    {
      ruleId: 'preview-law',
      name: '法规文本分类规则',
      status: 'draft',
      versionId: 1,
      definition: {
        nodes: [
          { id: 'input', type: 'start' },
          { id: 'semanticClassify', type: 'condition', field: 'topic', operator: 'contains', value: '监管' },
          { id: 'end', type: 'end' }
        ],
        edges: [
          { source: 'input', target: 'semanticClassify' },
          { source: 'semanticClassify', target: 'end' }
        ]
      }
    }
  ];
}

async function loadApprovalTemplates() {
  try {
    const result = await ruleApi.listApprovalTemplates({
      page: 1,
      pageSize: 20,
      status: 'enabled'
    }) as unknown as {
      items?: ApprovalTemplateSummary[];
      data?: { items?: ApprovalTemplateSummary[] };
    };
    approvalTemplates.value = result.items ?? result.data?.items ?? [];
  } catch {
    approvalTemplates.value = [];
  }
}

async function loadTaskReferenceOptions() {
  const [reportsResult, usersResult, organizationDirectoryResult] = await Promise.allSettled([
    reportApi.list({ page: 1, pageSize: 20 }),
    adminApi.listUsers({ page: 1, pageSize: 20 }),
    adminApi.organizationDirectory()
  ]);
  if (reportsResult.status === 'fulfilled') {
    taskReportOptions.value = reportsResult.value.items ?? [];
  }
  if (usersResult.status === 'fulfilled') {
    taskAssigneeOptions.value = (usersResult.value.items ?? []).filter((user) => user.status === 'enabled');
  }
  if (organizationDirectoryResult.status === 'fulfilled') {
    organizationDirectory.value = {
      departments: organizationDirectoryResult.value.departments ?? [],
      roles: organizationDirectoryResult.value.roles ?? []
    };
  }
}

async function loadSelectedTaskReportDetail() {
  const reportId = newNodeForm.value.reportId.trim();
  taskReportSections.value = [];
  if (!reportId) {
    return;
  }
  try {
    const report = await reportApi.getDetail(reportId);
    taskReportSections.value = report.sections ?? [];
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '任务报告详情加载失败';
  }
}

function applySelectedTextAsTaskAnchor() {
  const selection = window.getSelection();
  const selectedText = selection?.toString().trim() ?? '';
  const range = selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null;
  const anchorNode = range?.commonAncestorContainer ?? null;
  const anchorElement = anchorNode instanceof Element ? anchorNode : anchorNode?.parentElement;
  const sectionElement = anchorElement?.closest<HTMLElement>('[data-task-section-id]');
  const contentElement = sectionElement?.querySelector('p');
  const contentText = contentElement?.textContent ?? '';
  const startOffset = contentText.indexOf(selectedText);
  if (!sectionElement || !selectedText || startOffset < 0) {
    errorMessage.value = '请先选择报告文本再应用任务锚点';
    return;
  }
  newNodeForm.value.sectionId = sectionElement.dataset.taskSectionId ?? '';
  newNodeForm.value.startOffset = String(startOffset);
  newNodeForm.value.endOffset = String(startOffset + selectedText.length);
  newNodeForm.value.selectedText = selectedText;
  errorMessage.value = '';
}

function buildFlowNodes(definitionNodes: RuleNode[]) {
  const previewStatuses = approvalTemplateNodePreviewStatuses();
  const topologyIssueIds = new Set(topologyIssueNodeIds.value);
  return definitionNodes.map((node, index) => {
    const position = normalizeNodePosition(node, index);
    const previewStatus = previewStatuses.get(node.id);
    const topologyStatus = topologyIssueIds.has(node.id) ? 'blocked' as const : undefined;
    return {
      id: node.id,
      type: 'default',
      position,
      data: {
        label: node.id,
        type: node.type,
        summary: nodeSummary(node),
        previewStatus,
        previewLabel: previewStatus ? approvalTemplatePreviewStatusLabel(previewStatus) : undefined,
        topologyStatus,
        topologyLabel: topologyStatus ? '拓扑阻塞' : undefined
      }
    };
  });
}

function refreshFlowPreview() {
  const definition = selectedRule.value?.definition;
  const baseNodes = buildFlowNodes(definition?.nodes ?? []);
  flowNodes.value = [
    ...baseNodes,
    ...buildApprovalTemplatePreviewFlowNodes(baseNodes)
  ];
  flowEdges.value = [
    ...buildFlowEdges(definition?.edges ?? []),
    ...buildApprovalTemplatePreviewFlowEdges()
  ];
}

function approvalTemplateNodePreviewStatuses() {
  const statuses = new Map<string, RuleFlowNodeData['previewStatus']>();
  const insertAfterNodeId = approvalTemplateInsertAfterNodeId.value;
  if (!insertAfterNodeId || !selectedApprovalTemplate.value) {
    return statuses;
  }
  statuses.set(insertAfterNodeId, 'insert-point');
  approvalTemplateTargetOutgoingEdges.value.forEach((edge) => {
    statuses.set(edge.target, 'target');
  });
  return statuses;
}

function approvalTemplatePreviewStatusLabel(status: NonNullable<RuleFlowNodeData['previewStatus']>) {
  if (status === 'insert-point') return '预览插入点';
  if (status === 'target') return '预览目标节点';
  return '预览审批步骤';
}

function buildApprovalTemplatePreviewFlowNodes(baseNodes: Array<FlowNode<RuleFlowNodeData>>) {
  const template = selectedApprovalTemplate.value;
  const insertAfterNodeId = approvalTemplateInsertAfterNodeId.value;
  if (!template || template.steps.length === 0 || !insertAfterNodeId) {
    return [];
  }
  const insertAfterNode = baseNodes.find((node) => node.id === insertAfterNodeId);
  const basePosition = insertAfterNode?.position ?? { x: 40, y: 40 };
  const prefix = `tpl${template.approvalTemplateId}_`;
  return template.steps.map((step, index) => {
    const stepId = step.stepId || `step${index + 1}`;
    return {
      id: `preview-${prefix}${stepId}`,
      type: 'default',
      position: {
        x: basePosition.x + 220 * (index + 1),
        y: basePosition.y + 96
      },
      data: {
        label: `${prefix}${stepId}`,
        type: 'approval',
        summary: step.approvalTitle,
        previewStatus: 'approval-step' as const,
        previewLabel: approvalTemplatePreviewStatusLabel('approval-step')
      },
      draggable: false,
      selectable: false
    };
  });
}

function buildFlowEdges(definitionEdges: RuleEdge[]) {
  const brokenEdgeKeys = new Set(topologyIssueEdgeKeys.value);
  return definitionEdges.map((edge) => ({
    id: edgeKey(edge),
    source: edge.source,
    target: edge.target,
    label: brokenEdgeKeys.has(edgeKey(edge)) ? `断裂路径 ${edge.source} -> ${edge.target}` : edge.condition ?? '',
    class: brokenEdgeKeys.has(edgeKey(edge)) ? 'topology-broken-edge' : undefined
  }));
}

function buildApprovalTemplatePreviewFlowEdges() {
  const template = selectedApprovalTemplate.value;
  if (!template || template.steps.length === 0 || !approvalTemplateInsertAfterNodeId.value) {
    return [];
  }
  const prefix = `tpl${template.approvalTemplateId}_`;
  return approvalTemplateEdgePreviewItems.value.map((item) => {
    const source = item.changeType === 'add' && item.source.startsWith(prefix)
      ? `preview-${item.source}`
      : item.source;
    const target = item.changeType === 'add' && item.target.startsWith(prefix)
      ? `preview-${item.target}`
      : item.target;
    return {
      id: `preview-${item.changeType}-${edgeKey(item)}`,
      source,
      target,
      label: approvalTemplateFlowEdgePreviewLabel(item),
      class: `preview-${item.changeType}-edge`,
      animated: item.changeType === 'add',
      selectable: false,
      focusable: false
    };
  });
}

function approvalTemplateFlowEdgePreviewLabel(item: ApprovalTemplateEdgePreviewItem) {
  return `预览${item.changeType === 'remove' ? '移除' : '新增'}连线 ${item.source} -> ${item.target}`;
}

function normalizeNodePosition(node: RuleNode, index: number) {
  if (
    node.position &&
    Number.isFinite(node.position.x) &&
    Number.isFinite(node.position.y)
  ) {
    return {
      x: node.position.x,
      y: node.position.y
    };
  }
  return {
    x: 40 + (index % 4) * 220,
    y: 40 + Math.floor(index / 4) * 140
  };
}

function nodeSummary(node: RuleNode) {
  if (node.type === 'condition') {
    return `${node.field} ${node.operator} ${node.value}`;
  }
  if (node.type === 'branch') {
    return `${node.field} ${node.operator} ${node.value} ? true/false`;
  }
  if (node.type === 'aggregate') {
    return `${node.operation} ${node.sourceField}.${node.valueField || '*'} -> ${node.outputField}`;
  }
  if (node.type === 'subprocess') {
    return `子流程 ${node.subprocessRuleId}${node.subprocessName ? ` -> ${node.subprocessName}` : ''}`;
  }
  if (node.type === 'approval') {
    const delegateText = node.delegateRole ? ` 代理 ${node.delegateRole}` : '';
    const delegateWindowText = node.delegateActiveFrom || node.delegateActiveTo
      ? ` ${node.delegateActiveFrom || '*'}..${node.delegateActiveTo || '*'}`
      : '';
    const escalationText = node.slaEscalationRole ? ` 升级 ${node.slaEscalationRole}` : '';
    const escalationPolicyText = node.slaEscalations?.length
      ? ` 策略 ${node.slaEscalations.map((policy) => `${policy.afterHours}h:${policy.role}`).join(', ')}`
      : '';
    return `审批 ${approvalNodeModeLabel(approvalNodeMode(node))} ${approvalNodeRoles(node).join(', ')}${delegateText}${delegateWindowText}${escalationText}${escalationPolicyText} -> ${node.approvalTitle}`;
  }
  if (node.type === 'action' && node.actionType === 'webhook') {
    return `${node.method || 'POST'} ${node.endpoint}`;
  }
  if (node.type === 'action' && node.actionType === 'create_task') {
    return `任务报告 ${node.reportId} -> 用户 ${node.assigneeUserId}`;
  }
  if (node.type === 'action') {
    return `${node.actionType || 'notify'} ${node.message || ''}`.trim();
  }
  return node.type;
}

function approvalNodeRoles(node: RuleNode) {
  const roles = Array.isArray(node.assigneeRoles) ? node.assigneeRoles.filter(Boolean) : [];
  if (roles.length > 0) {
    return roles;
  }
  return node.assigneeRole ? [node.assigneeRole] : [];
}

function approvalNodeMode(node: RuleNode) {
  return node.approvalMode === 'any' ? 'any' : 'all';
}

function approvalNodeModeLabel(mode: string) {
  return mode === 'any' ? '任一审批' : '全部审批';
}

function ruleStatusLabel(status: string) {
  const labels: Record<string, string> = {
    draft: '草稿',
    pending_review: '待审核',
    published: '已发布',
    archived: '已归档',
    disabled: '已停用'
  };
  return labels[status] ?? status;
}

function ruleRunTypeLabel(runType: string) {
  const labels: Record<string, string> = {
    debug: '调试运行',
    production: '生产运行',
    scheduled: '定时调度',
    subprocess: '子流程'
  };
  return labels[runType] ?? runType;
}

function ruleRunStatusLabel(status: string) {
  const labels: Record<string, string> = {
    succeeded: '成功',
    failed: '失败',
    running: '运行中',
    pending: '待执行',
    skipped: '已跳过'
  };
  return labels[status] ?? status;
}

function actionExecutionStatusLabel(status: string) {
  const labels: Record<string, string> = {
    succeeded: '成功',
    failed: '失败',
    pending_retry: '待重试',
    compensation_exhausted: '补偿已耗尽',
    compensation_ignored: '已忽略'
  };
  return labels[status] ?? status;
}

function approvalStatusLabel(status: string) {
  const labels: Record<string, string> = {
    pending: '待审批',
    approved: '已通过',
    rejected: '已驳回',
    supplement_required: '待补充',
    resubmitted: '已重提',
    closed: '已关闭'
  };
  return labels[status] ?? status;
}

function parseApprovalRoles(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter((item, index, items) => item.length > 0 && items.indexOf(item) === index);
}

function appendApprovalAssigneeRole(role: string) {
  const normalizedRole = role.trim();
  if (!normalizedRole) {
    return;
  }
  const roles = parseApprovalRoles(newNodeForm.value.assigneeRoles);
  if (!roles.includes(normalizedRole)) {
    roles.push(normalizedRole);
  }
  newNodeForm.value.assigneeRoles = roles.join(', ');
}

function nextCanvasNodePosition() {
  const index = nodes.value.length;
  return {
    x: 40 + (index % 4) * 220,
    y: 40 + Math.floor(index / 4) * 140
  };
}

function definitionWithFlowPositions(definition: RuleDefinition) {
  return {
    ...definition,
    nodes: (definition.nodes ?? []).map((node, index) => {
      const flowNode = flowNodes.value.find((item) => item.id === node.id) as { position?: { x: number; y: number } } | undefined;
      return {
        ...node,
        position: flowNode?.position ?? normalizeNodePosition(node, index)
      };
    }),
    edges: definition.edges ?? []
  };
}

function validateRuleTopology(definition: RuleDefinition): RuleTopologyValidationResult | null {
  const definitionNodes = definition.nodes ?? [];
  const definitionEdges = definition.edges ?? [];
  const nodeTypes = new Map(definitionNodes.map((node) => [node.id, node.type]));
  const startNode = definitionNodes.find((node) => node.type === 'start');
  if (!startNode || !definitionNodes.some((node) => node.type === 'end')) {
    return { message: '规则拓扑必须包含开始和结束节点', blockedNodeIds: startNode ? [startNode.id] : [], blockedEdgeKeys: [] };
  }
  const edgesBySource = new Map<string, RuleEdge[]>();
  for (const edge of definitionEdges) {
    if (!nodeTypes.has(edge.source) || !nodeTypes.has(edge.target) || edge.source === edge.target) {
      return {
        message: '规则拓扑包含无效连线',
        blockedNodeIds: [edge.source, edge.target].filter((id) => nodeTypes.has(id)),
        blockedEdgeKeys: [edgeKey(edge)]
      };
    }
    edgesBySource.set(edge.source, [...(edgesBySource.get(edge.source) ?? []), edge]);
  }
  const validatePath = (nodeId: string, path: Set<string>): RuleTopologyValidationResult | null => {
    const nodeType = nodeTypes.get(nodeId);
    if (!nodeType) {
      return { message: '规则拓扑包含无效连线', blockedNodeIds: [], blockedEdgeKeys: [] };
    }
    if (path.has(nodeId)) {
      return { message: '规则拓扑存在循环', blockedNodeIds: [nodeId], blockedEdgeKeys: [] };
    }
    if (nodeType === 'end') {
      return null;
    }
    const outgoingEdges = edgesBySource.get(nodeId) ?? [];
    if (outgoingEdges.length === 0) {
      return { message: '规则拓扑路径无法到达结束节点', blockedNodeIds: [nodeId], blockedEdgeKeys: [] };
    }
    const nextPath = new Set(path);
    nextPath.add(nodeId);
    for (const edge of outgoingEdges) {
      const error = validatePath(edge.target, nextPath);
      if (error) {
        return {
          ...error,
          blockedEdgeKeys: error.blockedEdgeKeys.length > 0 ? error.blockedEdgeKeys : [edgeKey(edge)]
        };
      }
    }
    return null;
  };
  return validatePath(startNode.id, new Set());
}

function handleFlowNodeDragStop() {
  if (!selectedRule.value) {
    return;
  }
  applyLocalDefinition(definitionWithFlowPositions(selectedRule.value.definition));
}

async function selectRule(rule: RuleSummary) {
  selectedRule.value = rule;
  syncWebhookConfigFromSelectedRule();
  productionResult.value = null;
  subprocessTopology.value = null;
  await refreshRuleAuxiliaryData();
}

async function refreshRuleAuxiliaryData() {
  await Promise.allSettled([loadRunHistory(), loadApprovalRecords(), loadActionExecutions()]);
}

async function loadRunHistory() {
  if (!selectedRule.value) {
    runHistory.value = [];
    return;
  }
  const result = await ruleApi.listRuns(String(selectedRule.value.ruleId), { page: 1, pageSize: 5 }) as unknown as { items: RuleRunHistoryItem[] };
  runHistory.value = result.items ?? [];
}

async function loadSubprocessTopology(run: RuleRunHistoryItem) {
  if (!selectedRule.value) {
    return;
  }
  loadingSubprocessTopologyRunId.value = run.runId;
  errorMessage.value = '';
  try {
    subprocessTopology.value = await ruleApi.subprocessRunTopology(
      String(selectedRule.value.ruleId),
      String(run.runId)
    ) as unknown as RuleSubprocessTopology;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '子流程拓扑加载失败';
  } finally {
    loadingSubprocessTopologyRunId.value = null;
  }
}

async function loadApprovalRecords() {
  if (!selectedRule.value) {
    approvalRecords.value = [];
    return;
  }
  try {
    const result = await ruleApi.listApprovalRecords(String(selectedRule.value.ruleId), { page: 1, pageSize: 10 }) as unknown as { items: RuleApprovalRecordItem[] };
    approvalRecords.value = result.items ?? [];
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '审批记录加载失败';
  }
}

async function handleApprovalRecord(record: RuleApprovalRecordItem, action: 'approve' | 'reject') {
  if (!selectedRule.value) {
    return;
  }
  approvalActionRecordId.value = record.approvalRecordId;
  approvalActionType.value = action;
  errorMessage.value = '';
  try {
    await ruleApi.handleApprovalRecord(String(selectedRule.value.ruleId), String(record.approvalRecordId), {
      action,
      comment: action === 'approve' ? '在规则页面通过' : '在规则页面驳回'
    });
    await loadApprovalRecords();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : (action === 'approve' ? '审批通过失败' : '审批驳回失败');
  } finally {
    approvalActionRecordId.value = null;
    approvalActionType.value = null;
  }
}

async function loadActionExecutions() {
  if (!selectedRule.value) {
    actionExecutions.value = [];
    return;
  }
  loadingActionExecutions.value = true;
  try {
    const result = await ruleApi.listActionExecutions(String(selectedRule.value.ruleId), { page: 1, pageSize: 10 }) as unknown as { items: RuleActionExecutionItem[] };
    actionExecutions.value = result.items ?? [];
    selectedActionExecutionIds.value = selectedActionExecutionIds.value.filter((id) =>
      actionExecutions.value.some((execution) => execution.actionExecutionId === id && canRetryActionExecution(execution))
    );
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Webhook 执行台账加载失败';
  } finally {
    loadingActionExecutions.value = false;
  }
}

function canRetryActionExecution(execution: RuleActionExecutionItem) {
  return execution.actionType === 'webhook' && (execution.status === 'pending_retry' || execution.status === 'failed');
}

function toggleCompensatableFilter() {
  actionStatusFilter.value = actionStatusFilter.value === 'all' ? 'compensatable' : 'all';
}

function toggleActionSelection(actionExecutionId: number | string, selected: boolean) {
  if (!selected) {
    selectedActionExecutionIds.value = selectedActionExecutionIds.value.filter((id) => id !== actionExecutionId);
    return;
  }
  if (!selectedActionExecutionIds.value.includes(actionExecutionId)) {
    selectedActionExecutionIds.value = [...selectedActionExecutionIds.value, actionExecutionId];
  }
}

async function retryActionExecution(execution: RuleActionExecutionItem) {
  if (!selectedRule.value) return;
  retryingActionExecutionId.value = execution.actionExecutionId;
  errorMessage.value = '';
  try {
    await ruleApi.retryWebhookActionExecution(String(selectedRule.value.ruleId), String(execution.actionExecutionId));
    await loadActionExecutions();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Webhook 动作重试失败';
  } finally {
    retryingActionExecutionId.value = null;
  }
}

async function batchRetryActionExecutions() {
  await batchHandleActionExecutions('retry');
}

async function batchIgnoreActionExecutions() {
  await batchHandleActionExecutions('ignore');
}

async function batchHandleActionExecutions(operation: 'retry' | 'ignore') {
  if (!selectedRule.value || selectedActionExecutionIds.value.length === 0) return;
  batchHandlingActionExecutions.value = true;
  errorMessage.value = '';
  try {
    await ruleApi.batchHandleWebhookActionExecutions(String(selectedRule.value.ruleId), {
      operation,
      actionExecutionIds: selectedActionExecutionIds.value,
      reason: operation === 'ignore' ? '从规则动作台账忽略' : undefined
    });
    selectedActionExecutionIds.value = [];
    await loadActionExecutions();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : (operation === 'ignore' ? 'Webhook 动作批量忽略失败' : 'Webhook 动作批量重试失败');
  } finally {
    batchHandlingActionExecutions.value = false;
  }
}

function toRunHistoryItem(result: ProductionRunResult): RuleRunHistoryItem {
  return {
    runId: result.runId ?? result.debugRunId ?? 'latest',
    ruleId: result.ruleId ?? selectedRule.value?.ruleId ?? '',
    versionId: result.versionId,
    status: result.status ?? 'succeeded',
    runType: result.runType,
    matched: result.output.matched,
    triggeredByUserId: result.triggeredByUserId ?? null,
    durationMs: result.durationMs ?? null,
    errorMessage: result.errorMessage ?? null
  };
}

function formatTraceItem(item: DebugResult['output']['trace'][number]) {
  const base = `${item.nodeId} · ${item.type} · ${item.matched ? 'true' : 'false'}`;
  if (item.type === 'branch') {
    return `${base} 路 ${item.selectedPath} -> ${item.nextNodeId}`;
  }
  if (item.type !== 'aggregate') {
    return base;
  }
  return `${base} · ${item.outputField}: ${item.value}`;
}

async function replaceSelectedRule(updated: RuleSummary) {
  rules.value = rules.value.map((rule) => (rule.ruleId === updated.ruleId ? updated : rule));
  selectedRule.value = updated;
  syncWebhookConfigFromSelectedRule(selectedWebhookNodeId.value);
  await refreshRuleAuxiliaryData();
}

function syncWebhookConfigFromSelectedRule(preferredNodeId = '') {
  const webhookNodes = selectedRule.value?.definition.nodes?.filter((node) => node.type === 'action' && node.actionType === 'webhook') ?? [];
  const node = webhookNodes.find((item) => item.id === preferredNodeId) ?? webhookNodes[0];
  selectedWebhookNodeId.value = node?.id ?? '';
  webhookConfig.value = {
    endpoint: node?.endpoint ?? '',
    method: node?.method ?? 'POST',
    maxRetryCount: String(node?.maxRetryCount ?? 0),
    retryBackoffSeconds: String(node?.retryBackoffSeconds ?? 0),
    maxAsyncReplayAttempts: String(node?.maxAsyncReplayAttempts ?? 3),
    signatureSecret: node?.signatureSecret ?? '',
    headersJson: formatJsonObject(node?.headers ?? {}),
    bodyJson: formatJsonObject(node?.body ?? {})
  };
}

function formatJsonObject(value: Record<string, unknown>) {
  return JSON.stringify(value, null, 2);
}

function parseJsonObject(value: string, fieldName: string) {
  const parsed = JSON.parse(value || '{}') as unknown;
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${fieldName} 必须是 JSON 对象`);
  }
  return parsed as Record<string, unknown>;
}

function applyLocalDefinition(definition: RuleDefinition) {
  if (!selectedRule.value) return;
  topologyIssueNodeIds.value = [];
  topologyIssueEdgeKeys.value = [];
  const updated = {
    ...selectedRule.value,
    definition
  };
  rules.value = rules.value.map((rule) => (rule.ruleId === updated.ruleId ? updated : rule));
  selectedRule.value = updated;
  syncWebhookConfigFromSelectedRule(selectedWebhookNodeId.value);
}

function applyApprovalTemplate() {
  if (!selectedRule.value) return;
  const template = approvalTemplates.value.find(
    (item) => String(item.approvalTemplateId) === selectedApprovalTemplateId.value
  );
  if (!template) {
    errorMessage.value = '请选择审批模板';
    return;
  }
  const prefix = `tpl${template.approvalTemplateId}_`;
  const templateNodes = template.steps.map((step, index) => approvalTemplateStepToNode(template, prefix, step, index));
  const existingIds = new Set(nodes.value.map((node) => node.id));
  const duplicated = templateNodes.find((node) => existingIds.has(node.id));
  if (duplicated) {
    errorMessage.value = `审批模板节点已存在：${duplicated.id}`;
    return;
  }
  const templateEdges = templateNodes.slice(0, -1).map((node, index) => ({
    source: node.id,
    target: templateNodes[index + 1].id
  }));
  const insertionEdges = approvalTemplateInsertionEdges(
    approvalTemplateInsertAfterNodeId.value,
    approvalTemplateInsertEdgeKey.value,
    templateNodes,
    edges.value
  );
  applyLocalDefinition({
    ...selectedRule.value.definition,
    nodes: [...nodes.value, ...templateNodes],
    edges: [...insertionEdges, ...templateEdges]
  });
  errorMessage.value = '';
}

function approvalTemplateInsertionEdges(insertAfterNodeId: string, selectedEdgeKey: string, templateNodes: RuleNode[], currentEdges: RuleEdge[]) {
  if (!insertAfterNodeId || templateNodes.length === 0) {
    return currentEdges;
  }
  if (!nodes.value.some((node) => node.id === insertAfterNodeId)) {
    return currentEdges;
  }
  const outgoingEdges = currentEdges.filter((edge) => edge.source === insertAfterNodeId);
  const targetOutgoingEdges = selectedEdgeKey
    ? outgoingEdges.filter((edge) => edgeKey(edge) === selectedEdgeKey)
    : outgoingEdges;
  if (targetOutgoingEdges.length === 0) {
    return currentEdges;
  }
  const targetEdgeKeys = new Set(targetOutgoingEdges.map(edgeKey));
  const retainedEdges = currentEdges.filter((edge) => !targetEdgeKeys.has(edgeKey(edge)));
  const firstTemplateNode = templateNodes[0];
  const lastTemplateNode = templateNodes[templateNodes.length - 1];
  return [
    ...retainedEdges,
    {
      source: insertAfterNodeId,
      target: firstTemplateNode.id,
      condition: targetOutgoingEdges[0].condition
    },
    ...targetOutgoingEdges.map((edge) => ({
      ...edge,
      source: lastTemplateNode.id
    }))
  ];
}

function approvalTemplateStepToNode(template: ApprovalTemplateSummary, prefix: string, step: ApprovalTemplateStepPayload, index: number): RuleNode {
  const assigneeRoles = step.assigneeRoles.filter((role) => role.trim());
  const stepId = step.stepId || `step${index + 1}`;
  const node: RuleNode = {
    id: `${prefix}${stepId}`,
    type: 'approval',
    approvalTemplateId: template.approvalTemplateId,
    approvalTemplateVersion: template.version ?? 1,
    approvalTemplateStepId: stepId,
    assigneeRole: assigneeRoles[0],
    assigneeRoles,
    approvalMode: step.approvalMode === 'any' ? 'any' : 'all',
    approvalTitle: step.approvalTitle,
    position: {
      x: 160 + index * 220,
      y: 360
    }
  };
  if (step.slaHours !== undefined) {
    node.slaHours = step.slaHours;
  }
  if (step.slaEscalations && step.slaEscalations.length > 0) {
    node.slaEscalations = step.slaEscalations;
    node.slaEscalationRole = step.slaEscalations[0].role;
  }
  return node;
}

function edgeKey(edge: RuleEdge) {
  return `${edge.source}->${edge.target}`;
}

function edgePreviewLabel(edge: Pick<RuleEdge, 'source' | 'target' | 'condition'>) {
  return `${edge.source} -> ${edge.target}${edge.condition ? ` (${edge.condition})` : ''}`;
}

function addCanvasNode() {
  if (!selectedRule.value) return;
  const id = newNodeForm.value.id.trim();
  if (!id) {
    errorMessage.value = '新增节点编号必填';
    return;
  }
  if (nodes.value.some((node) => node.id === id)) {
    errorMessage.value = '新增节点编号已存在';
    return;
  }
  const node: RuleNode = {
    id,
    type: newNodeForm.value.type,
    position: nextCanvasNodePosition()
  };
  if (newNodeForm.value.type === 'action') {
    node.actionType = newNodeForm.value.actionType.trim() || 'notify';
    node.message = newNodeForm.value.message.trim();
    if (node.actionType === 'create_task') {
      const reportId = parsePositiveInteger(newNodeForm.value.reportId);
      const assigneeUserId = parsePositiveInteger(newNodeForm.value.assigneeUserId);
      const content = newNodeForm.value.content.trim();
      const startOffset = parseNonNegativeInteger(newNodeForm.value.startOffset);
      const endOffset = parseNonNegativeInteger(newNodeForm.value.endOffset);
      const selectedText = newNodeForm.value.selectedText.trim();
      if (reportId === null) {
        errorMessage.value = '新增任务报告编号必填';
        return;
      }
      if (assigneeUserId === null) {
        errorMessage.value = '新增任务处理人编号必填';
        return;
      }
      if (!content) {
        errorMessage.value = '新增任务内容必填';
        return;
      }
      if (startOffset === null) {
        errorMessage.value = '新增任务起始位置必填';
        return;
      }
      if (endOffset === null || endOffset <= startOffset) {
        errorMessage.value = '新增任务结束位置必须大于起始位置';
        return;
      }
      if (!selectedText) {
        errorMessage.value = '新增任务选中文本必填';
        return;
      }
      node.reportId = reportId;
      node.assigneeUserId = assigneeUserId;
      node.content = content;
      node.anchor = {
        sectionId: newNodeForm.value.sectionId.trim(),
        startOffset,
        endOffset,
        selectedText
      };
    }
  }
  if (newNodeForm.value.type === 'condition' || newNodeForm.value.type === 'branch') {
    const field = newNodeForm.value.field.trim();
    const operator = newNodeForm.value.operator.trim();
    const value = newNodeForm.value.value.trim();
    if (!field) {
      errorMessage.value = '新增条件字段必填';
      return;
    }
    if (!operator) {
      errorMessage.value = '新增条件操作符必填';
      return;
    }
    if (!value) {
      errorMessage.value = '新增条件值必填';
      return;
    }
    node.field = field;
    node.operator = operator;
    node.value = value;
  }
  if (newNodeForm.value.type === 'aggregate') {
    const sourceField = newNodeForm.value.sourceField.trim();
    const valueField = newNodeForm.value.valueField.trim();
    const outputField = newNodeForm.value.outputField.trim();
    if (!sourceField) {
      errorMessage.value = '新增聚合源字段必填';
      return;
    }
    if (newNodeForm.value.operation === 'sum' && !valueField) {
      errorMessage.value = '新增聚合取值字段必填';
      return;
    }
    if (!outputField) {
      errorMessage.value = '新增聚合输出字段必填';
      return;
    }
    node.sourceField = sourceField;
    node.operation = newNodeForm.value.operation;
    node.valueField = valueField;
    node.outputField = outputField;
  }
  if (newNodeForm.value.type === 'subprocess') {
    const subprocessRuleId = parsePositiveInteger(newNodeForm.value.subprocessRuleId);
    const subprocessName = newNodeForm.value.subprocessName.trim();
    if (subprocessRuleId === null) {
      errorMessage.value = '新增子流程规则编号必填';
      return;
    }
    node.subprocessRuleId = subprocessRuleId;
    if (subprocessName) {
      node.subprocessName = subprocessName;
    }
  }
  if (newNodeForm.value.type === 'approval') {
    const assigneeRoles = parseApprovalRoles(newNodeForm.value.assigneeRoles);
    const approvalTitle = newNodeForm.value.approvalTitle.trim();
    const slaHours = parsePositiveInteger(newNodeForm.value.slaHours);
    const slaEscalationRole = newNodeForm.value.slaEscalationRole.trim();
    const slaEscalations = parseSlaEscalations(newNodeForm.value.slaEscalations);
    if (assigneeRoles.length === 0) {
      errorMessage.value = '新增审批处理角色必填';
      return;
    }
    if (!approvalTitle) {
      errorMessage.value = '新增审批标题必填';
      return;
    }
    if (slaEscalations === null) {
      errorMessage.value = '新增审批 SLA 升级策略必须使用 afterHours:role 格式';
      return;
    }
    node.assigneeRole = assigneeRoles[0];
    node.assigneeRoles = assigneeRoles;
    node.approvalMode = newNodeForm.value.approvalMode;
    const delegateRole = newNodeForm.value.delegateRole.trim();
    if (delegateRole) {
      node.delegateRole = delegateRole;
    }
    const delegateActiveFrom = newNodeForm.value.delegateActiveFrom.trim();
    if (delegateActiveFrom) {
      node.delegateActiveFrom = delegateActiveFrom;
    }
    const delegateActiveTo = newNodeForm.value.delegateActiveTo.trim();
    if (delegateActiveTo) {
      node.delegateActiveTo = delegateActiveTo;
    }
    node.approvalTitle = approvalTitle;
    if (slaHours !== null) {
      node.slaHours = slaHours;
    }
    if (slaEscalationRole) {
      node.slaEscalationRole = slaEscalationRole;
    }
    if (slaEscalations.length > 0) {
      node.slaEscalations = slaEscalations;
    }
  }
  applyLocalDefinition({
    ...selectedRule.value.definition,
    nodes: [...nodes.value, node],
    edges: edges.value
  });
  newNodeForm.value = {
    id: '',
    type: 'action',
    actionType: 'notify',
    message: '',
    field: '',
    operator: '',
    value: '',
    sourceField: '',
    operation: 'sum',
    valueField: '',
    outputField: '',
    subprocessRuleId: '',
    subprocessName: '',
    assigneeRoles: '',
    approvalMode: 'all',
    delegateRole: '',
    delegateActiveFrom: '',
    delegateActiveTo: '',
    approvalTitle: '',
    slaHours: '',
    slaEscalationRole: '',
    slaEscalations: '',
    reportId: '',
    assigneeUserId: '',
    content: '',
    sectionId: '',
    startOffset: '',
    endOffset: '',
    selectedText: ''
  };
  selectedApprovalAssigneeRole.value = '';
  errorMessage.value = '';
}

function parsePositiveInteger(value: string) {
  const parsed = Number.parseInt(value, 10);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function parseSlaEscalations(value: string) {
  const text = value.trim();
  if (!text) {
    return [];
  }
  const policies = [];
  for (const item of text.split(',')) {
    const [afterHoursText, roleText, ...extra] = item.split(':');
    const afterHours = parsePositiveInteger(afterHoursText?.trim() ?? '');
    const role = roleText?.trim() ?? '';
    if (extra.length > 0 || afterHours === null || !role) {
      return null;
    }
    policies.push({ afterHours, role });
  }
  return policies;
}

function parseNonNegativeInteger(value: string) {
  const parsed = Number.parseInt(value, 10);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null;
}

function addCanvasEdge() {
  if (!selectedRule.value) return;
  const source = newEdgeForm.value.source;
  const target = newEdgeForm.value.target;
  if (!source || !target) {
    errorMessage.value = '新增连线源节点和目标节点必填';
    return;
  }
  if (source === target) {
    errorMessage.value = '新增连线源节点和目标节点不能相同';
    return;
  }
  if (edges.value.some((edge) => edge.source === source && edge.target === target)) {
    errorMessage.value = 'Edge already exists';
    return;
  }
  const edge: RuleEdge = {
    source,
    target
  };
  const condition = newEdgeForm.value.condition.trim();
  if (condition) {
    edge.condition = condition;
  }
  applyLocalDefinition({
    ...selectedRule.value.definition,
    nodes: nodes.value,
    edges: [...edges.value, edge]
  });
  newEdgeForm.value = {
    source: '',
    target: '',
    condition: ''
  };
  errorMessage.value = '';
}

function deleteCanvasEdge() {
  if (!selectedRule.value || !deleteEdgeKey.value) return;
  const targetKey = deleteEdgeKey.value;
  applyLocalDefinition({
    ...selectedRule.value.definition,
    nodes: nodes.value,
    edges: edges.value.filter((edge) => edgeKey(edge) !== targetKey)
  });
  deleteEdgeKey.value = '';
  errorMessage.value = '';
}

function deleteCanvasNode() {
  if (!selectedRule.value || !deleteNodeId.value) return;
  const targetId = deleteNodeId.value;
  applyLocalDefinition({
    ...selectedRule.value.definition,
    nodes: nodes.value.filter((node) => node.id !== targetId),
    edges: edges.value.filter((edge) => edge.source !== targetId && edge.target !== targetId)
  });
  deleteNodeId.value = '';
  errorMessage.value = '';
}

async function saveCanvasChanges() {
  if (!selectedRule.value) return;
  const updatedDefinition = definitionWithFlowPositions(selectedRule.value.definition);
  const topologyError = validateRuleTopology(updatedDefinition);
  if (topologyError) {
    errorMessage.value = topologyError.message;
    topologyIssueNodeIds.value = topologyError.blockedNodeIds;
    topologyIssueEdgeKeys.value = topologyError.blockedEdgeKeys;
    refreshFlowPreview();
    return;
  }
  topologyIssueNodeIds.value = [];
  topologyIssueEdgeKeys.value = [];
  savingCanvas.value = true;
  errorMessage.value = '';
  try {
    const updated = await ruleApi.saveRule(String(selectedRule.value.ruleId), {
      status: selectedRule.value.status,
      definition: updatedDefinition
    }) as unknown as RuleSummary;
    await replaceSelectedRule(updated);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '规则画布保存失败';
  } finally {
    savingCanvas.value = false;
  }
}

function toNonNegativeInteger(value: string, fallback: number) {
  const parsed = Number.parseInt(value, 10);
  if (Number.isNaN(parsed) || parsed < 0) {
    return fallback;
  }
  return parsed;
}

async function saveWebhookConfig() {
  if (!selectedRule.value || !selectedWebhookNodeId.value) return;
  savingWebhookConfig.value = true;
  errorMessage.value = '';
  try {
    const headers = parseJsonObject(webhookConfig.value.headersJson, 'Webhook 请求头 JSON');
    const body = parseJsonObject(webhookConfig.value.bodyJson, 'Webhook 请求体 JSON');
    const updatedDefinition: RuleDefinition = {
      ...selectedRule.value.definition,
      nodes: nodes.value.map((node) => {
        if (node.id !== selectedWebhookNodeId.value) {
          return node;
        }
        return {
          ...node,
          endpoint: webhookConfig.value.endpoint.trim(),
          method: webhookConfig.value.method.trim().toUpperCase() || 'POST',
          maxRetryCount: toNonNegativeInteger(webhookConfig.value.maxRetryCount, 0),
          retryBackoffSeconds: toNonNegativeInteger(webhookConfig.value.retryBackoffSeconds, 0),
          maxAsyncReplayAttempts: toNonNegativeInteger(webhookConfig.value.maxAsyncReplayAttempts, 3),
          signatureSecret: webhookConfig.value.signatureSecret,
          headers,
          body
        };
      }),
      edges: edges.value
    };
    const updated = await ruleApi.saveRule(String(selectedRule.value.ruleId), {
      status: selectedRule.value.status,
      definition: updatedDefinition
    }) as unknown as RuleSummary;
    await replaceSelectedRule(updated);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Webhook 配置保存失败';
  } finally {
    savingWebhookConfig.value = false;
  }
}

async function submitReview() {
  if (!selectedRule.value) return;
  submittingReview.value = true;
  errorMessage.value = '';
  try {
    const updated = await ruleApi.submitForReview(String(selectedRule.value.ruleId), { comment: 'ready for approval' }) as unknown as RuleSummary;
    await replaceSelectedRule(updated);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '规则提交审核失败';
  } finally {
    submittingReview.value = false;
  }
}

async function approveSelectedRule() {
  if (!selectedRule.value) return;
  approving.value = true;
  errorMessage.value = '';
  try {
    const updated = await ruleApi.approveRule(String(selectedRule.value.ruleId), { comment: 'approved for production' }) as unknown as RuleSummary;
    await replaceSelectedRule(updated);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '规则审批发布失败';
  } finally {
    approving.value = false;
  }
}

async function runProduction() {
  if (!selectedRule.value) return;
  runningProduction.value = true;
  errorMessage.value = '';
  productionResult.value = null;
  try {
    const sample = JSON.parse(sampleJson.value) as Record<string, unknown>;
    productionResult.value = await ruleApi.executeRule(String(selectedRule.value.ruleId), { sample }) as unknown as ProductionRunResult;
    runHistory.value = [toRunHistoryItem(productionResult.value), ...runHistory.value.filter((run) => run.runId !== (productionResult.value?.debugRunId ?? productionResult.value?.runId))].slice(0, 5);
    await loadRunHistory();
    await loadApprovalRecords();
    await loadActionExecutions();
    if (runHistory.value.length === 0 && productionResult.value) {
      runHistory.value = [toRunHistoryItem(productionResult.value)];
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '规则生产运行失败';
    await Promise.allSettled([loadRunHistory(), loadApprovalRecords(), loadActionExecutions()]);
  } finally {
    runningProduction.value = false;
  }
}

async function runDebug() {
  if (!selectedRule.value) return;
  debugging.value = true;
  errorMessage.value = '';
  debugResult.value = null;
  try {
    const sample = JSON.parse(sampleJson.value) as Record<string, unknown>;
    debugResult.value = await ruleApi.debugRule(String(selectedRule.value.ruleId), { sample }) as unknown as DebugResult;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '规则调试失败';
  } finally {
    debugging.value = false;
  }
}

async function retrySelectedSchedule() {
  if (!selectedRule.value) return;
  retryingSchedule.value = true;
  errorMessage.value = '';
  try {
    const sample = JSON.parse(sampleJson.value) as Record<string, unknown>;
    const updated = await ruleApi.retrySchedule(String(selectedRule.value.ruleId), {
      nextRunAt: new Date().toISOString(),
      scheduleInput: { sample }
    }) as unknown as RuleSummary;
    await replaceSelectedRule(updated);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '规则调度重试失败';
  } finally {
    retryingSchedule.value = false;
  }
}

onMounted(loadRules);
</script>

<style scoped>
.rule-page {
  display: grid;
  gap: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.rule-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
}

.rule-list {
  display: grid;
  align-content: start;
  gap: 8px;
}

.rule-list-header,
.node-library {
  display: grid;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
}

.rule-list-header h3,
.node-library h3,
.node-library h4 {
  margin: 0;
}

.rule-list-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.node-library article {
  display: grid;
  gap: 6px;
}

.node-library h4 {
  color: #303846;
  font-size: 13px;
}

.node-library span {
  display: inline-flex;
  width: fit-content;
  padding: 3px 8px;
  border-radius: 4px;
  background: #f4f6f8;
  color: #606266;
  font-size: 12px;
}

.rule-item {
  display: grid;
  gap: 6px;
  padding: 12px;
  text-align: left;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
}

.rule-item.active {
  border-color: #409eff;
}

.rule-item span {
  color: #6b7280;
  font-size: 12px;
}

.canvas {
  min-height: 520px;
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 16px;
  background: #fff;
  border: 1px solid #dcdfe6;
}

.node-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}

.flow-canvas-panel {
  display: grid;
  gap: 12px;
}

.flow-hint {
  color: #6b7280;
  font-size: 12px;
}

.flow-canvas-shell {
  height: 420px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
  background: #f8fafc;
}

.rule-flow-canvas {
  width: 100%;
  height: 100%;
}

.flow-node-card {
  width: 180px;
  min-height: 88px;
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #dbe3ee;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.flow-node-card span {
  color: #6b7280;
  font-size: 12px;
}

.flow-node-card strong {
  color: #111827;
}

.flow-node-card small {
  color: #475569;
  font-size: 11px;
  font-weight: 700;
}

.flow-node-card.preview-insert-point {
  border-color: #2563eb;
  background: #eff6ff;
}

.flow-node-card.preview-target {
  border-color: #d97706;
  background: #fffbeb;
}

.flow-node-card.preview-approval-step {
  border-style: dashed;
  border-color: #16a34a;
  background: #f0fdf4;
}

.flow-node-card.topology-blocked {
  border-color: #dc2626;
  background: #fef2f2;
  box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.16), 0 4px 12px rgba(15, 23, 42, 0.08);
}

.flow-node-card.topology-blocked small {
  color: #991b1b;
}

.rule-flow-canvas :deep(.preview-add-edge path) {
  stroke: #16a34a;
  stroke-width: 3;
  stroke-dasharray: 8 5;
}

.rule-flow-canvas :deep(.preview-remove-edge path) {
  stroke: #dc2626;
  stroke-width: 3;
  stroke-dasharray: 3 5;
}

.rule-flow-canvas :deep(.preview-add-edge .vue-flow__edge-text) {
  fill: #166534;
  font-weight: 700;
}

.rule-flow-canvas :deep(.preview-remove-edge .vue-flow__edge-text) {
  fill: #991b1b;
  font-weight: 700;
}

.rule-flow-canvas :deep(.topology-broken-edge path) {
  stroke: #dc2626;
  stroke-width: 3;
  stroke-dasharray: 4 4;
}

.rule-flow-canvas :deep(.topology-broken-edge .vue-flow__edge-text) {
  fill: #991b1b;
  font-weight: 700;
}

.flow-node-card p {
  margin: 0;
  color: #303846;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.node-card {
  min-height: 104px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.node-card span {
  color: #6b7280;
  font-size: 12px;
}

.node-card strong {
  display: block;
  margin-top: 6px;
}

.node-card p {
  margin: 10px 0 0;
  color: #303846;
}

.edge-list,
.trace-list,
.topology-action-list,
.topology-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.approval-record-list {
  display: grid;
  gap: 8px;
}

.approval-record-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.approval-record-row div {
  display: grid;
  gap: 4px;
}

.approval-record-row small {
  color: #6b7280;
  font-size: 12px;
}

.approval-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.edge-list span,
.trace-list span,
.topology-list span {
  padding: 4px 8px;
  background: #f3f4f6;
  border-radius: 4px;
  font-size: 12px;
}

.edge-list span.topology-broken-edge-label {
  color: #991b1b;
  background: #fef2f2;
  outline: 1px solid #dc2626;
  font-weight: 700;
}

.webhook-config {
  display: grid;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.canvas-editor {
  display: grid;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.canvas-editor h3 {
  margin: 0;
}

.webhook-config h3 {
  margin: 0;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field {
  display: grid;
  gap: 6px;
}

.field span {
  color: #303846;
  font-size: 12px;
  font-weight: 600;
}

.field-wide {
  grid-column: 1 / -1;
}

.field select {
  min-height: 32px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  color: #303846;
  background: #fff;
}

.editor-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px;
}

.editor-actions .field {
  min-width: 220px;
}

.sample-editor {
  display: grid;
  gap: 8px;
}

.debug-result {
  display: grid;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #e5e7eb;
}

.debug-result h3,
.debug-result p {
  margin: 0;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ledger-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.ledger-list {
  display: grid;
  gap: 8px;
}

.compensation-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(104px, 1fr));
  gap: 8px;
  align-items: stretch;
}

.compensation-summary h4 {
  grid-column: 1 / -1;
  margin: 0;
  color: #303846;
  font-size: 14px;
}

.compensation-summary span,
.compensation-summary strong {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  color: #303846;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}

.ledger-row {
  display: grid;
  grid-template-columns: 28px minmax(140px, 1fr) minmax(220px, 2fr) minmax(220px, 2fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.ledger-select-placeholder {
  width: 16px;
  height: 16px;
}

.ledger-row div {
  display: grid;
  gap: 4px;
}

.ledger-row span,
.ledger-row small,
.ledger-row p {
  color: #6b7280;
  font-size: 12px;
}

.ledger-row p,
.ledger-row small {
  overflow-wrap: anywhere;
}

@media (max-width: 860px) {
  .rule-layout {
    grid-template-columns: 1fr;
  }

  .ledger-row {
    grid-template-columns: 1fr;
  }

  .config-grid {
    grid-template-columns: 1fr;
  }

  .compensation-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
