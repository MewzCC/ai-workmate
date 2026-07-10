import type {
  AiTaskExecuteRequest,
  AiTaskExecuteResponse,
  AiTaskPlanRequest,
  AiTaskPlanResponse,
} from '@/types/oa';

const BASE = '/api';

interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

async function parseResult<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  const json: ApiResult<T> = await res.json();
  if (json.code !== 200) {
    throw new Error(json.message || '接口请求失败');
  }
  return json.data;
}

function inferTaskType(input: string): AiTaskPlanResponse['type'] {
  if (/新建|创建|采购|入职/.test(input)) return 'create';
  if (/修改|调整|改为|同步/.test(input)) return 'update';
  if (/审批|预审|通过|驳回|催办/.test(input)) return 'approve';
  if (/接口|联调|失败|报错|排查|回放/.test(input)) return 'debug';
  if (/导出|汇总|报表|下载/.test(input)) return 'export';
  return 'general';
}

export function createFallbackPlan(request: AiTaskPlanRequest): AiTaskPlanResponse {
  const type = inferTaskType(request.input);
  const risky = ['approve', 'export', 'update'].includes(type);
  return {
    taskId: `task_local_${Date.now()}`,
    type,
    riskLevel: risky ? 'high' : 'medium',
    requireConfirm: risky,
    summary: '后端不可用，已生成本地模拟执行计划。',
    steps: [
      { title: '读取当前页面上下文', description: `识别 ${request.pageId} 页面、当前角色和可用按钮权限。` },
      { title: '校验角色权限', description: '按 RBAC、数据范围与 AI 动作白名单过滤不可执行操作。' },
      { title: '生成执行方案', description: '形成可确认的步骤，不直接修改真实业务数据。' },
      { title: '等待人工确认', description: '高风险动作需要二次确认并写入审计记录。' },
    ],
  };
}

export function createFallbackExecute(request: AiTaskExecuteRequest): AiTaskExecuteResponse {
  return {
    success: request.confirm,
    auditId: `AUDIT-LOCAL-${Date.now().toString().slice(-6)}`,
    message: 'AI 任务已使用本地 mock 模拟执行完成。',
    result: {
      successCount: 2,
      pendingConfirmCount: request.type === 'approve' ? 2 : 1,
      rejectSuggestCount: request.type === 'approve' ? 1 : 0,
    },
  };
}

export async function getSystemHealth(): Promise<{ status: string; service: string }> {
  const res = await fetch(`${BASE}/system/health`);
  return parseResult(res);
}

export async function planAiTask(request: AiTaskPlanRequest): Promise<AiTaskPlanResponse> {
  const res = await fetch(`${BASE}/ai/tasks/plan`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  return parseResult(res);
}

export async function executeAiTask(request: AiTaskExecuteRequest): Promise<AiTaskExecuteResponse> {
  const res = await fetch(`${BASE}/ai/tasks/execute`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  return parseResult(res);
}
