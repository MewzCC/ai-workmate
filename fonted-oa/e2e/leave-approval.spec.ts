import { expect, request, test } from '@playwright/test';

const employeeEmail = process.env.E2E_EMPLOYEE_EMAIL;
const employeePassword = process.env.E2E_EMPLOYEE_PASSWORD;
const approverEmail = process.env.E2E_APPROVER_EMAIL;
const approverPassword = process.env.E2E_APPROVER_PASSWORD;
const apiBaseUrl = process.env.E2E_API_BASE_URL || 'http://localhost:8080';

test('employee submits leave, approver decides, employee sees the result', async () => {
  test.skip(
    !employeeEmail || !employeePassword || !approverEmail || !approverPassword,
    'Provide the four E2E account environment variables; accounts must belong to one tenant and be configured as applicant/approver.',
  );

  const employee = await request.newContext({ baseURL: apiBaseUrl });
  const approver = await request.newContext({ baseURL: apiBaseUrl });
  try {
    await login(employee, employeeEmail!, employeePassword!);
    const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    const create = await employee.post('/api/leave-applications', {
      data: {
        leaveType: 'PERSONAL',
        startDate: tomorrow,
        startPeriod: 'AM',
        endDate: tomorrow,
        endPeriod: 'PM',
        reason: 'Phase 1 automated end-to-end verification',
      },
    });
    expect(create.ok()).toBeTruthy();
    const draft = (await create.json()).data;
    expect(draft.status).toBe('DRAFT');
    expect(draft.durationHalfDays).toBe(2);

    const submit = await employee.post(`/api/leave-applications/${draft.id}/submit`, {
      data: { version: draft.version },
    });
    expect(submit.ok()).toBeTruthy();
    expect((await submit.json()).data.status).toBe('PENDING');

    await login(approver, approverEmail!, approverPassword!);
    const todos = await approver.get('/api/todos?status=PENDING&page=1&size=100');
    expect(todos.ok()).toBeTruthy();
    const task = (await todos.json()).data.records.find(
      (item: { applicationId: number }) => item.applicationId === draft.id,
    );
    expect(task).toBeTruthy();

    const approve = await approver.post(`/api/approval-tasks/${task.id}/approve`, {
      data: { version: task.version, comment: 'E2E approved' },
    });
    expect(approve.ok()).toBeTruthy();
    expect((await approve.json()).data.status).toBe('APPROVED');

    const detail = await employee.get(`/api/leave-applications/${draft.id}`);
    expect(detail.ok()).toBeTruthy();
    expect((await detail.json()).data.status).toBe('APPROVED');

    const timeline = await employee.get(`/api/approval-tasks/${task.id}/timeline`);
    expect(timeline.ok()).toBeTruthy();
    expect((await timeline.json()).data.map((item: { action: string }) => item.action))
      .toEqual(['SUBMIT', 'APPROVE']);
  } finally {
    await employee.dispose();
    await approver.dispose();
  }
});

async function login(
  context: Awaited<ReturnType<typeof request.newContext>>,
  email: string,
  password: string,
) {
  const response = await context.post('/api/auth/login/password', {
    data: { email, password, remember: false },
  });
  expect(response.ok()).toBeTruthy();
}
