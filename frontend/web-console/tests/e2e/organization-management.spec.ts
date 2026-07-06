import { expect, test } from '@playwright/test';

test.describe('Organization management E2E', () => {
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

    await page.goto('/dashboard');
    await page.getByRole('menuitem', { name: 'Organization management' }).click();

    await expect(page.getByRole('heading', { name: 'Organization Management' })).toBeVisible();
    await expect(page.getByText('HQ / Headquarters / company')).toBeVisible();
    await expect(page.getByText('FIN / Finance Center / department')).toBeVisible();

    await page.getByLabel('Organization unit code').fill('RISK');
    await page.getByLabel('Organization unit name', { exact: true }).fill('Risk Team');
    await page.getByLabel('Organization unit parent id', { exact: true }).fill('1');
    await page.getByLabel('Organization unit type', { exact: true }).fill('team');
    await page.getByLabel('Organization unit sort order', { exact: true }).fill('30');
    await page.getByRole('button', { name: 'Create organization unit' }).click();

    expect(unitPayload).toMatchObject({
      code: 'RISK',
      name: 'Risk Team',
      parentId: 1,
      unitType: 'team',
      sortOrder: 30
    });
    await expect(page.getByText('Organization unit created: RISK')).toBeVisible();
    await expect(page.getByText('RISK / Risk Team / team')).toBeVisible();

    await page.getByLabel('Update organization unit id').fill('3');
    await page.getByLabel('Update organization unit name').fill('Finance Risk Team');
    await page.getByLabel('Update organization unit parent id').fill('2');
    await page.getByLabel('Update organization unit type').fill('team');
    await page.getByLabel('Update organization unit sort order').fill('5');
    await page.getByRole('button', { name: 'Update organization unit' }).click();

    expect(updateUnitPayload).toMatchObject({
      name: 'Finance Risk Team',
      parentId: 2,
      unitType: 'team',
      sortOrder: 5
    });
    await expect(page.getByText('Organization unit updated: RISK')).toBeVisible();
    await expect(page.getByText('RISK / Finance Risk Team / team')).toBeVisible();

    await page.getByLabel('Position organization unit id').fill('3');
    await page.getByLabel('Position code').fill('risk_manager');
    await page.getByLabel('Position name').fill('Risk Manager');
    await page.getByLabel('Position roles').fill('risk_manager, finance_delegate');
    await page.getByLabel('Position manager user id').fill('71');
    await page.getByLabel('Position sort order').fill('10');
    await page.getByRole('button', { name: 'Create organization position' }).click();

    expect(positionPayload).toMatchObject({
      organizationUnitId: 3,
      code: 'risk_manager',
      name: 'Risk Manager',
      roles: ['risk_manager', 'finance_delegate'],
      managerUserId: 71,
      sortOrder: 10
    });
    await expect(page.getByText('Organization position created: risk_manager')).toBeVisible();
    await expect(page.getByText('risk_manager / Risk Manager / roles risk_manager, finance_delegate / manager 71')).toBeVisible();

    await page.getByLabel('Assignment user selector').selectOption('71');
    await page.getByLabel('Assignment position selector').selectOption('10');
    await page.getByLabel('Assignment active from', { exact: true }).fill('2026-01-01T08:00');
    await page.getByLabel('Assignment active to', { exact: true }).fill('2026-12-31T18:30');
    await page.getByLabel('Primary position assignment', { exact: true }).check();
    await page.getByRole('button', { name: 'Assign user to position' }).click();

    expect(assignmentPayload).toMatchObject({
      userId: 71,
      positionId: 10,
      primary: true,
      activeFrom: '2026-01-01T00:00:00.000Z',
      activeTo: '2026-12-31T10:30:00.000Z'
    });
    await expect(page.getByText('Position assignment created: user 71 -> position 10')).toBeVisible();
    await expect(page.getByText('Fiona Manager / fin.manager / Risk Team / Risk Manager')).toBeVisible();

    await page.getByLabel('Batch import position assignments').fill([
      '71,10,true,2026-03-01T08:00,2026-12-31T18:30',
      '9999,10,false,,'
    ].join('\n'));
    await page.getByRole('button', { name: 'Batch import position assignments' }).click();

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
    await expect(page.getByText('Position assignments imported: 1 succeeded, 1 failed')).toBeVisible();

    await page.getByLabel('Update assignment id').fill('99');
    await page.getByLabel('Update assignment active from').fill('2026-02-01T08:00');
    await page.getByLabel('Update assignment active to').fill('2026-11-30T18:30');
    await page.getByLabel('Update primary position assignment').check();
    await page.getByRole('button', { name: 'Update position assignment' }).click();

    expect(updateAssignmentPayload).toMatchObject({
      primary: true,
      activeFrom: '2026-02-01T00:00:00.000Z',
      activeTo: '2026-11-30T10:30:00.000Z'
    });
    await expect(page.getByText('Position assignment updated: 99')).toBeVisible();

    await page.getByLabel('Disable assignment id').fill('99');
    await page.getByLabel('Disable assignment reason').fill('role ended');
    await page.getByRole('button', { name: 'Disable position assignment' }).click();

    expect(disableAssignmentPayload).toMatchObject({
      reason: 'role ended'
    });
    await expect(page.getByText('Position assignment disabled: 99')).toBeVisible();
    await expect(page.getByText('Fiona Manager / fin.manager / Risk Team / Risk Manager')).not.toBeVisible();
  });
});
