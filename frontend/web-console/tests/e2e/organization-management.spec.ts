import { expect, test } from '@playwright/test';

test.describe('Organization management E2E', () => {
  test('P0：组织管理页面使用中文业务文案', async ({ page }) => {
    await page.route('**/api/v1/users?*', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [],
            page: 1,
            pageSize: 100,
            total: 0
          }
        }
      });
    });
    await page.route('**/api/v1/organization-directory', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            departments: [],
            roles: [],
            organizationTree: []
          }
        }
      });
    });

    await page.goto('/admin/organizations');

    await expect(page.getByRole('heading', { name: '组织管理' })).toBeVisible();
    await expect(page.getByRole('button', { name: '刷新组织树' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '创建组织单元' })).toBeVisible();
    await expect(page.getByLabel('组织单元编码')).toBeVisible();
    await expect(page.getByLabel('组织单元名称', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '创建组织单元' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '创建岗位' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '分配用户岗位' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '批量导入岗位分配' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '组织树' })).toBeVisible();
    await expect(page.getByText('暂无组织单元')).toBeVisible();

    const visibleText = await page.locator('body').innerText();
    expect(visibleText).not.toMatch(/Organization Management|Refresh organization tree|Create Organization Unit|Update Organization Unit|Create Organization Position|Assign User To Position|Batch Import Position Assignments|Update Position Assignment|Disable Position Assignment|Organization Tree|No organization units|Parent ID|Sort order|Active from|Active to|Primary position|Select user|Select position/);
  });

  test('creates organization units and positions from the admin menu', async ({ page }) => {
    let unitPayload: Record<string, unknown> | null = null;
    let updateUnitPayload: Record<string, unknown> | null = null;
    let positionPayload: Record<string, unknown> | null = null;
    let assignmentPayload: Record<string, unknown> | null = null;
    let batchAssignmentPayload: Record<string, unknown> | null = null;
    let updateAssignmentPayload: Record<string, unknown> | null = null;
    let disableAssignmentPayload: Record<string, unknown> | null = null;
    const tree = [
      {
        unitId: 1,
        code: 'HQ',
        name: 'Headquarters',
        parentId: null,
        unitType: 'company',
        positions: [],
        children: [
          {
            unitId: 2,
            code: 'FIN',
            name: 'Finance Center',
            parentId: 1,
            unitType: 'department',
            positions: [],
            children: []
          }
        ]
      }
    ];
    const findUnit = (nodes: typeof tree, unitId: number): (typeof tree)[number] | undefined => {
      for (const node of nodes) {
        if (node.unitId === unitId) {
          return node;
        }
        const found = findUnit(node.children as typeof tree, unitId);
        if (found) {
          return found;
        }
      }
      return undefined;
    };

    await page.route('**/api/v1/users?*', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              {
                userId: 71,
                username: 'fin.manager',
                displayName: 'Fiona Manager',
                department: 'Finance Center',
                position: 'Finance Analyst',
                status: 'enabled',
                roles: ['finance_delegate']
              }
            ],
            page: 1,
            pageSize: 100,
            total: 1
          }
        }
      });
    });

    await page.route('**/api/v1/organization-directory', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            departments: [],
            roles: [],
            organizationTree: tree
          }
        }
      });
    });

    await page.route('**/api/v1/organization-position-assignments', async (route) => {
      assignmentPayload = await route.request().postDataJSON();
      const risk = findUnit(tree, 3);
      if (risk && risk.positions[0]) {
        risk.positions[0].users = [
        {
          userId: 71,
          username: 'fin.manager',
          displayName: 'Fiona Manager',
          department: 'Risk Team',
          position: 'Risk Manager',
          roles: ['risk_manager', 'finance_delegate']
        }
      ];
      }
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            assignmentId: 99,
            userId: 71,
            positionId: 10,
            primary: true,
            status: 'enabled'
          }
        }
      });
    });

    await page.route('**/api/v1/organization-position-assignments/batch-import', async (route) => {
      batchAssignmentPayload = await route.request().postDataJSON();
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            imported: 1,
            failed: 1,
            items: [
              { assignmentId: 100, userId: 71, positionId: 10, primary: true, status: 'imported' },
              { userId: 9999, positionId: 10, status: 'failed', reason: 'user_not_found' }
            ]
          }
        }
      });
    });

    await page.route('**/api/v1/organization-position-assignments/99/disable', async (route) => {
      disableAssignmentPayload = await route.request().postDataJSON();
      const risk = findUnit(tree, 3);
      if (risk && risk.positions[0]) {
        risk.positions[0].users = [];
      }
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            assignmentId: 99,
            userId: 71,
            positionId: 10,
            primary: true,
            status: 'disabled'
          }
        }
      });
    });

    await page.route('**/api/v1/organization-position-assignments/99', async (route) => {
      updateAssignmentPayload = await route.request().postDataJSON();
      const risk = findUnit(tree, 3);
      if (risk && risk.positions[0]) {
        risk.positions[0].users = [
        {
          userId: 71,
          username: 'fin.manager',
          displayName: 'Fiona Manager',
          department: 'Risk Team',
          position: 'Risk Manager',
          roles: ['risk_manager', 'finance_delegate']
        }
      ];
      }
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            assignmentId: 99,
            userId: 71,
            positionId: 10,
            primary: true,
            status: 'enabled',
            activeFrom: '2026-02-01T00:00:00.000Z',
            activeTo: '2026-11-30T10:30:00.000Z'
          }
        }
      });
    });

    await page.route('**/api/v1/organization-units', async (route) => {
      unitPayload = await route.request().postDataJSON();
      tree[0].children.push({
        unitId: 3,
        code: 'RISK',
        name: 'Risk Team',
        parentId: 1,
        unitType: 'team',
        positions: [],
        children: []
      });
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: tree[0].children[1]
        }
      });
    });

    await page.route('**/api/v1/organization-units/3', async (route) => {
      updateUnitPayload = await route.request().postDataJSON();
      const risk = tree[0].children.find((unit) => unit.unitId === 3);
      if (risk) {
        risk.name = 'Finance Risk Team';
        risk.parentId = 2;
        risk.unitType = 'team';
        tree[0].children = tree[0].children.filter((unit) => unit.unitId !== 3);
        tree[0].children[0].children.push(risk);
      }
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: risk
        }
      });
    });

    await page.route('**/api/v1/organization-positions', async (route) => {
      positionPayload = await route.request().postDataJSON();
      const risk = findUnit(tree, 3);
      risk?.positions.push({
        positionId: 10,
        organizationUnitId: 3,
        code: 'risk_manager',
        name: 'Risk Manager',
        roles: ['risk_manager', 'finance_delegate'],
        managerUserId: 71
      });
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: risk?.positions[0]
        }
      });
    });

    await page.goto('/admin/organizations');

    await expect(page.getByRole('heading', { name: '组织管理' })).toBeVisible();
    await expect(page.getByText('HQ / Headquarters / company')).toBeVisible();
    await expect(page.getByText('FIN / Finance Center / department')).toBeVisible();

    await page.getByLabel('组织单元编码').fill('RISK');
    await page.getByLabel('组织单元名称', { exact: true }).fill('Risk Team');
    await page.getByLabel('上级组织编号', { exact: true }).fill('1');
    await page.getByLabel('组织类型', { exact: true }).fill('team');
    await page.getByLabel('组织单元排序号', { exact: true }).fill('30');
    await page.getByRole('button', { name: '创建组织单元' }).click();

    expect(unitPayload).toMatchObject({
      code: 'RISK',
      name: 'Risk Team',
      parentId: 1,
      unitType: 'team',
      sortOrder: 30
    });
    await expect(page.getByText('组织单元已创建：RISK')).toBeVisible();
    await expect(page.getByText('RISK / Risk Team / team')).toBeVisible();

    await page.getByLabel('更新组织单元编号').fill('3');
    await page.getByLabel('更新组织单元名称').fill('Finance Risk Team');
    await page.getByLabel('更新上级组织编号').fill('2');
    await page.getByLabel('更新组织类型').fill('team');
    await page.getByLabel('更新组织单元排序号').fill('5');
    await page.getByRole('button', { name: '更新组织单元' }).click();

    expect(updateUnitPayload).toMatchObject({
      name: 'Finance Risk Team',
      parentId: 2,
      unitType: 'team',
      sortOrder: 5
    });
    await expect(page.getByText('组织单元已更新：RISK')).toBeVisible();
    await expect(page.getByText('RISK / Finance Risk Team / team')).toBeVisible();

    await page.getByLabel('岗位所属组织单元编号').fill('3');
    await page.getByLabel('岗位编码').fill('risk_manager');
    await page.getByLabel('岗位名称').fill('Risk Manager');
    await page.getByLabel('岗位角色').fill('risk_manager, finance_delegate');
    await page.getByLabel('岗位负责人用户编号').fill('71');
    await page.getByLabel('岗位排序号').fill('10');
    await page.getByRole('button', { name: '创建岗位' }).click();

    expect(positionPayload).toMatchObject({
      organizationUnitId: 3,
      code: 'risk_manager',
      name: 'Risk Manager',
      roles: ['risk_manager', 'finance_delegate'],
      managerUserId: 71,
      sortOrder: 10
    });
    await expect(page.getByText('岗位已创建：risk_manager')).toBeVisible();
    await expect(page.getByText('risk_manager / Risk Manager / 角色 risk_manager, finance_delegate / 负责人 71')).toBeVisible();

    await page.getByLabel('分配用户选择').selectOption('71');
    await page.getByLabel('分配岗位选择').selectOption('10');
    await page.getByLabel('岗位分配生效开始', { exact: true }).fill('2026-01-01T08:00');
    await page.getByLabel('岗位分配生效结束', { exact: true }).fill('2026-12-31T18:30');
    await page.getByLabel('主岗位分配', { exact: true }).check();
    await page.getByRole('button', { name: '分配用户岗位' }).click();

    expect(assignmentPayload).toMatchObject({
      userId: 71,
      positionId: 10,
      primary: true,
      activeFrom: '2026-01-01T00:00:00.000Z',
      activeTo: '2026-12-31T10:30:00.000Z'
    });
    await expect(page.getByText('岗位分配已创建：用户 71 -> 岗位 10')).toBeVisible();
    await expect(page.getByText('Fiona Manager / fin.manager / Risk Team / Risk Manager')).toBeVisible();

    await page.getByRole('textbox', { name: '批量导入岗位分配' }).fill([
      '71,10,true,2026-03-01T08:00,2026-12-31T18:30',
      '9999,10,false,,'
    ].join('\n'));
    await page.getByRole('button', { name: '批量导入岗位分配' }).click();

    expect(batchAssignmentPayload).toMatchObject({
      assignments: [
        {
          userId: 71,
          positionId: 10,
          primary: true,
          activeFrom: '2026-03-01T00:00:00.000Z',
          activeTo: '2026-12-31T10:30:00.000Z'
        },
        {
          userId: 9999,
          positionId: 10,
          primary: false
        }
      ]
    });
    await expect(page.getByText('岗位分配导入完成：成功 1 条，失败 1 条')).toBeVisible();

    await page.getByLabel('更新分配编号').fill('99');
    await page.getByLabel('更新分配生效开始').fill('2026-02-01T08:00');
    await page.getByLabel('更新分配生效结束').fill('2026-11-30T18:30');
    await page.getByLabel('更新为主岗位').check();
    await page.getByRole('button', { name: '更新岗位分配' }).click();

    expect(updateAssignmentPayload).toMatchObject({
      primary: true,
      activeFrom: '2026-02-01T00:00:00.000Z',
      activeTo: '2026-11-30T10:30:00.000Z'
    });
    await expect(page.getByText('岗位分配已更新：99')).toBeVisible();

    await page.getByLabel('停用分配编号').fill('99');
    await page.getByLabel('停用分配原因').fill('role ended');
    await page.getByRole('button', { name: '停用岗位分配' }).click();

    expect(disableAssignmentPayload).toMatchObject({
      reason: 'role ended'
    });
    await expect(page.getByText('岗位分配已停用：99')).toBeVisible();
    await expect(page.getByText('Fiona Manager / fin.manager / Risk Team / Risk Manager')).not.toBeVisible();
  });
});
